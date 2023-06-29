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

package org.apache.hadoop.fs.store.diag;



/**
 * Common path capabilities.
 * This is a copy of org.apache.hadoop.fs.CommonPathCapabilities.
 */
public final class CapabilityKeys {

  public static final String FS_CAPABILITY_ETAGS_AVAILABLE = "fs.capability.etags.available";

  public static final String FS_AZURE_CAPABILITY_READAHEAD_SAFE =
      "fs.azure.capability.readahead.safe";


  /**
   * Does the store support
   * {@code FileSystem.setAcl(Path, List)},
   * {@code FileSystem.getAclStatus(Path)}
   * and related methods?
   * Value: {@value}.
   */
  public static final String FS_ACLS = "fs.capability.paths.acls";

  /**
   * Does the store support {@code FileSystem.append(Path)}?
   * Value: {@value}.
   */
  public static final String FS_APPEND = "fs.capability.paths.append";

  /**
   * Does the store support {@code FileSystem.getFileChecksum(Path)}?
   * Value: {@value}.
   */
  public static final String FS_CHECKSUMS = "fs.capability.paths.checksums";

  /**
   * Does the store support {@code FileSystem.concat(Path, Path[])}?
   * Value: {@value}.
   */
  public static final String FS_CONCAT = "fs.capability.paths.concat";

  /**
   * Does the store support {@code FileSystem.listCorruptFileBlocks(Path)} ()}?
   * Value: {@value}.
   */
  public static final String FS_LIST_CORRUPT_FILE_BLOCKS =
      "fs.capability.paths.list-corrupt-file-blocks";

  /**
   * Does the store support
   * {@code FileSystem.createPathHandle(FileStatus, Options.HandleOpt...)}
   * and related methods?
   * Value: {@value}.
   */
  public static final String FS_PATHHANDLES = "fs.capability.paths.pathhandles";

  /**
   * Does the store support {@code FileSystem.setPermission(Path, FsPermission)}
   * and related methods?
   * Value: {@value}.
   */
  public static final String FS_PERMISSIONS = "fs.capability.paths.permissions";

  /**
   * Does this filesystem connector only support filesystem read operations?
   * For example, the {@code HttpFileSystem} is always read-only.
   * This is different from "is the specific instance and path read only?",
   * which must be determined by checking permissions (where supported), or
   * attempting write operations under a path.
   * Value: {@value}.
   */
  public static final String FS_READ_ONLY_CONNECTOR =
      "fs.capability.paths.read-only-connector";

  /**
   * Does the store support snapshots through
   * {@code FileSystem.createSnapshot(Path)} and related methods??
   * Value: {@value}.
   */
  public static final String FS_SNAPSHOTS = "fs.capability.paths.snapshots";

  /**
   * Does the store support {@code FileSystem.setStoragePolicy(Path, String)}
   * and related methods?
   * Value: {@value}.
   */
  public static final String FS_STORAGEPOLICY =
      "fs.capability.paths.storagepolicy";

  /**
   * Does the store support symlinks through
   * {@code FileSystem.createSymlink(Path, Path, boolean)} and related methods?
   * Value: {@value}.
   */
  public static final String FS_SYMLINKS =
      "fs.capability.paths.symlinks";

  /**
   * Does the store support {@code FileSystem#truncate(Path, long)} ?
   * Value: {@value}.
   */
  public static final String FS_TRUNCATE =
      "fs.capability.paths.truncate";

  /**
   * Does the store support XAttributes through
   * {@code FileSystem#.setXAttr()} and related methods?
   * Value: {@value}.
   */
  public static final String FS_XATTRS = "fs.capability.paths.xattrs";

  /**
   * Probe for support for BatchListingOperations.
   */
  public static final String FS_EXPERIMENTAL_BATCH_LISTING =
      "fs.capability.batch.listing";

  /**
   * Does the store support multipart uploading?
   * Value: {@value}.
   */
  public static final String FS_MULTIPART_UPLOADER =
      "fs.capability.multipart.uploader";


  /**
   * Stream abort() capability implemented by Abortable.abort()}.
   * Value: {@value}.
   */
  public static final String ABORTABLE_STREAM =
      "fs.capability.outputstream.abortable";

  /**
   * Does this FS support etags?
   * That is: will FileStatus entries from listing/getFileStatus
   * probes support EtagSource and return real values.
   */
  public static final String ETAGS_AVAILABLE =
      "fs.capability.etags.available";

  /**
   * Are etags guaranteed to be preserved across rename() operations..
   * FileSystems MUST NOT declare support for this feature
   * unless this holds.
   */
  public static final String ETAGS_PRESERVED_IN_RENAME =
      "fs.capability.etags.preserved.in.rename";

  /**
   * Flag to indicate whether a stream is a magic output stream;
   * returned in {@code StreamCapabilities}
   * Value: {@value}.
   */
  public static final String STREAM_CAPABILITY_MAGIC_OUTPUT
      = "fs.s3a.capability.magic.output.stream";

  /**
   * Flag to indicate that a store supports magic committers.
   * returned in {@code PathCapabilities}
   * Value: {@value}.
   */
  public static final String STORE_CAPABILITY_MAGIC_COMMITTER
      = "fs.s3a.capability.magic.committer";

  /**
   * Does the FS Support S3 Select?
   * Value: {@value}.
   */
  public static final String S3_SELECT_CAPABILITY =
      "fs.s3a.capability.select.sql";

  /**
   * {@code PathCapabilities} probe to indicate that the filesystem
   * keeps directory markers.
   * Value: {@value}.
   */
  public static final String STORE_CAPABILITY_DIRECTORY_MARKER_AWARE
      = "fs.s3a.capability.directory.marker.aware";

  /**
   * {@code PathCapabilities} probe to indicate that the filesystem
   * keeps directory markers.
   * Value: {@value}.
   */
  public static final String STORE_CAPABILITY_DIRECTORY_MARKER_POLICY_KEEP
      = "fs.s3a.capability.directory.marker.policy.keep";

  /**
   * {@code PathCapabilities} probe to indicate that the filesystem
   * deletes directory markers.
   * Value: {@value}.
   */
  public static final String STORE_CAPABILITY_DIRECTORY_MARKER_POLICY_DELETE
      = "fs.s3a.capability.directory.marker.policy.delete";

  /**
   * {@code PathCapabilities} probe to indicate that the filesystem
   * keeps directory markers in authoritative paths only.
   * Value: {@value}.
   */
  public static final String
      STORE_CAPABILITY_DIRECTORY_MARKER_POLICY_AUTHORITATIVE =
      "fs.s3a.capability.directory.marker.policy.authoritative";

  /**
   * {@code PathCapabilities} probe to indicate that a path
   * keeps directory markers.
   * Value: {@value}.
   */
  public static final String STORE_CAPABILITY_DIRECTORY_MARKER_ACTION_KEEP
      = "fs.s3a.capability.directory.marker.action.keep";

  /**
   * {@code PathCapabilities} probe to indicate that a path
   * deletes directory markers.
   * Value: {@value}.
   */
  public static final String STORE_CAPABILITY_DIRECTORY_MARKER_ACTION_DELETE
      = "fs.s3a.capability.directory.marker.action.delete";
  /**
   * Stream supports multipart uploads to the given path.
   */
  public static final String STORE_CAPABILITY_DIRECTORY_MULTIPART_UPLOAD_ENABLED =
      "fs.s3a.capability.multipart.uploads.enabled";

  /**
   * Flag for create performance.
   * This is *not* a configuration option; it is for use in the
   * {code createFile()} builder.
   * Value {@value}.
   */
  public static final String FS_S3A_CREATE_PERFORMANCE =
      "fs.s3a.create.performance";

  /**
   * Prefix for adding a header to the object when created.
   * The actual value must have a "." suffix and then the actual header.
   * This is *not* a configuration option; it is only for use in the
   * {code createFile()} builder.
   * Value {@value}.
   */
  public static final String FS_S3A_CREATE_HEADER = "fs.s3a.create.header";

  /**
   * Streams that support IOStatistics context and capture thread-level
   * IOStatistics.
   */
  public static String IOSTATISTICS_CONTEXT = "fs.capability.iocontext.supported";


  /**
   * IOStatisticsSource API.
   */
  public static String IOSTATISTICS = "iostatistics";

  /**
   * Support for vectored IO api.
   * See {@code PositionedReadable#readVectored(List, IntFunction)}.
   */
  public static String VECTOREDIO = "in:readvectored";


  /**
   * Stream setReadahead capability implemented by
   * {@code CanSetReadahead#setReadahead(Long)}.
   */
  public static String READAHEAD = "in:readahead";

  /**
   * Stream setDropBehind capability implemented by
   * {@code CanSetDropBehind#setDropBehind(Boolean)}.
   */
  public static String DROPBEHIND = "dropbehind";

  /**
   * Stream unbuffer capability implemented by {@code CanUnbuffer#unbuffer()}.
   */
  public static String UNBUFFER = "in:unbuffer";

  /**
   * Stream read(ByteBuffer) capability implemented by
   * {@code ByteBufferReadable#read(java.nio.ByteBuffer)}.
   */
  public static String READBYTEBUFFER = "in:readbytebuffer";

  /**
   * Stream read(long, ByteBuffer) capability implemented by
   * {@code ByteBufferPositionedReadable#read(long, java.nio.ByteBuffer)}.
   */
  public static String PREADBYTEBUFFER = "in:preadbytebuffer";

  /**
   * Stream hflush capability implemented by {@code Syncable#hflush()}.
   *
   * Use the {@code #HSYNC} probe to check for the support of Syncable;
   * it's that presence of {@code hsync()} which matters.
   */
  @Deprecated
  public static String HFLUSH = "hflush";

  /**
   * Stream hsync capability implemented by {@code Syncable#hsync()}.
   */
  public static String HSYNC = "hsync";

  /**
   * Set of input/output stream capabilities to scan for.
   */
  public static String[] INPUTSTREAM_CAPABILITIES = {
      DROPBEHIND,
      IOSTATISTICS,
      IOSTATISTICS_CONTEXT,
      PREADBYTEBUFFER,
      READAHEAD,
      READBYTEBUFFER,
      UNBUFFER,
      VECTOREDIO,
      FS_AZURE_CAPABILITY_READAHEAD_SAFE
  };

  /**
   * Set of input/output stream capabilities to scan for.
   */
  public static String[] OUTPUTSTREAM_CAPABILITIES = {
      ABORTABLE_STREAM,
      HFLUSH,
      HSYNC,
      IOSTATISTICS,
      IOSTATISTICS_CONTEXT,
      STREAM_CAPABILITY_MAGIC_OUTPUT,
  };


  private CapabilityKeys() {
  }
}
