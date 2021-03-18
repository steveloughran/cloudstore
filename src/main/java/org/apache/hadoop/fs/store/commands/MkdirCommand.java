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

package org.apache.hadoop.fs.store.commands;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.LogFixup;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.fs.store.StoreExitCodes;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.LOGFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

/**
 * Mkdir, but with log4j fixup.
 */
public class MkdirCommand extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(MkdirCommand.class);

  public static final String USAGE
      = "Usage: mkdir [" +LOGFILE + " <filename>]"
      + " <path>";


  public MkdirCommand() {
    createCommandFormat(1, 1);
    addValueOptions(LOGFILE);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (paths.size() < 1) {
      errorln(USAGE);
      return E_USAGE;
    }
    String logFile = getOption(LOGFILE);
    if (logFile != null) {
      println("Using log4j properties file \"%s\"", logFile);
      File f = new File(logFile);
      if (!f.exists()) {
        errorln("File not found: %s", f.getAbsolutePath());
        return StoreExitCodes.E_NOT_FOUND;
      }
      LogFixup.useLogFile(f);
    }
    // path on the CLI
    String pathString = paths.get(0);
    Path path = new Path(pathString);
    println("creating directory %s", path);
    FileSystem fs = path.getFileSystem(getConf());
    println("Using filesystem %s", fs.getUri());
    Path absPath = path.makeQualified(fs.getUri(), fs.getWorkingDirectory());
    try (StoreDurationInfo ignored = new StoreDurationInfo(
        LOG, "mkdirs(%s)", absPath)) {
      // before we do that, we scan the tree, top to bottom
      checkPathIsDir(fs, absPath);
      // do the create
      fs.mkdirs(absPath);
    }
    return 0;
  }


  /**
   * Recursive check for directory status.
   * @param fs filesystem
   * @param path Path to check
   * @throws IOException Failure
   */
  private void checkPathIsDir(FileSystem fs, Path path) throws IOException {
    if (path.isRoot()) {
      return;
    }
    // its not root. Validate the parent
    checkPathIsDir(fs, path.getParent());
    // now us
    LOG.info("Checking {}", path);
    FileStatus status = null;
    try {
      status = fs.getFileStatus(path);
    } catch (FileNotFoundException e) {
      // not a problem
      return;
    }
    if (!status.isDirectory()) {
      String message = "Path "
          + path + " must be a directory, but it is a file: "
          + status;
      LOG.error("{}", message);
      throw new IOException(message);
    }
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new MkdirCommand(), args);
  }

  /**
   * Main entry point. Calls {@code System.exit()} on all execution paths.
   * @param args argument list
   */
  public static void main(String[] args) {
    try {
      exit(exec(args), "");
    } catch (Throwable e) {
      exitOnThrowable(e);
    }
  }

}
