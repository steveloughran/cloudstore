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

package org.apache.hadoop.fs.s3a.diag;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.shell.CommandFormat;
import org.apache.hadoop.fs.store.DurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.util.VersionInfo.*;

public class S3ADiag extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(S3ADiag.class);

  private static final String HELLO = "Hello";


  CommandFormat commandFormat = new CommandFormat(0, Integer.MAX_VALUE);


  // Exit codes
  static final int SUCCESS = 0;

  static final int E_USAGE = 42;

  static final int ERROR = -1;

  static final String USAGE = "Usage: S3ADiag <filesystem>";

  static Object[][] props = {
      {"fs.s3a.access.key", true},
      {"fs.s3a.secret.key", true},
      {"fs.s3a.session.token", true},
      {"fs.s3a.server-side-encryption-algorithm", false},
      {"fs.s3a.server-side-encryption.key", true},
      {"fs.s3a.aws.credentials.provider", false},
      {"fs.s3a.proxy.host", false},
      {"fs.s3a.proxy.port", false},
      {"fs.s3a.proxy.username", false},
      {"fs.s3a.proxy.password", true},
      {"fs.s3a.proxy.domain", false},
      {"fs.s3a.proxy.workstation", false},
      {"fs.s3a.fast.upload", false},
      {"fs.s3a.fast.upload.buffer", false},
      {"fs.s3a.fast.upload.active.blocks", false},
      {"fs.s3a.signing-algorithm", false},
      {"fs.s3a.experimental.input.fadvise", false},
      {"fs.s3a.user.agent.prefix", false},
      {"fs.s3a.experimental.input.fadvise", false},
      {"fs.s3a.signing-algorithm", false},
      {"fs.s3a.threads.max", false},
      {"fs.s3a.threads.keepalivetime", false},
      {"fs.s3a.max.total.tasks", false},
      {"fs.s3a.multipart.size", false},
      {"fs.s3a.buffer.dir", false},
      {"fs.s3a.metadatastore.impl", false},
      {"fs.s3a.metadatastore.authoritative", false},
      {"fs.s3a.committer.magic.enabled", false},
  };

  private void showProp(Configuration conf, String key, boolean sensitive) {
    String v = conf.get(key);
    if (v == null) {
      v = "(unset)";
    } else {
      if (sensitive) {
        int len = v.length();
        if (len > 2) {
          StringBuilder b = new StringBuilder(len);
          b.append(v.charAt(0));
          for (int i = 1; i < len - 1; i++) {
            b.append('*');
          }
          b.append(v.charAt(len - 1));
          v = b.toString();
        } else {
          // short values get special treatment
          v = "**";
        }
      }
    }
    println("%s = %s", key, v);
  }

  @Override
  public final int run(String[] args) throws Exception {
    return run(args, System.out);
  }

  public int run(String[] args, PrintStream stream) throws Exception {
    setOut(stream);
    List<String> paths = parseArgs(args);
    if (paths.size() != 1) {
      errorln(USAGE);
      return E_USAGE;
    }
    println("Hadoop %s", getVersion());
    println("Compiled by %s on %s", getUser(), getDate());
    println("Compiled with protoc %s", getProtocVersion());
    println("From source with checksum %s", getSrcChecksum());


    Configuration conf = getConf();
    Path path = new Path(paths.get(0));
    FileSystem fs = path.getFileSystem(conf);

    println("Filesystem for %s is %s", path, fs);

    // examine the FS
    Configuration fsConf = fs.getConf();
    for (int i = 0; i < props.length; i++) {
      showProp(fsConf, (String) props[i][0], (Boolean) props[i][1]);
    }

    Path root = fs.makeQualified(new Path("/"));
    try (DurationInfo d = new DurationInfo(LOG,
        "Listing  %s", root)) {
      println("%s has %d entries", root, fs.listStatus(root).length);
    }

    String dirName = "dir-" + UUID.randomUUID();
    Path dir = new Path(root, dirName);
    try (DurationInfo d = new DurationInfo(LOG,
        "Creating a directory %s", dir)) {
      fs.mkdirs(dir);
    }
    try {
      Path file = new Path(dir, "file");
      try (DurationInfo d = new DurationInfo(LOG,
          "Creating a file %s", file)) {
        FSDataOutputStream data = fs.create(file, true);
        data.writeUTF(HELLO);
        data.close();
      }
      try (DurationInfo d = new DurationInfo(LOG,
          "Listing  %s", dir)) {
        fs.listFiles(dir, false);
      }

      try (DurationInfo d = new DurationInfo(LOG,
          "Reading a file %s", file)) {
        FSDataInputStream in = fs.open(file);
        String utf = in.readUTF();
        in.close();
        if (!HELLO.equals(utf)) {
          throw new IOException("Expected " + file + " to contain the text "
              + HELLO + " -but it has the text \"" + utf + "\"");
        }
      }
      try (DurationInfo d = new DurationInfo(LOG,
          "Deleting file %s", file)) {
        fs.delete(file, true);
      }
    } finally {
      try (DurationInfo d = new DurationInfo(LOG,
          "Deleting directory %s", dir)) {
        try {
          fs.delete(dir, true);
        } catch (Exception e) {
          LOG.warn("When deleting {}: ", dir, e);
        }
      }


    }


    // Validate parameters.
    return SUCCESS;
  }


  /**
   * Parse CLI arguments and returns the position arguments.
   * The options are stored in {@link #commandFormat}.
   *
   * @param args command line arguments.
   * @return the position arguments from CLI.
   */
  List<String> parseArgs(String[] args) {
    return args.length > 0 ? commandFormat.parse(args, 0)
        : new ArrayList<String>(0);
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new S3ADiag(), args);
  }

  /**
   * Main entry point. Calls {@code System.exit()} on all execution paths.
   * @param args argument list
   */
  public static void main(String[] args) {
    try {

      exit(exec(args), "");
    } catch (CommandFormat.UnknownOptionException e) {
      errorln(e.getMessage());
      exit(E_USAGE, e.getMessage());
    } catch (Throwable e) {
      e.printStackTrace(System.err);
      exit(ERROR, e.toString());
    }
  }

  protected static void exit(int status, String text) {
    ExitUtil.terminate(status, text);
  }
}
