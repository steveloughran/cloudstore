/*
 * Copyright 2013 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.fs.gs;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import com.google.cloud.hadoop.repackaged.gcs.com.google.api.client.auth.oauth2.Credential;
import com.google.cloud.hadoop.repackaged.gcs.com.google.cloud.hadoop.util.CredentialFactory;
import com.google.cloud.hadoop.repackaged.gcs.com.google.cloud.hadoop.util.HadoopCredentialConfiguration;
import com.google.cloud.hadoop.repackaged.gcs.com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.service.launcher.LauncherExitCodes;
import org.apache.hadoop.util.ToolRunner;

import static com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystemConfiguration.GCS_CONFIG_PREFIX;
import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.fs.store.diag.GCSDiagnosticsInfo.SERVICE_ACCOUNT_PRIVATE_KEY;

/**
 * Debug GS credentials.
 * Getting past the "java.io.IOException: Invalid PKCS8 data" error message
 * with gs auth is a nightmare. This class tries to debug it.
 *
 * Needs the shaded gs jar on the classpath as we call its internals.
 *
 * code includes methods copied from
 * com.google.cloud.hadoop.util.CredentialFactory
 * https://cloud.google.com/docs/authentication/production
 * https://developers.google.com/accounts/docs/application-default-credentials
 */
public class GsCredDiag extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(GsCredDiag.class);

  /** Manage your data and permissions in Google Cloud Storage. */
  public static final String DEVSTORAGE_FULL_CONTROL
      = "https://www.googleapis.com/auth/devstorage.full_control";
  public static final String USAGE
      = "Usage: gcscreds\n"
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(XMLFILE, "file", "XML config file to load")
      + optusage(VERBOSE, "print verbose output")
      + "\t<gs path>";

  public GsCredDiag() {
    createCommandFormat(1, 1,
        VERBOSE);
    addValueOptions(TOKENFILE, XMLFILE, DEFINE);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (paths.size() < 1) {
      errorln(USAGE);
      return E_USAGE;
    }

    final Configuration conf = createPreconfiguredConfig();
    String key = conf.get(SERVICE_ACCOUNT_PRIVATE_KEY);
    if (key != null) {
      if (isVerbose()) {

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
    final CredentialFactory factory = HadoopCredentialConfiguration.getCredentialFactory(
        conf, GCS_CONFIG_PREFIX);
    println("factory %s",
        factory);
    Credential credential = factory.getCredential(
        ImmutableList.of(DEVSTORAGE_FULL_CONTROL));
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
    return ToolRunner.run(new GsCredDiag(), args);
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
