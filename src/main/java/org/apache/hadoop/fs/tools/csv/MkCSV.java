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

package org.apache.hadoop.fs.tools.csv;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.Progressable;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

public class MkCSV extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(MkCSV.class);

  public static final String USAGE
      = "Usage: mkcsv\n"
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(TOKENFILE, "file", "Hadoop token file to load")
      + optusage(XMLFILE, "file", "XML config file to load")
      + optusage(VERBOSE, "print verbose output")
      + "\t<records> <path>";

  private static final int BUFFER_SIZE = 32 * 1024;

  private static final int MB_1 = (1024 * 1024);

  private static final int KB_1 = 1024;

  public static final int UPLOAD_BUFFER_SIZE = MB_1;

  /**
   * number of numeric elements in column 2.
   * Width of the column will be at most ELEMENTS * 5;
   */
  private static final int ELEMENTS = 1000;

  private static final String EOL = "\r\n";

  private static final String SEPARATOR = "\t";

  public MkCSV() {
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
    long rows = Long.parseLong(size);
    if (rows < 0) {
      errorln("Invalid row count %s", size);
      errorln(USAGE);
      return E_USAGE;
    }
    println("Writing CSV file to %s with row count %s", path, rows);
    // create the block data column
    int elements = ELEMENTS;
    StringBuilder sb = new StringBuilder(elements * 5);
    for (int i = 1; i <= elements; i++) {
      sb.append(String.format("%04x-", i));
    }
    // and a final end of block char
    sb.append("!");

    String block = sb.toString();

    // progress callback counts #of invocations, and optionally prints a .
    AtomicLong progressCount = new AtomicLong();
    final boolean verbose = isVerbose();
    Progressable progress = () -> {
      progressCount.incrementAndGet();
      if (verbose) {
        print(".");
      }
    };
    FileSystem fs = path.getFileSystem(conf);
    println("Using filesystem %s", fs.getUri());

    // total duration tracker.
    final StoreDurationInfo
        uploadDurationTracker = new StoreDurationInfo(null, "upload");

    // open the file. track duration
    FSDataOutputStream upload;
    try (StoreDurationInfo d = new StoreDurationInfo(LOG,
        "Opening %s for upload", path)) {
      upload = fs.createFile(path)
          .progress(progress)
          .recursive()
          .bufferSize(BUFFER_SIZE)
          .overwrite(true)
          .build();
    }
    try {
      StoreCsvWriter writer = new StoreCsvWriter(upload, SEPARATOR, EOL);

      for (int r = 1; r <= rows; r++) {
        writer.quote(r);
        // now collect a subset of the value
        int count = r % elements;
        int last = count * 5 + 1;
        writer.column(block.substring(0, last));
        writer.newline();
      }
      // now close the file
      try (StoreDurationInfo d = new StoreDurationInfo(LOG,
          "upload stream close()")) {
        writer.flush();
        writer.close();
      }

    } finally {
      printIfVerbose("Upload Stream: %s", upload);
    }

    println();



    // upload is done, print some statistics
    uploadDurationTracker.finished();
    FileStatus status = fs.getFileStatus(path);

    // end of upload
    printFSInfoInVerbose(fs);

    long sizeBytes = status.getLen();
    summarize("Upload", uploadDurationTracker, sizeBytes);

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
    return ToolRunner.run(new MkCSV(), args);
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
