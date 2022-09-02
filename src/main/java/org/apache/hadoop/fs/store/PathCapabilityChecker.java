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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.VersionInfo;

import static org.apache.hadoop.fs.store.StoreExitCodes.E_EXCEPTION_THROWN;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_UNSUPPORTED_VERSION;

public class PathCapabilityChecker {

  private final Method hasPathCapability;
  private final Object source;

  public PathCapabilityChecker(Object source) {
    this.source = source;
    Method method;
    try {
      method = source.getClass().getMethod("hasPathCapability",
          Path.class, String.class);
    } catch (NoSuchMethodException e) {
      method = null;
    }
    hasPathCapability = method;
  }

  public boolean methodAvailable() {
    return hasPathCapability != null;
  }

  /**
   * Does an object have a capability?
   * uses reflection so the jar can compile/run against
   * older hadoop releases.
   * throws StoreExitException(E_UNSUPPORTED_VERSION) if the api isn't found.
   * @param fs filesystem
   * @param path path
   * @param capability capability to probe
   * @return true iff the interface is available
   * @throws IOException fallure
   */
  public boolean hasPathCapability(Path path, String capability)
      throws IOException {
    if (!methodAvailable()) {
      throw new StoreExitException(E_UNSUPPORTED_VERSION,
          "Hadoop version does not support PathCapabilities: "
              + VersionInfo.getVersion());
    }
    try {
      return (Boolean) hasPathCapability.invoke(source, path, capability);
    } catch (IllegalAccessException e) {
      throw new StoreExitException(E_UNSUPPORTED_VERSION,
          "Hadoop version does not support PathCapabilities: "
              + VersionInfo.getVersion());
    } catch (InvocationTargetException e) {
      Throwable ex = e.getTargetException();
      if (ex instanceof IOException) {
        throw (IOException) ex;
      } else {
        throw new StoreExitException(E_EXCEPTION_THROWN,
            ex.toString(), ex);
      }
    }
  }

}
