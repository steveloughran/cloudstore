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
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.LocalDirAllocator;
import org.apache.hadoop.fs.s3a.Constants;
import org.apache.hadoop.fs.s3a.S3AUtils;

import static org.apache.hadoop.fs.s3a.Constants.BUFFER_DIR;

public class S3ADiagnosticsInfo extends StoreDiagnosticsInfo {

  private static final Logger LOG = LoggerFactory.getLogger(
      S3ADiagnosticsInfo.class);

  public static final String ASSUMED_ROLE_STS_ENDPOINT
      = "fs.s3a.assumed.role.sts.endpoint";

  private static final Object[][] options = {
      {"fs.s3a.access.key", true, false},
      {"fs.s3a.secret.key", true, true},
      {"fs.s3a.session.token", true, true},
      {"fs.s3a.server-side-encryption-algorithm", true, false},
      {"fs.s3a.server-side-encryption.key", true, true},
      {"fs.s3a.aws.credentials.provider", false, false},
      {"fs.s3a.proxy.host", false, false},
      {"fs.s3a.proxy.port", false, false},
      {"fs.s3a.proxy.username", false, false},
      {"fs.s3a.proxy.password", true, true},
      {"fs.s3a.proxy.domain", false, false},
      {"fs.s3a.proxy.workstation", false, false},
      {"fs.s3a.connection.ssl.enabled", false, false},
      {"fs.s3a.connection.maximum", false, false},
      {"fs.s3a.multipart.size", false, false},
      {"fs.s3a.buffer.dir", false, false},
      {"fs.s3a.block.size", false, false},

      {"fs.s3a.signing-algorithm", false, false},
      {"fs.s3a.fast.upload.buffer", false, false},
      {"fs.s3a.fast.upload.active.blocks", false, false},
      {"fs.s3a.experimental.input.fadvise", false, false},
      {"fs.s3a.user.agent.prefix", false, false},
      {"fs.s3a.threads.max", false, false},
      {"fs.s3a.threads.keepalivetime", false, false},
      {"fs.s3a.max.total.tasks", false, false},

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
      {"fs.s3a.s3guard.ddb.table", false, false},
      {"fs.s3a.s3guard.ddb.region", false, false},
      {"fs.s3a.s3guard.ddb.table.create", false, false},
      {"fs.s3a.s3guard.ddb.max.retries", false, false},
      {"fs.s3a.s3guard.ddb.background.sleep", false, false},

      /* committer */
      {"fs.s3a.committer.magic.enabled", false, false},
      {"fs.s3a.committer.staging.tmp.path", false, false},
      {"fs.s3a.committer.threads", false, false},
      {"mapreduce.outputcommitter.factory.scheme.s3a", false, false},
      {"fs.s3a.committer.name", false, false},
      {"fs.s3a.committer.staging.conflict-mode", false, false},

      /* misc */
      {"fs.s3a.etag.checksum.enabled", false, false},
      {"fs.s3a.retry.interval", false, false},
      {"fs.s3a.retry.throttle.limit", false, false},
      {"fs.s3a.retry.throttle.interval", false, false},
      {"fs.s3a.attempts.maximum", false, false},

      // delegation       
      {"fs.s3a.delegation.token.binding", false, false},

      {"", false, false},

  };

  protected static final Object[][] ENV_VARS = {
      {"AWS_ACCESS_KEY_ID", false},
      {"AWS_SECRET_ACCESS_KEY", true},
      {"AWS_SESSION_TOKEN", true},
  };

  public static final String[] classnames = {
      "org.apache.hadoop.fs.s3a.S3AFileSystem",
      "com.amazonaws.services.s3.AmazonS3",
      "com.amazonaws.ClientConfiguration",
  };

  public static final String[] optionalClassnames = {
       "com.amazonaws.services.dynamodbv2.AmazonDynamoDB",
      /* Jackson stuff */
      "com.fasterxml.jackson.annotation.JacksonAnnotation",
      "com.fasterxml.jackson.core.JsonParseException",
      "com.fasterxml.jackson.databind.ObjectMapper",
      /* And Joda-time. Not relevant on the shaded SDK,
       *  but critical for older ones */
      "org.joda.time.Interval",
      // STS
      "com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient",
      // S3 Select
      "com.amazonaws.services.s3.model.SelectObjectContentRequest",
      // Delegation Tokens
//      "org.apache.knox.gateway.shell.Hadoop",
      "org.apache.knox.gateway.shell.knox.token.Token",
      "org.apache.commons.configuration.Configuration",
      "",
  };

  public static final String HADOOP_TMP_DIR = "hadoop.tmp.dir";

  public S3ADiagnosticsInfo(final URI fsURI) {
    super(fsURI);
  }

  @Override
  public String getName() {
    return "S3A FileSystem connector";
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
    return ENV_VARS;
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
  public Configuration patchConfigurationToInitalization(final Configuration conf) {
    return S3AUtils.propagateBucketOptions(conf, getFsURI().getHost());
  }

  /**
   * Determine the S3 endpoints if set (or default).
   * {@inheritDoc}
   */
  @Override
  public List<URI> listEndpointsToProbe(final Configuration conf)
      throws IOException {
    String endpoint = conf.getTrimmed(Constants.ENDPOINT, "s3.amazonaws.com");
    String bucketURI;
    String bucket = getFsURI().getHost();
    String fqdn;
    if (bucket.contains(".")) {
      LOG.info("URI appears to be FQDN; using as endpoint");
      fqdn = bucket;
    } else {
      fqdn = bucket + "." + endpoint;
    }
    final boolean pathStyleAccess = conf.getBoolean("fs.s3a.path.style.access", false);
    boolean secureConnections =
        conf.getBoolean("fs.s3a.connection.ssl.enabled", true);
    String scheme = secureConnections ? "https" : "http";
    if (pathStyleAccess) {
      LOG.info("Enabling path style access");
      bucketURI = String.format("%s://%s/%s", scheme, endpoint, bucket);
    } else {
      bucketURI = String.format("%s://%s/", scheme, fqdn);
    }
    List<URI> uris = new ArrayList<>(2);
    uris.add(StoreDiag.toURI("Bucket URI", bucketURI));
    // If the STS endpoints is set, work out the URI
    final String sts = conf.get(ASSUMED_ROLE_STS_ENDPOINT, "");
    if (!sts.isEmpty()) {
      uris.add(StoreDiag.toURI(ASSUMED_ROLE_STS_ENDPOINT,
          String.format("https://%s/", sts)));
    }
    return uris;
  }

  @Override
  protected void validateConfig(final Printout printout,
      final Configuration conf) throws IOException {
    URI fsURI = getFsURI();
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
    
  }
}
