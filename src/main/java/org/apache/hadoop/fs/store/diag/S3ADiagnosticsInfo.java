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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalDirAllocator;
import org.apache.hadoop.fs.Path;

import static org.apache.hadoop.fs.s3a.Constants.*;
import static org.apache.hadoop.fs.store.StoreUtils.cat;
import static org.apache.hadoop.fs.store.diag.OptionSets.STANDARD_ENV_VARS;

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

  //Enable path style access? Overrides default virtual hosting
  public static final String PATH_STYLE_ACCESS = "fs.s3a.path.style.access";

  private static final Object[][] options = {
      /* Core auth */
      {"fs.s3a.access.key", true, true},
      {"fs.s3a.secret.key", true, true},
      {"fs.s3a.session.token", true, true},
      {"fs.s3a.server-side-encryption-algorithm", true, false},
      {"fs.s3a.server-side-encryption.key", true, true},
      {"fs.s3a.aws.credentials.provider", false, false},
      {"fs.s3a.endpoint", false, false},
      {"fs.s3a.endpoint.region", false, false},
      {"fs.s3a.signing-algorithm", false, false},
      {"fs.s3a.cse.method", true, false},
      {"fs.s3a.cse.kms.key-id", true, false},

      /* Core Set */
      {"fs.s3a.acl.default", false, false},
      {"fs.s3a.attempts.maximum", false, false},
      {"fs.s3a.authoritative.path", false, false},
      {"fs.s3a.block.size", false, false},
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
      {"fs.s3a.custom.signers", false, false},
      {"fs.s3a.directory.marker.retention", false, false},
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
      {"fs.s3a.paging.maximum", false, false},
      {"fs.s3a.multiobjectdelete.enable", false, false},
      {"fs.s3a.multipart.purge", false, false},
      {"fs.s3a.multipart.purge.age", false, false},
      {"fs.s3a.paging.maximum", false, false},
      {PATH_STYLE_ACCESS, false, false},
      {"fs.s3a.proxy.host", false, false},
      {"fs.s3a.proxy.port", false, false},
      {"fs.s3a.proxy.username", false, false},
      {"fs.s3a.proxy.password", true, true},
      {"fs.s3a.proxy.domain", false, false},
      {"fs.s3a.proxy.workstation", false, false},
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
      {"fs.s3a.delegation.token.secondary.bindings", false, false},

      /* auditing */
      {"fs.s3a.audit.referrer.enabled", false, false},
      {"fs.s3a.audit.referrer.filter", false, false},
      {"fs.s3a.audit.reject.out.of.span.operations", false, false},
      {"fs.s3a.audit.request.handlers", false, false},
      {"fs.s3a.audit.service.classname", false, false},
      {"", false, false},
      {"", false, false},
      {"", false, false},


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
      {"com.amazonaws.services.s3.disableImplicitGlobalClients", false},
      {"com.amazonaws.services.s3.enableV4", false},
      {"com.amazonaws.services.s3.enforceV4", false},
      {"", false},
      {"", false},
  };



  /**
   * Mandatory classnames.
   */
  public static final String[] classnames = {
      "org.apache.hadoop.fs.s3a.S3AFileSystem",
      "com.amazonaws.services.s3.AmazonS3",
      "com.amazonaws.ClientConfiguration",
      "java.lang.System",
  };

  /**
   * Optional classes.
   */
  public static final String[] optionalClassnames = {
      // AWS features outwith the aws-s3-sdk JAR and needed for later releases.
       "com.amazonaws.services.dynamodbv2.AmazonDynamoDB",
      // STS
      "com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient",

      /* Jackson stuff */
      "com.fasterxml.jackson.annotation.JacksonAnnotation",
      "com.fasterxml.jackson.core.JsonParseException",
      "com.fasterxml.jackson.databind.ObjectMapper",
      /* And Joda-time. Not relevant on the shaded SDK,
       *  but critical for older ones */
      "org.joda.time.Interval",

      // S3Guard
      "org.apache.hadoop.fs.s3a.s3guard.S3Guard",

      // Committers
      "org.apache.hadoop.fs.s3a.commit.staging.StagingCommitter",
      "org.apache.hadoop.fs.s3a.commit.magic.MagicS3GuardCommitter",
      "org.apache.hadoop.fs.s3a.Invoker",

      // Assumed Role credential provider (Hadoop 3.1)
      "org.apache.hadoop.fs.s3a.auth.AssumedRoleCredentialProvider",
      // session creds, just for completeness
      "org.apache.hadoop.fs.s3a.TemporaryAWSCredentialsProvider",

      // Delegation Tokens
      "org.apache.hadoop.fs.s3a.auth.delegation.S3ADelegationTokens",


      // S3 Select: HADOOP-15229
      "com.amazonaws.services.s3.model.SelectObjectContentRequest",
      "org.apache.hadoop.fs.s3a.select.SelectInputStream",

      // S3Guard rename extensions
      "org.apache.hadoop.fs.s3a.impl.RenameOperation",
      "org.apache.hadoop.fs.s3a.impl.NetworkBinding",

      // dir markers
      "org.apache.hadoop.fs.s3a.impl.DirectoryPolicy",

      // Auditing
      "org.apache.hadoop.fs.s3a.audit.AuditManagerS3A",

      // extra stuff from extension modules
      "org.apache.knox.gateway.cloud.idbroker.s3a.IDBDelegationTokenBinding",
      "org.wildfly.openssl.OpenSSLProvider",

      "",

  };

  public S3ADiagnosticsInfo(final URI fsURI) {
    super(fsURI);
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
    return AWS_SYSPROPS;
  }

  @Override
  public String[] getClassnames(final Configuration conf) {
    return classnames;
  }

  @Override
  public String[] getOptionalClassnames(final Configuration conf) {
    return optionalClassnames;
  }

  /**
   * Patch the config. This uses reflection to work on Hadoop 2.7.
   * @param conf initial configuration.
   * @return patched config.
   */
  @Override
  public Configuration patchConfigurationToInitalization(final Configuration conf)
      {
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
    String scheme = secureConnections ? "https" : "http";
    if (pathStyleAccess) {
      LOG.info("Enabling path style access");
      bucketURI = String.format("%s://%s/%s", scheme, endpoint, bucket);
    } else {
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

    String encryption = conf.get("fs.s3a.server-side-encryption-algorithm", "").trim();
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
}
