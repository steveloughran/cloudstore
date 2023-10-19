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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.StreamCapabilities;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.TaskID;
import org.apache.hadoop.mapreduce.lib.output.PathOutputCommitter;
import org.apache.hadoop.mapreduce.lib.output.PathOutputCommitterFactory;
import org.apache.hadoop.mapreduce.task.TaskAttemptContextImpl;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.DEBUG;
import static org.apache.hadoop.fs.store.CommonParameters.DEBUG_USAGE;
import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

/**
 * CommitterInfo.
 * Finds out about what committer is in use for a path
 *
 * Prints some performance numbers at the end.
 */
@SuppressWarnings("InstanceofIncompatibleInterface")
public class CommitterInfo extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(CommitterInfo.class);

  public static final String USAGE
      = "Usage: committerinfo\n"
      + STANDARD_OPTS
      + " <path>";

  public CommitterInfo() {
    createCommandFormat(1, 999);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = processArgs(args, 1, 1, USAGE);
    final Configuration conf = createPreconfiguredConfig();

    final Path source = new Path(paths.get(0));

    try (StoreDurationInfo ignored = new StoreDurationInfo(LOG, "Create committer")) {
      FileSystem fs = source.getFileSystem(conf);
      Configuration fsConf = fs.getConf();
      PathOutputCommitterFactory factory
          = PathOutputCommitterFactory.getCommitterFactory(source, fsConf);
      println("Committer factory for path %s is \n" +
              " %s\n" +
              " (classname %s)",
          source, factory, factory.getClass().getCanonicalName());
      PathOutputCommitter committer = factory.createOutputCommitter(
          source,
          new TaskAttemptContextImpl(fsConf,
              new TaskAttemptID(new TaskID(), 1)));
      println("Created committer of class\n" +
              " %s:\n" +
              " %s",
          committer.getClass().getCanonicalName(), committer);
      if (committer instanceof StreamCapabilities
        && ((StreamCapabilities) committer).hasCapability(
                    "mapreduce.job.committer.dynamic.partitioning")) {
          println("Committer declares support for spark dynamic partitioning");
      }
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
    return ToolRunner.run(new CommitterInfo(), args);
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
