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
import java.net.URI;
import java.security.PrivilegedExceptionAction;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.service.launcher.LauncherExitCodes;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.DEBUG;
import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;

/**
 * Fetch all delegation tokens from the given filesystems.
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class FetchTokens extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(FetchTokens.class);

  public static final String USAGE =
      "Usage: fetchdt <file> [-renewer <renewer>] [-r]"
          + optusage(XMLFILE, "file", "XML config file to load")
          + optusage(VERBOSE, "verbose output")
          + "-r: require each filesystem to issue a token\n"
          + " <url1> ... <url999>\n";

  private static final String RENEWER = "renewer";

  private static final String REQUIRED = "r";

  public FetchTokens() {
    createCommandFormat(2, 999,
            REQUIRED);
    addValueOptions(RENEWER);
  }

  @Override
  protected void addStandardValueOptions() {
    addValueOptions(
        DEFINE,
        DEBUG,
        VERBOSE,
        XMLFILE);
  }

  public int run(String[] args, PrintStream stream) throws Exception {
    setOut(stream);
    List<String> paths = processArgs(args, 2, -1, USAGE);
    final Configuration conf = createPreconfiguredConfig();
    final UserGroupInformation self = UserGroupInformation.getLoginUser();

    final Path tokenfile = new Path(paths.get(0));
    final String ropt = getOption(RENEWER);
    final String renewer = ropt != null ?
        ropt : self.getShortUserName();

    final List<String> urls = paths.subList(1, paths.size());
    final boolean required = hasOption(REQUIRED);

    // qualify the FS so that what gets printed is absolute.
    FileSystem fs = tokenfile.getFileSystem(conf);
    Path dest = tokenfile.makeQualified(fs.getUri(), fs.getWorkingDirectory());

    println("Collecting tokens for %d filesystem%s to to %s",
        urls.size(),
        plural(urls.size()),
        dest);
    Credentials retrieved = self.doAs(
        (PrivilegedExceptionAction<Credentials>) () ->
            saveTokens(conf, dest, renewer, required, urls));
    int n = retrieved.numberOfTokens();
    if (n > 0) {
      println("Saved %d token%s to %s", n, plural(n), dest);
    } else {
      println("No tokens collected, file %s unchanged", dest);
    }
    return 0;
  }

  /**
   * Fetch and save the tokens; print their details.
   * in a verbose run, the filesystem statistics are also printed
   * @param conf configuration
   * @param dest dest path for token file
   * @param renewer any renewer
   * @param required are tokens required?
   * @param urls list of filesystem URLs
   * @return the credentials
   * @throws IOException failure
   */
  protected Credentials saveTokens(
      Configuration conf,
      Path dest,
      String renewer,
      boolean required,
      List<String> urls) throws IOException {

    Credentials cred = new Credentials();
    int count = 0;
    for (String url : urls) {
      Path path = new Path(url);
      try (StoreDurationInfo ignored =
               new StoreDurationInfo(LOG, "Fetching tokens for %s", path)) {

        FileSystem fs = path.getFileSystem(conf);
        URI fsUri = fs.getUri();
        LOG.debug("Acquired FS {}", fs);
        Token<?>[] tokens = fs.addDelegationTokens(renewer, cred);
        if (tokens != null && tokens.length != 0) {
          count += tokens.length;
          for (Token<?> token : tokens) {
            println("Fetched token: %s", token);
          }
          maybeDumpStorageStatistics(fs);
        } else {
          println("No token for %s", path);
          maybeDumpStorageStatistics(fs);

          if (required) {
            throw new ExitUtil.ExitException(
                LauncherExitCodes.EXIT_NOT_FOUND,
                "No tokens issued by filesystem " + fsUri);
          }
        }
      }
    }
    // all the tokens are collected, so save
    try(StoreDurationInfo ignored =
            new StoreDurationInfo(LOG, "Saving %d token%s to %s",
                count, plural(count), dest)) {
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
    return ToolRunner.run(new FetchTokens(), args);
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
