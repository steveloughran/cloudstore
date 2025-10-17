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

import java.io.FileNotFoundException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.EtagSource;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_NOT_FOUND;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_SERVICE_UNAVAILABLE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_UNIMPLEMENTED;

/**
 * Print the status.
 * <p>
 * Prints some performance numbers at the end.
 */
public class EtagCommand extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(EtagCommand.class);

  public static final String USAGE
      = "Usage: etag\n"
      + STANDARD_OPTS
      + " <path>";


  public EtagCommand() {
    createCommandFormat(1, 999);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = processArgs(args, 1, 1, USAGE);
    final Configuration conf = createPreconfiguredConfig();

    final Path source = new Path(paths.get(0));
    FileSystem fs = source.getFileSystem(conf);
    FileStatus st = null;

    try (StoreDurationInfo duration = new StoreDurationInfo(LOG,
        "get path status for %s", source)) {
      st = fs.getFileStatus(source);
    } catch (FileNotFoundException e) {
      throw new ExitUtil.ExitException(E_NOT_FOUND, "Not found: " + source, e);
    }

    if (st instanceof EtagSource) {
      final String etag = ((EtagSource) st).getEtag();
      println("Etag of %s = %s", source, etag);
      if (etag == null) {
        errorln("File status of path %s is an EtagSource but the value is null:\n%s", source, st);
        throw new ExitUtil.ExitException(E_SERVICE_UNAVAILABLE, "Etag is null");
      }
      if (etag.isEmpty()) {
        errorln("File status of path %s is an EtagSource but the value is the empty string:\n%s",
            source, st);
        throw new ExitUtil.ExitException(E_SERVICE_UNAVAILABLE, "Etag is empty string");
      }

    } else {
      errorln("File status of path %s is not an EtagSource:\n%s", source, st);
      throw new ExitUtil.ExitException(E_UNIMPLEMENTED,
          "Filesystem does not provide Etag information");
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
    return ToolRunner.run(new EtagCommand(), args);
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
