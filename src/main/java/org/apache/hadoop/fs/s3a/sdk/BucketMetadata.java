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

import java.util.List;

import software.amazon.awssdk.services.s3.model.HeadBucketResponse;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.s3a.S3AInternals;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

/**
 * Debug bucket settings; v2 sdk
 */
public class BucketMetadata extends StoreEntryPoint {

  public static final String USAGE
      = "Usage: bucketmetadata [-debug] <path>";

  public BucketMetadata() {
    createCommandFormat(1, 1);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> argList = processArgs(args, 1, 1, USAGE);
    final Configuration conf = createPreconfiguredConfig();

    // path on the CLI
    Path path = new Path(argList.get(0));
    heading("Getting bucket info for %s", path);
    FileSystem fs = path.getFileSystem(conf);
    if (!(fs instanceof S3AFileSystem)) {
      println("Filesystem for path %s is not an S3A FileSystem %s",
          path, fs);
      return -1;
    }
    S3AFileSystem s3a = (S3AFileSystem) fs;
    final S3AInternals internals = s3a.getS3AInternals();
    final HeadBucketResponse response = internals.getBucketMetadata();

    println("Bucket metadata from S3");
    println(
        "Region %s%nLocation Name %s%nLocation Type %s%n",
        response.bucketRegion(),
        response.bucketLocationName(),
        response.bucketLocationTypeAsString()
    );

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
    return ToolRunner.run(new BucketMetadata(), args);
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
