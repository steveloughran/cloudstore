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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FSDataOutputStreamBuilder;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.impl.FutureIOSupport.awaitFuture;
import static org.apache.hadoop.fs.statistics.IOStatisticsLogging.ioStatisticsToPrettyString;
import static org.apache.hadoop.fs.store.CommonParameters.OPTIONS;
import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.diag.OptionSets.EnhancedOpenFileOptions.FS_OPTION_OPENFILE_LENGTH;
import static org.apache.hadoop.fs.store.diag.OptionSets.EnhancedOpenFileOptions.FS_OPTION_OPENFILE_READ_POLICY;
import static org.apache.hadoop.fs.store.diag.OptionSets.EnhancedOpenFileOptions.FS_OPTION_OPENFILE_READ_POLICY_WHOLE_FILE;
import static org.apache.hadoop.io.IOUtils.closeStreams;
import static org.apache.hadoop.io.IOUtils.copyBytes;

/**
 * Put a file, allow for an option set of parameters on the put.
 * <p>
 * Prints some performance numbers at the end.
 */
public class Put extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(Put.class);

  public static final String USAGE
      = "Usage: put\n"
      + STANDARD_OPTS
  + optusage(OPTIONS, "property-file", "A property file of createFile options")
      + " <source> <dest>";


  public Put() {
    createCommandFormat(2, 2);
    addValueOptions(OPTIONS);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = processArgs(args, 2, 2, USAGE);
    final Configuration conf = createPreconfiguredConfig();

    final Path source = new Path(paths.get(0));
    final Path dest = new Path(paths.get(1));
    final Optional<String> options = getOptional(OPTIONS);
    FileSystem sourceFs = null;
    FileSystem destFs = null;
    StoreDurationInfo duration = new StoreDurationInfo(LOG,
        "Put %s to %s", source, dest);
    FSDataOutputStream out = null;
    FSDataInputStream in = null;

    try {
      sourceFs = source.getFileSystem(conf);
      destFs = dest.getFileSystem(conf);
      final FileStatus sourceStatus = sourceFs.getFileStatus(source);
      LOG.info("Source file Size: {}", sourceStatus.getLen());
      Properties props = null;
      if (options.isPresent()) {
        final String filename = options.get();
        props = new Properties();
        try (FileInputStream propsIn = new FileInputStream(filename)) {
          props.load(propsIn);
          LOG.info("Loaded {} properties from {}", props.size(), filename);
        } catch (IOException e) {
          LOG.error("Failed to load properties from {}", filename, e);
          throw e;
        }
      }
      final CompletableFuture<FSDataInputStream> future = sourceFs.openFile(source)
          .opt(FS_OPTION_OPENFILE_LENGTH, sourceStatus.getLen())
          .opt(FS_OPTION_OPENFILE_READ_POLICY, FS_OPTION_OPENFILE_READ_POLICY_WHOLE_FILE)
          .build();
      in = awaitFuture(future);
      final FSDataOutputStreamBuilder destBuilder = destFs.createFile(dest);
      if (props != null) {
        props.forEach((k, v) -> destBuilder.opt(k.toString(), v.toString()));
      }
      out = destBuilder.build();
      copyBytes(in, out, 64_000, true);
      println("input statistics: %s", ioStatisticsToPrettyString(in.getIOStatistics()));
      println("output statistics: %s", ioStatisticsToPrettyString(out.getIOStatistics()));

    } finally {
      closeStreams(in, out);
      duration.close();
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
    return ToolRunner.run(new Put(), args);
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
