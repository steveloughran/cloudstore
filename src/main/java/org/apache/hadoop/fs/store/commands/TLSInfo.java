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
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.fs.store.diag.DiagnosticsEntryPoint;
import org.apache.hadoop.fs.store.diag.Printout;
import org.apache.hadoop.util.ToolRunner;

import static java.util.Arrays.asList;
import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.fs.store.diag.OptionSets.TLS_SYSPROPS;

/**
 * Print TLS info.
 */
public class TLSInfo extends DiagnosticsEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(TLSInfo.class);

  public static final String USAGE
      = "Usage: tlsinfo\n"
      + STANDARD_OPTS;

  public TLSInfo() {
    createCommandFormat(0, 0);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (!paths.isEmpty()) {
      errorln(USAGE);
      return E_USAGE;
    }
    lookupAndPrintSanitizedValues(TLS_SYSPROPS,
        "TLS System Properties",
        System::getProperty);
    println();
    tlsInfo(this);
    certInfo(this, isVerbose());
    return 0;
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
   * @param verbose verbose output
   */
  public static void certInfo(final Printout printout, final boolean verbose) {

    try {
      TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      List<X509Certificate> x509Certificates = new ArrayList<>();
      trustManagerFactory.init((KeyStore) null);
      asList(trustManagerFactory.getTrustManagers()).stream()
          .forEach(t ->
              x509Certificates.addAll(asList(((X509TrustManager) t).getAcceptedIssuers())));
      printout.heading("Certificates from the default certificate manager");
      int counter = 1;
      for (X509Certificate cert : x509Certificates) {
        printout.println("[%03d] %s: %s",
            counter,
            cert.getSubjectX500Principal().toString(),
            verbose ? cert.toString() : "");
        counter++;
      }
    } catch (NoSuchAlgorithmException | KeyStoreException e) {
      printout.warn("Failed to retrieve keystore %s", e.toString());
      LOG.warn("Stack trace", e);
    }
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
