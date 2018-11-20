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

public class OptionSets {

  /**
   * Not all of these are in CommonConfigurationKeysPublic of older
   * Hadoop versions, so they are inlined.
   */
  protected static final Object[][] SECURITY_OPTIONS = {
      {"hadoop.security.authentication", false, false},
      {"hadoop.security.authorization", false, false},
      {"hadoop.security.credential.provider.path", false, false},
      {"hadoop.security.credstore.java-keystore-provider.password-file", false, false},
      {"hadoop.security.credential.clear-text-fallback", false, false},
      {"hadoop.security.key.provider.path", false, false},
      {"hadoop.security.crypto.jceks.key.serialfilter", false, false},
      {"hadoop.http.authentication.simple.anonymous.allowed", false, false},
      {"hadoop.rpc.protection", false, false},
      {"dfs.data.transfer.protection", false, false},
      {"", false, false},
  };
  
  protected static final Object[][] CLUSTER_OPTIONS = {
      {"fs.defaultFS", false, false},
      {"fs.default.name", false, false},
      {"hdp.version", false, false},
      {"yarn.resourcemanager.address", false, false},
      {"yarn.resourcemanager.webapp.address", false, false},
      {"yarn.resourcemanager.webapp.https.address", false, false},
  };
  
  
}
