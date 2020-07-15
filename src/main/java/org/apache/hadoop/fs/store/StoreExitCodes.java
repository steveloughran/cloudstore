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

/**
 * See LauncherExitCodes; here just to build against older versions
 */
public class StoreExitCodes {

  public static final int E_SUCCESS = 0;
  public static final int E_ERROR = -1;

  public static final int E_USAGE = 42;

  public static final int E_INVALID_ARGUMENT = -1;

  public static final int E_NOT_FOUND = 44;

  public static final int E_NO_ACCESS = 41;

  /**
   * Exit code when an exception was thrown from the service: {@value}.
   * <p>
   * Approximate HTTP equivalent: {@code 500 Internal Server Error}
   */
  public static final int E_EXCEPTION_THROWN = 50;

  /**
   * Unimplemented feature: {@value}.
   * <p>
   * Approximate HTTP equivalent: {@code 501: Not Implemented}
   */
  public static final int E_UNIMPLEMENTED = 51;

  /**
   * Service Unavailable; it may be available later: {@value}.
   * <p>
   * Approximate HTTP equivalent: {@code 503 Service Unavailable}
   */
  public static final int E_SERVICE_UNAVAILABLE = 53;

  /**
   * The application does not support, or refuses to support this
   * version: {@value}.
   * <p>
   * If raised, this is expected to be raised server-side and likely due
   * to client/server version incompatibilities.
   * <p>
   * Approximate HTTP equivalent: {@code 505: Version Not Supported}
   */
  public static final int E_UNSUPPORTED_VERSION = 55;

}
