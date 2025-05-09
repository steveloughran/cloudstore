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
import java.time.Instant;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ObjectVersion;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.store.diag.Printout;

import static java.util.Objects.requireNonNull;
import static org.apache.hadoop.fs.s3a.sdk.S3ListingSupport.createListVersionsRequest;
import static org.apache.hadoop.fs.s3a.sdk.S3ListingSupport.isDirMarker;
import static org.apache.hadoop.fs.s3a.sdk.S3ListingSupport.objectRepresentsDirectory;
import static org.apache.hadoop.fs.s3a.sdk.S3ListingSupport.pathToKey;
import static org.apache.hadoop.fs.s3a.sdk.S3ListingSupport.result;

public class ListAndProcessVersionedObjects {

  private static final Logger LOG = LoggerFactory.getLogger(ListVersions.class);

  private final String name;

  private final Printout out;

  private final S3AFileSystem fs;

  private final Path source;

  private final SummaryProcessor processor;

  private final Instant ageLimit;

  private final long limit;

  private long tombstones = 0;

  private long fileTombstones = 0;

  private long hidden = 0;

  private long hiddenData = 0;

  private long hiddenZeroByteFiles = 0;

  private long dirMarkers = 0;

  private long hiddenDirMarkers = 0;

  private long objectCount = 0;


  private long totalSize = 0;

  private long processedCount;

  public ListAndProcessVersionedObjects(
      final String name,
      final Printout out,
      final S3AFileSystem fs,
      final Path source,
      final SummaryProcessor processor,
      final Instant ageLimit,
      final long limit) {
    this.name = requireNonNull(name);
    this.out = requireNonNull(out);
    this.fs = requireNonNull(fs);
    this.source = requireNonNull(source);
    this.processor = requireNonNull(processor);
    this.ageLimit = requireNonNull(ageLimit);
    this.limit = limit;
  }

  public long getTombstones() {
    return tombstones;
  }


  public long getFileTombstones() {
    return fileTombstones;
  }

  public long getDirTombstones() {
    return tombstones - fileTombstones;
  }

  public long getHidden() {
    return hidden;
  }

  public long getHiddenData() {
    return hiddenData;
  }

  public long getHiddenZeroByteFiles() {
    return hiddenZeroByteFiles;
  }

  public long getDirMarkers() {
    return dirMarkers;
  }

  public long getHiddenDirMarkers() {
    return hiddenDirMarkers;
  }


  public long execute() throws IOException {
    if (ageLimit.toEpochMilli() > 0) {
      out.println("Skipping entries older than %s", ageLimit);
      if (ageLimit.isAfter(Instant.now())) {
        // happens if millis were used or something else went wrong
        out.warn("the filter time is greater than the current time");
      }
    }
    final S3Client s3 = fs.getS3AInternals().getAmazonS3Client(name);
    String key = pathToKey(source);
    ListObjectVersionsRequest request = createListVersionsRequest(
        source.toUri().getHost(), key, null);

    final Iterator<ListObjectVersionsResponse> objectVersions =
        s3.listObjectVersionsPaginator(request).iterator();

    processedCount = 0;
    out.heading("Processing %s", source);
    try {

      boolean finished = false;
      while (!finished && objectVersions.hasNext()) {
        final ListObjectVersionsResponse page = objectVersions.next();
        for (ObjectVersion summary : page.versions()) {
          // maybe skip
          if (summary.lastModified().isBefore(ageLimit)) {
            continue;
          }
          objectCount++;

          final long size = summary.size();
          totalSize += size;

          final boolean isDirMarker = isDirMarker(summary);
          dirMarkers += result(isDirMarker);
          if (!summary.isLatest()) {
            if (!isDirMarker) {
              hidden++;
              hiddenData += size;
              hiddenZeroByteFiles += result(size == 0);
            } else {
              hiddenDirMarkers++;
            }
          }
          if (processor.process(summary, fs.keyToQualifiedPath(summary.key()), false)) {
            processedCount++;
            if (limit > 0 && processedCount >= limit) {
              finished = true;
              break;
            }
          }
        }
        for (DeleteMarkerEntry tombstone : page.deleteMarkers()) {
          tombstones++;
          fileTombstones += result(!objectRepresentsDirectory(tombstone.key(), 0));
          if (processor.processTombstone(fs.keyToQualifiedPath(tombstone.key()), tombstone)) {
            processedCount++;
            if (limit > 0 && processedCount >= limit) {
              finished = true;
              break;
            }
          }
        }
      }
    } finally {
      processor.close();
    }
    return processedCount;
  }

  public long getObjectCount() {
    return objectCount;
  }

  public long getTotalSize() {
    return totalSize;
  }

  static class NoopProcessor implements SummaryProcessor {

    @Override
    public boolean process(final ObjectVersion summary, final Path path,
        final boolean isDeleteMarker) throws IOException {
      return true;
    }

    @Override
    public void close() throws IOException {

    }
  }

}
