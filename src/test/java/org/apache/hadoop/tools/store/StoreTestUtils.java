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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.fs.contract.ContractTestUtils;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.fs.store.diag.Printout;
import org.apache.hadoop.fs.store.diag.StringBuilderPrintout;
import org.apache.hadoop.test.LambdaTestUtils;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Various utils, a lot pulled from ContractTestUtils, LambdaTestUtils. Why copy & paste? Allows
 * this whole module to build and run against earlier Hadoop versions.
 */
public final class StoreTestUtils {

  private static final Logger LOG = LoggerFactory.getLogger(StoreTestUtils.class);

  private StoreTestUtils() {}

  public static int exec(Tool tool, String... args) throws Exception {
    return ToolRunner.run(tool, args);
  }

  public static void expectSuccess(Tool tool, String... args) throws Exception {
    expectOutcome(0, tool, args);
  }

  public static <E extends Throwable> E expectException(Class<E> clazz, final Tool tool,
      final String... args) throws Exception {
    return LambdaTestUtils.intercept(clazz, () -> exec(tool, args));
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

  public static <T> ExitUtil.ExitException expectExitException(int exitCode, Callable<T> eval)
      throws Exception {
    final ExitUtil.ExitException ex = LambdaTestUtils.intercept(ExitUtil.ExitException.class, eval);
    if (ex.status != exitCode) {
      throw ex;
    }
    return ex;
  }

  public static void expectOutcome(int expected, Tool tool, String... args) throws Exception {
    assertThat(exec(tool, args)).describedAs("outcome of %s", toString(args)).isEqualTo(expected);
  }

  /**
   * Result of running a {@link Tool} with stdout captured.
   */
  public static final class CapturedRun {
    public final int exitCode;
    public final String captured;

    CapturedRun(int exitCode, String captured) {
      this.exitCode = exitCode;
      this.captured = captured;
    }

    public boolean contains(String substring) {
      return captured.contains(substring);
    }

    @Override
    public String toString() {
      return "CapturedRun{exit=" + exitCode + "}\n" + captured;
    }
  }

  /**
   * Run a {@link Tool} with the {@link StoreEntryPoint} active Printout sink swapped to a
   * {@link StringBuilderPrintout}, returning the exit code and the captured output. The previous
   * sink is always restored.
   *
   * @param tool the Tool to run
   * @param args its arguments
   * @return exit code + captured output
   * @throws Exception any exception thrown by the tool
   */
  public static CapturedRun runAndCapture(Tool tool, String... args) throws Exception {
    final StringBuilderPrintout cap = new StringBuilderPrintout();
    final Printout prev = StoreEntryPoint.setActivePrintout(cap);
    try {
      int rc = exec(tool, args);
      return new CapturedRun(rc, cap.toString());
    } finally {
      StoreEntryPoint.setActivePrintout(prev);
    }
  }

  /**
   * As {@link #runAndCapture(Tool, String...)} but also asserts the exit code is zero and returns
   * the captured output.
   *
   * @param tool the Tool to run
   * @param args its arguments
   * @return the captured stdout
   * @throws Exception any exception thrown by the tool
   */
  public static String captureSuccess(Tool tool, String... args) throws Exception {
    final CapturedRun r = runAndCapture(tool, args);
    assertThat(r.exitCode).describedAs("exit code of %s with output %s", toString(args), r.captured)
        .isEqualTo(0);
    return r.captured;
  }

  public static String toString(String[] args) {
    return "exec(" + StringUtils.join(" ", args) + ")";
  }

  public static void mkdirs(File dir) {
    assertThat(dir.mkdirs()).describedAs("Failed to create " + dir).isTrue();
  }

  /**
   * Create some test files
   * 
   * @param destDir destination; things to in under it.
   * @param fileCount total number of files
   * @return number of expected files in recursive enum
   * @throws IOException
   */
  public static int createTestFiles(File destDir, int fileCount) throws IOException {
    File subdir = new File(destDir, "subdir");
    int expected = 0;
    mkdirs(subdir);
    File top = new File(destDir, "top");
    FileUtils.write(top, "toplevel", StandardCharsets.UTF_8);
    expected++;
    for (int i = 0; i < fileCount; i++) {
      String text = String.format("file-%02d", i);
      File f = new File(subdir, text);
      FileUtils.write(f, f.toString(), StandardCharsets.UTF_8);
    }
    expected += fileCount;

    // and write the largest file
    File largest = new File(subdir, "largest");
    FileUtils.writeByteArrayToFile(largest, ContractTestUtils.dataset(8192, 32, 64));
    expected++;
    return expected;
  }
}
