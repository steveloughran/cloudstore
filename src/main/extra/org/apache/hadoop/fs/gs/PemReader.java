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
/*
 * Copyright (c) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.hadoop.fs.gs;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;

import com.google.cloud.hadoop.repackaged.gcs.com.google.api.client.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * derivative of the google pem reader which requires a section title and
 * provides diagnostics.
 */
public class PemReader implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(PemReader.class);

  private static final String BEGIN = "-----BEGIN %s-----";
  private static final String END = "-----END %s-----";

  /** Reader. */
  private final BufferedReader reader;

  /** @param reader reader */
  public PemReader(Reader reader) {
    this.reader = new BufferedReader(reader);
  }

  /**
   * Reads the next section in the PEM file, optionally based on a title to look for.
   *
   * @param titleToLookFor title to look for.
   * @return next section or {@code null} for end of file
   */
  public Section readNextSection(String titleToLookFor)
      throws IOException {
    StringBuilder keyBuilder = new StringBuilder();
    int lines = 0;
    String scan = String.format(BEGIN, titleToLookFor);
    while (true) {
      String line = reader.readLine();
      lines++;
      if (line == null) {
        return new Section(titleToLookFor, lines,
            "no match for line '" + scan + "'");
      }
      if (scan.equals(line)) {
        break;
      }

    }

    LOG.info("title match  at line {}", lines);

    LOG.info("scanning for end ");
    int start = lines;
    scan = String.format(END, titleToLookFor);

    while (true) {
      String line = reader.readLine();
      lines++;
      if (line == null) {
        break;
      }
      if (scan.equals(line)) {
        return new Section(titleToLookFor,
            lines, Base64.decodeBase64(keyBuilder.toString()));
      }

      keyBuilder.append(line);
    }
    return new Section(titleToLookFor, lines - start,
        "no match for line <" + scan + ">");
  }


  /**
   * Reads the first section in the PEM file, optionally based on a title to look for, and then
   * closes the reader.
   *
   * @param titleToLookFor title to look for or {@code null} for any title
   * @param reader reader
   * @return first section found or {@code null} for none found
   */
  public static Section readFirstSectionAndClose(Reader reader,
      String titleToLookFor)
      throws IOException {

    try (PemReader pemReader = new PemReader(reader)) {
      return pemReader.readNextSection(titleToLookFor);

    }
  }

  /**
   * Closes the reader.
   *
   * <p>To ensure that the stream is closed properly, call {@link #close()} in a finally block.
   */
  public void close() throws IOException {
    reader.close();
  }

  /** Section in the PEM file. */
  public static final class Section {

    private final boolean success;

    /** Title. */
    private final String title;

    private final int lines;

    /** Base64-decoded bytes. */
    private final byte[] base64decodedBytes;

    private final String message;

    /**
     * @param title title
     * @param lines lines read
     * @param base64decodedBytes base64-decoded bytes
     */
    Section(String title, final int lines, byte[] base64decodedBytes) {
      this.success = true;
      this.title = title;
      this.lines = lines;
      this.base64decodedBytes = base64decodedBytes;
      message = "";
    }

    public Section(final String title, final int lines, final String message) {
      this.title = title;
      this.lines = lines;
      this.message = message;

      success = false;
      base64decodedBytes = null;
    }

    /** Returns the title. */
    public String getTitle() {
      return title;
    }

    /** Returns the base64-decoded bytes (modifiable array). */
    public byte[] getBase64DecodedBytes() {
      return base64decodedBytes;
    }

    @Override
    public String toString() {
      return "Section{" +
          "success=" + success +
          ", title='" + title + '\'' +
          ", lines=" + lines +
          ", message='" + message + '\'' +
          '}';
    }

    public boolean isSuccess() {
      return success;
    }

    public int getLines() {
      return lines;
    }

    public byte[] getBase64decodedBytes() {
      return base64decodedBytes;
    }

    public String getMessage() {
      return message;
    }
  }
}
