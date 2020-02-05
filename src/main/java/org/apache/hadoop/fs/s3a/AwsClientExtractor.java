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

package org.apache.hadoop.fs.s3a;

import java.io.IOException;
import java.lang.reflect.Method;

import com.amazonaws.services.s3.AmazonS3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.s3a.debug.InternalS3ClientFactory;

public class AwsClientExtractor {

  private static final Logger LOG = LoggerFactory.getLogger(
      AwsClientExtractor.class);

  public static AmazonS3 extractAwsClient(FileSystem fs) {
    S3AFileSystem s3a = (S3AFileSystem) fs;
    try {
      // try the later version
      final Method method27 = S3AFileSystem.class.getMethod(
          "getAmazonS3ClientForTesting", String.class);
      return (AmazonS3) method27.invoke(fs, "diagnostics");
    } catch (Exception e) {
      LOG.info(
          "Hadoop 3.3 getAmazonS3ClientForTesting() method not found; falling back");
      LOG.debug("falling back", e);
    }
    try {
      final Method method27 = S3AFileSystem.class.getDeclaredMethod(
          "getAmazonS3Client");
      return (AmazonS3) method27.invoke(fs);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to get AWS Client: from " + fs, e);
    }
  }

  public static AmazonS3 createAwsClient(FileSystem fs) throws IOException {
    final InternalS3ClientFactory factory
        = new InternalS3ClientFactory(fs.getConf());
    return factory.createS3Client(fs.getUri());
  }
}
