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

package org.apache.hadoop.tools.store;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.contract.ContractTestUtils;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

/**
 * Various utils, a lot pulled from ContractTestUtils, LambdaTestUtils.
 * Why copy & paste? Allows this whole module to build and run
 * against earlier Hadoop versions.
 */
public final class StoreTestUtils extends Assert {

  private static final Logger LOG = LoggerFactory.getLogger(StoreTestUtils.class);

  private StoreTestUtils() {
  }

  public static int exec(Tool tool, String... args) throws Exception {
    return ToolRunner.run(tool, args);
  }

  public static void expectSuccess(Tool tool, String... args) throws Exception {
    expectOutcome(0, tool, args);
  }

  public static <E extends Throwable> E expectException(Class<E> clazz,
      final Tool tool,
      final String... args) throws Exception {
    return intercept(clazz,
        new Callable<Integer>() {
          @Override
          public Integer call() throws Exception {
            return exec(tool, args);
          }
        });
  }

  private static String robustToString(Object o) {
    if (o == null) {
      return "(null)";
    } else {
      try {
        return o.toString();
      } catch (Exception e) {
        LOG.info("Exception calling toString()", e);
        return o.getClass().toString();
      }
    }
  }

  @SuppressWarnings("unchecked")
  public static <T, E extends Throwable> E intercept(
      Class<E> clazz,
      Callable<T> eval)
      throws Exception {
    try {
      T result = eval.call();
      throw new AssertionError("Expected an exception, got "
          + robustToString(result));
    } catch (Throwable e) {
      if (clazz.isAssignableFrom(e.getClass())) {
        return (E) e;
      }
      throw e;
    }
  }

  public static void expectOutcome(int expected, Tool tool, String... args)
      throws Exception {
    assertEquals(toString(args), expected, exec(tool, args));
  }

  public static String toString(String[] args) {
    return "exec(" + StringUtils.join(" ", args) + ")";
  }

  public static void mkdirs(File dir) {
    assertTrue("Failed to create " + dir, dir.mkdirs());
  }


  public static File createTestDir() throws IOException {
    String testDir = System.getProperty("test.build.data");
    File testDirectory;
    if (testDir == null) {
      File tf = File.createTempFile("TestLocalCloudup", ".dir");
      tf.delete();
      testDir = tf.getAbsolutePath();
      testDirectory = new File(testDir);
    } else {
      testDirectory = new File(testDir);
      // test dir from sysprop; force delete
      FileUtil.fullyDelete(testDirectory);
    }
    mkdirs(testDirectory);
    return testDirectory;
  }

  /**
   * Create some test files
   * @param destDir destination; things to in under it.
   * @param fileCount total number of files
   * @return number of expected files in recursive enum
   * @throws IOException
   */
  public static int createTestFiles(File destDir, int fileCount)
      throws IOException {
    File subdir = new File(destDir, "subdir");
    int expected = 0;
    mkdirs(subdir);
    File top = new File(destDir, "top");
    FileUtils.write(top, "toplevel");
    expected++;
    for (int i = 0; i < fileCount; i++) {
      String text = String.format("file-%02d", i);
      File f = new File(subdir, text);
      FileUtils.write(f, f.toString());
    }
    expected += fileCount;

    // and write the largest file
    File largest = new File(subdir, "largest");
    FileUtils.writeByteArrayToFile(largest,
        ContractTestUtils.dataset(8192, 32, 64));
    expected++;
    return expected;
  }

}
