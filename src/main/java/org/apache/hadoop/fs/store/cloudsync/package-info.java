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

/**
 * CloudSync Algorithm.
 *
 * The source is assumed to be a classic filesystem where directory tree walks
 * are efficient.
 *
 * Its directory tree is listed via a treewalk, to build a list of files
 * <i>and</i> directories.
 * This is converted into a local model of the directory tree.
 *
 * The destination is assumed to be a store where a
 * {@code FileSystem listFiles(path, recursive=true)} operation
 * is believed to be significantly faster than a treewalk.
 *
 * Ths is precisely the situation encountered with the delete phase of a
 * distcp backup from HDFS to S3/
 *
 * The destination scan is used to list objects at the far end.
 * As the results are retrieved, the local model of the dir tree
 * is checked.
 *
 * Possible conditions.
 * <ol>
 *   <li>Dest file in source: keep.</li>
 *   <li>Dest file not in source: queue for deletion.</li>
 *   <li>
 *     Parent entry of dest file not in source and not yet deleted in dest:
 *   queue parent for deletion, add directory in source tree to indicate that
 *   this has been done. Use the topmost entry under an existing directory
 *   entry for this delete request, so all delete requests under the tree
 *   will share the same parent.
 *   </li>
 *   <li>Parent entry of dest file already queued for deletion: ignore.</li>
 * </ol>
 * The highest entry in the directory tree of deleted dirs must be identified,
 * so its bulk delete is as efficient as possible.
 *
 * The deletion queue is serviced by a separate threadpool whose threads
 * randomly select entries from the queue for deletion.
 * This is so as to reduce the effects of throttling.
 * If the deletions are happening at the same rate as the listing, then
 * there is no throttling; there is no need to select randomly.
 * The more throttling/the bigger the backlog (which can also be driven
 * by the size of directories to delete, as that is O(files), the
 * more entries there are to randomly select, hence throttling
 * mitigated more.
 *
 * Does DELETE get throttled?
 *
 * It is moot: calls are made to probe for and potentially create parent
 * directory markers, these GET/PUT requests are throttled.
 * Spreading the operations across the destination store may reduce
 * these effects; at worst it will have no effect.
 *
 * There is also support for backpressure from the delete operations: with
 * a limited queue size for pending deletes, the more pending delete operations
 * there are, the more the queue fills up: the thread generating the list
 * of files to delete is blocked.
 * *
 * An improvement would be to actually slow down the posting of DELETE
 * requests when throttling was detected: this would require the delete threads
 * to be able to detect this. Measuring the time to service a {@code delete()}
 * call is insufficient. as deleting larger directory trees is inherently slower.
 *
 *
 * Performance estimates
 * <ul>
 *   <li>Time to list source is cost of treewalk.</li>
 *   <li>
 *     This is <i>O(directories)</i> + time to marshall/unmarshall the
 *     file status arrays.
 *   </li>
 *   <li>
 *     Memory used to cache treewalk data is <i>O(directories + files)</i>.
 *   </li>
 *   <li>
 *     If only the path name is used in each entry in the tree, and a byte to
 *     represent state (file/dir/deleted dir), size of record can be low.
 *   </li>
 *   <li>
 *     Storage of all entries of a directory can be in an {@code ArrayList}
 *     whose initial size ; the size is that of the number of entries
 *     returned by {@code getFileStatus()}.
 *   </li>
 *   <li>
 *     Thus: {@code sizeof(record) + (directories * sizeof(ArrayList))}
 *   </li>
 *   <li>
 *     As new deleted directory entries are added to the tree, these lists
 *     of child entries will go; there is a cost here for reallocating new
 *     arrays and copying old data. The more directories deleted, the higher
 *     this overhead.
 *   </li>
 *   <li>Time to list destination depends on the implementation.</li>
 *   <li>
 *     If using the base {@code FileSystem.listFiles()}, this is the same as
 *     a treewalk; recursion handled by a stack size <i>O(width * depth)</i>.
 *   </li>
 *   <li>
 *     If using an object store which supports bulk listing of all descendants
 *     then the time will be <i>O(files/list-respons-size)</i> and any
 *     throttling overhead.
 *   </li>
 *   <li>
 *     Time to delete a file will be <i>O(1)</i>, plus any throttling overhead.
 *    </li>
 *   <li>
 *     Time to delete a directory on a filesystem with an atomic directory delete
 *     should be <i>O(1)</i>, plus any throttling overhead.
 *   </li>
 *   <li>
 *     Time to delete a directory on an object store where it is somehow mimiced
 *     will be the time for a bulk listing of children, plus time to issue
 *     single/bulk directory delete calls, plus any throttling overhead.
 *   </li>
 *   <li>
 *     There's also the time for every file listed to walk the tree to
 *     determine if a parent directory has been deleted. This is <i>O(depth)</i>
 *     The time to examine the children of each entry will be a factor:
 *     a sorted arraylist will permit a binary search.
 *   </li>
 *   <li>
 *     Assuming the listing returns an ordered list of objects (as S3 does).
 *     The lookup overhead can be eliminated for a sequence of files if the last
 *     found deleted/undeleted directory is cached.
 *   </li>
 *   <li>
 *     Each file entry would be examined to see if the cached entry is a parent.
 *   </li>
 *   <li>
 *     If it is, and it is marked for deletion: discard file info.
 *   </li>
 *   <li>
 *     If it is, and is not marked for deletion, examine child to see if it
 *     exists. Queue file for deletion iff it is not found.
 *   </li>
 *   <li>
 *     If the cached directory is not found, search the directory tree
 *     to find the first directory which exists; queue its child for
 *     deletion, add an entry in the source tree to mark deleted directory,
 *     and then cache this value.
 *   </li>
 *
 * </ul>
 *
 * <h2>Limitations of the algorithm</h2>
 *
 * By using a treewalk to build up the source directory tree, it is inefficient
 * at listing the source directory when using an object store as the source:
 * it is only performant for filesystem-to-object-store backups.
 *
 * The alternative strategy would be to use the recursive listFiles call in
 * the source tree, and then infer the directory tree from the responses.
 * This would be very inefficient with any filesystem source which used
 * the base recursive treewalk: it would be discarding the tree and rebuilding
 * it.
 *
 * <h2>Correctness</h2>
 *
 * The algorithm is correct iff
 * <ol>
 *   <li>
 *     It deletes all files and directories which are in the destination
 *     directory tree but not in the source.
 *   </li>
 *   <li>
 *     It preserves all file and directories which are in the source tree.
 *   </li>
 * </ol>
 *
 * For a safety check, the deletion threads could examine
 * the tree before issuing the delete request. As this would be in a
 * parallelize process, provided there was no/minimal locking of directory
 * entries, the cost of this validation should be low.
 * Specifically, it should be the time to walk the tree. Even if the
 * destination scan thread has deleted directory markers added, as these
 * are not needed in the safety check, we do not need a consistent view
 * of the source tree at this point.
 *
 * <h2>Concurrency issues</h2>
 *
 *
 * What happens when entries are deleted from the remote store while their
 * listing is still in progress?
 * This can occur if the parent directory is scheduled for deletion --and
 * the deletion operation is faster than the listing process.
 * Because the directory entry is added to the source tree, these entries will
 * not be queued for deletion.
 *
 * Can a directory be queued for deletion more than once?
 * Not with a sequential directory listing sequence adding deleted directory
 * markers.
 *
 * What if an external agent updates the destination directory tree during
 * this operation?
 * Don't do that: data may be lost.
 *
 * What if an external agent updates the source tree during this operation?
 * Changes to the tree may not be complete or valid.
 * The only changes would be detected are:
 * directory is deleted or deleted + replaced file a file between
 * the listing of the parent directory and its own treewalk.

 * Proposed: if. during the treewalk, an inconsistency is detected, the
 * synchronization process is halted. No changes will have been
 * made to the destination filesystem at this point.
 *
 * <h3>Security Issues</h3>
 *
 * What happens if the application lacks the permissions to list
 * some/all entries in the source directory tree?
 *
 * The algorithm will fail; no changes will have been made to the destination.
 *
 * What happens if the application lacks the permissions to list or delete
 * some/all entries in the destination store?
 * The algorithm will fail. Some remote entries may have been deleted.
 */

package org.apache.hadoop.fs.store.cloudsync;
