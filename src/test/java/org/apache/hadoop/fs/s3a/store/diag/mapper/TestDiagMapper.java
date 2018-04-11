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

package org.apache.hadoop.fs.s3a.store.diag.mapper;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;

/**
 * Test the diagnostics mapper.
 * TODO: fix up CP, implement tests
 */
public class TestDiagMapper {

  private static final Logger LOG = LoggerFactory.getLogger(
      TestDiagMapper.class);

/*
  private static MiniMRClientCluster mrcluster;

  @BeforeClass
  public void setupClass() throws Exception {
    mrcluster = MiniMRClientClusterFactory(TestDiagMapper.class,
        1,
        new Configuration());
    mrcluster.start();
  }

  @AfterClass
  public void teardownClass() throws Exception {
    if (mrcluster != null) {
      try {
        mrcluster.stop();
      } catch (Exception e) {
        LOG.warn("When closing MR cluster", e);
      }
    }
  }
*/
}
