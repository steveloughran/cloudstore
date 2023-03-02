/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.fs.s3a.extra;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.S3VersionSummary;
import com.amazonaws.services.s3.model.VersionListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.fs.tools.csv.SimpleCsvWriter;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.LIMIT;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.fs.store.diag.S3ADiagnosticsInfo.FS_S3A_AUDIT_REJECT_OUT_OF_SPAN_OPERATIONS;

public class ListVersions extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(ListVersions.class);

  public static final String OUTPUT = "out";

  public static final String DELETED = "deleted";

  public static final String DIRS = "dirs";

  public static final String QUIET = "q";

  public static final String SEPARATOR = "separator";


  public static final String USAGE
      = "Usage: listversions <path>\n"
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(DELETED, "include delete markers")
      + optusage(DIRS, "include directory markers")
      + optusage(LIMIT, "limit", "limit of files to list")
      + optusage(OUTPUT, "file", "output file")
      + optusage(QUIET, "quiet output")
      + optusage(SEPARATOR, "string", "Separator if not <tab>")
      + optusage(TOKENFILE, "file", "Hadoop token file to load")
      + optusage(VERBOSE, "print verbose output")
      + optusage(XMLFILE, "file", "XML config file to load");

  public static final String NAME = "listversions";

  public ListVersions() {
    createCommandFormat(1, 1,
        DELETED,
        DIRS,
        QUIET,
        VERBOSE);
    addValueOptions(TOKENFILE, XMLFILE, DEFINE, LIMIT, OUTPUT,SEPARATOR);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (paths.size() < 1) {
      errorln(USAGE);
      return E_USAGE;
    }

    final Configuration conf = createPreconfiguredConfig();
    // stop auditing rejecting client direct calls.
    conf.setBoolean(FS_S3A_AUDIT_REJECT_OUT_OF_SPAN_OPERATIONS, false);
    int limit = getOptional(LIMIT).map(Integer::valueOf).orElse(0);
    boolean quiet = hasOption(QUIET);
    boolean logDeleted = hasOption(DELETED);
    boolean logDirs = hasOption(DIRS);


    S3AFileSystem fs = null;
    final Path source = new Path(paths.get(0));
    try (StoreDurationInfo duration = new StoreDurationInfo(LOG,
        NAME)) {
      fs = (S3AFileSystem) source.getFileSystem(conf);

      final AmazonS3 s3 = fs.getAmazonS3ClientForTesting(NAME);
      String key = S3ListingSupport.pathToKey(source);
      ListVersionsRequest request = S3ListingSupport.createListVersionsRequest(
          source.toUri().getHost(), key, null);

      final ListVersionsIterator objects
          = new ListVersionsIterator(s3, source, request);

      int objectCount = 0;
      long totalSize = 0;
      heading("Listing %s", source);
      SummaryWriter writer;


      if (!quiet) {
        final String output = getOption(OUTPUT);
        PrintStream dest;
        boolean closeOutput;
        if (output != null) {
          // writing to a dir
          final Path destPath = new Path(output);
          final FileSystem destFS = destPath.getFileSystem(conf);
          final FSDataOutputStream dataOutputStream = destFS.createFile(destPath)
              .overwrite(true)
              .recursive()
              .build();
          dest = new PrintStream(dataOutputStream);
          closeOutput = true;
          println("Saving output to %s", destPath);
        } else {
          dest = getOut();
          closeOutput = false;
        }
        final String separator = getOptional(SEPARATOR).orElse("\t");
        writer = new CsvVersionWriter(dest, closeOutput, separator);
      } else {
        writer = new SummaryWriter();
      }
      long tombstones = 0;
      long fileTombstones = 0;
      long hidden = 0;
      long hiddenData = 0;
      long hiddenZeroByteFiles = 0;
      long dirMarkers = 0;
      long hiddenDirMarkers = 0;
      try {

        boolean finished = false;
        while (!finished && objects.hasNext()) {
          final VersionListing page = objects.next();
          for (S3VersionSummary summary : page.getVersionSummaries()) {
            objectCount++;
            if (limit > 0 && objectCount > limit) {
              finished = true;
              break;
            } else {
              final long size = summary.getSize();
              totalSize += size;

              final boolean isDirMarker = isDirMarker(summary);
              boolean write = logDirs || !isDirMarker;
              dirMarkers += result(isDirMarker);
              if (summary.isDeleteMarker()) {
                tombstones++;
                fileTombstones += result(!isDirMarker);
                write &= logDeleted;
              } else {
                if (!summary.isLatest()) {
                  if (!isDirMarker) {
                    hidden++;
                    hiddenData += size;
                    hiddenZeroByteFiles += result(size == 0);
                  } else {
                    hiddenDirMarkers++;
                  }
                }
              }
              if (write) {
                writer.write(summary, fs.keyToQualifiedPath(summary.getKey()));
              }
            }
          }
        }
      } finally {
        writer.close();

      }

      println();
      println("Found %,d objects under %s with total size %,d bytes", objectCount, source, totalSize);
      println("Hidden file count %,d with hidden data size %,d bytes",
          hidden, hiddenData);
      println("Hidden zero-byte file count %,d", hiddenZeroByteFiles);
      println("Hidden directory markers %,d", hiddenDirMarkers);
      println("Tombstone entries %,d comprising %,d files and %,d dir markers",
          tombstones, fileTombstones, tombstones - fileTombstones);
      println();

    } finally {
      maybeDumpStorageStatistics(fs);
    }

    return 0;
  }

  private int result(boolean b) {
    return b ? 1 : 0;
  }

  private static class SummaryWriter implements Closeable {

    void write(S3VersionSummary summary, Path path) throws IOException {

    }
    @Override
    public void close() throws IOException {

    }
  }


  /**
   * write to csv; pulled out to make writing to avro etc easier in future.
   */
  private static final class CsvVersionWriter extends SummaryWriter {

    private final SimpleCsvWriter csv;

    private final DateFormat df = new SimpleDateFormat("yyyy-MM-ddZhh:mm:ss");

    long index = 0;

    private CsvVersionWriter(final OutputStream out, final boolean closeOutput, String separator) throws IOException {
      csv = new SimpleCsvWriter(out, separator, "\n", true, closeOutput);
      csv.columns(
          "index",
          "key",
          "path",
          "restore",
          "latest",
          "size",
          "tombstone",
          "directory",
          "date",
          "timestamp",
          "version",
          "etag"
      );
      csv.newline();
    }

    @Override
    public void close() throws IOException {
      csv.close();
    }

    void write(S3VersionSummary summary, Path path) throws IOException {
      final boolean deleteMarker = summary.isDeleteMarker();
      final boolean dirMarker = isDirMarker(summary);
      csv.columnL(++index);
      csv.column(summary.getKey());
      csv.column(path);
      csv.columnB(!deleteMarker && !dirMarker);
      csv.columnB(summary.isLatest());
      csv.columnL(summary.getSize());
      csv.columnB(deleteMarker);
      csv.columnB(dirMarker);
      final Date lastModified = summary.getLastModified();
      csv.column(df.format(lastModified));
      csv.columnL(lastModified.getTime());
      final String versionId = summary.getVersionId();
      csv.column(versionId);
      csv.column(summary.getETag());
      csv.newline();
    }

    private long getIndex() {
      return index;
    }
  }

  private static boolean isDirMarker(final S3VersionSummary summary) {
    return S3ListingSupport.objectRepresentsDirectory(summary.getKey(), summary.getSize());
  }


  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new ListVersions(), args);
  }

  /**
   * Main entry point. Calls {@code System.exit()} on all execution paths.
   * @param args argument list
   */
  public static void main(String[] args) {
    try {
      exit(exec(args), "");
    } catch (Throwable e) {
      exitOnThrowable(e);
    }
  }

}
