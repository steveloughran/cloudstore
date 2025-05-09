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

import org.apache.hadoop.fs.store.commands.Command;

/**
 * Help command: list the public commands.
 */
public class help extends Command {

  /**
   * When adding new entries here, use alphabetical order.
   * @param args command line args
   */
  public static void main(String[] args) {
    println("Cloudstore");
    println("==========");
    println("");
    bandwidth.help();
    bulkdelete.help();
    cloudup.help();
    committerinfo.help();
    constval.help();
    distcpdiag.help();
    dux.help();
    fetchdt.help();
    filestatus.help();
    jobtokens.help();
    list.help();
    locatefiles.help();
    localhost.help();
    mkcsv.help();
    pathcapability.help();
    storediag.help();
    tarhardened.help();
    tlsinfo.help();

    // extras must not refer to the optional classes.
    println("");
    println("");
    println("AWS V2 SDK-only Commands");
    println("========================");

    println("%nRequires an S3A connector built with the V2 AWS SDK");
    println("");
    bucketmetadata.help();
    bucketstate.help();
    deleteobject.help();
    gcscreds.help();
    iampolicy.help();
    listobjects.help();
    listversions.help();
    mkbucket.help();
    regions.help();
    restore.help();
    sessionkeys.help();

    println("");
    println("See https://github.com/steveloughran/cloudstore");

  }
}
