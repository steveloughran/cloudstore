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

import java.io.IOException;
import java.net.URI;

import static org.apache.hadoop.fs.store.diag.DiagnosticsEntryPoint.toURI;

/**
 * Endpoint to probe for and the results of that probe.
 */
public final class EndpointProbe {

  /** URI. */
  public final URI endpoint;

  /** Generic description. */
  public final String description;

  /** where did the endpoint come from? */
  public final String origin;

  private final boolean expectWorldReadable;
  private boolean success;

  private boolean usedProxy;

  private String message;

  private String body;

  public EndpointProbe(final URI endpoint, final String description, final String origin,
      final boolean expectWorldReadable) {
    this.endpoint = endpoint;
    this.description = description;
    this.origin = origin;
    this.expectWorldReadable = expectWorldReadable;
  }

  public EndpointProbe(final String endpoint, final String description, final String origin,
      final boolean expectWorldReadable)
      throws IOException {
    this.expectWorldReadable = expectWorldReadable;
    this.endpoint = toURI(origin, endpoint);
    this.description = description;
    this.origin = origin;
  }

  @Override
  public String toString() {
    return "EndpointProbe{" +
        "endpoint=" + endpoint +
        ", description='" + description + '\'' +
        ", origin='" + origin + '\'' +
        ", success=" + success +
        ", usedProxy=" + usedProxy +
        '}';
  }

  public URI getEndpoint() {
    return endpoint;
  }

  public String getDescription() {
    return description;
  }

  public String getOrigin() {
    return origin;
  }

  public boolean isSuccess() {
    return success;
  }

  public void failed(String message) {
    this.success = false;
    this.message = message;
  }

  public void setSuccess(final boolean success) {
    this.success = success;
  }

  public boolean isUsedProxy() {
    return usedProxy;
  }

  public void setUsedProxy(final boolean usedProxy) {
    this.usedProxy = usedProxy;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }

  public String getBody() {
    return body;
  }

  public void setBody(final String body) {
    this.body = body;
  }

  public boolean isExpectWorldReadable() {
    return expectWorldReadable;
  }
}
