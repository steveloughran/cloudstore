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
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.store.StoreDiagConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link Printout} that writes to a {@link PrintStream} (defaults to {@code System.out}).
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class StdoutPrintout implements Printout {

  private static final Logger LOG = LoggerFactory.getLogger(StdoutPrintout.class);

  private PrintStream out;

  private final AtomicInteger headingCounter = new AtomicInteger(1);
  private final AtomicInteger subheadingCounter = new AtomicInteger(1);

  /** Hide all sensitive data. */
  private boolean hideAllSensitiveChars = StoreDiagConstants.DEFAULT_HIDE_ALL_SENSITIVE_CHARS;

  public StdoutPrintout() {
    this(System.out);
  }

  public StdoutPrintout(PrintStream out) {
    this.out = out;
  }

  public final PrintStream getOut() {
    return out;
  }

  public final void setOut(PrintStream out) {
    this.out = out;
  }

  @Override
  public boolean isHideAllSensitiveChars() {
    return hideAllSensitiveChars;
  }

  @Override
  public void setHideAllSensitiveChars(boolean hideAllSensitiveChars) {
    this.hideAllSensitiveChars = hideAllSensitiveChars;
  }

  @Override
  public void println() {
    out.println();
    flush();
  }

  @Override
  public void println(String format) {
    out.print(format);
    out.println();
    flush();
  }

  @Override
  public void println(String format, Object... args) {
    print(format, args);
    out.println();
    flush();
  }

  @Override
  public void flush() {
    out.flush();
  }

  @Override
  public void print(String format, Object... args) {
    if (args.length == 0) {
      out.print(format);
    } else {
      out.printf(format, args);
    }
  }

  @Override
  public void warn(String format, Object... args) {
    println("WARNING: " + String.format(format, args));
  }

  @Override
  public void advise(String format, Object... args) {
    println();
    println("ADVISE: " + String.format(format, args));
    println();
  }

  @Override
  public void error(String format, Object... args) {
    println("ERROR: " + String.format(format, args));
  }

  @Override
  public void exception(Throwable thrown, String format, Object... args) {
    String message = String.format(format, args);
    println("EXCEPTION: " + message + "; " + thrown);
    LOG.error(message, thrown);
  }

  @Override
  public void heading(String format, Object... args) {
    final int hc = headingCounter.getAndIncrement();
    subheadingCounter.set(1);
    String text = String.format("%d. ", hc) + String.format(format, args);
    println();
    println(text);
    println(underline('=', text.length()));
    println();
  }

  @Override
  public void subheading(String format, Object... args) {
    final int hc = headingCounter.get() - 1;
    final int shc = subheadingCounter.getAndIncrement();
    String prefix = String.format("%d.%d ", hc, shc);
    final String text = String.format(prefix + format, args);
    println();
    println(text);
    println(underline('-', text.length()));
  }

  @Override
  public void debug(String format, Object... args) {
    LOG.debug(format, args);
  }

  @Override
  public void printOptions(String title, Configuration conf, Object[][] options)
      throws IOException {
    int index = 0;
    if (options.length > 0) {
      heading(title);
      for (final Object[] option : options) {
        printOption(conf, ++index, (String) option[0], (Boolean) option[1], (Boolean) option[2]);
      }
    }
  }

  /**
   * This does NOT sanitize/obfuscate values; expects the StoreEntryPoint to do this.
   * 
   * @param value option value.
   * @param obfuscate should it be obfuscated?
   * @return an unsanitized string.
   */
  @Override
  public String maybeSanitize(String value, boolean obfuscate) {
    return "\"" + value + "\"";
  }

  @Override
  public void printOption(Configuration conf, int index, String key, boolean secret,
      boolean obfuscate) {
    if (key.isEmpty()) {
      return;
    }
    final String raw = conf.getRaw(key);
    final String value = raw == null ? "(unset)" : maybeSanitize(raw, obfuscate);
    println("[%03d]  %s = %s", index, key, value);
  }

  private static String underline(final char c, final int len) {
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append(c);
    }
    return sb.toString();
  }
}
