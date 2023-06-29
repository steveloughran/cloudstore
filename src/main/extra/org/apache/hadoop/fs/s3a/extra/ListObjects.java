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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.s3a.Invoker.once;
import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.LIMIT;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.fs.store.diag.S3ADiagnosticsInfo.FS_S3A_AUDIT_REJECT_OUT_OF_SPAN_OPERATIONS;

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
      + optusage(XMLFILE, "file", "XML config file to load");

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
    conf.setBoolean(FS_S3A_AUDIT_REJECT_OUT_OF_SPAN_OPERATIONS, false);
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
    S3AFileSystem fs = null;
    final Path source = new Path(paths.get(0));
    try (StoreDurationInfo duration = new StoreDurationInfo(LOG,
        "listobjects")) {
      fs = (S3AFileSystem) source.getFileSystem(conf);
      String bucket = fs.getBucket();
      final AmazonS3 s3 = fs.getAmazonS3ClientForTesting("listobjects");
      String key = S3ListingSupport.pathToKey(source);
      ListObjectsRequest request = S3ListingSupport.createListObjectsRequest(
          source.toUri().getHost(), key, null);

      final ObjectListingIterator objects = new ObjectListingIterator(s3, source, request);
      List<String> prefixes = new ArrayList<>();
      int objectCount = 0;
      long size = 0;
      List<DeleteObjectsRequest.KeyVersion> objectsToDelete =
          new ArrayList<>(deletePageSize);

      heading("Listing objects under %s", source);
      boolean finished = false;
      while (!finished && objects.hasNext()) {
        final ObjectListing page = objects.next();
        for (S3ObjectSummary summary : page.getObjectSummaries()) {
          objectCount++;
          if (limit > 0 && objectCount >= limit) {
            finished = true;
            break;
          }
          size += summary.getSize();
          String k = summary.getKey();

          if (!quiet) {

            // print the output, if verbose call getFileStatus for the s3a view.
            String extra;
            if (isVerbose()) {
              extra = "\t" + fs.getFileStatus(S3ListingSupport.keyToPath(k));
            } else {
              extra = "";
            }
            println("[%05d] %s%s",
                objectCount,
                S3ListingSupport.stringify(summary),
                extra);
          }
          if (objectRepresentsDirectory(summary)) {
            markers.add(k);
          }
          if (delete) {
            objectsToDelete.add(new DeleteObjectsRequest.KeyVersion(k));
            if (objectsToDelete.size() >= deletePageSize) {
              final List<DeleteObjectsRequest.KeyVersion> keyVersions =
                  objectsToDelete;
              objectsToDelete = new ArrayList<>(deletePageSize);
              delete(s3, bucket, keyVersions);
            }
          }

        }
        for (String prefix : page.getCommonPrefixes()) {
          prefixes.add(prefix);
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
    } finally {
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
      List<DeleteObjectsRequest.KeyVersion> kv) throws IOException {
    if (kv.isEmpty()) {
      return;
    }
    try (StoreDurationInfo duration = new StoreDurationInfo(LOG, "deleting %s objects",
        kv.size())) {
      DeleteObjectsRequest request = new DeleteObjectsRequest(bucket)
          .withKeys(kv);
      once("delete", kv.toString(), () -> s3.deleteObjects(request));
      kv.clear();
    }
  }

  /**
   * Predicate: does the object represent a directory?.
   * @return true if it meets the criteria for being an object
   */
  public boolean objectRepresentsDirectory(
      S3ObjectSummary summary) {
    final String name = summary.getKey();
    final long size = summary.getSize();
    return S3ListingSupport.objectRepresentsDirectory(name, size);
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
