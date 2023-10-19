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

package org.apache.hadoop.fs.tools.cloudup;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.collections.comparators.ReverseComparator;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FSDataOutputStreamBuilder;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FutureDataInputStreamBuilder;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathIOException;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.fs.StorageStatistics;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.fs.store.StoreUtils;
import org.apache.hadoop.util.Progressable;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.BLOCK;
import static org.apache.hadoop.fs.store.CommonParameters.CSVFILE;
import static org.apache.hadoop.fs.store.CommonParameters.DEBUG;
import static org.apache.hadoop.fs.store.CommonParameters.FLUSH;
import static org.apache.hadoop.fs.store.CommonParameters.HFLUSH;
import static org.apache.hadoop.fs.store.CommonParameters.IGNORE;
import static org.apache.hadoop.fs.store.CommonParameters.LARGEST;
import static org.apache.hadoop.fs.store.CommonParameters.OVERWRITE;
import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.CommonParameters.UPDATE;
import static org.apache.hadoop.fs.store.StoreUtils.await;

/**
 * Entry point for Cloudup: parallelized upload of local files
 * to remote (cloud) storage with shuffle after selective choice
 * of largest files.
 */
public class Cloudup extends StoreEntryPoint {


  public static final String THREADS = "threads";


  private static final Logger LOG = LoggerFactory.getLogger(Cloudup.class);


  private static final int DEFAULT_LARGEST = 4;

  private static final int DEFAULT_THREADS = 16;

  private static final int DEFAULT_BLOCK_SIZE = 2;

  // all the verbs, here just just make renaming again easier.

  private static final String COPYING = "Copying";

  private static final String COPY_CAPS = "Copy";

  private static final String COPY_LC = "copy";

  private static final String COPIES = "copies";

  private static final String COPIED = "copied";

  private int blockSize = DEFAULT_BLOCK_SIZE;

  /**
   * Usage string: {@value}.
   */
  public static final String USAGE
      = "Usage: cloudup [options] <source> <dest>\n"
      + STANDARD_OPTS
      + optusage(BLOCK, "size", "block size in megabytes")
      // + optusage(CSVFILE, "file", "CSV file to log operation details")
      + optusage(FLUSH, "flush the output after writing each block")
      + optusage(HFLUSH, "hflush() the output after writing each block")
      + optusage(IGNORE, "ignore errors")
      + optusage(LARGEST, "largest", "number of large files to " + COPY_LC + " first")
      + optusage(OVERWRITE, "overwrite files")
      + optusage(THREADS, "threads", "number of worker threads")
      + optusage(UPDATE, "only copy up new or more recent files");

  /**
   * Executor service for workers.
   */
  private ExecutorService workers;

  /**
   * Source filesystem.
   */
  private FileSystem sourceFS;

  /**
   * Source path (qualified).
   */
  private Path sourcePath;

  /**
   * Source path status.
   */
  private FileStatus sourcePathStatus;

  /**
   * Did the destination exist?
   */
  private boolean destDidNotExist;

  /**
   * Destination filesystem.
   */
  private FileSystem destFS;

  /**
   * Destination path (qualified).
   */
  private Path destPath;

  /**
   * Destination path status.
   */
  private FileStatus destPathStatus;

  /**
   * Exit flag: set to true when problems surface.
   */
  private final AtomicBoolean exit = new AtomicBoolean(false);

  /**
   * Verbose option.
   */
  private boolean verbose;

  /**
   * Overwrite option.
   */
  private boolean overwrite = true;

  /**
   * Should failures be ignored?
   */
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private boolean ignoreFailures = true;

  private final AtomicLong operationIndex = new AtomicLong(0);

  /**
   * First exception raised.
   */
  private final AtomicReference<Exception> firstException = new AtomicReference<>();

  /**
   * Completion service for uploads.
   */
  private CompletionService<Outcome> completion;

  /**
   * Update option.
   */
  private boolean update;

  /**
   * Flush option.
   */
  private boolean flush;

  /**
   * Hflush option.
   */
  private boolean hflush;

  public Cloudup() {
    createCommandFormat(2, 2,
        DEBUG,
        FLUSH,
        HFLUSH,
        IGNORE,
        OVERWRITE,
        UPDATE
    );
    addValueOptions(
        BLOCK,
        CSVFILE,
        LARGEST,
        THREADS
    );
  }

  /**
   * current time.
   * @return now.
   */
  private long now() {
    return System.currentTimeMillis();
  }

  @Override
  public synchronized void close() throws IOException {
    if (workers != null) {
      workers.shutdown();
      workers = null;
    }
  }

  @Override
  public int run(String[] args) throws Exception {
    // parse the path
    List<String> argList = processArgs(args, 2, -1, USAGE);

    Configuration conf = createPreconfiguredConfig();
    //Configuration conf = patchForMaxS3APerformance(createPreconfiguredConfig());
    flush = hasOption(FLUSH);
    hflush = hasOption(HFLUSH);
    ignoreFailures = hasOption(IGNORE);
    overwrite = hasOption(OVERWRITE);
    update = hasOption(UPDATE);

    verbose = isVerbose();
    final Path src = new Path(argList.get(0));
    sourceFS = src.getFileSystem(conf);
    sourcePath = sourceFS.makeQualified(src);

    final Path dest = new Path(argList.get(1));
    destFS = dest.getFileSystem(conf);
    destPath = destFS.makeQualified(dest);

    final String csvFile = getOption(CSVFILE);
    if (csvFile != null) {
      warn("CSV file logging is not yet implemented");
    }
    final int largest = getIntOption(LARGEST, DEFAULT_LARGEST);
    final int threads = getIntOption(THREADS, DEFAULT_THREADS);
    blockSize = getIntOption(BLOCK, DEFAULT_BLOCK_SIZE) * (1024 * 1024);


    println(COPYING
            + " from %s to %s;"
            + " threads=%,d; large files=%,d; block size=%dn;"
            + " overwrite=%s; update=%s verbose=%s; ignore failures=%s",
        sourcePath, destPath,
        threads, largest, blockSize,
        overwrite, update, verbose, ignoreFailures);

    // see what we have for a source: file & dir are treated differently
    sourcePathStatus = sourceFS.getFileStatus(sourcePath);
    try {
      destPathStatus = destFS.getFileStatus(destPath);
    } catch (FileNotFoundException e) {
      destPathStatus = null;
    }
    destDidNotExist = destPathStatus == null;

    if (destFS.equals(sourceFS)) {
      // dest FS is also source filesystem.
      // make sure that the source isn't under the dest,
      // and vice versa

      String s = sourcePath.toString();
      String d = destPath.toString();
      StoreUtils.checkArgument(!s.startsWith(d),
          "Source path " + s
              + "%s is under destination path " + d);
      StoreUtils.checkArgument(!d.startsWith(s),
          "Destination path " + s
              + "%s is under source path " + d);
    }

    // worker pool
    workers = new ThreadPoolExecutor(threads, threads,
        0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>());

    final StoreDurationInfo preparationDuration = new StoreDurationInfo();
    // list the files
    Future<List<UploadEntry>> listFilesOperation =
        workers.submit(buildUploads());

    // prepare the destination

    final Future<String> prepareDestResult = workers.submit(prepareDest());
    String info = await(prepareDestResult);
    debug("Destination prepared: {}", info);

    List<UploadEntry> uploadList = await(listFilesOperation);
    final int uploadCount = uploadList.size();

    preparationDuration.finished();
    println("Files to "
            + COPY_LC
            + " = %,d; preparation  = %s",
        uploadCount, preparationDuration);


    // full upload operation
    final StoreDurationInfo uploadDuration = new StoreDurationInfo();
    final NanoTimer uploadTimer = new NanoTimer();

    // now completion service for all outstanding workers
    completion = new ExecutorCompletionService<>(workers);

    // upload initial sorted entries.

    // reverse sort to get largest first
    uploadList.sort(new ReverseComparator(new UploadEntry.SizeComparator()));

    // select the largest few of them
    final int sortUploadCount = Math.min(largest, uploadCount);
    long sortUploadSize = 0;
    int submittedFiles = 0;
    for (int i = 0; i < sortUploadCount; i++) {
      UploadEntry upload = uploadList.get(i);
      long submitSize = submit(upload);
      println("[%02d]: size = %,d bytes: %s",
          i + 1,
          upload.getSize(),
          upload.getSource());
      if (submitSize >= 0) {
        submittedFiles++;
        sortUploadSize += submitSize;
      }
    }

    // largest files queued for upload
    int remaining = uploadCount - sortUploadCount;
    println("Largest %,d "
            + COPIES
            + " commenced, total size = %,d bytes. Remaining files: %,d",
        submittedFiles,
        sortUploadSize,
        remaining);

    // shuffle and submit remainder

    int shuffledUploadCount = 0;
    long shuffledUploadSize = 0;

    if (remaining > 0) {
      Collections.shuffle(uploadList);
      for (UploadEntry entry : uploadList) {
        long size = submit(entry);
        if (size >= 0) {
          // file was submitted for upload
          shuffledUploadCount++;
          shuffledUploadSize += size;
        }
      }
      println("Shuffled files and queued: %,d, total size = %,d bytes",
          shuffledUploadCount, shuffledUploadSize);
    }
    submittedFiles += shuffledUploadCount;

    if (submittedFiles == 0) {
      println("No files submitted");
      return 0;
    }

    final long uploadSize = sortUploadSize + shuffledUploadSize;


    // now await all outcomes to complete
    println("Awaiting completion of %,d operations", uploadCount);
    List<Future<Outcome>> outcomes = new ArrayList<>(uploadCount);
    for (int i = 0; i < uploadCount; i++) {
      Future<Outcome> outcome = completion.take();
      LOG.debug("Operation {} completed", i + 1);
      outcomes.add(outcome);
    }

    uploadDuration.finished();
    uploadTimer.end();

    if (isVerbose()) {
      dumpStats(sourceFS, "Source statistics");
      if (!sourceFS.equals(destFS)) {
        dumpStats(destFS, "Dest statistics");
      }
    }

    // run through the outcomes and process errors
    // at this point, all the uploads have been executed.
    long finalUploadedSize = 0;
    long skippedSize = 0;
    int skipCount = 0;
    int errors = 0;
    Exception exception = firstException.get();

    for (Future<Outcome> outcome : outcomes) {
      try {
        final Outcome result = await(outcome);
        result.maybeThrowException();
        if (result.skipped()) {
          skipCount++;
          skippedSize += result.getBytesUploaded();
        } else {
          finalUploadedSize += result.getBytesUploaded();
        }
      } catch (InterruptedException ignored) {
        // ignored
      } catch (Exception e) {
        errors++;
        if (exception == null) {
          exception = e;
        }
      }
    }


    heading("Summary of " + COPY_LC + " from %s to %s",
        sourcePath, destPath);
    println("File copies attempted: %,d; size %,d bytes",
        uploadCount,
        uploadSize,
        uploadDuration);
    println("Files skipped: %,d, size %,d bytes", skipCount, skippedSize);
    println();
    println("Listing duration: (HH:MM:ss) : %s", preparationDuration);
    println(COPY_CAPS
        + " duration: (HH:MM:ss) : %s", uploadDuration);
    println();
    final int filesActuallyUploaded = uploadCount - skipCount;
    if (filesActuallyUploaded > 0) {
      println("Effective bandwidth %,.3f MiB/s, %,.3f Megabits/s",
          uploadTimer.bandwidth(finalUploadedSize),
          uploadTimer.bandwidthMegabits(finalUploadedSize));
      println("Seconds per file: %.3fs",
          ((double) (uploadDuration.value()) / (filesActuallyUploaded * 1000)));
    } else {
      println("No files "
          + COPIED);
    }
    println();

    if (exception != null) {
      println(COPY_CAPS + " failed due to an error");
      println("Number of errors: %,d actual bytes " + COPIED + " = %,d",
          errors,
          finalUploadedSize);
      if (!ignoreFailures) {
        throw exception;
      }
    }
    println();
    println();

    return 0;
  }

  /**
   * Create an upload.
   * @param upload upload entry
   * @return length of upload; -1 for "none"
   */
  private Callable<Outcome> createUploadOperation(final UploadEntry upload) {
    return () -> uploadOneFile(upload);
  }

  /**
   * Submit an upload; does nothing if the upload is already queued.
   * Updates the ID of the upload.
   * @param upload upload to submit
   * @return size to upload; -1 for no upload
   */
  private long submit(final UploadEntry upload) {
    LOG.debug("Submit {}", upload);
    if (upload.inState(UploadEntry.State.ready)) {
      Callable<Outcome> operation = createUploadOperation(upload);
      upload.setState(UploadEntry.State.queued);
      LOG.debug("Queued {}", upload);
      completion.submit(operation);
      return upload.getSize();
    }
    return -1;
  }

  /**
   * Callable to prepare destination;
   * @return a string for logging.
   */
  private Callable<String> prepareDest() {
    return () -> {
      try {
        destFS.getFileStatus(destPath);
      } catch (FileNotFoundException e) {
        // dest doesn't exist
      }
      return destPath.toString();
    };
  }

  private Callable<List<UploadEntry>> buildUploads() {
    return () -> {
      println("Listing source files under %s", sourcePath);
      return createUploadList();
    };
  }

  /**
   * List the source files and build the list.
   * @return list of uploads
   * @throws IOException failure to list
   */
  private List<UploadEntry> createUploadList() throws IOException {
    List<UploadEntry> uploads = new ArrayList<>();
    RemoteIterator<LocatedFileStatus> ri = sourceFS.listFiles(sourcePath, true);
    while (ri.hasNext()) {
      LocatedFileStatus status = ri.next();
      UploadEntry entry = new UploadEntry(status);
      entry.setDest(getFinalPath(status.getPath()));
      uploads.add(entry);
    }
    if (verbose) {
      println("List %s", ri);
    }
    if (ri instanceof Closeable) {
      ((Closeable) ri).close();
    }

    return uploads;
  }

  /**
   * Upload one entry.
   * @param upload upload information
   * @return the outcome of the upload
   */
  private Outcome uploadOneFile(final UploadEntry upload) {

    String threadId = Thread.currentThread().getName();

    // fail fast on exit flag
    if (exit.get()) {
      return Outcome.notExecuted(upload);
    }

    //skip uploading duplicate (and uploaded already) files
    if (!upload.notYetExecuted()) {
      return Outcome.notExecuted(upload);
    }
    upload.setId(operationIndex.incrementAndGet());
    upload.setStartTime(now());
    final Path source = upload.getSource();
    final Path dest = destFS.makeQualified(upload.getDest());
    try {
      println("[%s] [%04d] " + COPYING + " %s to %s (size: %,d bytes)",
          threadId, upload.getId(), source, dest, upload.getSize());
      final UploadEntry.State state = copyFile(upload, dest);
      upload.setState(state);
      upload.setEndTime(now());
      final String outcome = state == UploadEntry.State.succeeded
          ? ("Successful " + COPY_LC + " of")
          : ("Skipped " + COPY_LC + " of");
      println("[%s] [%04d] %s %s to %s  (size: %,d bytes) in %ss",
          threadId,
          upload.getId(),
          outcome,
          source,
          dest,
          upload.getSize(),
          StoreDurationInfo.humanTime(upload.getDuration()));
      return Outcome.succeeded(upload);
    } catch (Exception e) {
      upload.setState(UploadEntry.State.failed);
      upload.setException(e);
      upload.setEndTime(now());
      println("[%s] [%04d] Failed to "
          + COPY_LC
          + " %s to %s: %s", threadId, upload.getId(), source, dest, e);
      LOG.debug(COPY_CAPS
          + " {} to {} failed", source, dest, e);
      noteException(e);
      return Outcome.failed(upload, e);
    }
  }

  /**
   * Upload one file; uses readFully, fails if the stream
   * is shorter than expected, and logs close time.
   * @param upload upload entry
   * @param dest test path
   * @return the outcome (skipped/succeeded)
   * @throws IOException failure
   */
  private UploadEntry.State copyFile(final UploadEntry upload, final Path dest)
      throws IOException, InterruptedException {
    final Path source = upload.getSource();
    long remaining = upload.getSize();
    final long id = upload.getId();

    FileStatus sourceStatus = upload.getSourceStatus();
    boolean s3aCreatePerformance = destDidNotExist;

    if (update && !destDidNotExist) {
      // update is set, and as the dest path may exist, look for it.
      try {
        // update is set: only upload if the source is newer
        final FileStatus destStatus = destFS.getFileStatus(dest);

        if (destStatus.getLen() == sourceStatus.getLen()
            && sourceStatus.getModificationTime() <= destStatus.getModificationTime()) {
          // source is older than dest
          debug("Skipping "
                  + COPY_LC
                  + " of %s to %s",
              source, dest);
          return UploadEntry.State.skipped;
        }
        // file exists and will be overwritten
        debug("Overwriting {}", dest);
        s3aCreatePerformance = true;
      } catch (FileNotFoundException fnfe) {
        // dest doesn't exist; no need to worry about overwriting.
        s3aCreatePerformance = true;
      }
    }

    int bufferSize = (int) Math.min(blockSize, remaining);

    // now, very aggressive write call, especially in update where we know the dest path
    // is being overwritten
    final FSDataOutputStreamBuilder output = destFS.createFile(dest)
        .overwrite(overwrite || update)
        .progress(progress)
        .recursive()
        .bufferSize(bufferSize);
    // enable optimised read options on s3a fs and maybe others.
    output.opt("fs.s3a.create.performance",
        Boolean.valueOf(
            s3aCreatePerformance));  // either we know there's no file, or we're overwriting

    final FutureDataInputStreamBuilder input = sourceFS.openFile(source)
        .opt("fs.option.openfile.read.policy", "whole-file, sequential")
        .opt("fs.s3a.experimental.fadvise", "sequential")
        .opt("fs.option.openfile.length", Long.toString(sourceStatus.getLen()))
        .withFileStatus(sourceStatus);

    try (FSDataInputStream in = await(input.build());
         FSDataOutputStream out = output.build()) {
      byte[] buffer = new byte[bufferSize];
      while (remaining > 0) {
        int len = (int) Math.min(bufferSize, remaining);
        in.readFully(buffer, 0, len);
        out.write(buffer, 0, len);
        if (flush) {
          out.flush();
        }
        if (hflush) {
          out.hflush();
        }

        remaining -= len;
        if (verbose) {
          print(".");
        }
      }

      try (StoreDurationInfo d = new StoreDurationInfo(LOG, isVerbose(),
          "[%04d] close reader (%s)", id, source)) {
        in.close();
      }
      try (StoreDurationInfo d = new StoreDurationInfo(LOG, isVerbose(),
          "[%04d] close writer (%s)", id, dest)) {
        out.flush();
        out.close();
      }
      if (verbose) {
        println("[%04d] In: %s", id, in);
        println("[%04d] Out: %s", id, out);
      }
    }

    return UploadEntry.State.succeeded;
  }

  /**
   * Progress counter is incremented on every callback from
   * the output stream.
   */
  final AtomicLong progressCount = new AtomicLong();

  /**
   * Progress callback.
   */
  final Progressable progress = () -> {
    progressCount.incrementAndGet();
    if (verbose) {
      print("^");
    }
  };


  /**
   * Note the exception.
   * If this is the first exception, it's recorded, and,
   * if ignoreFailures == false, triggers the end of the upload
   * @param ex exception.
   */
  private synchronized void noteException(Exception ex) {
    if (firstException.compareAndSet(null, ex)) {
      if (!ignoreFailures) {
        exit.set(true);
      }
    }
  }

  /**
   * Find the final name of a given output file, given the job output directory
   * and the work directory.
   * @param srcFile the specific task output file
   * @return the final path for the specific output file
   * @throws IOException failure
   */
  private Path getFinalPath(Path srcFile) throws IOException {
    URI taskOutputUri = srcFile.toUri();
    URI relativePath = sourcePath.toUri().relativize(taskOutputUri);
    if (taskOutputUri == relativePath) {
      throw new PathIOException(sourcePath.toString(),
          "Cannot get the relative path:"
              + " base = " + sourcePath + " child = " + srcFile);
    }

    if (!relativePath.getPath().isEmpty()) {
      return new Path(destPath, relativePath.getPath());
    } else {
      // relative path is none.
      if (destPathStatus != null && destPathStatus.isFile()) {
        return destPath;
      } else {
        // source is a file, dest is a dir
        return new Path(destPath, srcFile.getName());
      }
    }
  }

  /**
   * Extracts the stats of a filesystem and dump it.
   * @param fs filesystem.
   */
  private void dumpStats(FileSystem fs, String header) {
    // Not supported on Hadoop 2.7
    println();
    println("%s: %s", header, fs.getUri());

    if (verbose) {
      // hope to see FS IOStats
      println("Filesystem %s", fs);
    }

    Iterator<StorageStatistics.LongStatistic> iterator
        = fs.getStorageStatistics().getLongStatistics();
    // convert to a (sorted) treemap
    SortedMap<String, Long> results = new TreeMap<>();

    while (iterator.hasNext()) {
      StorageStatistics.LongStatistic stat = iterator.next();
      results.put(stat.getName(), stat.getValue());
    }
    // log the results
    results.entrySet().forEach((entry) ->
        println("%s=%,d", entry.getKey(), entry.getValue()));

  }

  /**
   * Outcome of an upload: A count of uploaded data, outcome and error.
   */
  private static final class Outcome {

    private final boolean executed;

    private final UploadEntry upload;

    private final long bytesUploaded;

    private final Exception exception;

    private Outcome(
        final boolean executed,
        final UploadEntry upload,
        final long bytesUploaded,
        final Exception exception) {
      this.executed = executed;
      this.upload = upload;
      this.bytesUploaded = bytesUploaded;
      this.exception = exception;
    }

    private static Outcome notExecuted(final UploadEntry upload) {
      return new Outcome(false, upload, 0, null);
    }

    private static Outcome succeeded(final UploadEntry upload) {
      return new Outcome(true, upload, upload.getSize(), null);
    }

    private static Outcome failed(final UploadEntry upload,
        final Exception exception) {
      return new Outcome(true, upload, 0, exception);
    }

    private long getBytesUploaded() {
      return bytesUploaded;
    }

    private UploadEntry getUpload() {
      return upload;
    }

    private boolean isExecuted() {
      return executed;
    }

    private UploadEntry.State getState() {
      return upload.getState();
    }

    private boolean skipped() {
      return upload.inState(UploadEntry.State.skipped);
    }

    private Exception getException() {
      return exception;
    }

    private void maybeThrowException() throws Exception {
      if (exception != null) {
        throw exception;
      }
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
    try (final Cloudup tool = new Cloudup()) {
      return ToolRunner.run(tool, args);
    }
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
