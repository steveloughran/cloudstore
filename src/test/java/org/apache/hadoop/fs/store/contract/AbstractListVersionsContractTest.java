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

import static org.apache.hadoop.fs.contract.ContractTestUtils.createFile;
import static org.apache.hadoop.fs.contract.ContractTestUtils.readDataset;
import static org.apache.hadoop.tools.store.StoreTestUtils.captureSuccess;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.fs.EtagSource;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.contract.AbstractFSContractTestBase;
import org.apache.hadoop.fs.s3a.sdk.ListVersions;
import org.apache.hadoop.fs.s3a.sdk.RestoreObject;
import org.apache.hadoop.fs.s3a.sdk.Undelete;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * Cross-FS contract test for the {@code listversions}, {@code restore} and {@code undelete}
 * commands. Drives all three through one sequence on a single file. The test is gated on the bucket
 * exposing the {@code header.x-amz-version-id} xattr — if the path's listXAttrs does not contain
 * it, the bucket is not versioned and the test is skipped.
 */
public abstract class AbstractListVersionsContractTest extends AbstractFSContractTestBase {

  /** XAttr that S3A surfaces only when an object has a version id. */
  public static final String VERSION_XATTR = "header.x-amz-version-id";

  @Test
  public void testListVersionsRestoreUndelete() throws Exception {
    final Path versioned = new Path(methodPath(), "versioned.txt");

    // 1. write v1, capture etag
    final FileSystem fs = getFileSystem();
    createFile(fs, versioned, true, "v1".getBytes(StandardCharsets.UTF_8));

    // probe for versioning via xattr; skip if absent
    final List<String> attrs = fs.listXAttrs(versioned);
    org.junit.Assume.assumeTrue("Bucket is not versioned (xattr " + VERSION_XATTR + " absent on "
        + versioned + " -- listXAttrs returned " + attrs + ")", attrs.contains(VERSION_XATTR));

    final FileStatus s1 = fs.getFileStatus(versioned);
    final String e1 = ((EtagSource) s1).getEtag();
    Assertions.assertThat(e1).as("etag of v1").isNotBlank();

    // 2. overwrite with v2
    createFile(fs, versioned, true, "v2-content".getBytes(StandardCharsets.UTF_8));
    final FileStatus s2 = fs.getFileStatus(versioned);
    final String e2 = ((EtagSource) s2).getEtag();
    Assertions.assertThat(e2).as("etag of v2").isNotBlank().isNotEqualTo(e1);

    // 3. delete (creates a tombstone)
    Assertions.assertThat(fs.delete(versioned, false)).as("delete returned true").isTrue();

    // 4. listversions -deleted -out csv
    File csvFile = File.createTempFile("listversions", ".csv");
    csvFile.deleteOnExit();
    final String captured1 = captureSuccess(new ListVersions(), "-deleted", "-out",
        csvFile.toURI().toString(), versioned.toUri().toString());
    Assertions.assertThat(captured1).as("listversions summary").contains("Found ")
        .contains("Tombstone entries");
    // captured stdout reports tombstone count separately; CSV writer
    // only emits ObjectVersion rows, not DeleteMarkerEntry rows.
    Assertions.assertThat(captured1).as("listversions tombstone count > 0")
        .matches("(?s).*Tombstone entries [1-9].*");

    // parse CSV to map etag -> versionId for the live ObjectVersions
    final Map<String, String> etagToVersion = new HashMap<>();
    boolean header = true;
    for (String line : Files.readAllLines(csvFile.toPath(), StandardCharsets.UTF_8)) {
      if (line.isEmpty() || header) {
        header = false;
        continue;
      }
      // columns: index,key,path,restore,latest,size,tombstone,directory,date,timestamp,version,etag
      final String[] cols = line.split("\t");
      if (cols.length < 12) {
        continue;
      }
      final String version = stripQuotes(cols[10]);
      final String etag = stripQuotes(cols[11]);
      etagToVersion.put(etag, version);
    }
    final String e1n = stripQuotes(e1);
    final String e2n = stripQuotes(e2);
    Assertions.assertThat(etagToVersion).as("non-tombstone etag->version map").containsKey(e1n)
        .containsKey(e2n);

    final String v1Id = etagToVersion.get(e1n);
    Assertions.assertThat(v1Id).as("version id of v1").isNotBlank();

    // 5. restore v1 -> versioned-restored.txt
    final Path restored = new Path(methodPath(), "versioned-restored.txt");
    final String captured2 = captureSuccess(new RestoreObject(), versioned.toUri().toString(), v1Id,
        restored.toUri().toString());
    Assertions.assertThat(captured2).as("restore output").contains("Restored object")
        .contains(restored.toUri().toString());
    // verify the restored content matches the original "v1" body. The
    // restore is implemented as a copy, which on a multipart path can
    // produce a fresh etag, so compare content rather than etag.
    Assertions.assertThat(new String(readDataset(fs, restored, 2), StandardCharsets.UTF_8))
        .as("contents of restored file match v1").isEqualTo("v1");

    // 6. undelete the original path -> tombstone gone, latest live = v2
    final String captured3 = captureSuccess(new Undelete(), versioned.toUri().toString());
    Assertions.assertThat(captured3).as("undelete output").contains("Removed ")
        .contains("tombstones");

    final FileStatus afterUndelete = fs.getFileStatus(versioned);
    Assertions.assertThat(stripQuotes(((EtagSource) afterUndelete).getEtag()))
        .as("after undelete the live version equals e2").isEqualTo(e2n);
  }

  /** Trim leading/trailing double quotes (S3 etags are quoted on the wire). */
  private static String stripQuotes(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    while (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
      t = t.substring(1, t.length() - 1);
    }
    return t;
  }
}
