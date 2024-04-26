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

import org.apache.hadoop.fs.statistics.IOStatistics;
import org.apache.hadoop.fs.store.shim.impl.Invocation;
import org.apache.hadoop.fs.store.shim.impl.ShimReflectionSupport;

import static org.apache.hadoop.fs.store.shim.impl.Invocation.unavailable;
import static org.apache.hadoop.fs.store.shim.impl.ShimReflectionSupport.loadClass;
import static org.apache.hadoop.fs.store.shim.impl.ShimReflectionSupport.loadInvocation;

/**
 * Support for IO statistics (initially through reflection).
 */
public class IOStatisticsIntegration {

  public static final String CLASSNAME_IOSTATISTICS = "org.apache.hadoop.fs.statistics.IOStatistics";

  public static final String CLASSNAME_IOSTATISTICS_LOGGING = "org.apache.hadoop.fs.statistics.IOStatisticsLogging";
  public static final String CLASSNAME_IOSTATISTICS_SUPPORT = "org.apache.hadoop.fs.statistics.IOStatisticsSupport";

  private final Class<?> ioStatisticsClass;

  private final Class<?> ioStatisticsLogging;
  private final Class<?> ioStatisticsSupport;

  private final Invocation<String> _ioStatisticsToPrettyString;

  private final Invocation<?> _retrieveIOStatistics;

  public IOStatisticsIntegration() {
    // try to load the class
    ioStatisticsClass = loadClass(CLASSNAME_IOSTATISTICS);
    if (ioStatisticsClass == null) {
      // if that class is missing, so is the rest.
      ioStatisticsSupport = null;
      ioStatisticsLogging = null;
      _ioStatisticsToPrettyString = unavailable("ioStatisticsToPrettyString");
      _retrieveIOStatistics = unavailable("retrieveIOStatistics");
    } else {
      ioStatisticsSupport = loadClass(CLASSNAME_IOSTATISTICS_SUPPORT);
      _retrieveIOStatistics = loadInvocation(ioStatisticsSupport,
          ioStatisticsClass, "retrieveIOStatistics", Object.class);

      ioStatisticsLogging = loadClass(CLASSNAME_IOSTATISTICS_LOGGING);

      _ioStatisticsToPrettyString = loadInvocation(ioStatisticsLogging,
          String.class, "ioStatisticsToPrettyString", ioStatisticsClass);
    }
  }

  public boolean available() {
    return ioStatisticsClass != null;
  }

  public String ioStatisticsToPrettyString(Object source) {
    if (!available()) {
      return "";
    }
    return _ioStatisticsToPrettyString.invokeUnchecked(null,
        _retrieveIOStatistics.invokeUnchecked(null, source));
  }
}
