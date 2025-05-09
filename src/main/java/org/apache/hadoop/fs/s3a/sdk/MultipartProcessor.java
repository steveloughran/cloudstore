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
import java.util.NoSuchElementException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;

import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.fs.s3a.S3AFileSystem;

public class MultipartProcessor {

  private final S3AFileSystem fs;

  private final S3Client amazonS3;


  public MultipartProcessor(final S3AFileSystem fs) {
    this.fs = fs;
    amazonS3 = fs.getS3AInternals().getAmazonS3Client("api");
  }

  public PartIterator partListing(String key, final String id) {
    return new PartIterator(key, id);
  }


  public class PartIterator implements RemoteIterator<ListPartsResponse> {

    private final String key;

    private final String id;

    private boolean firstListing = true;

    private ListPartsResponse partListing;

    private ListPartsRequest request;

    PartIterator(final String key, final String id) {
      this.key = key;
      this.id = id;
    }

    public void listFirst() {
      request = ListPartsRequest.builder()
          .bucket(fs.getBucket())
          .key(key)
          .uploadId(id)
          .build();
      partListing = amazonS3.listParts(request);
    }

    public void listNext() {
      request = request.toBuilder()
          .partNumberMarker(partListing.nextPartNumberMarker())
          .build();
      partListing = amazonS3.listParts(request);
    }

    @Override
    public boolean hasNext() throws IOException {
      return firstListing || (partListing != null && partListing.isTruncated());

    }

    @Override
    public ListPartsResponse next() throws IOException {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      if (firstListing) {
        firstListing = false;
        listFirst();
      } else {
        // not first listing, so there's a valid, truncated part
        listNext();
      }
      return null;
    }
  }


}
