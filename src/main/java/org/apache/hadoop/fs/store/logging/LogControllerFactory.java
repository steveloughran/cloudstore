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

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.fs.store.diag.StoreLogExactlyOnce;

public final class LogControllerFactory {
  private static final Logger LOG = LoggerFactory.getLogger(LogControllerFactory.class);
  private static final StoreLogExactlyOnce LOG_ONCE = new StoreLogExactlyOnce(LOG);

  public static final String LOG4J = "org.apache.hadoop.fs.store.logging.Log4JController";

  /**
   * create a controller.
   * @return the instantiated controllerl or empty of the class can't be instantiated.
   */
  public static Optional<LogControl> createController(String classname) {
    try {
      Class<?> clazz = Class.forName(classname);
      return Optional.of((LogControl) clazz.newInstance());
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
      | ClassCastException e) {
      LOG_ONCE.warn("Failed to create controller {}: {}", classname, e, e);
      return Optional.empty();
    }
  }

}
