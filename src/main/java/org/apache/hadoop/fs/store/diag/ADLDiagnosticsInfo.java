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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;

public class ADLDiagnosticsInfo extends StoreDiagnosticsInfo {

  private static final Logger LOG = LoggerFactory.getLogger(
      ADLDiagnosticsInfo.class);

  private static final Object[][] options = {

      {"fs.adl.oauth2.client.id", false},
      {"fs.adl.oauth2.credential", true},
      {"fs.adl.oauth2.access.token.provider.type", false},
      {"fs.adl.oauth2.refresh.token", false},
      {"fs.adl.oauth2.devicecode.clientapp.id", false},
      {"fs.adl.oauth2.msi.port", false},
      {"fs.adl.oauth2.refresh.url", false},
      {"adl.feature.client.cache.readahead", false},
      {"adl.feature.client.cache.drop.behind.writes", false},
      {"adl.debug.override.localuserasfileowner", false},
      {"", false},
      {"", false},
      {"", false},
      {"", false},
      {"", false},
      {"", false},
      {"", false},
      {"", false},
      {"", false},
      {"", false},
      {"", false},
  };

  public static final String[] classnames = {
      "org.apache.hadoop.fs.adl.AdlFileSystem",
      "com.microsoft.azure.datalake.store.ADLStoreClient",
  };

  public ADLDiagnosticsInfo(final URI fsURI) {
    super(fsURI);
  }

  @Override
  public String getName() {
    return "Azure Datalake connector";
  }

  @Override
  public String getDescription() {
    return "ASF Filesystem Connector to Microsoft Azure Datalake";
  }

  @Override
  public String getHomepage() {
    return "https://hadoop.apache.org/docs/current/hadoop-azure-datalake/index.html";
  }

  @Override
  public Object[][] getFilesystemOptions() {
    return options;
  }

  @Override
  public String[] getClassnames() {
    return classnames;
  }

  /**
   * Determine the S3 endpoint if set (or default.
   * {@inheritDoc}
   */
  @Override
  public List<URI> listEndpointsToProbe(final Configuration conf)
      throws URISyntaxException {
    List<URI> uris = new ArrayList<>(2);
    String endpoint = conf.getTrimmed("fs.adl.oauth2.refresh.url", "");
    if (!endpoint.isEmpty()) {
      uris.add(new URI(endpoint));
    }
    String bucket = fsURI.getHost();
    uris.add(new URI(String.format("https://%s", bucket)));
    return uris;
  }
}
