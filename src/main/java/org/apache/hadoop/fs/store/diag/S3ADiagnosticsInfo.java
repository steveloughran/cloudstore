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
import java.util.Locale;

import com.amazonaws.auth.AWSCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.AWSCredentialProviderList;
import org.apache.hadoop.fs.s3a.S3AFileStatus;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.s3a.S3AUtils;
import org.apache.hadoop.fs.s3native.S3xLoginHelper;
import org.apache.hadoop.util.ExitUtil;

import static org.apache.hadoop.fs.s3a.Constants.BUFFER_DIR;
import static org.apache.hadoop.fs.s3a.Constants.DEFAULT_SECURE_CONNECTIONS;
import static org.apache.hadoop.fs.s3a.Constants.INPUT_FADVISE;
import static org.apache.hadoop.fs.s3a.Constants.INPUT_FADV_NORMAL;
import static org.apache.hadoop.fs.s3a.Constants.INPUT_FADV_RANDOM;
import static org.apache.hadoop.fs.s3a.Constants.INPUT_FADV_SEQUENTIAL;
import static org.apache.hadoop.fs.s3a.Constants.SECURE_CONNECTIONS;
import static org.apache.hadoop.fs.s3a.S3AUtils.getAWSAccessKeys;
import static org.apache.hadoop.fs.store.StoreUtils.cat;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.ABORTABLE_STREAM;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.ETAGS_AVAILABLE;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.FS_CHECKSUMS;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.FS_MULTIPART_UPLOADER;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.FS_S3A_CREATE_HEADER;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.FS_S3A_CREATE_PERFORMANCE;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.S3_SELECT_CAPABILITY;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.STORE_CAPABILITY_DIRECTORY_MARKER_ACTION_DELETE;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.STORE_CAPABILITY_DIRECTORY_MARKER_ACTION_KEEP;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.STORE_CAPABILITY_DIRECTORY_MARKER_AWARE;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.STORE_CAPABILITY_DIRECTORY_MARKER_POLICY_AUTHORITATIVE;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.STORE_CAPABILITY_DIRECTORY_MARKER_POLICY_DELETE;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.STORE_CAPABILITY_DIRECTORY_MARKER_POLICY_KEEP;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.STORE_CAPABILITY_DIRECTORY_MULTIPART_UPLOAD_ENABLED;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.STORE_CAPABILITY_MAGIC_COMMITTER;
import static org.apache.hadoop.fs.store.StoreUtils.sanitize;
import static org.apache.hadoop.fs.store.diag.HBossConstants.CAPABILITY_HBOSS;
import static org.apache.hadoop.fs.store.diag.OptionSets.HTTP_CLIENT_RESOURCES;
import static org.apache.hadoop.fs.store.diag.OptionSets.STANDARD_ENV_VARS;
import static org.apache.hadoop.fs.store.diag.OptionSets.STANDARD_SYSPROPS;
import static org.apache.hadoop.fs.store.diag.OptionSets.X509;

/**
 * Reminder: do not cast to any S3A FS class or reference Constants so
 * that the diagnostics will work even if S3AFileSystem or a dependency
 * is not on the classpath.
 */
public class S3ADiagnosticsInfo extends StoreDiagnosticsInfo {

  private static final Logger LOG = LoggerFactory.getLogger(
      S3ADiagnosticsInfo.class);

  public static final String ASSUMED_ROLE_STS_ENDPOINT
      = "fs.s3a.assumed.role.sts.endpoint";

  //use a custom endpoint?
  public static final String ENDPOINT = "fs.s3a.endpoint";

  public static final String DEFAULT_ENDPOINT = "";

  public static final String REGION = "fs.s3a.endpoint.region";

  //Enable path style access? Overrides default virtual hosting
  public static final String PATH_STYLE_ACCESS = "fs.s3a.path.style.access";

  public static final String DIRECTORY_MARKER_RETENTION =
      "fs.s3a.directory.marker.retention";

  public static final String FS_S3A_THREADS_MAX = "fs.s3a.threads.max";

  public static final String FS_S3A_CONNECTION_MAXIMUM =
      "fs.s3a.connection.maximum";

  public static final String FS_S3A_COMMITTER_THREADS =
      "fs.s3a.committer.threads";

  public static final String FS_S3A_MULTIPART_SIZE = "fs.s3a.multipart.size";

  // minimum size in bytes before we start a multipart uploads or copy
  public static final String MIN_MULTIPART_THRESHOLD =
      "fs.s3a.multipart.threshold";

  /**
   * Option to enable or disable the multipart uploads.
   * Value: {@value}.
   */
  public static final String MULTIPART_UPLOADS_ENABLED = "fs.s3a.multipart.uploads.enabled";


  public static final String FS_S3A_FAST_UPLOAD_BUFFER =
      "fs.s3a.fast.upload.buffer";

  public static final String FS_S3A_FAST_UPLOAD_ACTIVE_BLOCKS =
      "fs.s3a.fast.upload.active.blocks";


  public static final String DELEGATION_TOKEN_BINDING =
      "fs.s3a.delegation.token.binding";

  public static final String AWS_CREDENTIALS_PROVIDER =
      "fs.s3a.aws.credentials.provider";

  public static final String FS_S3A_AUDIT_REJECT_OUT_OF_SPAN_OPERATIONS =
      "fs.s3a.audit.reject.out.of.span.operations";

  public static final String ACCESS_KEY = "fs.s3a.access.key";

  public static final String SECRET_KEY = "fs.s3a.secret.key";

  public static final String SESSION_TOKEN = "fs.s3a.session.token";

  public static final String DISABLE_CACHE = "fs.s3a.impl.disable.cache";

  public static final String BULK_DELETE_PAGE_SIZE = "fs.s3a.bulk.delete.page.size";

  public static final String CONNECTION_SSL_ENABLED = "fs.s3a.connection.ssl.enabled";

  public static final String SERVER_SIDE_ENCRYPTION_ALGORITHM =
      "fs.s3a.server-side-encryption-algorithm";

  public static final String SERVER_SIDE_ENCRYPTION_KEY = "fs.s3a.server-side-encryption.key";

  /**
   * Controls whether the prefetching input stream is enabled.
   */
  public static final String PREFETCH_ENABLED_KEY = "fs.s3a.prefetch.enabled";

  /**
   * The size of a single prefetched block in number of bytes.
   */
  public static final String PREFETCH_BLOCK_SIZE_KEY = "fs.s3a.prefetch.block.size";
  /**
   * Maximum number of blocks prefetched at any given time.
   */
  public static final String PREFETCH_BLOCK_COUNT_KEY = "fs.s3a.prefetch.block.count";

  private static final Object[][] options = {
      /* Core auth */
      {ACCESS_KEY, true, true},
      {SECRET_KEY, true, true},
      {SESSION_TOKEN, true, true},
      {SERVER_SIDE_ENCRYPTION_ALGORITHM, true, false},
      {SERVER_SIDE_ENCRYPTION_KEY, true, true},
      {"fs.s3a.encryption.algorithm", true, false},
      {"fs.s3a.encryption.key", true, true},
      {AWS_CREDENTIALS_PROVIDER, false, false},
      {ENDPOINT, false, false},
      {REGION, false, false},
      {"fs.s3a.signing-algorithm", false, false},

      /* Core Set */
      {"fs.s3a.acl.default", false, false},
      {"fs.s3a.attempts.maximum", false, false},
      {"fs.s3a.authoritative.path", false, false},
      {"fs.s3a.block.size", false, false},
      {"fs.s3a.bucket.probe", false, false},
      {"fs.s3a.buffer.dir", false, false},
      {BULK_DELETE_PAGE_SIZE, false, false},
      /* change detection */
      {"fs.s3a.change.detection.source", false, false},
      {"fs.s3a.change.detection.mode", false, false},
      {"fs.s3a.change.detection.version.required", false, false},

      {CONNECTION_SSL_ENABLED, false, false},
      {FS_S3A_CONNECTION_MAXIMUM, false, false},
      {"fs.s3a.connection.establish.timeout", false, false},
      {"fs.s3a.connection.request.timeout", false, false},
      {"fs.s3a.connection.timeout", false, false},
      {"fs.s3a.create.performance", false, false},
      {"fs.s3a.create.storage.class", false, false},
      {"fs.s3a.custom.signers", false, false},
      {DIRECTORY_MARKER_RETENTION, false, false},
      {"fs.s3a.downgrade.syncable.exceptions", false, false},
      {"fs.s3a.etag.checksum.enabled", false, false},
      {"fs.s3a.executor.capacity", false, false},
      {INPUT_FADVISE, false, false},
      {"fs.s3a.experimental.aws.s3.throttling", false, false},
      {"fs.s3a.experimental.optimized.directory.operations", false, false},
      {"fs.s3a.fast.buffer.size", false, false},
      {FS_S3A_FAST_UPLOAD_BUFFER, false, false},
      {FS_S3A_FAST_UPLOAD_ACTIVE_BLOCKS, false, false},
      {DISABLE_CACHE, false, false},
      {"fs.s3a.list.version", false, false},
      {"fs.s3a.max.total.tasks", false, false},
      {"fs.s3a.multiobjectdelete.enable", false, false},
      {FS_S3A_MULTIPART_SIZE, false, false},
      {MULTIPART_UPLOADS_ENABLED, false, false},
      {"fs.s3a.multipart.purge", false, false},
      {"fs.s3a.multipart.purge.age", false, false},
      {MIN_MULTIPART_THRESHOLD, false, false},
      {"fs.s3a.paging.maximum", false, false},
      {"fs.s3a.prefetch.enabled", false, false},
      {"fs.s3a.prefetch.block.count", false, false},
      {"fs.s3a.prefetch.block.size", false, false},
      {PATH_STYLE_ACCESS, false, false},
      {"fs.s3a.proxy.host", false, false},
      {"fs.s3a.proxy.port", false, false},
      {"fs.s3a.proxy.username", false, false},
      {"fs.s3a.proxy.password", true, true},
      {"fs.s3a.proxy.domain", false, false},
      {"fs.s3a.proxy.workstation", false, false},
      {"fs.s3a.rename.raises.exceptions", false, false},
      {"fs.s3a.readahead.range", false, false},
      {"fs.s3a.retry.limit", false, false},
      {"fs.s3a.retry.interval", false, false},
      {"fs.s3a.retry.throttle.limit", false, false},
      {"fs.s3a.retry.throttle.interval", false, false},
      {"fs.s3a.ssl.channel.mode", false, false},
      {"fs.s3a.s3.client.factory.impl", false, false},
      {FS_S3A_THREADS_MAX, false, false},
      {"fs.s3a.threads.keepalivetime", false, false},
      {"fs.s3a.user.agent.prefix", false, false},
      {"fs.s3a.vectored.read.min.seek.size", false, false},
      {"fs.s3a.vectored.read.max.merged.size", false, false},
      {"fs.s3a.vectored.active.ranged.reads", false, false},


      /* Assumed Role */
      {"fs.s3a.assumed.role.arn", false, false},
      {"fs.s3a.assumed.role.sts.endpoint", false, false},
      {"fs.s3a.assumed.role.sts.endpoint.region", false, false},
      {"fs.s3a.assumed.role.session.name", false, false},
      {"fs.s3a.assumed.role.session.duration", false, false},
      {"fs.s3a.assumed.role.credentials.provider", false, false},
      {"fs.s3a.assumed.role.policy", false, false},

      /* s3guard */
      {"fs.s3a.metadatastore.impl", false, false},
      {"fs.s3a.metadatastore.authoritative", false, false},
      {"fs.s3a.metadatastore.authoritative.dir.ttl", false, false},
      {"fs.s3a.metadatastore.fail.on.write.error", false, false},
      {"fs.s3a.metadatastore.metadata.ttl", false, false},
      {"fs.s3a.s3guard.consistency.retry.interval", false, false},
      {"fs.s3a.s3guard.consistency.retry.limit", false, false},
      {"fs.s3a.s3guard.ddb.table", false, false},
      {"fs.s3a.s3guard.ddb.region", false, false},
      {"fs.s3a.s3guard.ddb.background.sleep", false, false},
      {"fs.s3a.s3guard.ddb.max.retries", false, false},
      {"fs.s3a.s3guard.ddb.table.capacity.read", false, false},
      {"fs.s3a.s3guard.ddb.table.capacity.write", false, false},
      {"fs.s3a.s3guard.ddb.table.create", false, false},
      {"fs.s3a.s3guard.ddb.throttle.retry.interval", false, false},
      {"fs.s3a.s3guard.local.max_records", false, false},
      {"fs.s3a.s3guard.local.ttl", false, false},

      /* committer */
      {"fs.s3a.committer.name", false, false},
      {"fs.s3a.committer.magic.enabled", false, false},
      {"fs.s3a.committer.staging.abort.pending.uploads", false, false},
      {"fs.s3a.committer.staging.conflict-mode", false, false},
      {"fs.s3a.committer.staging.tmp.path", false, false},
      {FS_S3A_COMMITTER_THREADS, false, false},
      {"fs.s3a.committer.staging.unique-filenames", false, false},
      {"mapreduce.outputcommitter.factory.scheme.s3a", false, false},
      {"mapreduce.fileoutputcommitter.marksuccessfuljobs", false, false},


      /* delegation */
      {DELEGATION_TOKEN_BINDING, false, false},
      /* this is from ranger, it should have been in .ext */
      {"fs.s3a.signature.cache.max.size", false, false},

      /* auditing */
      {"fs.s3a.audit.enabled", false, false},
      {"fs.s3a.audit.referrer.enabled", false, false},
      {"fs.s3a.audit.referrer.filter", false, false},
      {FS_S3A_AUDIT_REJECT_OUT_OF_SPAN_OPERATIONS, false, false},
      {"fs.s3a.audit.request.handlers", false, false},
      {"fs.s3a.audit.service.classname", false, false},

      /* access points. */
      {"fs.s3a.accesspoint.arn", false, false},
      {"fs.s3a.accesspoint.required", false, false},

      /* prefetching */
      {PREFETCH_ENABLED_KEY, false, false},
      {PREFETCH_BLOCK_SIZE_KEY, false, false},
      {PREFETCH_BLOCK_COUNT_KEY, false, false},

      /* hboss */
      {HBossConstants.DATA_URI, false, false},
      {HBossConstants.SYNC_IMPL, false, false},
      {HBossConstants.WAIT_INTERVAL_WARN, false, false},
      {HBossConstants.ZK_CONN_STRING, false, false},
      {HBossConstants.ZK_BASE_SLEEP_MS, false, false},
      {HBossConstants.ZK_MAX_RETRIES, false, false},

      {"", false, false},

  };

  protected static final Object[][] ENV_VARS = {
      {"AWS_ACCESS_KEY_ID", true},
      {"AWS_ACCESS_KEY", true},
      {"AWS_SECRET_KEY", true},
      {"AWS_SECRET_ACCESS_KEY", true},
      {"AWS_SESSION_TOKEN", true},
      {"AWS_REGION", false},
      {"AWS_S3_US_EAST_1_REGIONAL_ENDPOINT", false},
      {"AWS_CBOR_DISABLE", false},
      {"AWS_CONFIG_FILE", false},
      {"AWS_CONTAINER_CREDENTIALS_RELATIVE_URI", false},
      {"AWS_CONTAINER_CREDENTIALS_FULL_URI", false},
      {"AWS_CONTAINER_AUTHORIZATION_TOKEN", true},
      {"AWS_EC2_METADATA_DISABLED", false},
      {"AWS_EC2_METADATA_SERVICE_ENDPOINT", false},
      {"AWS_MAX_ATTEMPTS", false},
      {"AWS_METADATA_SERVICE_TIMEOUT", false},
      {"AWS_PROFILE", false},
      {"AWS_RETRY_MODE", false},
      {"", false},
  };

  /**
   * AWS System properties lifted from com.amazonaws.SDKGlobalConfiguration.
   */
  protected static final Object[][] AWS_SYSPROPS = {
      {"aws.accessKeyId", true},
      {"aws.secretKey", true},
      {"aws.sessionToken", true},
      {"aws.region", false},
      {"com.amazonaws.regions.RegionUtils.fileOverride", false},
      {"com.amazonaws.regions.RegionUtils.disableRemote", false},
      {"com.amazonaws.sdk.disableCertChecking", false},
      {"com.amazonaws.sdk.ec2MetadataServiceEndpointOverride", false},
      {"com.amazonaws.sdk.enableDefaultMetrics", false},
      {"com.amazonaws.sdk.enableInRegionOptimizedMode", false},
      {"com.amazonaws.sdk.enableThrottledRetry", false},
      {"com.amazonaws.sdk.enableRuntimeProfiling", false},
      {"com.amazonaws.sdk.disableEc2Metadata", false},
      {"com.amazonaws.sdk.maxAttempts", false},
      {"com.amazonaws.sdk.retryMode", false},
      {"com.amazonaws.sdk.s3.defaultStreamBufferSize", false},
      {"com.amazonaws.services.s3.disableImplicitGlobalClients", false},
      {"com.amazonaws.services.s3.enableV4", false},
      {"com.amazonaws.services.s3.enforceV4", false},
      {"org.wildfly.openssl.path", false},
      {"", false},
      {"", false},
  };

  public static final String S3AFS_CLASSNAME =
      "org.apache.hadoop.fs.s3a.S3AFileSystem";

  /**
   * Mandatory classnames.
   */
  public static final String[] CLASSNAMES = {
      S3AFS_CLASSNAME,

      "java.lang.System",
  };

  /**
   * Optional classes.
   */
  public static final String[] OPTIONAL_CLASSNAMES = {
      // v1 sdk
      "com.amazonaws.services.s3.AmazonS3",
      "com.amazonaws.ClientConfiguration",
      "com.amazonaws.auth.EnvironmentVariableCredentialsProvider",
      // AWS features outwith the aws-s3-sdk JAR and needed for later releases.
      // STS
      "com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient",

      // region support
      "com.amazonaws.regions.AwsRegionProvider",
      "com.amazonaws.regions.AwsEnvVarOverrideRegionProvider",
      "com.amazonaws.regions.AwsSystemPropertyRegionProvider",
      "com.amazonaws.regions.InstanceMetadataRegionProvider",
      "com.amazonaws.internal.TokenBucket",

      // v2 SDK
      "software.amazon.awssdk.auth.credentials.AwsCredentialsProvider",
      "software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider",
      "software.amazon.awssdk.core.exception.SdkException",

      /* Jackson stuff */
      "com.fasterxml.jackson.annotation.JacksonAnnotation",
      "com.fasterxml.jackson.core.JsonParseException",
      "com.fasterxml.jackson.databind.ObjectMapper",

      // Committers
      "org.apache.hadoop.fs.s3a.commit.staging.StagingCommitter",
      "org.apache.hadoop.fs.s3a.commit.magic.MagicS3GuardCommitter",
      "org.apache.hadoop.fs.s3a.Invoker",

      // Assumed Role credential provider (Hadoop 3.1)
      "org.apache.hadoop.fs.s3a.auth.AssumedRoleCredentialProvider",


      // Delegation Tokens
      "org.apache.hadoop.fs.s3a.auth.delegation.S3ADelegationTokens",

      // S3 Select: HADOOP-15229
      "com.amazonaws.services.s3.model.SelectObjectContentRequest",
      "org.apache.hadoop.fs.s3a.select.SelectInputStream",

      // rename extensions
      "org.apache.hadoop.fs.s3a.impl.RenameOperation",
      "org.apache.hadoop.fs.s3a.impl.NetworkBinding",

      // dir markers
      "org.apache.hadoop.fs.s3a.impl.DirectoryPolicy",

      // Auditing
      "org.apache.hadoop.fs.s3a.audit.AuditManagerS3A",
      // including the bit where auditing doesn't leak
      "org.apache.hadoop.util.WeakReferenceMap",

      // etags
      "org.apache.hadoop.fs.EtagSource",

      // access points
      "org.apache.hadoop.fs.s3a.ArnResource",


      // SSL stuff
      "org.wildfly.openssl.OpenSSLProvider",
      X509,

      // prefetch
      "org.apache.hadoop.fs.impl.prefetch.PrefetchingStatistics",
      "org.apache.hadoop.fs.s3a.prefetch.S3ACachingBlockManager",


      // extra stuff from extension modules
      "org.apache.knox.gateway.cloud.idbroker.s3a.IDBDelegationTokenBinding",
      "org.apache.ranger.raz.hook.s3.RazS3ADelegationTokenIdentifier",

      // HBase HBoss
      "org.apache.hadoop.hbase.oss.HBaseObjectStoreSemantics",
      "org.apache.hadoop.fs.store.s3a.DiagnosticsAWSCredentialsProvider",
      "",

  };

  /**
   * Path Capabilities different versions of the store may
   * support.
   */
  public static final String[] OPTIONAL_CAPABILITIES = {
      ETAGS_AVAILABLE,
      FS_CHECKSUMS,
      FS_MULTIPART_UPLOADER,
      ABORTABLE_STREAM,

      // s3 specific
      STORE_CAPABILITY_MAGIC_COMMITTER,
      S3_SELECT_CAPABILITY,
      STORE_CAPABILITY_DIRECTORY_MARKER_AWARE,
      STORE_CAPABILITY_DIRECTORY_MARKER_POLICY_KEEP,
      STORE_CAPABILITY_DIRECTORY_MARKER_POLICY_DELETE,
      STORE_CAPABILITY_DIRECTORY_MARKER_POLICY_AUTHORITATIVE,
      STORE_CAPABILITY_DIRECTORY_MARKER_ACTION_KEEP,
      STORE_CAPABILITY_DIRECTORY_MARKER_ACTION_DELETE,
      STORE_CAPABILITY_DIRECTORY_MULTIPART_UPLOAD_ENABLED,
      FS_S3A_CREATE_PERFORMANCE,
      FS_S3A_CREATE_HEADER,


      // hboss if wrapped by it
      CAPABILITY_HBOSS
  };

  public static final String[] OPTIONAL_RESOURCES = {
      "log4j.properties",
      "com/amazonaws/internal/config/awssdk_config_default.json",
      "awssdk_config_override.json",
      "com/amazonaws/endpointdiscovery/endpoint-discovery.json"
  };

  public static final String KEEP = "keep";

  public static final int SECRET_KEY_EXPECTED_LENGTH = 30;

  public S3ADiagnosticsInfo(final URI fsURI, final Printout output) {
    super(fsURI, output);
  }

  @Override
  public String getName() {
    return "S3A FileSystem Connector";
  }

  @Override
  public String getDescription() {
    return "ASF Filesystem Connector to Amazon S3 Storage and compatible stores";
  }

  @Override
  public String getHomepage() {
    return "https://hadoop.apache.org/docs/current/hadoop-aws/tools/hadoop-aws/index.html";
  }

  @Override
  public Object[][] getFilesystemOptions() {
    return options;
  }

  @Override
  public Object[][] getEnvVars() {
    return cat(ENV_VARS, STANDARD_ENV_VARS);
  }

  @Override
  public Object[][] getSelectedSystemProperties() {
    return cat(AWS_SYSPROPS, STANDARD_SYSPROPS);
  }

  @Override
  public String[] getClassnames(final Configuration conf) {
    return CLASSNAMES;
  }

  @Override
  public String[] getOptionalClassnames(final Configuration conf) {
    return OPTIONAL_CLASSNAMES;
  }

  @Override
  public String[] getRequiredResources(final Configuration conf) {
    return HTTP_CLIENT_RESOURCES;
  }

  @Override
  public String[] getOptionalResources(final Configuration conf) {
    return OPTIONAL_RESOURCES;
  }

  @Override
  public String[] getOptionalPathCapabilites() {
    return OPTIONAL_CAPABILITIES;
  }

  /**
   * Patch the config. Originally used reflection to work on Hadoop 2.7;
   * now just calls S3AUtils.
   * @param conf initial configuration.
   * @return patched config.
   */
  @Override
  public Configuration patchConfigurationToInitalization(
      final Configuration conf) {
    return S3AUtils.propagateBucketOptions(conf, getFsURI().getHost());
  }

  /**
   * Determine the S3 endpoints if set (or default).
   * {@inheritDoc}
   */
  @Override
  public List<URI> listEndpointsToProbe(final Configuration conf)
      throws IOException, URISyntaxException {
    String endpoint = conf.getTrimmed(ENDPOINT, "s3.amazonaws.com");
    String bucketURI;
    String bucket = getFsURI().getHost();
    String fqdn;
    if (bucket.contains(".")) {
      LOG.info("URI appears to be FQDN; using {} as endpoint", bucket);
      fqdn = bucket;
    } else if (endpoint.contains("://")) {
      LOG.info("endpoint is URI {}", endpoint);
      URI uri = new URI(endpoint);
      fqdn = uri.getHost();
    } else {
      fqdn = bucket + "." + endpoint;
    }
    final boolean pathStyleAccess = conf.getBoolean(PATH_STYLE_ACCESS, false);
    boolean secureConnections =
        conf.getBoolean(CONNECTION_SSL_ENABLED, true);
    final boolean endpointIsUrl =
        endpoint.startsWith("http:") || endpoint.startsWith("https:");
    String scheme = secureConnections ? "https" : "http";
    if (pathStyleAccess) {
      getOutput().println("Enabling path style access");
      if (endpointIsUrl) {
        bucketURI = String.format("%s/%s", endpoint, bucket);
      } else {
        bucketURI = String.format("%s://%s/%s", scheme, endpoint, bucket);
      }
    } else {
      if (endpointIsUrl) {
        getOutput().warn(
            "Endpoint %s is a URL; using path style access isn't going to work"
                + "\nset %s to true",
            endpoint, PATH_STYLE_ACCESS);
        throw new ExitUtil.ExitException(-1,
            "inconsisent endoint and path access settings");
      }
      bucketURI = String.format("%s://%s/", scheme, fqdn);
    }
    List<URI> uris = new ArrayList<>(3);
    uris.add(StoreDiag.toURI("Bucket URI", bucketURI));
    // If the STS endpoints is set, work out the URI
    final String sts = conf.get(ASSUMED_ROLE_STS_ENDPOINT, "");
    if (!sts.isEmpty()) {
      uris.add(StoreDiag.toURI(ASSUMED_ROLE_STS_ENDPOINT,
          String.format("https://%s/", sts)));
    }
    return uris;
  }

/*  @Override
  public List<URI> listOptionalEndpointsToProbe(final Configuration conf)
      throws IOException, URISyntaxException {
    List<URI> l = new ArrayList<>(0);
    l.add(new URI("http://169.254.169.254"));
    return l;
  }
  */

  @Override
  protected void validateConfig(final Printout printout,
      final Configuration conf,
      final boolean writeOperations) throws IOException {
    printout.heading("S3A Config validation");

    printout.heading("Output Buffering");
    validateBufferDir(printout, conf, BUFFER_DIR, OptionSets.HADOOP_TMP_DIR,
        writeOperations);

    printout.heading("Encryption");

    String encryption =
        conf.get(SERVER_SIDE_ENCRYPTION_ALGORITHM, "").trim();
    String key = conf.get(SERVER_SIDE_ENCRYPTION_KEY, "").trim();
    boolean hasKey = !key.isEmpty();
    switch (encryption) {

    case "SSE-C":
      if (!hasKey) {
        throw new IllegalStateException(String.format(
            "Encryption method %s requires a key in %s",
            encryption, SERVER_SIDE_ENCRYPTION_KEY));
      }
      break;

    case "SSE-KMS":
      if (!hasKey) {
        printout.warn("SSE-KMS is enabled in %s"
                + " but there is no key set in %s",
            SERVER_SIDE_ENCRYPTION_ALGORITHM,
            SERVER_SIDE_ENCRYPTION_KEY);
        printout.warn("The default key will be used%n"
            + "The current user MUST have permissions to use this");
      } else {
        if (!key.startsWith("arn:aws:kms:")) {
          printout.warn("The SSE-KMS key does not contain a full key"
              + " reference of arn:aws:kms:...");
        }
      }
      break;
    case "":
    case "AES-256":
      // all good
      break;
    default:
      printout.warn("Unknown encryption method: %s", encryption);
    }

    // validate endpoint to make sure it is not a URL.
    printout.heading("Endpoint validation");
    String endpoint = conf.getTrimmed(ENDPOINT, "").toLowerCase(Locale.ROOT);
    String region = conf.getTrimmed(REGION, "").toLowerCase(Locale.ROOT);
    String bucket = getFsURI().getHost();
    boolean privateLink = false;


    boolean secureConnections = conf.getBoolean(SECURE_CONNECTIONS,
        DEFAULT_SECURE_CONNECTIONS);
    boolean pathStyleAccess = conf.getBoolean(PATH_STYLE_ACCESS, false);
    printout.println("%s = \"%s\"", ENDPOINT, endpoint);
    printout.println("%s = \"%s\"", REGION, region);
    printout.println("%s = \"%s\"", PATH_STYLE_ACCESS, pathStyleAccess);
    printout.println("%s = \"%s\"", SECURE_CONNECTIONS, secureConnections);

    boolean isUsingAws = false;
    if (endpoint.isEmpty()) {
      isUsingAws = true;
      printout.println("Central us-east endpoint will be used. "
          + "When not executing within EC2, this is less efficient for buckets in other regions");
      if (bucket.contains(".")) {
        printout.warn("The s3 bucket looks like a domain name but the client is using AWS us-east");
        printout.warn("Set " + ENDPOINT + " to the endpoint,"
            + " tune " + PATH_STYLE_ACCESS
            + " and " + SECURE_CONNECTIONS + " as appropriate");
      }
    } else if (endpoint.endsWith("amazonaws.cn") || endpoint.endsWith("amazonaws.cn/")) {
      isUsingAws = true;
      printout.println("AWS china is in use");
    } else if (endpoint.endsWith(".vpce.amazonaws.com") || endpoint.endsWith(".vpce.amazonaws.com/")) {
      isUsingAws = true;
      privateLink = true;
      printout.println("AWS VPCE is being used for a VPN connection to S3");
      printout.warn("you MUST set %s to the region of this store; it is currently \"%s\"",
          REGION, region);
      printout.println("Note: older hadoop releases do not support this option");
      printout.println("See https://issues.apache.org/jira/browse/HADOOP-17705 for a workaround");
    } else if (!endpoint.contains(".amazonaws.")) {
      isUsingAws = false;
      printout.println(
          "This does not appear to be an amazon endpoint, unless it is a VPN addresss.");
      if (region.isEmpty()) {
        printout.println("If this is a vpn link to aws, the AWS region MUST be set in %s",
            REGION);
      }
      printout.println(
          "For third party endpoints, verify the network port and http protocol"
              + " options are valid.");
      if (pathStyleAccess) {
        printout.println("Path style access is enabled;"
            + " this is normally the correct setting for third party stores.");
      } else {
        printout.warn("Path style access is disabled"
            + " this is not the normal setting for third party stores.");
        printout.warn("It requires DNS to resolve all bucket hostnames");
        if (secureConnections) {
          printout.warn("As https is in use, the certificates must also be wildcarded");
        }
      }
    } else {
      isUsingAws = true;
      printout.println("Endpoint is an AWS S3 store");
      if (region.isEmpty()) {
        printout.println("For reliable signing and performance the AWS region SHOULD be set in %s",
            REGION);
      }

    }

    if (!privateLink && (endpoint.startsWith("https:") || endpoint.startsWith("http:"))) {
      printout.warn("Value of %s looks like a URL: %s", ENDPOINT, endpoint);
      printout.println("It SHOULD normally be a hostname or IP address");
      printout.println("Unless you have a private store with a non-standard port or"
          + " are using AWS S3 PrivateLink");
      if (!pathStyleAccess) {
        printout.warn("You should probably set %s to true", PATH_STYLE_ACCESS);
      }
    }
    if (!privateLink && isUsingAws) {
      printout.println("Important: if you are working with a third party store,");
      printout.println(" this client is still trying to connect to to AWS S3");
      printout.println("Expect failure until %s is set to the private endpoint", ENDPOINT);
    }

    printout.heading("Bucket Name validation");

    printout.println("bucket name = \"%s\"", bucket);

    if (bucket.contains(".")) {
      printout.warn("The bucket name %s contains dot '.'", bucket);
      printout.warn("AWS do not allow this on new buckets as it has problems");
      printout.warn("In the S3A connector, per bucket options no longer work");
      if (!pathStyleAccess && secureConnections) {
        printout.warn("HTTPS certificate validation is probably broken");
      }
      printout.warn("If you are using a fully qualified domain name as the bucket name *this doesn't work");
      int l = 1;
      printout.println("%d. Set " + ENDPOINT + " to the endpoint/S3 host", l++);
      printout.warn("%d. Use the bucket name in the s3a URL", l++);
      if (!pathStyleAccess) {
        printout.warn("%d. Consider setting " + PATH_STYLE_ACCESS + " to true", l++);
      }
      if (secureConnections) {
        printout.println("%d. To disable HTTPS, set %s to true", SECURE_CONNECTIONS, l++);
      }
    }
    String dtbinding = conf.getTrimmed(DELEGATION_TOKEN_BINDING, "");
    String[] auth = conf.getStrings(AWS_CREDENTIALS_PROVIDER, "");

    if (!dtbinding.isEmpty()) {
      printout.heading("Delegation Tokens");

      printout.println("Delegation token binding %s is active", dtbinding);
      printout.println("This will take over authentication from the settings in %s",
          AWS_CREDENTIALS_PROVIDER);
    } else {
      // TODO: analyse default values.
    }

    printout.heading("Analyzing login credentials");
    final S3xLoginHelper.Login accessKeys = getAWSAccessKeys(getFsURI(), conf);
    String accessKey = accessKeys.getUser();
    String secretKey = accessKeys.getPassword();
    String sessionToken = lookupPassword(conf, SESSION_TOKEN, "");
    if (accessKey.isEmpty()) {
      printout.warn("No S3A access key defined; env var or other auth mechanism must be active");
    } else {
      printout.println("access key %s",
          sanitize(accessKey, false));
      // there is a key, validate things approximately
      if (accessKey.length() < 16) {
        printout.warn("Key length (%d) too short for AWS", accessKey.length());
      }
      if (secretKey.isEmpty()) {
        printout.warn("Access key set in %s, but secret key is not set", ACCESS_KEY, SECRET_KEY);
      } else {
        final int secretLen = secretKey.length();
        if (secretLen < SECRET_KEY_EXPECTED_LENGTH) {
          printout.warn(
              "Length of secret key from %s expected to be 30 characters long, but is %d characters",
              SECRET_KEY, secretLen);
        } else {
          printout.println("Secret key length is %d characters", secretLen);
        }
        if (sessionToken.isEmpty()) {
          printout.println("Connector has access key, secret key and no session token");
        } else {
          printout.println("Connector has access key, secret key and session token");
        }
      }

    }

    // now print everything fs.s3a.ext, assuming that
    // there are no secrets in it. Don't do that.
    printPrefixedOptions(printout, conf, "fs.s3a.ext.");
  }

  @Override
  public void validateFilesystem(final Printout printout,
      final Path path,
      final FileSystem filesystem) throws IOException {
    super.validateFilesystem(printout, path, filesystem);

    if (!S3AFS_CLASSNAME.equals(
        filesystem.getClass().getCanonicalName())) {
      // possible if there are things in front, so don't fail.
      printout.warn("The filesystem class %s is not the S3AFileSystem",
          filesystem.getClass());
    } else {
      // it is s3afs, so review the auth chain
      S3AFileSystem s3aFs = (S3AFileSystem) filesystem;
      final AWSCredentialProviderList credentials = s3aFs.shareCredentials("diagnostics");
      final AWSCredentials liveCredentials = credentials.getCredentials();
      final String keyId = liveCredentials.getAWSAccessKeyId();
      printout.heading("Credential review");
      printout.println("AWS Credentials retrieved from class of type %s: %s",
          liveCredentials.getClass().getCanonicalName(),
          liveCredentials);
      printout.println("Access key: %s", sanitize(keyId, false));
      printout.println();
    }

  }


  @Override
  public void validateFile(
      final Printout printout,
      final FileSystem filesystem,
      final Path path,
      final FileStatus status) throws IOException {

    printout.heading("Reviewing bucket versioning");
    if (!(status instanceof S3AFileStatus)) {
      printout.warn("The file status for path %s is not an S3AFileStatus: %s%n",
          path, status);
      return;
    }
    S3AFileStatus st = (S3AFileStatus) status;
    final String versionId = st.getVersionId();
    if (versionId == null) {
      printout.warn("The bucket does not have versioning enabled;"
          + " another backup strategy is needed");
    } else {
      printout.println("The bucket is using versioning.");

      if (!filesystem.hasPathCapability(path,
          STORE_CAPABILITY_DIRECTORY_MARKER_ACTION_KEEP)) {
        printout.warn("To avoid performance penalties, set %s to %s",
            DIRECTORY_MARKER_RETENTION, KEEP);
      } else {
        printout.println("directory marker retention is enabled, so performance will suffer less");
      }
      printout.println("");

    }


  }


  @Override
  protected void performanceHints(
      final Printout printout,
      final Configuration conf) {

    printout.heading("Performance Hints");


    hint(printout, conf.getBoolean(DISABLE_CACHE, false),
        "The option " + DISABLE_CACHE + " is true. "
            + "This may result in the creation of many S3A clients, and use up memory and other resources");

    int threads = 512;
    sizeHint(printout, conf,
        FS_S3A_THREADS_MAX, threads);
    sizeHint(printout, conf,
        FS_S3A_CONNECTION_MAXIMUM, threads * 2);
    sizeHint(printout, conf,
        FS_S3A_COMMITTER_THREADS, 256);

    hint(printout,
        !KEEP.equals(conf.get(DIRECTORY_MARKER_RETENTION, "")),
        "If backwards compatibility is not an issue, set %s to keep",
        DIRECTORY_MARKER_RETENTION);
    hint(printout,
        "org.apache.hadoop.fs.s3a.s3guard.DynamoDBMetadataStore"
            .equals(conf.get("fs.s3a.metadatastore.impl", "")),
        "S3Guard is no longer needed -decommission it");


    reviewReadPolicy(printout, conf);


    // TODO look at output buffer options
  }

  /**
   * Review the read policy.
   */
  private void reviewReadPolicy(final Printout printout,
      final Configuration conf) {
    // look at seek policy and warn of risks
    final String fadvise =
        conf.getTrimmed(INPUT_FADVISE, INPUT_FADV_NORMAL);
    printout.heading("Seek policy: %s", fadvise);
    switch (fadvise) {
    case INPUT_FADV_NORMAL:
    case OptionSets.EnhancedOpenFileOptions.FS_OPTION_OPENFILE_READ_POLICY_ADAPTIVE:
    case OptionSets.EnhancedOpenFileOptions.FS_OPTION_OPENFILE_READ_POLICY_DEFAULT:
      printout.println("Policy starts 'sequential' and switches to 'random' on"
          + " a backwards seek");
      printout.println("This is adaptive and suitable for most workloads");
      break;
    case INPUT_FADV_RANDOM:
    case OptionSets.EnhancedOpenFileOptions.FS_OPTION_OPENFILE_READ_POLICY_VECTOR:
      printout.println(
          "Stream is optimized for random IO, especially ORC and Parquet files");
      printout.println(
          "This policy is very bad for sequential datasets (text, CSV, avro, .gzipped");
      printout.println("And for whole file operations (distcp, fs shell)");
      printout.println("Recommended for ORC, Parquet data");
      break;
    case INPUT_FADV_SEQUENTIAL:
    case OptionSets.EnhancedOpenFileOptions.FS_OPTION_OPENFILE_READ_POLICY_WHOLE_FILE:
      printout.println("This is the initial state of the %s policy",
          INPUT_FADV_NORMAL);
      printout.println(
          "This policy is very bad for ORC and Parquet files which seek around");
      printout.println(
          "As seeks will break the active HTTP request and force a renegotiaton");
      printout.println(
          "Recommend: switch to normal for better handling of random IO");
      break;
    default:
      printout.warn("unknown seek policy");
    }
  }

  /**
   * Get a password from a configuration/configured credential providers.
   * @param conf configuration
   * @param key key to look up
   * @param defVal value to return if there is no password
   * @return a password or the value in {@code defVal}
   * @throws IOException on any problem
   */
  private static String lookupPassword(Configuration conf, String key, String defVal)
      throws IOException {
    try {
      final char[] pass = conf.getPassword(key);
      return pass != null
          ? new String(pass).trim()
          : defVal;
    } catch (IOException ioe) {
      throw new IOException("Cannot find password option " + key, ioe);
    }
  }
}
