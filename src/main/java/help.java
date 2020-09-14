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
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    bucketstate.help();
    cloudup.help();
    committerinfo.help();
    distcpdiag.help();
    fetchdt.help();
    filestatus.help();
    list.help();
    listobjects.help();
    locatefiles.help();
    mkdir.help();
    pathcapability.help();
    storediag.help();

    // extras must not refer to the optional classes.
    println("");
    println("");
    println("Extra Commands");
    println("==============");
    println("");
    printCommand("cleans3guard", "Clean all s3guard entries");
    printCommand("iampolicy", "generate IAM policy");
    printCommand("sessionkeys", "optional extra: generate session keys");
    println(
        "%nThese are only available on some builds and require a compatible hadoop release");

  }
}
