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

import static org.apache.hadoop.service.launcher.LauncherExitCodes.EXIT_FAIL;
import static org.apache.hadoop.tools.store.StoreTestUtils.expectOutcome;
import static org.apache.hadoop.tools.store.StoreTestUtils.expectSuccess;

import java.util.UUID;
import org.apache.hadoop.fs.contract.AbstractFSContractTestBase;
import org.apache.hadoop.fs.store.commands.PathCapability;
import org.junit.Test;

/**
 * Cross-FS contract tests for the {@code pathcapability} command.
 *
 * <p>
 * Each concrete subclass binds to a filesystem (local, HDFS, S3A, ...) and declares one path
 * capability that filesystem is known to advertise via
 * {@link org.apache.hadoop.fs.PathCapabilities#hasPathCapability}. The base runs a positive probe
 * against that capability and a negative probe against a fabricated capability name guaranteed not
 * to exist anywhere.
 */
public abstract class AbstractPathCapabilityContractTest extends AbstractFSContractTestBase {

  /**
   * A path capability that the test filesystem is known to report as supported. Pick one off the
   * per-FS storediag info — for LocalFS / HDFS this is {@code fs.capability.paths.append}; for
   * object stores it's a store-specific capability such as
   * {@code fs.s3a.capability.directory.listing.inconsistent}.
   */
  protected abstract String knownValidCapability();

  @Test
  public void testPositiveCapability() throws Exception {
    expectSuccess(new PathCapability(), knownValidCapability(),
        getFileSystem().getUri().toString());
  }

  @Test
  public void testNegativeCapability() throws Exception {
    // synthetic name that no filesystem will ever advertise
    String bogus = "fs.cloudstore.test.no-such-capability." + UUID.randomUUID();
    expectOutcome(EXIT_FAIL, new PathCapability(), bogus, getFileSystem().getUri().toString());
  }
}
