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
package org.apache.hadoop.fs.store.contract;

import static org.apache.hadoop.tools.store.StoreTestUtils.captureSuccess;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.contract.AbstractFSContract;
import org.apache.hadoop.fs.contract.AbstractFSContractTestBase;
import org.apache.hadoop.fs.store.commands.BucketState;
import org.apache.hadoop.fs.store.test.S3AStoreContract;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * S3A integration test for the {@code bucketstate} command. Exercises the one happy-path output: a
 * bucket policy is either present (multiline JSON), absent ("NONE"), or unreadable
 * ("Access-Denied").
 */
public class ITestS3ABucketState extends AbstractFSContractTestBase {

  @Override
  protected AbstractFSContract createContract(Configuration conf) {
    return new S3AStoreContract(conf);
  }

  @Test
  public void testBucketStatePrintsPolicy() throws Exception {
    final String captured = captureSuccess(new BucketState(), getFileSystem().getUri().toString());
    Assertions.assertThat(captured).as("bucketstate output").contains("Bucket policy:");
    // one of the three documented outcomes: NONE, Access-Denied, or
    // a multiline policy beginning on the next line.
    Assertions.assertThat(captured).containsPattern("Bucket policy: (NONE|Access-Denied|\\n)");
  }
}
