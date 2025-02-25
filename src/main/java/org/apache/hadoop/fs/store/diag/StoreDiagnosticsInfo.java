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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalDirAllocator;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.StoreUtils;

import static org.apache.hadoop.fs.store.StoreEntryPoint.DEFAULT_HIDE_ALL_SENSITIVE_CHARS;
import static org.apache.hadoop.fs.store.StoreEntryPoint.getOrigins;
import static org.apache.hadoop.fs.store.StoreUtils.sanitize;
import static org.apache.hadoop.fs.store.diag.OptionSets.STANDARD_SYSPROPS;
import static org.apache.hadoop.fs.store.diag.StoreDiag.sortKeys;

/**
 * Class for filesystems to implement to provide better diagnostics than the
 * default.
 */
public class StoreDiagnosticsInfo {

  protected static final Object[][] EMPTY_OPTIONS = {};

  protected static final String[] EMPTY_CLASSNAMES = {};

  protected static final String[] EMPTY_CAPABILITIES = {};

  protected static final String[] EMPTY_RESOURCES = {};

  protected static final String[] DEFAULT_OPTIONAL_RESOURCES = {
      "log4j.properties"
  };

  protected static final List<URI> EMPTY_ENDPOINTS = new ArrayList<>(0);

  private final URI fsURI;

  private final Printout output;

  public StoreDiagnosticsInfo(final URI fsURI, final Printout output) {
    this.fsURI = fsURI;
    this.output = output;
  }

  /**
   * Bind the diagnostics to a store.
   * @param fsURI filesystem URI
   * @param output
   * @return the diagnostics info provider.
   */
  public static StoreDiagnosticsInfo bindToStore(URI fsURI,
      final Printout output)
      throws IOException {
    StoreDiagnosticsInfo store;
    StoreUtils.checkArgument(fsURI != null, "Null fsURI argument");
    String scheme = fsURI.getScheme();
    if (scheme == null) {
      try {
        // no scheme. Switch to default FS.
        URI defaultUri = FileSystem.getDefaultUri(new Configuration());
        fsURI = new URI(defaultUri.getScheme(),
            defaultUri.getUserInfo(),
            defaultUri.getHost(),
            defaultUri.getPort(),
            fsURI.getPath(),
            fsURI.getQuery(),
            fsURI.getFragment());
        scheme = fsURI.getScheme();
      } catch (URISyntaxException e) {
        throw new IOException("Unable to build new URI from " + fsURI, e);
      }
    }
    switch (scheme) {
    case "hdfs":
    case "webhdfs":
      store = new HDFSDiagnosticsInfo(fsURI, output);
      break;
    case "s3a":
      store = new S3ADiagnosticsInfo(fsURI, output);
      break;
    case "adl":
    case "adls":
      store = new ADLDiagnosticsInfo(fsURI, output);
      break;
    case "wasb":
    case "wasbs":
      store = new WasbDiagnosticsInfo(fsURI, output);
      break;
    case "abfs":
    case "abfss":
      store = new AbfsDiagnosticsInfo(fsURI, output);
      break;
    case "gs":
    case "gcs":
      store = new GCSDiagnosticsInfo(fsURI, output);
      break;

    case "s3":
    case "s3n":
      // fail on s3n and s3a. Yes, this breaks AWS S3 connector. 
      // No, I don't care about this. Not my problem.
      // If the AWS team are going to take all of Hadoop and serve it
      // up without contributing code back, they get to do the same
      // for the diagnostics tools.
      throw new IllegalArgumentException("Store URI unsuppported: " + scheme);
    default:
      // any other FS: create the generic one
      store = new StoreDiagnosticsInfo(fsURI, output);
    }
    return store;
  }

  public Printout getOutput() {
    return output;
  }

  /**
   * Get the filesystem name.
   * @return FS name
   */
  public String getName() {
    return "Store for scheme " + getScheme();
  }

  /**
   * Get the FS Scheme.
   * @return scheme
   */
  public String getScheme() {
    return fsURI.getScheme();
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
    return OptionSets.STANDARD_ENV_VARS;
  }

  /**
   * List of system properties. Each entry must be a pair of
   * (string, sensitive); sensitive strings don't have their values
   * fully printed.
   * @return option array
   */
  public Object[][] getSelectedSystemProperties() {
    return STANDARD_SYSPROPS;
  }

  /**
   * should HTTPS/TLS binding info be printed?
   * default is true.
   * @return true for it to be logged.
   */
  public boolean printTLSInfo() {
    return true;
  }

  /**
   * Take the raw config and patch as the FS will have during
   * initialization.
   * This handles stores like S3A which do some per-bucket config.
   * @param conf initial configuration.
   * @return the configuration to work with.
   */
  public Configuration patchConfigurationToInitalization(
      final Configuration conf) throws IOException {
    return conf;
  }

  /**
   * Get the classnames.
   * @param conf config to use in determining their location.
   * @return a possibly empty list of required implementation classes.
   */
  public String[] getClassnames(final Configuration conf) {
    // look for an implementation
    String impl = conf.get("fs." + getScheme() + ".impl", "");
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
   * Get a list of resources to look for.
   * If any of these are not found, it is an error.
   * @param conf config to use in determining their location.
   * @return a possibly empty list of resources.
   */
  public String[] getRequiredResources(final Configuration conf) {
    return EMPTY_RESOURCES;
  }

  /**
   * Get a list of optional resources to look for.
   * If any of these are not found, it is not an error.
   * @param conf config to use in determining their location.
   * @return a possibly empty list of optional resources.
   */
  public String[] getOptionalResources(final Configuration conf) {
    return DEFAULT_OPTIONAL_RESOURCES;
  }

  /**
   * List the endpoints to probe for (auth, REST, etc).
   * @param conf configuration to use, will already have been patched.
   * @return a possibly empty ist of endpoints for DNS lookup and HTTP
   * connections.
   */
  public List<URI> listEndpointsToProbe(Configuration conf)
      throws IOException, URISyntaxException {
    return EMPTY_ENDPOINTS;
  }

  /**
   * List optional endpoints to probe; its not an error if these aren't
   * reachable.
   * @param conf configuration to use, will already have been patched.
   * @return a possibly empty ist of endpoints for DNS lookup and HTTP
   * connections.
   */
  public List<URI> listOptionalEndpointsToProbe(Configuration conf)
      throws IOException, URISyntaxException {
    return EMPTY_ENDPOINTS;
  }

  /**
   * Get a list of optional capablities to look for.
   * If any of these are not true.
   * @return a possibly empty list of path capabilites.
   */
  public String[] getOptionalPathCapabilites() {
    return EMPTY_CAPABILITIES;
  }

  /**
   * Look up an option; if not empty add it as a URI.
   * @param uris URI list to add to
   * @param conf config
   * @param key key to check
   * @param uriPrefix any prefix to add to build the URI, e.g "https:"
   * @param defVal
   * @return true iff there was a URI
   * @throws IOException parsing problem
   */
  protected boolean addUriOption(final List<URI> uris,
      final Configuration conf,
      final String key,
      final String uriPrefix,
      final String defVal) throws IOException {
    String endpoint = conf.getTrimmed(key, defVal);
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
   * @param writeOperations
   * @throws IOException failure
   */
  protected void validateConfig(Printout printout,
      final Configuration conf, final boolean writeOperations) throws IOException {

  }

  public URI getFsURI() {
    return fsURI;
  }

  /**
   * Warn if the Fs URI is in the wrong domain.
   * It's tempting to fail fast here to stop people missing it, but
   * there's a risk that people are using a custom domain and they
   * don't want a failure.
   * @param printout dest for messages
   * @param domain domain to expect.
   */
  protected void warnOnInvalidDomain(final Printout printout,
      final String domain,
      final String followupURL) {
    final String host = getFsURI().getHost();
    if (host == null) {
      printout.warn("The URL For this store doesn't have a valid host %s",
          getFsURI());
    } else if (!host.endsWith(domain)) {
      printout.warn("The URL for this store normally contains the domain %s,"
              + " but it is %s",
          domain, host);
      printout.warn("Unless you are using a private endpoint, this is NOT"
          + " GOING TO WORK");
      if (followupURL != null) {
        printout.warn("For more information, see: %s", followupURL);
      }
    }
  }

  /**
   * Print all options with a prefix.
   * Any option with ".secret" or ".pass" in them will be obfuscated.
   * @param printout where to print
   * @param conf config to read
   * @param prefix prefix to scan
   */
  protected void printPrefixedOptions(final Printout printout,
      final Configuration conf,
      final String prefix) {
    printout.heading("Configuration options with prefix %s", prefix);
    Map<String, String> propsWithPrefix = conf.getPropsWithPrefix(prefix);
    Set<String> sorted = sortKeys(propsWithPrefix.keySet());
    for (String key : sorted) {
      final String propertyVal = propsWithPrefix.get(key);
      final String propertyName = prefix + key;
      String value = "\"" + propertyVal +  "\"";
      if (propertyName.contains(".secret.") || propertyName.contains(".pass")) {
        value = sanitize(propertyVal, DEFAULT_HIDE_ALL_SENSITIVE_CHARS);
      }
      printout.println("%s=%s", propertyName, value);
    }
  }

  /**
   * Constructs a mapping of configuration and includes all properties that
   * start with the specified configuration prefix.  Property names in the
   * mapping are trimmed to remove the configuration prefix.
   * @param confPrefix configuration prefix
   * @return mapping of configuration properties with prefix stripped
   */
  public Map<String, String> getPropsWithPrefix(final Configuration conf,
      String confPrefix) {
    Map<String, String> configMap = new HashMap<>();
    for (Map.Entry<String, String> c : conf) {
      String name = c.getKey();
      if (name.startsWith(confPrefix)) {
        String value = conf.get(name);
        String keyName = name.substring(confPrefix.length());
        configMap.put(keyName, value);
      }
    }
    return configMap;
  }


  protected static void validateBufferDir(final Printout printout,
      final Configuration conf,
      final String bufferDirKey,
      final String fallbackDirKey,
      final boolean createTempFile) throws IOException {
    String bufferOption = conf.get(bufferDirKey) != null
        ? bufferDirKey : fallbackDirKey;

    printout.println("Buffer configuration option %s = %s",
        bufferOption, conf.get(bufferOption));
    final Collection<String> directories = conf.getTrimmedStringCollection(bufferOption);
    printout.println("Number of buffer directories: %d", directories.size());
    boolean failureLikely = false;
    int dirsToCreate = 0;
    Queue<String> dirQueue = new LinkedList<>(directories);
    while (!dirQueue.isEmpty()) {
      String directory = dirQueue.poll();
      final File dir = new File(directory);
      if (!dir.isAbsolute()) {
        printout.warn("Directory option %s is not absolute", dir);
        failureLikely = true;
        continue;
      }
      final File absDir = dir.getAbsoluteFile();
      printout.println("Buffer path %s:", absDir);
      if (!absDir.exists()) {
        printout.println("\t* does not exist: expect it to be created");
        dirsToCreate++;
        // scan the parent now too
        final File parentFile = absDir.getParentFile();
        if (parentFile != null && !parentFile.getCanonicalPath().equals("/")) {
          dirQueue.add(parentFile.getAbsolutePath());
        }
        continue;
      }
      if (!absDir.isDirectory()) {
        printout.warn("\t* is a file");
        failureLikely = true;
        continue;
      }
      if (!absDir.canWrite()) {
        printout.warn("\t* is not writable by the current user");
        failureLikely = true;
        continue;
      }
      // at this point the dir is good
      printout.println("\t* exists and is writable");
      // how much data is in it?
      long count = 0;
      long size = 0;
      for (File file : absDir.listFiles()) {
        if (file.isFile()) {
          count++;
          size += file.length();
        }
      }
      printout.println("\t* contains %d file(s) with total size %,d bytes", count, size);
    }

    if (failureLikely) {
      printout.warn("\nOutput buffer issues identified; data uploads may fail");
    }


    if (dirsToCreate > 0) {
      printout.println("Directories to be created: %d", dirsToCreate);
    }

    if (createTempFile) {
      printout.println("\nAttempting to create a temporary file");
      final LocalDirAllocator directoryAllocator = new LocalDirAllocator(
          bufferOption);

      File temp = directoryAllocator.createTmpFileForWrite("temp", 1, conf);

      printout.println("\nTemporary file successfully created in %s",
          temp.getParentFile());
      temp.delete();
    } else {
      printout.println("\nRerun storediag with the -w option to test write access to the store");
    }
  }

  /**
   * Any preflight checks of the filesystem config/url etc.
   * @param printout output
   * @param path path which will be used
   * @throws IOException failure.
   */
  public void preflightFilesystemChecks(Printout printout,
      Path path) throws IOException {

  }

  /**
   * Perform any validation of the filesystem itself (is it the right type,
   * are there any options you can check for. This is
   * called after the FS has been created, but before any read or
   * write IO has taken place.
   * Important: if you try to cast the FS for these checks, then if
   * the FS JARs aren't on the classpath, the specific diagnostics
   * info subclass will fail with a ClassNotFoundException. Do not cast,
   * at least not directly in that specific class.
   * @param printout dest for messages
   * @param path created filesystem
   * @param filesystem filesystem instance.
   */
  public void validateFilesystem(final Printout printout,
      final Path path,
      final FileSystem filesystem) throws IOException {

  }

  /**
   * Validate a file which has just been created.
   * @param printout output
   * @param filesystem fs
   * @param path path
   * @param status status of file at path
   * @throws IOException failure
   */
  public void validateFile(
      Printout printout,
      FileSystem filesystem,
      Path path,
      FileStatus status) throws IOException {

  }

  /**
   * Print any performance hints.
   * @param printout printout
   * @param conf config
   */
  protected void performanceHints(
      Printout printout,
      Configuration conf) {
  }

  /**
   * Provide a hint.
   * @param printout destination
   * @param condition condition to trigger the hint
   * @param text string to format
   * @param args varargs
   * @return true if the hint was displayed
   */
  protected boolean hint(
      final Printout printout,
      boolean condition,
      String text,
      Object... args) {
    if (condition) {
      printout.println(text, args);
      return true;
    }
    return false;
  }

  /**
   * Hint when the size is too low/unset.
   * @param printout destination
   * @param conf config to probe
   * @param option option to resolve
   * @param recommend recommended value
   * @return true if a hint was made
   */
  protected boolean sizeHint(
      final Printout printout,
      final Configuration conf,
      String option,
      long recommend) {

    // print a message if unset
    if (hint(printout,
        conf.get(option) == null,
        "Option %s is unset. Recommend a value of at least %d",
        option, recommend)) {
      return true;
    }
    // work out the origin
    String source = getOrigins(conf, option, "");
    // if set, check the value
    long val = conf.getLong(option, 0);

    return hint(printout,
        val < recommend,
        "Option %s (source %s) has value %d. Recommend a value of at least %d",
        option, source, val, recommend);
  }

  /**
   * Hint when the time is too low/unset, knowing that zero is a special
   * case which is not hinted on if {@code isZeroSpecial} is set.
   * <p>
   * Requires unset values to be milliseconds.
   * @param printout destination
   * @param conf config to probe
   * @param option option to resolve
   * @param recommendDuration recommended minimum duration.
   * @param isZeroSpecial is zero special?
   * @param description description to print.
   * @return true if a hint was made
   */
  protected boolean timeHint(
      final Printout printout,
      final Configuration conf,
      String option,
      Duration recommendDuration,
      boolean isZeroSpecial,
      String description) {
    long recommend = recommendDuration.toMillis();

    printout.println("\n%s: %s", option, description);
    String hintStr;
    if (isZeroSpecial) {
      hintStr = String.format("Recommend a value of 0 or at least %dms", recommend);
    } else {
      hintStr = String.format("Recommend a value of at least %dms", recommend);
    }
    // print a message if unset
    if (hint(printout,
        conf.get(option) != null,
        "Option %s is unset. Recommend a value of at least %dms",
        option, recommend)) {
      return true;
    }
    // if set, check the value
    long val = conf.getTimeDuration(option, 0, TimeUnit.MILLISECONDS);
    // work out the origin
    String source = getOrigins(conf, option, "");
    boolean shouldRecommend;
    if (isZeroSpecial) {
      shouldRecommend = val > 0 && val < recommend;
    } else {
      shouldRecommend = val < recommend;
    }
    return hint(printout,
        shouldRecommend,
        "Option %s has value %d (source %s). %s",
        option, val, source, hintStr);
  }

  /**
   * Validate an output stream of a file being written to.
   * @param printout printout
   * @param fs fs
   * @param file path to file
   * @param data output stream
   * @throws IOException IO failure
   */
  public void validateOutputStream(
      final Printout printout,
      final FileSystem fs,
      final Path file,
      final FSDataOutputStream data) throws IOException {

  }


  /**
   * Validate an open input stream.
   * The stream is the wrapped stream from FSDataInputStream;
   * its type can be validated, cast to the fs-specific value, etc.
   * @param printout printout
   * @param fs filesystem
   * @param file filename
   * @param in wrapped stream from FSDataInputStream
   */
  void validateInputStream(final Printout printout,
      final FileSystem fs,
      final Path file,
      final InputStream in) {

  }

  /**
   * should a deep tree list take place?
   * @return false by default
   */
  public boolean deepTreeList() {
    return false;
  }

}
