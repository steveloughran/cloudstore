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
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.amazonaws.services.s3.model.S3VersionSummary;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.tools.csv.SimpleCsvWriter;

import static org.apache.hadoop.fs.s3a.extra.S3ListingSupport.isDirMarker;

/**
 * write to csv; pulled out to make writing to avro etc easier in future.
 */
final class CsvVersionWriter extends ListAndProcessVersionedObjects.NoopProcessor {

  private final SimpleCsvWriter csv;

  private final DateFormat df = new SimpleDateFormat("yyyy-MM-ddZhh:mm:ss");

  private final boolean logDirs;

  private final boolean logDeleted;

  long index = 0;

  CsvVersionWriter(
      final OutputStream out,
      final boolean closeOutput,
      String separator,
      final boolean logDirs,
      final boolean logDeleted) throws
                                                                                        IOException {
    this.logDirs = logDirs;
    this.logDeleted = logDeleted;
    csv = new SimpleCsvWriter(out, separator, "\n", true, closeOutput);
    csv.columns(
        "index",
        "key",
        "path",
        "restore",
        "latest",
        "size",
        "tombstone",
        "directory",
        "date",
        "timestamp",
        "version",
        "etag");
    csv.newline();
  }

  @Override
  public void close() throws IOException {
    csv.close();
  }

  public boolean process(S3VersionSummary summary, Path path) throws IOException {
    final boolean deleteMarker = summary.isDeleteMarker();
    final boolean dirMarker = isDirMarker(summary);
    if (dirMarker && !logDirs) {
      return false;
    }
    if (deleteMarker && !logDeleted) {
      return false;
    }
    csv.columnL(++index);
    csv.column(summary.getKey());
    csv.column(path);
    csv.columnB(!deleteMarker && !dirMarker);
    csv.columnB(summary.isLatest());
    csv.columnL(summary.getSize());
    csv.columnB(deleteMarker);
    csv.columnB(dirMarker);
    final Date lastModified = summary.getLastModified();
    csv.column(df.format(lastModified));
    csv.columnL(lastModified.getTime());
    final String versionId = summary.getVersionId();
    csv.column(versionId);
    csv.column(summary.getETag());
    csv.newline();
    return true;
  }

  private long getIndex() {
    return index;
  }
}
