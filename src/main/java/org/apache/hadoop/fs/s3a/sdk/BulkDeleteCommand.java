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

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.BulkDelete;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.fs.store.StoreExitCodes;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.statistics.IOStatisticsLogging.ioStatisticsToPrettyString;
import static org.apache.hadoop.fs.statistics.IOStatisticsSupport.retrieveIOStatistics;
import static org.apache.hadoop.fs.store.CommonParameters.PAGE;
import static org.apache.hadoop.fs.store.StoreUtils.readLines;

/**
 * Bulk delete.
 */
public class BulkDeleteCommand extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(BulkDelete.class);

  public static final String USAGE
      = "Usage: bulkdelete [-verbose] [-page <pagesize>] <path> <file>\n"
      + "<file> is a text file with full/relative paths to files under <path>\n"
      + "   Empty lines and lines starting with # are ignored.\n"
      + "   As are root paths of stores\n"
      + "<pagesize> is the page size if less than the store page size ";

  public BulkDeleteCommand() {
    createCommandFormat(2, 2);
    addValueOptions(
        PAGE);
  }

  /**
   * @param args command specific arguments.
   * @return
   * @throws Exception
   */
  @Override
  public int run(String[] args) throws Exception {
    List<String> argList = processArgs(args, 2, 2, USAGE);

    final Configuration conf = createPreconfiguredConfig();

    int pageLimit = getIntOption(PAGE, 0);
    // path on the CLI
    Path path = new Path(argList.get(0));
    heading("Bulk delete under %s", path);

    String filename = argList.get(1);
    File file = new File(filename);
    if (!file.exists()) {
      errorln("File not found \"%s\"", filename);
      return StoreExitCodes.E_NOT_FOUND;
    }

    // get the list of paths to delete
    final List<String> toDelete = readLines(file);
    final int files = toDelete.size();
    println("%d files to delete", files);
    if (files == 0) {
      // no files
      return 0;
    }

    FileSystem fs = path.getFileSystem(conf);
    fs.setWorkingDirectory(new Path("/"));

    final StoreDurationInfo executionDuration = new StoreDurationInfo();

    final List<Path> pathsToDelete = toDelete.stream().map(l ->
            fs.makeQualified(new Path(l)))
        .filter(p -> !p.isRoot())
        .collect(Collectors.toList());

    final BulkDelete deleter = fs.createBulkDelete(path);
    int pageSize = deleter.pageSize();
    println("Store page size = %s", pageSize);
    if (pageLimit > 0 && pageLimit < pageSize) {
      pageSize = pageLimit;
      println("Delete page size = %s", pageSize);
    }
    println();

    int batches = 0;
    int failures = 0;
    for (int i = 0; i < files; i += pageSize) {
      batches++;
      int end = Math.min(files, i + pageSize);
      List<Path> batch = pathsToDelete.subList(i, end);
      batch.forEach(p -> println("  %s", p));

      // delete one batch
      final List<Map.Entry<Path, String>> result = deleter.bulkDelete(batch);

      final int rs = result.size();
      failures += rs;

      result.forEach(entry -> {
        println("   Failed to delete %s: ", entry.getKey(), entry.getValue());
      });

    }

    executionDuration.finished();

    heading("Summary");
    println("Bulk delete of %d file(s) finished, duration: %s",
        files, executionDuration.getDurationString());
    println("Batch count: %d. Failure count: %d",
        batches, failures);
    if (isVerbose()) {
      heading("Statistics");
      println("\nStatistics\n%s",
          ioStatisticsToPrettyString(retrieveIOStatistics(fs)));
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
    return ToolRunner.run(new BulkDeleteCommand(), args);
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
