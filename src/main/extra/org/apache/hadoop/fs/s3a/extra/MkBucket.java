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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.Invoker;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.s3a.Constants.S3A_BUCKET_PROBE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.fs.store.diag.S3ADiagnosticsInfo.FS_S3A_AUDIT_REJECT_OUT_OF_SPAN_OPERATIONS;

/**
 * Create the s3 bucket.
 */
public class MkBucket extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(MkBucket.class);

  public static final String USAGE
      = "Usage: mkbucket <region> <S3A path>";

  public MkBucket() {
    createCommandFormat(2, 2,
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
    conf.setInt(S3A_BUCKET_PROBE, 0);
    conf.setBoolean(FS_S3A_AUDIT_REJECT_OUT_OF_SPAN_OPERATIONS, false);

    final String region = paths.get(0);
    final String bucketPath = paths.get(1);
    final Path source = new Path(bucketPath);
    S3AFileSystem fs = (S3AFileSystem) source.getFileSystem(conf);
    final AmazonS3 client = fs.getAmazonS3ClientForTesting("mkbucket");
    final String bucketName = source.toUri().getHost();
    final CreateBucketRequest request =
        new CreateBucketRequest(bucketName, region);
    Bucket bucket;
    try (StoreDurationInfo ignored = new StoreDurationInfo(LOG,
                "Creating bucket %s", bucketName)) {
      bucket = Invoker.once("delete", source.toString(), () ->
          client.createBucket(request));
    }
    println("Created bucket %s", bucket);


    return 0;
  }

  /**
   * Main entry point. Calls {@code System.exit()} on all execution paths.
   * @param args argument list
   */
  public static void main(String[] args) {
    try {
      exit(ToolRunner.run(new MkBucket(), args), "");
    } catch (Throwable e) {
      exitOnThrowable(e);
    }
  }

}
