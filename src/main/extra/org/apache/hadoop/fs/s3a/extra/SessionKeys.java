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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSSessionCredentials;
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
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.fs.store.commands.EnvEntry;
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

  public static final String JSON = "json";
  public static final String ROLE = "role";

  public static final String USAGE
      = "Usage: sessionkeys\n"
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(XMLFILE, "file", "XML config file to load")
      + optusage(ROLE, "arn", "Role to assume")
      + optusage(JSON, "file", "Json file to load (only valid if -role is set")
      + " <S3A path>";

  public SessionKeys() {
    createCommandFormat(1, 1,
        VERBOSE);
    addValueOptions(TOKENFILE, XMLFILE, DEFINE, ROLE, JSON);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (paths.size() < 1) {
      errorln(USAGE);
      return E_USAGE;
    }

    final Configuration conf = createPreconfiguredConfig();

    // get JSON or empty string
    String role = getOptional(ROLE).orElse("");
    String jsonFile = getOptional(JSON).orElse("");
    boolean hasRole = !role.isEmpty();
    boolean hasJsonFile = !jsonFile.isEmpty();
    String json = null;
    if (hasJsonFile) {
      if (!hasRole) {
        errorln("No -role specified for JSON");
        return E_USAGE;
      }
      json = new String(Files.readAllBytes(Paths.get(jsonFile)));
    }

    AWSCredentialProviderList credentials = null;

    final Path source = new Path(paths.get(0));
    try (StoreDurationInfo ignored = new StoreDurationInfo(LOG,
        "requesting %s credentials",
        hasRole ? "role" : "session")) {
      S3AFileSystem fs = (S3AFileSystem) source.getFileSystem(conf);
      credentials = fs.shareCredentials("session");
      Configuration fsconf = fs.getConf();
      String bucket = fs.getBucket();
      String keyId;
      String secretKey;
      String sessionToken;
      // look to see if the creds are already session credentials
      if (credentials.getCredentials() instanceof AWSSessionCredentials) {
        // already got session credentials, so just print out
        println("Bucket credentials are allready session credentials");
        final AWSSessionCredentials session = (AWSSessionCredentials) credentials.getCredentials();
        keyId = session.getAWSAccessKeyId();
        secretKey = session.getAWSSecretKey();
        sessionToken = session.getSessionToken();
      } else {

        Credentials sessionCreds;

        AWSSecurityTokenServiceClientBuilder builder = STSClientFactory2.builder(
            fsconf, bucket, credentials);
        STSClientFactory2.STSClient stsClient
            = STSClientFactory2.createClientConnection(builder.build(),
            new Invoker(new S3ARetryPolicy(conf), Invoker.LOG_EVENT));

        if (!hasRole) {
          sessionCreds = stsClient.requestSessionCredentials(36,
              TimeUnit.HOURS);
        } else {
          sessionCreds = stsClient.requestRole(role,
              "role-session",
              json,
              12, TimeUnit.HOURS);
        }


        keyId = sessionCreds.getAccessKeyId();
        secretKey = sessionCreds.getSecretAccessKey();
        sessionToken = sessionCreds.getSessionToken();
      }


      String endpoint = fsconf.get("fs.s3a.endpoint");
      String region = fsconf.get("fs.s3a.endpoint.region");
      String endpointkey = String.format("fs.s3a.bucket.%s.endpoint", bucket);
      String regionkey = String.format("fs.s3a.bucket.%s.endpoint.region",
          bucket);

      List<EnvEntry> entries = new ArrayList<>();
      entries.add(new EnvEntry(ACCESS_KEY, AWS_ACCESS_KEY_ID, keyId));
      entries.add(new EnvEntry(SECRET_KEY, AWS_SECRET_ACCESS_KEY, secretKey));
      entries.add(new EnvEntry(SESSION_TOKEN, AWS_SESSION_TOKEN, sessionToken));
      entries.add(new EnvEntry(AWS_CREDENTIALS_PROVIDER, "",
          TEMPORARY_AWSCREDENTIALS_PROVIDER));
      if (endpoint != null) {
        entries.add(new EnvEntry(endpointkey, "", endpoint));
      }
      if (region != null) {
        entries.add(new EnvEntry(regionkey, "AWS_REGION", region));
      }

      // =================================================
      heading("XML settings");

      StringBuilder xml = new StringBuilder();
      xml.append("<configuration>\n\n");

      entries.forEach(e ->
          xml.append(e.xml()));
      xml.append("\n</configuration>\n");

      println(xml.toString());

      // =================================================
      heading("Properties");

      StringBuilder props = new StringBuilder();
      entries.forEach(e ->
          props.append(e.property()));

      println(props.toString());

      // =================================================
      heading("CLI Arguments");

      StringBuilder cliprops = new StringBuilder();
      entries.forEach(e ->
          cliprops.append(e.cliProperty()));

      println(cliprops.toString());

      // =================================================
      heading("Spark");
      StringBuilder spark = new StringBuilder();
      entries.forEach(e ->
          spark.append(e.spark()));
      println(spark.toString());

      // =================================================
      heading("Bash");

      StringBuilder bash = new StringBuilder();
      entries.stream()
          .filter(EnvEntry::hasEnvVar)
          .forEach(e ->
              bash.append(e.bash()));
      println(bash.toString());

      // =================================================
      heading("Fish");

      StringBuilder fish = new StringBuilder();
      entries.stream()
          .filter(EnvEntry::hasEnvVar)
          .forEach(e ->
              fish.append(e.fish()));
      println(fish.toString());

      // =================================================
      heading("env");

      StringBuilder env = new StringBuilder();
      entries.stream()
          .filter(EnvEntry::hasEnvVar)
          .forEach(e ->
              env.append(e.env()));
      println(env.toString());


    } finally {
      if (credentials != null) {
        credentials.close();
      }
    }

    return 0;
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
