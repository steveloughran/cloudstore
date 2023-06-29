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

package org.apache.hadoop.fs.s3a.cloudup;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.test.AbstractS3AStoreTest;
import org.apache.hadoop.fs.tools.cloudup.Cloudup;
import org.apache.hadoop.tools.store.StoreTestUtils;

import static org.apache.hadoop.fs.contract.ContractTestUtils.cleanup;
import static org.apache.hadoop.tools.store.StoreTestUtils.*;

/**
 * As the S3A test base isn't available, do enough to make it look
 * like it is, to ease later merge.
 */
public class ITestS3ACloudup extends AbstractS3AStoreTest {
  protected static final Logger LOG =
      LoggerFactory.getLogger(ITestS3ACloudup.class);
  private Path root;
  private Path testPath;


  private static File testDirectory;
  private File methodDir;
  private File sourceDir;

  @BeforeClass
  public static void classSetup() throws Exception {
    testDirectory = createTestDir();
  }

  @Before
  public void setup() throws Exception {
    super.setup();
    root = new Path(getFileSystem().getUri());
    testPath = new Path(root, "/ITestS3ACloudup");

    methodDir = new File(testDirectory, methodName.getMethodName());
    StoreTestUtils.mkdirs(methodDir);
    sourceDir = new File(methodDir, "src");
    FileUtil.fullyDelete(sourceDir);
  }


  @After
  public void teardown() throws Exception {
    if (methodDir != null) {
      FileUtil.fullyDelete(methodDir);
    }
    cleanup("TEARDOWN", getFileSystem(), testPath);
  }

  @Test
  public void testUpload() throws Throwable {
    Path dest = methodPath();
    int expected = createTestFiles(sourceDir, 256);
    expectSuccess(
        new Cloudup(),
        "-s", sourceDir.toURI().toString(),
        "-d", dest.toUri().toString(),
        "-t", "16",
        "-o",
        "-l", "3");

  }

  public Path methodPath() {
    return new Path(testPath, methodName.getMethodName());
  }

}
