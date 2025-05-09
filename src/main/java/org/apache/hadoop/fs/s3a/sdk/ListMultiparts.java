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

package org.apache.hadoop.fs.s3a.sdk;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.MultipartUpload;
import software.amazon.awssdk.services.s3.model.Part;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.fs.tools.csv.SimpleCsvWriter;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.LIMIT;
import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.fs.store.diag.S3ADiagnosticsInfo.FS_S3A_AUDIT_REJECT_OUT_OF_SPAN_OPERATIONS;

public class ListMultiparts extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(ListMultiparts.class);

  public static final String AGE = "age";

  public static final String DIRS = "dirs";

  public static final String OUTPUT = "out";

  public static final String QUIET = "q";

  public static final String SEPARATOR = "separator";

  public static final String SINCE = "since";


  public static final String USAGE
      = "Usage: listversions <path>\n"
      + STANDARD_OPTS
      + optusage(LIMIT, "limit", "limit of files to list")
      + optusage(OUTPUT, "file", "output file")
      + optusage(QUIET, "quiet output")
      + optusage(SEPARATOR, "string", "Separator if not <tab>")
      + optusage(AGE, "seconds", "Only include versions created in this time interval")
      + optusage(SINCE, "epoch-time", "Only include versions after this time");

  public static final String NAME = "listversions";

  public ListMultiparts() {
    createCommandFormat(1, 1,
        QUIET);
    addValueOptions(
        LIMIT,
        OUTPUT,
        SEPARATOR,
        AGE,
        SINCE
    );
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
    int limit = getIntOption(LIMIT, 0);
    boolean logDirs = hasOption(DIRS);
    long since = getLongOption(SINCE, 0);

    final Optional<Long> age = getOptionalLong(AGE);
    if (age.isPresent()) {
      if (since > 0) {
        errorln("Only one of " + AGE + " and " + SINCE + " may be specified");
        return E_USAGE;
      }
      since = Instant.now().minusSeconds(age.get()).getEpochSecond();
    }
    Instant ageLimit = Instant.ofEpochSecond(since);

    S3AFileSystem fs = null;
    final Path source = new Path(paths.get(0));
    final String name = NAME;
    fs = (S3AFileSystem) source.getFileSystem(conf);
    try (StoreDurationInfo duration = new StoreDurationInfo(getOut(), name)) {
      SummaryProcessor processor;


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

      final SimpleCsvWriter csv = new SimpleCsvWriter(dest, separator, "\n", true, closeOutput);

      final DateFormat df = new SimpleDateFormat("yyyy-MM-ddZhh:mm:ss");
      final RemoteIterator<MultipartUpload> uploads = fs.listUploads(fs.pathToKey(source));
      final MultipartProcessor multipartProcessor = new MultipartProcessor(fs);

      int entries = 0;
      csv.columns(
          "index",
          "key",
          "size",
          "blocks",
          "date",
          "id");
      csv.newline();
      long totalParts = 0;
      long totalSize = 0;
      while (uploads.hasNext()) {
        final MultipartUpload upload = uploads.next();
        long bytes = 0;
        long parts = 0;
        final String key = upload.key();
        final MultipartProcessor.PartIterator listing =
            multipartProcessor.partListing(key, upload.uploadId());
        while (listing.hasNext()) {
          final ListPartsResponse part = listing.next();
          for (Part summary : part.parts()) {
            parts++;
            bytes += summary.size();
          }
          csv.columns(++entries, key);
          csv.columnL(bytes);
          csv.columnL(parts);
          csv.column(df.format(upload.initiated()));
          csv.column(upload.uploadId());
          totalParts += parts;
          totalSize += bytes;

        }
      }


      println();
      println("Found %,d uploads under %s with total size %,d bytes in %,d parts",
          entries, source, totalSize, totalParts);
      println();

    } finally {

      maybeDumpStorageStatistics(fs);
    }

    return 0;
  }


  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new ListMultiparts(), args);
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
