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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.store.diag.DiagnosticsEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

/**
 * Checks to see if the tar command is hardened by taking a command line param and trying
 * to untar it.
 */
public class TarHardened extends DiagnosticsEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(TarHardened.class);

  public static final String USAGE
      = "Usage: tarhardened [filename]\n"
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(XMLFILE, "file", "XML config file to load")
      + optusage(VERBOSE, "verbose output");

  public TarHardened() {
    createCommandFormat(1, 1, VERBOSE);
    addValueOptions(XMLFILE, DEFINE);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (paths.size() > 1) {
      // too many entries
      errorln(USAGE);
      return E_USAGE;
    }
    String filename;
    if (paths.isEmpty()) {
      File tar = File.createTempFile("tarhardened", ".tgz");
      tar.delete();
      filename = tar.getAbsolutePath() + "; true";
    } else {
      filename = paths.get(0);
    }
    File tmpdir = File.createTempFile("tarhardened-dir", "");
    tmpdir.delete();
    final File source = new File(filename);
    println("Attempting to untar file with name \"%s\"", source);
    FileUtil.unTar(source, tmpdir);
    println("untar operation reported success");
    println();
    return 0;
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new TarHardened(), args);
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
