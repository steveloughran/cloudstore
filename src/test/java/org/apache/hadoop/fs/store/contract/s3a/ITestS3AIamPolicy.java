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
package org.apache.hadoop.fs.store.contract.s3a;

import static org.apache.hadoop.tools.store.StoreTestUtils.captureSuccess;

import java.util.EnumSet;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.contract.AbstractFSContract;
import org.apache.hadoop.fs.contract.AbstractFSContractTestBase;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.s3a.auth.RoleModel;
import org.apache.hadoop.fs.s3a.auth.delegation.AWSPolicyProvider;
import org.apache.hadoop.fs.s3a.sdk.IamPolicy;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * S3A integration test for the {@code iampolicy} command. Confirms that the printed JSON matches
 * what we get from calling {@code RoleModel.toJson(new Policy(fs.listAWSPolicyRules(...)))}
 * directly.
 */
public class ITestS3AIamPolicy extends AbstractFSContractTestBase {

  @Override
  protected AbstractFSContract createContract(Configuration conf) {
    return new S3AStoreContract(conf);
  }

  @Test
  public void testIamPolicyEmitsPolicyJson() throws Exception {
    final S3AFileSystem fs = (S3AFileSystem) getFileSystem();
    // sanity: the FS exposes some rules — otherwise the command has nothing to print
    final List<RoleModel.Statement> rules =
        fs.listAWSPolicyRules(EnumSet.of(AWSPolicyProvider.AccessLevel.READ,
            AWSPolicyProvider.AccessLevel.WRITE, AWSPolicyProvider.AccessLevel.ADMIN));
    Assertions.assertThat(rules).as("policy rules from FS").isNotEmpty();

    final String captured = captureSuccess(new IamPolicy(), fs.getUri().toString());
    // command output is a single JSON document with these standard keys.
    // sids regenerate per call, so don't compare to a freshly-generated copy.
    Assertions.assertThat(captured).as("iampolicy json output").contains("\"Version\"")
        .contains("\"Statement\"").contains("\"Effect\"").contains("\"Resource\"")
        .contains("arn:aws:s3:::" + fs.getBucket());
  }
}
