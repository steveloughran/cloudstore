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

import org.apache.hadoop.fs.statistics.IOStatisticsLogging;
import org.apache.hadoop.fs.statistics.IOStatisticsSupport;
import org.apache.hadoop.fs.store.shim.impl.Invocation;

import static org.apache.hadoop.fs.statistics.IOStatisticsSupport.retrieveIOStatistics;
import static org.apache.hadoop.fs.store.shim.impl.Invocation.unavailable;
import static org.apache.hadoop.fs.store.shim.impl.ShimReflectionSupport.loadClass;
import static org.apache.hadoop.fs.store.shim.impl.ShimReflectionSupport.loadInvocation;

/**
 * Support for IO statistics (initially through reflection).
 */
public class IOStatisticsIntegration {

  public IOStatisticsIntegration() {

  }

  public boolean available() {
    return true;
  }

  public String ioStatisticsToPrettyString(Object source) {
    return IOStatisticsLogging.ioStatisticsToPrettyString(
        retrieveIOStatistics(source));
  }
}
