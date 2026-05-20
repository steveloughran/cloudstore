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
import java.net.URI;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class S3ASupport {

  private S3ASupport() {}


  private static final Logger LOG = LoggerFactory.getLogger(S3ASupport.class);

  /**
   * Get a password from a configuration/configured credential providers.
   * 
   * @param conf configuration
   * @param key key to look up
   * @param defVal value to return if there is no password
   * @return a password or the value in {@code defVal}
   * @throws IOException on any problem
   */
  public static String lookupPassword(Configuration conf, String key, String defVal)
      throws IOException {
    try {
      final char[] pass = conf.getPassword(key);
      return pass != null ? new String(pass).trim() : defVal;
    } catch (IOException ioe) {
      throw new IOException("Cannot find password option " + key, ioe);
    }
  }

  public static Login getAWSAccessKeys(URI name, Configuration conf) throws IOException {
    String bucket = name != null ? name.getHost() : "";
    String accessKey = lookupPassword(bucket, conf, "fs.s3a.access.key");
    String secretKey = lookupPassword(bucket, conf, "fs.s3a.secret.key");
    return new Login(accessKey, secretKey);
  }

  /** @deprecated */
  @Deprecated
  public static String lookupPassword(String bucket, Configuration conf, String baseKey,
      String overrideVal) throws IOException {
    return lookupPassword(bucket, conf, baseKey, overrideVal, "");
  }

  public static String lookupPassword(String bucket, Configuration conf, String baseKey)
      throws IOException {
    return lookupPassword(bucket, conf, baseKey, null, "");
  }

  public static String lookupPassword(String bucket, Configuration conf, String baseKey,
      String overrideVal, String defVal) throws IOException {
    String initialVal;
    if (StringUtils.isNotEmpty(bucket)) {
      String subkey = baseKey.substring("fs.s3a.".length());
      String shortBucketKey = String.format("fs.s3a.bucket.%s.%s", bucket, subkey);
      String longBucketKey = String.format("fs.s3a.bucket.%s.%s", bucket, baseKey);
      initialVal = getPassword(conf, longBucketKey, overrideVal);
      initialVal = getPassword(conf, shortBucketKey, initialVal);
    } else {
      initialVal = overrideVal;
    }

    return getPassword(conf, baseKey, initialVal, defVal);
  }

  private static String getPassword(Configuration conf, String key, String val) throws IOException {
    return getPassword(conf, key, val, "");
  }

  private static String getPassword(Configuration conf, String key, String val, String defVal)
      throws IOException {
    return StringUtils.isEmpty(val) ? lookupPassword(conf, key, defVal) : val;
  }

  private static String lookupPassword2(Configuration conf, String key, String defVal)
      throws IOException {
    try {
      char[] pass = conf.getPassword(key);
      return pass != null ? (new String(pass)).trim() : defVal;
    } catch (IOException var4) {
      throw new IOException("Cannot find password option " + key, var4);
    }
  }

  /**
   * login.
   */
  public static final class Login {

    private final String user;

    private final String password;

    public static final Login EMPTY = new Login();

    public Login() {
      this("", "");
    }

    public Login(String user, String password) {
      this.user = user;
      this.password = password;
    }

    public boolean hasLogin() {
      return StringUtils.isNotEmpty(this.password) || StringUtils.isNotEmpty(this.user);
    }

    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (o != null && this.getClass() == o.getClass()) {
        Login that = (Login) o;
        return Objects.equals(this.user, that.user) && Objects.equals(this.password, that.password);
      } else {
        return false;
      }
    }

    public int hashCode() {
      return Objects.hash(this.user, this.password);
    }

    public String getUser() {
      return this.user;
    }

    public String getPassword() {
      return this.password;
    }
  }
}
