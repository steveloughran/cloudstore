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
package org.apache.hadoop.fs.gs;

import static com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystemConfiguration.GCS_CONFIG_PREFIX;
import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.diag.GCSDiagnosticsInfo.SERVICE_ACCOUNT_PRIVATE_KEY;
import static org.apache.hadoop.service.launcher.LauncherExitCodes.EXIT_USAGE;

import com.google.cloud.hadoop.repackaged.gcs.com.google.api.client.auth.oauth2.Credential;
import com.google.cloud.hadoop.repackaged.gcs.com.google.cloud.hadoop.util.CredentialFactory;
import com.google.cloud.hadoop.repackaged.gcs.com.google.cloud.hadoop.util.HadoopCredentialConfiguration;
import com.google.cloud.hadoop.repackaged.gcs.com.google.common.collect.ImmutableList;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.service.launcher.LauncherExitCodes;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Debug GS credentials. Getting past the "java.io.IOException: Invalid PKCS8 data" error message
 * with gs auth is a nightmare. This class tries to debug it.
 * <p>
 * Needs the shaded gs jar on the classpath as we call its internals.
 * <p>
 * * code includes methods copied from com.google.cloud.hadoop.util.CredentialFactory
 * <a href="https://cloud.google.com/docs/authentication/production">...</a>
 * <a href="https://developers.google.com/accounts/docs/application-default-credentials">...</a>
 * <p>
 * Security note, -verbose will print the credentials. Sometimes this is necessary to work out what
 * is going wrong. Do not file security reports about this behavior.
 */
public class GsCredDiag extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(GsCredDiag.class);

  /** Manage your data and permissions in Google Cloud Storage. */
  public static final String DEVSTORAGE_FULL_CONTROL =
      "https://www.googleapis.com/auth/devstorage.full_control";

  public static final String USAGE = "Usage: gcscreds\n" + STANDARD_OPTS + "\t<gs path>\n"
      + "warning: the -verbose option will print the credentials. DO NOT SHARE THE OUTPUT";

  public GsCredDiag() {
    createCommandFormat(1, 1);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (paths.isEmpty()) {
      errorln(USAGE);
      return EXIT_USAGE;
    }

    final Configuration conf = createPreconfiguredConfig();
    String key;
    final char[] password = conf.getPassword(SERVICE_ACCOUNT_PRIVATE_KEY);
    if (password != null) {
      key = new String(password).trim();
    } else {
      key = null;
    }
    if (key != null) {
      if (isVerbose()) {
        warn("The private key printed below is a critical secret. Do not share it");

        // insecure
        println("private key is <%s>", key);
      }
      if (key.contains("\\n")) {
        println("key uses \\n for separator -gs connector must convert to line endings");
        key = key.replace("\\n", System.lineSeparator());
        if (isVerbose()) {

          // insecure

          println("modified private key is <%s>", key);
        }
      }
    } else {
      println("no key in %s", SERVICE_ACCOUNT_PRIVATE_KEY);
      return LauncherExitCodes.EXIT_NOT_FOUND;
    }
    Reader reader = new StringReader(key);
    PemReader.Section section = PemReader.readFirstSectionAndClose(reader, "PRIVATE KEY");
    if (!section.isSuccess()) {
      errorln("Failed to parse private key: %s", section.getMessage());
      return LauncherExitCodes.EXIT_NOT_ACCEPTABLE;
    }

    println("Parsed private key -entry length %d lines", section.getLines());
    final CredentialFactory factory =
        HadoopCredentialConfiguration.getCredentialFactory(conf, GCS_CONFIG_PREFIX);
    println("factory %s", factory);
    Credential credential = factory.getCredential(ImmutableList.of(DEVSTORAGE_FULL_CONTROL));
    return 0;
  }

  /**
   * Execute the command, return the result or throw an exception, as appropriate.
   * 
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new GsCredDiag(), args);
  }

}
