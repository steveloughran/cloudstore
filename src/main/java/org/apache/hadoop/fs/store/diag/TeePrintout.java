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
 * {@link Printout} that fans out every call to two underlying sinks. Used by {@code storediag -w}
 * to write to stdout and an in-memory buffer simultaneously.
 */
public class TeePrintout implements Printout {

  private final Printout primary;
  private final Printout secondary;

  public TeePrintout(Printout primary, Printout secondary) {
    this.primary = primary;
    this.secondary = secondary;
  }

  @Override
  public void println() {
    primary.println();
    secondary.println();
  }

  @Override
  public void println(String format) {
    primary.println(format);
    secondary.println(format);
  }

  @Override
  public void println(String format, Object... args) {
    primary.println(format, args);
    secondary.println(format, args);
  }

  @Override
  public void flush() {
    primary.flush();
    secondary.flush();
  }

  @Override
  public void print(String format, Object... args) {
    primary.print(format, args);
    secondary.print(format, args);
  }

  @Override
  public void warn(String format, Object... args) {
    primary.warn(format, args);
    secondary.warn(format, args);
  }

  @Override
  public void advise(String format, Object... args) {
    primary.advise(format, args);
    secondary.advise(format, args);
  }

  @Override
  public void error(String format, Object... args) {
    primary.error(format, args);
    secondary.error(format, args);
  }

  @Override
  public void exception(Throwable thrown, String format, Object... args) {
    primary.exception(thrown, format, args);
    secondary.exception(thrown, format, args);
  }

  @Override
  public void heading(String format, Object... args) {
    primary.heading(format, args);
    secondary.heading(format, args);
  }

  @Override
  public void subheading(String format, Object... args) {
    primary.subheading(format, args);
    secondary.subheading(format, args);
  }

  @Override
  public void debug(String format, Object... args) {
    primary.debug(format, args);
    secondary.debug(format, args);
  }

  @Override
  public void printOptions(String title, Configuration conf, Object[][] options)
      throws IOException {
    primary.printOptions(title, conf, options);
    secondary.printOptions(title, conf, options);
  }

  @Override
  public String maybeSanitize(String value, boolean obfuscate) {
    return primary.maybeSanitize(value, obfuscate);
  }

  @Override
  public void printOption(Configuration conf, int index, String key, boolean secret,
      boolean obfuscate) throws IOException {
    primary.printOption(conf, index, key, secret, obfuscate);
    secondary.printOption(conf, index, key, secret, obfuscate);
  }
}
