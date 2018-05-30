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

@InterfaceAudience.Private
@InterfaceStability.Evolving
package org.apache.hadoop.fs.tools.cloudup;

/**
 *
 * Cloudup.
 *
 * <pre>
 *   cloudup -s source -d dest [-o] [-i] [-l <largest>] [-t threads]
 * </pre>
 * Algorithm.
 *
 * The source path must be on the local filesystem.
 * The destination path may be any filesystem.
 * {@code FileSystem.copyFromLocalFile()} is used for the upload
 * If the filesystem implements a high-performance version of this,
 * as S3A does, then it is used to directly perform the upload.
 * Otherwise the source is opened as an input stream and written
 * to a filesystem-created output stream.
 * <ol>
 *   <li>
 *     A thread pool of T workers is created.
 *   </li>
 *   <li>
 *      One worker performs {@code FileSystem.listFiles()} to recursively
 *      list all source files
 *   </li>
 *   <li>
 *     Another prepares the destination.
 *   </li>
 *   <li>
 *     Once these tasks are completed, the upload begins.
 *   </li>
 *   <li>
 *     The largest L files are selected for upload first, to avoid them
 *     creating a long-tail of uploads.
 *   </li>
 *   <li>
 *     Each upload is performed in its own worker thread.
 *   </li>
 *   <li>
 *     The remaining files are selected at random from the list,
 *     to reduce throttling on uploads to individual shards in the
 *     remote store.
 *   </li>
 *   <li>
 *     The program waits for the uplaods to complete.
 *   </li>
 * </ol>
 *
 * Performance:
 *
 * This works best for a store with a high performance operation to
 * upload a local file. In S3A this is done with a direct API call
 * of the AWS SDK: we avoid all copying of the stream to new buffers or
 * block-sized files. This eliminates a complete local read and local
 * write of every byte.
 *
 * The upload of the largest L files is designed to handle the situation of
 * "X big files and Y small files, possibly targeting 2 or more shards of
 * the destination.
 *
 * Large file uploads can take the most end-to-end time. and most of
 * the bandwidth.
 * Small file creation/uploads can be more expensive per-byte than
 * the larger uploads, but use less bandwidth, so can be executed
 * in parallel with larger uploads without significantly slowing the
 * larger uploads.
 * Store often throttle writes, and, if sharded, to specific paths
 * in the store.
 *
 * Queuing uploads of the L largest files first is intended to avoid
 * one or two large uploads becoming initiated after all other operations
 * have completed.
 *
 * Randomly selecting uploads to queue after that first L uploads is
 * intended to reduce the risk of shard-level throttling.
 *
 * Weaknesses
 *
 * large uploads will compete for bandwidth; initiating all large
 * uploads in parallel may be counter-productive.
 *
 * By selecting remaining files to upload at random, there is still
 * the risk of the L+1'th largest file being queued last.
 *
 * All uploads are competing for local disk IO.
 *
 * There is no explicit bandwidth throttling.
 * Heavy bandwidth use may interfere with the network performance
 * of other applications sharing the same uplink to the object store.
 *
 * There is no explicit throtting of HTTP requests to the remote store.
 *
 *
 * It may be more efficient to have two worker pools: big files and small files.
 * The small file worker pool would be large and would take files of
 * up to a few 10s of KiBs. on the assumption that
 * because small uplaods are more expensive to initiate,
 *
 * The large file queue would take the larger files, and upload very
 * few at a time 1? 2?, sorting the order to do large uploads first.
 */

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;

