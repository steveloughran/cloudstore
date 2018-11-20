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

import org.apache.hadoop.conf.Configuration;

/**
 * Wasb diagnostics.
 * Doesn't include credential information.
 */
public class WasbDiagnosticsInfo extends StoreDiagnosticsInfo {

  private static final Object[][] options = {

      {"fs.azure.user.agent.prefix", false, false},
      {"fs.azure.secure.mode", false, false},
      {"fs.azure.local.sas.key.mode", false, false},
      {"fs.azure.atomic.rename.dir", false, false},
      {"fs.azure.flatlist.enable", false, false},
      {"fs.azure.autothrottling.enable", false, false},
      {"fs.azure.enable.kerberos.support", false, false},
      {"fs.azure.enable.spnego.token.cache", false, false},
      {"fs.azure.cred.service.urls", false, false},
      {"fs.azure.saskeygenerator.http.retry.policy.enabled", false, false},
      {"fs.azure.saskeygenerator.http.retry.policy.spec", false, false},
      {"fs.azure.saskey.cacheentry.expiry.period", false, false},
      {"fs.azure.authorization.remote.service.urls", false, false},
      {"fs.azure.delegation.token.service.urls", false, false},
  };

  public static final String[] classnames = {
      "com.fasterxml.jackson.databind.ObjectReader",
      "com.google.common.base.Preconditions",
      "com.microsoft.azure.storage.StorageErrorCode",
      "org.apache.commons.logging.Log",
      "org.apache.http.client.methods.HttpGet",
      "org.eclipse.jetty.util.ajax.JSON",
      "org.eclipse.jetty.util.log.Log",
      "org.apache.hadoop.fs.azure.NativeAzureFileSystem",
  };

  public static final String[] optionalClassnames = {
      "org.apache.commons.lang.StringUtils",
      "org.apache.commons.lang3.StringUtils"
  };
  public WasbDiagnosticsInfo(final URI fsURI) {
    super(fsURI);
  }

  @Override
  public String getName() {
    return "Azure WASB connector";
  }

  @Override
  public String getDescription() {
    return "ASF Filesystem Connector to Microsoft Azure Storage";
  }

  @Override
  public String getHomepage() {
    return "https://hadoop.apache.org/docs/current/hadoop-azure/index.html";
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
  protected void validateConfig(final Printout printout,
      final Configuration conf)
      throws IOException {
    super.validateConfig(printout, conf);
    warnOnInvalidDomain(printout, ".blob.core.windows.net", 
        "https://docs.microsoft.com/en-us/azure/storage/blobs/storage-custom-domain-name");
  }

}
