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

import static org.apache.hadoop.tools.store.StoreTestUtils.expectSuccess;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.contract.AbstractFSContractTestBase;
import org.apache.hadoop.fs.tools.cloudup.Cloudup;
import org.junit.Test;

/**
 * Cross-FS contract tests for the {@code cloudup} command. The destination
 * is the test filesystem provided by {@link #createContract}; the source is
 * always a local temporary directory created per-test under
 * {@link AbstractFSContractTestBase#methodPath()}'s sibling local dir.
 *
 * <p>Concrete subclasses bind to a specific destination filesystem (HDFS, S3A,
 * ABFS, ...). Tests cover a single-file copy, a recursive directory copy, and
 * the {@code -overwrite} flag.
 */
public abstract class AbstractCloudupContractTest extends AbstractFSContractTestBase {

    /**
     * Single-file copy from a local temp dir into the contract FS.
     *
     * <p>Cloudup treats a non-existent destination as a directory and copies
     * the source file in under its basename — see {@code Cloudup.getFinalPath}.
     */
    @Test
    public void testCloudupSingleFile() throws Exception {
        File srcFile = File.createTempFile("cloudup-single", ".txt");
        srcFile.deleteOnExit();
        Files.write(srcFile.toPath(), "hello cloudstore".getBytes(StandardCharsets.UTF_8));

        Path destDir = methodPath();
        getFileSystem().delete(destDir, true);

        expectSuccess(new Cloudup(), srcFile.toURI().toString(), destDir.toUri().toString());

        Path destFile = new Path(destDir, srcFile.getName());
        FileStatus dest = getFileSystem().getFileStatus(destFile);
        assertTrue("Destination is not a file: " + dest, dest.isFile());
        assertEquals("Destination size mismatch", srcFile.length(), dest.getLen());
    }

    /**
     * Recursive copy of a small tree of files into the contract FS.
     */
    @Test
    public void testCloudupRecursiveTree() throws Exception {
        File srcDir = Files.createTempDirectory("cloudup-tree").toFile();
        try {
            FileUtils.write(new File(srcDir, "top.txt"), "top", StandardCharsets.UTF_8);
            File sub = new File(srcDir, "sub");
            assertTrue(sub.mkdir());
            FileUtils.write(new File(sub, "a.txt"), "alpha", StandardCharsets.UTF_8);
            FileUtils.write(new File(sub, "b.txt"), "beta", StandardCharsets.UTF_8);

            Path destPath = new Path(methodPath(), "tree");
            getFileSystem().delete(destPath, true);

            expectSuccess(
                    new Cloudup(),
                    "-threads",
                    "2",
                    srcDir.toURI().toString(),
                    destPath.toUri().toString());

            assertTrue("Destination tree is missing top.txt", getFileSystem().exists(new Path(destPath, "top.txt")));
            assertTrue(
                    "Destination tree is missing sub/a.txt", getFileSystem().exists(new Path(destPath, "sub/a.txt")));
            assertTrue(
                    "Destination tree is missing sub/b.txt", getFileSystem().exists(new Path(destPath, "sub/b.txt")));
        } finally {
            FileUtil.fullyDelete(srcDir);
        }
    }

    /**
     * Re-running cloudup against an existing destination needs {@code -overwrite}
     * to succeed. Source is a single file; cloudup creates the file at
     * {@code destDir/<basename>}.
     */
    @Test
    public void testCloudupOverwrite() throws Exception {
        File srcFile = File.createTempFile("cloudup-overwrite", ".txt");
        srcFile.deleteOnExit();
        Files.write(srcFile.toPath(), "v1".getBytes(StandardCharsets.UTF_8));

        Path destDir = methodPath();
        getFileSystem().delete(destDir, true);
        Path destFile = new Path(destDir, srcFile.getName());

        // initial copy
        expectSuccess(new Cloudup(), srcFile.toURI().toString(), destDir.toUri().toString());

        // rewrite source with a different payload, then overwrite
        Files.write(srcFile.toPath(), "v2-longer".getBytes(StandardCharsets.UTF_8));
        expectSuccess(
                new Cloudup(),
                "-overwrite",
                srcFile.toURI().toString(),
                destDir.toUri().toString());

        assertEquals(
                "Destination not overwritten with new payload",
                srcFile.length(),
                getFileSystem().getFileStatus(destFile).getLen());
    }
}
