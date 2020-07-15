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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.DurationInfo;
import org.apache.hadoop.fs.store.LogFixup;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.fs.store.StoreExitCodes;
import org.apache.hadoop.fs.store.StoreExitException;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.util.VersionInfo;

import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.LIMIT;
import static org.apache.hadoop.fs.store.CommonParameters.LOGFILE;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_ERROR;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_EXCEPTION_THROWN;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_UNSUPPORTED_VERSION;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

/**
 * Look for a path capability.
 */
public class PathCapability extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(PathCapability.class);

  public static final String USAGE1
      = "Usage: pathcapability [" +LOGFILE + " <filename>]"
      + " <capability> <path>";

  public static final String USAGE
      = "Usage: pathcapability [options] <capability> <path>\n"
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(TOKENFILE, "file", "Hadoop token file to load")
      + optusage(VERBOSE, "print verbose output")
      + optusage(XMLFILE, "file", "XML config file to load");


  public PathCapability() {
    createCommandFormat(2, 2);
    addValueOptions(LOGFILE);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> argList = parseArgs(args);
    if (argList.size() < 1) {
      errorln(USAGE);
      return E_USAGE;
    }
    addAllDefaultXMLFiles();
    maybeAddTokens(TOKENFILE);
    final Configuration conf = getConf();

    maybeAddXMLFileOption(conf, XMLFILE);
    maybePatchDefined(conf, DEFINE);

    // path on the CLI
    String capability = argList.get(0);
    String pathString = argList.get(1);
    Path path = new Path(pathString);
    println("creating directory %s", path);
    FileSystem fs = path.getFileSystem(conf);
    println("Using filesystem %s", fs.getUri());
    Path absPath = path.makeQualified(fs.getUri(), fs.getWorkingDirectory());
    if (hasPathCapability(fs, absPath, capability)) {
      println("Path %s has capability %s",
          absPath, capability);
      return 0;
    } else {
      println("Path %s lacks capability %s",
          absPath, capability);
      return E_ERROR;
    }
  }

  private boolean hasPathCapability(FileSystem fs, Path path, String capability)
      throws IOException {
    try {
      Method hasPathCapability = FileSystem.class.getMethod("hasPathCapability",
          Path.class, String.class);
      return (Boolean) hasPathCapability.invoke(fs, path, capability);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new StoreExitException(E_UNSUPPORTED_VERSION,
          "Hadoop version does not support PathCapabilities: "
              + VersionInfo.getVersion());
    } catch (InvocationTargetException e) {
      Throwable ex = e.getTargetException();
      if (ex instanceof IOException) {
        throw (IOException) ex;
      } else {
        throw new StoreExitException(E_EXCEPTION_THROWN,
            ex.toString(), ex);
      }
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
    return ToolRunner.run(new PathCapability(), args);
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
