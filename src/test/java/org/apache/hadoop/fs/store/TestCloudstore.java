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
package org.apache.hadoop.fs.store;

import static org.apache.hadoop.service.launcher.LauncherExitCodes.EXIT_USAGE;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.fs.store.commands.LocalHost;
import org.junit.Test;

/**
 * Unit tests for the {@link Cloudstore} dispatcher. No credentials needed.
 */
public class TestCloudstore {

  @Test
  public void testNoArgsIsUsageError() throws Exception {
    assertThat(Cloudstore.exec()).isEqualTo(EXIT_USAGE);
  }

  @Test
  public void testUnknownCommandIsUsageError() throws Exception {
    assertThat(Cloudstore.exec("no-such-command-name")).isEqualTo(EXIT_USAGE);
  }

  @Test
  public void testHelpReturnsZero() throws Exception {
    assertThat(Cloudstore.exec("help")).isEqualTo(0);
  }

  @Test
  public void testHelpFlagsAlsoWork() throws Exception {
    assertThat(Cloudstore.exec("-help")).isEqualTo(0);
    assertThat(Cloudstore.exec("--help")).isEqualTo(0);
  }

  @Test
  public void testRegistryCoversCoreCommands() {
    assertThat(Cloudstore.commands()).containsKeys("dux", "list", "storediag", "cloudup", "tlsinfo",
        "pathcapability");
  }

  /** Dispatch a known command end-to-end. {@code localhost} takes no args and exits 0. */
  @Test
  public void testDispatchToLocalHost() throws Exception {
    // sanity check that the registered class is what we expect
    assertThat(Cloudstore.commands().get("localhost").tool).isEqualTo(LocalHost.class);
    assertThat(Cloudstore.exec("localhost")).isEqualTo(0);
  }
}
