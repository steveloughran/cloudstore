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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.fs.store.DurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.mapred.LocatedFileStatusFetcher;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.mapreduce.lib.input.FileInputFormat.LIST_STATUS_NUM_THREADS;

/**
 * Use the mapreduce LocateFiles class
 */
public class LocateFiles extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(LocateFiles.class);

  public static final String THREADS = "threads";

  public static final String USAGE
      = "Usage: locatefiles\n"
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(TOKENFILE, "file", "Hadoop token file to load")
      + optusage(XMLFILE, "file", "XML config file to load")
      + optusage(THREADS, "threads", "number of threads")
      + optusage(VERBOSE, "print verbose output")
      + "[<path>|<pattern>]";

  public static final int DEFAULT_THREADS = 4;

  public LocateFiles() {
    createCommandFormat(1, 1, VERBOSE);
    addValueOptions(TOKENFILE, XMLFILE, DEFINE, THREADS);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (paths.size() != 1) {
      errorln(USAGE);
      return E_USAGE;
    }

    addAllDefaultXMLFiles();
    maybeAddTokens(TOKENFILE);
    final Configuration conf = new Configuration();

    maybeAddXMLFileOption(conf, XMLFILE);
    maybePatchDefined(conf, DEFINE);

    int threads = getOptional(THREADS).map(Integer::valueOf).orElse(2);

    final Path source = new Path(paths.get(0));
    println("");
    heading("Locating files under %s with thread count %d",
        source,
        threads);

    final DurationInfo duration = new DurationInfo(LOG, "List located files");
    final DurationInfo firstLoad = new DurationInfo(LOG,
        "LocateFileStatus execution");
    final AtomicInteger count = new AtomicInteger(0);
    final AtomicLong size = new AtomicLong(0);
    FileSystem fs = source.getFileSystem(conf);
    try {
      Configuration roleConfig = fs.getConf();
      roleConfig.setInt(LIST_STATUS_NUM_THREADS, DEFAULT_THREADS);
      LocatedFileStatusFetcher fetcher =
          new LocatedFileStatusFetcher(
              roleConfig,
              new Path[]{source},
              true,
              HIDDEN_FILE_FILTER,
              true);
      Iterable<FileStatus> statuses = fetcher.getFileStatuses();
      for (FileStatus status : statuses) {
        int c = count.incrementAndGet();
        if (c == 1) {
          firstLoad.close();
        }
        size.addAndGet(status.getLen());
        printStatus(c, status);
        LOG.debug("Status: {}", status);
      }
    } finally {
      duration.close();
    }
    long files = count.get();
    double millisPerFile = files > 0 ? (((float)duration.value()) / files) : 0;
    long totalSize = size.get();
    long bytesPerFile = (files > 0 ? totalSize / files : 0);
    println("");
    println("Found %s files, %,.0f milliseconds per file",
        files, millisPerFile);
    println("Data size %,d bytes, %,d bytes per file",
        totalSize, bytesPerFile);
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
    return ToolRunner.run(new LocateFiles(), args);
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

  private static final PathFilter HIDDEN_FILE_FILTER =
      (p) -> {
        String n = p.getName();
        return !n.startsWith("_") && !n.startsWith(".");
      };
}
