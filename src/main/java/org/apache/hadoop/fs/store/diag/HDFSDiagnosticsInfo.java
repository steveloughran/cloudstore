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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;

/**
 * HDFS Diagnostics.
 * DO NOT IMPORT ANY HDFS CLASSES.
 */
public class HDFSDiagnosticsInfo extends StoreDiagnosticsInfo {

  private static final Logger LOG = LoggerFactory.getLogger(
      HDFSDiagnosticsInfo.class);

  private static final Object[][] options = {
      {"fs.defaultFS", false, false},
      {"hadoop.security.authentication", false, false},
      {"hadoop.security.authorization", false, false},
      {"hadoop.rpc.protection", false, false},
      {"dfs.client.cache.drop.behind.writes", false, false},
      {"dfs.client.cache.drop.behind.reads", false, false},
      {"dfs.client.cache.readahead", false, false},
      {"dfs.client.context", false, false},
      {"dfs.client.domain.socket.data.traffic", false, false},
      {"dfs.client.failover.max.attempts", false, false},
      {"dfs.client.failover.sleep.base.millis", false, false},
      {"dfs.client.failover.sleep.max.millis", false, false},
      {"dfs.client.failover.connection.retries", false, false},
      {"dfs.client.failover.connection.retries.on.timeouts", false, false},
      {"dfs.client.local.interface", false, false},
      {"dfs.client.mmap.enabled", false, false},
      {"dfs.client.mmap.cache.size", false, false},
      {"dfs.client.mmap.cache.timeout.ms", false, false},
      {"dfs.client.mmap.retry.timeout.ms", false, false},
      {"dfs.client.read.shortcircuit", false, false},
      {"dfs.client.read.shortcircuit.skip.checksum", false, false},
      {"dfs.client.read.shortcircuit.streams.cache.size", false, false},
      {"dfs.client.read.shortcircuit.streams.cache.expiry.ms", false, false},
      {"dfs.client.retry.max.attempts", false, false},
      {"dfs.client.retry.policy.enabled", false, false},
      {"dfs.client.retry.policy.spec", false, false},
      {"dfs.client.retry.window.base", false, false},
      {"dfs.client.short.circuit.replica.stale.threshold.ms", false, false},
      {"dfs.client.slow.io.warning.threshold.ms", false, false},
      {"dfs.client.socket.send.buffer.size", false, false},
      {"dfs.client.socket-timeout", false, false},
      {"dfs.client-write-packet-size", false, false},
      {"dfs.client.use.datanode.hostname", false, false},
      {"dfs.client.use.legacy.blockreader.local", false, false},
      {"dfs.client.write.exclude.nodes.cache.expiry.interval.millis", false, false},
      {"dfs.datanode.kerberos.principal", false, false},
      {"dfs.data.transfer.protection", false, false},
      {"dfs.data.transfer.saslproperties.resolver.class", false, false},
      {"dfs.http.policy", false, false},
      {"dfs.namenode.acls.enabled", false, false},
      {"dfs.namenode.rpc-address", false, false},
      {"dfs.namenode.http-address", false, false},
      {"dfs.webhdfs.socket.connect-timeout", false, false},
      {"dfs.webhdfs.socket.read-timeout", false, false},
      {"", false, false},
  };

  public static final String[] classnames = {
      "org.apache.hadoop.hdfs.HdfsConfiguration",
      "org.apache.hadoop.ipc.RPC",
      "org.apache.hadoop.security.UserGroupInformation",
      "org.apache.hadoop.hdfs.protocolPB.ClientNamenodeProtocolTranslatorPB",
      "org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos",
      "com.google.protobuf.Message",
      "com.google.protobuf.ExtensionRegistry",
  };

  public HDFSDiagnosticsInfo(final URI fsURI) {
    super(fsURI);
  }

  /**
   * Patch by creating an HDFS Configuration instance; this will ensure
   * hdfs-site.xml is picked up.
   * Uses reflection so that there's no explicit import of any HDFS classes.
   * @param conf initial configuration.
   * @return an HDFS config
   */
  @Override
  public Configuration patchConfigurationToInitalization(final Configuration conf)
      throws IOException {
    try {
      ClassLoader loader = this.getClass().getClassLoader();
      Class<?> clazz = loader.loadClass(
          "org.apache.hadoop.hdfs.HdfsConfiguration");
      Constructor<?> ctor = clazz.getConstructor();
      return (Configuration) ctor.newInstance();
    } catch (ClassNotFoundException
            | ClassCastException
            | NoSuchMethodException
            | IllegalAccessException
            | InvocationTargetException
            | InstantiationException e) {
      LOG.warn("Problem finding/loading/creating HdfsConfiguration class", e);
      throw new IOException(e);
    }
  }

  @Override
  public String getName() {
    return "HDFS";
  }

  @Override
  public String getDescription() {
    return "Hadoop HDFS Filesystem";
  }

  @Override
  public String getHomepage() {
    return "https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-hdfs/HdfsUserGuide.html";
  }

  @Override
  public Object[][] getFilesystemOptions() {
    return options;
  }

  @Override
  public String[] getClassnames(final Configuration conf) {
    return classnames;
  }

  @Override
  public List<URI> listEndpointsToProbe(final Configuration conf)
      throws IOException {
    List<URI> uris = new ArrayList<>(2);
    boolean isHttps = conf.getBoolean("dfs.http.policy", false);
    if (isHttps) {
      addUriOption(uris, conf, "dfs.namenode.https-address", "https://");
    } else {
      addUriOption(uris, conf, "dfs.namenode.http-address", "http://");
    }
    return uris;
  }

}
