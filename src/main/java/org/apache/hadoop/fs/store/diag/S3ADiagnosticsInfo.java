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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalDirAllocator;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.StoreExitException;

import static org.apache.hadoop.fs.s3a.Constants.BUFFER_DIR;
import static org.apache.hadoop.fs.s3a.Constants.DEFAULT_SECURE_CONNECTIONS;
import static org.apache.hadoop.fs.s3a.Constants.INPUT_FADVISE;
import static org.apache.hadoop.fs.s3a.Constants.INPUT_FADV_NORMAL;
import static org.apache.hadoop.fs.s3a.Constants.INPUT_FADV_RANDOM;
import static org.apache.hadoop.fs.s3a.Constants.INPUT_FADV_SEQUENTIAL;
import static org.apache.hadoop.fs.s3a.Constants.SECURE_CONNECTIONS;
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
import static org.apache.hadoop.fs.store.diag.CapabilityKeys.STORE_CAPABILITY_MAGIC_COMMITTER;
import static org.apache.hadoop.fs.store.diag.HBossConstants.CAPABILITY_HBOSS;
import static org.apache.hadoop.fs.store.diag.OptionSets.HTTP_CLIENT_RESOURCES;
import static org.apache.hadoop.fs.store.diag.OptionSets.STANDARD_ENV_VARS;
import static org.apache.hadoop.fs.store.diag.OptionSets.STANDARD_SYSPROPS;

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

  public static final String HADOOP_TMP_DIR = "hadoop.tmp.dir";

  //use a custom endpoint?
  public static final String ENDPOINT = "fs.s3a.endpoint";
  public static final String DEFAULT_ENDPOINT = "";
  public static final String REGION = "fs.s3a.endpoint.region";

  //Enable path style access? Overrides default virtual hosting
  public static final String PATH_STYLE_ACCESS = "fs.s3a.path.style.access";

  public static final String DIRECTORY_MARKER_RETENTION =
      "fs.s3a.directory.marker.retention";

  private static final Object[][] options = {
      /* Core auth */
      {"fs.s3a.access.key", true, true},
      {"fs.s3a.secret.key", true, true},
      {"fs.s3a.session.token", true, true},
      {"fs.s3a.server-side-encryption-algorithm", true, false},
      {"fs.s3a.server-side-encryption.key", true, true},
      {"fs.s3a.encryption.algorithm", true, false},
      {"fs.s3a.encryption.key", true, true},
      {"fs.s3a.aws.credentials.provider", false, false},
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
      {"fs.s3a.bulk.delete.page.size", false, false},
      /* change detection */
      {"fs.s3a.change.detection.source", false, false},
      {"fs.s3a.change.detection.mode", false, false},
      {"fs.s3a.change.detection.version.required", false, false},

      {"fs.s3a.connection.ssl.enabled", false, false},
      {"fs.s3a.connection.maximum", false, false},
      {"fs.s3a.connection.establish.timeout", false, false},
      {"fs.s3a.connection.request.timeout", false, false},
      {"fs.s3a.connection.timeout", false, false},
      {"fs.s3a.create.performance", false, false},
      {"fs.s3a.create.storage.class", false, false},
      {"fs.s3a.custom.signers", false, false},
      {DIRECTORY_MARKER_RETENTION, false, false},
      {"fs.s3a.downgrade.syncable.exceptions", false, false},
      {"fs.s3a.etag.checksum.enabled", false, false},
      {"fs.s3a.experimental.input.fadvise", false, false},
      {"fs.s3a.experimental.aws.s3.throttling", false, false},
      {"fs.s3a.experimental.optimized.directory.operations", false, false},
      {"fs.s3a.fast.buffer.size", false, false},
      {"fs.s3a.fast.upload.buffer", false, false},
      {"fs.s3a.fast.upload.active.blocks", false, false},
      {"fs.s3a.impl.disable.cache", false, false},
      {"fs.s3a.list.version", false, false},
      {"fs.s3a.max.total.tasks", false, false},
      {"fs.s3a.multipart.size", false, false},
      {"fs.s3a.multiobjectdelete.enable", false, false},
      {"fs.s3a.multipart.purge", false, false},
      {"fs.s3a.multipart.purge.age", false, false},
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
      {"fs.s3a.threads.max", false, false},
      {"fs.s3a.threads.keepalivetime", false, false},
      {"fs.s3a.user.agent.prefix", false, false},
      {"fs.s3a.vectored.read.min.seek.size", false, false},
      {"fs.s3a.vectored.read.max.merged.size", false, false},


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
      {"fs.s3a.committer.threads", false, false},
      {"fs.s3a.committer.staging.unique-filenames", false, false},
      {"mapreduce.outputcommitter.factory.scheme.s3a", false, false},
      {"mapreduce.fileoutputcommitter.marksuccessfuljobs", false, false},


      /* delegation */
      {"fs.s3a.delegation.token.binding", false, false},
      /* this is from ranger, it should have been in .ext */
      {"fs.s3a.signature.cache.max.size", false, false},

      /* auditing */
      {"fs.s3a.audit.enabled", false, false},
      {"fs.s3a.audit.referrer.enabled", false, false},
      {"fs.s3a.audit.referrer.filter", false, false},
      {"fs.s3a.audit.reject.out.of.span.operations", false, false},
      {"fs.s3a.audit.request.handlers", false, false},
      {"fs.s3a.audit.service.classname", false, false},

      /* access points. */
      {"fs.s3a.accesspoint.arn", false, false},
      {"fs.s3a.accesspoint.required", false, false},

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
      {"AWS_CONTAINER_CREDENTIALS_RELATIVE_URI", false},
      {"AWS_CONTAINER_CREDENTIALS_FULL_URI", false},
      {"AWS_CONTAINER_AUTHORIZATION_TOKEN", true},
      {"AWS_EC2_METADATA_DISABLED", false},
      {"AWS_EC2_METADATA_SERVICE_ENDPOINT", false},
      {"AWS_MAX_ATTEMPTS", false},
      {"AWS_METADATA_SERVICE_TIMEOUT", false},
      {"AWS_RETRY_MODE", false},
      {"AWS_CONFIG_FILE", false},
      {"", false},
      {"", false},
      {"", false},
      {"", false},
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

  /**
   * Mandatory classnames.
   */
  public static final String[] CLASSNAMES = {
      "org.apache.hadoop.fs.s3a.S3AFileSystem",
      "com.amazonaws.services.s3.AmazonS3",
      "com.amazonaws.ClientConfiguration",
      "java.lang.System",
  };

  /**
   * Optional classes.
   */
  public static final String[] OPTIONAL_CLASSNAMES = {
      // AWS features outwith the aws-s3-sdk JAR and needed for later releases.
       "com.amazonaws.services.dynamodbv2.AmazonDynamoDB",
      // STS
      "com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient",
      // region support
      "com.amazonaws.regions.AwsRegionProvider",
      "com.amazonaws.regions.AwsEnvVarOverrideRegionProvider",
      "com.amazonaws.regions.AwsSystemPropertyRegionProvider",
      "com.amazonaws.regions.InstanceMetadataRegionProvider",
      "com.amazonaws.internal.TokenBucket",

      /* Jackson stuff */
      "com.fasterxml.jackson.annotation.JacksonAnnotation",
      "com.fasterxml.jackson.core.JsonParseException",
      "com.fasterxml.jackson.databind.ObjectMapper",

      // S3Guard
      "org.apache.hadoop.fs.s3a.s3guard.DynamoDBMetadataStore",

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

      "org.apache.hadoop.fs.s3a.ArnResource",
      "",

      // extra stuff from extension modules
      "org.apache.knox.gateway.cloud.idbroker.s3a.IDBDelegationTokenBinding",
      "org.wildfly.openssl.OpenSSLProvider",
      "org.apache.ranger.raz.hook.s3.RazS3ADelegationTokenIdentifier",

      // HBase HBoss
      "org.apache.hadoop.hbase.oss.HBaseObjectStoreSemantics",
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
   * Patch the config. This uses reflection to work on Hadoop 2.7.
   * @param conf initial configuration.
   * @return patched config.
   */
  @Override
  public Configuration patchConfigurationToInitalization(
      final Configuration conf) {
    try {
      Class<?> aClass = getClass().getClassLoader()
          .loadClass("org.apache.hadoop.fs.s3a.S3AUtils");
      Method m = aClass.getMethod("propagateBucketOptions",
          Configuration.class,
          String.class);
      return (Configuration)m.invoke(null, conf, getFsURI().getHost());
    } catch (ClassNotFoundException e) {
      LOG.error("S3AUtils not found: hadoop-aws is not on the classpath", e);
      // this will carry on elsewhere
    } catch (NoSuchMethodException
        | IllegalAccessException
        | InvocationTargetException e) {
      LOG.info("S3AUtils.propagateBucketOptions() not found; assume old Hadoop version");
    }
    return conf;
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
        conf.getBoolean("fs.s3a.connection.ssl.enabled", true);
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
        throw new StoreExitException(-1,
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
      final Configuration conf) throws IOException {
    String bufferOption = conf.get(BUFFER_DIR) != null
        ? BUFFER_DIR : HADOOP_TMP_DIR;
    printout.heading("S3A Config validation");

    printout.println("Buffer configuration option %s = %s",
        bufferOption, conf.get(bufferOption));

    final LocalDirAllocator directoryAllocator = new LocalDirAllocator(
        bufferOption);

    File temp = directoryAllocator.createTmpFileForWrite("temp", 1, conf);

    printout.println("Temporary files created in %s",
        temp.getParentFile());
    temp.delete();

    String encryption =
        conf.get("fs.s3a.server-side-encryption-algorithm", "").trim();
    String key = conf.get("fs.s3a.server-side-encryption.key", "").trim();
    boolean hasKey = !key.isEmpty();
    switch (encryption) {

    case "SSE-C":
      if (!hasKey) {
        throw new IllegalStateException(String.format(
            "Encryption method %s requires a key in %s",
            encryption, "fs.s3a.server-side-encryption.key"));
      }
      break;

    case "SSE-KMS":
      if (!hasKey) {
        printout.warn("SSE-KMS is enabled in %s"
                + " but there is no key set in %s",
            "fs.s3a.server-side-encryption-algorithm",
            "fs.s3a.server-side-encryption.key");
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

    boolean secureConnections = conf.getBoolean(SECURE_CONNECTIONS,
        DEFAULT_SECURE_CONNECTIONS);
    boolean pathStyleAccess = conf.getBoolean(PATH_STYLE_ACCESS, false);
    printout.println("Endpoint is set to \"%s\"", endpoint);
    if (endpoint.startsWith("https:") || endpoint.startsWith("http:")) {
      printout.warn("Value of %s looks like a URL: %s", ENDPOINT, endpoint);
      printout.println("It SHOULD normally be a hostname or IP address");
      printout.println("Unless you have a private store with a non-standard port or are using AWS S3 PrivateLink");
      if (!pathStyleAccess) {
        printout.warn("You should probably set %s to true", PATH_STYLE_ACCESS);
      }
    }
    if (endpoint.isEmpty()) {
      printout.println("Central us-east endpoint will be used."
          + "When not executing within EC2, this is less efficient for buckets in other regions");
    } else if (endpoint.endsWith("amazonaws.cn")) {
      printout.println("AWS china is in use");
    } else if (!endpoint.contains(".amazonaws.")) {
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
          printout.warn(
              "As https is in use, the certificates must also be wildcarded");
        }
      }
    } else {
      printout.println("Endpoint is an AWS S3 store");

    }
    String bucket = getFsURI().getHost();
    if (bucket.contains(".")) {
      printout.warn("The bucket name %s contains dot '.'", bucket);
      printout.warn("AWS do not allow this on new buckets as it has problems");
      printout.warn("In the S3A connector, per bucket options no longer work");
      if (!pathStyleAccess && secureConnections) {
        printout.warn("HTTPS certificate validation is probably broken");
      }
    }

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


    // now print everything fs.s3a.ext, assuming that
    // there are no secrets in it. Don't do that.
    printPrefixedOptions(printout, conf, "fs.s3a.ext.");
  }

  @Override
  public void validateFilesystem(final Printout printout,
      final Path path,
      final FileSystem filesystem) throws IOException {
    super.validateFilesystem(printout, path, filesystem);

    if (!"org.apache.hadoop.fs.s3a.S3AFileSystem".equals(
        filesystem.getClass().getCanonicalName())) {
      printout.warn("The filesystem class %s is not the S3AFileSystem",
          filesystem.getClass());
    }
  }


  @Override
  protected void performanceHints(
      final Printout printout,
      final Configuration conf) {

    printout.heading("Performance Hints");
    int threads = 512;
    sizeHint(printout, conf,
        "fs.s3a.threads.max", threads);
    sizeHint(printout, conf,
        "fs.s3a.connection.maximum", threads * 2);
    sizeHint(printout, conf,
        "fs.s3a.committer.threads", 256);

    hint(printout,
        !"keep".equals(conf.get(DIRECTORY_MARKER_RETENTION, "")),
        "If backwards compatibility is not an issue, set %s to keep",
        DIRECTORY_MARKER_RETENTION);
    hint(printout,
        "org.apache.hadoop.fs.s3a.s3guard.DynamoDBMetadataStore"
            .equals(conf.get("fs.s3a.metadatastore.impl","")),
        "S3Guard is no longer needed -decommission it");

  }

}
