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

import static org.apache.hadoop.fs.store.diag.CapabilityKeys.FS_PERMISSIONS;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.contract.AbstractFSContract;
import org.apache.hadoop.fs.contract.localfs.LocalFSContract;

/**
 * pathcapability run against the local filesystem. Always-on.
 */
public class TestLocalPathCapabilityContract extends AbstractPathCapabilityContractTest {

  @Override
  protected AbstractFSContract createContract(Configuration conf) {
    return new LocalFSContract(conf);
  }

  @Override
  protected String knownValidCapability() {
    // ChecksumFileSystem (the wrapper around RawLocalFileSystem) explicitly
    // blocks FS_APPEND / FS_CONCAT but delegates other capabilities to the
    // underlying raw FS, which advertises FS_PERMISSIONS.
    return FS_PERMISSIONS;
  }
}
