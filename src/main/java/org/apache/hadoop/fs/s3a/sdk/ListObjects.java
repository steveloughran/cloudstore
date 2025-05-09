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

package org.apache.hadoop.fs.s3a.sdk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.s3a.Invoker.once;
import static org.apache.hadoop.fs.store.CommonParameters.LIMIT;
import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.diag.S3ADiagnosticsInfo.FS_S3A_AUDIT_REJECT_OUT_OF_SPAN_OPERATIONS;

public class ListObjects extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(ListObjects.class);

  public static final String PURGE = "purge";

  public static final String DELETE = "delete";

  public static final String QUIET = "q";


  public static final String USAGE
      = "Usage: listobjects <path>\n"
      + STANDARD_OPTS
      + optusage(DELETE, "delete the objects")
      + optusage(LIMIT, "limit", "limit of files to list")
      + optusage(PURGE, "purge directory markers")
      + optusage(QUIET, "quiet output")
      ;

  public ListObjects() {
    createCommandFormat(1, 1,
        PURGE,
        DELETE,
        QUIET);
    addValueOptions(
        LIMIT);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = processArgs(args, 2, -1, USAGE);

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
      final S3Client s3 = fs.getS3AInternals().getAmazonS3Client("listobjects");
      String key = S3ListingSupport.pathToKey(source);
      ListObjectsV2Request request = S3ListingSupport.createListObjectsRequest(
          source.toUri().getHost(), key, null);

      final ObjectListingIterator objects = new ObjectListingIterator(s3, source, request);
      List<String> prefixes = new ArrayList<>();
      int objectCount = 0;
      long size = 0;
      List<ObjectIdentifier> objectsToDelete =
          new ArrayList<>(deletePageSize);

      heading("Listing objects under %s", source);
      boolean finished = false;
      while (!finished && objects.hasNext()) {
        final ListObjectsV2Response page = objects.next();
        for (S3Object summary : page.contents()) {
          objectCount++;
          if (limit > 0 && objectCount >= limit) {
            finished = true;
            break;
          }
          size += summary.size();
          String k = summary.key();

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
            objectsToDelete.add(ObjectIdentifier.builder().key(k).build());
            if (objectsToDelete.size() >= deletePageSize) {
              final List<ObjectIdentifier> keyVersions =
                  objectsToDelete;
              objectsToDelete = new ArrayList<>(deletePageSize);
              delete(s3, bucket, keyVersions);
            }
          }

        }
        page.commonPrefixes().stream().map(CommonPrefix::prefix).forEach(prefixes::add);
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

        List<ObjectIdentifier> kv = new ArrayList<>(
            markerCount);
        for (String marker : markers) {
          println(marker);
          if (purge) {
            kv.add(ObjectIdentifier.builder().key(marker).build());
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
  public void delete(S3Client s3,
      String bucket,
      List<ObjectIdentifier> kv) throws IOException {
    if (kv.isEmpty()) {
      return;
    }
    try (StoreDurationInfo duration = new StoreDurationInfo(LOG, "deleting %s objects",
        kv.size())) {
      final DeleteObjectsRequest request = DeleteObjectsRequest.builder()
          .bucket(bucket)
          .delete(Delete.builder()
              .objects(kv)
              .quiet(true)
              .build())
          .build();
      once("delete", kv.toString(), () -> s3.deleteObjects(request));
      kv.clear();
    }
  }

  /**
   * Predicate: does the object represent a directory?.
   * @return true if it meets the criteria for being an object
   */
  public boolean objectRepresentsDirectory(
      S3Object summary) {
    final String name = summary.key();
    final long size = summary.size();
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
