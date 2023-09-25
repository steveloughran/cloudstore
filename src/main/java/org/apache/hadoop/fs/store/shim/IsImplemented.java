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

package org.apache.hadoop.fs.shim.api;

/**
 * Interface to probe for feature being directly available in
 * the Hadoop runtime, through the shim API.
 * If a capability is not implemented it may be because
 * <ol>
 *   <li>The capability is unknown in this release of the shim library.</li>
 *   <li>The capability is unknown in the shim class.</li>
 *   <li>The capability is known in the shim class but the hadoop runtime
 *   lacks the API.</li>
 *   <li>The capability is known, the API exists but is not supported by the object
 *   instance to the which shim class is bound.</li>
 *   <li>The capability is known but disabled/not working.</li>
 * </ol>
 * The API may be dynamic, where a fallback happens after the failure of a direct
 * invocation.
 */
public interface IsImplemented {

  /**
   * Is a feature directly available by the wrapped class, rather
   * than being emulated by the shim library.
   *
   * @param capability capability/feature to probe for
   *
   * @return true if the wrapped class supports it directly
   */
  default boolean isImplemented(String capability) {
    return false;
  }
}
