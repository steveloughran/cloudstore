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

import static org.apache.hadoop.fs.contract.ContractTestUtils.touch;
import static org.apache.hadoop.tools.store.StoreTestUtils.captureSuccess;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.contract.AbstractFSContractTestBase;
import org.apache.hadoop.fs.s3a.sdk.ListObjects;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * Cross-FS contract test for the {@code listobjects} command. Walks a single directory tree through
 * listing, marker purge and full delete in one pass to keep S3 setup overhead low.
 */
public abstract class AbstractListObjectsContractTest extends AbstractFSContractTestBase {

  @Test
  public void testListPurgeDelete() throws Exception {
    final Path parent = methodPath();
    final Path child = new Path(parent, "child");
    final FileSystem fs = getFileSystem();
    fs.mkdirs(parent);
    fs.mkdirs(child);
    final Path file1 = new Path(parent, "file1.txt");
    final Path file2 = new Path(parent, "file2.txt");
    final Path file3 = new Path(child, "file3.txt");
    touch(fs, file1);
    touch(fs, file2);
    touch(fs, file3);

    // 1. Initial listing finds all files.
    final String l1 = captureSuccess(new ListObjects(), parent.toUri().toString());
    Assertions.assertThat(l1).as("initial listing").contains("Listing Objects under " + parent)
        .contains("file1.txt").contains("file2.txt").contains("file3.txt").contains("Found ");

    // 2. Purge directory markers. A pass with -purge writes the
    // "directory markers will be purged" line; markers may be zero on
    // connectors which never created them, in which case the heading
    // "No markers found to purge" appears.
    final String l2 = captureSuccess(new ListObjects(), "-purge", parent.toUri().toString());
    Assertions.assertThat(l2).as("purge run").contains("directory markers will be purged");

    // 3. Files survived the purge.
    final String l3 = captureSuccess(new ListObjects(), parent.toUri().toString());
    Assertions.assertThat(l3).as("listing after purge").contains("file1.txt").contains("file2.txt")
        .contains("file3.txt");

    // 4. -delete removes everything under the parent.
    final String l4 = captureSuccess(new ListObjects(), "-delete", parent.toUri().toString());
    Assertions.assertThat(l4).as("delete run").contains("objects will be deleted")
        .contains("Deleted ");

    // 5. Final listing reports zero objects.
    final String l5 = captureSuccess(new ListObjects(), parent.toUri().toString());
    Assertions.assertThat(l5).as("listing after delete").contains("Found 0 objects");
  }
}
