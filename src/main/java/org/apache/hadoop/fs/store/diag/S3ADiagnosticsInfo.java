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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.s3a.S3ASupport;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.apache.hadoop.fs.store.StoreUtils.cat;
import static org.apache.hadoop.fs.store.StoreUtils.sanitize;
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.*;
import static org.apache.hadoop.fs.store.diag.DiagUtils.isIpV4String;
import static org.apache.hadoop.fs.store.diag.DiagnosticsEntryPoint.toURI;
import static org.apache.hadoop.fs.store.diag.HBossConstants.CAPABILITY_HBOSS;
import static org.apache.hadoop.fs.store.diag.OptionSets.EnhancedOpenFileOptions.FS_OPTION_OPENFILE_READ_POLICY_ADAPTIVE;
import static org.apache.hadoop.fs.store.diag.OptionSets.EnhancedOpenFileOptions.FS_OPTION_OPENFILE_READ_POLICY_DEFAULT;
import static org.apache.hadoop.fs.store.diag.OptionSets.EnhancedOpenFileOptions.FS_OPTION_OPENFILE_READ_POLICY_VECTOR;
import static org.apache.hadoop.fs.store.diag.OptionSets.EnhancedOpenFileOptions.FS_OPTION_OPENFILE_READ_POLICY_WHOLE_FILE;
import static org.apache.hadoop.fs.store.diag.OptionSets.HTTP_CLIENT_RESOURCES;
import static org.apache.hadoop.fs.store.diag.OptionSets.JAVA_NET_SYSPROPS;
import static org.apache.hadoop.fs.store.diag.OptionSets.STANDARD_ENV_VARS;
import static org.apache.hadoop.fs.store.diag.OptionSets.STANDARD_SYSPROPS;
import static org.apache.hadoop.fs.store.diag.OptionSets.X509;

/**
 * Reminder: do not cast to any S3A FS class or reference Constants so
 * that the diagnostics will work even if S3AFileSystem or a dependency
 * is not on the classpath.
 */
@SuppressWarnings("MagicNumber")
public class S3ADiagnosticsInfo extends StoreDiagnosticsInfo {

  private static final Logger LOG = LoggerFactory.getLogger(
      S3ADiagnosticsInfo.class);

  public static final String ASSUMED_ROLE_STS_ENDPOINT
      = "fs.s3a.assumed.role.sts.endpoint";

  //use a custom endpoint?
  public static final String ENDPOINT = "fs.s3a.endpoint";

  public static final String DEFAULT_ENDPOINT = "";

  public static final String REGION = "fs.s3a.endpoint.region";

  /**
   * Is the endpoint a FIPS endpoint?
   * Can be queried as a path capability.
   * Value {@value}.
   */
  public static final String ENDPOINT_FIPS = "fs.s3a.endpoint.fips";

  //Enable path style access? Overrides default virtual hosting
  public static final String PATH_STYLE_ACCESS = "fs.s3a.path.style.access";

  public static final String DIRECTORY_MARKER_RETENTION =
      "fs.s3a.directory.marker.retention";

  public static final String FS_S3A_THREADS_MAX = "fs.s3a.threads.max";
  /**
   * Default value of {@link #FS_S3A_THREADS_MAX}: {@value}.
   */
  public static final int FS_S3A_THREADS_MAX_DEFAULT = 96;

  public static final String FS_S3A_CONNECTION_MAXIMUM =
      "fs.s3a.connection.maximum";

  public static final String FS_S3A_COMMITTER_THREADS =
      "fs.s3a.committer.threads";

  public static final String FS_S3A_MULTIPART_SIZE = "fs.s3a.multipart.size";
  public static final long DEFAULT_MULTIPART_SIZE = 67108864; // 64M

  // minimum size in bytes before we start a multipart uploads or copy
  public static final String MIN_MULTIPART_THRESHOLD =
      "fs.s3a.multipart.threshold";

  /**
   * Option to enable or disable the multipart uploads.
   * Value: {@value}.
   */
  public static final String MULTIPART_UPLOADS_ENABLED = "fs.s3a.multipart.uploads.enabled";
  public static final long DEFAULT_MIN_MULTIPART_THRESHOLD = 134217728; // 128M


  public static final String FS_S3A_FAST_UPLOAD_BUFFER =
      "fs.s3a.fast.upload.buffer";

  public static final String FS_S3A_FAST_UPLOAD_ACTIVE_BLOCKS =
      "fs.s3a.fast.upload.active.blocks";

  public static final String FS_S3A_FAST_UPLOAD_BUFFER_DISK = "disk";

  /**
   * Which input strategy to use for buffering, seeking and similar when
   * reading data.
   * Value: {@value}
   */
  public static final String INPUT_FADVISE =
      "fs.s3a.experimental.input.fadvise";

  /**
   * General input. Some seeks, some reads.
   * Value: {@value}
   */
  public static final String INPUT_FADV_NORMAL = "normal";

  /**
   * Optimized for sequential access.
   * Value: {@value}
   */
  public static final String INPUT_FADV_SEQUENTIAL = "sequential";

  public static final String ASYNC_DRAIN_THRESHOLD = "fs.s3a.input.async.drain.threshold";

  /**
   * Optimized purely for random seek+read/positionedRead operations;
   * The performance of sequential IO may be reduced in exchange for
   * more efficient {@code seek()} operations.
   * Value: {@value}
   */
  public static final String INPUT_FADV_RANDOM = "random";

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

  public static final int BULK_DELETE_PAGE_SIZE_DEFAULT = 250;

  public static final String CONNECTION_SSL_ENABLED = "fs.s3a.connection.ssl.enabled";

  public static final String SERVER_SIDE_ENCRYPTION_ALGORITHM =
      "fs.s3a.server-side-encryption-algorithm";

  public static final String SERVER_SIDE_ENCRYPTION_KEY = "fs.s3a.server-side-encryption.key";

  public static final String ENCRYPTION_ALGORITHM = "fs.s3a.encryption.algorithm";

  public static final String ENCRYPTION_KEY = "fs.s3a.encryption.key";

  public static final String CLIENT_SIDE_ENCRYPTION_REGION = "fs.s3a.encryption.cse.kms.region";

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

  /**
   * Signing.
   */
  public static final String SIGNING_ALGORITHM = "fs.s3a.signing-algorithm";

  /**
   * Signing; allows use of v2 signing on sdk v1,
   * -D fs.s3a.signing-algorithm=S3SignerType
   */
  public static final String SIGNING_V2_ALGORITHM = "S3SignerType";

  public static final String BUCKET_PROBE = "fs.s3a.bucket.probe";

  public static final String MULTIPART_PURGE = "fs.s3a.multipart.purge";

  public static final String SECURE_CONNECTIONS = "fs.s3a.connection.ssl.enabled";

  public static final String BUFFER_DIR = "fs.s3a.buffer.dir";

  /**
   * Acquisition timeout for connections from the pool:
   * {@value}.
   * Default unit is milliseconds for consistency with other options.
   */
  public static final String CONNECTION_ACQUISITION_TIMEOUT =
      "fs.s3a.connection.acquisition.timeout";

  public static final String CONNECTION_ESTABLISH_TIMEOUT = "fs.s3a.connection.establish.timeout";

  /**
   * Should PUT requests await a 100 CONTINUE responses before uploading
   * data?
   * <p>
   * Value: {@value}.
   */
  public static final String CONNECTION_EXPECT_CONTINUE =
      "fs.s3a.connection.expect.continue";

  public static final String CONNECTION_IDLE_TIME =
      "fs.s3a.connection.idle.time";

  public static final String CONNECTION_PART_UPLOAD_TIMEOUT = "fs.s3a.connection.part.upload.timeout";

  public static final String CONNECTION_REQUEST_TIMEOUT = "fs.s3a.connection.request.timeout";

  public static final String CONNECTION_TIMEOUT = "fs.s3a.connection.timeout";

  public static final String CONNECTION_TTL = "fs.s3a.connection.ttl";

  public static final String CONNECTION_KEEPALIVE = "fs.s3a.connection.keepalive";

  /**
   * Should checksums be validated on download?
   * This is slower and not needed on TLS connections.
   * Value: {@value}.
   */
  public static final String CHECKSUM_VALIDATION =
      "fs.s3a.checksum.validation";

  /**
   * AWS credentials providers mapping with key/value pairs.
   * Value = {@value}
   */
  public static final String AWS_CREDENTIALS_PROVIDER_MAPPING =
      "fs.s3a.aws.credentials.provider.mapping";

  /**
   * Is the higher performance copy from local file to S3 enabled?
   * This switch allows for it to be disabled if there are problems.
   * Value: {@value}.
   */
  public static final String OPTIMIZED_COPY_FROM_LOCAL = "fs.s3a.optimized.copy.from.local.enabled";

  /** original constant name. */
  public static final String ORIG_COPY_FROM_LOCAL_ENABLED = "fs.s3a.copy.from.local.enabled";

  public static final String CHANNEL_MODE = "fs.s3a.ssl.channel.mode";

  public static final String MULTIOBJECTDELETE_ENABLE = "fs.s3a.multiobjectdelete.enable";


  /**
   * Input stream type: {@value}.
   */
  public static final String INPUT_STREAM_TYPE = "fs.s3a.input.stream.type";

  public static final String CROSS_REGION_ACCESS_ENABLED = "fs.s3a.cross.region.access.enabled";


  /**
   * Is the create overwrite feature enabled or not?
   * A configuration option and a path status probe.
   * Value {@value}.
   */
  public static final String CONDITIONAL_CREATE_ENABLED =
      "fs.s3a.create.conditional.enabled";

  public static final String OPTION_CREATE_CONDITIONAL_OVERWRITE = "fs.option.create.conditional.overwrite";

  public static final String OPTION_CREATE_CONDITIONAL_OVERWRITE_ETAG =
      "fs.option.create.conditional.overwrite.etag";

  public static final String OPTION_CREATE_IN_CLOSE = "fs.option.create.in.close";

  public static final String FS_S3A_DOWNGRADE_SYNCABLE_EXCEPTIONS =
      "fs.s3a.downgrade.syncable.exceptions";

  public static final String FS_S3A_EXECUTOR_CAPACITY = "fs.s3a.executor.capacity";

  /**
   * Each option is a triple of
   * (key, secure, obfuscate)
   */
  private static final Object[][] S3A_OPTIONS = {
      /* Core auth */
      {ACCESS_KEY, true, true},
      {SECRET_KEY, true, true},
      {SESSION_TOKEN, true, true},
      {SERVER_SIDE_ENCRYPTION_ALGORITHM, true, false},
      {SERVER_SIDE_ENCRYPTION_KEY, true, true},
      {ENCRYPTION_ALGORITHM, true, false},
      {ENCRYPTION_KEY, true, true},
      {CLIENT_SIDE_ENCRYPTION_REGION, false, false},
      {AWS_CREDENTIALS_PROVIDER, false, false},
      {ENDPOINT, false, false},
      {REGION, false, false},
      {ENDPOINT_FIPS, false, false},
      {SIGNING_ALGORITHM, false, false},
      {AWS_CREDENTIALS_PROVIDER_MAPPING, false, false},

      /* Core Set */
      {"fs.s3a.acl.default", false, false},
      {"fs.s3a.attempts.maximum", false, false},
      {"fs.s3a.authoritative.path", false, false},
      {"fs.s3a.aws.credentials.provider.mapping", false, false},
      {"fs.s3a.block.size", false, false},
      {BUCKET_PROBE, false, false},
      {"fs.s3a.buffer.dir", false, false},
      {BULK_DELETE_PAGE_SIZE, false, false},
      /* change detection */
      {"fs.s3a.change.detection.source", false, false},
      {"fs.s3a.change.detection.mode", false, false},
      {"fs.s3a.change.detection.version.required", false, false},

      {CHECKSUM_VALIDATION, false, false},
      {"fs.s3a.classloader.isolation", false, false},
      {CONNECTION_SSL_ENABLED, false, false},
      {CONNECTION_KEEPALIVE, false, false},
      {FS_S3A_CONNECTION_MAXIMUM, false, false},
      {CONNECTION_ACQUISITION_TIMEOUT, false, false},
      {CONNECTION_ESTABLISH_TIMEOUT, false, false},
      {CONNECTION_EXPECT_CONTINUE, false, false},
      {CONNECTION_IDLE_TIME, false, false},
      {CONNECTION_PART_UPLOAD_TIMEOUT, false, false},
      {CONNECTION_REQUEST_TIMEOUT, false, false},
      {CONNECTION_TIMEOUT, false, false},
      {CONNECTION_TTL, false, false},
      {"fs.s3a.create.checksum.algorithm", false, false},
      {CONDITIONAL_CREATE_ENABLED, false, false},
      {"fs.s3a.create.performance", false, false},
      {"fs.s3a.create.storage.class", false, false},
      {CROSS_REGION_ACCESS_ENABLED, false, false},
      {"fs.s3a.custom.signers", false, false},
      {"fs.s3a.http.signer.enabled", false, false},
      {"fs.s3a.http.signer.class", false, false},
      {DIRECTORY_OPERATIONS_PURGE_UPLOADS, false, false},
      {DIRECTORY_MARKER_RETENTION, false, false},
      {FS_S3A_DOWNGRADE_SYNCABLE_EXCEPTIONS, false, false},
      {"fs.s3a.etag.checksum.enabled", false, false},
      {FS_S3A_EXECUTOR_CAPACITY, false, false},
      {INPUT_FADVISE, false, false},
      {ASYNC_DRAIN_THRESHOLD, false, false},
      {"fs.s3a.experimental.aws.s3.throttling", false, false},
      {"fs.s3a.experimental.optimized.directory.operations", false, false},
      {"fs.s3a.fast.buffer.size", false, false},
      {FS_S3A_FAST_UPLOAD_BUFFER, false, false},
      {FS_S3A_FAST_UPLOAD_ACTIVE_BLOCKS, false, false},
      {DISABLE_CACHE, false, false},            // disable filesystem caching
      {INPUT_STREAM_TYPE, false, false},        // stream factory
      {"fs.s3a.list.version", false, false},
      {"fs.s3a.max.total.tasks", false, false},
      {MULTIOBJECTDELETE_ENABLE, false, false},
      {FS_S3A_MULTIPART_SIZE, false, false},
      {MULTIPART_UPLOADS_ENABLED, false, false},
      {MULTIPART_PURGE, false, false},
      {"fs.s3a.multipart.purge.age", false, false},
      {MIN_MULTIPART_THRESHOLD, false, false},
      {OPTIMIZED_COPY_FROM_LOCAL, false, false},
      {ORIG_COPY_FROM_LOCAL_ENABLED, false, false},
      {"fs.s3a.paging.maximum", false, false},
      {"fs.s3a.prefetch.enabled", false, false},
      {"fs.s3a.performance.flags", false, false},
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
      {"fs.s3a.retry.http.5xx.errors", false, false},
      {"fs.s3a.retry.limit", false, false},
      {"fs.s3a.retry.interval", false, false},
      {"fs.s3a.retry.throttle.limit", false, false},
      {"fs.s3a.retry.throttle.interval", false, false},
      {CHANNEL_MODE, false, false},
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
      {"fs.s3a.assumed.role.external.id", false, false},
      {"fs.s3a.assumed.role.policy", false, false},

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
      {"fs.s3a.audit.request.handlers", false, false},       // v1 sdk
      {"fs.s3a.audit.execution.interceptors", false, false}, // v2 sdk
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

      {"test.fs.s3.bucket", false, false}, // print some of the test setups.
      {"", false, false},

  };

  public static final String ENV_AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";

  public static final String ENV_AWS_ACCESS_KEY = "AWS_ACCESS_KEY";

  public static final String ENV_AWS_SECRET_KEY = "AWS_SECRET_KEY";

  public static final String ENV_AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";

  public static final String ENV_AWS_SESSION_TOKEN = "AWS_SESSION_TOKEN";

  public static final String ENV_AWS_REGION = "AWS_REGION";

  protected static final Object[][] ENV_VARS = {
      {ENV_AWS_ACCESS_KEY_ID, true},
      {ENV_AWS_ACCESS_KEY, true},
      {ENV_AWS_SECRET_KEY, true},
      {ENV_AWS_SECRET_ACCESS_KEY, true},
      {ENV_AWS_SESSION_TOKEN, true},
      {ENV_AWS_REGION, false},
      {"AWS_CA_BUNDLE", false},
      {"AWS_CBOR_DISABLE", false},
      {"AWS_CLI_AUTO_PROMPT", true},
      {"AWS_CLI_FILE_ENCODING", true},
      {"AWS_CLI_S3_MV_VALIDATE_SAME_S3_PATHS", true},
      {"", true},
      {"", true},
      {"AWS_CONFIG_FILE", false},
      {"AWS_CONTAINER_AUTHORIZATION_TOKEN", true},
      {"AWS_CONTAINER_CREDENTIALS_FULL_URI", false},
      {"AWS_CONTAINER_CREDENTIALS_RELATIVE_URI", false},
      {"AWS_CSM_CLIENT_ID", false},
      {"AWS_CSM_HOST", false},
      {"AWS_CSM_PORT", false},
      {"AWS_DATA_PATH", false},
      {"AWS_DEFAULT_OUTPUT", false},
      {"AWS_DEFAULT_REGION", false},
      {"AWS_DEFAULTS_MODE", false},
      {"AWS_EC2_METADATA_DISABLED", false},
      {"AWS_EC2_METADATA_SERVICE_ENDPOINT", false},
      {"AWS_ENDPOINT_URL", false},
      {"AWS_ENDPOINT_URL_S3", false},
      {"AWS_IGNORE_CONFIGURED_ENDPOINT_URLS", false},
      {"AWS_JAVA_V1_DISABLE_DEPRECATION_ANNOUNCEMENT", false},
      {"AWS_JAVA_V1_PRINT_LOCATION", false},
      {"AWS_MAX_ATTEMPTS", false},
      {"AWS_METADATA_SERVICE_TIMEOUT", false},
      {"AWS_PROFILE", false},
      {"AWS_REQUEST_CHECKSUM_CALCULATION", false},
      {"AWS_RESPONSE_CHECKSUM_VALIDATION", false},
      {"AWS_RETRY_MODE", false},
      {"AWS_ROLE_ARN", false},
      {"AWS_ROLE_SESSION_NAME", false},
      {"AWS_SDK_UA_APP_ID", false},
      {"AWS_S3_US_EAST_1_REGIONAL_ENDPOINT", false},
      {"AWS_SHARED_CREDENTIALS_FILE", false},   // was AWS_CONFIG_FILE
      {"AWS_SIGV4A_SIGNING_REGION_SET", false},
      {"AWS_USE_DUALSTACK_ENDPOINT", false},
      {"AWS_USE_FIPS_ENDPOINT", false},
      {"AWS_WEB_IDENTITY_TOKEN_FILE", false},
      {"", false},
  };

  /**
   * If any of these are set then the config is a mess as
   * they make configs inconsistent, don't get propagated
   * and generally create the "hdfs fs -ls works but not distcp"
   * problems.
   */
  private static final String[][] ENV_VARS_WE_HATE = {
      {ENV_AWS_ACCESS_KEY_ID, ACCESS_KEY, "S3 access key"},
      {ENV_AWS_ACCESS_KEY, ACCESS_KEY, "S3 access key"},
      {ENV_AWS_SECRET_KEY, SECRET_KEY, "S3 secret key"},
      {ENV_AWS_SECRET_ACCESS_KEY, SECRET_KEY, "S3 secret key"},
      {ENV_AWS_SESSION_TOKEN, SESSION_TOKEN, "S3 session token"},
      {ENV_AWS_REGION, REGION, "S3 endpoint region"},
  };

  /**
   * AWS System properties lifted from com.amazonaws.SDKGlobalConfiguration.
   */
  protected static final Object[][] AWS_SYSPROPS = {
      {"aws.accessKeyId", true},
      {"aws.secretKey", true},
      {"aws.sessionToken", true},

      // aws v2 sdk sysprops from software.amazon.awssdk.core.SdkSystemSetting
      {"aws.binaryIonEnabled", false},
      {"aws.cborEnabled", false},
      {"aws.configFile", false},
      {"aws.containerAuthorizationToken", true},
      {"aws.containerCredentialsFullUri", false},
      {"aws.containerCredentialsPath", false},
      {"aws.containerServiceEndpoint", false},
      {"aws.defaultsMode", false},
      {"aws.disableEc2Metadata", false},
      {"aws.disableRequestCompression", false},
      {"aws.disableRequestCompression", false},
      {"aws.disableS3ExpressAuth", false},
      {"aws.ec2MetadataServiceEndpoint", false},
      {"aws.ec2MetadataServiceEndpointMode", false},
      {"aws.endpointDiscoveryEnabled", false},
      {"aws.executionEnvironment", false},
      {"aws.maxAttempts", false},
      {"aws.profile", false},
      {"aws.region", false},
      {"aws.requestChecksumCalculation", false},
      {"aws.responseChecksumValidation", false},
      {"aws.requestMinCompressionSizeBytes", false},
      {"aws.retryMode", false},
      {"aws.roleArn", false},
      {"aws.roleSessionName", false},
      {"aws.s3UseUsEast1RegionalEndpoint", false},
      {"aws.secretAccessKey", true},
      {"aws.sharedCredentialsFile", false},
      {"aws.useDualstackEndpoint", false},
      {"aws.useFipsEndpoint", false},
      {"aws.webIdentityTokenFile", false},

      // v1 options
      {"aws.java.v1.printLocation", false},
      {"aws.java.v1.disableDeprecationAnnouncement", false},
      {"com.amazonaws.regions.RegionUtils.disableRemote", false},
      {"com.amazonaws.regions.RegionUtils.fileOverride", false},
      {"com.amazonaws.sdk.disableCertChecking", false},
      {"com.amazonaws.sdk.disableEc2Metadata", false},
      {"com.amazonaws.sdk.ec2MetadataServiceEndpointOverride", false},
      {"com.amazonaws.sdk.enableDefaultMetrics", false},
      {"com.amazonaws.sdk.enableInRegionOptimizedMode", false},
      {"com.amazonaws.sdk.enableRuntimeProfiling", false},
      {"com.amazonaws.sdk.enableThrottledRetry", false},
      {"com.amazonaws.sdk.maxAttempts", false},
      {"com.amazonaws.sdk.retryMode", false},
      {"com.amazonaws.sdk.s3.defaultStreamBufferSize", false},
      {"com.amazonaws.services.s3.disableGetObjectMD5Validation", false},
      {"com.amazonaws.services.s3.disableImplicitGlobalClients", false},
      {"com.amazonaws.services.s3.disablePutObjectMD5Validation", false},
      {"com.amazonaws.services.s3.enableV4", false},
      {"com.amazonaws.services.s3.enforceV4", false},

      {"org.wildfly.openssl.path", false},
      {"org.wildfly.openssl.libwfssl.path", false},

      // v2 options
      {"software.amazon.awssdk.http.service.impl", false},
      {"software.amazon.awssdk.http.async.service.impl", false},
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


      /* Jackson stuff */
      "com.fasterxml.jackson.annotation.JacksonAnnotation",
      "com.fasterxml.jackson.core.JsonParseException",
      "com.fasterxml.jackson.databind.ObjectMapper",

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

      // s3a store and bulk delete
      "org.apache.hadoop.fs.s3a.S3AStore",

      // HADOOP-19131. Assist reflection IO with WrappedOperations class (#6686)
      "org.apache.hadoop.io.wrappedio.WrappedIO",
      "org.apache.hadoop.io.wrappedio.WrappedStatistics",

      // on demand creation of transfer manager for better
      // performance
      "org.apache.hadoop.util.functional.LazyAutoCloseableReference",

      // recovery from multipart uploads
      "org.apache.hadoop.fs.s3a.impl.UploadContentProviders",

      // extra stuff from extension modules
      "org.apache.knox.gateway.cloud.idbroker.s3a.IDBDelegationTokenBinding",
      "org.apache.ranger.raz.hook.s3.RazS3ADelegationTokenIdentifier",
      "org.apache.ranger.raz.hook.s3.RazAnonymousAWSCredentialsProvider",


      // v2 SDK
      "software.amazon.awssdk.auth.credentials.AwsCredentialsProvider",
      "software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider",
      "software.amazon.awssdk.core.exception.SdkException",
      "software.amazon.awssdk.crt.s3.S3MetaRequest",
      "software.amazon.eventstream.MessageDecoder",
      "software.amazon.awssdk.transfer.s3.progress.TransferListener",
      "software.amazon.awssdk.services.s3.s3express.S3ExpressConfiguration",  // s3 express
      "software.amazon.awssdk.core.checksums.RequestChecksumCalculation",
      "",

      // HADOOP-19330. Stream Leak reporting.
      "org.apache.hadoop.fs.impl.LeakReporter",

      // CSE encryption
      "org.apache.hadoop.fs.s3a.impl.CSEMaterials",

      // stream factory
      "org.apache.hadoop.fs.s3a.impl.streams.ClassicObjectInputStreamFactory",
      // and analytics stream to match
      "software.amazon.s3.analyticsaccelerator.S3SeekableInputStreamFactory",

      // see if this is coming from the "unshaded" sdk or elsewhere
      "org/reactivestreams/Processor"

  };

  public static final String CLASSIC_STREAM = "classic";

  public static final String PREFETCHING_STREAM = "prefetching";

  public static final String ANALYTICS_STREAM = "analytics";

  public static final String CUSTOM_STREAM = "custom";

  /**
   * Path Capabilities different versions of the store may
   * support.
   */
  public static final String[] OPTIONAL_CAPABILITIES = {
      ABORTABLE_STREAM,
      DIRECTORY_LISTING_INCONSISTENT,
      ETAGS_AVAILABLE,
      FS_CHECKSUMS,
      FS_MULTIPART_UPLOADER,
      OPTION_CREATE_CONDITIONAL_OVERWRITE,
      OPTION_CREATE_CONDITIONAL_OVERWRITE_ETAG,
      OPTION_CREATE_IN_CLOSE,
      STREAM_LEAKS,

      // s3 specific
      STORE_CAPABILITY_AWS_V2,

      STORE_CAPABILITY_DIRECTORY_MARKER_AWARE,
      STORE_CAPABILITY_DIRECTORY_MARKER_POLICY_KEEP,
      STORE_CAPABILITY_DIRECTORY_MARKER_POLICY_DELETE,
      STORE_CAPABILITY_DIRECTORY_MARKER_POLICY_AUTHORITATIVE,
      STORE_CAPABILITY_DIRECTORY_MARKER_ACTION_KEEP,
      STORE_CAPABILITY_DIRECTORY_MARKER_ACTION_DELETE,
      STORE_CAPABILITY_MULTIPART_UPLOAD_ENABLED,
      STORE_CAPABILITY_MAGIC_COMMITTER,
      STORE_CAPABILITY_S3_EXPRESS_STORAGE,

      FS_S3A_CREATE_PERFORMANCE,
      FS_S3A_CREATE_PERFORMANCE + ".enabled",
      CONDITIONAL_CREATE_ENABLED,
      FS_S3A_CREATE_HEADER,
      DIRECTORY_OPERATIONS_PURGE_UPLOADS,
      ENDPOINT_FIPS,

      INPUT_STREAM_TYPE + "." + CLASSIC_STREAM,
      INPUT_STREAM_TYPE + "." + PREFETCHING_STREAM,
      INPUT_STREAM_TYPE + "." + ANALYTICS_STREAM,

      OPTIMIZED_COPY_FROM_LOCAL,

      // hboss if wrapped by it
      CAPABILITY_HBOSS
  };

  public static final String[] OPTIONAL_RESOURCES = {
      "log4j.properties",
      "com/amazonaws/internal/config/awssdk_config_default.json",
      "awssdk_config_override.json",
      "com/amazonaws/endpointdiscovery/endpoint-discovery.json",
      "software/amazon/awssdk/global/handlers/execution.interceptors",
      "software/amazon/awssdk/services/s3/execution.interceptors"
  };

  public static final String KEEP = "keep";

  public static final int SECRET_KEY_EXPECTED_LENGTH = 30;

  public static final String DEFAULT_JSSE = "default_jsse";

  public static final String OPEN_SSL = "openssl";

  public static final String DEFAULT = "default";

  public static final String DEFAULT_JSSE_WITH_GCM = "default_jsse_with_gcm";

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
    return S3A_OPTIONS;
  }

  @Override
  public Object[][] getEnvVars() {
    return cat(ENV_VARS, STANDARD_ENV_VARS);
  }

  @Override
  public Object[][] getSelectedSystemProperties() {
    return cat(cat(AWS_SYSPROPS, STANDARD_SYSPROPS),
        JAVA_NET_SYSPROPS);
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
    return S3ASupport.propagateBucketOptions(conf, getFsURI().getHost());
  }

  /**
   * Determine the S3 endpoints if set (or default).
   * {@inheritDoc}
   */
  @Override
  public List<EndpointProbe> listEndpointsToProbe(final Printout printout, final Configuration conf)
      throws IOException, URISyntaxException {
    String endpoint = conf.getTrimmed(ENDPOINT, "s3.amazonaws.com");
    String bucketURI;
    String bucket = getFsURI().getHost();
    String fqdn;
    String origin;
    if (bucket.contains(".")) {
      printout.println("Filesystem appears to be FQDN; using %s as endpoint", bucket);
      fqdn = bucket;
      origin = "filesystem URL";
    } else if (endpoint.contains("://")) {
      printout.println("Endpoint is URI %s", endpoint);
      URI uri = new URI(endpoint);
      fqdn = uri.getHost();
      origin = "Configuration option " + ENDPOINT;
    } else {
      fqdn = bucket + "." + endpoint;
      origin = "virtual hostname prepended to endpoint";
    }
    final boolean pathStyleAccess = conf.getBoolean(PATH_STYLE_ACCESS, false);
    boolean secureConnections =
        conf.getBoolean(CONNECTION_SSL_ENABLED, true);
    final boolean endpointIsUrl =
        endpoint.startsWith("http:") || endpoint.startsWith("https:");
    String scheme = secureConnections ? "https" : "http";
    if (pathStyleAccess) {
      // path style access, so full bucket url is a path under
      // the endpoint.
      printout.println("Enabling path style access");
      if (endpointIsUrl) {
        bucketURI = String.format("%s/%s", endpoint, bucket);
      } else {
        bucketURI = String.format("%s://%s/%s", scheme, endpoint, bucket);
      }
      origin = "S3 store with path style access";
    } else {
      // virtual host access
      if (endpointIsUrl) {
        printout.error("Inconsistent endpoint and path access settings");
        printout.error(
            "Endpoint %s is a URL; using bucket-hostname access isn't going to work"
                + "\nset %s to true",
            endpoint, PATH_STYLE_ACCESS);
        // stop with probes
        return EMPTY_ENDPOINTS;
      }
      bucketURI = String.format("%s://%s/", scheme, fqdn);
    }
    List<EndpointProbe> uris = new ArrayList<>(3);
    uris.add(new EndpointProbe(bucketURI, "S3 store for bucket", origin, false));

    // If the STS endpoints is set, work out the URI
    final String sts = conf.get(ASSUMED_ROLE_STS_ENDPOINT, "");
    if (!sts.isEmpty()) {
      uris.add(new EndpointProbe(String.format("https://%s/", sts),
          "Endpoint of STS Service",
          ASSUMED_ROLE_STS_ENDPOINT,
          true));
    }
    return uris;
  }

  public List<EndpointProbe> listOptionalEndpointsToProbe(final Printout printout, final Configuration conf)
      throws IOException {

    List<EndpointProbe> uris = new ArrayList<>(0);

    if (conf.getTrimmed(ENDPOINT, "").isEmpty()
        && conf.getTrimmed(REGION, "").isEmpty()
        && conf.getBoolean(CROSS_REGION_ACCESS_ENABLED, true)) {

      // the region is not known, so will hit us central
      // add that
      uris.add(new EndpointProbe("https://us-east-1.s3.amazonaws.com/",
          "S3 cross resolution endpoint",
          "Needed as endpoint and origin are unset and "
              + CROSS_REGION_ACCESS_ENABLED + " is not false",
          true));
    }

    return uris;
  }

  private boolean warnIfOptionAlsoSetInEnvVar(
      final Printout printout,
      final Configuration conf,
      final String purpose,
      final String option,
      final String envVar) {
    String env = System.getenv(envVar);
    final boolean hasEnv = env != null && !env.isEmpty();
    final boolean hasConf = conf.get(option) != null;
    if (hasEnv) {
      printout.warn("%s is set in the environment variable %s",
          purpose, envVar, env, option, env);
      if (hasConf) {
        printout.warn("%s is also set in the configuration option, which can cause confusion",
            option);
        printout.println("*Recommend*: unset the environment variable %s", envVar);
      } else {
        printout.warn("This environment variable will not be passed into launched applications");
        printout.println(
            "*Recommend*: unset the environment variable %s; set the option \"%s\" instead",
            envVar, option);
      }
      printout.println();
    }
    return hasEnv;
  }

  @Override
  protected void validateConfig(final Printout printout,
      final Configuration conf,
      final boolean writeOperations) throws IOException {
    printout.heading("S3A Configuration validation");

    // check for any of the env vars we hate
    for (String[] entry : ENV_VARS_WE_HATE) {
      warnIfOptionAlsoSetInEnvVar(printout, conf,
          entry[2], entry[1], entry[0]);
    }

    printout.heading("Output Buffering and performance ");
    String buffer = conf.getTrimmed(FS_S3A_FAST_UPLOAD_BUFFER, FS_S3A_FAST_UPLOAD_BUFFER_DISK);
    final int activeBlocks = conf.getInt(FS_S3A_FAST_UPLOAD_ACTIVE_BLOCKS, 4);
    long multiPartThreshold = conf.getLongBytes(
        MIN_MULTIPART_THRESHOLD, DEFAULT_MIN_MULTIPART_THRESHOLD);
    long partSize = conf.getLongBytes(FS_S3A_MULTIPART_SIZE, DEFAULT_MULTIPART_SIZE);

    printout.println("%s = %s", FS_S3A_FAST_UPLOAD_BUFFER, buffer);
    if (FS_S3A_FAST_UPLOAD_BUFFER_DISK.equals(buffer)) {
      printout.println("File Output is buffered to disk.");

      printout.println("The maximum file size of a single buffered block is %,3d bytes",
          multiPartThreshold);
      printout.println("Note that many blocks may be queued for upload");

    } else {
      printout.println("Output is buffered to %s memory", buffer);
      // max memory use; first multipart plus blocks plus one being created
      long totalMemory = activeBlocks * partSize + multiPartThreshold;
      printout.println("Memory consumption of each write stream:");
      printout.println("    %d blocks of %,d bytes, plus an initial threshold block of %,d bytes",
          activeBlocks, partSize, multiPartThreshold);
      printout.println("    Maximum block memory consumption is %,d bytes", totalMemory);
    }

    validateBufferDir(printout, conf, BUFFER_DIR, OptionSets.HADOOP_TMP_DIR,
        writeOperations, 0);

    if (!conf.getBoolean(FS_S3A_DOWNGRADE_SYNCABLE_EXCEPTIONS, true)) {
      printout.warn("Output streams reject calls to hsync/hflush");
      printout.println("This is not recommended in production systems as it may overreact");
      printout.println("Recommend setting " + FS_S3A_DOWNGRADE_SYNCABLE_EXCEPTIONS + " to true");
    }

    printout.heading("Encryption");

    String encryption =
        conf.getTrimmed(SERVER_SIDE_ENCRYPTION_ALGORITHM, "");
    String key = conf.getTrimmed(SERVER_SIDE_ENCRYPTION_KEY, "");
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
    String signing = conf.getTrimmed(SIGNING_ALGORITHM, "");
    String bucket = getFsURI().getHost();
    boolean privateLink = false;
    boolean isIpv4;

    // using https.
    boolean sslConnection = conf.getBoolean(SECURE_CONNECTIONS, true);
    boolean pathStyleAccess = conf.getBoolean(PATH_STYLE_ACCESS, false);
    boolean crossRegionAccess = conf.getBoolean(CROSS_REGION_ACCESS_ENABLED, true);
    String channelMode = conf.getTrimmed(CHANNEL_MODE, DEFAULT_JSSE).toLowerCase(Locale.ROOT);
    printout.println("%s = \"%s\"", ENDPOINT, endpoint);
    printout.println("%s = \"%s\"", REGION, region);
    printout.println("%s = \"%s\"", CROSS_REGION_ACCESS_ENABLED, crossRegionAccess);
    printout.println("%s = \"%s\"", PATH_STYLE_ACCESS, pathStyleAccess);
    printout.println("%s = \"%s\"", SIGNING_ALGORITHM, signing);
    printout.println("%s = \"%s\"", SECURE_CONNECTIONS, sslConnection);
    printout.println("%s = \"%s\"", CHANNEL_MODE, channelMode);
    printout.println();

    if (sslConnection) {
      printout.heading("SSL implementation from %s", CHANNEL_MODE);

      printout.println("This option controls whether the JVM or OpenSSL is used for the TLS channel");
      printout.println("OpenSSL is faster, but requires the wildfly library and OpenSSL installed");
      printout.println("See: https://github.com/wildfly-security/wildfly-openssl/blob/main/README.md#installing-the-native-library");

      printout.println("It is also somewhat brittle;\n"
          + "If HTTPS problems are encountered, try switching to %s",
          DEFAULT_JSSE);

      printout.println("%nSome third party stores and/or proxies require the GCM ciphers."
          + "%nThese are disabled for performance reasons."
          + "%nIf TLS negotiation errors surface, try using %s", DEFAULT_JSSE_WITH_GCM);

      printout.println("%nSSL Channel Mode set from %s is %s:",
          CHANNEL_MODE, channelMode);
      // advise on channel mode
      switch (channelMode) {
      case DEFAULT:
        printout.println("This attempts to use OpenSSL, falling back to the JVM");
        break;
      case DEFAULT_JSSE:
        printout.println("This uses the JVM only, with GCM ciphers disabled");
        break;
      case DEFAULT_JSSE_WITH_GCM:
        printout.println("This uses the JVM only, with GCM enabled");
        break;
      case OPEN_SSL:
        printout.println("This uses OpenSSL only. This must be available.");
        break;
      default:
        printout.println("unknown");
      }
    }

    printout.heading("Endpoint");

    boolean isUsingAws;
    boolean isUsingV2Signing = SIGNING_V2_ALGORITHM.equals(signing);

    if (endpoint.isEmpty()) {
      isUsingAws = true;
      if (region.isEmpty()) {
        printout.println(
              "Central/us-east endpoint will be used first.\n"
            + "  -This is inefficient for buckets in other regions.\n"
            + "  -If network rules prevent access to the central endpoint,\n"
            + "   operations may fail");
        if (!crossRegionAccess) {
          printout.println(
                    "Cross-region access is disabled\n"
                  + " -redirection to other regions may fail");
        }
      } else {
        printout.println("AWS Endpoint is not declared, but the region is defined.\n"
            + "  -the S3 client will use the region-specific endpoint");
      }
      if (bucket.contains(".")) {
        printout.warn("The S3 bucket looks like a domain name -but the client is using AWS us-east-1");
        printout.warn("Set " + ENDPOINT + " to the endpoint,"
            + " tune " + PATH_STYLE_ACCESS
            + " and " + SECURE_CONNECTIONS + " as appropriate");
      }
    } else if (endpoint.endsWith("amazonaws.cn") || endpoint.endsWith("amazonaws.cn/")) {
      isUsingAws = true;
      printout.println("AWS china is in use");
    } else if (endpoint.endsWith(".vpce.amazonaws.com") || endpoint.endsWith(
        ".vpce.amazonaws.com/")) {
      isUsingAws = true;
      privateLink = true;
      printout.println("AWS PrivateLink is being used for a VPN connection to S3");
      printout.warn("You MUST set %s to the region of this store; it is currently \"%s\"",
          REGION, region);
      printout.println(
          "Note: Hadoop releases without CDPD-26441/HADOOP-17705 do not support this option");
      printout.println("See https://issues.apache.org/jira/browse/HADOOP-17705 for a workaround");
      printout.println("See also:");
      printout.println(
          "\tHADOOP-17771. S3AFS creation fails: Unable to find a region via the region provider chain.");
      if (!endpoint.startsWith("https://bucket.") && !endpoint.startsWith("bucket.")) {
        printout.warn("The endpoint %s hostname does not start with \"bucket.\"", endpoint);
        printout.warn("This is not a valid endpoint for PrivateLink");
      }
    } else if (!endpoint.contains(".amazonaws.")) {
      isUsingAws = false;
      printout.println(
          "This does not appear to be an amazon endpoint, unless it is a VPN address.");
      isIpv4 = isIpV4String(endpoint);

      if (region.isEmpty()) {
        printout.println("If this is a VPN link to AWS, the AWS region MUST be set in %s",
            REGION);
      }
      printout.println("For third party endpoints, verify the network port"
          + " and http protocol options are valid.");

      printout.warn(
          "If you are trying to connect to a bucket in AWS, this configuration is unlikely to work");
      if (isIpv4) {
        printout.println("endpoint appears to be an IPv4 network address");
        if (sslConnection) {
          printout.warn("HTTPS and dotted network addresses rarely work");
        }
      }
      if (pathStyleAccess) {
        boolean showHowToChange;
        printout.println("- Path style access is enabled;"
            + " this is normally the correct setting for third party stores.");
        if (isUsingAws && !privateLink) {
          printout.warn(
              "-This is not the recommended setting for AWS S3 except through PrivateLink");
          showHowToChange = true;
        } else {
          showHowToChange = false;
        }
        if (showHowToChange) {
          printout.warn("- To disable path style access, set %s to false", PATH_STYLE_ACCESS);
        }


      } else {
        printout.warn("Path style access is disabled"
            + " this is not the normal setting for third party stores.");
        printout.println("It requires DNS to resolve all bucket hostnames");
        if (isIpv4) {
          printout.warn("As the endpoint is an IP address, constructed hostnames unlikely to work");
        }
        if (sslConnection) {
          printout.warn("As https is in use, the certificates must also be wildcarded");
        }
      }
    } else {
      isUsingAws = true;
      printout.println("Endpoint is an AWS S3 store");
      if (region.isEmpty()) {
        printout.println("For reliable signing and performance the AWS region SHOULD be set in %s",
            REGION);
        printout.warn("If the network configuration prevents this client from accessing us-east region, the store will not be accessible");
      }
    }

    final boolean httpsUrl = endpoint.startsWith("https:");
    final boolean httpURL = endpoint.startsWith("http:");
    if (httpsUrl) {
      sslConnection = true;
    }
    if (!privateLink && (httpsUrl || httpURL)) {
      printout.warn("Value of %s looks like a URL: %s", ENDPOINT, endpoint);
      printout.println("It SHOULD normally be a hostname or IP address");
      printout.println("Unless you have a private store with a non-standard port or"
          + " are using AWS S3 PrivateLink");
      if (!pathStyleAccess) {
        printout.warn("You should probably set %s to true", PATH_STYLE_ACCESS);
      }
    }
    if (isUsingAws && !privateLink) {
      printout.println("");
      printout.println("This client is configured to connect to AWS S3");
      printout.println("");
      printout.println("Important: if you are working with a third party store,");
      printout.println("Expect failure until %s is set to the private endpoint", ENDPOINT);
    }

    if (isUsingV2Signing) {
      if (isUsingAws) {
        printout.warn(
            "The signing algorithm is %s; this is not supported on newer AWS buckets or the v2 AWS SDK",
            SIGNING_V2_ALGORITHM);
      } else {
        printout.println(
            "The signing algorithm is %s; this is required for some third-party S3 stores",
            SIGNING_V2_ALGORITHM);
        printout.warn("The signing algorithm is not available through the v2 AWS SDK");
      }
    }


    printout.heading("Bucket Name validation");

    printout.println("bucket name = \"%s\"", bucket);

    if (bucket.contains(".")) {
      printout.warn("The bucket name %s contains dot '.'", bucket);
      printout.warn("AWS do not allow this on new buckets as it has problems");
      printout.warn("In the S3A connector, per bucket options no longer work");
      if (!pathStyleAccess && sslConnection) {
        printout.warn("HTTPS certificate validation will fail unless any private"
            + " TLS certificate includes multiple wildcards");
      }
      printout.warn(
          "If you are using a fully qualified domain name as the bucket name *this doesn't work*");
      int l = 1;
      printout.println("%d. Set " + ENDPOINT + " to the endpoint/S3 host", l++);
      printout.println("%d. WARNING: Use the bucket name in the s3a URL", l++);
      if (!pathStyleAccess) {
        printout.println("%d. WARNING: Consider setting " + PATH_STYLE_ACCESS + " to true", l++);
      }
      if (sslConnection) {
        printout.println("%d. To disable HTTPS, set %s to true/use http",
            l++, SECURE_CONNECTIONS);
        if (httpsUrl) {
          printout.println("%d. And switch to an HTTP URL", l++);
        }
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
    final S3ASupport.Login accessKeys = S3ASupport.getAWSAccessKeys(getFsURI(), conf);
    String accessKey = accessKeys.getUser();
    String secretKey = accessKeys.getPassword();
    String sessionToken = S3ASupport.lookupPassword(conf, SESSION_TOKEN, "");
    if (accessKey.isEmpty()) {
      printout.warn(
          "No S3A access key defined; env var or other authentication mechanism must be active");
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

    // analytics accelerator options
    printPrefixedOptions(printout, conf, "fs.s3a.analytics.accelerator.");
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
    }
  }

  @Override
  public void validateFile(
      final Printout printout,
      final FileSystem filesystem,
      final Path path,
      final FileStatus status) throws IOException {

    printout.subheading("Reviewing bucket versioning");
    // using full import of S3AFileStatus to ensure it doesn't get used earlier on.
    if (!(status instanceof org.apache.hadoop.fs.s3a.S3AFileStatus)) {
      printout.warn("The file status for path %s is not an S3AFileStatus: %s%n",
          path, status);
      return;
    }
    org.apache.hadoop.fs.s3a.S3AFileStatus st = (org.apache.hadoop.fs.s3a.S3AFileStatus) status;
    final String versionId = st.getVersionId();
    if (versionId == null) {
      printout.println("The bucket does not have versioning enabled;"
          + " another backup strategy is needed");
    } else {
      printout.println("The bucket is using versioning.");

      if (!filesystem.hasPathCapability(path,
          STORE_CAPABILITY_DIRECTORY_MARKER_ACTION_KEEP)) {
        printout.warn("To avoid performance penalties, set %s to %s",
            DIRECTORY_MARKER_RETENTION, KEEP);
      } else {
        printout.println("Directory marker retention is enabled, so performance will suffer less");
      }
      printout.println("");
    }
  }

  @Override
  protected void performanceHints(
      final Printout printout,
      final Configuration conf) {

    printout.heading("Performance Hints");

    printout.subheading("CPUs/cores");
    int cpus = Runtime.getRuntime().availableProcessors();
    printout.println("\nThis diagnostics process was launched with %d cores", cpus);
    printout.println("Processes interacting with cloud stores generally benefit from having as many cores as possible");
    printout.println("That is: for applications such as distcp, it is better to have fewer processes with more cores");

    printout.subheading("Size options");
    hint(printout, conf.getBoolean(DISABLE_CACHE, false),
        "The option " + DISABLE_CACHE + " is true. "
            + "This may: result in the creation of many S3A clients, with consequences:\n"
            + "- add startup overhead to all uses\n"
            + "- use up memory, threads and other limited resources\n"
            + "- leak filesystem instances");

    int threadsMaxAdvised = 512;
    sizeHint(printout, conf,
        FS_S3A_THREADS_MAX, threadsMaxAdvised);
    sizeHint(printout, conf,
        FS_S3A_CONNECTION_MAXIMUM, threadsMaxAdvised * 2);
    sizeHint(printout, conf,
        FS_S3A_COMMITTER_THREADS, 256);

    if (sizeHint(printout, conf, FS_S3A_EXECUTOR_CAPACITY, 64)) {
      printout.println("This controls the amount of parallel operations during directory delete and renames.\n"
          + "Larger values may make these slightly faster, especially rename.");
    }

    printout.subheading("Time options");
    printout.println("\nReleases before Hadoop 3.4.0 may not support ms/m/h/... units");
    printout.println("If use of a time unit is rejected: supply a value in milliseconds");

    Duration requestTimeout = ofMinutes(15);
    Duration aMinute = ofMinutes(1);
    timeHint(printout, conf,
        CONNECTION_TIMEOUT, ofSeconds(200),
        false,
        "Time limit sending/receiving TCP packet");
    timeHint(printout, conf,
        CONNECTION_ESTABLISH_TIMEOUT, aMinute,
        false,
        "Time limit establishing a TLS connection to the store");
    timeHint(printout, conf,
        CONNECTION_REQUEST_TIMEOUT, requestTimeout,
        true,
        "Request timeout:\n"
            + "Maximum time for an HTTP request to return a 200 response."
            + "A low value can cause slow uploads to fail.");
    timeHint(printout, conf,
        CONNECTION_TTL, ofMinutes(5),
        true,
        "Maximum HTTP connection duration in the connection pool.\n"
            + "Reduces the risk of broken connections being reused.");

    printout.subheading("On 2024+ releases with HADOOP-18915 (hadoop 3.4.0+/CDP 7.2.18.0+)");
    timeHint(printout, conf, CONNECTION_ACQUISITION_TIMEOUT,
        aMinute,
        true,
        "Maximum wait to acquire a connection from the connection pool.");
    timeHint(printout, conf, CONNECTION_IDLE_TIME,
        aMinute,
        true,
        "Maximum idle time for connections in the pool");

    printout.subheading("On 2024+ releases with HADOOP-19295 (hadoop 3.4.1+):");
    timeHint(printout, conf, CONNECTION_PART_UPLOAD_TIMEOUT,
        requestTimeout,
        true, "");

    reviewReadPolicy(printout, conf);

    printout.subheading("Miscellaneous options");
    printout.println();

    int bucketProbe = conf.getInt(BUCKET_PROBE, 0);
    hint(printout, bucketProbe > 0,
        "Bucket existence is probed for on startup -this is inefficient and not needed.\n"
            + "set %s to 0", BUCKET_PROBE);

    hint(printout, conf.getBoolean(MULTIPART_PURGE, false),
        "Whenever a filesystem client is created, it prunes the bucket for old uploads.\n"
            + "Use a store lifecycle rule to do this and set %s to false",
        MULTIPART_PURGE);

    hint(printout,
        !KEEP.equals(conf.get(DIRECTORY_MARKER_RETENTION, "")),
        "If backwards compatibility is not an issue, set %s to \"keep\"",
        DIRECTORY_MARKER_RETENTION);


    printout.subheading("Bulk Delete behavior");
    boolean multi = conf.getBoolean(MULTIOBJECTDELETE_ENABLE, true);
    int pageSize = conf.getInt(BULK_DELETE_PAGE_SIZE, BULK_DELETE_PAGE_SIZE_DEFAULT);
    printout.println("%s = %s", MULTIOBJECTDELETE_ENABLE, multi);
    printout.println("%s = %d", BULK_DELETE_PAGE_SIZE, pageSize);
    printout.println();
    if (multi) {
      printout.println("Multi object delete is enabled; page size is %d", pageSize);
      hint(printout, pageSize > 500,
          "The page size is > 500. This is dangerous on heavily loaded stores\n"
              + "See HADOOP-16823. Large DeleteObject requests are their own Thundering Herd.");
      hint(printout, pageSize < 100,
          "The page size is <100. This will slow down renames, deletes and other bulk operations.");
    } else {
      printout.println("Multi object delete is disabled.");
      printout.println("This should only be done when working with third party stores.");

      printout.advise("When using AWS S3 stores, set %s = %s", MULTIOBJECTDELETE_ENABLE, true);

    }
  }

  /**
   * Review the read policy.
   */
  private void reviewReadPolicy(final Printout printout,
      final Configuration conf) {

    printout.subheading("Input stream type and read policy");
    boolean isClassicStream;

    final String streamType = conf.getTrimmed(INPUT_STREAM_TYPE, "").toLowerCase(Locale.ROOT);
    if (!streamType.isEmpty()) {
      printout.println("Stream type set in %s: %s", INPUT_STREAM_TYPE, streamType);
    }
    switch (streamType) {
    case ANALYTICS_STREAM:
    case CUSTOM_STREAM:
    case PREFETCHING_STREAM:
      isClassicStream = false;
      break;
    case "":
    case CLASSIC_STREAM:
    default:
      isClassicStream = true;
    }
    if (!isClassicStream) {
      printout.println("Data is being read through a more advanced stream than the classic S3AInputStream");
      printout.println("The stream options analyzed next may not apply");
    }

    // look at seek policy and warn of risks
    final String fadvise = conf.getTrimmed(INPUT_FADVISE, INPUT_FADV_NORMAL);

    printout.subheading("Read policy set by %s: %s", INPUT_FADVISE, fadvise);

    switch (fadvise) {

    case INPUT_FADV_NORMAL:
    case FS_OPTION_OPENFILE_READ_POLICY_ADAPTIVE:
    case FS_OPTION_OPENFILE_READ_POLICY_DEFAULT:
      printout.println("Adaptive: Reads start 'sequential' but switch to 'random' if"
          + " backward/random IO is detected");
      printout.println("This is adaptive and suitable for most workloads");
      break;

    case INPUT_FADV_RANDOM:
      printout.println("Stream is optimized for random IO, such as HBase, ORC and Parquet files");
      printout.println("This policy is suboptimal for sequential datasets (text, CSV, avro, .gzipped");
      printout.println("Recommended for ORC, Parquet data, HBase");
      break;

      // unlikely, just maps to random.
    case FS_OPTION_OPENFILE_READ_POLICY_VECTOR:
      printout.println("Stream is optimized for vector IO, especially ORC and Parquet files with libraries"
          + " using the Vector IO API for parallel reads");
      printout.println("All other read operations will be treated as random IO");

      // shouldn't be used for cluster configs.
    case INPUT_FADV_SEQUENTIAL:
    case FS_OPTION_OPENFILE_READ_POLICY_WHOLE_FILE:
      printout.println("The whole file is to be read.");
      printout.println("This policy is VERY BAD for ORC and Parquet files which seek around,"
          + "as unlike the %s policy, it doesn't recognize and adapt to their use patterns",
          FS_OPTION_OPENFILE_READ_POLICY_ADAPTIVE );
      printout.println("* seek() operations may abort the active HTTP request and require a new connection to be set up");
      printout.println("* HTTP connections may be kept too long");
      printout.advise("unless used for a specific purpose, change %s to %s",
          INPUT_FADVISE, FS_OPTION_OPENFILE_READ_POLICY_ADAPTIVE);
      break;

    case "avro":
    case "csv":
    case "json":
      printout.println("File-Format specific; supported on Hadoop 3.4.1+ only: sequential IO optimized");

    case "hbase":
    case "parquet":
      printout.println("File-Format specific; supported on Hadoop 3.4.1+ only: random IO optimized");

    default:
      printout.warn("Unrecognized policy for %s", INPUT_FADVISE);
    }

  }

  @Override
  public boolean deepTreeList() {
    return true;
  }

}
