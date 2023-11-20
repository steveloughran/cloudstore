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

package org.apache.hadoop.fs.s3a.sdk2;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.providers.AwsProfileRegionProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.regions.providers.InstanceProfileRegionProvider;
import software.amazon.awssdk.regions.providers.SystemSettingsRegionProvider;

import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

/**
 * Debug region settings; v2 sdk
 */
public class Regions2 extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(Regions2.class);

  public static final String USAGE
      = "Usage: regions";

  private String foundRegion = null;

  private String regionProvider = null;

  public Regions2() {
    createCommandFormat(0, 0);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (!paths.isEmpty()) {
      errorln(USAGE);
      return E_USAGE;
    }

    heading("Determining AWS region for SDK clients");
    println("This uses same region resolution chain and ordering as in the AWS client.");
    println("See "
        + "https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-region-selection.html");

    printRegion("SystemSettingsRegionProvider",
        new SystemSettingsRegionProvider(),
        "System property aws.region");
    printRegion("AwsProfileRegionProvider",
        new AwsProfileRegionProvider(),
        "Region info in ~/.aws/config");
    printRegion("InstanceMetadataRegionProvider",
        new InstanceProfileRegionProvider(),
        "EC2 metadata; will only work in AWS infrastructure");
    if (foundRegion == null) {
      heading("Region was NOT FOUND");
      warn("AWS region was not determined through SDK region chain");
      warn("This may not work");
      return 50;  // 500 server error
    } else {

      // all good
      heading("Region found: \"%s\"", foundRegion);
      println("Region was determined by %s as  \"%s\"", regionProvider, foundRegion);
    }
    return 0;
  }

  private boolean printRegion(String name,
      AwsRegionProvider provider,
      String comment) {
    heading("Determining region using %s", name);
    println("%s", comment);
    String region = null;
    try (StoreDurationInfo ignored = new StoreDurationInfo(LOG,
        "%s.getRegion()", name)) {
      region = provider.getRegion().id();
    } catch (Exception e) {
      warn("Provider raised an exception %s", e);
    }
    if (region != null) {
      println("Region is determined as \"%s\"", region);
      if (foundRegion == null) {
        foundRegion = region;
        regionProvider = name;
      }
      return true;
    } else {
      println("region is not known");
      return false;
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
    return ToolRunner.run(new Regions2(), args);
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
