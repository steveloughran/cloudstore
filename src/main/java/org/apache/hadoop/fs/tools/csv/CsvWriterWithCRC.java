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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * This is a small utility class to write out rows to a CSV/TSV file.
 * It does not do any escaping of written text, so don't write entries
 * containing separators.
 * Quoting must be done external to this class.
 */
public final class CsvWriterWithCRC extends SimpleCsvWriter  {

  private CRC32 rowCrc = new CRC32();

  /**
   * Instantiate.
   * @param out output writer.
   * @param separator field separator.
   * @param eol end of line sequence
   * @param quote quote columns?
   */
  public CsvWriterWithCRC(
      final Writer out,
      final String separator,
      final String eol,
      final boolean quote) {
    super(out, separator, eol, quote, true);
  }

  /**
   * Instantiate.
   * @param out output stream.
   * @param separator field separator.
   * @param eol end of line sequence
   * @param quote quote columns?
   */
  public CsvWriterWithCRC(
      final OutputStream out,
      final String separator,
      final String eol,
      final boolean quote) {
    this(new PrintWriter(out), separator, eol, quote);
  }

  @Override
  public void write(String val) throws IOException {
    super.write(val);
    rowCrc.update(val.getBytes(StandardCharsets.UTF_8));
  }


  /**
   * Write a newline. This does not update the CRC.
   * @return this instance
   * @throws IOException IO failure.
   */
  @Override
  public SimpleCsvWriter newline() throws IOException {
    super.newline();
    rowCrc.update(getEol().getBytes(StandardCharsets.UTF_8));
    return this;
  }

  /**
   * get the row CRC.
   * @return the row crc
   */
  public long getRowCrc() {
    return rowCrc.getValue();
  }

  public void resetRowCrc() {
    rowCrc.reset();
  }
}
