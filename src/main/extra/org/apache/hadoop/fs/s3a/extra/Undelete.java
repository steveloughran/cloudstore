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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.S3VersionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.s3a.impl.StoreContext;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.s3a.extra.S3ListingSupport.isDirMarker;
import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.LIMIT;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
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
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(LIMIT, "limit", "limit of files to list")
      + optusage(AGE, "seconds", "Only include versions created in this time interval")
      + optusage(SINCE, "epoch-time", "Only include versions after this time")
      + optusage(TOKENFILE, "file", "Hadoop token file to load")
      + optusage(XMLFILE, "file", "XML config file to load");

  public static final String NAME = "undelete";

  private final int deletePageSize;

  private AmazonS3 s3;

  private S3AFileSystem fs;

  private StoreContext context;

  private Path source;

  private List<DeleteObjectsRequest.KeyVersion> deletions;

  public Undelete() {
    createCommandFormat(1, 1);
    addValueOptions(TOKENFILE,
        XMLFILE,
        DEFINE,
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
    if (paths.size() < 1) {
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

    s3 = fs.getAmazonS3ClientForTesting(NAME);
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
  public boolean process(final S3VersionSummary summary, final Path path) throws IOException {
    final boolean deleteMarker = summary.isDeleteMarker();
    final boolean dirMarker = isDirMarker(summary);
    if (dirMarker || !deleteMarker) {
      // skip this
      return false;
    }
    // ok, queue for explicit delete;
    // for now this is blocking, though async IO would work too.
    println("%s @ %s", fs.keyToQualifiedPath(summary.getKey()), summary.getVersionId());
    deletions.add(new DeleteObjectsRequest.KeyVersion(summary.getKey(), summary.getVersionId()));
    if (deletions.size() == deletePageSize) {
      executeDeleteOperation();
    }
    return true;
  }

  private void executeDeleteOperation() throws IOException {
    final DeleteObjectsRequest r = new DeleteObjectsRequest(fs.getBucket())
        .withKeys(deletions)
        .withQuiet(true);

    println();
    try(StoreDurationInfo duration = new StoreDurationInfo(getOut(),
        "deleting %,d tombstones", deletions.size())) {
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
