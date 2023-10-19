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

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.fs.store.StoreEntryPoint;

public class TestLog4JController {

  private static final Logger LOG = LoggerFactory.getLogger(TestLog4JController.class);

  @Test
  public void testCreateLogger() throws Throwable {
    Optional<LogControl> control =
        LogControllerFactory.createController(LogControllerFactory.LOG4J);
    Assertions.assertThat(control)
        .describedAs("created controller")
        .isPresent()
        .containsInstanceOf(Log4JController.class);
  }

  @Test
  public void testConfigureLevel() throws Throwable {
    LogControl control =
        LogControllerFactory.createController(LogControllerFactory.LOG4J).get();
    final String name = this.getClass().getName();
    control.setLogLevel(name, LogControl.LogLevel.DEBUG);
    LOG.debug("debug at debug level");
    control.setLogLevel(name, LogControl.LogLevel.INFO);
    LOG.info("info at info level");
    LOG.debug("debug at info level");

    LOG.info("switching back to debug");
    control.setLogLevel(name, LogControl.LogLevel.DEBUG);
    LOG.debug("debug at debug level again");
  }
}
