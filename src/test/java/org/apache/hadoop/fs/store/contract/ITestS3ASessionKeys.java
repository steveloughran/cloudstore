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
import org.apache.hadoop.fs.s3a.sdk.SessionKeys;
import org.apache.hadoop.fs.store.test.S3AStoreContract;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * S3A integration test for the {@code sessionkeys} command. Asserts the command emits all six
 * output formats: XML settings, properties, CLI arguments, Spark, Bash, Fish, and env. TODO: make
 * sure session keys aren't printed on test failures.
 */
public class ITestS3ASessionKeys extends AbstractFSContractTestBase {

  @Override
  protected AbstractFSContract createContract(Configuration conf) {
    return new S3AStoreContract(conf);
  }

  @Test
  public void testSessionKeysEmitsAllFormats() throws Exception {
    final String captured = captureSuccess(new SessionKeys(), getFileSystem().getUri().toString());
    Assertions.assertThat(captured).as("sessionkeys output sections").contains("XML settings")
        .contains("Properties").contains("CLI Arguments").contains("Spark").contains("Bash")
        .contains("Fish").contains("env");
    // XML / Spark / properties section should mention an S3A access key configuration.
    Assertions.assertThat(captured).as("sessionkeys hadoop credential key in output")
        .contains("fs.s3a.access.key");
    // bash/fish/env should mention the AWS env var
    Assertions.assertThat(captured).as("sessionkeys AWS env var in output")
        .contains("AWS_ACCESS_KEY_ID");
  }
}
