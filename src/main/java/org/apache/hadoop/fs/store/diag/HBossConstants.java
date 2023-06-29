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

/**
 * HBoss support.
 * From {@code org.apache.hadoop.hbase.oss.Constants}
 */
public class HBossConstants {
  public static final String DATA_URI = "fs.hboss.data.uri";
  public static final String SYNC_IMPL = "fs.hboss.sync.impl";

  public static final String ZK_CONN_STRING = "fs.hboss.sync.zk.connectionString";
  public static final String ZK_BASE_SLEEP_MS = "fs.hboss.sync.zk.sleep.base.ms";
  public static final String ZK_MAX_RETRIES = "fs.hboss.sync.zk.sleep.max.retries";


  public static final String WAIT_INTERVAL_WARN = "fs.hboss.lock-wait.interval.warning";

  public static final String CAPABILITY_HBOSS =
      "org.apache.hadoop.hbase.hboss";

}
