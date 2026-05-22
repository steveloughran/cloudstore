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
package org.apache.hadoop.fs.store.diag;

import static org.apache.hadoop.service.launcher.LauncherExitCodes.EXIT_USAGE;
import static org.apache.hadoop.test.LambdaTestUtils.intercept;
import static org.apache.hadoop.tools.store.StoreTestUtils.expectExitException;
import static org.apache.hadoop.tools.store.StoreTestUtils.expectSuccess;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import org.apache.hadoop.fs.UnsupportedFileSystemException;
import org.apache.hadoop.fs.shell.CommandFormat;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Exercise {@link StoreDiag} in-process against the local filesystem and deliberately broken
 * inputs. None of these tests need cloud credentials.
 *
 * <p>
 * Companion to the contract-test-based suite under {@code org.apache.hadoop.fs.store.contract}
 * which targets each remote filesystem via {@code AbstractFSContractTestBase}.
 */
public class TestStoreDiagInvocations {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  /** Existing local directory: a fully successful diag run. */
  @Test
  public void testStorediagAgainstLocalDir() throws Exception {
    File dir = temp.newFolder("storediag-local");
    // pin readonly with -r so the run doesn't try to create marker files
    // outside the temp folder.
    StoreDiag diag = new StoreDiag();
    expectSuccess(diag, "-r", dir.toURI().toString());
  }

  /** No positional argument → usage error. */
  @Test
  public void testStorediagNoArgs() throws Exception {
    expectExitException(EXIT_USAGE, () -> StoreDiag.exec());
  }

  /**
   * Two positional arguments → CommandFormat rejects before processArgs has a chance to wrap as an
   * ExitException with EXIT_USAGE.
   */
  @Test
  public void testStorediagTooManyArgs() throws Exception {
    intercept(CommandFormat.TooManyArgumentsException.class,
        () -> StoreDiag.exec("file:///a", "file:///b"));
  }

  /** Unknown scheme → UnsupportedFileSystemException propagates from bindToStore. */
  @Test
  public void testStorediagUnknownScheme() throws Exception {
    intercept(UnsupportedFileSystemException.class,
        () -> StoreDiag.exec("nosuchfs://example/path"));
  }

  /** -required file does not exist → FileNotFoundException propagates. */
  @Test
  public void testStorediagMissingRequiredFile() throws Exception {
    File dir = temp.newFolder("storediag-required-missing");
    File missing = new File(dir, "no-such-required-file.txt");
    intercept(FileNotFoundException.class,
        () -> StoreDiag.exec("-required", missing.getAbsolutePath(), dir.toURI().toString()));
  }

  /** -required points at an empty file → success (zero classes to probe). */
  @Test
  public void testStorediagEmptyRequiredFile() throws Exception {
    File dir = temp.newFolder("storediag-required-empty");
    File required = new File(dir, "required.txt");
    Files.write(required.toPath(), new byte[0]);
    StoreDiag diag = new StoreDiag();
    expectSuccess(diag, "-r", "-required", required.getAbsolutePath(), dir.toURI().toString());
  }

  /**
   * Pointing storediag at a path under a nonexistent local directory still succeeds: storediag
   * binds to the filesystem root and reports diagnostics even when the supplied path inside that
   * root is missing. {@code EXIT_NOT_FOUND} only fires when the *root* of the store cannot be
   * resolved, which cannot be triggered against {@code file://} on a host where {@code /} exists.
   */
  @Test
  public void testStorediagPathBelowMissingDir() throws Exception {
    File dir = temp.newFolder("storediag-below-missing");
    File missing = new File(dir, "definitely-not-here");
    StoreDiag diag = new StoreDiag();
    expectSuccess(diag, "-r", missing.toURI().toString());
  }
}
