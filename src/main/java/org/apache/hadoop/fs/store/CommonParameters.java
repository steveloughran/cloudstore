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

import static org.apache.hadoop.fs.store.StoreEntryPoint.optusage;

/**
 * Common parameters across entry points.
 */
public final class CommonParameters {

  /** {@value}. */
  public static final String TOKENFILE = "tokenfile";

  /** {@value}. */
  public static final String XMLFILE = "xmlfile";

  /** file for system properties {@value}. */
  public static final String SYSPROPS = "sysprop";

  /** {@value}. */
  public static final String DEFINE = "D";

  /** {@value}. */
  public static final String VERBOSE = "verbose";

  /** {@value}. */
  public static final String DEBUG = "debug";

  /** {@value}. */
  public static final String LOG_OVERRIDES = "logoverrides";

  /**
   * Standard options of all entry points.
   */
  public static final String STANDARD_OPTS =
      optusage(DEFINE, "key=value", "Define a single configuration option")
          + optusage(SYSPROPS, "file", "Property file of system properties")
          + optusage(TOKENFILE, "file", "Hadoop token file to load")
          + optusage(XMLFILE, "file", "XML config file to load")
          + optusage(VERBOSE, "verbose output")
          + optusage(DEBUG, "enable JVM logs (ALL) and override log4j levels (DEBUG) on specified packages or classes")
          + optusage(LOG_OVERRIDES, "file", "A newline separated list of package and class names")
          ;

  /**
   * File for log4j properties: {@value}.
   */
  public static final String LOGFILE = "logfile";

  /** {@value}. */
  public static final String LIMIT = "limit";

  /** {@value}. */
  public static final String THREADS = "threads";

  public static final String BFS = "bfs";

  public static final String BLOCK = "block";

  public static final String CSVFILE = "csv";

  public static final String FLUSH = "flush";

  public static final String HFLUSH = "hflush";

  public static final String IGNORE = "ignore";

  public static final String LARGEST = "largest";

  public static final String OVERWRITE = "overwrite";

  public static final String PAGE = "page";

  public static final String UPDATE = "update";


  private CommonParameters() {
  }
}
