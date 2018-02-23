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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.conf.Configuration;

class StoreDiagnosticsInfo {

  protected static final Object[][] EMPTY_OPTIONS = {};

  protected static final Object[][] STANDARD_ENV_VARS = {
      {"PATH", false},
  };

  protected static final String[] EMPTY_CLASSNAMES = {};

  protected static final List<URI> EMPTY_ENDPOINTS = new ArrayList<>(0);

  protected final URI fsURI;

  public StoreDiagnosticsInfo(final URI fsURI) {
    this.fsURI = fsURI;
  }

  /**
   * Get the filesystem name.
   * @return FS name
   */
  public String getName() {
    return "Store for scheme " + fsURI.getScheme();
  }

  /**
   * Any extra description.
   * @return text
   */
  public String getDescription() {
    return "";
  }

  /**
   * Any home page of the filesystem.
   * @return a string to turn into a URL if not empty.
   */
  public String getHomepage() {
    return "";
  }

  /**
   * List of options for filesystems. Each entry must be a pair of
   * (string, sensitive); sensitive strings don't have their values
   * fully printed.
   * @return option array
   */
  public Object[][] getFilesystemOptions() {
      return EMPTY_OPTIONS;
  }

  /**
   * List of env vars for filesystems. Each entry must be a pair of
   * (string, sensitive); sensitive strings don't have their values
   * fully printed.
   * @return option array
   */
  public Object[][] getEnvVars() {
      return STANDARD_ENV_VARS;
  }

  /**
   * Take the raw config and patch as the FS will have during
   * initialization.
   * This handles stores like S3A which do some per-bucket config.
   * @param conf initial configuration.
   * @return the configuration to work with.
   */
  public Configuration patchConfigurationToInitalization(final Configuration conf) {
    return conf;
  }

  /**
   * Get the classnames.
   * @param conf config to use in determining their location.
   * @return a possibly empty list of required implementation classes.
   */
  public String[] getClassnames(final Configuration conf) {
    // look for an implementation
    String impl = conf.get("fs." + fsURI.getScheme() + ".impl", "");
    if (!impl.isEmpty()) {
      String[] r = new String[1];
      r[0] = impl;
      return r;
    } else {
      return EMPTY_CLASSNAMES;
    }
  }

  /**
   * Get a list of optional classes to look for.
   * If any of these are not found, it is not an error.
   * @param conf config to use in determining their location.
   * @return a possibly empty list of optional implementation classes.
   */
  public String[] getOptionalClassnames(final Configuration conf) {
    return EMPTY_CLASSNAMES;
  }

  /**
   * List the endpoints to probe for (auth, REST, etc).
   * @param conf configuration to use, will already have been patched.
   * @return a possibly empty ist of endpoints for DNS lookup then HTTP connect to.
   */
  public List<URI> listEndpointsToProbe(Configuration conf)
      throws URISyntaxException {
    return EMPTY_ENDPOINTS;
  }

  /**
   * Look up an option; if not empty add it as a URI.
   * @param uris URI list to add to
   * @param conf config
   * @param key key to check
   * @param uriPrefix any prefix to add to build the URI, e.g "https:"
   * @return true iff there was a URI
   * @throws URISyntaxException parsing problem
   */
  protected boolean addUriOption(final List<URI> uris, final Configuration conf,
      final String key,
      final String uriPrefix) throws URISyntaxException {
    String endpoint = conf.getTrimmed(key, "");
    if (!endpoint.isEmpty()) {
      try {
        uris.add(new URI(uriPrefix + endpoint));
        return true;
      } catch (URISyntaxException e) {
        throw new URISyntaxException(endpoint,
            String.format("From configuration key %s: %s",
                key, e.getMessage()));

      }
    } else {
      return false;
    }
  }
}
