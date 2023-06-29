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

package org.apache.hadoop.fs.store;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.shell.CommandFormat;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

public class CheckStoreProperty extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(
      CheckStoreProperty.class);

  protected CommandFormat commandFormat = new CommandFormat(0,
      Integer.MAX_VALUE);

  static final String USAGE = "Usage: CheckStoreProperty <filesystem> <key> <value>";


  @Override
  public final int run(String[] args) throws Exception {
    return run(args, System.out);
  }

  public int run(String[] args, PrintStream stream) throws Exception {
    setOut(stream);
    List<String> argList = parseArgs(args);
    if (argList.size() != 3) {
      errorln(USAGE);
      return E_USAGE;
    }

    // path on the CLI
    String pathString = argList.get(0);
    if (!pathString.endsWith("/")) {
      pathString = pathString + "/";
    }
    Path path = new Path(pathString);
    Configuration conf = new Configuration(true);
    FileSystem fs = path.getFileSystem(conf);
    Configuration fsConf = fs.getConf();

    String key = argList.get(1);
    String expected = argList.get(2);

    String actual = fsConf.getTrimmed(key);
    if (!expected.equals(actual)) {
      println("Expected option %s of filesystem %s to be \"%s\", but was \"%s\"",
          path, key, expected, actual);
      return -1;
    } else {
      println("Value of %s for %s is as expected: %s",
          key, path, expected);
      return 0;
    }
  }


  /**
   * Parse CLI arguments and returns the position arguments.
   * The options are stored in {@link #commandFormat}.
   *
   * @param args command line arguments.
   * @return the position arguments from CLI.
   */
  public List<String> parseArgs(String[] args) {
    return args.length > 0 ? commandFormat.parse(args, 0)
        : new ArrayList<>(0);
  }
    /**
     * Execute the command, return the result or throw an exception,
     * as appropriate.
     * @param args argument varags.
     * @return return code
     * @throws Exception failure
     */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new CheckStoreProperty(), args);
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
