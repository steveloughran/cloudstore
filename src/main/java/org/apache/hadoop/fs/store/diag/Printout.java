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

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;

/**
 * API for printing.
 */
public interface Printout {

  void println();

  /**
   * Print a formatted string followed by a newline to the output stream.
   * @param format format string
   */
  void println(String format);

  /**
   * Print a formatted string followed by a newline to the output stream.
   * @param format format string
   * @param args optional arguments
   */
  void println(String format, Object... args);

  /**
   * Flush the stream.
   */
  void flush();

  /**
   * Print a formatted string without any newline.
   * @param format format string
   * @param args optional arguments
   */
  void print(String format, Object... args);

  /**
   * Print a warning.
   * @param format format string
   * @param args optional arguments
   */
  void warn(String format, Object... args);


  /**
   * Print an error. unless overridden, this forwards to warn.
   * @param format format string
   * @param args optional arguments
   */
  default void error(String format, Object... args) {
    warn(format, args);
  }

  void heading(String format, Object... args);

  /**
   * Debug message.
   * @param format format string
   * @param args arguments.
   */
  void debug(String format, Object... args);

  /**
   * Print the selected options in a config.
   * This is an array of (name, secret, obfuscate) entries.
   * @param title heading to print
   * @param conf source configuration
   * @param options map of options
   */
  void printOptions(String title, Configuration conf,
      Object[][] options)
      throws IOException;

  /**
   * Sanitize a value if needed.
   * @param value option value.
   * @param obfuscate should it be obfuscated?
   * @return string safe to log; in quotes
   */
  String maybeSanitize(String value, boolean obfuscate);

  /**
   * Retrieve and print an option.
   * Secrets are looked for through Configuration.getPassword(),
   * rather than the simpler get(option).
   * They are also sanitized in printing, so as to keep the secrets out
   * of bug reports.
   * @param conf source configuration
   * @param index index for a prefix
   * @param key key
   * @param secret is it secret?
   * @param obfuscate should it be obfuscated?
   */
  void printOption(Configuration conf,
      int index,
      String key,
      boolean secret,
      boolean obfuscate)
      throws IOException;
}
