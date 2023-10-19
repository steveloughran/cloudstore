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

import java.io.IOException;
import java.io.PrintStream;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.mapreduce.security.TokenCache;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.service.launcher.LauncherExitCodes;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.yarn.client.util.YarnClientUtils.getRmPrincipal;

/**
 * Fetch all delegation tokens from the given filesystems
 * as if this was a job collecting its DTs.
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class JobTokens extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(JobTokens.class);

  private static final String REQUIRED = "r";

  public static final String USAGE =
      "Usage: jobtokens [options] <file> <url1> ... <url999>\n"
          + STANDARD_OPTS
          + optusage(REQUIRED, "-r: require a token to be issued");

  public JobTokens() {
    createCommandFormat(2, 999,
            REQUIRED);
  }

  public int run(String[] args, PrintStream stream) throws Exception {
    setOut(stream);
    List<String> paths = processArgs(args, 2, -1, USAGE);

    final Configuration conf = createPreconfiguredConfig();
    final UserGroupInformation self = UserGroupInformation.getLoginUser();

    final Path tokenfile = new Path(paths.get(0));

    final List<String> urls = paths.subList(1, paths.size());
    final boolean required = hasOption(REQUIRED);

    final String renewer = getRmPrincipal(conf);
    println("Yarn principal for Job Token renewal \"%s\"",
        renewer == null ? "": renewer);

    // qualify the FS so that what gets printed is absolute.
    FileSystem fs = tokenfile.getFileSystem(conf);
    Path dest = tokenfile.makeQualified(fs.getUri(), fs.getWorkingDirectory());

    println("Collecting tokens for %d filesystem%s to to %s",
        urls.size(),
        plural(urls.size()),
        dest);
    Credentials retrieved = self.doAs((PrivilegedExceptionAction<Credentials>) () ->
            saveTokens(conf, dest, renewer, required, urls));
    int n = retrieved.numberOfTokens();
    if (n > 0) {
      println("Saved %d token%s to %s", n, plural(n), dest);
    } else {
      println("No tokens collected, file %s unchanged", dest);
    }
    return 0;
  }

  protected Credentials saveTokens(
      Configuration conf,
      Path dest,
      String renewer,
      boolean required,
      List<String> urls) throws IOException {

    Credentials cred = new Credentials();
    int count = 0;
    Path[] paths = new Path[urls.size()];
    StringBuilder sb = new StringBuilder();
    int i = 0;
    for (String url : urls) {
      final Path p = new Path(url);
      paths[i++] = p;
      sb.append(p.toUri().getHost());
    }
    String hosts = sb.toString();

    try (StoreDurationInfo ignored =
             new StoreDurationInfo(LOG, "Fetching tokens for %s", hosts)) {

      TokenCache.obtainTokensForNamenodes(cred, paths, conf);
      final Collection<Token<? extends TokenIdentifier>>
          tokens = dumpTokens(cred);

      if (tokens.isEmpty()) {
        println("No tokens collected");
        if (required) {
          throw new ExitUtil.ExitException(
              LauncherExitCodes.EXIT_NOT_FOUND,
              "No tokens collected for hosts " + hosts);
        }
      }
    }
    // all the tokens are collected, so save
    try(StoreDurationInfo ignored =
            new StoreDurationInfo(LOG, "Saving %d tokens to %s",
                count, dest)) {
      cred.writeTokenStorageFile(dest, conf);
    }
    return cred;
  }


  @Override
  public final int run(String[] args) throws Exception {
    return run(args, System.out);
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new JobTokens(), args);
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
