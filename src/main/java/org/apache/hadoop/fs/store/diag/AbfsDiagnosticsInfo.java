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
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.StoreDurationInfo;

/**
 * Abfs diagnostics.
 */
public class AbfsDiagnosticsInfo extends StoreDiagnosticsInfo {

  private static final Logger LOG = LoggerFactory.getLogger(
      AbfsDiagnosticsInfo.class);

  private static final Object[][] options = {

      {"abfs.external.authorization.class", false, false},
      {"fs.abfs.impl", false, false},
      {"fs.abfss.impl", false, false},
      {"fs.azure.abfs.endpoint", false, false},
      {"fs.azure.account.auth.type", false, false},
      {"fs.azure.account.hns.enabled", false, false},
      {"fs.azure.account.keyprovider", false, false},
      {"fs.azure.account.oauth.provider.type", false, false},
      {"fs.azure.account.oauth2.client.id", false, false},
      {"fs.azure.account.oauth2.client.secret", true, true},
      {"fs.azure.account.oauth2.client.endpoint", false, false},
      {"fs.azure.account.oauth2.msi.authority", false, false},
      {"fs.azure.account.oauth2.msi.endpoint", false, false},
      {"fs.azure.account.oauth2.msi.tenant", false, false},
      {"fs.azure.account.oauth2.user.name", false, false},
      {"fs.azure.account.oauth2.user.password", true, true},
      {"fs.azure.account.oauth2.refresh.token", true, true},
      {"fs.azure.account.oauth2.refresh.token.endpoint", true, true},
      {"fs.azure.always.use.https", false, false},
      {"fs.azure.appendblob.directories", false, false},
      {"fs.azure.atomic.rename.key", false, false},
      {"fs.azure.block.location.impersonatedhost", false, false},
      {"fs.azure.block.size", false, false},
      {"fs.azure.concurrentRequestCount.out", false, false},
      {"fs.azure.concurrentRequestCount.in", false, false},
      {"fs.azure.createRemoteFileSystemDuringInitialization", false, false},
      {"fs.azure.custom.token.fetch.retry.count", false, false},
      {"fs.azure.delegation.token.provider.type", false, false},
      {"fs.azure.disable.outputstream.flush", false, false},
      {"fs.azure.enable.abfslistiterator", false, false},
      {"fs.azure.enable.autothrottling", false, false},
      {"fs.azure.enable.conditional.create.overwrite", false, false},
      {"fs.azure.enable.delegation.token", false, false},
      {"fs.azure.enable.flush", false, false},
      {"fs.azure.identity.transformer.enable.short.name", false, false},
      {"fs.azure.identity.transformer.domain.name", false, false},
      {"fs.azure.identity.transformer.service.principal.id", false, false},
      {"fs.azure.identity.transformer.service.principal.substitution.list", false, false},
      {"fs.azure.identity.transformer.skip.superuser.replacement", false, false},
      {"fs.azure.io.read.tolerate.concurrent.append", false, false},
      {"fs.azure.io.retry.backoff.interval", false, false},
      {"fs.azure.io.retry.max.backoff.interval", false, false},
      {"fs.azure.io.retry.max.retries", false, false},
      {"fs.azure.io.retry.min.backoff.interval", false, false},
      {"fs.azure.io.read.tolerate.concurrent.append", false, false},
      {"fs.azure.list.max.results", false, false},
      {"fs.azure.oauth.token.fetch.retry.max.retries", false, false},
      {"fs.azure.oauth.token.fetch.retry.min.backoff.interval", false, false},
      {"fs.azure.oauth.token.fetch.retry.max.backoff.interval", false, false},
      {"fs.azure.objectmapper.threadlocal.enabled", false, false},
      {"fs.azure.readahead.range", false, false},
      {"fs.azure.readaheadqueue.depth", false, false},
      {"fs.azure.read.alwaysReadBufferSize", false, false},
      {"fs.azure.read.readahead.blocksize", false, false},
      {"fs.azure.read.request.size", false, false},
      {"fs.azure.read.readahead.blocksize", false, false},
      {"fs.azure.sas.token.provider.type", false, false},
      {"fs.azure.sas.token.renew.period.for.streams", false, false},
      {"fs.azure.secure.mode", false, false},
      {"fs.azure.skipUserGroupMetadataDuringInitialization", false, false},
      {"fs.azure.shellkeyprovider.script", false, false},
      {"fs.azure.ssl.channel.mode", false, false},
      {"fs.azure.user.agent.prefix", false, false},
      {"fs.azure.use.upn", false, false},
      {"fs.azure.write.enableappendwithflush", false, false},
      {"fs.azure.write.request.size", false, false},
      {"fs.azure.write.max.concurrent.requests", false, false},
      {"fs.azure.write.max.requests.to.queue", false, false},

      /* committer */
      {"mapreduce.outputcommitter.factory.scheme.s3a", false, false},
      {"mapreduce.fileoutputcommitter.marksuccessfuljobs", false, false},
      {"mapreduce.fileoutputcommitter.algorithm.version.v1.experimental.mv.threads", false, false},
      {"mapreduce.fileoutputcommitter.algorithm.version.v1.experimental.parallel.task.commit", false, false},
      {"mapreduce.manifest.committer.cleanup.move.to.trash", false, false},
      {"mapreduce.manifest.committer.io.thread.count", false, false},
      {"mapreduce.manifest.committer.validate.output", false, false},

      /* Test setup. */
      {"fs.azure.test.account.name", false, false},
      {"fs.azure.test.namespace.enabled", false, false},
      {"fs.azure.abfs.account.name", false, false},
      {"fs.contract.test.fs.abfs", false, false},
      {"", false, false},
  };

  public static final String[] classnames = {
      "com.fasterxml.jackson.annotation.JsonProperty",
      "com.fasterxml.jackson.core.JsonFactory",
      "com.fasterxml.jackson.databind.ObjectReader",
      "org.apache.http.client.utils.URIBuilder",
      "org.apache.hadoop.fs.azurebfs.AzureBlobFileSystem",
  };

  public static final String[] optionalClassnames = {
      "org.wildfly.openssl.OpenSSLProvider",
      "com.google.common.base.Preconditions",
      "org.apache.hadoop.thirdparty.com.google.common.base.Preconditions",
      "org.apache.hadoop.mapreduce.lib.output.committer.manifest.ManifestCommitter"
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
  public String[] getOptionalClassnames(final Configuration conf) {
    return optionalClassnames;
  }

  @Override
  protected void validateConfig(final Printout printout,
      final Configuration conf)
      throws IOException {
    super.validateConfig(printout, conf);
    warnOnInvalidDomain(printout, ".dfs.core.windows.net",
        "https://docs.microsoft.com/en-us/azure/storage/data-lake-storage/introduction-abfs-uri");
  }

  @Override
  public List<URI> listEndpointsToProbe(final Configuration conf)
      throws IOException, URISyntaxException {
    List<URI> uris = new ArrayList<>(2);
    addUriOption(uris, conf, "fs.azure.account.oauth2.refresh.token.endpoint", "",
        "https://login.microsoftonline.com/Common/oauth2/token");
    return uris;
  }

  @Override
  public void validateFilesystem(final Printout printout,
      final Path path,
      final FileSystem filesystem) throws IOException {
    super.validateFilesystem(printout, path, filesystem);

    try (StoreDurationInfo ignored = new StoreDurationInfo(LOG,
        "Probing for bucket being classic Azure or ADLS Gen 2 Storage")) {
      filesystem.getAclStatus(new Path("/"));
      printout.println("FileSystem %s is an ADLS Gen 2 store",
          filesystem.getUri());
    } catch (UnsupportedOperationException e) {

      printout.warn("FileSystem %s IS NOT an ADLS Gen 2 store",
          filesystem.getUri());
      printout.warn("Some operations will be slow/non-atomic");
    } catch (Exception e) {
      printout.warn("FileSystem %s returned an error in getIsNamespaceEnabled(): %s",
          filesystem.getUri(), e.toString());
      printout.debug("Error calling getIsNamespaceEnabled()", e);
    }
  }
}
