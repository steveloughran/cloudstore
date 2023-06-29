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

package org.apache.hadoop.fs.store.commands;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.net.NetUtils.getCanonicalUri;
import static org.apache.hadoop.net.NetUtils.getLocalInetAddress;

/**
 * Print the local hostname.
 *
 * Prints some performance numbers at the end.
 */
public class LocalHost extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(LocalHost.class);

  public static final String USAGE
      = "Usage: localhost\n"
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(TOKENFILE, "file", "Hadoop token file to load")
      + optusage(XMLFILE, "file", "XML config file to load")
      + optusage(VERBOSE, "verbose output");

  public LocalHost() {
    createCommandFormat(1, 999, VERBOSE);
    addValueOptions(TOKENFILE, XMLFILE, DEFINE);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (!paths.isEmpty()) {
      errorln(USAGE);
      return E_USAGE;
    }

    final InetAddress localHost = InetAddress.getLocalHost();
    println("InetAddress.getLocalHost(): %s",
        localHost);
    println("getLocalInetAddress(): %s",
        getLocalInetAddress(localHost.getHostName()));

    println("getLoopbackAddress(): %s",
        InetAddress.getLoopbackAddress().getHostName());

    println("getCanonicalUri(): %s",
        getCanonicalUri(new URI("http",
            localHost.getCanonicalHostName(), ""),
            0));
    return 0;
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new LocalHost(), args);
  }

  /**
   * Main entry point. Calls {@code System.exit()} on all execution paths.
   * @param args argument list
   */
  public static void main(String[] args) {
    try {
      exit(exec(args), "");
    } catch (Throwable e) {
      exitOnThrowable(e);
    }
  }

}
