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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple min/mean/max statistics.
 */
public class MinMeanMax {

  final String name;
  final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
  final AtomicLong max = new AtomicLong(Long.MIN_VALUE);
  final AtomicInteger samples = new AtomicInteger(0);
  final AtomicLong sum = new AtomicLong(0);

  public MinMeanMax(final String name) {
    this.name = name;
  }

  /**
   * copilot made this up for us.
   * @param value new value
   */
  public synchronized void add(final long value) {
    min.accumulateAndGet(value, Math::min);
    max.accumulateAndGet(value, Math::max);
    sum.addAndGet(value);
    samples.incrementAndGet();
  }

  public String getName() {
    return name;
  }

  public long min() {
    return min.get();
  }

  public long max() {
    return max.get();
  }

  public int samples() {
    return samples.get();
  }

  public long sum() {
    return sum.get();
  }

  public double mean() {
    final int sam = samples.get();
    return sam > 0 ? ((double) sum.get()) / sam : 0;
  }
}
