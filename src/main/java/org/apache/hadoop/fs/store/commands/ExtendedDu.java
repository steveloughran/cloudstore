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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathAccessDeniedException;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;
import static org.apache.hadoop.fs.store.CommonParameters.BFS;
import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.LIMIT;
import static org.apache.hadoop.fs.store.CommonParameters.THREADS;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.util.StringUtils.TraditionalBinaryPrefix.long2String;

/**
 * Use the mapreduce LocateFiles class
 */
public class ExtendedDu extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(ExtendedDu.class);

  public static final String USAGE
      = "Usage: dux\n"
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(TOKENFILE, "file", "Hadoop token file to load")
      + optusage(XMLFILE, "file", "XML config file to load")
      + optusage(THREADS, "threads", "number of threads")
      + optusage(LIMIT, "limit", "limit of files to list")
      + optusage(VERBOSE, "print verbose output")
      + optusage(BFS, "breadth first search of the tree", "do a deep breadth first search of the tree")
      + "\t<path>";

  public static final int DEFAULT_THREADS = 8;

  public static final int DECIMAL_PLACES = 2;

  private AtomicInteger fileCount;

  private AtomicLong totalSize;

  private FileSystem fs;

  private int limit;

  ExecutorService completion;

  private Queue<Future<Summary>> queue;

  public ExtendedDu() {
    createCommandFormat(1, 1, VERBOSE);
    addValueOptions(TOKENFILE, XMLFILE, DEFINE, THREADS, LIMIT, BFS);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (paths.size() != 1) {
      errorln(USAGE);
      return E_USAGE;
    }

    maybeAddTokens(TOKENFILE);
    final Configuration conf = createPreconfiguredConfig();

    int threads = getOptional(THREADS).map(Integer::valueOf).orElse(
        DEFAULT_THREADS);

    boolean isBFS = getOptional(BFS).isPresent();

    final Path source = new Path(paths.get(0));
    println("");
    heading("Listing files under %s with thread count %d",
        source,
        threads);

    final PrintStream out = isVerbose() ? getOut() : null;

    final StoreDurationInfo duration = new StoreDurationInfo(out, "List files under %s", source);
    fileCount = new AtomicInteger(0);
    totalSize = new AtomicLong(0);
    fs = source.getFileSystem(conf);
    limit = getOptional(LIMIT).map(Integer::valueOf).orElse(0);
    // worker pool
    completion = new ThreadPoolExecutor(threads, threads,
        0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>());
    queue = new LinkedBlockingQueue<>();
    List<Summary> results = new ArrayList<>();


    try {
      long submitted = 0;
      RemoteIterator<FileStatus> dir;
      try (StoreDurationInfo firstLoad = new StoreDurationInfo(out,
          "Initial list of path %s", source)) {
        dir = fs.listStatusIterator(source);
      }
      while (dir.hasNext()) {
        final FileStatus st = dir.next();
        if (st.isFile()) {
          // add the file
          updateValues(1, st.getLen());
        } else {
          // it's a dir
          submitted++;
          if(!isBFS) {
            queue.add(completion.submit(() -> scanOneDir(out, st.getPath())));
          } else {
            queue.add(completion.submit(() -> scanOneDirBFS(out, st.getPath())));
          }
        }
      }

      // here we have all scans submitted
      println("Waiting for %d scan to finish", submitted);
      while (!queue.isEmpty()) {
        Future<Summary> fts = queue.remove();
        if(fts.isDone()){
          results.add(fts.get());
        } else {
          queue.add(fts);
        }
      }

    } catch (LimitReachedException ex) {
      // limit reached

    } finally {
      duration.close();
    }
    printIfVerbose("FileSystem: %s", fs);
    maybeDumpStorageStatistics(fs);
    Collections.sort(results);
    println("");
    heading("path    files   size");
    results.forEach(s->
        println("%s\t%d\t%s",
            s.path,
            s.count,
            long2String(s.size, "", DECIMAL_PLACES)));
    long files = fileCount.get();
    double millisPerFile = files > 0 ? (((float)duration.value()) / files) : 0;
    long totalSize = this.totalSize.get();
    long bytesPerFile = (files > 0 ? totalSize / files : 0);
    println("");
    heading("Disk usage of %s", source);
    println("Found %s files, time taken %,.2f, %,.2f milliseconds per file",
        files, (float)duration.value(), millisPerFile);
    println("Data size %siB (%,d bytes)",
        long2String(totalSize, "", DECIMAL_PLACES), totalSize);
    println("Average file size %siB (%,d bytes)",
        long2String(bytesPerFile,"", DECIMAL_PLACES), bytesPerFile);
    println("");

    return 0;
  }

  /**
   * Summary of a tree scan.
   */
  private static final class Summary implements Comparable<Summary> {

    private final Path path;

    long size;

    int count;

    private Summary(final Path path,
        final long size,
        final int count) {
      this.path = path;
      this.size = size;
      this.count = count;
    }

    @Override
    public int compareTo(final Summary o) {
      return path.toString().compareTo(o.path.toString());
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("Summary{");
      sb.append("path=").append(path);
      sb.append(", size=").append(byteCountToDisplaySize(size));
      sb.append(", count=").append(count);
      sb.append('}');
      return sb.toString();
    }
  }

  private Summary scanOneDirBFS(final PrintStream out, Path path) throws IOException {
    long size = 0;
    int count = 0;
    RemoteIterator<LocatedFileStatus> lister = null;
    try (StoreDurationInfo duration = new StoreDurationInfo(out,
        "List %s", path)){
      lister = fs.listFiles(path, true);
      while (lister.hasNext()) {
        final LocatedFileStatus status = lister.next();
        long len = status.getLen();
        count ++;
        if(!status.isFile()){
          out.println("Pushing path:" + path.toString());
          queue.add(completion.submit(() -> scanOneDirBFS(out, status.getPath())));
          continue;
        }
        size += len;
        updateValues(1, len);
      }
    } catch (InterruptedIOException | RejectedExecutionException interrupted) {
      println("Interrupted");
      LOG.debug("Interrupted", interrupted);
    } catch (AccessDeniedException | PathAccessDeniedException denied) {
      println("Access denied listing %s", path);
    } catch (FileNotFoundException | LimitReachedException end) {
      // path has been deleted; ignore
    } finally {
      if (lister != null) {
        printIfVerbose("List iterator: %s", lister);
        maybeClose(lister);
      }
    }
    final Summary summary = new Summary(path, size, count);
    return summary;
  }

  private Summary scanOneDir(final PrintStream out, Path path) throws IOException {
    long size = 0;
    int count = 0;
    RemoteIterator<LocatedFileStatus> lister = null;
    try (StoreDurationInfo duration = new StoreDurationInfo(out,
        "List %s", path)){
      lister = fs.listFiles(path, true);
      while (lister.hasNext()) {
        final LocatedFileStatus status = lister.next();
        long len = status.getLen();
        count ++;
        size += len;
        updateValues(1, len);
      }
    } catch (InterruptedIOException | RejectedExecutionException interrupted) {
      println("Interrupted");
      LOG.debug("Interrupted", interrupted);
    } catch (AccessDeniedException | PathAccessDeniedException denied) {
      println("Access denied listing %s", path);
    } catch (FileNotFoundException | LimitReachedException end) {
      // path has been deleted; ignore
    } finally {
      if (lister != null) {
        printIfVerbose("List iterator: %s", lister);
        maybeClose(lister);
      }
    }
    final Summary summary = new Summary(path, size, count);
    return summary;
  }

  private void updateValues(int count, long size) throws LimitReachedException {
    totalSize.addAndGet(size);
    final int c = fileCount.addAndGet(count);
    if (limit > 0 && c >= limit) {
      throw new LimitReachedException();
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
    return ToolRunner.run(new ExtendedDu(), args);
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

  private static final PathFilter HIDDEN_FILE_FILTER =
      (p) -> {
        String n = p.getName();
        return !n.startsWith("_") && !n.startsWith(".");
      };
}
