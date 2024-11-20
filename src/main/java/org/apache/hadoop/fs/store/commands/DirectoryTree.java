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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;

import static org.apache.hadoop.fs.store.CommonParameters.LIMIT;
import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.CommonParameters.THREADS;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

/**
 * Directory Tree scan with
 * - output to a text file; one entry per line
 * - obfuscate all dirs by replacing all but first three chars with a "-$number".
 * - some summary info at the end (dirs, depth, time to scan)
 * The purpose is to generate reports which we can then use to generate matching
 * directory structures.
 */
public class DirectoryTree extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(DirectoryTree.class);

  public static final String USAGE
      = "Usage: dux\n"
      + STANDARD_OPTS
      + optusage(THREADS, "threads", "number of threads")
      + optusage(LIMIT, "limit", "limit of files to list")
      + "\t<path>";

  public static final int DEFAULT_THREADS = 8;

  private FileSystem fs;

  public DirectoryTree() {
    createCommandFormat(1, 1);
    addValueOptions(
        THREADS,
        LIMIT);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = processArgs(args, 1, 1, USAGE);
    final Configuration conf = createPreconfiguredConfig();

    int threads = getIntOption(THREADS, DEFAULT_THREADS);

    final Path source = new Path(paths.get(0));
    heading("Deleting __temporary directories under %s with thread count %d",
        source,
        threads);

    final StoreDurationInfo duration = new StoreDurationInfo(LOG,
        "List files under %s", source);
    fs = source.getFileSystem(conf);
    // worker pool
    ExecutorService workers = new ThreadPoolExecutor(threads, threads,
        0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>());

    // now completion service for all outstanding workers
    ExecutorCompletionService<Summary> completion
        = new ExecutorCompletionService<>(workers);
    List<Summary> results = new ArrayList<>();

    return 0;
  }

  /**
   * Summary of a tree scan.
   */
  private static final class Summary implements Comparable<Summary> {

    private final Path path;

    private Summary(final Path path) {
      this.path = path;
    }

    @Override
    public int compareTo(final Summary o) {
      return path.toString().compareTo(o.path.toString());
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("Summary{");
      sb.append("path=").append(path);
      sb.append('}');
      return sb.toString();
    }
  }

}
