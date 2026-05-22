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
import java.util.TreeMap;
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
 * → {@link EntryPoint} registry dispatched through {@link ToolRunner#run}.
 *
 * <p>
 * Invocation, using the bundled jar:
 *
 * <pre>{@code
 * hadoop jar cloudstore-1.3.jar org.apache.hadoop.fs.store.Cloudstore <command> [args...]
 * hadoop jar cloudstore-1.3.jar org.apache.hadoop.fs.store.Cloudstore help
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
@SuppressWarnings("SpellCheckingInspection")
public final class Cloudstore {

  private static final Logger LOG = LoggerFactory.getLogger(Cloudstore.class);

  /** Registry value: implementing class + one-line description shown by {@code help}. */
  static final class EntryPoint {

    final Class<? extends Tool> tool;
    final String description;

    private EntryPoint(final Class<? extends Tool> tool, final String description) {
      this.tool = tool;
      this.description = description;
    }
  }

  private static EntryPoint ep(final Class<? extends Tool> tool, final String description) {
    return new EntryPoint(tool, description);
  }

  /** Command name → {@link EntryPoint}. {@link #printUsage} sorts by key at print time. */
  private static final Map<String, EntryPoint> COMMANDS;

  static {
    Map<String, EntryPoint> m = new LinkedHashMap<>();
    m.put("auditlogs", ep(AuditTool.class, "Audit log processings"));
    m.put("bandwidth", ep(Bandwidth.class, "measure network bandwidth"));
    m.put("bucketmetadata", ep(BucketMetadata.class, "retrieve bucket metadata"));
    m.put("bucketstate", ep(BucketState.class, "prints the AWS bucket state"));
    m.put("bulkdelete", ep(BulkDeleteCommand.class, "bulk delete objects/files"));
    m.put("cloudup", ep(Cloudup.class, "copies to/from cloud storage"));
    m.put("committerinfo", ep(CommitterInfo.class, "Print committer information"));
    m.put("constval", ep(Constval.class, "look up a constant value in a class"));
    m.put("deleteobject", ep(DeleteObject.class, "Delete an S3 object"));
    m.put("dux", ep(ExtendedDu.class, "extended du"));
    m.put("etag", ep(EtagCommand.class, "print the etag of an object (where supported)"));
    m.put("fetchdt", ep(FetchTokens.class, "fetch delegation tokens"));
    m.put("filestatus", ep(PrintStatus.class, "print file statuses"));
    m.put("gcscreds",
        ep(GsCredDiag.class, "credential diagnostics for GCS. Warning: logs secrets"));
    m.put("iampolicy", ep(IamPolicy.class, "generate IAM policy"));
    m.put("list", ep(ListFiles.class, "list files"));
    m.put("listmultiparts", ep(ListMultiparts.class, "list multipart uploads to CSV"));
    m.put("listobjects", ep(ListObjects.class, "list S3 objects and their translated statuses"));
    m.put("listversions", ep(ListVersions.class, "list all versions of S3 objects under a path"));
    m.put("localhost", ep(LocalHost.class, "print local host details"));
    m.put("locatefiles", ep(LocateFiles.class, "locate files"));
    m.put("mkbucket", ep(MkBucket.class, "Create an S3 bucket"));
    m.put("mkcsv", ep(MkCSV.class, "generate CSV file"));
    m.put("pathcapability", ep(PathCapability.class, "probe for path capabilities"));
    m.put("put", ep(Put.class, "file upload/copy"));
    m.put("regions", ep(Regions.class, "Emulate region lookup of AWS SDK"));
    m.put("restore", ep(RestoreObject.class, "Restore a versioned S3 object"));
    m.put("sessionkeys", ep(SessionKeys.class, "request STS session credentials"));
    m.put("storediag", ep(StoreDiag.class, "store diagnostics"));
    m.put("tlsinfo", ep(TLSInfo.class, "Print TLS information"));
    m.put("undelete", ep(Undelete.class, "undelete s3 objects by removing tombstones"));
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
    EntryPoint entry = COMMANDS.get(name);
    if (entry == null) {
      System.err.println("Unknown command: " + name);
      printUsage(System.err);
      return LauncherExitCodes.EXIT_USAGE;
    }
    Tool tool = entry.tool.getDeclaredConstructor().newInstance();
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
      // usage error
      System.err.println(ex.getMessage());
      ExitUtil.terminate(LauncherExitCodes.EXIT_USAGE, ex.getMessage());
    } else if (ex instanceof ExitUtil.ExitException) {
      // exception with explicitly declared exit code
      LOG.debug("Command failure", ex);
      ExitUtil.terminate((ExitUtil.ExitException) ex);
    } else {
      // any other failure
      ex.printStackTrace(System.err);
      ExitUtil.terminate(LauncherExitCodes.EXIT_FAIL, ex.toString());
    }
  }

  /** Visible for testing. */
  static Map<String, EntryPoint> commands() {
    return COMMANDS;
  }

  private static void printUsage(java.io.PrintStream out) {
    out.println("Usage: cloudstore <command> [args...]");
    out.println();
    out.println("Commands:");
    Map<String, EntryPoint> sorted = new TreeMap<>(COMMANDS);
    for (Map.Entry<String, EntryPoint> e : sorted.entrySet()) {
      out.printf("  %-16s  %s%n", e.getKey(), e.getValue().description);
    }
  }
}
