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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.xml.bind.DatatypeConverter;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.s3a.auth.NoAwsCredentialsException;
import org.apache.hadoop.util.StringUtils;

import static org.apache.hadoop.fs.s3a.Constants.ACCESS_KEY;
import static org.apache.hadoop.fs.s3a.Constants.SECRET_KEY;
import static org.apache.hadoop.fs.s3a.Constants.SESSION_TOKEN;

public class DiagnosticsAWSCredentialsProvider implements
    AWSCredentialsProvider {

  private static final Logger LOG = LoggerFactory.getLogger(
      DiagnosticsAWSCredentialsProvider.class);

  private static final String[] secrets = {
      ACCESS_KEY,
      SECRET_KEY,
      SESSION_TOKEN
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
    Arrays.stream(secrets)
        .forEach(this::printSecretOption);

  }

  @Override
  public AWSCredentials getCredentials() {

    throw new NoAwsCredentialsException("No credentials");
  }

  @Override
  public void refresh() {

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
