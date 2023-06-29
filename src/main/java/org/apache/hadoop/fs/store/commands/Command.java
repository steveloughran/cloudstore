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

package org.apache.hadoop.fs.store.commands;

/**
 * This is the base class for the minimal CLI commands.
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class Command {

  public static void println(String format, Object... args) {
    System.out.printf(format, args);
    System.out.println();
  }

  protected static void printCommand(String name, String function) {
    System.out.printf("%s\t--\t%s%n", name, function);
  }

}
