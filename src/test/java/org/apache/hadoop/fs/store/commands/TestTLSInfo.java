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
package org.apache.hadoop.fs.store.commands;

import static org.apache.hadoop.tools.store.StoreTestUtils.expectExitException;
import static org.apache.hadoop.tools.store.StoreTestUtils.expectSuccess;

import org.apache.hadoop.fs.shell.CommandFormat;
import org.apache.hadoop.service.launcher.LauncherExitCodes;
import org.apache.hadoop.test.LambdaTestUtils;
import org.apache.hadoop.tools.store.StoreTestUtils;
import org.junit.Test;

/**
 * Unit tests for {@link TLSInfo}.
 *
 * <p>
 * TLSInfo reads the JVM's default trust manager, so these tests run anywhere a JDK is installed: no
 * cloud credentials, no network. Surefire-only.
 */
public class TestTLSInfo {

  /** No arguments: dump full trust store info, expect success. */
  @Test
  public void testRunWithNoArgs() throws Exception {
    expectSuccess(new TLSInfo());
  }

  /** -verbose: same as no-args, just more output. */
  @Test
  public void testRunVerbose() throws Exception {
    expectSuccess(new TLSInfo(), "-verbose");
  }

  /**
   * Match argument matching at least one well-known root CA — every JDK trust store ships at least
   * one cert whose subject contains "CA" (case-insensitive), so this is portable across vendors.
   */
  @Test
  public void testMatchHitsAtLeastOneCertificate() throws Exception {
    expectSuccess(new TLSInfo(), "ca");
  }

  /**
   * Match a string that should not appear in any cert. {@link TLSInfo} returns {@code -1} (==
   * {@link LauncherExitCodes#EXIT_FAIL}) when an alias filter matches nothing.
   */
  @Test
  public void testNoMatchReturnsError() throws Exception {
    StoreTestUtils.expectOutcome(LauncherExitCodes.EXIT_FAIL, new TLSInfo(),
        "match-string-that-does-not-appear-in-any-trusted-cert-" + System.nanoTime());
  }

  /** Two positional args: caught by CommandFormat before the run() body. */
  @Test
  public void testTooManyArgs() throws Exception {
    LambdaTestUtils.intercept(CommandFormat.TooManyArgumentsException.class,
        () -> TLSInfo.exec("first", "second", "third"));
  }

  /** Exec entry point exits cleanly on the no-arg happy path. */
  @Test
  public void testExecNoArgsReturnsZero() throws Exception {
    // sanity check that the static exec wrapper behaves the same as
    // direct ToolRunner.run, mirroring TestConstval.testExecNoArgs.
    org.assertj.core.api.Assertions.assertThat(TLSInfo.exec()).isEqualTo(0);
  }

  /**
   * Unknown option -- caught by CommandFormat before run() runs. Mirrors the pattern in
   * TestStoreDiagInvocations#testStorediagTooManyArgs.
   */
  @Test
  public void testUnknownOption() throws Exception {
    expectExitException(LauncherExitCodes.EXIT_FAIL, () -> {
      try {
        return TLSInfo.exec("--no-such-flag");
      } catch (CommandFormat.UnknownOptionException uoe) {
        // map UnknownOption to the same exit code Cloudstore.exitOnThrowable
        // would use, so the assertion stays in the ExitException family.
        throw new org.apache.hadoop.util.ExitUtil.ExitException(LauncherExitCodes.EXIT_FAIL,
            uoe.getMessage());
      }
    });
  }
}
