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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.ObjectVersion;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.s3a.impl.StoreContext;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.s3a.sdk.S3ListingSupport.isDirMarker;
import static org.apache.hadoop.fs.store.CommonParameters.LIMIT;
import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.fs.store.diag.S3ADiagnosticsInfo.FS_S3A_AUDIT_REJECT_OUT_OF_SPAN_OPERATIONS;

/**
 * Undeleter -removes tombstone markers (possibly) on top of files.
 */
public class Undelete extends StoreEntryPoint implements SummaryProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(Undelete.class);

  public static final String AGE = "age";

  public static final String DELETED = "deleted";

  public static final String DIRS = "dirs";

  public static final String OUTPUT = "out";

  public static final String QUIET = "q";

  public static final String SEPARATOR = "separator";

  public static final String SINCE = "since";


  public static final String USAGE
      = "Usage: listversions <path>\n"
      + STANDARD_OPTS
      + optusage(LIMIT, "limit", "limit of files to list")
      + optusage(AGE, "seconds", "Only include versions created in this time interval")
      + optusage(SINCE, "epoch-time", "Only include versions after this time");

  public static final String NAME = "undelete";

  private final int deletePageSize;

  private S3Client s3;

  private S3AFileSystem fs;

  private StoreContext context;

  private Path source;

  private List<ObjectIdentifier> deletions;

  public Undelete() {
    createCommandFormat(1, 1);
    addValueOptions(
        LIMIT,
        //OUTPUT,
        //SEPARATOR,
        AGE,
        SINCE
    );
    deletePageSize = 1000;
  }

  private void newDeletions() {
    deletions = new ArrayList<>(deletePageSize);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (paths.isEmpty()) {
      errorln(USAGE);
      return E_USAGE;
    }

    final Configuration conf = createPreconfiguredConfig();
    // stop auditing rejecting client direct calls.
    conf.setBoolean(FS_S3A_AUDIT_REJECT_OUT_OF_SPAN_OPERATIONS, false);
    int limit = getIntOption(LIMIT, 0);
    long since = getLongOption(SINCE, 0);

    final Optional<Long> age = getOptionalLong(AGE);
    if (age.isPresent()) {
      if (since > 0) {
        errorln("Only one of " + AGE + " and " + SINCE + " may be specified");
        return E_USAGE;
      }
      since = Instant.now().minusSeconds(age.get()).getEpochSecond();
    }
    Instant ageLimit = Instant.ofEpochSecond(since);

    source = new Path(paths.get(0));
    fs = (S3AFileSystem) source.getFileSystem(conf);
    context = fs.createStoreContext();

    s3 = fs.getS3AInternals().getAmazonS3Client(NAME);
    newDeletions();

    try (StoreDurationInfo duration = new StoreDurationInfo(getOut(), NAME)) {

      final ListAndProcessVersionedObjects listing =
          new ListAndProcessVersionedObjects(NAME,
              this,
              fs,
              source,
              this,
              ageLimit,
              limit);

      final long count = listing.execute();

      // final deletion
      if (!deletions.isEmpty()) {
        executeDeleteOperation();
      }

      println();

      println("Removed %,d tombstones\n", count);

    }

    return 0;
  }

  @Override
  public boolean process(final ObjectVersion summary, final Path path, final boolean isDeleteMarker) throws IOException {
    final boolean dirMarker = isDirMarker(summary);
    if (dirMarker) {
      // skip this
      return false;
    }
    // ok, queue for explicit delete;
    // for now this is blocking, though async IO would work too.
    println("%s @ %s", fs.keyToQualifiedPath(summary.key()), summary.versionId());
    deletions.add(ObjectIdentifier.builder().key(summary.key())
        .versionId(summary.versionId()).build());
    if (deletions.size() == deletePageSize) {
      executeDeleteOperation();
    }
    return true;
  }

  private void executeDeleteOperation() throws IOException {
    final DeleteObjectsRequest r = DeleteObjectsRequest.builder()
        .bucket(fs.getBucket())
        .delete(Delete.builder()
            .objects(deletions)
            .quiet(true)
            .build())
        .build();
    println();
    try (StoreDurationInfo duration = new StoreDurationInfo(getOut(),
        "deleting %,d objects", deletions.size())) {
      context.getInvoker().retry("delete", source.toString(), true, () ->
          s3.deleteObjects(r));
    }
    newDeletions();
  }


  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new Undelete(), args);
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
