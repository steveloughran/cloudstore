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

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.AWSCredentialProviderList;
import org.apache.hadoop.fs.s3a.Invoker;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.s3a.S3ARetryPolicy;
import org.apache.hadoop.fs.store.DurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.s3a.Constants.ACCESS_KEY;
import static org.apache.hadoop.fs.s3a.Constants.AWS_CREDENTIALS_PROVIDER;
import static org.apache.hadoop.fs.s3a.Constants.SECRET_KEY;
import static org.apache.hadoop.fs.s3a.Constants.SESSION_TOKEN;
import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

public class SessionKeys extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(SessionKeys.class);

  public static final String TEMPORARY_AWSCREDENTIALS_PROVIDER
      = "org.apache.hadoop.fs.s3a.TemporaryAWSCredentialsProvider";

  public static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";

  public static final String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";

  public static final String AWS_SESSION_TOKEN = "AWS_SESSION_TOKEN";


  public SessionKeys() {
    createCommandFormat(1, 1,
        VERBOSE);
    addValueOptions(TOKENFILE, XMLFILE, DEFINE);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (paths.size() < 1) {
      return E_USAGE;
    }

    addAllDefaultXMLFiles();
    final Configuration conf = new Configuration();
    maybeAddXMLFileOption(conf, XMLFILE);
    maybePatchDefined(conf, DEFINE);
    AWSCredentialProviderList credentials = null;

    final Path source = new Path(paths.get(0));
    try (DurationInfo ignored = new DurationInfo(LOG, "session")) {
      S3AFileSystem fs = (S3AFileSystem) source.getFileSystem(conf);
      credentials = fs.shareCredentials("session");
      Configuration fsconf = fs.getConf();
      String bucket = fs.getBucket();
      AWSSecurityTokenServiceClientBuilder builder = STSClientFactory2.builder(
          fsconf, bucket, credentials);
      STSClientFactory2.STSClient stsClient
          = STSClientFactory2.createClientConnection(builder.build(),
          new Invoker(new S3ARetryPolicy(conf), Invoker.LOG_EVENT));
      Credentials sessionCreds = stsClient.requestSessionCredentials(36,
          TimeUnit.HOURS);

      String keyId = sessionCreds.getAccessKeyId();
      String secretKey = sessionCreds.getSecretAccessKey();
      String sessionToken = sessionCreds.getSessionToken();

      String endpoint = fsconf.get("fs.s3a.endpoint");
      String endpointkey = String.format("fs.s3a.bucket.%s.endpoint", bucket);

      heading("XML settings");

      StringBuilder xml = new StringBuilder();

      xml.append(elt(ACCESS_KEY, keyId));
      xml.append(elt(SECRET_KEY, secretKey));
      xml.append(elt(SESSION_TOKEN, sessionToken));

      xml.append(elt(AWS_CREDENTIALS_PROVIDER,
          TEMPORARY_AWSCREDENTIALS_PROVIDER));
      if (endpointkey != null) {
        xml.append(elt(endpointkey, endpoint));
      }
      println(xml.toString());

      heading("Properties");

      StringBuilder props = new StringBuilder();

      props.append(property(ACCESS_KEY, keyId));
      props.append(property(SECRET_KEY, secretKey));
      props.append(property(AWS_CREDENTIALS_PROVIDER,
          TEMPORARY_AWSCREDENTIALS_PROVIDER));
      props.append(property(SESSION_TOKEN, sessionToken));
      if (endpointkey != null) {
        props.append(property(endpointkey, endpoint));
      }
      println(props.toString());

      heading("Bash");

      StringBuilder bash = new StringBuilder();
      bash.append(env(AWS_ACCESS_KEY_ID, keyId));
      bash.append(env(AWS_SECRET_ACCESS_KEY, secretKey));
      bash.append(env(AWS_SESSION_TOKEN, sessionToken));
      println(bash.toString());

      heading("Fish");

      StringBuilder fish = new StringBuilder();
      fish.append(fishenv(AWS_ACCESS_KEY_ID, keyId));
      fish.append(fishenv(AWS_SECRET_ACCESS_KEY, secretKey));
      fish.append(fishenv(AWS_SESSION_TOKEN, sessionToken));
      println(fish.toString());
    } finally {
      if (credentials != null) {
        credentials.close();;
      }
    }

    return 0;
  }

  private String elt(String name, String text) {
    return String.format("<%s>%n  %s%n</%s>%n", name, text, name);
  }

  private String env(String name, String text) {
    return String.format("export %s=%s%n", name, text);
  }

  private String property(String name, String text) {
    return String.format("%s=%s%n", name, text);
  }

  private String fishenv(String name, String text) {
    return String.format("set -gx %s %s%n", name, text);
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new SessionKeys(), args);
  }

  /**
   * Main entry point. Calls {@code System.exit()} on all execution paths.
   * @param args argument list
   */
  public static void main(String[] args) {
    try {
      exit(exec(args), "");
    } catch (Throwable e) {
      exitOnThrowable(e);
    }
  }


}
