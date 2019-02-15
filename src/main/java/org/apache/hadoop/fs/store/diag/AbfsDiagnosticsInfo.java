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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.conf.Configuration;

/**
 * Abfs diagnostics.
 */
public class AbfsDiagnosticsInfo extends StoreDiagnosticsInfo {

  private static final Object[][] options = {

      {"abfs.external.authorization.class", false, false},
      {"fs.azure.abfs.endpoint", false, false},
      {"fs.azure.account.auth.type", false, false},
      {"fs.azure.account.keyprovider", false, false},
      {"fs.azure.account.oauth.provider.type", false, false},
      {"fs.azure.account.oauth2.client.id", false, false},
      {"fs.azure.account.oauth2.client.secret", true, true},
      {"fs.azure.account.oauth2.client.endpoint", false, false},
      {"fs.azure.account.oauth2.msi.tenant", false, false},
      {"fs.azure.account.oauth2.user.name", false, false},
      {"fs.azure.account.oauth2.user.password", true, true},
      {"fs.azure.account.oauth2.refresh.token", true, true},
      {"fs.azure.always.use.https", false, false},
      {"fs.azure.atomic.rename.key", false, false},
      {"fs.azure.block.location.impersonatedhost", false, false},
      {"fs.azure.block.size", false, false},
      {"fs.azure.createRemoteFileSystemDuringInitialization", false, false},
      {"fs.azure.delegation.token.provider.type", false, false},
      {"fs.azure.enable.autothrottling", false, false},
      {"fs.azure.enable.delegation.token", false, false},
      {"fs.azure.enable.flush", false, false},
      {"fs.azure.identity.transformer.enable.short.name", false, false},
      {"fs.azure.identity.transformer.domain.name", false, false},
      {"fs.azure.identity.transformer.service.principal.id", false, false},
      {"fs.azure.identity.transformer.service.principal.substitution.list", false, false},
      {"fs.azure.identity.transformer.skip.superuser.replacement", false, false},
      {"fs.azure.io.retry.backoff.interval", false, false},
      {"fs.azure.io.retry.max.retries", false, false},
      {"fs.azure.io.read.tolerate.concurrent.append", false, false},
      {"fs.azure.read.request.size", false, false},
      {"fs.azure.readaheadqueue.depth", false, false},
      {"fs.azure.secure.mode", false, false},
      {"fs.azure.skipUserGroupMetadataDuringInitialization", false, false},
      {"fs.azure.shellkeyprovider.script", false, false},
      {"fs.azure.ssl.channel.mode", false, false},
      {"fs.azure.user.agent.prefix", false, false},
      {"fs.azure.use.upn", false, false},
      {"fs.azure.write.request.size", false, false},
      {"", false, false},
      {"", false, false},
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
    String account = getFsURI().getHost();
    addAccountOption(optionList, "fs.azure.account.key",
        true, true);
    addAccountOption(optionList,
        "fs.azure.account.auth.type",
        false, false);
    addAccountOption(optionList,
        "fs.azure.account.oauth2.user.password",
        true, true);
    addAccountOption(optionList,
        "fs.azure.account.oauth2.refresh.token",
        true, true);
    addAccountOption(optionList,
        "fs.azure.account.oauth2.user.name",
        true, false);
    addAccountOption(optionList,
        "fs.azure.account.oauth2.msi.tenant",
        true, false);
    addAccountOption(optionList,
        "fs.azure.account.oauth2.client.endpoint",
        false, false);
    addAccountOption(optionList,
        "fs.azure.account.oauth2.client.id",
        true, false);
    addAccountOption(optionList,
        "fs.azure.account.oauth2.client.secret",
        true, true);
    addAccountOption(optionList,
        "fs.azure.account.oauth.provider.type",
        false, false);

    addAccountOption(optionList,
        "fs.azure.account.keyprovider",
        false, false);
    addAccountOption(optionList,
        "",
        false, false);
    addAccountOption(optionList,
        "",
        false, false);


    return optionList.toArray(new Object[0][0]);
  }

  /**
   * Add a new entry to a list.
   * @param list list to add to
   * @param key key to add
   * @param secret is it secret?
   * @param sensitive sensitive flag
   */
  protected void add(List<Object[]> list,
      String key,
      final boolean secret, boolean sensitive) {

    if (!key.isEmpty()) {
      list.add(new Object[]{key, secret, sensitive});
    }
  }

  /**
   * Add an account-specific option tuned for the host.
   * @param list list to add to
   * @param key key to add
   * @param secret is it secret?
   * @param sensitive sensitive flag
   */
  protected void addAccountOption(
      List<Object[]> list,
      String key,
      boolean secret, boolean sensitive) {
    if (!key.isEmpty()) {
      add(list, key + "." + getFsURI().getHost(), secret, sensitive);
    }
  }
  
  @Override
  public String[] getClassnames(final Configuration conf) {
    return classnames;
  }

  @Override
  protected void validateConfig(final Printout printout,
      final Configuration conf)
      throws IOException {
    super.validateConfig(printout, conf);
    warnOnInvalidDomain(printout, ".dfs.core.windows.net",
        "https://docs.microsoft.com/en-us/azure/storage/data-lake-storage/introduction-abfs-uri");
  }
}
