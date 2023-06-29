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

package org.apache.hadoop.fs.s3a.extra;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.GetSessionTokenRequest;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.s3a.Invoker;
import org.apache.hadoop.fs.s3a.Retries;
import org.apache.hadoop.fs.s3a.S3AUtils;

/**
 * Factory for creating STS Clients.
 * Lifted from hadoop 3.3.0 and stripped down a bit...always
 * uses central STS endpoint.
 */
public class STSClientFactory2 {

  private static final Logger LOG =
      LoggerFactory.getLogger(STSClientFactory2.class);

  /**
   * Create the builder ready for any final configuration options.
   * Picks up connection settings from the Hadoop configuration, including
   * proxy secrets.
   * @param conf Configuration to act as source of options.
   * @param bucket Optional bucket to use to look up per-bucket proxy secrets
   * @param credentials AWS credential chain to use
   * @return the builder to call {@code build()}
   * @throws IOException problem reading proxy secrets
   */
  public static AWSSecurityTokenServiceClientBuilder builder(
      final Configuration conf,
      final String bucket,
      final AWSCredentialsProvider credentials) throws IOException {
    final ClientConfiguration awsConf = S3AUtils.createAwsConf(conf, bucket);
    return builder(credentials, awsConf);
  }

  /**
   * Create the builder ready for any final configuration options.
   * Picks up connection settings from the Hadoop configuration, including
   * proxy secrets.
   * @param credentials AWS credential chain to use
   * @param awsConf AWS configuration.
   * @return the builder to call {@code build()}
   */
  public static AWSSecurityTokenServiceClientBuilder builder(
      final AWSCredentialsProvider credentials,
      final ClientConfiguration awsConf) {
    final AWSSecurityTokenServiceClientBuilder builder
        = AWSSecurityTokenServiceClientBuilder.standard();
    Preconditions.checkArgument(credentials != null, "No credentials");
    builder.withClientConfiguration(awsConf);
    builder.withCredentials(credentials);
    return builder;
  }

  /**
   * Create an STS Client instance.
   * @param tokenService STS instance
   * @param invoker invoker to use
   * @return an STS client bonded to that interface.
   * @throws IOException on any failure
   */
  public static STSClient createClientConnection(
      final AWSSecurityTokenService tokenService,
      final Invoker invoker)
      throws IOException {
    return new STSClient(tokenService, invoker);
  }

  /**
   * STS client connection with retries.
   */
  public static final class STSClient implements Closeable {

    private final AWSSecurityTokenService tokenService;

    private final Invoker invoker;

    private STSClient(final AWSSecurityTokenService tokenService,
        final Invoker invoker) {
      this.tokenService = tokenService;
      this.invoker = invoker;
    }

    @Override
    public void close() throws IOException {
      try {
        tokenService.shutdown();
      } catch (UnsupportedOperationException ignored) {
        // ignore this, as it is what the STS client currently
        // does.
      }
    }

    /**
     * Request a set of session credentials.
     *
     * @param duration duration of the credentials
     * @param timeUnit time unit of duration
     * @return the role result
     * @throws IOException on a failure of the request
     */
    @Retries.RetryTranslated
    public Credentials requestSessionCredentials(
        final long duration,
        final TimeUnit timeUnit) throws IOException {
      int durationSeconds = (int) timeUnit.toSeconds(duration);
      LOG.debug("Requesting session token of duration {}", duration);
      final GetSessionTokenRequest request = new GetSessionTokenRequest();
      request.setDurationSeconds(durationSeconds);
      return invoker.retry("request session credentials", "",
          true,
          () -> {
            LOG.info("Requesting Amazon STS Session credentials");
            return tokenService.getSessionToken(request).getCredentials();
          });
    }

    /**
     * Request a set of role credentials.
     *
     * @param roleARN ARN to request
     * @param sessionName name of the session
     * @param policy optional policy; "" is treated as "none"
     * @param duration duration of the credentials
     * @param timeUnit time unit of duration
     * @return the role result
     * @throws IOException on a failure of the request
     */
    @Retries.RetryTranslated
    public Credentials requestRole(
        final String roleARN,
        final String sessionName,
        final String policy,
        final long duration,
        final TimeUnit timeUnit) throws IOException {
      LOG.debug("Requesting role {} with duration {}; policy = {}",
          roleARN, duration, policy);
      AssumeRoleRequest request = new AssumeRoleRequest();
      request.setDurationSeconds((int) timeUnit.toSeconds(duration));
      request.setRoleArn(roleARN);
      request.setRoleSessionName(sessionName);
      if (policy != null && !policy.isEmpty()) {
        request.setPolicy(policy);
      }
      return invoker.retry("request role credentials", "", true,
          () -> tokenService.assumeRole(request).getCredentials());
    }
  }
}
