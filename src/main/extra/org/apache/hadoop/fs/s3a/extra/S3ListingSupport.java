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

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  public static ListObjectsRequest createListObjectsRequest(final String bucket,
      String key,
      String delimiter) {
    ListObjectsRequest request = new ListObjectsRequest();
    request.setBucketName(bucket);
    request.setMaxKeys(MAX_KEYS);
    request.setPrefix(key);
    if (delimiter != null) {
      request.setDelimiter(delimiter);
    }
    return request;
  }
  /**
   * Create a {@code ListObjectsRequest} request against this bucket.
   * @param bucket bucket to listJitendra
   * @param key key for request
   * @param delimiter any delimiter
   * @return the request
   */
  public static ListVersionsRequest createListVersionsRequest(final String bucket,
      String key,
      String delimiter) {
    ListVersionsRequest request = new ListVersionsRequest();
    request.setBucketName(bucket);
    request.setPrefix(key);
    request.setMaxResults(MAX_KEYS);
    if (delimiter != null) {
      request.setDelimiter(delimiter);
    }
    return request;
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
  public static String stringify(S3ObjectSummary summary) {
    return String.format("\"%s\"\tsize: [%d]\t%s\ttag: %s", summary.getKey(),
        summary.getSize(),
        df.format(summary.getLastModified()),
        summary.getETag());
  }
}
