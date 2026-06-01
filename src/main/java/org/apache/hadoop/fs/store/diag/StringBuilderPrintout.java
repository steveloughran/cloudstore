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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.apache.hadoop.fs.store.StoreUtils;

/**
 * {@link Printout} that captures all output into an in-memory buffer. Test-only collaborator: tests
 * install one as the active printout on {@link org.apache.hadoop.fs.store.StoreEntryPoint}, run a
 * command, and assert on {@link #toString()}.
 */
public final class StringBuilderPrintout extends StdoutPrintout {

  private final ByteArrayOutputStream arrayOutputStream;

  public StringBuilderPrintout() {
    this(new ByteArrayOutputStream());
  }

  private StringBuilderPrintout(ByteArrayOutputStream arrayOutputStream) {
    super(toPrintStream(arrayOutputStream));
    this.arrayOutputStream = arrayOutputStream;
  }

  private static PrintStream toPrintStream(ByteArrayOutputStream baos) {
    try {
      return new PrintStream(baos, true, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("UTF-8 unavailable", e);
    }
  }

  @Override
  public String toString() {
    flush();
    return new String(arrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
  }

  /**
   * Reset the captured buffer.
   */
  public void clear() {
    flush();
    arrayOutputStream.reset();
  }

  /**
   * Substring containment check on the captured output.
   *
   * @param substring needle
   * @return true if found
   */
  public boolean contains(String substring) {
    return toString().contains(substring);
  }

  /**
   * Always sanitize: tests must never capture cleartext secrets, even if the caller passed
   * {@code obfuscate=false}.
   */
  @Override
  public String maybeSanitize(String value, boolean obfuscate) {
    return StoreUtils.sanitize(value, true);
  }

  @Override
  public boolean isHideAllSensitiveChars() {
    return true;
  }
}
