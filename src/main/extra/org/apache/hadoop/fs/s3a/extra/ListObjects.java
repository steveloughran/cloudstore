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

package org.apache.hadoop.fs.s3a.extra;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.s3a.S3AUtils.translateException;
import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.LIMIT;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.fs.store.StoreUtils.checkArgument;

public class ListObjects extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(ListObjects.class);

  public static final String PURGE = "purge";
  public static final String DELETE = "delete";
  public static final String QUIET = "q";


  public static final String USAGE
      = "Usage: listobjects <path>\n"
      + optusage(DELETE, "delete the objects")
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(LIMIT, "limit", "limit of files to list")
      + optusage(PURGE, "purge directory markers")
      + optusage(QUIET, "quiet output")
      + optusage(TOKENFILE, "file", "Hadoop token file to load")
      + optusage(VERBOSE, "print verbose output")
      + optusage(XMLFILE, "file", "XML config file to load")
      ;

  public ListObjects() {
    createCommandFormat(1, 1,
        PURGE,
        DELETE,
        QUIET,
        VERBOSE);
    addValueOptions(TOKENFILE, XMLFILE, DEFINE, LIMIT);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (paths.size() < 1) {
      errorln(USAGE);
      return E_USAGE;
    }

    final Configuration conf = createPreconfiguredConfig();
    // stop auditing rejecting client direct calls.
    conf.setBoolean("fs.s3a.audit.reject.out.of.span.operations", false);
    int limit = getOptional(LIMIT).map(Integer::valueOf).orElse(0);
    int deletePageSize = 500;
    boolean purge = hasOption(PURGE);
    boolean quiet = hasOption(QUIET);

    boolean delete = hasOption(DELETE);
    String deleteType = "";
    if (delete) {
      deleteType = "objects";
      println("objects will be deleted");
      purge = false;
    } else if (purge) {
      deleteType = "markers";
      println("directory markers will be purged");
    }

    List<String> markers = new ArrayList<>();
    final Path source = new Path(paths.get(0));
    try (StoreDurationInfo duration = new StoreDurationInfo(LOG, "listobjects")) {
      S3AFileSystem fs = (S3AFileSystem) source.getFileSystem(conf);
      String bucket = ((S3AFileSystem) fs).getBucket();
      final AmazonS3 s3 = fs.getAmazonS3ClientForTesting("listobjects");
      String key = pathToKey(source);
      ListObjectsRequest request = createListObjectsRequest(
          source.toUri().getHost(), key, null);

      final ObjectListingIterator objects
          = new ObjectListingIterator(s3, source, request);
      List<String> prefixes = new ArrayList<>();
      int objectCount = 0;
      long size = 0;
      List<DeleteObjectsRequest.KeyVersion> objectsToDelete = new ArrayList<>(deletePageSize);

      heading("Listing objects under %s", source);
      while (objects.hasNext()) {
        final ObjectListing page = objects.next();
        for (S3ObjectSummary summary : page.getObjectSummaries()) {
          objectCount++;
          size += summary.getSize();
          String k = summary.getKey();

          if (!quiet) {
            println("object %s\t%s",
                stringify(summary),
                fs.getFileStatus(keyToPath(k)));
          }
          if (objectRepresentsDirectory(summary)) {
            markers.add(k);
          }
          if (delete) {
            objectsToDelete.add(new DeleteObjectsRequest.KeyVersion(k));
            if (objectsToDelete.size() >= deletePageSize) {
              final List<DeleteObjectsRequest.KeyVersion> keyVersions = objectsToDelete;
              objectsToDelete = new ArrayList<>(deletePageSize);
              delete(s3, bucket, keyVersions);
            }
          }
        }
        for (String prefix : page.getCommonPrefixes()) {
          prefixes.add(prefix);
        }
        if (limit > 0 && objectCount >= limit) {
          break;
        }
      }

      if (delete && !objectsToDelete.isEmpty()) {
        // final batch
        delete(s3, bucket, objectsToDelete);
      }
      println("");
      String action = delete ? "Deleted" : "Found";
      println("%s %d objects with total size %d bytes", action, objectCount, size);
      if (!prefixes.isEmpty()) {
        println("");
        heading("%s prefixes", prefixes.size());
        for (String prefix : prefixes) {
          println(prefix);
        }
      }
      if (!markers.isEmpty()) {
        println("");
        int markerCount = markers.size();
        heading("marker count: %d", markerCount);
        if (purge) {
          println("Purging all directory markers");
        }

        List<DeleteObjectsRequest.KeyVersion> kv = new ArrayList<>(
            markerCount);
        for (String marker : markers) {
          println(marker);
          if (purge) {
            kv.add(new DeleteObjectsRequest.KeyVersion(marker));
            if (kv.size() >= deletePageSize) {
              delete(s3, bucket, kv);
              kv.clear();
            }
          }
        }
        if (purge) {
          delete(s3, bucket, kv);
        } else {
          if (!delete) {
            println("\nTo delete these markers, rerun with the option -%s",
                PURGE);
          }
        }
      } else if (purge) {
        heading("No markers found to purge");
      }
      maybeDumpStorageStatistics(fs);
    }

    return 0;
  }

  /**
   * Delete the entries; clears the list. No-op if empty.
   * @param s3
   * @param bucket
   * @param kv
   */
  public void delete(AmazonS3 s3,
      String bucket,
      List<DeleteObjectsRequest.KeyVersion> kv) {
    if (kv.isEmpty()) {
      return;
    }
    StoreDurationInfo duration = new StoreDurationInfo(LOG, "deleting %s objects",
        kv.size());
    try {
      DeleteObjectsRequest request = new DeleteObjectsRequest(bucket)
          .withKeys(kv);
      s3.deleteObjects(request);
      kv.clear();
    } finally {
      duration.close();
    }
  }

  /**
   * Turns a path (relative or otherwise) into an S3 key.
   *
   * @param path input path, may be relative to the working dir
   * @return a key excluding the leading "/", or, if it is the root path, ""
   */
  String pathToKey(Path path) {
    checkArgument(path.isAbsolute(), "not absolute: " + path);
    if (path.toUri().getScheme() != null && path.toUri().getPath().isEmpty()) {
      return "";
    }

    return path.toUri().getPath().substring(1);
  }

  /**
   * Convert a path back to a key.
   * @param key input key
   * @return the path from this key
   */
  private Path keyToPath(String key) {
    return new Path("/" + key);
  }

  /**
   * Create a {@code ListObjectsRequest} request against this bucket.
   *
   * @param bucket bucket to listJitendra
   * @param key key for request
   * @param delimiter any delimiter
   * @return the request
   */
  private ListObjectsRequest createListObjectsRequest(final String bucket,
      String key,
      String delimiter) {
    ListObjectsRequest request = new ListObjectsRequest();
    request.setBucketName(bucket);
    request.setMaxKeys(5000);
    request.setPrefix(key);
    if (delimiter != null) {
      request.setDelimiter(delimiter);
    }
    return request;
  }

  /**
   * Predicate: does the object represent a directory?.
   * @param name object name
   * @param size object size
   * @return true if it meets the criteria for being an object
   */
  public boolean objectRepresentsDirectory(final String name,
      final long size) {
    boolean hasDirName = !name.isEmpty()
        && name.charAt(name.length() - 1) == '/';

    boolean isEmpty = size == 0L;
    if (hasDirName && !isEmpty) {
      println("Warning: object %s has length %d so is not a directory marker",
          name, size);
    }
    return hasDirName && isEmpty;
  }

  /**
   * Predicate: does the object represent a directory?.
   * @return true if it meets the criteria for being an object
   */
  public boolean objectRepresentsDirectory(
      S3ObjectSummary summary) {
    final String name = summary.getKey();
    final long size = summary.getSize();
    return objectRepresentsDirectory(name, size);
  }

  /**
   * String information about a summary entry.
   * @param summary summary object
   * @return string value
   */
  public static String stringify(S3ObjectSummary summary) {
    return String.format("\"%s\"\tsize: [%d]\ttag: %s", summary.getKey(),
        summary.getSize(),
        summary.getETag());
  }

  /**
   * Wraps up AWS `ListObjects` requests in a remote iterator
   * which will ask for more listing data if needed.
   *
   * That is:
   *
   * 1. The first invocation of the {@link #next()} call will return the results
   * of the first request, the one created during the construction of the
   * instance.
   *
   * 2. Second and later invocations will continue the ongoing listing,
   * calling {@link S3AFileSystem#continueListObjects} to request the next
   * batch of results.
   *
   * 3. The {@link #hasNext()} predicate returns true for the initial call,
   * where {@link #next()} will return the initial results. It declares
   * that it has future results iff the last executed request was truncated.
   *
   * Thread safety: none.
   */
  static class ObjectListingIterator implements RemoteIterator<ObjectListing> {

    /** The path listed. */
    private final Path listPath;

    private final AmazonS3 s3;

    /** The most recent listing results. */
    private ObjectListing objects;

    /** Indicator that this is the first listing. */
    private boolean firstListing = true;

    /**
     * Count of how many listings have been requested
     * (including initial result).
     */
    private int listingCount = 1;

    /**
     * Maximum keys in a request.
     */
    private int maxKeys;

    /**
     * Constructor -calls `listObjects()` on the request to populate the
     * initial set of results/fail if there was a problem talking to the bucket.
     * @param listPath path of the listing
     * @param request initial request to make
     * */
    ObjectListingIterator(
        AmazonS3 s3,
        Path listPath,
        ListObjectsRequest request) {
      this.listPath = listPath;
      this.maxKeys = 5000;
      this.s3 = s3;
      this.objects = s3.listObjects(request);
    }

    /**
     * Declare that the iterator has data if it is either is the initial
     * iteration or it is a later one and the last listing obtained was
     * incomplete.
     * @throws IOException never: there is no IO in this operation.
     */
    @Override
    public boolean hasNext() throws IOException {
      return firstListing || objects.isTruncated();
    }

    /**
     * Ask for the next listing.
     * For the first invocation, this returns the initial set, with no
     * remote IO. For later requests, S3 will be queried, hence the calls
     * may block or fail.
     * @return the next object listing.
     * @throws IOException if a query made of S3 fails.
     * @throws NoSuchElementException if there is no more data to list.
     */
    @Override
    public ObjectListing next() throws IOException {
      if (firstListing) {
        // on the first listing, don't request more data.
        // Instead just clear the firstListing flag so that it future calls
        // will request new data.
        firstListing = false;
      } else {
        try {
          if (!objects.isTruncated()) {
            // nothing more to request: fail.
            throw new NoSuchElementException("No more results in listing of "
                + listPath);
          }
          // need to request a new set of objects.
          LOG.debug("[{}], Requesting next {} objects under {}",
              listingCount, maxKeys, listPath);
          objects = s3.listNextBatchOfObjects(objects);
          listingCount++;
          LOG.debug("New listing status: {}", this);
        } catch (AmazonClientException e) {
          throw translateException("listObjects()", listPath, e);
        }
      }
      return objects;
    }

    @Override
    public String toString() {
      return "Object listing iterator against " + listPath
          + "; listing count " + listingCount
          + "; isTruncated=" + objects.isTruncated();
    }

    /**
     * Get the path listed.
     * @return the path used in this listing.
     */
    public Path getListPath() {
      return listPath;
    }

    /**
     * Get the count of listing requests.
     * @return the counter of requests made (including the initial lookup).
     */
    public int getListingCount() {
      return listingCount;
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
    return ToolRunner.run(new ListObjects(), args);
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
