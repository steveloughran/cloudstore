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

package org.apache.hadoop.fs.store.commands;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.fs.store.diag.DiagnosticsEntryPoint;
import org.apache.hadoop.fs.store.diag.Printout;
import org.apache.hadoop.util.ToolRunner;

import static java.util.Arrays.asList;
import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.diag.OptionSets.STANDARD_ENV_VARS;
import static org.apache.hadoop.fs.store.diag.OptionSets.STANDARD_SECURITY_PROPS;
import static org.apache.hadoop.fs.store.diag.OptionSets.TLS_ENV_VARS;
import static org.apache.hadoop.fs.store.diag.OptionSets.TLS_SYSPROPS;

/**
 * Print TLS info.
 */
public class TLSInfo extends DiagnosticsEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(TLSInfo.class);

  public static final String USAGE
      = "Usage: tlsinfo [<match>]\n"
      + STANDARD_OPTS;

  public TLSInfo() {
    createCommandFormat(0,1);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> params = processArgs(args, 0, 1, USAGE);
    String alias = params.isEmpty() ? null : params.get(0);

    lookupAndPrintSanitizedValues(TLS_SYSPROPS,
        "TLS System Properties",
        System::getProperty);

    printEnvVars(TLS_ENV_VARS);
    printSecurityProperties(STANDARD_SECURITY_PROPS);

    println();
    tlsInfo(this);
    final int matches = certInfo(this,
        "Certificates from the default certificate manager",
        null,
        alias,
        isVerbose());
    // examine the keystore
/*    final String keystoreType = KeyStore.getDefaultType();
    println("Default Keystore type: %s", keystoreType);
    KeyStore keyStore = KeyStore.getInstance(keystoreType);
    keyStore.load(null);

    keyStoreInfo(keyStore, alias);*/

    if (alias != null) {
      if (matches > 0) {
        println("Number of certificates matching the string \"%s\" :%d",
            alias, matches);
      } else {
        // error condition
        println("No certificates found matching the string \"%s\"",
            alias);
        return -1;
      }
    }
    return 0;
  }

  private void keyStoreInfo(final KeyStore keyStore, String alias) throws KeyStoreException {
    println("Keystore has %d entries", keyStore.size());
    if (alias != null) {
      println("Looking for alias %s", alias);
      if (keyStore.containsAlias(alias)) {
        println("Found alias %s", alias);
        X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
        println("Certificate: %s", cert);
      } else {
        println("Alias %s not found", alias);
      }
    } else {
      // print out all the aliases
      int counter = 0;
      final Enumeration<String> aliases = keyStore.aliases();
      while (aliases.hasMoreElements()) {
        String a = aliases.nextElement();
        counter++;
        println("[%03d] alias: %s", counter, a);
      }

    }
  }

  /**
   * Print information about TLS.
   * @param printout output
   */
  public static void tlsInfo(final Printout printout) {


    try {
      final SSLContext sslContext = SSLContext.getDefault();
      final SSLParameters sslParameters =
          sslContext.getSupportedSSLParameters();
      final String[] protocols = sslParameters.getProtocols();
      printout.heading("HTTPS supported protocols");
      for (String protocol : protocols) {
        printout.println("    %s", protocol);
      }
    } catch (NoSuchAlgorithmException e) {
      LOG.warn("failed to create SSL context", e);
    }
    printout.println();
    printout.println("See https://www.java.com/en/configure_crypto.html");
    printout.println();
  }

  /**
   * Print out certificate info.
   * @param printout dest
   * @param heading heading to print
   * @param keyStore nullable keystore
   * @param alias
   * @param verbose verbose output
   * @return
   */
  public static int certInfo(
      final Printout printout,
      String heading,
      KeyStore keyStore,
      final String alias,
      final boolean verbose) {

    String match = alias != null ? alias.toLowerCase(Locale.ROOT) : "";
    int counter = 0;
    try {
      TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      List<X509Certificate> x509Certificates = new ArrayList<>();
      trustManagerFactory.init(keyStore);
      asList(trustManagerFactory.getTrustManagers()).stream()
          .forEach(t ->
              x509Certificates.addAll(asList(((X509TrustManager) t).getAcceptedIssuers())));
      printout.heading(heading);
      for (X509Certificate cert : x509Certificates) {
        final X500Principal principal = cert.getSubjectX500Principal();
        if (!verbose && !match.isEmpty() && !principal.getName().toLowerCase(Locale.ROOT).contains(match)) {
          continue;
        }
        counter++;
        printout.println("[%03d] %s: %s",
            counter,
            principal.toString(),
            verbose ? cert.toString() : "");
      }
    } catch (Exception e) {
      printout.warn("Failed to retrieve keystore %s", e.toString());
      LOG.warn("Stack trace", e);
    }

    return counter;
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new TLSInfo(), args);
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
