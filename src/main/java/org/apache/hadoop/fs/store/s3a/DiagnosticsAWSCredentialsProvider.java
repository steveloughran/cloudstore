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

package org.apache.hadoop.fs.store.s3a;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.s3a.auth.NoAwsCredentialsException;
import org.apache.hadoop.util.StringUtils;

public class DiagnosticsAWSCredentialsProvider implements
    AwsCredentialsProvider {

  private static final Logger LOG = LoggerFactory.getLogger(
      DiagnosticsAWSCredentialsProvider.class);

  private static final String[] secrets = {
      "fs.s3a.access.key",
      "fs.s3a.secret.key",
      "fs.s3a.session.token"
  };

  protected static final String[] ENV_VARS = {
      "AWS_ACCESS_KEY_ID",
      "AWS_ACCESS_KEY",
      "AWS_SECRET_KEY",
      "AWS_SECRET_ACCESS_KEY",
      "AWS_SESSION_TOKEN",
      "AWS_REGION"
  };


  private final Configuration conf;

  private MessageDigest digester;


  public DiagnosticsAWSCredentialsProvider(final Configuration conf) {
    this.conf = conf;
    try {
      digester = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      // ignored
      digester = null;
    }
    Arrays.stream(secrets).forEach(this::printSecretOption);
    Arrays.stream(ENV_VARS).forEach(this::printEnvVar);
  }

  @Override
  public AwsCredentials resolveCredentials() {
    throw new NoAwsCredentialsException("No credentials");
  }

  /**
   * determine the md5sum of a byte array.
   * @param data data
   * @return md5sum, or "" if the digester is null
   */
  private String md5sum(byte[] data) {
    if (digester != null) {
      digester.reset();
      digester.update(data);
      return DatatypeConverter.printHexBinary(digester.digest());
    } else {
      return "";
    }
  }


  /**
   * determine the md5sum of a char array.
   * @param data data
   * @return md5sum, or "" if the digester is null
   */
  private String md5sum(char[] data) {
    byte[] bytes = new byte[data.length];
    for (int i = 0; i < data.length; i++) {
      bytes[i] = (byte) data[i];
    }
    return md5sum(bytes);
  }

  /**
   * Print a secret option from the config.
   * @param key key to resolve.
   */
  public void printSecretOption(
      final String key) {
    String source = "";
    String option;
    char[] password;
    try {
      password = conf.getPassword(key);
    } catch (IOException e) {
      password = null;
    }
    if (password != null) {
      option = new String(password);
      String confOption = conf.get(key);
      if (option.equals(confOption)) {
        // the value in the conf file equals that of the password; assume that is the source
        String[] origins = conf.getPropertySources(key);
        if (origins != null && origins.length != 0) {
          source = "[" + StringUtils.join(",", origins) + "]";
        } else {
          source = "configuration class";
        }
      } else {
        source = "<credentials>";

      }
      LOG.info("Option {} = {} {} from {}",
          key, sanitize(option), md5sum(password), source);
    } else {
      LOG.info("Option {} unset", key);
    }
  }

  private void printEnvVar(String var) {
    final String value = System.getenv(var);
    if (value != null) {
      LOG.info("Env Var {} = {} {}",
          var, sanitize(value), md5sum(value.getBytes(StandardCharsets.UTF_8)));
    }
  }

  /**
   * Create a list of star characters.
   * @param n number to create.
   * @return a string of stars
   */
  private static String stars(int n) {
    StringBuilder b = new StringBuilder(n);
    for (int i = 0; i < n; i++) {
      b.append('*');
    }
    return b.toString();
  }

  /**
   * Sanitize a sensitive option.
   * @param value option value.
   * @return sanitized value.
   */
  public static String sanitize(final String value) {
    String safe = value;
    int len = safe.length();
    StringBuilder b = new StringBuilder(len);
    int prefix = 2;
    int suffix = 4;
    if (len > 2 + 4) {
      b.append(value, 0, prefix);
      b.append(stars(len - prefix - suffix));
      b.append(value, len - suffix, len);
      safe = b.toString();
    } else {
      // short values get special treatment
      safe = stars(2 + 4);
    }
    return String.format("\"%s\" [%d]", safe, len);
  }
}
