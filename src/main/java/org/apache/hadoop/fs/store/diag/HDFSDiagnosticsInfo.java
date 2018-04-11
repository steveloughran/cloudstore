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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.HdfsConfiguration;

public class HDFSDiagnosticsInfo extends StoreDiagnosticsInfo {

  private static final Object[][] options = {
      {"dfs.namenode.kerberos.principal", false},
      {"dfs.datanode.kerberos.principal", false},
      {"dfs.http.policy", false},
      {"hadoop.security.authentication", false},
      {"hadoop.security.authorization", false},
      {"hadoop.rpc.protection", false},
  };

  public static final String[] classnames = {
      "org.apache.hadoop.hdfs.HdfsConfiguration",
      "org.apache.hadoop.ipc.RPC",
      "org.apache.hadoop.security.UserGroupInformation"
  };

  public HDFSDiagnosticsInfo(final URI fsURI) {
    super(fsURI);
  }

  /**
   * Patch by creating an HDFS Configuration instance; this will ensure
   * hdfs-site.xml is picked up.
   * @param conf initial configuration.
   * @return an HDFS config
   */
  @Override
  public Configuration patchConfigurationToInitalization(final Configuration conf) {
    return new HdfsConfiguration(conf);
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
