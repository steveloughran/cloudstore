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

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.GetObjectMetadataRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerConfiguration;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.s3a.Invoker;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.s3a.api.RequestFactory;
import org.apache.hadoop.fs.s3a.audit.AuditManagerS3A;
import org.apache.hadoop.fs.s3a.impl.CopyOutcome;
import org.apache.hadoop.fs.s3a.impl.StoreContext;
import org.apache.hadoop.util.BlockingThreadPoolExecutorService;

import static org.apache.hadoop.fs.s3a.Constants.DEFAULT_KEEPALIVE_TIME;
import static org.apache.hadoop.fs.s3a.Constants.DEFAULT_MAX_THREADS;
import static org.apache.hadoop.fs.s3a.Constants.DEFAULT_MAX_TOTAL_TASKS;
import static org.apache.hadoop.fs.s3a.Constants.DEFAULT_MIN_MULTIPART_THRESHOLD;
import static org.apache.hadoop.fs.s3a.Constants.DEFAULT_MULTIPART_SIZE;
import static org.apache.hadoop.fs.s3a.Constants.KEEPALIVE_TIME;
import static org.apache.hadoop.fs.s3a.Constants.MAX_THREADS;
import static org.apache.hadoop.fs.s3a.Constants.MAX_TOTAL_TASKS;
import static org.apache.hadoop.fs.s3a.Constants.MIN_MULTIPART_THRESHOLD;
import static org.apache.hadoop.fs.s3a.Constants.MULTIPART_SIZE;
import static org.apache.hadoop.fs.s3a.S3AUtils.getMultipartSizeProperty;
import static org.apache.hadoop.fs.s3a.S3AUtils.intOption;
import static org.apache.hadoop.fs.s3a.S3AUtils.longOption;

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

  private final long multiPartThreshold;

  private final long partSize;

  private final StoreContext storeContext;

  private final AuditManagerS3A auditManager;

  private final ThreadPoolExecutor unboundedThreadPool;

  private final AmazonS3 s3;

  private final Invoker invoker;

  private TransferManager transfers;

  public VersionedFileCopier(final S3AFileSystem fs) {
    this.fs = fs;
    this.s3 = fs.getAmazonS3ClientForTesting("VersionedFileCopier");
    this.storeContext = fs.createStoreContext();
    this.invoker = storeContext.getInvoker();
    this.conf = storeContext.getConfiguration();
    this.requestFactory = storeContext.getRequestFactory();
    this.partSize = getMultipartSizeProperty(conf,
        MULTIPART_SIZE, DEFAULT_MULTIPART_SIZE);

    this.multiPartThreshold = getMultipartSizeProperty(conf,
        MIN_MULTIPART_THRESHOLD, DEFAULT_MIN_MULTIPART_THRESHOLD);
    this.auditManager = fs.getAuditManager();

    // initThreadPools
    int maxThreads = conf.getInt(MAX_THREADS, DEFAULT_MAX_THREADS);
    int totalTasks = intOption(conf,
        MAX_TOTAL_TASKS, DEFAULT_MAX_TOTAL_TASKS, 1);
    long keepAliveTime = longOption(conf, KEEPALIVE_TIME,
        DEFAULT_KEEPALIVE_TIME, 0);
    this.unboundedThreadPool = new ThreadPoolExecutor(
        maxThreads, Integer.MAX_VALUE,
        keepAliveTime, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(),
        BlockingThreadPoolExecutorService.newDaemonThreadFactory(
            "VersionedFileCopier-unbounded"));
    unboundedThreadPool.allowCoreThreadTimeOut(true);

    // init transfer manager
    TransferManagerConfiguration transferConfiguration =
        new TransferManagerConfiguration();
    transferConfiguration.setMinimumUploadPartSize(partSize);
    transferConfiguration.setMultipartUploadThreshold(multiPartThreshold);
    transferConfiguration.setMultipartCopyPartSize(partSize);
    transferConfiguration.setMultipartCopyThreshold(multiPartThreshold);

    transfers = new TransferManager(s3, unboundedThreadPool);
    transfers.setConfiguration(transferConfiguration);
  }


  public RequestFactory getRequestFactory() {
    return requestFactory;
  }

  @Override
  public void close() throws IOException {
    transfers.shutdownNow(false);
    unboundedThreadPool.shutdown();

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
    GetObjectMetadataRequest head = requestFactory.newGetObjectMetadataRequest(sourceKey);
    head.withVersionId(version);
    final ObjectMetadata srcom =
        invoker.retry("HEAD @" + version,
            sourceKey, true, () -> s3.getObjectMetadata(head));

    CopyObjectRequest copyObjectRequest =
        requestFactory.newCopyObjectRequest(sourceKey, destKey, srcom);
    copyObjectRequest.withSourceVersionId(version);

    invoker.retry(action, sourceKey, true, () -> {
      CopyOutcome copyOutcome = CopyOutcome.waitForCopy(
          transfers.copy(copyObjectRequest,
              auditManager.createStateChangeListener()));
      if (copyOutcome.getInterruptedException() != null) {
        // copy interrupted: convert to an IOException.
        throw (IOException) new InterruptedIOException(
            "Interrupted during " + action + ", cancelling")
            .initCause(copyOutcome.getInterruptedException());
      }
      if (copyOutcome.getAwsException() != null) {
        throw copyOutcome.getAwsException();
      }
      return copyOutcome.getCopyResult();
    });
    return srcom.getContentLength();
  }
}
