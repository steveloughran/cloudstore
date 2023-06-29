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
import java.util.NoSuchElementException;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.VersionListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.fs.s3a.Invoker;
import org.apache.hadoop.fs.s3a.S3AFileSystem;

import static org.apache.hadoop.fs.s3a.extra.S3ListingSupport.MAX_KEYS;

/**
 * Wraps up AWS `ListVersions` requests in a remote iterator
 * which will ask for more listing data if needed.
 *
 * Thread safety: none.
 */
class ListVersionsIterator implements RemoteIterator<VersionListing> {

  private static final Logger LOG = LoggerFactory.getLogger(ListVersionsIterator.class);


  /** The path listed. */
  private final Path listPath;

  private final AmazonS3 s3;

  /** The most recent listing results. */
  private VersionListing objects;

  /** Indicator that this is the first listing. */
  private boolean firstListing = true;

  /**
   * Count of how many listings have been requested
   * (including initial result).
   */
  private int listingCount = 1;

  /**
   * Maximum keys in a request.
   */
  private int maxKeys;

  /**
   * Constructor -calls `listObjects()` on the request to populate the
   * initial set of results/fail if there was a problem talking to the bucket.
   * @param listPath path of the listing
   * @param request initial request to make
   */
  ListVersionsIterator(
      AmazonS3 s3,
      Path listPath,
      ListVersionsRequest request) {

    this.listPath = listPath;
    this.maxKeys = MAX_KEYS;
    this.s3 = s3;
    this.objects = s3.listVersions(request);
  }

  /**
   * Declare that the iterator has data if it is either is the initial
   * iteration or it is a later one and the last listing obtained was
   * incomplete.
   * @throws IOException never: there is no IO in this operation.
   */
  @Override
  public boolean hasNext() throws IOException {
    return firstListing || objects.isTruncated();
  }

  /**
   * Ask for the next listing.
   * For the first invocation, this returns the initial set, with no
   * remote IO. For later requests, S3 will be queried, hence the calls
   * may block or fail.
   * @return the next object listing.
   * @throws IOException if a query made of S3 fails.
   * @throws NoSuchElementException if there is no more data to list.
   */
  @Override
  public VersionListing next() throws IOException {
    if (firstListing) {
      // on the first listing, don't request more data.
      // Instead just clear the firstListing flag so that it future calls
      // will request new data.
      firstListing = false;
    } else {
      Invoker.once("listObjects()", listPath.toString(), () -> {
        if (!objects.isTruncated()) {
          // nothing more to request: fail.
          throw new NoSuchElementException("No more results in listing of "
              + listPath);
        }
        // need to request a new set of objects.
        LOG.debug("[{}], Requesting next {} objects under {}",
            listingCount, maxKeys, listPath);
        objects = s3.listNextBatchOfVersions(objects);
        listingCount++;
        LOG.debug("New listing status: {}", this);
      });
    }
    return objects;
  }

  @Override
  public String toString() {
    return "Version listing iterator against " + listPath
        + "; listing count " + listingCount
        + "; isTruncated=" + objects.isTruncated();
  }

  /**
   * Get the path listed.
   * @return the path used in this listing.
   */
  public Path getListPath() {
    return listPath;
  }

  /**
   * Get the count of listing requests.
   * @return the counter of requests made (including the initial lookup).
   */
  public int getListingCount() {
    return listingCount;
  }
}
