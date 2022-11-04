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
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static java.util.Objects.requireNonNull;

/**
 * This is a small utility class to write out rows to a CSV/TSV file.
 * It does not do any escaping of written text, so don't write entries
 * containing separators.
 * Quoting must be done external to this class.
 */
public final class StoreCsvWriter implements Closeable {

  private final Writer out;

  private final String separator;

  private final String eol;

  private final boolean quote;

  private boolean isStartOfLine = true;

  private CRC32 rowCrc = new CRC32();

  /**
   * Instantiate.
   * @param out output writer.
   * @param separator field separator.
   * @param eol end of line sequence
   * @param quote quote columns?
   */
  public StoreCsvWriter(
      final Writer out,
      final String separator,
      final String eol,
      final boolean quote) {
    this.out = requireNonNull(out);
    this.separator = requireNonNull(separator);
    this.eol = requireNonNull(eol);
    this.quote = quote;
  }

  /**
   * Instantiate.
   * @param out output stream.
   * @param separator field separator.
   * @param eol end of line sequence
   * @param quote quote columns?
   */
  public StoreCsvWriter(
      final OutputStream out,
      final String separator,
      final String eol,
      final boolean quote) {
    this(new PrintWriter(out), separator, eol, quote);
  }

  /**
   * Close the output stream.
   * @throws IOException IO failure.
   */
  @Override
  public void close() throws IOException {
    out.close();
  }

  /**
   * Write a single object's string value.
   * @param o object to write.
   * @return this instance
   * @throws IOException IO failure.
   */
  public StoreCsvWriter column(Object o) throws IOException {
    if (isStartOfLine) {
      isStartOfLine = false;
      rowCrc = new CRC32();
    } else {
      write(separator);
    }
    return quote
        ? quote(o)
        : write(o.toString());
  }

  private StoreCsvWriter write(String val) throws IOException {
    out.write(val);
    rowCrc.update(val.getBytes(StandardCharsets.UTF_8));
    return this;
  }

  /**
   * Quote a single object's string value.
   * @param o object to write.
   * @return this instance
   * @throws IOException IO failure.
   */
  public StoreCsvWriter quote(Object o) throws IOException {
    return write(String.format("\"%s\"", o));
  }

  /**
   * Write a newline. This does not update the CRC.
   * @return this instance
   * @throws IOException IO failure.
   */
  public StoreCsvWriter newline() throws IOException {
    out.write(eol);
    rowCrc.update(eol.getBytes(StandardCharsets.UTF_8));
    isStartOfLine = true;
    return this;
  }

  /**
   * Write a collection of objects as separated columns.
   * @param objects varags list of objects to write
   * @return this instance.
   * @throws IOException IO failure.
   */
  public StoreCsvWriter columns(Object... objects) throws IOException {
    for (Object object : objects) {
      column(object);
    }
    return this;
  }

  /**
   * Flush the stream.
   * @throws IOException failure
   */
  public void flush() throws IOException {
    out.flush();
  }

  /**
   * get the row CRC.
   * @return the row crc
   */
  public long getRowCrc() {
    return rowCrc.getValue();
  }
}
