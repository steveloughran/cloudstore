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

package org.apache.hadoop.fs.store.diag;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;

import static org.apache.hadoop.fs.store.StoreDiagConstants.IOSTATISTICS_LOGGING_LEVEL;

/**
 * standard env vars, JVM options etc.
 */
public class OptionSets {

  /**
   * Not all of these are in CommonConfigurationKeysPublic of older
   * Hadoop versions, so they are inlined.
   */
  public static final Object[][] SECURITY_OPTIONS = {
      {"dfs.data.transfer.protection", false, false},
      {"hadoop.http.authentication.simple.anonymous.allowed", false, false},
      {"hadoop.http.authentication.type", false, false},
      {"hadoop.kerberos.min.seconds.before.relogin", false, false},
      {"hadoop.kerberos.keytab.login.autorenewal.enabled", false, false},
      {"hadoop.security.authentication", false, false},
      {"hadoop.security.authorization", false, false},
      {"hadoop.security.credential.provider.path", false, false},
      {"hadoop.security.credstore.java-keystore-provider.password-file", false, false},
      {"hadoop.security.credential.clear-text-fallback", false, false},
      {"hadoop.security.key.provider.path", false, false},
      {"hadoop.security.crypto.jceks.key.serialfilter", false, false},
      {"hadoop.rpc.protection", false, false},
      {"hadoop.tokens", false, false},
      {"hadoop.token.files", false, false},
      {"", false, false},
  };

  public static final Object[][] CLUSTER_OPTIONS = {
      {"fs.defaultFS", false, false},
      {"fs.default.name", false, false},
      {"fs.creation.parallel.count", false, false},
      {"fs.permissions.umask-mode", false, false},
      {"fs.trash.classname", false, false},
      {"fs.trash.interval", false, false},
      {"fs.trash.checkpoint.interval", false, false},
      {"hadoop.tmp.dir", false, false},
      {"hdp.version", false, false},
      {"yarn.resourcemanager.address", false, false},
      {"yarn.resourcemanager.principal", false, false},
      {"yarn.resourcemanager.webapp.address", false, false},
      {"yarn.resourcemanager.webapp.https.address", false, false},
      {"mapreduce.input.fileinputformat.list-status.num-threads", false, false},
      {"mapreduce.jobtracker.kerberos.principal", false, false},
      {"mapreduce.job.hdfs-servers.token-renewal.exclude", false, false},
      {"mapreduce.application.framework.path", false, false},
      {IOSTATISTICS_LOGGING_LEVEL, false, false},
      {"fs.iostatistics.thread.level.enabled", false, false},

      {"", false, false},
  };

  public static final String HADOOP_TOKEN = "HADOOP_TOKEN";

  public static final String HADOOP_TOKEN_FILE_LOCATION
      = "HADOOP_TOKEN_FILE_LOCATION";

  /**
   * These are standard env vars which are good to look at.
   */
  public static final Object[][] STANDARD_ENV_VARS = {
      {"PATH", false},
      {"HADOOP_CONF_DIR", false},
      {"HADOOP_CLASSPATH", false},
      {"HADOOP_CREDSTORE_PASSWORD", true},
      {"HADOOP_HEAPSIZE", false},
      {"HADOOP_HEAPSIZE_MIN", false},
      {"HADOOP_HOME", false},
      {"HADOOP_LOG_DIR", false},
      {"HADOOP_OPTIONAL_TOOLS", false},
      {"HADOOP_OPTS", false},
      {"HADOOP_SHELL_SCRIPT_DEBUG", false},
      {HADOOP_TOKEN, false},
      {HADOOP_TOKEN_FILE_LOCATION, false},
      {"HADOOP_KEYSTORE_PASSWORD", true},
      {"HADOOP_TOOLS_HOME", false},
      {"HADOOP_TOOLS_OPTIONS", false},
      {"HADOOP_YARN_HOME", false},
      {"HDP_VERSION", false},
      {"JAVA_HOME", false},
      {"LD_LIBRARY_PATH", false},
      {"LOCAL_DIRS", false},
      {"OPENSSL_ROOT_DIR", false},
      {"PYSPARK_DRIVER_PYTHON", false},
      {"SPARK_HOME", false},
      {"SPARK_CONF_DIR", false},
      {"SPARK_SCALA_VERSION", false},
      {"YARN_CONF_DIR", false},
      {"", false},
      // TODO: add the https proxy vars
  };


  /**
   * Standard System properties.
   */
  public static final Object[][] STANDARD_SYSPROPS = {
      {"java.version", false},
      {"", false},
  };

  /**
   * TLS System properties.
   */
  public static final Object[][] TLS_SYSPROPS = {
      {"java.version", false},
      {"java.version", false},
      {"java.library.path", false},
      {"https.protocols", false},
      {"javax.net.ssl.keyStore", false},
      {"javax.net.ssl.keyStorePassword", true},
      {"javax.net.ssl.trustStore", false},
      {"javax.net.ssl.trustStorePassword", true},
      {"jdk.certpath.disabledAlgorithms", false},
      {"jdk.tls.client.cipherSuites", false},
      {"jdk.tls.client.protocols", false},
      {"jdk.tls.disabledAlgorithms", false},
      {"jdk.tls.legacyAlgorithms", false},
      {"", false},
  };

  public static final String MOZILLA_PUBLIC_SUFFIX_LIST =
      "mozilla/public-suffix-list.txt";

  public static final String[] HTTP_CLIENT_RESOURCES = {
      MOZILLA_PUBLIC_SUFFIX_LIST
  };

  public static final String HADOOP_TMP_DIR = "hadoop.tmp.dir";


  /**
   * The enhanced {@code openFile()} options.
   */
  @InterfaceAudience.Public
  @InterfaceStability.Evolving
  public static final class EnhancedOpenFileOptions {

    private EnhancedOpenFileOptions() {
    }

    /**
     * Prefix for all standard filesystem options: {@value}.
     */
    private static final String FILESYSTEM_OPTION = "fs.option.";

    /**
     * Prefix for all openFile options: {@value}.
     */
    public static final String FS_OPTION_OPENFILE =
        FILESYSTEM_OPTION + "openfile.";

    /**
     * OpenFile option for file length: {@value}.
     */
    public static final String FS_OPTION_OPENFILE_LENGTH =
        FS_OPTION_OPENFILE + "length";

    /**
     * OpenFile option for split start: {@value}.
     */
    public static final String FS_OPTION_OPENFILE_SPLIT_START =
        FS_OPTION_OPENFILE + "split.start";

    /**
     * OpenFile option for split end: {@value}.
     */
    public static final String FS_OPTION_OPENFILE_SPLIT_END =
        FS_OPTION_OPENFILE + "split.end";

    /**
     * OpenFile option for buffer size: {@value}.
     */
    public static final String FS_OPTION_OPENFILE_BUFFER_SIZE =
        FS_OPTION_OPENFILE + "buffer.size";

    /**
     * OpenFile option for read policies: {@value}.
     */
    public static final String FS_OPTION_OPENFILE_READ_POLICY =
        FS_OPTION_OPENFILE + "read.policy";

    /**
     * Set of standard options which openFile implementations
     * MUST recognize, even if they ignore the actual values.
     */
    public static final Set<String> FS_OPTION_OPENFILE_STANDARD_OPTIONS =
        Collections.unmodifiableSet(Stream.of(
                FS_OPTION_OPENFILE_BUFFER_SIZE,
                FS_OPTION_OPENFILE_READ_POLICY,
                FS_OPTION_OPENFILE_LENGTH,
                FS_OPTION_OPENFILE_SPLIT_START,
                FS_OPTION_OPENFILE_SPLIT_END)
            .collect(Collectors.toSet()));

    /**
     * Read policy for adaptive IO: {@value}.
     */
    public static final String FS_OPTION_OPENFILE_READ_POLICY_ADAPTIVE =
        "adaptive";

    /**
     * Read policy {@value} -whateve the implementation does by default.
     */
    public static final String FS_OPTION_OPENFILE_READ_POLICY_DEFAULT =
        "default";

    /**
     * Read policy for random IO: {@value}.
     */
    public static final String FS_OPTION_OPENFILE_READ_POLICY_RANDOM =
        "random";

    /**
     * Read policy for sequential IO: {@value}.
     */
    public static final String FS_OPTION_OPENFILE_READ_POLICY_SEQUENTIAL =
        "sequential";

    /**
     * Vectored IO API to be used: {@value}.
     */
    public static final String FS_OPTION_OPENFILE_READ_POLICY_VECTOR =
        "vector";

    /**
     * Whole file to be read, end-to-end: {@value}.
     */
    public static final String FS_OPTION_OPENFILE_READ_POLICY_WHOLE_FILE =
        "whole-file";

    /**
     * All the current read policies as a set.
     */
    public static final Set<String> FS_OPTION_OPENFILE_READ_POLICIES =
        Collections.unmodifiableSet(Stream.of(
                FS_OPTION_OPENFILE_READ_POLICY_ADAPTIVE,
                FS_OPTION_OPENFILE_READ_POLICY_DEFAULT,
                FS_OPTION_OPENFILE_READ_POLICY_RANDOM,
                FS_OPTION_OPENFILE_READ_POLICY_SEQUENTIAL,
                FS_OPTION_OPENFILE_READ_POLICY_VECTOR,
                FS_OPTION_OPENFILE_READ_POLICY_WHOLE_FILE)
            .collect(Collectors.toSet()));

  }
}
