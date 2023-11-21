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

import com.amazonaws.services.s3.AmazonS3;

import org.apache.hadoop.fs.s3a.S3AFileSystem;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Internal accessor to S3 state
 */
public class InternalAccess {

  private final S3AFileSystem filesystem;

  public InternalAccess(final S3AFileSystem filesystem) {
    this.filesystem = checkNotNull(filesystem);
  }

  /**
   * Returns the S3 client used by this filesystem.
   * This is for internal use within the S3A code itself.
   * @return AmazonS3Client
   */
  public AmazonS3 getAmazonS3Client() {
    return filesystem.getAmazonS3ClientForTesting("Diagnostics");
  }
}
