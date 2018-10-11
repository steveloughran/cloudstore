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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.s3a.Constants;
import org.apache.hadoop.fs.s3a.S3AUtils;

public class S3ADiagnosticsInfo extends StoreDiagnosticsInfo {

  private static final Logger LOG = LoggerFactory.getLogger(
      S3ADiagnosticsInfo.class);

  private static final Object[][] options = {
      {"fs.s3a.access.key", true},
      {"fs.s3a.secret.key", true},
      {"fs.s3a.session.token", true},
      {"fs.s3a.server-side-encryption-algorithm", false},
      {"fs.s3a.server-side-encryption.key", true},
      {"fs.s3a.aws.credentials.provider", false},
      {"fs.s3a.proxy.host", false},
      {"fs.s3a.proxy.port", false},
      {"fs.s3a.proxy.username", false},
      {"fs.s3a.proxy.password", true},
      {"fs.s3a.proxy.domain", false},
      {"fs.s3a.proxy.workstation", false},
      {"fs.s3a.connection.ssl.enabled", false},
      {"fs.s3a.connection.maximum", false},
      {"fs.s3a.multipart.size", false},
      {"fs.s3a.buffer.dir", false},
      {"fs.s3a.block.size", false},

      {"fs.s3a.signing-algorithm", false},
      {"fs.s3a.fast.upload.buffer", false},
      {"fs.s3a.fast.upload.active.blocks", false},
      {"fs.s3a.experimental.input.fadvise", false},
      {"fs.s3a.user.agent.prefix", false},
      {"fs.s3a.threads.max", false},
      {"fs.s3a.threads.keepalivetime", false},
      {"fs.s3a.max.total.tasks", false},

      /* Assumed Role */
      {"fs.s3a.assumed.role.arn", false},
      {"fs.s3a.assumed.role.sts.endpoint", false},
      {"fs.s3a.assumed.role.sts.endpoint.region", false},
      {"fs.s3a.assumed.role.session.name", false},
      {"fs.s3a.assumed.role.session.duration", false},
      {"fs.s3a.assumed.role.credentials.provider", false},
      {"fs.s3a.assumed.role.policy", false},

      /* s3guard */
      {"fs.s3a.metadatastore.impl", false},
      {"fs.s3a.metadatastore.authoritative", false},
      {"fs.s3a.s3guard.ddb.table", false},
      {"fs.s3a.s3guard.ddb.region", false},
      {"fs.s3a.s3guard.ddb.table.create", false},
      {"fs.s3a.s3guard.ddb.max.retries", false},
      {"fs.s3a.s3guard.ddb.background.sleep", false},
      {"", false},

      /* committer */
      {"fs.s3a.committer.magic.enabled", false},
      {"fs.s3a.committer.staging.tmp.path", false},
      {"fs.s3a.committer.threads", false},
      {"mapreduce.outputcommitter.factory.scheme.s3a", false},
      {"fs.s3a.committer.name", false},
      {"fs.s3a.committer.staging.conflict-mode", false},

      /* misc */
      {"fs.s3a.etag.checksum.enabled", false},
      {"fs.s3a.retry.interval", false},
      {"fs.s3a.retry.throttle.limit", false},
      {"fs.s3a.retry.throttle.interval", false},
      {"fs.s3a.attempts.maximum", false},
      {"", false},
  };

  protected static final Object[][] ENV_VARS = {
      {"PATH", false},
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
      "com.amazonaws.services.s3.model.SelectObjectContentResponse",
      ""
  };

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
    return S3AUtils.propagateBucketOptions(conf, fsURI.getHost());
  }

  /**
   * Determine the S3 endpoint if set (or default).
   * {@inheritDoc}
   */
  @Override
  public List<URI> listEndpointsToProbe(final Configuration conf)
      throws IOException {
    String endpoint = conf.getTrimmed(Constants.ENDPOINT, "s3.amazonaws.com");
    String bucketURI;
    String bucket = fsURI.getHost();
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
    addUriOption(uris, conf, "fs.s3a.assumed.role.sts.endpoint", "");
    return uris;
  }
}
