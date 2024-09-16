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

package org.apache.hadoop.fs.store.logging;

/**
 * Interface to assist reflection-based control of logger back ends.
 * An instance of LogControl is able to control the log levels of
 * loggers for log libraries such as Log4j, yet can be used in
 * code designed to support multiple back end loggers behind
 * SLF4J.
 */
public interface LogControl {

  /**
   * Enumeration of log levels.
   * The list is in descending order.
   */
  enum LogLevel {
    ALL("ALL"),
    FATAL("FATAL"),
    ERROR("ERROR"),
    WARN("WARN"),
    INFO("INFO"),
    DEBUG("DEBUG"),
    TRACE("TRACE"),
    OFF("OFF");

    /**
     * Level name.
     */
    public final String key;

    LogLevel(final String key) {
      this.key = key;
    }

  }

  /**
   * Sets a log level for a class/package.
   * @param log log to set
   * @param level level to set
   */
  void setLogLevel(String log, LogLevel level);


}
