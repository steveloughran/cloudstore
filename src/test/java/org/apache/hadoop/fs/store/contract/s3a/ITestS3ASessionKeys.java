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

import static org.apache.hadoop.fs.s3a.Constants.SESSION_TOKEN;
import static org.apache.hadoop.tools.store.StoreTestUtils.runAndCapture;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.contract.AbstractFSContract;
import org.apache.hadoop.fs.contract.AbstractFSContractTestBase;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.s3a.sdk.SessionKeys;
import org.apache.hadoop.tools.store.StoreTestUtils.CapturedRun;
import org.assertj.core.api.Assertions;
import org.junit.Assume;
import org.junit.Test;

/**
 * S3A integration test for the {@code sessionkeys} command.
 *
 * <p>
 * The captured output of {@code sessionkeys} contains real session credentials. Assertions in this
 * test use {@link org.assertj.core.api.AbstractAssert#overridingErrorMessage(String, Object...)} to
 * replace AssertJ's default failure message — which would otherwise include the full actual string
 * — with a message that names only the missing token, so a test failure cannot leak credentials
 * into a CI log.
 */
public class ITestS3ASessionKeys extends AbstractFSContractTestBase {

  @Override
  protected AbstractFSContract createContract(Configuration conf) {
    return new S3AStoreContract(conf);
  }

  @Test
  public void testSessionKeysEmitsAllFormats() throws Exception {
    // sessionkeys derives a fresh STS session from long-lived credentials.
    // If the FS is already configured with a session token (i.e. the caller
    // supplied temporary credentials) the STS call to get session credentials
    // fails. Skip rather than fail in that environment.
    final S3AFileSystem fs = (S3AFileSystem) getFileSystem();
    final String bucket = fs.getBucket();
    final Configuration conf = fs.getConf();
    final char[] perBucket = conf.getPassword("fs.s3a.bucket." + bucket + "." + SESSION_TOKEN);
    final char[] global = conf.getPassword(SESSION_TOKEN);
    final boolean hasSessionToken =
        (perBucket != null && perBucket.length > 0) || (global != null && global.length > 0);
    Assume.assumeFalse("FS already uses session credentials; sessionkeys cannot derive new ones",
        hasSessionToken);

    // Use the shortest practical token lifetime (15 minutes) so that any
    // accidental credential leak — into a CI log, a captured stack trace,
    // or a surefire report — self-expires before it can be indexed or
    // exploited. The duration argument also exercises the -duration option.
    final CapturedRun run = runAndCapture(new SessionKeys(),
        "-duration", "15m",
        getFileSystem().getUri().toString());
    Assertions.assertThat(run.exitCode)
        .overridingErrorMessage(
            "sessionkeys exit code != 0 (output suppressed: may contain " + "credentials)")
        .isZero();
    final String captured = run.captured;
    assertContainsHidden(captured, "XML settings");
    assertContainsHidden(captured, "Properties");
    assertContainsHidden(captured, "CLI Arguments");
    assertContainsHidden(captured, "Spark");
    assertContainsHidden(captured, "Bash");
    assertContainsHidden(captured, "Fish");
    assertContainsHidden(captured, "env");
    // hadoop credential key surfaces in xml/properties/spark/cli sections
    assertContainsHidden(captured, "fs.s3a.access.key");
    // bash/fish/env sections surface the AWS env var
    assertContainsHidden(captured, "AWS_ACCESS_KEY_ID");
  }

  private static void assertContainsHidden(String captured, String needle) {
    Assertions.assertThat(captured.contains(needle))
        .overridingErrorMessage("sessionkeys output is missing expected token \"%s\""
            + " (full output suppressed: it contains credentials)", needle)
        .isTrue();
  }
}
