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
import java.time.Instant;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.S3VersionSummary;
import com.amazonaws.services.s3.model.VersionListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.store.diag.Printout;

import static java.util.Objects.requireNonNull;
import static org.apache.hadoop.fs.s3a.extra.S3ListingSupport.createListVersionsRequest;
import static org.apache.hadoop.fs.s3a.extra.S3ListingSupport.isDirMarker;
import static org.apache.hadoop.fs.s3a.extra.S3ListingSupport.pathToKey;
import static org.apache.hadoop.fs.s3a.extra.S3ListingSupport.result;

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
    final AmazonS3 s3 = fs.getAmazonS3ClientForTesting(name);
    String key = pathToKey(source);
    ListVersionsRequest request = createListVersionsRequest(
        source.toUri().getHost(), key, null);

    final ListVersionsIterator objects
        = new ListVersionsIterator(s3, source, request);

    processedCount = 0;
    out.heading("Processing %s", source);
    try {

      boolean finished = false;
      while (!finished && objects.hasNext()) {
        final VersionListing page = objects.next();
        for (S3VersionSummary summary : page.getVersionSummaries()) {
          // maybe skip
          if (summary.getLastModified().toInstant().isBefore(ageLimit)) {
            continue;
          }
          objectCount++;

          final long size = summary.getSize();
          totalSize += size;

          final boolean isDirMarker = isDirMarker(summary);
          dirMarkers += result(isDirMarker);
          if (summary.isDeleteMarker()) {
            tombstones++;
            fileTombstones += result(!isDirMarker);
          } else {
            if (!summary.isLatest()) {
              if (!isDirMarker) {
                hidden++;
                hiddenData += size;
                hiddenZeroByteFiles += result(size == 0);
              } else {
                hiddenDirMarkers++;
              }
            }
          }
          if (processor.process(summary, fs.keyToQualifiedPath(summary.getKey()))) {
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
    public boolean process(final S3VersionSummary summary, final Path path) throws IOException {
      return true;
    }

    @Override
    public void close() throws IOException {

    }
  }

}
