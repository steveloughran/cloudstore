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
import org.apache.hadoop.fs.s3a.s3guard.BulkOperationState;
import org.apache.hadoop.fs.s3a.s3guard.MetadataStore;
import org.apache.hadoop.fs.s3a.s3guard.S3Guard;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

/**
 * Clean out from S3Guard all entries under a path.
 */
public class CleanS3Guard extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(CleanS3Guard.class);

  public static final String USAGE
      = "Usage: cleans3guard"
      + " <S3A path>";

  public CleanS3Guard() {
    createCommandFormat(1, 1,
        VERBOSE);
  }


  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (paths.size() < 1) {
      errorln(USAGE);
      return E_USAGE;
    }

    final Configuration conf = createPreconfiguredConfig();

    final Path path = new Path(paths.get(0));
    S3AFileSystem fs = (S3AFileSystem) path.getFileSystem(conf);
    if (!fs.hasMetadataStore()) {
      println("S3 bucket %s does not have a S3Guard metadata store", fs.getUri());
      return -1;
    }
    MetadataStore metastore = fs.getMetadataStore();
    println("Removing from S3Guard all entries under %s", path);
    try (BulkOperationState operationState = S3Guard.initiateBulkWrite(
        metastore,
        BulkOperationState.OperationType.Delete,
        path)) {
      metastore.deleteSubtree(path, operationState);
    }
    println("");
    println("S3Guard cleanup completed. To repopulate the directory, run");
    println("");
    println("  hadoop s3guard import %s", path);
    println("");
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
    return ToolRunner.run(new CleanS3Guard(), args);
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
