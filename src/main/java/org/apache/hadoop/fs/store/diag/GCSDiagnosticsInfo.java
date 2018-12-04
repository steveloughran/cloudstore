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
      "com.google.cloud.hadoop.util.AccessTokenProvider",
      // Knox Integration (WiP)
      "org.apache.knox.gateway.shell.knox.token.Token",
      "org.apache.commons.configuration.Configuration",
      "com.google.api.client.util.DateTime",
      "org.apache.knox.gateway.cloud.idbroker.google.CloudAccessBrokerTokenProvider",
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
      {"fs.gs.enable.service.account.auth", false, false},
      {"fs.gs.http.transport.type", false, false},
      {"fs.gs.inputstream.inplace.seek.limit", false, false},
      {"fs.gs.outputstream.type", false, false},
      {"fs.gs.proxy.address", false, false},
      {"fs.gs.project.id", false, false},
      {"fs.gs.reported.permissions", false, false},
      {"fs.gs.service.account.auth.email", false, false},
      {"fs.gs.service.account.auth.keyfile", false, false},

      {"google.cloud.auth.client.id", true, false},
      {"google.cloud.auth.client.secret", true, true},
      {"google.cloud.auth.client.file", false, false},
      {"google.cloud.auth.null.enable", false, false},
      {"google.cloud.auth.service.account.enable", false, false},
      {"google.cloud.auth.service.account.email", false, false},
      {"google.cloud.auth.service.account.keyfile", false, false},
      {"google.cloud.auth.service.account.json.keyfile", false, false},
      {"", false, false},
  };

  public GCSDiagnosticsInfo(final URI fsURI) {
    super(fsURI);
  }

  @Override
  public String getName() {
    return "gcs";
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

  @Override
  public List<URI> listOptionalEndpointsToProbe(final Configuration conf)
      throws IOException, URISyntaxException {
    List<URI> l = new ArrayList<>(0);
    l.add(new URI("http://169.254.169.254"));
    return l;
  }
}
