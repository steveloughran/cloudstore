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
import org.apache.hadoop.fs.store.PathCapabilityChecker;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.LOGFILE;
import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_ERROR;

/**
 * Look for a path capability.
 */
public class PathCapability extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(PathCapability.class);

  public static final String USAGE
      = "Usage: pathcapability [options] <capability> <path>\n"
      + STANDARD_OPTS;

  public PathCapability() {
    createCommandFormat(2, 2);
    addValueOptions(LOGFILE);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> argList = processArgs(args, 1, -1, USAGE);
    final Configuration conf = createPreconfiguredConfig();

    // path on the CLI
    String capability = argList.get(0);
    String pathString = argList.get(1);
    Path path = new Path(pathString);
    println("Probing %s for capability %s", path, capability);
    FileSystem fs = path.getFileSystem(conf);
    println("Using filesystem %s", fs.getUri());
    Path absPath = path.makeQualified(fs.getUri(), fs.getWorkingDirectory());
    if (new PathCapabilityChecker(fs).
        hasPathCapability(absPath, capability)) {

      println("Path %s has capability %s",
          absPath, capability);
      return 0;
    } else {
      println("Path %s lacks capability %s",
          absPath, capability);
      return E_ERROR;
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
    return ToolRunner.run(new PathCapability(), args);
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
