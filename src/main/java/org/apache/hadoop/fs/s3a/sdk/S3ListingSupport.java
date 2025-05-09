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

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.S3Object;

import org.apache.hadoop.fs.Path;

import static org.apache.hadoop.fs.store.StoreUtils.checkArgument;

/**
 * Listing support for {@link ListObjects} and others.
 */
public class S3ListingSupport {

  private static final Logger LOG = LoggerFactory.getLogger(S3ListingSupport.class);

  private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

  public static final int MAX_KEYS = 5000;

  /**
   * Turns a path (relative or otherwise) into an S3 key.
   * @param path input path, may be relative to the working dir
   * @return a key excluding the leading "/", or, if it is the root path, ""
   */
  public static String pathToKey(Path path) {
    checkArgument(path.isAbsolute(), "not absolute: " + path);
    if (path.toUri().getScheme() != null && path.toUri().getPath().isEmpty()) {
      return "";
    }

    return path.toUri().getPath().substring(1);
  }

  /**
   * Convert a path back to a key.
   * @param key input key
   * @return the path from this key
   */
  public static Path keyToPath(String key) {
    return new Path("/" + key);
  }

  /**
   * Create a {@code ListObjectsRequest} request against this bucket.
   * @param bucket bucket to listJitendra
   * @param key key for request
   * @param delimiter any delimiter
   * @return the request
   */
  public static ListObjectsV2Request createListObjectsRequest(final String bucket,
      String key,
      String delimiter) {
    ListObjectsV2Request.Builder request = ListObjectsV2Request.builder()
        .bucket(bucket)
        .maxKeys(MAX_KEYS)
        .prefix(key);
    if (delimiter != null) {
      request.delimiter(delimiter);
    }
    return request.build();
  }

  /**
   * Create a {@code ListObjectsRequest} request against this bucket.
   * @param bucket bucket to listJitendra
   * @param key key for request
   * @param delimiter any delimiter
   * @return the request
   */
  public static ListObjectVersionsRequest createListVersionsRequest(
      final String bucket,
      String key,
      String delimiter) {
    ListObjectVersionsRequest.Builder request = ListObjectVersionsRequest.builder()
        .bucket(bucket)
        .maxKeys(MAX_KEYS)
        .prefix(key);
    if (delimiter != null) {
      request.delimiter(delimiter);
    }
    return request.build();
  }

  /**
   * Predicate: does the object represent a directory?.
   * @param name object name
   * @param size object size
   * @return true if it meets the criteria for being an object
   */
  public static boolean objectRepresentsDirectory(final String name,
      final long size) {
    boolean hasDirName = !name.isEmpty()
        && name.charAt(name.length() - 1) == '/';

    boolean isEmpty = size == 0L;
    if (hasDirName && !isEmpty) {
      LOG.warn("Warning: object {} has length {} so is not a directory marker",
          name, size);
    }
    return hasDirName && isEmpty;
  }

  /**
   * String information about a summary entry.
   * @param summary summary object
   * @return string value
   */
  public static String stringify(S3Object summary) {
    return String.format("\"%s\"\tsize: [%d]\t%s\ttag: %s", summary.key(),
        summary.size(),
        df.format(summary.lastModified()),
        summary.eTag());
  }

  static boolean isDirMarker(final ObjectVersion summary) {
    return objectRepresentsDirectory(summary.key(), summary.size());
  }



  static int result(boolean b) {
    return b ? 1 : 0;
  }

}
