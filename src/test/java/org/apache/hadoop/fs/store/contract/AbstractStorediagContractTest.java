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
import static org.apache.hadoop.tools.store.StoreTestUtils.expectSuccess;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.apache.hadoop.fs.contract.AbstractFSContractTestBase;
import org.apache.hadoop.fs.store.diag.StoreDiag;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * Cross-FS contract tests for the {@code storediag} command. Concrete subclasses provide the
 * {@link org.apache.hadoop.fs.contract.AbstractFSContract} that binds the test to a specific
 * filesystem (local, S3A, ABFS, GCS, ...).
 *
 * <p>
 * Each test invokes {@link StoreDiag#exec(String...)} in-process. Cloud-backed subclasses honour
 * the cloudstore convention of skipping when {@code src/test/resources/auth-keys.xml} provides no
 * credentials.
 */
public abstract class AbstractStorediagContractTest extends AbstractFSContractTestBase {

  /**
   * Happy-path: storediag against the test filesystem URI returns 0. Run in {@code -r} (read-only)
   * mode so that no marker files are written.
   */
  @Test
  public void testStorediagSuccess() throws Exception {
    expectSuccess(new StoreDiag(), "-r", getFileSystem().getUri().toString());
  }

  /**
   * Storediag with an empty {@code -required} classes file is still a success: the file is read but
   * yields no classes to probe.
   */
  @Test
  public void testStorediagWithEmptyRequiredFile() throws Exception {
    File required = File.createTempFile("storediag-required", ".txt");
    required.deleteOnExit();
    Files.write(required.toPath(), new byte[0]);
    expectSuccess(new StoreDiag(), "-r", "-required", required.getAbsolutePath(),
        getFileSystem().getUri().toString());
  }

  /**
   * Storediag's captured stdout includes the canonical section headings.
   */
  @Test
  public void testStorediagOutputContainsSections() throws Exception {
    final String captured =
        captureSuccess(new StoreDiag(), "-r", getFileSystem().getUri().toString());
    Assertions.assertThat(captured).as("storediag captured stdout")
        .contains("Store Diagnostics for").contains("Determining OS version").contains("Security")
        .contains("Endpoints").contains("Success!");
  }

  /**
   * Storediag accepts the {@code -reveal} flag (opts into partial obfuscation of sensitive option
   * values; default is full redaction).
   */
  @Test
  public void testStorediagRevealFlag() throws Exception {
    expectSuccess(new StoreDiag(), "-r", "-reveal", getFileSystem().getUri().toString());
  }

  /**
   * Storediag's {@code -logfile} option writes the diagnostics to a file in addition to stdout.
   */
  @Test
  public void testStorediagLogFile() throws Exception {
    File logFile = File.createTempFile("storediag-logfile", ".txt");
    logFile.deleteOnExit();
    final String captured = captureSuccess(new StoreDiag(), "-r", "-logfile",
        logFile.getAbsolutePath(), getFileSystem().getUri().toString());
    Assertions.assertThat(logFile).as("storediag log file").exists();
    final String onDisk = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
    Assertions.assertThat(onDisk).as("storediag log file contents").isNotEmpty()
        .contains("Store Diagnostics for").contains("Success!");
    // captured stdout should also have the same heading
    Assertions.assertThat(captured).contains("Store Diagnostics for");
  }
}
