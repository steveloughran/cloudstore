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

package org.apache.hadoop.tools.cloudup;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.fs.tools.cloudup.Cloudup;

import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.tools.store.StoreTestUtils.*;

public class TestLocalCloudup extends Assert {

  @Rule
  public TestName methodName = new TestName();

  /**
   * Set the timeout for every test.
   */
  @Rule
  public Timeout testTimeout = new Timeout(15 * 1000);

  protected static final Logger LOG =
      LoggerFactory.getLogger(TestLocalCloudup.class);
  private static File testDirectory;
  private File methodDir;
  private File sourceDir;
  private File destDir;

  @BeforeClass
  public static void classSetup() throws Exception {
    Thread.currentThread().setName("JUnit");
    testDirectory = createTestDir();
  }

  @Before
  public void setup() throws Exception {
    methodDir = new File(testDirectory, methodName.getMethodName());
    mkdirs(methodDir);
    sourceDir = new File(methodDir, "src");
    destDir = new File(methodDir, "dest");
    FileUtil.fullyDelete(sourceDir);
    FileUtil.fullyDelete(destDir);
  }

  @After
  public void teardown() throws Exception {
    if (methodDir != null) {
      FileUtil.fullyDelete(methodDir);
    }
  }


  @Test
  public void testNoArgs() throws Throwable {
    // no args == failure
    expectOutcome(E_USAGE,
        new Cloudup()
    );
  }

  @Test
  public void testNonexistentSrcAndDest() throws Throwable {
    expectException(FileNotFoundException.class,
        new Cloudup(),
        "-s", sourceDir.toURI().toString(),
        "-d", destDir.toURI().toString());
  }

  @Test
  public void testCopyFileSrcAndDest() throws Throwable {
    FileUtils.write(sourceDir, "hello");
    expectSuccess(new Cloudup(),
        "-s", sourceDir.toURI().toString(),
        "-d", destDir.toURI().toString());
    assertDestDirIsFile();
  }

  @Test
  public void testNoOverwriteDest() throws Throwable {
    FileUtils.write(sourceDir, "hello");
    LOG.info("Initial upload");
    expectSuccess(new Cloudup(),
        "-s", sourceDir.toURI().toString(),
        "-d", destDir.toURI().toString());
    assertDestDirIsFile();
    LOG.info("Second upload");
    expectException(IOException.class,
        new Cloudup(),
        "-s", sourceDir.toURI().toString(),
        "-d", destDir.toURI().toString());

    // and now with -i, the failure is ignored
    expectSuccess(new Cloudup(),
        "-s", sourceDir.toURI().toString(),
        "-i",
        "-d", destDir.toURI().toString());
  }

  @Test
  public void testOverwriteDest() throws Throwable {
    FileUtils.write(sourceDir, "hello");
    LOG.info("Initial upload");
    expectSuccess(new Cloudup(),
        "-s", sourceDir.toURI().toString(),
        "-d", destDir.toURI().toString());
    assertDestDirIsFile();
    LOG.info("Second upload");
    expectSuccess(new Cloudup(),
        "-s", sourceDir.toURI().toString(),
        "-o",
        "-d", destDir.toURI().toString());
  }

  private void assertDestDirIsFile() {
    assertTrue("Not a file: " + destDir, destDir.isFile());
  }


  @Test
  public void testCopyRecursive() throws Throwable {
    int expected = createTestFiles(sourceDir, 64);

    expectSuccess(new Cloudup(),
        "-s", sourceDir.toURI().toString(),
        "-d", destDir.toURI().toString(),
        "-t", "4",
        "-l", "3");

    LocalFileSystem local = FileSystem.getLocal(new Configuration());
    Set<String> entries = new TreeSet<>();
    RemoteIterator<LocatedFileStatus> iterator
        = local.listFiles(new Path(destDir.toURI()), true);
    int count = 0;
    while (iterator.hasNext()) {
      LocatedFileStatus next = iterator.next();
      entries.add(next.getPath().toUri().toString());
      LOG.info("Entry {} size = {}", next.getPath(), next.getLen());
      count++;
    }
    assertEquals("Mismatch in files found", expected, count);

  }

}
