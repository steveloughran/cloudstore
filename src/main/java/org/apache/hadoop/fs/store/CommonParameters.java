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
 * Common parameters across entry points.
 */
public final class CommonParameters {

  /** {@value}. */
  public static final String TOKENFILE = "tokenfile";

  /** {@value}. */
  public static final String XMLFILE = "xmlfile";

  /** {@value}. */
  public static final String CSVFILE = "csv";

  /** file for system properties {@value}. */
  public static final String SYSPROPFILE = "syspropfile";

  /** Single system property {@value}. */
  public static final String SYSPROP = "sysprop";

  /** {@value}. */
  public static final String DEFINE = "D";

  /** {@value}. */
  public static final String VERBOSE = "verbose";

  /**
   * File for log4j properties: {@value}.
   */
  public static final String LOGFILE = "logfile";

  /** {@value}. */
  public static final String LIMIT = "limit";

  /** {@value}. */
  public static final String THREADS = "threads";

  public static final String BFS = "bfs";

  private CommonParameters() {
  }
}
