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

import org.apache.hadoop.util.ExitUtil;

/**
 * This is an RTE so we can do tricks in java 8 lambdas.
 */
public class StoreDiagException extends ExitUtil.ExitException {

  public StoreDiagException(final String message, final Object...args) {
    this(-1, message, args);
  }

  public StoreDiagException(final int status, final String message, final Object...args) {
    super(status, formatStr(message, args));
  }

  private static String formatStr(final String message, final Object[] args) {
    try {
      return String.format(message, args);
    } catch (Exception e) {
      return message;
    }

  }
}
