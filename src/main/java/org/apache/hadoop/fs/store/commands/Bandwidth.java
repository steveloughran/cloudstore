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

package org.apache.hadoop.fs.store.commands;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.MinMeanMax;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.fs.store.StoreUtils;
import org.apache.hadoop.fs.tools.csv.CsvWriterWithCRC;
import org.apache.hadoop.util.Progressable;
import org.apache.hadoop.util.ShutdownHookManager;
import org.apache.hadoop.util.ToolRunner;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.apache.hadoop.fs.store.CommonParameters.CSVFILE;
import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

/**
 * Bandwidth test of upload/download capacity.
 */
public class Bandwidth extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(Bandwidth.class);

  public static final String KEEP = "keep";
  public static final String RENAME = "rename";


  public static final String USAGE
      = "Usage: bandwidth [options] size <path>\n"
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(KEEP, "do not delete the file")
      + optusage(RENAME, "rename file to suffix .renamed")
      + optusage(TOKENFILE, "file", "Hadoop token file to load")
      + optusage(VERBOSE, "print verbose output")
      + optusage(XMLFILE, "file", "XML config file to load")
      + optusage(CSVFILE, "file", "CSV file save statistics to load");

  private static final int BUFFER_SIZE = 32 * 1024;

  public static final int UPLOAD_BUFFER_SIZE = MB_1;

  public static final int CLOSE_WARN_THRESHOLD_SECONDS = 60;

  public static final int PRIORITY = 10;

  private static final String EOL = "\r\n";

  private static final String SEPARATOR = ",";

  private byte[] dataBuffer;

  public Bandwidth() {
    createCommandFormat(2, 2, VERBOSE, KEEP, RENAME);
    addValueOptions(TOKENFILE, XMLFILE, DEFINE, CSVFILE);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> argList = parseArgs(args);
    if (argList.size() < 1) {
      errorln(USAGE);
      return E_USAGE;
    }
    maybeAddTokens(TOKENFILE);
    final boolean keep = hasOption(KEEP);
    final boolean rename = hasOption(RENAME);
    final String csvFile = getOption(CSVFILE);

    final Configuration conf = createPreconfiguredConfig();

    final PrintStream out = getOut();
    // path on the CLI
    String size = argList.get(0).toLowerCase(Locale.ENGLISH);
    String pathString = argList.get(1);
    Path uploadPath = new Path(pathString);
    println("Bandwidth test against %s with data size %s", uploadPath, size);
    Path downloadPath = rename
        ? new Path(uploadPath.getParent(), uploadPath.getName() + ".renamed")
        : uploadPath;
    if (keep) {
      println("Retaining file %s", downloadPath);
    }
    if (csvFile != null) {
      println("Saving statistics as CSV data to %s", csvFile);
    }

    if (size.endsWith("p") || size.endsWith("t") || size.endsWith("e")) {
      warn("That's going to take a while");
    }
    FileSystem fs = uploadPath.getFileSystem(conf);
    println("Using filesystem %s", fs.getUri());

    double uploadSize = StoreUtils.getDataSize(size);

    long sizeMB = Math.round(uploadSize);
    if (sizeMB <= 0) {
      warn("minimum size is 1M");
      sizeMB = 1;
    }
    println("Upload size in Megabytes %,d MB", sizeMB);
    long sizeBytes = sizeMB * MB_1;

    CsvWriterWithCRC writer = null;
    Path csvPath = null;
    if (csvFile != null) {
      csvPath = new Path(csvFile);
      FSDataOutputStream upload;

      FileSystem csvFs = csvPath.getFileSystem(conf);

      upload = csvFs.createFile(csvPath)
          .recursive()
          .bufferSize(BUFFER_SIZE)
          .overwrite(true)
          .build();
      writer = new CsvWriterWithCRC(upload, SEPARATOR, EOL, true);

      writer.columns("operation", "bytes", "duration");
      writer.newline();
    }

    // buffer of randomness
    dataBuffer = new byte[UPLOAD_BUFFER_SIZE];
    new Random().nextBytes(dataBuffer);
    int numberOfBuffersToUpload = (int) sizeMB;
    println("Writing data as %d arrays each of size %,d bytes", numberOfBuffersToUpload, UPLOAD_BUFFER_SIZE);

    // progress callback counts #of invocations, and optionally prints a .
    AtomicLong progressCount = new AtomicLong();
    final boolean verbose = isVerbose();
    Progressable progress = () -> {
      progressCount.incrementAndGet();
      if (verbose) {
        print(".");
      }
    };

    // total duration tracker.
    final StoreDurationInfo uploadDurationTracker = new StoreDurationInfo();

    // open the file. track duration
    FSDataOutputStream upload;
    try (StoreDurationInfo d = new StoreDurationInfo(out, "Opening %s for upload", uploadPath)) {
      upload = fs.createFile(uploadPath)
          .progress(progress)
          .recursive()
          .bufferSize(BUFFER_SIZE)
          .overwrite(true)
          .build();
    }
    // set up
    if (!keep) {
      // setup cleanup so if the upload is interrupted,
      // filesystem shutdown may delete the file
      fs.deleteOnExit(uploadPath);
      if (rename) {
        fs.deleteOnExit(downloadPath);
      }
      ShutdownHookManager.get().addShutdownHook(() -> {
        try {
          fs.close();
        } catch (IOException ignored) {

        }
      }, PRIORITY);
    }
    MinMeanMax blockUploads = new MinMeanMax("block write duration");
    StoreDurationInfo closeDuration;
    try {
      // now do the upload
      // println placement is so that progress . entries are on their own line
      for (int i = 0; i < numberOfBuffersToUpload; i++) {
        StoreDurationInfo duration = new StoreDurationInfo();
        print("Write block %,d", i);
        upload.write(dataBuffer);
        duration.finished();
        blockUploads.add(duration.value());
        println(" in %.3f seconds", duration.value() / 1000.0);
        row(writer, "upload", sizeBytes, duration);
      }
      println();

      // close and so write all remaining data
      closeDuration = new StoreDurationInfo(out, "upload stream close()");
      try {
        upload.close();
      } finally {
        closeDuration.close();
      }
      row(writer, "close-upload", sizeBytes, closeDuration);
    } finally {
      printIfVerbose("Upload Stream: %s", upload);
    }
    // upload is done, print some statistics
    uploadDurationTracker.finished();

    // end of upload
    printFSInfoInVerbose(fs);


    Optional<StoreDurationInfo> renameDurationTracker = empty();
    if (rename) {
      heading("Rename");
      final StoreDurationInfo rd = new StoreDurationInfo();
      renameDurationTracker = of(rd);
      try (StoreDurationInfo d = new StoreDurationInfo(out, "rename to %s", downloadPath)) {
        fs.rename(uploadPath, downloadPath);
      }
      rd.finished();
      row(writer, "rename", sizeBytes, rd);
    }
    // now download
    heading("Download " + downloadPath);

    StoreDurationInfo downloadDurationTracker = new StoreDurationInfo();
    FSDataInputStream download;
    StoreDurationInfo openDuration = new StoreDurationInfo(out, "open %s", downloadPath);
    try {
      download = fs.openFile(downloadPath)
          .opt("fs.option.openfile.read.policy", "whole-file")
          .opt("fs.option.openfile.length", Long.toString(sizeBytes))
          .build().get();
    } finally {
      openDuration.finished();
      row(writer, "open", 0, openDuration);
    }
    MinMeanMax blockDownload = new MinMeanMax("block read duration");

    try {
      long pos = 0;
      // now do the download
      for (int i = 0; i < numberOfBuffersToUpload; i++) {
        print("Read block %,d", i);
        StoreDurationInfo duration = new StoreDurationInfo();
        download.readFully(pos, dataBuffer);
        pos += UPLOAD_BUFFER_SIZE;
        duration.finished();
        blockDownload.add(duration.value());
        println(" in %.3f seconds", duration.value() / 1000.0);
        row(writer, "download", sizeBytes, duration);
      }
      println();
      try (StoreDurationInfo d = new StoreDurationInfo(out, "download stream close()")) {
        download.close();
      }
    } finally {
      writer.close();
      printIfVerbose("Download Stream: %s", download);
    }
    downloadDurationTracker.finished();


    if (!keep) {
      try (StoreDurationInfo d = new StoreDurationInfo(out, "delete file %s", uploadPath)) {
        fs.delete(uploadPath, false);
        fs.delete(downloadPath, false);
      }
    }

    // now print summaries
    summarize("Upload", uploadDurationTracker, sizeBytes,
        "Blocks uploaded (ignoring close() overhead):", blockUploads);

    // use close to time for "real" mean block upload time
    println("Close() duration: %s (hour:minute:seconds)", closeDuration.getDurationString());
    println("Mean Upload duration/block including close() overhead %.3f seconds",
        (blockUploads.sum() + uploadDurationTracker.value()) / blockUploads.samples()  / 1000.0);

    // warn on slow close
    final Duration dur = closeDuration.asDuration();
    if (dur.getSeconds() > CLOSE_WARN_THRESHOLD_SECONDS) {
      heading("Close() slow due to data generation/bandwidth mismatch");
      warn("Close took %s seconds", dur.getSeconds());
      warn("This is a sign of a mismatch between data generation and upload bandwidth");
      warn("A long delay in close() can cause problems with applications which do not expect delays");
      warn("Consider limiting the number of blocks which can be queued for upload");
      warn("in the filesystem client's output stream");
    }

    renameDurationTracker.ifPresent(t ->
        summarize("Rename", t, sizeBytes, "", null));
    summarize("Download", downloadDurationTracker, sizeBytes,
        "Blocks downloaded:", blockDownload);

    if (csvPath != null) {
      print("CSV formatted data saved to %s", csvPath);
    }
    return 0;

  }

  private static void row(final CsvWriterWithCRC writer,
      final String a,
      final long sizeCol,
      final StoreDurationInfo dur) throws IOException {
    if (writer != null) {
      writer.column(a)
          .columnL(sizeCol)
          .columnL(dur.value())
          .newline();
    }
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new Bandwidth(), args);
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
