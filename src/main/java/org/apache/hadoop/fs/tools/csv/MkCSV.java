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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

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
import static org.apache.hadoop.fs.store.StoreUtils.getDataSize;

/**
 * Create a large CSV file for validation.
 */
public class MkCSV extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(MkCSV.class);

  public static final String HEADER = "header";

  public static final String QUOTE = "quote";

  public static final String USAGE
      = "Usage: mkcsv\n"
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(TOKENFILE, "file", "Hadoop token file to load")
      + optusage(XMLFILE, "file", "XML config file to load")
      + optusage(VERBOSE, "print verbose output")
      + optusage(HEADER, "print a header row")
      + optusage(QUOTE, "quote column text")
      + "\t<records> <path>";

  private static final int BUFFER_SIZE = 32 * 1024;

  private static final int MB_1 = (1024 * 1024);

  private static final int KB_1 = 1024;

  public static final int UPLOAD_BUFFER_SIZE = MB_1;

  /**
   * number of numeric elements in column 2.
   * Width of the column will be at most ELEMENTS * 5;
   */
  private static final int ELEMENTS = 100;

  private static final String EOL = "\r\n";

  private static final String SEPARATOR = ",";

  public static final String START = "start";

  public static final String END = "end";


  public MkCSV() {
    createCommandFormat(2, 2, VERBOSE, HEADER, QUOTE);
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
    long rows = (long) getDataSize(size);
    if (rows < 0) {
      errorln("Invalid row count %s", size);
      errorln(USAGE);
      return E_USAGE;
    }
    boolean header = hasOption(HEADER);
    boolean quote = hasOption(QUOTE);
    final boolean verbose = isVerbose();

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

    final List<String> blockData = new ArrayList<>();
    blockRows(blockData, 'a', 'z', elements);
    blockRows(blockData, 'A', 'Z', elements);
    blockRows(blockData, '0', '9', elements);
    final int blockCount = blockData.size();


    // progress callback counts #of invocations, and optionally prints a .
    AtomicLong progressCount = new AtomicLong();
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
        uploadDurationTracker = new StoreDurationInfo();

    // open the file. track duration
    FSDataOutputStream upload;
    try (StoreDurationInfo d = new StoreDurationInfo(LOG,
        "Opening %s for writing", path)) {
      upload = fs.createFile(path)
          .progress(progress)
          .recursive()
          .bufferSize(BUFFER_SIZE)
          .overwrite(true)
          .build();
    }
    try {
      CsvWriterWithCRC writer = new CsvWriterWithCRC(upload, SEPARATOR, EOL, quote);
      if (header) {
        writer
            .columns(START, "rowId", "length", "dataCrc", "data", "rowId2", "rowCrc", END);
        writer.newline();
      }

      Random rand = new Random();
      for (int r = 1; r <= rows; r++) {
        writer.resetRowCrc();
        writer.column(START);
        String rowId = Long.toString(r);
        writer.column(rowId);
        // now collect a subset of the value
        int lastElt = 2 + rand.nextInt(elements);
        String dataRow = blockData.get(r % blockCount);
        int length = Math.min(lastElt, elements);
        String data = dataRow.substring(length);
        writer.columnL(data.length());
        // data CRC
        CRC32 crc = new CRC32();
        crc.update(data.getBytes(StandardCharsets.UTF_8));
        writer.columnL(crc.getValue());
        writer.column(data);
        // repeat the row ID
        writer.column(rowId);
        // full row checksum
        writer.columnL(writer.getRowCrc());
        // end of row
        writer.column(END);
        writer.newline();
      }
      // now close the file
      try (StoreDurationInfo d = new StoreDurationInfo(LOG,
          "upload stream close()")) {
        writer.flush();
        writer.close();
      }

    } finally {
      printIfVerbose("Write Stream: %s", upload);
    }

    println();


    // upload is done, print some statistics
    uploadDurationTracker.finished();
    FileStatus status = fs.getFileStatus(path);

    // end of upload
    printFSInfoInVerbose(fs);

    long sizeBytes = status.getLen();
    summarize("CSV Generation", uploadDurationTracker, sizeBytes);

    return 0;

  }

  /**
   * Generate a row from a string
   * @param s string to use
   * @param elements number of elements
   * @return string of s repeated elements times.
   */
  private String blockRow(String s, int elements) {
    StringBuilder sb = new StringBuilder(elements);
    for (int i = 1; i <= elements; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  private void blockRows(List<String> rows, char start, char end, int elements) {
    for (char i = start; i <= end; i++) {
      rows.add(blockRow(Character.toString(i), elements));
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
