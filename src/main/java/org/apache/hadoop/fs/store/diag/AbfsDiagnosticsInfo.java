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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.azurebfs.constants.FileSystemConfigurations;
import org.apache.hadoop.fs.azurebfs.services.AbfsOutputStream;
import org.apache.hadoop.fs.store.StoreDurationInfo;

import static org.apache.hadoop.fs.store.StoreUtils.cat;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.*;
import static org.apache.hadoop.fs.store.diag.OptionSets.HADOOP_TMP_DIR;
import static org.apache.hadoop.fs.store.diag.OptionSets.JAVA_NET_SYSPROPS;
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
   * <br>
   * Default is {@link FileSystemConfigurations#DATA_BLOCKS_BUFFER_DEFAULT}.
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
   * <p>
   * Default is {@link FileSystemConfigurations#BLOCK_UPLOAD_ACTIVE_BLOCKS_DEFAULT}
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

  private static final Object[][] options = {

      {"abfs.external.authorization.class", false, false},
      {"fs.abfs.impl", false, false},
      {"fs.abfss.impl", false, false},
      {"fs.azure.abfs.endpoint", false, false},
      {"fs.azure.abfs.latency.track", false, false},
      {"fs.azure.account.auth.type", false, false},
      {"fs.azure.account.hns.enabled", false, false},
      {"fs.azure.account.keyprovider", false, false},
      {"fs.azure.account.oauth.provider.type", false, false},
      {"fs.azure.account.oauth2.client.endpoint", false, false},
      {"fs.azure.account.oauth2.client.id", false, false},
      {"fs.azure.account.oauth2.client.secret", true, true},
      {"fs.azure.account.oauth2.msi.authority", false, false},
      {"fs.azure.account.oauth2.msi.endpoint", false, false},
      {"fs.azure.account.oauth2.msi.tenant", false, false},
      {"fs.azure.account.oauth2.refresh.token", true, true},
      {"fs.azure.account.oauth2.refresh.token.endpoint", true, true},
      {"fs.azure.account.oauth2.user.name", false, false},
      {"fs.azure.account.oauth2.user.password", true, true},
      {"fs.azure.account.throttling.enabled", false, false},
      {"fs.azure.always.use.https", false, false},
      {"fs.azure.appendblob.directories", false, false},
      {FS_AZURE_ATOMIC_RENAME_KEY, false, false},
      {"fs.azure.block.location.impersonatedhost", false, false},
      {"fs.azure.block.size", false, false},
      {"fs.azure.buffered.pread.disable", false, false},
      {"fs.azure.client-provided-encryption-key", true, true},
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
      {"fs.azure.enable.conditional.create.overwrite", false, false},
      {"fs.azure.enable.delegation.token", false, false},
      {"fs.azure.enable.flush", false, false},
      {"fs.azure.enable.mkdir.overwrite", false, false},
      {FS_AZURE_ENABLE_READAHEAD, false, false},
      {"fs.azure.identity.transformer.class", false, false},
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
      {"fs.azure.oauth.token.fetch.retry.max.backoff.interval", false, false},
      {"fs.azure.oauth.token.fetch.retry.max.retries", false, false},
      {"fs.azure.oauth.token.fetch.retry.min.backoff.interval", false, false},
      {"fs.azure.objectmapper.threadlocal.enabled", false, false},
      {"fs.azure.read.alwaysReadBufferSize", false, false},
      {"fs.azure.read.optimizefooterread", false, false},
      {"fs.azure.read.readahead.blocksize", false, false},
      {"fs.azure.read.request.size", false, false},
      {"fs.azure.read.smallfilescompletely", false, false},
      {"fs.azure.readahead.range", false, false},
      {FS_AZURE_READAHEADQUEUE_DEPTH, false, false},
      {"fs.azure.rename.raises.exceptions", false, false},
      {"fs.azure.sas.token.provider.type", false, false},
      {"fs.azure.sas.token.renew.period.for.streams", false, false},
      {"fs.azure.secure.mode", false, false},
      {"fs.azure.shellkeyprovider.script", false, false},
      {"fs.azure.skipUserGroupMetadataDuringInitialization", false, false},
      {"fs.azure.ssl.channel.mode", false, false},
      {"fs.azure.use.upn", false, false},
      {"fs.azure.user.agent.prefix", false, false},
      {"fs.azure.write.enableappendwithflush", false, false},
      {"fs.azure.write.max.concurrent.requests", false, false},
      {"fs.azure.write.max.requests.to.queue", false, false},
      {"fs.azure.write.request.size", false, false},

      /* committer */
      {"mapreduce.outputcommitter.factory.scheme.abfs", false, false},
      {"mapreduce.fileoutputcommitter.marksuccessfuljobs", false, false},

      {"mapreduce.manifest.committer.cleanup.parallel.delete", false, false},
      {"mapreduce.manifest.committer.delete.target.files", false, false},
      {"mapreduce.manifest.committer.diagnostics.manifest.directory", false, false},
      {"mapreduce.manifest.committer.io.thread.count", false, false},
      {"mapreduce.manifest.committer.store.operations.classname", false, false},
      {"mapreduce.manifest.committer.summary.report.directory", false, false},
      {"mapreduce.manifest.committer.validate.output", false, false},

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

  public static final String[] optionalClassnames = {
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
      FS_PERMISSIONS,
      FS_ACLS,
      FS_APPEND,
      ETAGS_AVAILABLE,
      ETAGS_PRESERVED_IN_RENAME,
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
          writeOperations);
    }
    int leaseThreads;
    final String leaseDirs = conf.getTrimmed(FS_AZURE_INFINITE_LEASE_DIRECTORIES, "");
    if (!leaseDirs.isEmpty()) {
      leaseThreads = conf.getInt(FS_AZURE_LEASE_THREADS, 0);
      printout.println("Filesystem has directory leasing for directories %s with lease thread count of %,d",
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

  }

  @Override
  public List<URI> listEndpointsToProbe(final Configuration conf)
      throws IOException, URISyntaxException {
    List<URI> uris = new ArrayList<>(2);
    addUriOption(uris, conf, "fs.azure.account.oauth2.refresh.token.endpoint", "",
        "https://login.microsoftonline.com/Common/oauth2/token");
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
      printout.warn( "This means the account %s does not exist", host);
      throw e;
    }
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
}
