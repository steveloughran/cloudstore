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

package org.apache.hadoop.fs.tools;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.shell.CommandFormat;
import org.apache.hadoop.fs.store.DurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.s3a.S3AUtils.applyLocatedFiles;
import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

public class ListFiles extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(ListFiles.class);

  public static final String LIMIT = "limit";

  public static final String USAGE
      = "Usage: list\n"
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(TOKENFILE, "file", "Hadoop token file to load")
      + optusage(XMLFILE, "file", "XML config file to load")
      + optusage(LIMIT, "limit", "limit of files to list")
      + " <path>";

  public ListFiles() {
    setCommandFormat(new CommandFormat(1, 1));
    getCommandFormat().addOptionWithValue(TOKENFILE);
    getCommandFormat().addOptionWithValue(XMLFILE);
    getCommandFormat().addOptionWithValue(DEFINE);
    getCommandFormat().addOptionWithValue(LIMIT);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (paths.size() != 1) {
      errorln(USAGE);
      return E_USAGE;
    }

    addAllDefaultXMLFiles();
    maybeAddTokens(TOKENFILE);
    final Configuration conf = new Configuration();

    maybeAddXMLFileOption(conf, XMLFILE);
    maybePatchDefined(conf, DEFINE);

    int limit = getOptional(LIMIT).map(Integer::valueOf).orElse(0);

    final Path source = new Path(paths.get(0));
    println("");
    println("Listing%s files under %s",
        limit == 0 ? "" : (" up to " + limit),
        source);

    FileSystem fs = source.getFileSystem(conf);
    final DurationInfo duration = new DurationInfo(LOG, "Directory list");
    final DurationInfo firstLoad = new DurationInfo(LOG, "First listing");
    final AtomicInteger count = new AtomicInteger(0);
    final AtomicLong size = new AtomicLong(0);
    try {
      applyLocatedFiles(fs.listFiles(source, true),
          (status) -> {
            int c = count.incrementAndGet();
            if (c == 1) {
              firstLoad.close();
            }
            size.addAndGet(status.getLen());
            println("[%d]\t%s\t%,d\t%s\t%s\t[%s]",
                c,
                status.getPath(),
                status.getLen(),
                status.getOwner(),
                status.getGroup(),
                status.isEncrypted() ? "encrypted" : "");
            if (limit > 0 && c >= limit) {
              throw new LimitReachedException();
            }
          });
    } catch(LimitReachedException expected) {

      // the limit has been reached

    } finally {
      duration.close();
    }
    long files = count.get();
    double millisPerFile = files > 0 ? (((float)duration.value()) / files) : 0;
    long totalSize = size.get();
    long bytesPerFile = (long) (files > 0 ? totalSize / files : 0);
    println("");
    println("Found %s files, %,.0f milliseconds per file",
        files, millisPerFile);
    println("Data size %,d bytes, %,d bytes per file",
        totalSize, bytesPerFile);
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
    return ToolRunner.run(new ListFiles(), args);
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

  private class LimitReachedException extends IOException {

    public LimitReachedException() {
      super("Limit reached");
    }
  }
}
