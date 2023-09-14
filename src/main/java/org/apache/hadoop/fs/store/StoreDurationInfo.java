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

import org.slf4j.Logger;

/**
 * A duration with logging of final state at info in the {@code close()} call.
 * This allows it to be used in a try-with-resources clause, and have the
 * duration automatically logged.
 *
 * Base on the S3A one; adds an empty constructor which doesn't do
 * any logging.
 */
public class StoreDurationInfo
    implements AutoCloseable {

  private final long started;

  private long finished;

  private boolean isFinished;

  private final String text;

  private final Logger log;

  private final PrintStream out;

  /**
   * Create the duration text from a {@code String.format()} code call.
   * @param log log to write to
   * @param format format string
   * @param args list of arguments
   */
  public StoreDurationInfo(Logger log, String format, Object... args) {
    started = time();
    finished = started;
    this.text = String.format(format, args);
    this.log = log;
    out = null;
    if (log != null) {
      log.info("Starting: {}", text);
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
    this.text = String.format(format, args);
    this.out = out;
    this.log = null;
    if (out != null) {
      out.printf("Starting: %s%n", text);
    }
  }

  /**
   * Create the duration with no output printed.
   */
  public StoreDurationInfo() {
    this.text = "";
    this.log = null;
    this.out = null;
    started = time();
    finished = started;
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
    long seconds = (time / 1000);
    long minutes = (seconds / 60);
    return String.format("%d:%02d.%03d", minutes, seconds % 60, time % 1000);
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
      log.info("Duration of {}: {}", text, this);
    }
    if (out != null) {
      out.printf("Duration of %s: %s%n", text, this);
    }
  }
}
