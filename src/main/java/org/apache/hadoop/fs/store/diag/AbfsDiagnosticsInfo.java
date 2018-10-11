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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.conf.Configuration;

/**
 * Abfs diagnostics.
 * Doesn't include credential information.
 */
public class AbfsDiagnosticsInfo extends StoreDiagnosticsInfo {

  private static final Object[][] options = {

      {"abfs.external.authorization.class", false},
      {"fs.azure.abfs.endpoint", false},
      {"fs.azure.account.keyprovider", false},
      {"fs.azure.account.oauth.provider.type", false},
      {"fs.azure.account.oauth2.client.id", false},
      {"fs.azure.account.oauth2.client.secret", true},
      {"fs.azure.account.oauth2.client.endpoint", false},
      {"fs.azure.account.oauth2.msi.tenant", false},
      {"fs.azure.account.oauth2.user.name", false},
      {"fs.azure.account.oauth2.user.password", true},
      {"fs.azure.account.oauth2.refresh.token", true},
      {"fs.azure.always.use.https", false},
      {"fs.azure.atomic.rename.key", false},
      {"fs.azure.block.location.impersonatedhost", false},
      {"fs.azure.block.size", false},
      {"fs.azure.delegation.token.provider.type", false},
      {"fs.azure.enable.delegation.token", false},
      {"fs.azure.enable.flush", false},
      {"fs.azure.read.request.size", false},
      {"fs.azure.readaheadqueue.depth", false},
      {"fs.azure.secure.mode", false},
      {"fs.azure.write.request.size", false},
      {"fs.azure.account.auth.type", false},
      {"fs.azure.ssl.channel.mode", false},
      {"", false},
  };

  public static final String[] classnames = {
      "com.fasterxml.jackson.annotation.JsonProperty",
      "com.google.common.base.Preconditions",
      "com.fasterxml.jackson.core.JsonFactory",
      "com.fasterxml.jackson.databind.ObjectReader",
      "com.microsoft.azure.storage.StorageErrorCode",
      "org.apache.http.client.utils.URIBuilder",
      "org.wildfly.openssl.OpenSSLProvider",
      "org.apache.hadoop.fs.azurebfs.AzureBlobFileSystem",
  };

  public AbfsDiagnosticsInfo(final URI fsURI) {
    super(fsURI);
  }

  @Override
  public String getName() {
    return "Azure Abfs connector";
  }

  @Override
  public String getDescription() {
    return "ASF Filesystem Connector to Microsoft Azure ABFS Storage";
  }

  @Override
  public String getHomepage() {
    return "https://hadoop.apache.org/docs/current/hadoop-azure/index.html";
  }

  @Override
  public Object[][] getFilesystemOptions() {

    List<Object[]> optionList = new ArrayList<>(
        Arrays.asList(AbfsDiagnosticsInfo.options));
    // dynamically create account-specific keys
    String account = fsURI.getHost();
    addAccountOption(optionList, "fs.azure.account.key", true);
    addAccountOption(optionList,
        "fs.azure.account.auth.type",
        false);
    addAccountOption(optionList,
        "fs.azure.account.oauth2.user.password",
        true);
    addAccountOption(optionList,
        "fs.azure.account.oauth2.refresh.token",
        true);
    addAccountOption(optionList,
        "fs.azure.account.oauth2.user.name",
        false);
    addAccountOption(optionList,
        "fs.azure.account.oauth2.msi.tenant",
        true);
    addAccountOption(optionList,
        "fs.azure.account.oauth2.client.endpoint",
        false);
    addAccountOption(optionList,
        "fs.azure.account.oauth2.client.id",
        false);
    addAccountOption(optionList,
        "fs.azure.account.oauth2.client.secret",
        true);
    addAccountOption(optionList,
        "fs.azure.account.oauth.provider.type",
        false);

    addAccountOption(optionList,
        "fs.azure.account.keyprovider",
        false);
    addAccountOption(optionList,
        "",
        false);
    addAccountOption(optionList,
        "",
        false);


    return optionList.toArray(new Object[0][0]);
  }

  /**
   * Add a new entry to a list.
   * @param list list to add to
   * @param key key to add
   * @param sensitive sensitive flag
   */
  protected void add(List<Object[]> list,
      String key,
      boolean sensitive) {

    if (!key.isEmpty()) {
      list.add(new Object[]{key, sensitive});
    }
  }

  protected void addAccountOption(List<Object[]> list,
      String key,
      boolean sensitive) {
    if (!key.isEmpty()) {
      add(list, key + "." + fsURI.getHost(), sensitive);
    }
  }
  
  @Override
  public String[] getClassnames(final Configuration conf) {
    return classnames;
  }

}
