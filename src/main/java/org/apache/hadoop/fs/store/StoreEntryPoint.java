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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.Tool;

/**
 * Entry point for store applications
 */
public class StoreEntryPoint extends Configured implements Tool {

  private static final Logger LOG = LoggerFactory.getLogger(StoreEntryPoint.class);

  private static final String HELLO = "Hello";

  private static PrintStream out = System.out;

  @Override
  public int run(String[] args) throws Exception {
    return 0;
  }

  public static PrintStream getOut() {
    return out;
  }

  public static void setOut(PrintStream out) {
    StoreEntryPoint.out = out;
  }

  /**
   * Print a formatted string followed by a newline to the output stream.
   * @param format format string
   * @param args optional arguments
   */
  protected void println(String format, Object... args) {
    out.println(String.format(format, args));
  }

  protected static void errorln(String format, Object... args) {
    System.err.println(String.format(format, args));
  }

  protected void heading(String format, Object... args) {
    String text = String.format(format, args);
    int l = text.length();
    StringBuilder sb = new StringBuilder(l);
    for (int i = 0; i < l; i++) {
      sb.append("=");
    }
    println("\n%s\n%s\n", text, sb.toString());
  }

  protected static void exit(int status, String text) {
    ExitUtil.terminate(status, text);
  }
}
