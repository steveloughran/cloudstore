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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;

/**
 * This is just a template to use when creating diagnostics bindings.
 */
public class GCSDiagnosticsInfo extends StoreDiagnosticsInfo {

  /**
   * Mandatory classnames.
   */
  public static final String[] classnames = {
      "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFS",
      "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystemBase",
  };

  /**
   *  Optional classnames.
   */
  public static final String[] optionalClassnames = {
      "com.google.cloud.hadoop.repackaged.gcs.com.google.cloud.hadoop.gcsio.GoogleCloudStorage",
  };

  /**
   * List of options for filesystems. 
   * Each entry must be a tuple of (string, password, sensitive).
   * "password" entries are read via Configuration.getPassword(),
   * so will be read from a credential file.
   * Sensitive strings don't have their values fully printed.
   */
  private static final Object[][] options = {

      {"fs.gs.client.id", true, false},
      {"fs.gs.client.secret", true, true},

      {"fs.gs.application.name.suffix", false, false},
      {"fs.gs.auth.access.token.provider.impl", false, false},
      {"fs.gs.authorization.handler.impl", false, false},
      {"fs.gs.authorization.handler.properties", false, false},
      {"fs.gs.batch.threads", false, false},
      {"fs.gs.block.size", false, false},
      {"fs.gs.bucket.delete.enable", false, false},
      {"fs.gs.checksum.type", false, false},
      {"fs.gs.cooperative.locking.enable", false, false},
      {"fs.gs.cooperative.locking.expiration.timeout.ms", false, false},
      {"fs.gs.cooperative.locking.max.concurrent.operations", false, false},
      {"fs.gs.copy.with.rewrite.enable", false, false},
      {"fs.gs.create.items.conflict.check.enable", false, false},
      {"fs.gs.delegation.token.binding", false, false},
      {"fs.gs.enable.service.account.auth", false, false},
      {"fs.gs.encryption.algorithm", false, false},
      {"fs.gs.encryption.key", false, false},
      {"fs.gs.encryption.key.hash", false, false},
      {"fs.gs.glob.algorithm", false, false},
      {"fs.gs.grpc.checksums.enable", false, false},
      {"fs.gs.grpc.enable", false, false},
      {"fs.gs.grpc.read.metadata.timeout.ms", false, false},
      {"fs.gs.grpc.read.timeout.ms", false, false},
      {"fs.gs.grpc.server.address", false, false},
      {"fs.gs.grpc.write.buffered.requests", false, false},
      {"fs.gs.grpc.write.timeout.ms", false, false},
      {"fs.gs.http.connect-timeout", false, false},
      {"fs.gs.http.max.retry", false, false},
      {"fs.gs.http.read-timeout", false, false},
      {"fs.gs.http.transport.type", false, false},
      {"fs.gs.implicit.dir.repair.enable", false, false},
      {"fs.gs.inputstream.fadvise", false, false},
      {"fs.gs.inputstream.fast.fail.on.not.found.enable", false, false},
      {"fs.gs.inputstream.inplace.seek.limit", false, false},
      {"fs.gs.inputstream.min.range.request.size", false, false},
      {"fs.gs.inputstream.support.gzip.encoding.enable", false, false},
      {"fs.gs.lazy.init.enable", false, false},
      {"fs.gs.list.max.items.per.call", false, false},
      {"fs.gs.marker.file.pattern", false, false},
      {"fs.gs.max.requests.per.batch", false, false},
      {"fs.gs.max.wait.for.empty.object.creation.ms", false, false},
      {"fs.gs.outputstream.buffer.size", false, false},
      {"fs.gs.outputstream.direct.upload.enable", false, false},
      {"fs.gs.outputstream.pipe.buffer.size", false, false},
      {"fs.gs.outputstream.pipe.type", false, false},
      {"fs.gs.outputstream.sync.min.interval.ms", false, false},
      {"fs.gs.outputstream.type", false, false},
      {"fs.gs.outputstream.upload.cache.size", false, false},
      {"fs.gs.outputstream.upload.chunk.size", false, false},
      {"fs.gs.project.id", false, false},
      {"fs.gs.project.id", false, false},
      {"fs.gs.proxy.address", false, false},
      {"fs.gs.proxy.password", false, true},
      {"fs.gs.proxy.username", false, false},
      {"fs.gs.reported.permissions", false, false},
      {"fs.gs.reported.permissions", false, false},
      {"fs.gs.requester.pays.mode", false, false},
      {"fs.gs.requester.pays.mode", false, false},
      {"fs.gs.service.account.auth.email", false, false},
      {"fs.gs.service.account.auth.keyfile", false, false},
      {"fs.gs.status.parallel.enable", false, false},
      {"fs.gs.storage.http.headers", false, false},
      {"fs.gs.storage.root.url", false, false},
      {"fs.gs.storage.service.path", false, false},
      {"fs.gs.working.dir", false, false},
      {"fs.viewfs.overload.scheme.target.gs.impl", false, false},
      {"google.cloud.auth.client.file", false, false},
      {"google.cloud.auth.client.id", true, false},
      {"google.cloud.auth.client.secret", true, true},
      {"google.cloud.auth.null.enable", false, false},
      {"google.cloud.auth.service.account.email", false, false},
      {"google.cloud.auth.service.account.enable", false, false},
      {"google.cloud.auth.service.account.json.keyfile", false, false},
      {"google.cloud.auth.service.account.keyfile", false, false},
  };

  public GCSDiagnosticsInfo(final URI fsURI) {
    super(fsURI);
  }

  @Override
  public String getName() {
    return "gs";
  }

  @Override
  public String getDescription() {
    return "Filesystem Connector for Google Cloud Storage";
  }

  @Override
  public String getHomepage() {
    return "https://cloud.google.com/dataproc/docs/concepts/connectors/cloud-storage";
  }

  @Override
  public Object[][] getFilesystemOptions() {
    return options;
  }

  @Override
  public String[] getClassnames(final Configuration conf) {
    return classnames;
  }

  @Override
  public String[] getOptionalClassnames(final Configuration conf) {
    return optionalClassnames;
  }

 /* @Override
  public List<URI> listOptionalEndpointsToProbe(final Configuration conf)
      throws IOException, URISyntaxException {
    List<URI> l = new ArrayList<>(0);
    l.add(new URI("http://169.254.169.254"));
    return l;
  }
 */

  @Override
  protected void validateConfig(final Printout printout,
      final Configuration conf)
      throws IOException {
    super.validateConfig(printout, conf);

    // now print everything fs.s3a.ext, assuming that
    // there are no secrets in it. Don't do that.
    printPrefixedOptions(printout, conf, "fs.gs.ext.");
  }
}
