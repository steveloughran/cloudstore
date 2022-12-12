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

package org.apache.hadoop.fs.store.abfs;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.azurebfs.AzureBlobFileSystem;
import org.apache.hadoop.fs.azurebfs.constants.ConfigurationKeys;
import org.apache.hadoop.fs.store.PathCapabilityChecker;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.fs.store.commands.EnvEntry;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.LOGFILE;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_ERROR;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.fs.store.diag.AbfsDiagnosticsInfo.FS_AZURE_ENABLE_READAHEAD;
import static org.apache.hadoop.fs.store.diag.AbfsDiagnosticsInfo.FS_AZURE_READAHEADQUEUE_DEPTH;

/**
 * Check for/report on prefetch safety of an abfs instance.
 */
public class SafePrefetch extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(SafePrefetch.class);

  public static final String USAGE
      = "Usage: safeprefetch [options] <path>\n"
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(TOKENFILE, "file", "Hadoop token file to load")
      + optusage(VERBOSE, "print verbose output")
      + optusage(XMLFILE, "file", "XML config file to load");

  public SafePrefetch() {
    createCommandFormat(1, 1);
    addValueOptions(LOGFILE);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> argList = parseArgs(args);
    if (argList.size() < 1) {
      errorln(USAGE);
      return E_USAGE;
    }
    maybeAddTokens(TOKENFILE);
    final Configuration conf = createPreconfiguredConfig();

    // path on the CLI
    Path path = new Path(argList.get(0));
    println("Probing %s for prefetch safety %s", path);
    final String scheme = path.toUri().getScheme();


    FileSystem fs = path.getFileSystem(conf);
    if (!(fs instanceof AzureBlobFileSystem)) {
      println("Filesystem for path %s is not to an Azure store; not at risk: %s",
          path, fs);
      return 0;
    }
    String etag_capability = "fs.capability.etags.available";
    String hadoop_18546 = "hadoop-18546";

    println("Using filesystem %s", fs.getUri());
    Path abfsPath = path.makeQualified(fs.getUri(), fs.getWorkingDirectory());
    final PathCapabilityChecker checker = new PathCapabilityChecker(fs);
    if (!checker.methodAvailable()) {
      println("Hadoop version is too old for the feature to surface (no PathCapabilities)");
      return 0;
    }
    if (!checker.hasPathCapability(abfsPath, etag_capability)) {
      println("Filesystem is not from a release with the prefetch issue (no path capability %s)",
          etag_capability);
      return 0;
    }
    if (checker.hasPathCapability(abfsPath, hadoop_18546)) {

      println("Filesystem has prefetch issue fixed (has path capability %s)",
          abfsPath, hadoop_18546);
      return 0;
    }
    println("Store is vulnerable to inconsistent prefetching. This MUST be disabled");
    final Configuration fsConf = fs.getConf();
    List<EnvEntry> entries = new ArrayList<>();
    entries.add(new EnvEntry(FS_AZURE_READAHEADQUEUE_DEPTH, "", "0"));

    if (fsConf.getInt(FS_AZURE_READAHEADQUEUE_DEPTH, 2) == 0) {
      println("Queue depth is zero so prefetching will not take place (%s = 0)",
          FS_AZURE_READAHEADQUEUE_DEPTH);
      return 0;
    }

    try {
      // look for the readahead
      ConfigurationKeys.class.getField(FS_AZURE_ENABLE_READAHEAD);

      if (!fsConf.getBoolean(FS_AZURE_ENABLE_READAHEAD, true)) {
        println("Readahead is disabled in %s", FS_AZURE_ENABLE_READAHEAD);
        return 0;

      }
      entries.add(new EnvEntry(FS_AZURE_READAHEADQUEUE_DEPTH, "", "0"));
    } catch (Exception e) {

      // no disable option; don't confuse the user by mentioning it.

    }
    EnvEntry property = new EnvEntry(FS_AZURE_READAHEADQUEUE_DEPTH, "", "0");
    println("To disable prefetching, set %s to %s", property.getName(), property.getValue());
    println("\n%s\n", property.xml());
    println("\n%s\n", property.spark());

    warn("Filesystem is vulnerable until prefetching is disabled");
    StringBuilder xml = new StringBuilder();
    xml.append("<configuration>\n\n");
    entries.forEach(e -> xml.append(e.xml()));
    xml.append("\n</configuration>\n");

    println("hadoop XML: %n%s%n", xml);

    println();

    StringBuilder spark = new StringBuilder();
    entries.forEach(e -> spark.append(e.spark()));
    println("spark: %n%s%n", spark);
    return E_ERROR;
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new SafePrefetch(), args);
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
