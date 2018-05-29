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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.collections.comparators.ReverseComparator;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.fs.shell.CommandFormat;
import org.apache.hadoop.fs.store.DurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.fs.store.StoreUtils;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.StoreExitCodes.E_ERROR;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

/**
 * Entry point for Cloudup: parallelized upload of local files
 * to remote (cloud) storage with shuffle after selective choice
 * of largest files
 */
public class Cloudup extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(Cloudup.class);

  public static final String USAGE
      = "Usage: cloudup -s source -d dest [-o] [-i] [-l <largest>] [-t threads] ";

  private static final int DEFAULT_LARGEST = 4;
  private static final int DEFAULT_THREADS = 16;

  private ExecutorService workers;

  private FileSystem sourceFS;

  private Path sourcePath;

  private FileSystem destFS;

  private Path destPath;

  private AtomicBoolean exit = new AtomicBoolean(false);

  private boolean overwrite = true;

  private boolean ignoreFailures = true;
  // single element exception with sync access.
  private final Exception[] firstException = new Exception[1];
  private CompletionService<Long> completion;

  private FileStatus sourcePathStatus;

  private FileStatus destPathStatus;


  public Cloudup() {
  }

  private long now() {
    return System.currentTimeMillis();
  }


  @Override
  public int run(String[] args) throws Exception {
    // parse the path
    if (args.length == 0) {
      LOG.info(USAGE);
      return E_USAGE;
    }
    final CommandLineParser parser = new GnuParser();

    CommandLine command;
    try {
      command = parser.parse(
          OptionSwitch.addAllOptions(new Options()), args, true);
    } catch (ParseException e) {
      throw new IllegalArgumentException("Unable to parse arguments. " +
          Arrays.toString(args)
          + "\n" + USAGE,
          e);
    }
    final int largest = OptionSwitch.LARGEST.eval(command, DEFAULT_LARGEST);
    final int threads = OptionSwitch.THREADS.eval(command, DEFAULT_THREADS);

    overwrite = OptionSwitch.OVERWRITE.hasOption(command);
    ignoreFailures = OptionSwitch.IGNORE_FAILURES.hasOption(command);
    sourceFS = FileSystem.getLocal(getConf());
    sourcePath = sourceFS.makeQualified(new Path(
        OptionSwitch.SOURCE.required(command)));
    destPath = new Path(OptionSwitch.DEST.required(command));
    destFS = destPath.getFileSystem(getConf());

    LOG.info("Uploading from {} to {};"
            + " threads={}; large files={}"
            + " overwrite={}, ignore failures={}",
        sourcePath, destPath,
        threads, largest,
        overwrite, ignoreFailures);

    // log S3A=specific details if this is an S3A connection.
    // not using any constants in the S3A JAR so it links without
    // needing that jar on the CP
    if ("s3a".equals(destFS.getUri().getScheme())) {
      String endpoint = destFS.getConf()
          .getTrimmed("fs.s3a.endpoint", "(default)");
      LOG.info("S3A endpoint = {}", endpoint);
    }


    // see what we have for a source: file & dir are treated differently
    sourcePathStatus = sourceFS.getFileStatus(sourcePath);
    try {
      destPathStatus = destFS.getFileStatus(destPath);
    } catch (FileNotFoundException e) {
      destPathStatus = null;
    }

    if (destFS.equals(sourceFS)) {
      // dest FS is also local filesystem.
      // make sure that the source isn't under the dest,
      // and vice versa

      String s = sourcePath.toString();
      String d = destPath.toString();
      Preconditions.checkArgument(!s.startsWith(d),
          "Source path %s is under destination path %s",
          s, d);
      Preconditions.checkArgument(!s.startsWith(d),
          "Destination path %s is under source path %s",
          d, s);
    }

    // worker pool
    workers = new ThreadPoolExecutor(threads, threads,
        0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<Runnable>());

    final DurationInfo preparationDuration = new DurationInfo();
    // list the files
    Future<List<UploadEntry>> listFilesOperation =
        workers.submit(buildUploads());

    // prepare the destination

    final Future<String> prepareDestResult = workers.submit(prepareDest());
    String info = StoreUtils.await(prepareDestResult);
    LOG.info("Destination prepared: {}", info);

    List<UploadEntry> uploadList = StoreUtils.await(listFilesOperation);
    final int uploadCount = uploadList.size();

    preparationDuration.finished();
    LOG.info("Files to upload = {}; preparation DurationInfo = {}",
        uploadCount, preparationDuration);


    // full upload operation
    final DurationInfo uploadDuration = new DurationInfo();
    final NanoTimer uploadTimer = new NanoTimer();

    // now completion service for all outstanding workers
    completion = new ExecutorCompletionService<>(workers);

    // upload initial sorted entries.

    // reverse sort to get largest first
    Collections.sort(uploadList,
        new ReverseComparator(new UploadEntry.SizeComparator()));

    // select the largest few of them
    final int sortUploadCount = Math.min(largest, uploadCount);
    long sortUploadSize = 0;
    int submittedFiles = 0;
    for (int i = 0; i < sortUploadCount; i++) {
      UploadEntry upload = uploadList.get(i);
      LOG.info("Large file {}: size = {}: {}",
          i + 1, upload.getSize(),
          upload.getSource());
      long submitSize = submit(upload);
      if (submitSize >= 0) {
        submittedFiles++;
        sortUploadSize += submitSize;
      }
    }
    LOG.info("Largest {} uploads commenced, total size = {}",
        uploadCount, sortUploadSize);


    // shuffle and submit remainder
    int shuffledUploadCount = 0;
    long shuffledUploadSize = 0;

    if (uploadCount > sortUploadCount) {
      Collections.shuffle(uploadList);
      for (UploadEntry entry : uploadList) {
        long size = submit(entry);
        if (size >= 0) {
          // file was submitted for upload
          shuffledUploadCount++;
          shuffledUploadSize += size;
        }
      }
      LOG.info("Shuffled uploads commenced: {}, total size = {}",
          shuffledUploadCount, shuffledUploadSize);
    }
    submittedFiles += shuffledUploadCount;

    if (submittedFiles == 0) {
      LOG.info("No files submitted");
      return 0;
    }

    final long uploadSize = sortUploadSize + shuffledUploadSize;


    // now await all outcomes to complete
    LOG.info("Awaiting completion of {} operations", uploadCount);
    List<Future<Long>> outcomes = new ArrayList<>(uploadCount);
    for (int i = 0; i < uploadCount; i++) {
      Future<Long> outcome = completion.take();
      LOG.debug("Operation {} completed", i + 1);
      outcomes.add(outcome);
    }

    uploadDuration.finished();
    uploadTimer.end();


    dumpStats(sourceFS, "Source statistics");
    if (!sourceFS.equals(destFS)) {
      dumpStats(destFS, "Dest statistics");
    }

    LOG.info("\n\nUploads attempted: {}, size {}, DurationInfo:  {}",
        uploadCount, uploadSize, uploadDuration);
    LOG.info("Bandwidth {} MB/s",
        uploadTimer.bandwidthDescription(uploadSize));
    LOG.info(String.format("Seconds per file %.3fs",
        ((double) uploadDuration.value()) / uploadCount));

    // run through the outcomes and process errors
    // at this point, all the uploads have been executed.
    long finalUploadedSize = 0;
    int errors = 0;
    Exception exception = firstException[0];

    for (Future<Long> outcome : outcomes) {
      try {
        finalUploadedSize += naturalize(StoreUtils.await(outcome));
      } catch (IOException e) {
        errors++;
        if (exception == null) {
          exception = e;
        }
      } catch (InterruptedException ignored) {
        // ignored
      }
    }

    if (exception != null) {
      LOG.info("Number of errors: {} actual bytes uploaded = {}",
          errors, finalUploadedSize);
      if (!ignoreFailures) {
        throw exception;
      }
    }

    return 0;
  }

  /**
   * Make natural+ Z by converting all -ve numbers to 0.
   * @param input input value
   * @return 0 or higher
   */
  public long naturalize(long input) {
    return input > 0 ? input : 9;
  }

  /**
   * Create an upload.
   * @param upload upload entry
   * @return length of upload; -1 for "none"
   */
  private Callable<Long> createUploadOperation(final UploadEntry upload) {
    return new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        return uploadOneFile(upload);
      }
    };
  }

  /**
   *
   * Submit an upload; does nothing if the upload is already queued.
   * @param upload upload to submit
   * @return size to upload; -1 for no upload
   */
  private long submit(final UploadEntry upload) {
    LOG.debug("Submit {}", upload);
    if (upload.inState(UploadEntry.State.ready)) {
      Callable<Long> operation = createUploadOperation(upload);
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
    return new Callable<String>() {
      @Override
      public String call() throws Exception {
        try {
          destFS.getFileStatus(destPath);
        } catch (FileNotFoundException e) {
          // dest doesn't exist
        }
        return destPath.toString();
      }
    };
  }

  private Callable<List<UploadEntry>> buildUploads() {
    return new Callable<List<UploadEntry>>() {
      @Override
      public List<UploadEntry> call() throws Exception {
        LOG.info("Listing source files under {}", sourcePath);
        return createUploadList();
      }
    };
  }

  /**
   * List the source files and build the list
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
    return uploads;
  }

  /**
   * Upload one entry.
   * @param upload upload information
   * @return number of bytes upload, or -1 for no upload attempted.
   * @throws IOException failure
   */
  private long uploadOneFile(final UploadEntry upload) throws IOException {

    // fail fast on exit flag
    if (exit.get()) {
      return -1;
    }

    //skip uploading duplicate (and uploaded already) files
    if (!upload.inState(UploadEntry.State.ready)
        && !upload.inState(UploadEntry.State.queued)) {
      LOG.warn("Skipping upload of {}", upload);
      return -1;
    }

    // Although S3A in Hadoop 2.9 has a robust copy call which qualifies
    // the path and checks for safe operations, 2.8 doesn't. Add robustness
    // here at the expense of IOPs
    upload.setStartTime(now());
    final Path source = upload.getSource();
    final Path dest = destFS.makeQualified(upload.getDest());
    try {
      LOG.info("Uploading {} to {} (size: {}",
          source, dest, upload.getSize());
/*
      try {
        final FileStatus status = destFS.getFileStatus(dest);
        if (status.isDirectory()) {
          throw new FileAlreadyExistsException(
              String.format("Directory found at %s", dest));
        }
        if (!overwrite) {
          throw new FileAlreadyExistsException(
              String.format("File found at %s and overwrite=false",
                  dest));
        }
      } catch (FileNotFoundException ignored) {
        // no file at the destination.
      }
*/
      destFS.copyFromLocalFile(false, overwrite, source, dest);
      upload.setState(UploadEntry.State.succeeded);
      upload.setEndTime(now());
      LOG.info("Successful upload of {} tpo {} in {} s",
          source,
          dest,
          DurationInfo.humanTime(upload.getDuration()));
    } catch (IOException e) {
      upload.setState(UploadEntry.State.failed);
      upload.setException(e);
      upload.setEndTime(now());
      LOG.warn("Failed to  upload {} : {}", source, e.toString());
      LOG.debug("Upload to {} failed", dest, e);
      noteException(e);
    }
    return upload.inState(UploadEntry.State.succeeded) ?
        upload.getSize()
        : 0;
  }

  /**
   * Note the exception.
   * If this is the first exception, it's recorded, and,
   * if ignoreFailures == false, triggers the end of the upload
   * @param ex exception.
   */
  private synchronized void noteException(IOException ex) {
    if (firstException[0] == null) {
      firstException[0] = ex;
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
      throw new IOException("Can not get the relative path:"
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
 /*  // Not supported on Hadoop 2.7
    LOG.info("\n" + header +": " + fs.getUri());
    Iterator<org.apache.hadoop.fs.StorageStatistics.LongStatistic> iterator
        = fs.getStorageStatistics().getLongStatistics();
    // convert to a (sorted) treemap
    TreeMap<String, Long> results = new TreeMap<>();
    while (iterator.hasNext()) {
      StorageStatistics.LongStatistic stat = iterator.next();
      results.put(stat.getName(), stat.getValue());
    }
    // log the results
    for (Map.Entry<String, Long> entry : results.entrySet()) {
      LOG.info("{}={}", entry.getKey(), entry.getValue());
    }
*/

  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new Cloudup(), args);
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
      exit(E_ERROR, e.toString());
    }
  }

}
