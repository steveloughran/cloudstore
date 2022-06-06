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

import java.net.URI;

import org.apache.hadoop.conf.Configuration;

/**
 * This is just a template to use when creating diagnostics bindings.
 */
public class TemplateDiagnosticsInfo extends StoreDiagnosticsInfo {

  /**
   * Mandatory classnames.
   */
  public static final String[] classnames = {
      "com.example.mandatory",
  };

  /**
   *  Optional classnames.
   */
  public static final String[] optionalClassnames = {
      "",
  };

  /**
   * List of options for filesystems. 
   * Each entry must be a tuple of (string, password, sensitive).
   * "password" entries are read via Configuration.getPassword(),
   * so will be read from a credential file.
   * Sensitive strings don't have their values fully printed.
   */
  private static final Object[][] options = {

      {"fs.FS.something", false, false},
      {"fs.FS.secret", true, true},
  };

  public TemplateDiagnosticsInfo(final URI fsURI, final Printout output) {
    super(fsURI, output);
  }

  @Override
  public String getName() {
    return "NAME";
  }

  @Override
  public String getDescription() {
    return "Filesystem Connector to " + getName();
  }

  @Override
  public String getHomepage() {
    return "https://hadoop.apache.org/docs/current/index.html";
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
  public String[] getOptionalClassnames(final Configuration conf) {
    return optionalClassnames;
  }


}
