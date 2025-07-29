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

import java.io.PrintStream;
import java.time.Duration;
import java.util.function.Supplier;

import org.slf4j.Logger;

/**
 * A duration with logging of final state at info in the {@code close()} call.
 * This allows it to be used in a try-with-resources clause, and have the
 * duration automatically logged.
 *
 * Based on the hadoop one; adds an empty constructor which doesn't do
 * any logging.
 */
public class StoreDurationInfo
    implements AutoCloseable {

  private final long started;

  private long finished;

  private boolean isFinished;


  private final Supplier<String> text;

  public static final Supplier<String> EMPTY_TEXT = () -> "";

  private String textStr;

  private final Logger log;

  /**
   * Should the log be at INFO rather than DEBUG.
   */
  private final boolean logAtInfo;

  private final PrintStream out;

  /**
   * Create the duration text from a {@code String.format()} code call.
   * @param log log to write to
   * @param format format string
   * @param args list of arguments
   */
  public StoreDurationInfo(Logger log, String format, Object... args) {

    this(log, true, format, args);
  }

  /**
   * Create the duration text from a {@code String.format()} code call
   * and log either at info or debug.
   * @param log log to write to
   * @param logAtInfo should the log be at info, rather than debug
   * @param format format string
   * @param args list of arguments
   */
  public StoreDurationInfo(Logger log,
      boolean logAtInfo,
      String format,
      Object... args) {
    this.started = time();
    this.finished = started;
    this.text = () -> String.format(format, args);
    this.log = log;
    this.logAtInfo = logAtInfo;
    this.out = null;
    if (log != null) {
      if (logAtInfo) {
        log.info("Starting: {}", getFormattedText());
      } else {
        if (log.isDebugEnabled()) {
          log.debug("Starting: {}", getFormattedText());
        }
      }
    }
  }


  /**
   * Create the duration text from a {@code String.format()} code call.
   * @param out log to write to
   * @param format format string
   * @param args list of arguments
   */
  public StoreDurationInfo(PrintStream out, String format, Object... args) {
    started = time();
    finished = started;
    this.text = () -> String.format(format, args);
    this.out = out;
    this.log = null;
    this.logAtInfo = false;

    if (out != null) {
      out.printf("[%s] Starting: %s%n",
          humanTime(started),
          getFormattedText());
    }
  }

  /**
   * Create the duration with no output printed.
   */
  public StoreDurationInfo() {
    this.text = EMPTY_TEXT;
    this.log = null;
    this.out = null;
    this.logAtInfo = false;
    started = time();
    finished = started;
  }


  private String getFormattedText() {
    return (textStr == null) ? (textStr = text.get()) : textStr;
  }

  private long time() {
    return System.currentTimeMillis();
  }

  /**
   * Finish the operation; only valid once.
   */
  public synchronized void finished() {
    if (!isFinished) {
      finished = time();
      isFinished = true;
    }
  }

  public String getDurationString() {
    return humanTime(value());
  }

  public static String humanTime(long time) {
    long millis = time % 1000;
    long seconds = (time / 1000);
    long minutes = (seconds / 60);
    long hours = (minutes / 60);
    return String.format("%02d:%02d:%02d.%03d",
        hours %24,
        minutes % 60,
        seconds % 60,
        millis);
  }

  /**
   * The duration in seconds; only valid once finished/closed.
   * @return time in milliseconds
   */
  public long value() {
    return finished - started;
  }

  /**
   * Get as a java time duration; only valid once finished/closed.
   * @return duration.
   */
  public Duration asDuration() {
    return Duration.ofMillis(value());
  }

  @Override
  public String toString() {
    return getDurationString();
  }


  /**
   * Close the duration, invoke {@link #finished()}.
   * Log the final state if a log or output stream was provided.
   */
  @Override
  public void close() {
    finished();
    if (log != null) {
      if (logAtInfo) {
        log.info("Duration of {}: {}", getFormattedText(), this);
        ;
      } else {
        log.debug("Duration of {}: {}", getFormattedText(), this);
      }

    }
    if (out != null) {
      out.printf("Duration of %s: %s%n", getFormattedText(), this);
    }
  }
}
