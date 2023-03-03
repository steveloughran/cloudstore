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

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.fs.store.StoreUtils;
import org.apache.hadoop.util.Progressable;
import org.apache.hadoop.util.ToolRunner;

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

  public static final String USAGE
      = "Usage: bandwidth [options] size <path>\n"
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(TOKENFILE, "file", "Hadoop token file to load")
      + optusage(VERBOSE, "print verbose output")
      + optusage(XMLFILE, "file", "XML config file to load");

  private static final int BUFFER_SIZE = 32 * 1024;

  public static final int UPLOAD_BUFFER_SIZE = MB_1;

  private byte[] dataBuffer;

  public Bandwidth() {
    createCommandFormat(2, 2, VERBOSE);
    addValueOptions(TOKENFILE, XMLFILE, DEFINE);

  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> argList = parseArgs(args);
    if (argList.size() < 1) {
      errorln(USAGE);
      return E_USAGE;
    }
    maybeAddTokens(TOKENFILE);
    final Configuration conf = createPreconfiguredConfig();

    // path on the CLI
    String size = argList.get(0).toLowerCase(Locale.ENGLISH);
    String pathString = argList.get(1);
    Path path = new Path(pathString);
    println("Bandwidth test against %s with data size %s", path, size);

    if (size.endsWith("p") || size.endsWith("t") || size.endsWith("e")) {
      warn("That's going to take a while");
    }
    FileSystem fs = path.getFileSystem(conf);
    println("Using filesystem %s", fs.getUri());

    double uploadSize = StoreUtils.getDataSize(size);

    long sizeMB = Math.round(uploadSize);
    if (sizeMB <= 0) {
      warn("minimum size is 1M");
      sizeMB = 1;
    }
    println("Upload size in Megabytes %,d MB", sizeMB);
    long sizeBytes = sizeMB * MB_1;

    // buffer of randomness
    dataBuffer = new byte[UPLOAD_BUFFER_SIZE];
    new Random().nextBytes(dataBuffer);
    int numberOfBuffersToUpload = (int) sizeMB;

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
    try (StoreDurationInfo d = new StoreDurationInfo(LOG, "Opening %s for upload", path)) {
      upload = fs.createFile(path)
          .progress(progress)
          .recursive()
          .bufferSize(BUFFER_SIZE)
          .overwrite(true)
          .build();
    }
    try {
      // now do the upload
      // println placement is so that progress . entries are on their own line
      for (int i = 0; i < numberOfBuffersToUpload; i++) {
        upload.write(dataBuffer);
        println("%,d ", i);
      }
      println();
      // write all remaining data

      try (StoreDurationInfo d = new StoreDurationInfo(LOG, "upload stream close()")) {
        upload.close();
      }
    } finally {
      printIfVerbose("Upload Stream: %s", upload);
    }
    // upload is done, print some statistics
    uploadDurationTracker.finished();

    // end of upload
    printFSInfoInVerbose(fs);

    // now download
    heading("Download");

    StoreDurationInfo downloadDurationTracker = new StoreDurationInfo();
    FSDataInputStream download;
    try (StoreDurationInfo d = new StoreDurationInfo(LOG, "open %s", path)) {
      // TODO: once we drop CDH6 support, we can move to openFile and set
      // length and read policy
      download = fs.open(path);
    }
    try {
      long pos = 0;
      // now do the download
      for (int i = 0; i < numberOfBuffersToUpload; i++) {
        println("%,d ", i);
        download.readFully(pos, dataBuffer);
        pos += UPLOAD_BUFFER_SIZE;
      }
      println();
      try (StoreDurationInfo d = new StoreDurationInfo(LOG, "download stream close()")) {
        download.close();
      }
    } finally {
      printIfVerbose("Download Stream: %s", download);
    }
    downloadDurationTracker.finished();

    try (StoreDurationInfo d = new StoreDurationInfo(LOG, "delete file %s", path)) {
      fs.delete(path, false);
    }

    // now print both summaries
    summarize("Upload", uploadDurationTracker, sizeBytes);
    summarize("Download", downloadDurationTracker, sizeBytes);

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
