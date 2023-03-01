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

package org.apache.hadoop.fs.tools.csv;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;

import static java.util.Objects.requireNonNull;

/**
 * Write CSV files with quoting of strings, booleans mapped to
 * 1/0.
 */
public class SimpleCsvWriter implements Closeable {

  private final Writer out;

  private final String separator;

  private final String eol;

  private final boolean quote;

  private final boolean closeOutput;

  private boolean isStartOfLine = true;

  /**
   * construct
   * @param out output writer
   * @param separator separator string
   * @param eol end of line string
   * @param quote should strings be quoted?
   * @param closeOutput should the output be closed in close(), or just flushed?
   */
  public SimpleCsvWriter(
      final Writer out,
      final String separator,
      final String eol,
      final boolean quote, final boolean closeOutput) {
    this.quote = quote;
    this.out = requireNonNull(out);
    this.separator = requireNonNull(separator);
    this.eol = requireNonNull(eol);
    this.closeOutput = closeOutput;
  }

  /**
   * Instantiate.
   * @param out output stream
   * @param separator separator string
   * @param eol end of line string
   * @param quote should strings be quoted?
   * @param closeOutput should the output be closed in close(), or just flushed?
   */
  public SimpleCsvWriter(
      final OutputStream out,
      final String separator,
      final String eol,
      final boolean quote,
      final boolean closeOutput) {
    this(new PrintWriter(out), separator, eol, quote, closeOutput);
  }

  public Writer getOut() {
    return out;
  }

  public String getSeparator() {
    return separator;
  }

  public String getEol() {
    return eol;
  }

  public boolean isQuote() {
    return quote;
  }

  public boolean isStartOfLine() {
    return isStartOfLine;
  }

  /**
   * Close the output stream.
   * @throws IOException IO failure.
   */
  public void close() throws IOException {
    flush();
    if (closeOutput) {
      out.close();
    }
  }

  /**
   * Write a single object's string value.
   * @param o object to write; null is mapped to the empty string.
   * @throws IOException IO failure.
   */
  public void column(Object o) throws IOException {
    col(o, quote);
  }

  private void col(final Object o, final boolean quoteColumn) throws IOException {
    if (isStartOfLine) {
      isStartOfLine = false;
    } else {
      write(separator);
    }
    final String s = toString(o);
    if (quoteColumn) {
      quote(s);
    } else {
      write(s);
    }
  }

  private static String toString(final Object o) {
    return o != null
        ? o.toString()
        : "";
  }

  /**
   * Write a bool, mapping true to 1 and
   * false to 0.
   * That makes counting the number of matched
   * rows straightforward.
   * @param b boolean
   * @throws IOException io failure
   */
  public void columnB(boolean b) throws IOException {
    columnL(b ? 1 : 0);
  }

  /**
   * Write a long, unquoted, always.
   * @param i value
   * @throws IOException io failure
   */
  public void columnL(long i) throws IOException {
    col(Long.toString(i), false);
  }


  public void write(String val) throws IOException {
    out.write(val);
  }

  public void quote(Object o) throws IOException {
    write(String.format("\"%s\"", toString(o)));
  }

  public void newline() throws IOException {
    out.write(eol);
    isStartOfLine = true;
  }

  /**
   * Write a collection of objects as separated columns.
   * @param objects varags list of objects to write
   * @return this instance.
   * @throws IOException IO failure.
   */
  public void columns(Object... objects) throws IOException {
    for (Object object : objects) {
      column(object);
    }
  }

  /**
   * Flush the stream.
   * @throws IOException failure
   */
  public void flush() throws IOException {
    out.flush();
  }
}
