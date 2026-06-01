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

import static org.apache.hadoop.fs.store.CommonParameters.LARGEST;
import static org.apache.hadoop.fs.store.CommonParameters.THREADS;
import static org.apache.hadoop.service.launcher.LauncherExitCodes.EXIT_USAGE;
import static org.apache.hadoop.test.LambdaTestUtils.intercept;
import static org.apache.hadoop.tools.store.StoreTestUtils.createTestFiles;
import static org.apache.hadoop.tools.store.StoreTestUtils.expectException;
import static org.apache.hadoop.tools.store.StoreTestUtils.expectSuccess;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.fs.contract.AbstractFSContractTestBase;
import org.apache.hadoop.fs.contract.ContractTestUtils;
import org.apache.hadoop.fs.tools.cloudup.Cloudup;
import org.apache.hadoop.tools.store.StoreTestUtils;
import org.apache.hadoop.util.ExitUtil;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cross-FS contract tests for the {@code cloudup} command. The destination is the test filesystem
 * provided by {@link #createContract}; the source is always a local temporary directory created
 * per-test under {@link AbstractFSContractTestBase#methodPath()}'s sibling local dir.
 *
 * <p>
 * Concrete subclasses bind to a specific destination filesystem (HDFS, S3A, ABFS, ...). Tests cover
 * a single-file copy, a recursive directory copy, and the {@code -overwrite} flag.
 */
public abstract class AbstractCloudupContractTest extends AbstractFSContractTestBase {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractCloudupContractTest.class);

  @Rule
  public TemporaryFolder tempdir = new TemporaryFolder();

  private File sourceDir;

  private Path destDir;

  @Before
  public void setup() throws Exception {
    super.setup();
    destDir = methodPath();
    sourceDir = tempdir.getRoot();
  }

  /**
   * Single-file copy from a local temp dir into the contract FS.
   *
   * <p>
   * Cloudup treats a non-existent destination as a directory and copies the source file in under
   * its basename — see {@code Cloudup.getFinalPath}.
   */
  @Test
  public void testCloudupSingleFile() throws Exception {
    File srcFile = tempdir.newFile("cloudup-single.txt");
    Files.write(srcFile.toPath(), "hello cloudstore".getBytes(StandardCharsets.UTF_8));

    Path destPath = methodPath();
    getFileSystem().delete(destPath, true);

    expectSuccess(new Cloudup(), srcFile.toURI().toString(), destPath.toUri().toString());

    Path destFile = new Path(destPath, srcFile.getName());
    FileStatus dest = getFileSystem().getFileStatus(destFile);
    Assertions.assertThat(dest.isDirectory()).describedAs("Destination is not a file: %s", dest)
        .isFalse();
    Assertions.assertThat(dest.getLen()).describedAs("length of file %s", dest)
        .isEqualTo(srcFile.length());
  }

  /**
   * Recursive copy of a small tree of files into the contract FS.
   */
  @Test
  public void testCloudupRecursiveTree() throws Exception {
    File srcDir = tempdir.newFolder("cloudup-tree");
    FileUtils.write(new File(srcDir, "top.txt"), "top", StandardCharsets.UTF_8);
    File sub = new File(srcDir, "sub");
    assertTrue(sub.mkdir());
    FileUtils.write(new File(sub, "a.txt"), "alpha", StandardCharsets.UTF_8);
    FileUtils.write(new File(sub, "b.txt"), "beta", StandardCharsets.UTF_8);

    Path destPath = path("tree");
    getFileSystem().delete(destPath, true);

    expectSuccess(new Cloudup(), "-threads", "2", srcDir.toURI().toString(),
        destPath.toUri().toString());

    assertPathExists("dest", new Path(destPath, "top.txt"));
    assertPathExists("copied file", new Path(destPath, "sub/a.txt"));
    assertPathExists("copied file", new Path(destPath, "sub/b.txt"));
    assertPathExists("dest", new Path(destPath, "top.txt"));
  }

  /**
   * Re-running cloudup against an existing destination needs {@code -overwrite} to succeed. Source
   * is a single file; cloudup creates the file at {@code destPath<basename>}.
   */
  @Test
  public void testCloudupOverwrite() throws Exception {
    File srcFile = tempdir.newFile("cloudup-overwrite.txt");
    srcFile.deleteOnExit();
    Files.write(srcFile.toPath(), "v1".getBytes(StandardCharsets.UTF_8));

    Path destPath = methodPath();
    final FileSystem fs = getFileSystem();
    fs.delete(destPath, true);
    Path destFile = new Path(destPath, srcFile.getName());

    // initial copy
    expectSuccess(new Cloudup(), srcFile.toURI().toString(), destPath.toUri().toString());

    // rewrite source with a different payload, then overwrite
    Files.write(srcFile.toPath(), "v2-longer".getBytes(StandardCharsets.UTF_8));
    expectSuccess(new Cloudup(), "-overwrite", srcFile.toURI().toString(),
        destPath.toUri().toString());
    ContractTestUtils.assertFileHasLength(fs, destFile, (int) srcFile.length());
  }

  @Test
  public void testCopyRecursive() throws Throwable {

    int expected = createTestFiles(sourceDir, 64);
    Path destPath = methodPath();

    expectSuccess(new Cloudup(), "-" + THREADS, "4", "-" + LARGEST, "3",
        sourceDir.toURI().toString(), destPath.toString());

    Set<String> entries = new TreeSet<>();
    RemoteIterator<LocatedFileStatus> iterator = getFileSystem().listFiles(destPath, true);
    while (iterator.hasNext()) {
      LocatedFileStatus next = iterator.next();
      entries.add(next.getPath().toUri().toString());
      LOG.info("Entry {} size = {}", next.getPath(), next.getLen());
    }
    Assertions.assertThat(entries).hasSize(expected);
  }

  @Test
  public void testNoArgs() throws Throwable {
    // no args == failure
    intercept(ExitUtil.ExitException.class, Integer.toString(EXIT_USAGE),
        () -> StoreTestUtils.exec(new Cloudup()));
  }

  @Test
  public void testNonexistentSrcAndDest() throws Throwable {
    expectException(FileNotFoundException.class, new Cloudup(),
        sourceDir.toURI() + "/subdir", destDir.toString());
  }

}
