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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.hadoop.fs.gs.GsCredDiag;
import org.apache.hadoop.fs.s3a.sdk.BucketMetadata;
import org.apache.hadoop.fs.s3a.sdk.BulkDeleteCommand;
import org.apache.hadoop.fs.s3a.sdk.DeleteObject;
import org.apache.hadoop.fs.s3a.sdk.IamPolicy;
import org.apache.hadoop.fs.s3a.sdk.ListMultiparts;
import org.apache.hadoop.fs.s3a.sdk.ListObjects;
import org.apache.hadoop.fs.s3a.sdk.ListVersions;
import org.apache.hadoop.fs.s3a.sdk.MkBucket;
import org.apache.hadoop.fs.s3a.sdk.Regions;
import org.apache.hadoop.fs.s3a.sdk.RestoreObject;
import org.apache.hadoop.fs.s3a.sdk.SessionKeys;
import org.apache.hadoop.fs.s3a.sdk.Undelete;
import org.apache.hadoop.fs.shell.CommandFormat;
import org.apache.hadoop.fs.store.abfs.SafePrefetch;
import org.apache.hadoop.fs.store.audit.AuditTool;
import org.apache.hadoop.fs.store.commands.Bandwidth;
import org.apache.hadoop.fs.store.commands.BucketState;
import org.apache.hadoop.fs.store.commands.CommitterInfo;
import org.apache.hadoop.fs.store.commands.Constval;
import org.apache.hadoop.fs.store.commands.EtagCommand;
import org.apache.hadoop.fs.store.commands.ExtendedDu;
import org.apache.hadoop.fs.store.commands.FetchTokens;
import org.apache.hadoop.fs.store.commands.ListFiles;
import org.apache.hadoop.fs.store.commands.LocalHost;
import org.apache.hadoop.fs.store.commands.LocateFiles;
import org.apache.hadoop.fs.store.commands.PathCapability;
import org.apache.hadoop.fs.store.commands.PrintStatus;
import org.apache.hadoop.fs.store.commands.Put;
import org.apache.hadoop.fs.store.commands.TLSInfo;
import org.apache.hadoop.fs.store.commands.TarHardened;
import org.apache.hadoop.fs.store.diag.DistcpDiag;
import org.apache.hadoop.fs.store.diag.StoreDiag;
import org.apache.hadoop.fs.tools.cloudup.Cloudup;
import org.apache.hadoop.fs.tools.csv.MkCSV;
import org.apache.hadoop.service.launcher.LauncherExitCodes;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single launcher entry point. Replaces the per-command lowercase default-package shims with a name
 * → {@link Tool} class registry dispatched through {@link ToolRunner#run}.
 *
 * <p>
 * Invocation, using the bundled jar:
 *
 * <pre>{@code
 * hadoop jar cloudstore-1.1.jar org.apache.hadoop.fs.store.Cloudstore <command> [args...]
 * hadoop jar cloudstore-1.1.jar org.apache.hadoop.fs.store.Cloudstore help
 * }</pre>
 *
 * <p>
 * The lowercase default-package shims (e.g. {@code dux}, {@code storediag}) are still accepted by
 * {@code hadoop jar} for back-compat; this class is the supported single entry point going forward.
 *
 * <p>
 * Each invocation instantiates the {@link Tool} subclass via its public no-arg constructor and runs
 * it with the rest of the argv via {@link ToolRunner}. Throwables coming out of
 * {@code ToolRunner.run} are routed through the same three-way handler used by every existing
 * {@code main()}: usage errors map to {@link LauncherExitCodes#EXIT_USAGE},
 * {@link ExitUtil.ExitException}s preserve their embedded code, and any other throwable produces
 * {@link LauncherExitCodes#EXIT_FAIL}.
 */
public final class Cloudstore {

  private static final Logger LOG = LoggerFactory.getLogger(Cloudstore.class);

  /**
   * Command name → implementing Tool class. Insertion order is preserved so the {@code help} output
   * reads alphabetically.
   */
  private static final Map<String, Class<? extends Tool>> COMMANDS;

  static {
    Map<String, Class<? extends Tool>> m = new LinkedHashMap<>();
    m.put("auditlogs", AuditTool.class);
    m.put("bandwidth", Bandwidth.class);
    m.put("bucketmetadata", BucketMetadata.class);
    m.put("bucketstate", BucketState.class);
    m.put("bulkdelete", BulkDeleteCommand.class);
    m.put("cloudup", Cloudup.class);
    m.put("committerinfo", CommitterInfo.class);
    m.put("constval", Constval.class);
    m.put("deleteobject", DeleteObject.class);
    m.put("distcpdiag", DistcpDiag.class);
    m.put("dux", ExtendedDu.class);
    m.put("etag", EtagCommand.class);
    m.put("fetchdt", FetchTokens.class);
    m.put("filestatus", PrintStatus.class);
    m.put("gcscreds", GsCredDiag.class);
    m.put("iampolicy", IamPolicy.class);
    m.put("list", ListFiles.class);
    m.put("listmultiparts", ListMultiparts.class);
    m.put("listobjects", ListObjects.class);
    m.put("listversions", ListVersions.class);
    m.put("localhost", LocalHost.class);
    m.put("locatefiles", LocateFiles.class);
    m.put("mkbucket", MkBucket.class);
    m.put("mkcsv", MkCSV.class);
    m.put("pathcapability", PathCapability.class);
    m.put("put", Put.class);
    m.put("regions", Regions.class);
    m.put("restore", RestoreObject.class);
    m.put("safeprefetch", SafePrefetch.class);
    m.put("sessionkeys", SessionKeys.class);
    m.put("storediag", StoreDiag.class);
    m.put("tarhardened", TarHardened.class);
    m.put("tlsinfo", TLSInfo.class);
    m.put("undelete", Undelete.class);
    COMMANDS = Collections.unmodifiableMap(m);
  }

  private Cloudstore() {}

  /**
   * Dispatch one command. Returns the exit code from {@link ToolRunner#run} for known commands, or
   * {@link LauncherExitCodes#EXIT_USAGE} when the command name is missing, unknown, or {@code help}
   * / {@code -help} / {@code --help}.
   *
   * <p>
   * Throwables raised by the dispatched Tool propagate up, just like the existing per-command
   * {@code exec} methods.
   */
  public static int exec(String... args) throws Exception {
    if (args == null || args.length == 0) {
      printUsage(System.err);
      return LauncherExitCodes.EXIT_USAGE;
    }
    String name = args[0];
    if ("help".equals(name) || "-help".equals(name) || "--help".equals(name)) {
      printUsage(System.out);
      return 0;
    }
    Class<? extends Tool> clazz = COMMANDS.get(name);
    if (clazz == null) {
      System.err.println("Unknown command: " + name);
      printUsage(System.err);
      return LauncherExitCodes.EXIT_USAGE;
    }
    Tool tool = clazz.getDeclaredConstructor().newInstance();
    String[] rest = Arrays.copyOfRange(args, 1, args.length);
    return ToolRunner.run(tool, rest);
  }

  /**
   * Process entry point. Calls {@link ExitUtil#terminate} on every exit path, mirroring the
   * convention used by every existing {@code main()} in the project so the wrapper script semantics
   * stay identical.
   */
  public static void main(String[] args) {
    try {
      ExitUtil.terminate(exec(args), "");
    } catch (Throwable e) {
      exitOnThrowable(e);
    }
  }

  /**
   * Map a Throwable to an exit code following the same three-way split used by
   * {@code StoreEntryPoint.exitOnThrowable}.
   */
  static void exitOnThrowable(Throwable ex) {
    if (ex instanceof CommandFormat.UnknownOptionException) {
      System.err.println(ex.getMessage());
      ExitUtil.terminate(LauncherExitCodes.EXIT_USAGE, ex.getMessage());
    } else if (ex instanceof ExitUtil.ExitException) {
      LOG.debug("Command failure", ex);
      ExitUtil.terminate((ExitUtil.ExitException) ex);
    } else {
      ex.printStackTrace(System.err);
      ExitUtil.terminate(LauncherExitCodes.EXIT_FAIL, ex.toString());
    }
  }

  /** Visible for testing. */
  static Map<String, Class<? extends Tool>> commands() {
    return COMMANDS;
  }

  private static void printUsage(java.io.PrintStream out) {
    out.println("Usage: cloudstore <command> [args...]");
    out.println();
    out.println("Commands:");
    for (Map.Entry<String, Class<? extends Tool>> e : COMMANDS.entrySet()) {
      out.printf("  %-16s  %s%n", e.getKey(), e.getValue().getName());
    }
  }
}
