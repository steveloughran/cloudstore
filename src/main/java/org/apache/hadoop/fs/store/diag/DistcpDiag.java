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

package org.apache.hadoop.fs.store.diag;

import java.io.PrintStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.diag.OptionSets.CLUSTER_OPTIONS;

public class DistcpDiag extends DiagnosticsEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(DistcpDiag.class);

  protected static final Object[][] DISTCP_OPTIONS = {
      {"distcp.dynamic.strategy.impl", false, false},
      {"distcp.static.strategy.impl", false, false},
      {"mapreduce.reduce.class", false, false},
      {"distcp.filters.class", false, false},
      {"distcp.exclude-file-regex", false, false},
      {"distcp.copy.buffer.size", false, false},
      {"distcp.blocks.per.chunk", false, false},
      {"mapreduce.map.java.opts", false, false},
      {"mapreduce.reduce.java.opts", false, false},
      {"", false, false},
      {"", false, false},
  };

  @Override
  public final int run(String[] args) throws Exception {
    return run(args, System.out);
  }

  public int run(String[] args, PrintStream stream) throws Exception {
    addAllDefaultXMLFiles();
    // add the distcp resources
    addDefaultResources(
        "distcp-default.xml",
        "distcp-site");
    setOut(stream);
    heading("Distcp Diagnostics");

    printHadoopVersionInfo();

    heading("Resources");
    probeResource("distcp-default.xml", false);
    probeResource("distcp-site.xml", false);
    probeOptionalClasses(
        "org.apache.hadoop.tools.DistCp",
        "org.apache.hadoop.tools.mapred.CopyMapper",
        "org.apache.hadoop.tools.mapred.CopyCommitter",
        "org.apache.hadoop.tools.mapred.lib.DynamicInputFormat",
        "org.apache.hadoop.tools.mapred.DeletedDirTracker",
        "com.cloudera.hadoop.tools.mapred.lib.DynamicInputFormat",
        "com.cloudera.hadoop.tools.mapred.UniformSizeInputFormat",
        "com.cloudera.hadoop.tools.DistCp");

    println("");
    println("warning: classes stored in a distcp.tar.gz file in the cluster filesystem");
    println(" override those in the host filesystem in the distcp workers");

    final Configuration conf = getConf();
    printOptions("Hadoop Options", conf, CLUSTER_OPTIONS);
    printOptions("Distcp Options", conf, DISTCP_OPTIONS);
    return 0;
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

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new DistcpDiag(), args);
  }

}
