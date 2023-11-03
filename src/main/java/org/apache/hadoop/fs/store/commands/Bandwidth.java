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
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.StorageUnit;
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
import static org.apache.hadoop.fs.store.CommonParameters.BLOCK;
import static org.apache.hadoop.fs.store.CommonParameters.CSVFILE;
import static org.apache.hadoop.fs.store.CommonParameters.FLUSH;
import static org.apache.hadoop.fs.store.CommonParameters.HFLUSH;
import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_INVALID_ARGUMENT;

/**
 * Bandwidth test of upload/download capacity.
 */
public class Bandwidth extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(Bandwidth.class);

  public static final String KEEP = "keep";
  public static final String RENAME = "rename";
  public static final String POLICY = "policy";


  public static final String USAGE
      = "Usage: bandwidth [options] size <path>\n"
      + STANDARD_OPTS
      + optusage(BLOCK, "size", "block size in megabytes")
      + optusage(CSVFILE, "file", "CSV file to log operation details")
      + optusage(FLUSH, "flush the output after writing each block")
      + optusage(HFLUSH, "hflush() the output after writing each block")
      + optusage(KEEP, "do not delete the file")
      + optusage(RENAME, "rename file to suffix .renamed")
      + optusage(POLICY, "policy", "read policy for file (whole-file, sequential, random...)")
      ;

  private static final int BUFFER_SIZE = 32 * 1024;

  public static final int UPLOAD_BUFFER_SIZE_MB = 1;

  public static final int CLOSE_WARN_THRESHOLD_SECONDS = 60;

  public static final int PRIORITY = 10;

  private static final String EOL = "\r\n";

  private static final String SEPARATOR = ",";

  public static final String DEFAULT_READ_POLICY = "whole-file, sequential";

  public static final String DIGEST_ALGORITHM = "SHA-256";

  public Bandwidth() {
    createCommandFormat(2, 2,
        FLUSH,
        HFLUSH,
        KEEP,
        RENAME
    );
    addValueOptions(
        BLOCK,
        CSVFILE,
        POLICY
        );
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> argList = processArgs(args, 1, -1, USAGE);
    final boolean verbose = isVerbose();
    final boolean flush = hasOption(FLUSH);
    final boolean hflush = hasOption(HFLUSH);
    final boolean keep = hasOption(KEEP);
    final boolean rename = hasOption(RENAME);
    final String csvFile = getOption(CSVFILE);
    final String readPolicy = getOption(POLICY, DEFAULT_READ_POLICY)
        .trim()
        .toLowerCase(Locale.ENGLISH);
    String blockSizeStr = getOption(BLOCK, Integer.toString(UPLOAD_BUFFER_SIZE_MB));

    long blockSizeMB = (long) StoreUtils.getDataSize(blockSizeStr, StorageUnit.MB);

    final Configuration conf = createPreconfiguredConfig();

    final PrintStream out = getOut();
    // path on the CLI
    String size = argList.get(0).trim().toLowerCase(Locale.ENGLISH);
    Path uploadPath = new Path(argList.get(1));

    heading("Bandwidth test against %s with data size %s", uploadPath, size);
    println("Block size %d MB", blockSizeMB);
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

    double uploadSize = StoreUtils.getDataSize(size, StorageUnit.MB);

    long sizeMB = Math.round(uploadSize);
    if (sizeMB <= 0) {
      warn("minimum size is 1M");
      sizeMB = 1;
    }
    println("Upload size in Megabytes %,d MB", sizeMB);
    final long fileSizeBytes = sizeMB * MB_1;
    final int blockSize =(int)(blockSizeMB * MB_1);
    if (blockSize <= 0) {
      error("block size MB is invalid", blockSizeMB);
      return E_INVALID_ARGUMENT;
    }

    if (fileSizeBytes < blockSize) {
      error("upload size %,d MB smaller than the block size %,d MB", sizeMB, blockSizeMB);
      return E_INVALID_ARGUMENT;
    }
    int numberOfBuffersToUpload = (int)(sizeMB / blockSizeMB);
    println("Writing data as %,d blocks each of size %,d bytes", numberOfBuffersToUpload,
        blockSize);

    MessageDigest uploadDigest =  MessageDigest.getInstance(DIGEST_ALGORITHM);
    MessageDigest downloadDigest =  MessageDigest.getInstance(DIGEST_ALGORITHM);

    /*
      prepare the CSV output if requested
    */
    CsvWriterWithCRC csvWriter = null;
    Path csvPath = null;
    if (csvFile != null) {
      csvPath = new Path(csvFile);
      FileSystem csvFs = csvPath.getFileSystem(conf);
      FSDataOutputStream upload = csvFs.createFile(csvPath)
          .recursive()
          .bufferSize(BUFFER_SIZE)
          .overwrite(true)
          .build();
      csvWriter = new CsvWriterWithCRC(upload, SEPARATOR, EOL, true);

      csvWriter.columns("operation", "iteration", "bytes", "total bytes", "duration/millis");
      csvWriter.newline();
    }

    // buffer of randomness
    byte[] dataBuffer = new byte[blockSize];
    new Random().nextBytes(dataBuffer);


    // progress callback counts #of invocations
    AtomicLong progressCount = new AtomicLong();
    Progressable progress = () -> {
      progressCount.incrementAndGet();
    };

    // total duration tracker.
    final StoreDurationInfo uploadDurationTracker = new StoreDurationInfo();

    /*
     open the file. track duration
     */
    FSDataOutputStream upload;
    try (StoreDurationInfo d = new StoreDurationInfo(out, "Opening %s for upload", uploadPath)) {
      upload = fs.createFile(uploadPath)
          .progress(progress)
          .recursive()
          .bufferSize(BUFFER_SIZE)
          .overwrite(true)
          .build();
      d.finished();
      row(csvWriter, "create-file", 1, 0, 0, d);
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


    /*
    now do the upload
     */
    MinMeanMax blockUploads = new MinMeanMax("block write duration");
    StoreDurationInfo closeDuration;
    try {
      long total = 0;
      for (int i = 0; i < numberOfBuffersToUpload; i++) {
        StoreDurationInfo duration = new StoreDurationInfo();
        print("Write block %,d", i);
        upload.write(dataBuffer);
        uploadDigest.update(dataBuffer);
        if (flush) {
          upload.flush();
        }
        if (hflush) {
          upload.hflush();
        }
        duration.finished();
        blockUploads.add(duration.value());
        println(" in %.3f seconds", duration.value() / 1000.0);
        row(csvWriter, "upload-block",  i + 1, blockSize, total += blockSize, duration);
      }
      println();

      // close and so write all remaining data
      long progressInUpload = progressCount.get();
      closeDuration = new StoreDurationInfo(out, "upload stream close()");
      try {
        upload.close();
      } finally {
        closeDuration.close();
      }
      row(csvWriter, "close-upload", 1, 0, fileSizeBytes, closeDuration);

      println();
      // print out progress info
      long totalProgress = progressCount.get();
      println("Progress callbacks %d; in close %d",
          totalProgress, totalProgress - progressInUpload);
    } finally {
      printIfVerbose("Upload Stream: %s", upload);
    }
    // upload is done, print some statistics
    uploadDurationTracker.finished();
    row(csvWriter, "upload", 1, blockSize, blockSize, uploadDurationTracker);

    // end of upload
    printFSInfoInVerbose(fs);


    /*
      rename
     */
    Optional<StoreDurationInfo> renameDurationTracker = empty();
    if (rename) {
      heading("Rename");
      final StoreDurationInfo rd = new StoreDurationInfo();
      renameDurationTracker = of(rd);
      try (StoreDurationInfo ignored = new StoreDurationInfo(out, "rename to %s", downloadPath)) {
        fs.rename(uploadPath, downloadPath);
      }
      rd.finished();
      row(csvWriter, "rename", 1, fileSizeBytes, 0, rd);
    }

    /*
      download
     */
    heading("Download %s", downloadPath);

    final StoreDurationInfo downloadDurationTracker = new StoreDurationInfo();
    final FSDataInputStream download;
    final StoreDurationInfo openDuration = new StoreDurationInfo(out, "open %s", downloadPath);
    try {
      download = fs.openFile(downloadPath)
          .opt("fs.option.openfile.read.policy", readPolicy)
          .opt("fs.option.openfile.length", Long.toString(fileSizeBytes))
          .build().get();
    } finally {
      openDuration.finished();
      row(csvWriter, "open-for-download", 1, 0, 0, openDuration);
    }
    final MinMeanMax blockDownload = new MinMeanMax("block read duration");

    try {
      long pos = 0;
      long total = 0;
      // now do the download
      for (int i = 0; i < numberOfBuffersToUpload; i++) {
        print("Read block %,d", i);
        StoreDurationInfo duration = new StoreDurationInfo();
        download.readFully(pos, dataBuffer);
        downloadDigest.update(dataBuffer);

        pos += blockSize;
        duration.finished();
        blockDownload.add(duration.value());
        println(" in %.3f seconds", duration.value() / 1000.0);
        row(csvWriter, "download-block", i + 1, blockSize, total += blockSize, duration);
      }
      println();
      try (StoreDurationInfo d = new StoreDurationInfo(out, "download stream close()")) {
        download.close();
      }
      downloadDurationTracker.finished();
      row(csvWriter, "download", 1, fileSizeBytes, fileSizeBytes, downloadDurationTracker);
    } finally {
      if (csvWriter != null) {
        try {
          csvWriter.flush();
          csvWriter.close();
        } catch (IOException e) {
          errorln("Failed to close CSV write to %s: %s", csvPath, e);
          LOG.debug("Failed to close CSV writer: %s", e);
        }
      }
      printIfVerbose("Download Stream: %s", download);
    }


    /*
      deletion
    */
    if (!keep) {
      try (StoreDurationInfo ignored = new StoreDurationInfo(out, "delete file %s", uploadPath)) {
        fs.delete(uploadPath, false);
        fs.delete(downloadPath, false);
      }
    }

    // now print summaries
    summarize("Upload", uploadDurationTracker, fileSizeBytes,
        "Blocks uploaded (ignoring close() overhead):", blockUploads);

    // use close to time for "real" mean block upload time
    println("Close() duration: %s (minute:seconds)", closeDuration.getDurationString());
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
        summarize("Rename", t, fileSizeBytes, "", null));
    summarize("Download", downloadDurationTracker, fileSizeBytes,
        "Blocks downloaded:", blockDownload);

    int exitCode = 0;

    final byte[] uploadHash = uploadDigest.digest();
    final byte[] downloadHash = downloadDigest.digest();
    if (!Arrays.equals(uploadHash, downloadHash)) {
      errorln("Upload hash does not match download hash: data corrupted!");
      exitCode = -1;
    } else {
      println("Data checksums match: the data has not been corrupted during the test");
    }


    if (csvPath != null) {
      print("CSV formatted data saved to %s", csvPath);
    }

    println();

    return exitCode;

  }

  /**
   * write a row to the CSV file.
   * @param writer file to write to
   * @param a action
   * @param iteration iteration for repeated operations
   * @param opBytes bytes processed in operation
   * @param totalBytes ongoing byte count
   * @param dur duration
   * @throws IOException write failure
   */
  private static void row(
      @Nullable final CsvWriterWithCRC writer,
      final String a,
      final int iteration,
      final long opBytes,
      final long totalBytes,
      final StoreDurationInfo dur) throws IOException {
    if (writer != null) {
      writer.column(a)
          .columnL(iteration)
          .columnL(opBytes)
          .columnL(totalBytes)
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
