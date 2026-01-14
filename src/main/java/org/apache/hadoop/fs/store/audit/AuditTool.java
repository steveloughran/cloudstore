/*
 * Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.hadoop.fs.store.audit;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.DurationInfo;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.service.launcher.LauncherExitCodes.EXIT_COMMAND_ARGUMENT_ERROR;
import static org.apache.hadoop.service.launcher.LauncherExitCodes.EXIT_FAIL;
import static org.apache.hadoop.service.launcher.LauncherExitCodes.EXIT_SUCCESS;

/**
 * AuditTool is a Command Line Interface.
 * Its functionality is to parse the audit log files
 * and generate avro file.
 */
public class AuditTool extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(AuditTool.class);

  /**
   * Name of audit tool: {@value}.
   */
  public static final String AUDIT = "audit";


  /**
   * Name of this tool: {@value}.
   */
  public static final String AUDIT_TOOL =
      "auditlogs";

  /**
   * Purpose of this tool: {@value}.
   */
  public static final String PURPOSE =
      "\n\nUSAGE:\nMerge and parse audit log files and convert into avro files "
          + "for better visualization";

  // Exit codes
  private static final int SUCCESS = EXIT_SUCCESS;

  private static final int FAILURE = EXIT_FAIL;

  private static final int INVALID_ARGUMENT = EXIT_COMMAND_ARGUMENT_ERROR;

  private static final int SAMPLE = 500;

  public static final String OVERWRITE = "overwrite";

  public static final String USAGE
      = "Usage: auditlogs\n"
      + STANDARD_OPTS
      + " <path of source files>"
      + " <path of output file>"
      + optusage(OVERWRITE, "overwrite the output file");

  public AuditTool() {
    createCommandFormat(2, 2, OVERWRITE);
  }

  /**
   * Tells us the usage of the AuditTool by commands.
   * @return the string USAGE
   */
  public String getUsage() {
    return USAGE + PURPOSE;
  }

  public String getName() {
    return AUDIT_TOOL;
  }

  /**
   * This run method in AuditTool takes source and destination path of bucket,
   * and checks if there are directories and pass these paths to merge and
   * parse audit log files.
   * @param args argument list
   * @return exit code
   * @throws Exception on any failure.
   */
  @Override
  public int run(final String[] args)
      throws ExitUtil.ExitException, Exception {


    List<String> paths = processArgs(args, 2, -1, USAGE);

    // Path of audit log files
    Path logsPath = new Path(paths.get(0));

    // Path of destination file
    Path destPath = new Path(paths.get(1));
    println("Processing logs in source directory %s", logsPath);
    println("Writing output to file %s", destPath);

    final AuditLogProcessor auditProcessor;
    DurationInfo duration = new DurationInfo(LOG, "Log Source %s", logsPath);
    auditProcessor = new AuditLogProcessor(getConf(), SAMPLE);


    // Calls AuditLogProcessor for implementing merging, passing of
    // audit log files and converting into avro file
    auditProcessor.mergeAndParseAuditLogFiles(
        logsPath, destPath, hasOption(OVERWRITE), AuditLogProcessor.PROCESS_ALL);

    duration.finished();

    println("Read %d source files", auditProcessor.getLogFilesParsed());
    println("Processed %d records of which %d had audit information",
        auditProcessor.getLogRecordsProcessed(),
        auditProcessor.getReferrerHeadersParsed());
    println("Total processing time: %s", duration.getDurationString());
    println("Saved output to %s", destPath);
    if (!(auditProcessor.getLogRecordsProcessed() > 0)) {
      return FAILURE;
    }

    return SUCCESS;
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new AuditTool(), args);
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
