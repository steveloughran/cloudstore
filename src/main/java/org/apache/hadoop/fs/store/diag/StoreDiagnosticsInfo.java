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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Preconditions;

import org.apache.hadoop.conf.Configuration;

/**
 * Class for filesystems to implement to provide better diagnostics than the
 * default.
 */
public class StoreDiagnosticsInfo {

  protected static final Object[][] EMPTY_OPTIONS = {};

  protected static final Object[][] STANDARD_ENV_VARS = {
      {"PATH", false},
      {"HADOOP_HOME", false},
      {"HADOOP_OPTIONAL_TOOLS", false},
      {"HADOOP_SHELL_SCRIPT_DEBUG", false},
      {"HADOOP_TOOLS_HOME", false},
      {"HADOOP_TOOLS_OPTIONS", false},
      
      // TODO: add the https proxy vars
  };

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
  };
  
  protected static final String[] EMPTY_CLASSNAMES = {};

  protected static final List<URI> EMPTY_ENDPOINTS = new ArrayList<>(0);

  private final URI fsURI;

  public StoreDiagnosticsInfo(final URI fsURI) {
    this.fsURI = fsURI;
  }

  /**
   * Bind the diagnostics to a store.
   * @param fsURI filesystem URI
   * @return the diagnostics info provider.
   */
  public static StoreDiagnosticsInfo bindToStore(final URI fsURI) {
    StoreDiagnosticsInfo store;
    Preconditions.checkArgument(fsURI != null, "Null fsURI argument");
    switch (fsURI.getScheme()) {
    case "hdfs":
      store = new HDFSDiagnosticsInfo(fsURI);
      break;
    case "s3a":
      store = new S3ADiagnosticsInfo(fsURI);
      break;
    case "adl":
      store = new ADLDiagnosticsInfo(fsURI);
      break;
    case "wasb":
      store = new WasbDiagnosticsInfo(fsURI);
      break;
    case "abfs":
    case "abfss":
      store = new AbfsDiagnosticsInfo(fsURI);
      break;
    default:
      store = new StoreDiagnosticsInfo(fsURI);
    }
    return store;
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
   * List of options for filesystems. 
   * Each entry must be a tuple of (string, password, sensitive).
   * "password" entries are read via Configuration.getPassword(),
   * so will be read from a credential file.
   * Sensitive strings don't have their values fully printed.
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
  public Configuration patchConfigurationToInitalization(
      final Configuration conf) {
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
   * @return a possibly empty ist of endpoints for DNS lookup and HTTP
   * connections.
   */
  public List<URI> listEndpointsToProbe(Configuration conf)
      throws IOException {
    return EMPTY_ENDPOINTS;
  }

  /**
   * Look up an option; if not empty add it as a URI.
   * @param uris URI list to add to
   * @param conf config
   * @param key key to check
   * @param uriPrefix any prefix to add to build the URI, e.g "https:"
   * @return true iff there was a URI
   * @throws IOException parsing problem
   */
  protected boolean addUriOption(final List<URI> uris,
      final Configuration conf,
      final String key,
      final String uriPrefix) throws IOException {
    String endpoint = conf.getTrimmed(key, "");
    if (!endpoint.isEmpty()) {
        uris.add(StoreDiag.toURI(
            "From configuration key " + key,
            uriPrefix + endpoint));
        return true;
    } else {
      return false;
    }
  }

  /**
   * Override point: any store-specific config validation.
   * @param printout printer
   * @param conf
   * @throws IOException failure
   */
  protected void validateConfig(Printout printout,
      final Configuration conf) throws IOException {
    
  }

  public URI getFsURI() {
    return fsURI;
  }
}
