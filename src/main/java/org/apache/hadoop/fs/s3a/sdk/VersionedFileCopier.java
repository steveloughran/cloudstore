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

import java.io.Closeable;
import java.io.IOException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.s3a.Invoker;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.s3a.api.RequestFactory;
import org.apache.hadoop.fs.s3a.audit.AuditManagerS3A;
import org.apache.hadoop.fs.s3a.impl.StoreContext;

/**
 * Support for copying versioned files to different locations in the same bucket.
 * This code uses S3Auditing and other @private apis which came with
 * HADOOP-17511. Add an Audit plugin point for S3A auditing/context
 * then constructs a new TransferManager -the one in the s3afs is inaccessible.
 */
public class VersionedFileCopier implements Closeable {

  private final Configuration conf;

  private final S3AFileSystem fs;

  private final RequestFactory requestFactory;

  private final StoreContext storeContext;

  private final AuditManagerS3A auditManager;

  private final S3Client s3;

  private final Invoker invoker;

  public VersionedFileCopier(final S3AFileSystem fs) {
    this.fs = fs;
    this.s3 = fs.getS3AInternals().getAmazonS3Client("VersionedFileCopier");
    this.storeContext = fs.createStoreContext();
    this.invoker = storeContext.getInvoker();
    this.conf = storeContext.getConfiguration();
    this.requestFactory = storeContext.getRequestFactory();
    this.auditManager = fs.getAuditManager();


  }


  public RequestFactory getRequestFactory() {
    return requestFactory;
  }

  @Override
  public void close() throws IOException {
  }

  /**
   * Copy an object.
   * @param sourceKey source
   * @param version source version
   * @param destKey dest in same bucket
   * @return bytes copied
   * @throws IOException failure
   */
  long copy(String sourceKey, String version, String destKey) throws IOException {

    String action = String.format("copy %s @ %s to %s", sourceKey, version, destKey);
    HeadObjectRequest head = requestFactory.newHeadObjectRequestBuilder(sourceKey)
        .versionId(version)
        .build();
    final HeadObjectResponse srcom =
        invoker.retry("HEAD @" + version,
            sourceKey, true, () -> s3.headObject(head));

    CopyObjectRequest copyObjectRequest =
        requestFactory.newCopyObjectRequestBuilder(sourceKey, destKey, srcom)
            .sourceVersionId(version).build();


    CopyObjectResponse response = invoker.retry(action, sourceKey, true, () -> {
      return s3.copyObject(copyObjectRequest);
    });
    return srcom.contentLength();
  }
}
