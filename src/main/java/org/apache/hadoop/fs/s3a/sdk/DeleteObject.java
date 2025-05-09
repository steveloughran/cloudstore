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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.Invoker;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

/**
 * Deletes the objects on the command line
 */
public class DeleteObject extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(DeleteObject.class);

  public static final String USAGE
      = "Usage: deleteobject <S3A path>";

  public DeleteObject() {
    createCommandFormat(1, 1);
  }


  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = processArgs(args, 1, 1, USAGE);
    final Configuration conf = createPreconfiguredConfig();

    final Path source = new Path(paths.get(0));
    S3AFileSystem fs = (S3AFileSystem) source.getFileSystem(conf);
    S3Client s3 = fs.getS3AInternals().getAmazonS3Client("DeleteObjects");
    Invoker.once("delete", source.toString(), () ->
        s3.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(fs.getBucket())
                .key(fs.pathToKey(source))
                .build()));
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
    return ToolRunner.run(new DeleteObject(), args);
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
