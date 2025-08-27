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
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.azurebfs.services.AbfsOutputStream;
import org.apache.hadoop.fs.store.StoreDurationInfo;

import static java.util.Objects.requireNonNull;
import static org.apache.hadoop.fs.azurebfs.constants.ConfigurationKeys.AZURE_KEY_ACCOUNT_KEYPROVIDER;
import static org.apache.hadoop.fs.store.StoreUtils.cat;
import static org.apache.hadoop.fs.store.StoreUtils.checkArgument;
import static org.apache.hadoop.fs.store.StoreUtils.sanitize;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.ETAGS_AVAILABLE;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.ETAGS_PRESERVED_IN_RENAME;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.FS_ACLS;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.FS_APPEND;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.FS_AZURE_CAPABILITY_READAHEAD_SAFE;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.FS_PERMISSIONS;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.FS_XATTRS;
import static org.apache.hadoop.fs.store.diag.OptionSets.HADOOP_TMP_DIR;
import static org.apache.hadoop.fs.store.diag.OptionSets.JAVA_NET_SYSPROPS;
import static org.apache.hadoop.fs.store.diag.OptionSets.STANDARD_ENV_VARS;
import static org.apache.hadoop.fs.store.diag.OptionSets.STANDARD_SYSPROPS;
import static org.apache.hadoop.fs.store.diag.OptionSets.X509;
import static org.apache.hadoop.fs.store.diag.StoreDiag.printCanonicalHostname;

/**
 * Abfs diagnostics.
 */
public class AbfsDiagnosticsInfo extends StoreDiagnosticsInfo {

  private static final Logger LOG = LoggerFactory.getLogger(
      AbfsDiagnosticsInfo.class);

  public static final String FS_AZURE_ENABLE_READAHEAD = "fs.azure.enable.readahead";
  public static final String FS_AZURE_ENABLE_READAHEAD_V2 = "fs.azure.enable.readahead.v2";

  public static final String FS_AZURE_READAHEADQUEUE_DEPTH = "fs.azure.readaheadqueue.depth";


  /**
   * Buffer directory path for uploading AbfsOutputStream data blocks.
   * Value: {@value}
   */
  public static final String FS_AZURE_BLOCK_UPLOAD_BUFFER_DIR =
      "fs.azure.buffer.dir";

  /**
   * What data block buffer to use.
   * <br>
   * Options include: "disk"(Default), "array", and "bytebuffer".
   * Value: {@value}
   */
  public static final String DATA_BLOCKS_BUFFER =
      "fs.azure.data.blocks.buffer";

  /**
   * Maximum Number of blocks a single output stream can have
   * active (uploading, or queued to the central FileSystem
   * instance's pool of queued operations.
   * This stops a single stream overloading the shared thread pool.
   * {@value}
   */
  public static final String FS_AZURE_BLOCK_UPLOAD_ACTIVE_BLOCKS =
      "fs.azure.block.upload.active.blocks";

  /**
   * Limit of queued block upload operations before writes
   * block for an OutputStream. Value: {@value}
   */
  public static final int BLOCK_UPLOAD_ACTIVE_BLOCKS_DEFAULT = 20;

  public static final String FS_AZURE_INFINITE_LEASE_DIRECTORIES =
      "fs.azure.infinite-lease.directories";

  public static final String FS_AZURE_LEASE_THREADS = "fs.azure.lease.threads";

  public static final String FS_AZURE_ATOMIC_RENAME_KEY = "fs.azure.atomic.rename.key";

  public static final String FS_AZURE_ACCOUNT_KEY = "fs.azure.account.key";

  public static final String ACCESS_TOKEN_PROVIDER =
      "org.apache.hadoop.fs.azurebfs.oauth2.AccessTokenProvider";

  public static final String CREDENTIALS_TOKEN_PROVIDER =
      "org.apache.hadoop.fs.azurebfs.oauth2.ClientCredsTokenProvider";

  public static final String MSI_TOKEN_PROVIDER =
      "org.apache.hadoop.fs.azurebfs.oauth2.MsiTokenProvider";

  public static final String REFRESH_BASED_TOKEN_PROVIDER =
      "org.apache.hadoop.fs.azurebfs.oauth2.RefreshTokenBasedTokenProvider";

  public static final String USER_PASSWORD_TOKEN_PROVIDER =
      "org.apache.hadoop.fs.azurebfs.oauth2.UserPasswordTokenProvider";

  @Override
  public Object[][] getEnvVars() {
    return cat(ENV_VARS, STANDARD_ENV_VARS);
  }

  /**
   * Environment variables set by azure deployments.
   * This is not currently picked up by the abfs client; it is by go and python.
   * {@link <a href="https://github.com/Azure/azure-sdk-for-python/blob/main/sdk/identity/azure-identity/azure/identity/_credentials/workload_identity.py#L70-L72">python lib</a>}
   */
  protected static final Object[][] ENV_VARS = {
      {"AZURE_AUTHORITY_HOST", false},
      {"AZURE_CLIENT_ID", false},
      {"AZURE_FEDERATED_TOKEN_FILE", false},
      {"AZURE_TENANT_ID", false},
      {"", false},
  };

  public static final String FS_AZURE_ACCOUNT_AUTH_TYPE = "fs.azure.account.auth.type";

  public static final String FS_AZURE_ACCOUNT_OAUTH_PROVIDER_TYPE =
      "fs.azure.account.oauth.provider.type";

  public static final String FS_AZURE_ALWAYS_USE_HTTPS = "fs.azure.always.use.https";

  public static final String FS_AZURE_ACCOUNT_KEYPROVIDER = "fs.azure.account.keyprovider";

  public static final String FS_AZURE_IDENTITY_TRANSFORMER_CLASS =
      "fs.azure.identity.transformer.class";

  public static final String FS_AZURE_SAS_TOKEN_PROVIDER_TYPE = "fs.azure.sas.token.provider.type";

  public static final String FS_AZURE_ACCOUNT_OAUTH2_CLIENT_ENDPOINT =
      "fs.azure.account.oauth2.client.endpoint";

  public static final String FS_AZURE_ACCOUNT_OAUTH2_CLIENT_ID =
      "fs.azure.account.oauth2.client.id";

  public static final String FS_AZURE_ACCOUNT_OAUTH2_CLIENT_SECRET =
      "fs.azure.account.oauth2.client.secret";

  public static final String FS_AZURE_ACCOUNT_OAUTH2_MSI_AUTHORITY =
      "fs.azure.account.oauth2.msi.authority";

  public static final String FS_AZURE_ACCOUNT_OAUTH2_MSI_ENDPOINT =
      "fs.azure.account.oauth2.msi.endpoint";

  public static final String FS_AZURE_ACCOUNT_OAUTH2_MSI_TENANT =
      "fs.azure.account.oauth2.msi.tenant";

  public static final String FS_AZURE_ACCOUNT_OAUTH2_REFRESH_TOKEN =
      "fs.azure.account.oauth2.refresh.token";

  public static final String FS_AZURE_ACCOUNT_OAUTH2_REFRESH_TOKEN_ENDPOINT =
      "fs.azure.account.oauth2.refresh.token.endpoint";

  public static final String FS_AZURE_ACCOUNT_OAUTH2_TOKEN_FILE =
      "fs.azure.account.oauth2.token.file";

  public static final String FS_AZURE_ACCOUNT_OAUTH2_USER_NAME =
      "fs.azure.account.oauth2.user.name";

  public static final String FS_AZURE_ACCOUNT_OAUTH2_USER_PASSWORD =
      "fs.azure.account.oauth2.user.password";

  public static final String FS_AZURE_ACCOUNT_HNS_ENABLED = "fs.azure.account.hns.enabled";

  /**
   * Configuration options for the Abfs Client.
   */
  private static final Object[][] OPTIONS = {

      {"abfs.external.authorization.class", false, false},
      {"fs.abfs.impl", false, false},
      {"fs.abfss.impl", false, false},
      {"fs.azure.abfs.endpoint", false, false},
      {"fs.azure.abfs.latency.track", false, false},
      {FS_AZURE_ACCOUNT_AUTH_TYPE, false, false},
      {FS_AZURE_ACCOUNT_HNS_ENABLED, false, false},
      {FS_AZURE_ACCOUNT_KEYPROVIDER, false, false},
      {FS_AZURE_ACCOUNT_OAUTH_PROVIDER_TYPE, false, false},
      {FS_AZURE_ACCOUNT_OAUTH2_CLIENT_ENDPOINT, false, false},
      {FS_AZURE_ACCOUNT_OAUTH2_CLIENT_ID, false, false},
      {FS_AZURE_ACCOUNT_OAUTH2_CLIENT_SECRET, true, true},
      {FS_AZURE_ACCOUNT_OAUTH2_MSI_AUTHORITY, false, false},
      {FS_AZURE_ACCOUNT_OAUTH2_MSI_ENDPOINT, false, false},
      {FS_AZURE_ACCOUNT_OAUTH2_MSI_TENANT, false, false},
      {FS_AZURE_ACCOUNT_OAUTH2_REFRESH_TOKEN, true, true},
      {FS_AZURE_ACCOUNT_OAUTH2_REFRESH_TOKEN_ENDPOINT, true, true},
      {FS_AZURE_ACCOUNT_OAUTH2_TOKEN_FILE, true, true},
      {FS_AZURE_ACCOUNT_OAUTH2_USER_NAME, false, false},
      {FS_AZURE_ACCOUNT_OAUTH2_USER_PASSWORD, true, true},
      {"fs.azure.account.operation.idle.timeout", false, false},
      {"fs.azure.account.throttling.enabled", false, false},
      {"fs.azure.analysis.period", false, false},
      {"fs.azure.shellkeyprovider.script", true, true},
      {FS_AZURE_ALWAYS_USE_HTTPS, false, false},
      {"fs.azure.apache.http.client.idle.connection.ttl", false, false},
      {"fs.azure.apache.http.client.max.cache.connection.size", false, false},
      {"fs.azure.apache.http.client.max.io.exception.retries", false, false},
      {"fs.azure.appendblob.directories", false, false},
      {FS_AZURE_ATOMIC_RENAME_KEY, false, false},
      {"fs.azure.block.location.impersonatedhost", false, false},
      {"fs.azure.block.size", false, false},
      {"fs.azure.buffered.pread.disable", false, false},
      {"fs.azure.client-provided-encryption-key", true, true},
      {"fs.azure.client.correlationid", false, false},
      {"fs.azure.cluster.name", false, false},
      {"fs.azure.cluster.type", false, false},
      {"fs.azure.concurrentRequestCount.in", false, false},
      {"fs.azure.concurrentRequestCount.out", false, false},
      {"fs.azure.createRemoteFileSystemDuringInitialization", false, false},
      {"fs.azure.custom.token.fetch.retry.count", false, false},
      {"fs.azure.delegation.token.provider.type", false, false},
      {DATA_BLOCKS_BUFFER, false, false},
      {FS_AZURE_BLOCK_UPLOAD_BUFFER_DIR, false, false},
      {FS_AZURE_BLOCK_UPLOAD_ACTIVE_BLOCKS, false, false},
      {"fs.azure.disable.outputstream.flush", false, false},
      {"fs.azure.enable.abfslistiterator", false, false},
      {"fs.azure.enable.autothrottling", false, false},
      {"fs.azure.enable.check.acces", false, false},
      {"fs.azure.enable.checksum.validation", true, true},
      {"fs.azure.enable.conditional.create.overwrite", false, false},
      {"fs.azure.enable.delegation.token", false, false},
      {"fs.azure.enable.flush", false, false},
      {"fs.azure.enable.mkdir.overwrite", false, false},
      {FS_AZURE_ENABLE_READAHEAD, false, false},
      {FS_AZURE_ENABLE_READAHEAD_V2, false, false},
      {"fs.azure.enable.rename.resilience", false, false},
      {"fs.azure.encryption.context.provider.type", false, false},
      {"fs.azure.encryption.encoded.client-provided-key", false, true},
      {"fs.azure.encryption.encoded.client-provided-key-sha", true, true},
      {FS_AZURE_IDENTITY_TRANSFORMER_CLASS, false, false},
      {"fs.azure.identity.transformer.domain.name", false, false},
      {"fs.azure.identity.transformer.enable.short.name", false, false},
      {"fs.azure.identity.transformer.local.service.group.mapping.file.path", false, false},
      {"fs.azure.identity.transformer.local.service.principal.mapping.file.path", false, false},
      {"fs.azure.identity.transformer.service.principal.id", false, false},
      {"fs.azure.identity.transformer.service.principal.substitution.list", false, false},
      {"fs.azure.identity.transformer.skip.superuser.replacement", false, false},
      {FS_AZURE_INFINITE_LEASE_DIRECTORIES, false, false},
      {"fs.azure.io.rate.limit", false, false},
      {"fs.azure.io.read.tolerate.concurrent.append", false, false},
      {"fs.azure.io.retry.backoff.interval", false, false},
      {"fs.azure.io.retry.max.backoff.interval", false, false},
      {"fs.azure.io.retry.max.retries", false, false},
      {"fs.azure.io.retry.min.backoff.interval", false, false},
      {FS_AZURE_LEASE_THREADS, false, false},
      {"fs.azure.list.max.results", false, false},
      {"fs.azure.metric.analysis.timeout", false, false},
      {"fs.azure.networking.library", false, false},
      {"fs.azure.oauth.token.fetch.retry.max.backoff.interval", false, false},
      {"fs.azure.oauth.token.fetch.retry.max.retries", false, false},
      {"fs.azure.oauth.token.fetch.retry.min.backoff.interval", false, false},
      {"fs.azure.objectmapper.threadlocal.enabled", false, false},
      {"fs.azure.read.alwaysReadBufferSize", false, false},
      {"fs.azure.read.optimizefooterread", false, false},
      {"fs.azure.networking.library", false, false},
      {"fs.azure.apache.http.client.idle.connection.ttl", false, false},
      {"fs.azure.apache.http.client.max.cache.connection.size", false, false},
      {"fs.azure.apache.http.client.max.io.exception.retries", false, false},
      {"fs.azure.read.readahead.blocksize", false, false},
      {"fs.azure.read.request.size", false, false},
      {"fs.azure.read.smallfilescompletely", false, false},
      {"fs.azure.readahead.range", false, false},
      {FS_AZURE_READAHEADQUEUE_DEPTH, false, false},
      {"fs.azure.rename.raises.exceptions", false, false},
      {FS_AZURE_SAS_TOKEN_PROVIDER_TYPE, false, false},
      {"fs.azure.sas.fixed.token", true, true},
      {"fs.azure.sas.token.renew.period.for.streams", false, false},
      {"fs.azure.secure.mode", false, false},
      {"fs.azure.shellkeyprovider.script", false, false},
      {"fs.azure.skipUserGroupMetadataDuringInitialization", false, false},
      {"fs.azure.ssl.channel.mode", false, false},
      {"fs.azure.tracingheader.format", false, false},
      {"fs.azure.use.upn", false, false},
      {"fs.azure.user.agent.prefix", false, false},
      {"fs.azure.write.enableappendwithflush", false, false},
      {"fs.azure.write.max.concurrent.requests", false, false},
      {"fs.azure.write.max.requests.to.queue", false, false},
      {"fs.azure.write.request.size", false, false},
      {"", false, false},

      /* committer */
      {"mapreduce.outputcommitter.factory.scheme.abfs", false, false},

      {"mapreduce.manifest.committer.cleanup.parallel.delete", false, false},
      {"mapreduce.manifest.committer.cleanup.parallel.delete.base.first", false, false},
      {"mapreduce.manifest.committer.delete.target.files", false, false},
      {"mapreduce.manifest.committer.diagnostics.manifest.directory", false, false},
      {"mapreduce.manifest.committer.io.thread.count", false, false},
      {"mapreduce.manifest.committer.manifest.save.attempts", false, false},
      {"mapreduce.manifest.committer.store.operations.classname", false, false},
      {"mapreduce.manifest.committer.summary.report.directory", false, false},
      {"mapreduce.manifest.committer.validate.output", false, false},
      {"mapreduce.manifest.committer.writer.queue.capacity", false, false},
      {"mapreduce.fileoutputcommitter.cleanup.skipped", false, false},
      {"mapreduce.fileoutputcommitter.marksuccessfuljobs", false, false},
      {"mapreduce.fileoutputcommitter.algorithm.version.v1.experimental.mv.threads", false, false},
      {"mapreduce.fileoutputcommitter.algorithm.version.v1.experimental.parallel.task.commit", false, false},
      {"mapreduce.fileoutputcommitter.algorithm.version.v1.experimental.parallel.rename.recovery", false, false},

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

  public static final String[] OPTIONAL_CLASSNAMES = {
      // SSL stuff
      "org.wildfly.openssl.OpenSSLProvider",
      X509,
      // guavae
      "com.google.common.base.Preconditions",
      "org.apache.hadoop.thirdparty.com.google.common.base.Preconditions",
      // manifest committer
      "org.apache.hadoop.fs.EtagSource",
      "org.apache.hadoop.mapreduce.lib.output.committer.manifest.ManifestCommitter",
      "org.apache.hadoop.mapreduce.lib.output.ResilientCommitByRenameHelper",
      // extra stuff from extension modules
      "org.apache.knox.gateway.cloud.idbroker.abfs.AbfsIDBTokenIdentifier",
  };

  /**
   * Path Capabilities different versions of the store may
   * support.
   */
  public static final String[] optionalCapabilities = {
      ETAGS_AVAILABLE,
      ETAGS_PRESERVED_IN_RENAME,
      FS_ACLS,
      FS_APPEND,
      FS_AZURE_CAPABILITY_READAHEAD_SAFE,
      FS_PERMISSIONS,
      FS_XATTRS
  };

  public AbfsDiagnosticsInfo(final URI fsURI, final Printout output) {
    super(fsURI, output);
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
        Arrays.asList(AbfsDiagnosticsInfo.OPTIONS));
    // dynamically create account-specific keys
    String account = getFsURI().getHost();
    addAccountOption(optionList, FS_AZURE_ACCOUNT_KEY,
        true, true);
    addAccountOption(optionList, FS_AZURE_ACCOUNT_OAUTH_PROVIDER_TYPE, false, false);
    addAccountOption(optionList, FS_AZURE_ACCOUNT_OAUTH2_CLIENT_ENDPOINT, false, false);
    addAccountOption(optionList, FS_AZURE_ACCOUNT_OAUTH2_CLIENT_ID, true, false);
    addAccountOption(optionList, FS_AZURE_ACCOUNT_OAUTH2_CLIENT_SECRET, true, true);
    addAccountOption(optionList, FS_AZURE_ACCOUNT_OAUTH2_MSI_AUTHORITY, true, false);
    addAccountOption(optionList, FS_AZURE_ACCOUNT_OAUTH2_MSI_ENDPOINT, true, false);
    addAccountOption(optionList, FS_AZURE_ACCOUNT_OAUTH2_MSI_TENANT, true, false);
    addAccountOption(optionList, FS_AZURE_ACCOUNT_OAUTH2_USER_NAME, true, true);
    addAccountOption(optionList, FS_AZURE_ACCOUNT_OAUTH2_REFRESH_TOKEN, true, true);
    addAccountOption(optionList, FS_AZURE_ACCOUNT_OAUTH2_TOKEN_FILE, true, false);
    addAccountOption(optionList, FS_AZURE_ACCOUNT_OAUTH2_USER_NAME, true, false);
    addAccountOption(optionList, FS_AZURE_ACCOUNT_OAUTH2_USER_PASSWORD, true, true);

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
      boolean secret,
      boolean sensitive) {
    if (!key.isEmpty()) {
      add(list, abfsContainerKey(key), secret, sensitive);
    }
  }

  /**
   * Given a property key, determine its resolved value.
   * @param key key name
   * @return the property name.
   */
  private String abfsContainerKey(final String key) {
    return key + "." + getFsURI().getHost();
  }

  /**
   * Given a property key, resolve to the filesystem and then look up.
   * @param key key name
   * @return the property value or null
   */
  private String abfsContainerValue(Configuration conf,  final String key) {
    return conf.get(key + "." + getFsURI().getHost());
  }


  @Override
  public String[] getClassnames(final Configuration conf) {
    return classnames;
  }

  @Override
  public String[] getOptionalClassnames(final Configuration conf) {
    return OPTIONAL_CLASSNAMES;
  }

  public String[] getOptionalPathCapabilites() {
    return optionalCapabilities;
  }

  @Override
  public Object[][] getSelectedSystemProperties() {
    return cat(JAVA_NET_SYSPROPS, STANDARD_SYSPROPS);
  }

  @Override
  protected void validateConfig(final Printout printout,
      final Configuration conf,
      final boolean writeOperations)
      throws IOException {
    super.validateConfig(printout, conf, writeOperations);
    warnOnInvalidDomain(printout, ".dfs.core.windows.net",
        "https://docs.microsoft.com/en-us/azure/storage/data-lake-storage/introduction-abfs-uri");

    // look at block buffering
    printout.heading("Output Buffering");
    final String buffering = conf.getTrimmed(DATA_BLOCKS_BUFFER, "disk");
    int activeBlocks = conf.getInt(FS_AZURE_BLOCK_UPLOAD_ACTIVE_BLOCKS,
        BLOCK_UPLOAD_ACTIVE_BLOCKS_DEFAULT);
    printout.println("Written data is buffered to %s with up to %d blocks queued per stream",
        buffering, activeBlocks);
    if ("disk".equals(buffering)) {
      validateBufferDir(printout, conf, FS_AZURE_BLOCK_UPLOAD_BUFFER_DIR, HADOOP_TMP_DIR,
          writeOperations, 0);
    }
    int leaseThreads;
    final String leaseDirs = conf.getTrimmed(FS_AZURE_INFINITE_LEASE_DIRECTORIES, "");
    if (!leaseDirs.isEmpty()) {
      leaseThreads = conf.getInt(FS_AZURE_LEASE_THREADS, 0);
      printout.println(
          "Filesystem has directory leasing for directories %s with lease thread count of %,d",
          leaseDirs, leaseThreads);

      if (leaseThreads == 0) {
        printout.warn("Lease thread count is 0 (set in %s)", FS_AZURE_LEASE_THREADS);
        printout.warn("Leases will not be released");
      } else if (leaseThreads == 1) {
        printout.warn("Lease thread count is 1 (set in %s)", FS_AZURE_LEASE_THREADS);
        printout.warn("Leases release may be slower than desired");
      }
    }
    final String atomicRenames = conf.getTrimmed(FS_AZURE_ATOMIC_RENAME_KEY, "");
    if (!atomicRenames.isEmpty()) {
      printout.println("Atomic rename is enabled for %s", atomicRenames);

    }
    // now print everything fs.abfs.ext, assuming that
    // there are no secrets in it. Don't do that.
    printPrefixedOptions(printout, conf, "fs.abfs.ext.");

    printout.heading("Authentication");

    final URI uri = getFsURI();
    String[] authorityParts;
    try {
      authorityParts = authorityParts(uri);
    } catch (IllegalArgumentException e) {
      printout.error(e.getMessage());
      return;
    }

    final String fileSystemName = authorityParts[0];
    final String accountName = authorityParts[1];
    printout.println("Filesystem name: %s", fileSystemName);
    printout.println("Account: %s", accountName);

    WrappedConfiguration wrapped = new WrappedConfiguration(conf, accountName, printout);
    final PropVal auth = wrapped.get(FS_AZURE_ACCOUNT_AUTH_TYPE, "SharedKey");
    printout.println("Authentication type in %s is %s",
        FS_AZURE_ACCOUNT_AUTH_TYPE, auth.details());
    // look at auth info
    switch (auth.value) {
    case "SharedKey": {

      // shared key auth
      printout.println("Authentication is SharedKey");
      int dotIndex = accountName.indexOf(".");
      if (dotIndex <= 0) {
        printout.error("Account name in %s is not fully qualified", uri);
      } else {
        final Optional<PropVal> keyProvider = wrapped.get(AZURE_KEY_ACCOUNT_KEYPROVIDER);

        if (keyProvider.isPresent()) {
          printout.println("Shared Key resolution keys through class %s",
              keyProvider.get().details());
        } else {
          // using SimpleKeyProvider.java
          printout.println("Resolving secrets in Hadoop configuration class/JCEKS");
          try {
            final Optional<PropVal> password = wrapped.getPasswordString(FS_AZURE_ACCOUNT_KEY);
            if (password.isPresent()) {
              final PropVal val = password.get();
              printout.println("Secret key for authentication: %s",
                  val.sanitized());
            } else {
              getOutput().error("No shared key set in %s or %s",
                  FS_AZURE_ACCOUNT_KEY, abfsContainerKey(FS_AZURE_ACCOUNT_KEY));
            }
          } catch (Exception e) {
            printout.error("Failed to retrieve password configuration option %s: %s",
                FS_AZURE_ACCOUNT_KEY, e.toString());
            LOG.error("error string", e);
          }
        }
      }
    }
    break;
    case "OAuth":
      printout.println("OAuth2 is used for authentication enabled");

      // For the various OAuth options use the config `fs.azure.account
      //.oauth.provider.type`. Following are the implementations supported
      //ClientCredsTokenProvider, UserPasswordTokenProvider, MsiTokenProvider and
      //RefreshTokenBasedTokenProvider.
      final PropVal provider = wrapped.get(FS_AZURE_ACCOUNT_OAUTH_PROVIDER_TYPE)
          .orElse(new PropVal("unset", ""));
      printout.println("Token provider %s", provider);
      List<String> passwordStrings;
      String providerType = provider.value();
      switch (providerType) {
      case CREDENTIALS_TOKEN_PROVIDER:
        passwordStrings = Arrays.asList(
            FS_AZURE_ACCOUNT_OAUTH2_CLIENT_ENDPOINT,
            FS_AZURE_ACCOUNT_OAUTH2_CLIENT_ID,
            FS_AZURE_ACCOUNT_OAUTH2_CLIENT_SECRET);
        break;
      case MSI_TOKEN_PROVIDER:
        passwordStrings = Arrays.asList(
            FS_AZURE_ACCOUNT_OAUTH2_CLIENT_ID,
            FS_AZURE_ACCOUNT_OAUTH2_MSI_ENDPOINT,
            FS_AZURE_ACCOUNT_OAUTH2_MSI_TENANT);
        break;
      case REFRESH_BASED_TOKEN_PROVIDER:
        passwordStrings = Arrays.asList(
            FS_AZURE_ACCOUNT_OAUTH2_CLIENT_ID,
            FS_AZURE_ACCOUNT_OAUTH2_REFRESH_TOKEN_ENDPOINT,
            FS_AZURE_ACCOUNT_OAUTH2_REFRESH_TOKEN);
        break;
      case USER_PASSWORD_TOKEN_PROVIDER:
        passwordStrings = Arrays.asList(
            FS_AZURE_ACCOUNT_OAUTH2_CLIENT_ENDPOINT,
            FS_AZURE_ACCOUNT_OAUTH2_USER_NAME,
            FS_AZURE_ACCOUNT_OAUTH2_USER_PASSWORD
        );
        break;
      default:
        printout.error("Provider type %s is not recognized", provider);
        passwordStrings = Arrays.asList();
      }
      // go through each value and just print whether set or unset
      for (String passwordString : passwordStrings) {
        Optional<PropVal> pv = wrapped.get(passwordString);
        if (pv.isPresent()) {
          printout.println("%s: %s", passwordString, pv.get().sanitized());
        } else {
          printout.warn("Property not defined %s", passwordString);
        }
      }

      break;
    case "SAS":
      printout.println("Authentication is SAS");
      final PropVal tokenProvider = wrapped.get(FS_AZURE_SAS_TOKEN_PROVIDER_TYPE, "");
      printout.println("Token Provider = %s", tokenProvider.details());
      if (tokenProvider.value.isEmpty()) {
        printout.error("No OAuth token provider set in %s", FS_AZURE_SAS_TOKEN_PROVIDER_TYPE);
      }
      break;
    case "Custom":
      printout.println("Custom authentication.l");
      break;
    default:
      printout.warn("Authentication mechanism %s is not recognized: ", auth.value);
      printout.warn("Must be one of: SharedKey, SAS, OAuth, Custom");
    }

    wrapped.printIfDefined("IdentityTransformer",
        FS_AZURE_IDENTITY_TRANSFORMER_CLASS);
  }


  /**
   * Splut a URI into a tuple of (fileSystemName, accountName)
   * @param uri uri
   * @return auth info
   * @throws IllegalArgumentException if invalid
   */
  private String[] authorityParts(URI uri) {
    final String uritext = uri.toString();
    final String authority = uri.getRawAuthority();
    checkArgument(authority != null, "Authority is null in " + uritext);
    checkArgument(authority.contains("@"), "Authority has no @ splitter " + uritext);

    final String[] authorityParts = authority.split("@", 2);

    if (authorityParts.length < 2 || authorityParts[0] != null
        && authorityParts[0].isEmpty()) {
      final String errMsg = String
              .format("'%s' has a malformed authority, expected container name. "
                      + "Authority takes the form "
                      + "abfs" + "://[<container name>@]<account name>",
                  uritext);
      checkArgument(false, errMsg);
    }
    return authorityParts;
  }

  @Override
  public List<URI> listEndpointsToProbe(final Configuration conf)
      throws IOException, URISyntaxException {
    List<URI> uris = new ArrayList<>(2);
    addUriOption(uris, conf, FS_AZURE_ACCOUNT_OAUTH2_REFRESH_TOKEN_ENDPOINT, "",
        "https://login.microsoftonline.com/Common/oauth2/token");
    String store = requireNonNull(getFsURI().getHost());
    uris.add(new URI("https", store, "/", null));
    return uris;

  }

  /**
   * Any preflight checks of the filesystem config/url etc.
   * @param printout output
   * @param path path which will be used
   * @throws IOException failure.
   */
  @Override
  public void preflightFilesystemChecks(final Printout printout,
        final Path path) throws IOException {

    // look at the host of the account
    final String host = getFsURI().getHost();
    try (StoreDurationInfo ignored = new StoreDurationInfo(LOG,
            "Probing for account name being a valid host")) {
      printCanonicalHostname(printout, host);
    } catch (UnknownHostException e) {
      printout.warn("The hostname of the filesystem %s is unknown.", getFsURI());
      printout.warn("This means the account %s does not exist", host);
      throw e;
    }
  }

  @Override
  public void validateFilesystem(final Printout printout,
      final Path path,
      final FileSystem filesystem) throws IOException {
    super.validateFilesystem(printout, path, filesystem);

    try (StoreDurationInfo ignored = new StoreDurationInfo(LOG,
        "probing for container being classic Azure or ADLS Gen 2 Storage")) {
      filesystem.getAclStatus(new Path("/"));
      printout.println("Container %s is an ADLS Gen 2 store",
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

  @Override
  public void validateOutputStream(
      final Printout printout,
      final FileSystem fs,
      final Path file,
      final FSDataOutputStream out) throws IOException {

    final OutputStream wrappedStream = out.getWrappedStream();
    printout.heading("Validating ABFS output stream");
    if (!(wrappedStream instanceof AbfsOutputStream)) {
      printout.warn("output stream %s is not an ABFS stream", wrappedStream);
      return;
    }
    AbfsOutputStream abfsOut = (AbfsOutputStream) wrappedStream;

    printout.println("Writing under %s with output stream %s", file, abfsOut);
    try {
      final Method hasLeaseM = AbfsOutputStream.class.getMethod("hasLease");
      final boolean hasLease = (Boolean)hasLeaseM.invoke(abfsOut);
      printout.println("Stream %s lease on the path",
          hasLease ? "has a" : "has no");
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      LOG.debug("no method AbfsOutputStream.hasLease() on this release");
    }

    // now attempt to open the same path again
    try (FSDataOutputStream out2 = fs.createFile(file).overwrite(true).build()) {
      printout.println("Store permits multiple clients to open the same path for writing");
    } catch (IOException ioe) {
      LOG.info("Store doesn't permit concurrent writes", ioe);
    }

  }



  /**
   * Implementation of {@code AbfsConfiguration} resolution.
   */
  private static final class WrappedConfiguration {
    private final Configuration conf;
    private final String accountName;
    private final Printout printout;

    private WrappedConfiguration(final Configuration conf, final String accountName,
        final Printout printout) {
      this.conf = conf;
      this.accountName = accountName;
      this.printout = printout;
    }

    private String accountConf(String key) {
      return key + "." + accountName;
    }

    /**
     * Get a value, returns tuple if found, where first arg is
     * the value, second is source.
     * @param key key to resolve.
     * @return a value and origin.
     */
    private Optional<PropVal> get(String key) {
      final String full = accountConf(key);
      String s = conf.get(full);
      if (s != null) {
        return Optional.of(new PropVal(s, full));
      }
      s = conf.get(key);
      if (s != null) {
        return Optional.of(new PropVal(s, key));
      }
      return Optional.empty();
    }

    /**
     * Get the value or the default.
     * @param key key to resolve
     * @param defVal default value.
     * @return the value and origin
     */
    private PropVal get(String key, String defVal) {
      return get(key).orElseGet(() -> new PropVal(defVal, "default"));
    }

    /**
     * Retrieve a value and print it if found.
     * @param text text to print
     * @param key key to resolve
     * @return the value resolved.
     */
    private Optional<PropVal> printIfDefined(String text, String key) {
      final Optional<PropVal> val = get(key);
      val.ifPresent(p ->
          printout.println("%s = %s", text, p.details()));
      return val;
    }

    private void print(String key, String defVal) {
      printout.println("%s = %s",
          key,
          get(key).orElse(new PropVal(defVal, "default")));
    }

    public Optional<PropVal> getPasswordString(String key) throws IOException {
      final String full = accountConf(key);
      char[] passchars = conf.getPassword(full);
      if (passchars != null) {
        return Optional.of(new PropVal(new String(passchars), full));
      }
      passchars = conf.getPassword(key);
      if (passchars != null) {
        return Optional.of(new PropVal(new String(passchars), key));

      }
      return Optional.empty();
    }

  }

  /**
   * string tuple.
   */
  private static final class PropVal {

    private final String value;

    private final String origin;

    private PropVal(final String value, final String origin) {
      this.value = value;
      this.origin = origin;
    }

    private String getOrigin() {
      return origin;
    }

    private String value() {
      return value;
    }

    private String details() {
      return String.format("\"%s\" [%s]", value, origin);
    }

    private String sanitized() {
      return String.format("%s (source %s)", sanitize(value, false), origin);
    }

    @Override
    public String toString() {
      return details();
    }
  }
}


