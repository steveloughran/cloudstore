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

package org.apache.hadoop.fs.tools.cloudup;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple class for timing operations in nanoseconds, and for
 * printing some useful results in the process.
 */
public final class NanoTimer {
  private static final Logger LOG = LoggerFactory.getLogger(NanoTimer.class);

  private final long startTime;
  private long endTime;

  public NanoTimer() {
    startTime = now();
  }

  /**
   * End the operation.
   * @return the duration of the operation
   */
  public long end() {
    endTime = now();
    return duration();
  }

  /**
   * End the operation; log the duration.
   * @param format message
   * @param args any arguments
   * @return the duration of the operation
   */
  public long end(String format, Object... args) {
    long d = end();
    LOG.info("Duration of {}: {} nS",
        String.format(format, args), toHuman(d));
    return d;
  }

  public long now() {
    return System.nanoTime();
  }

  public long duration() {
    return endTime - startTime;
  }

  /**
   * Intermediate duration of the operation.
   * @return how much time has passed since the start (in nanos).
   */
  public long elapsedTime() {
    return now() - startTime;
  }

  public double bandwidth(long bytes) {
    return bandwidthMBs(bytes, duration());
  }

  /**
   * Bandwidth as bytes per second.
   * @param bytes bytes in
   * @return the number of bytes per second this operation.
   *         0 if duration == 0.
   */
  public double bandwidthBytes(long bytes) {
    double duration = duration();
    return duration > 0 ? bytes / duration : 0;
  }

  /**
   * How many nanoseconds per IOP, byte, etc.
   * @param operations operations processed in this time period
   * @return the nanoseconds it took each byte to be processed
   */
  public long nanosPerOperation(long operations) {
    return duration() / operations;
  }

  /**
   * Get a description of the bandwidth, even down to fractions of
   * a MB.
   * @param bytes bytes processed
   * @return bandwidth
   */
  public String bandwidthDescription(long bytes) {
    return String.format("%,.6f", bandwidth(bytes));
  }

  public long getStartTime() {
    return startTime;
  }

  public long getEndTime() {
    return endTime;
  }


  /**
   * Make times more readable, by adding a "," every three digits.
   * @param nanos nanos or other large number
   * @return a string for logging
   */
  public static String toHuman(long nanos) {
    return String.format(Locale.ENGLISH, "%,d", nanos);
  }

  /**
   * Log the bandwidth of a timer as inferred from the number of
   * bytes processed.
   * @param timer timer
   * @param bytes bytes processed in the time period
   */
  public static void bandwidth(NanoTimer timer, long bytes) {
    LOG.info("Bandwidth = {}  MB/S",
        timer.bandwidthDescription(bytes));
  }

  /**
   * Work out the bandwidth in MB/s.
   * @param bytes bytes
   * @param durationNS duration in nanos
   * @return the number of megabytes/second of the recorded operation
   */
  public static double bandwidthMBs(long bytes, long durationNS) {
    return bytes / (1024.0 * 1024) * 1.0e9 / durationNS;
  }

}
