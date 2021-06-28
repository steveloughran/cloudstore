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

package org.apache.hadoop.fs.s3a.extra;

/**
 * Environment/property entry, with
 * methods to convert to a given format,
 */
public class EnvEntry {
  final String name;
  final String envVar;
  final String value;

  public EnvEntry(String name, String envVar, String value) {
    this.name = name;
    this.envVar = envVar;
    this.value = value;
  }
  
  public String xml() {
    return String.format("<%s>%n  %s%n</%s>%n", name, value, name);
  }

  public String property() {
    return String.format("%s=%s%n", name, value);
  }


  public String cliProperty() {
    return String.format("-D %s=%s ", name, value);
  }


  public String spark() {
    return String.format("spark.hadoop.\"%s\" %n", name, value);
  }

  public String bash() {
    return String.format("export %s=\"%s\"%n", envVar, value);
  }

  public String fish() {
    return String.format("set -gx %s \"%s\";%n", envVar, value);
  }

  public boolean hasEnvVar() {
    return !envVar.isEmpty();
  }
}
