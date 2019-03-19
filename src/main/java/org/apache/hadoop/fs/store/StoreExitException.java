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

import org.apache.hadoop.util.ExitUtil;

/**
 * This is a subclass to handle working with 2.8.x releases.
 */
public class StoreExitException extends ExitUtil.ExitException {

  public StoreExitException(final int status, final String message) {
    super(status, message);
  }

  public StoreExitException(final int status,
      final String message,
      final Throwable cause) {
    super(status, message);
    initCause(cause);
  }

  public StoreExitException(final int status, final Throwable cause) {
    super(status, cause.getMessage());
    initCause(cause);
  }

  public int getExitCode() {
    return status;
  }

}
