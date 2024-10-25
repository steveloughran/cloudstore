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

import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;
import static org.apache.hadoop.fs.store.CommonParameters.LIMIT;
import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

/**
 * ListFiles recursive scan of a directory tree, with some
 * size limiting to avoid going overboard on a superdeep tree.
 *
 * Prints some performance numbers at the end.
 */
public class ListFiles extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(ListFiles.class);


  public static final String USAGE
      = "Usage: list <path>\n"
      + STANDARD_OPTS
      + optusage(LIMIT, "limit", "limit of files to list")
      ;

  public ListFiles() {
    createCommandFormat(1, 1);
    addValueOptions(
        LIMIT);}

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = processArgs(args, 1, 1, USAGE);

    final Configuration conf = createPreconfiguredConfig();

    int limit = getOptional(LIMIT).map(Integer::valueOf).orElse(0);

    final Path source = new Path(paths.get(0));
    heading("Listing%s files under %s",
        limit == 0 ? "" : (" up to " + limit),
        source);

    final StoreDurationInfo duration = new StoreDurationInfo(LOG, "Directory list");
    final StoreDurationInfo firstLoad = new StoreDurationInfo(LOG, "First listing");
    final AtomicInteger count = new AtomicInteger(0);
    final AtomicLong size = new AtomicLong(0);
    FileSystem fs = source.getFileSystem(conf);
    // track the largest file
    LocatedFileStatus largestFile = null;
    final RemoteIterator<LocatedFileStatus> lister = fs.listFiles(source,
        true);
    try {
      while (lister.hasNext()) {
        final LocatedFileStatus status = lister.next();
        int c = count.incrementAndGet();
        if (c == 1) {
          firstLoad.close();
        }
        final long len = status.getLen();
        size.addAndGet(len);
        printStatus(c, status);
        if (largestFile == null || len > largestFile.getLen()) {
          largestFile = status;
        }
        if (limit > 0 && c >= limit) {
          throw new LimitReachedException();
        }
      }
    } catch (InterruptedIOException | RejectedExecutionException interrupted) {
      println("Interrupted");
      LOG.debug("Interrupted", interrupted);
    } catch (LimitReachedException expected) {

      // the limit has been reached

    } finally {
      duration.close();
      printIfVerbose("List iterator: %s", lister);
      maybeClose(lister);
    }
    long files = count.get();
    double millisPerFile = files > 0 ? (((float) duration.value()) / files) : 0;
    long totalSize = size.get();
    long bytesPerFile = (files > 0 ? totalSize / files : 0);
    println("");
    println("Found %s files, %,.0f milliseconds per file",
        files, millisPerFile);
    println("Data size %s (%,d bytes)",
        byteCountToDisplaySize(totalSize),
        totalSize);
    println("Mean file size %s (%,d bytes)",
        byteCountToDisplaySize(bytesPerFile), bytesPerFile);
    if (largestFile != null) {
      println("Largest file: %s: size %s",
          largestFile.getPath(),
          byteCountToDisplaySize(largestFile.getLen()));
    }
    maybeDumpStorageStatistics(fs);
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
    return ToolRunner.run(new ListFiles(), args);
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
