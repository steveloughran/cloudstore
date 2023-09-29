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

package org.apache.hadoop.fs.s3a.extra;


import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

/**
 * Deletes the objects on the command line
 */
public class RestoreObject extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(RestoreObject.class);

  public static final String USAGE
      = "Usage: restore [-verbose] <S3A path> <version> <dest path>";

  public RestoreObject() {
    createCommandFormat(3, 3);
  }


  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (paths.size() != 3) {
      errorln(USAGE);
      return E_USAGE;
    }

    final Configuration conf = createPreconfiguredConfig();

    final Path source = new Path(paths.get(0));
    final String version = paths.get(1);
    final S3AFileSystem fs = (S3AFileSystem) source.getFileSystem(conf);
    final Path src = fs.makeQualified(source);
    final Path dst = fs.makeQualified(new Path(paths.get(2)));
    println("restoring %s @ %s to %s",
        src, version, dst);
    long l;
    try (VersionedFileCopier copier = new VersionedFileCopier(fs);
         StoreDurationInfo d = new StoreDurationInfo(getOut(), "restore")) {
      l = copier.copy(fs.pathToKey(src), version, fs.pathToKey(dst));
    }
    println("Restored object of size %,d bytes to %s%n", l, dst);

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
    return ToolRunner.run(new RestoreObject(), args);
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
