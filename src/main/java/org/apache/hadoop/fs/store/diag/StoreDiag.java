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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.fs.shell.CommandFormat;
import org.apache.hadoop.fs.store.DurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.diag.StoreDiagnosticsInfo.SECURITY_OPTIONS;
import static org.apache.hadoop.util.VersionInfo.*;
import static org.apache.hadoop.fs.store.StoreExitCodes.*;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class StoreDiag extends StoreEntryPoint
  implements Printout {

  private static final Logger LOG = LoggerFactory.getLogger(StoreDiag.class);

  private static final String HELLO = "Hello";

  protected static final int THRESHOLD = 4;

  public static final String CLASSPATH = "java.class.path";

  public static final String TOKENFILE = "tokenfile";

  public static final String XMLFILE = "xmlfile";

  protected CommandFormat commandFormat;

  static final String USAGE = "Usage: StoreDiag <filesystem>";

  private StoreDiagnosticsInfo storeInfo;


  public StoreDiag() {
     commandFormat = new CommandFormat(0, Integer.MAX_VALUE);
     commandFormat.addOptionWithValue(TOKENFILE);
     commandFormat.addOptionWithValue(XMLFILE);
  }

  /**
   * Print all JVM options.
   */
  private void printJVMOptions() {
    heading("System Properties");
    Properties sysProps = System.getProperties();
    for (String s : sortKeys(sysProps.keySet())) {
      if (CLASSPATH.equals(s)) {
        continue;
      }
      println("%s = \"%s\"", s, sysProps.getProperty(s));
    }
  }

  /**
   * Print the JARS -but only the JARs, not the paths to them. And
   * sort the list.
   */
  private void printJARS() {
    heading("JAR listing");
    final Map<String, String> jars = jarsOnClasspath();
    for (String s : sortKeys(jars.keySet())) {

      File f = new File(jars.get(s));
      String size = f.exists() ?
          String.format("%,d bytes", f.length())
          : "missing";
      
      println("%s\t%s (%s)", s, jars.get(s), size);
    }
  }
  
  /**
   * Sort the keys.
   * @param keys keys to sort.
   * @return new set of sorted keys
   */
  private Set<String> sortKeys(final Iterable<?> keys) {
    TreeSet<String> sorted = new TreeSet<>();
    for (Object k : keys) {
      sorted.add(k.toString());
    }
    return sorted;
  }

  /**
   * Print the environment variables.
   * This is an array of (name, obfuscate) entries.
   * @param vars variables.
   */
  public void printEnvVars(Object[][] vars) {
    if (vars.length > 0) {
      heading("Environment Variables");
      for (final Object[] option : vars) {
        String var = (String) option[0];
        String value = System.getenv(var);
        if (value != null) {
          value = maybeSanitize(value, (Boolean) option[1]);
        } else {
          value = "(unset)";
        }
        println("%s = %s", var, value);
      }
    }
  }

  /**
   * Print the selected options in a config.
   * This is an array of (name, secret, obfuscate) entries.
   * @param title heading to print
   * @param conf source configuration
   * @param options map of options
   */
  @Override
  public void printOptions(String title, Configuration conf,
      Object[][] options)
      throws IOException {
    if (options.length > 0) {
      heading(title);
      for (final Object[] option : options) {
        printOption(conf,
            (String) option[0],
            (Boolean) option[1],
            (Boolean) option[2]);
      }
    }
  }

  /**
   * Sanitize a value if needed.
   * @param value option value.
   * @param obfuscate should it be obfuscated?
   * @return string safe to log; in quotes
   */
  @Override
  public String maybeSanitize(String value, boolean obfuscate) {
    return obfuscate ? sanitize(value) : 
        ("\"" + value + "\"");
  }

  /**
   * Sanitize a sensitive option.
   * @param value option value.
   * @return sanitized value.
   */
  public String sanitize(final String value) {
    String safe = value;
    int len = safe.length();
    if (len > THRESHOLD) {
      StringBuilder b = new StringBuilder(len);
      b.append(value.charAt(0));
      for (int i = 1; i < len - 1; i++) {
        b.append('*');
      }
      b.append(value.charAt(len - 1));
      safe = b.toString();
    } else {
      // short values get special treatment
      safe = "**";
    }
    return String.format("\"%s\" [%d]", safe, len);
  }

  /**
   * Retrieve and print an option.
   * Secrets are looked for through Configuration.getPassword(),
   * rather than the simpler get(option).
   * They are also sanitized in printing, so as to keep the secrets out
   * of bug reports.
   * @param conf source configuration
   * @param key key
   * @param secret is it secret?
   * @param obfuscate should it be obfuscated?
   */
  @Override
  public void printOption(Configuration conf,
      final String key,
      final boolean secret,
      final boolean obfuscate)
      throws IOException {
    if (key.isEmpty()) {
      return;
    }
    String source = "";
    String option;
    if (secret) {
      final char[] password = conf.getPassword(key);
      if (password != null) {
        option = new String(password).trim();
        source = "<credentials>";
      } else {
        option = null;
      }
    } else {
      option = conf.get(key);
    }
    String full;
    if (option == null) {
      full = "(unset)";
    } else {
      option = maybeSanitize(option, obfuscate);
      String[] origins = conf.getPropertySources(key);
      if (origins != null && origins.length !=0) {
        source = "[" + StringUtils.join(",", origins) + "]";
      }
      full = option + " " + source;
    }
    println("%s = %s", key, full);
  }

  @Override
  public final int run(String[] args) throws Exception {
    return run(args, System.out);
  }

  public int run(String[] args, PrintStream stream) throws Exception {
    setOut(stream);
    List<String> paths = parseArgs(args);
    if (paths.size() != 1) {
      errorln(USAGE);
      return E_USAGE;
    }

    // process the options
    String tokenfile = getOption(TOKENFILE);
    if (tokenfile != null) {
      heading("Adding tokenfile %s", tokenfile);
      Credentials credentials = Credentials.readTokenStorageFile(
          new File(tokenfile), getConf());
      println("Loaded tokens");
      Collection<Token<? extends TokenIdentifier>> tokens
          = credentials.getAllTokens();
      for (Token<? extends TokenIdentifier> token : tokens) {
        println(token.toString());
      }
      UserGroupInformation.getCurrentUser().addCredentials(credentials);
    }

    // path on the CLI
    String pathString = paths.get(0);
    if (!pathString.endsWith("/")) {
      pathString = pathString + "/";
    }
    Path path = new Path(pathString);


    // and its FS URI
    storeInfo = bindToStore(path.toUri());
    printHadoopVersionInfo();
    printJVMOptions();
    printJARS();
    printEnvVars(storeInfo.getEnvVars());
    printStoreConfiguration();
    probeRequiredAndOptionalClasses();
    storeInfo.validateConfig(this, getConf());
    probeAllEndpoints();

    // and the filesystem operations
    executeFileSystemOperations(path, true);

    // Validate parameters.
    return E_SUCCESS;
  }

  /**
   * Probe all the endpoints.
   * @throws IOException IO Failure
   */
  public void probeAllEndpoints() throws IOException  {
    heading("Endpoints");

    probeEndpoints(storeInfo.listEndpointsToProbe(getConf()));
  }

  /**
   * Print the base configuration of the store.
   */
  public void printStoreConfiguration() throws IOException {


    final Configuration conf = getConf();
    printOptions("Security Options", conf, SECURITY_OPTIONS);
    printOptions("Selected Configuration Options",
        conf, storeInfo.getFilesystemOptions());

  }

  /**
   * Bind the diagnostics to a store.
   * @param fsURI filesystem
   * @return the store's diagnostics.
   */
  public StoreDiagnosticsInfo bindToStore(final URI fsURI) {
    heading("Diagnostics for filesystem %s", fsURI);

    StoreDiagnosticsInfo store = StoreDiagnosticsInfo.bindToStore(fsURI);

    println("%s%n%s%n%s",
        store.getName(), store.getDescription(), store.getHomepage());

    setConf(store.patchConfigurationToInitalization(getConf()));
    return store;
  }

  /**
   * Probe the list of endpoints.
   * @param endpoints list to probe (unauthed)
   * @throws IOException  IO Failure
   */
  public void probeEndpoints(final List<URI> endpoints)
      throws IOException {
    if (endpoints.isEmpty()) {
      println("No endpoints determined for this filesystem");
    } else {
      println("Attempting to list and connect to public service endpoints,");
      println("without any authentication credentials.");
      println("%nThis is just testing the reachability of the URLs.");

      println("%nIf the request fails with any network error it is likely%n"
          + "to be configuration problem with address, proxy, etc%n");

      println("If it is some authentication error, then don't worry so much%n"
          + "-look for the results of the filesystem operations");

      for (URI endpoint : endpoints) {
        probeOneEndpoint(endpoint);
      }
    }
  }

  /**
   * Look at optional classes.
   * @param optionalClasses list of optional classes; may be null.
   * @return true if 1+ class was missing.
   */
  public boolean probeOptionalClasses(final String[] optionalClasses) {
    if (optionalClasses.length > 0) {
      heading("Optional Classes");

      println("These classes are needed in some versions of Hadoop.");
      println("And/or for optional features to work.");
      println("");

      boolean missing = false;
      for (String classname : optionalClasses) {
        missing |= probeOptionalClass(classname);
      }
      if (missing) {
        println("%nAt least one optional class was missing"
            + " -the filesystem client *may* still work");
      }
      return missing;
    } else {
      return false;
    }
  }

  public void probeRequiredClasses(final String[] requiredClasses)
      throws ClassNotFoundException {
    if (requiredClasses.length > 0) {
      heading("Required Classes");
      println("All these classes must be on the classpath");
      println("");
      for (String classname : requiredClasses) {
        probeRequiredClass(classname);
      }
    }
  }

  public void printHadoopVersionInfo() {
    heading("Hadoop information");
    println("  Hadoop %s", getVersion());
    println("  Compiled by %s on %s", getUser(), getDate());
    println("  Compiled with protoc %s", getProtocVersion());
    println("  From source with checksum %s", getSrcChecksum());
  }

  /**
   * Probe one endpoint, print proxy values, etc. No auth.
   * Ignores "0.0.0.0" addresses as they are silly.
   * @param endpoint endpoint
   * @throws IOException network problem
   */
  public void probeOneEndpoint(URI endpoint)
      throws IOException {
    final String host = endpoint.getHost();

    heading("Endpoint: %s", endpoint);
    InetAddress addr = InetAddress.getByName(host);
    println("Canonical hostname %s%n  IP address %s",
        addr.getCanonicalHostName(),
        addr.getHostAddress());
    if ("0.0.0.0".equals(host)) {
      return;
    }
    URL url = endpoint.toURL();

    List<Proxy> proxies = ProxySelector.getDefault()
        .select(toURI(url));
    if (proxies.isEmpty() ||
        Proxy.Type.DIRECT == proxies.get(0).type()) {
      println("Proxy: none");
    } else {
      println("Proxy defined:");
      for (Proxy proxy : proxies) {
        println("   %s", proxy);
      }
    }
    println("%nConnecting to %s%n", url);


    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.connect();
    int responseCode = conn.getResponseCode();
    String responseMessage = conn.getResponseMessage();
    println("Response: %d : %s", responseCode, responseMessage);
    boolean success = responseCode == 200;
    println("HTTP response %d from %s: %s",
        responseCode, url, responseMessage);
    println("Using proxy: %s ", conn.usingProxy());
    Map<String, List<String>> headerFields = conn.getHeaderFields();
    if (headerFields != null) {
      for (String header : headerFields.keySet()) {
        println("%s: %s", header,
            StringUtils.join(",", headerFields.get(header)));
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    IOUtils.copyBytes(
        success ? conn.getInputStream() : conn.getErrorStream(),
        out, 4096, true);
    String body = out.toString();
    println("%n%s%n",
        body.substring(0, Math.min(1024, body.length())));
    if (success) {
      println("WARNING: this unauthenticated operation was not rejected.%n "
          + "This may mean the store is world-readable.%n "
          + "Check this by pasting %s into your browser", url);
    }
  }


  /**
   * Probe all the required classes.
   * @throws ClassNotFoundException no class
   */
  public void probeRequiredAndOptionalClasses() throws ClassNotFoundException {
    probeRequiredClasses(storeInfo.getClassnames(getConf()));
    probeOptionalClasses(storeInfo.getOptionalClassnames(getConf()));
  }


  /**
   * Look for a class; print its origin.
   * @param classname classname
   * @throws ClassNotFoundException if the class was not found.
   */
  public void probeRequiredClass(final String classname)
      throws ClassNotFoundException {
    if (classname.isEmpty()) {
      return;
    }
    println("class: %s", classname);
    Class<?> clazz = this.getClass().getClassLoader().loadClass(classname);
    println("       %s", clazz.getProtectionDomain().getCodeSource().getLocation());
  }

  /**
   * Look for a class; print its origin if found, else print the
   * fact that it is missing.
   * @param classname classname
   */
  public boolean probeOptionalClass(final String classname) {
    try {
      probeRequiredClass(classname);
      return true;
    } catch (ClassNotFoundException e) {
      println("       Not found on classpath: %s", classname);
      return false;
    }
  }

  /**
   * Execute the FS level operations, one by one.
   */
  public void executeFileSystemOperations(final Path path,
      final boolean attempWriteOperations) throws IOException {
    final Configuration conf = getConf();
    heading("Test filesystem %s", path);

    println("This call tests a set of operations against the filesystem");
    println("Starting with some read operations, then trying to write%n");

    FileSystem fs;

    try(DurationInfo ignored = new DurationInfo(
        LOG, "Creating filesystem")) {
      fs = path.getFileSystem(conf);
    }

    println("%s", fs);


    Path root = fs.makeQualified(new Path("/"));
    try (DurationInfo ignored = new DurationInfo(LOG,
        "GetFileStatus %s", root)) {
      println("root entry %s", fs.getFileStatus(root));
    }

    FileStatus rootFile = null;
    try (DurationInfo ignored = new DurationInfo(LOG,
        "Listing  %s", root)) {
      FileStatus[] statuses = fs.listStatus(root);
      println("%s root entry count: %d", root, statuses.length);
      for (FileStatus status : statuses) {
        if (status.isFile() && rootFile == null) {
          rootFile = status;
        }
      }
    }

    if (rootFile != null) {
      // found a file to read
      Path rootFilePath = rootFile.getPath();
      FSDataInputStream in = null;
      try (DurationInfo ignored = new DurationInfo(LOG,
          "Reading file %s", rootFilePath)) {
        in = fs.open(rootFilePath);
        // read the first char or -1
        int c = in.read();
        println("First character of file %s is 0x%02x: '%s'",
            rootFilePath,
            c,
            c > 32? Character.toString((char)c) : "(n/a)");
        in.close();
      } finally {
        IOUtils.closeStream(in);
      }
    }

    // now work with the full path
    try(DurationInfo ignored = new DurationInfo(LOG,
        "Listing directory %s", path)) {
      FileStatus status = fs.getFileStatus(path);
      if (status.isFile()) {
        throw new IOException("Not a directory: " + status);
      }

      RemoteIterator<LocatedFileStatus> files = fs.listFiles(path, false);
      while (files.hasNext()) {
        status = files.next();
        if (status.isFile()) {
          println("%s: is a file of size %d", status.getPath(),
              status.getLen());
        } else {
          println("%s: is a directory", status.getPath());

        }
      }
    } catch (FileNotFoundException e) {
      // this is fine.
    }

    if (!attempWriteOperations) {
      return;
    }

    // play with security
    boolean securityEnabled = UserGroupInformation.isSecurityEnabled();
    if (securityEnabled) {

      println("Security is enabled, user is %s",
          UserGroupInformation.getCurrentUser().getUserName());
    } else {
      println("Security is disabled");
    }
    String serviceName = fs.getCanonicalServiceName();
    if (serviceName == null) {
      println("FS does not provide delegation tokens%s",
          securityEnabled ? "" : " at least while security is disabled");
    } else {
      Credentials cred = new Credentials();
      try (DurationInfo ignored = new DurationInfo(LOG,
          "Attempting to add delegation tokens")) {
        fs.addDelegationTokens("yarn@EXAMPLE", cred);
      }
      Collection<Token<? extends TokenIdentifier>> tokens
          = cred.getAllTokens();
      int size = tokens.size();
      println("Number of tokens issued by filesystem: %d", size);
      if (size > 0) {
        for (Token<? extends TokenIdentifier> token : tokens) {
          println("Token %s", token);
        }
      } else {
        println("Filesystem did not issue any delegation tokens");
      }
    }

    // now create a directory
    Path dir = new Path(path, "dir-" + UUID.randomUUID());

    try (DurationInfo ignored = new DurationInfo(LOG,
        "Looking for a directory which does not yet exist %s", dir)) {
      FileStatus status = fs.getFileStatus(dir);
      println("Unexpectedly got the status of a file which should not exist%n"
          + "    %s", status);
    } catch (FileNotFoundException expected) {
      // expected this; ignore it.
    }

    try (DurationInfo ignored = new DurationInfo(LOG,
        "Creating a directory %s", dir)) {
      fs.mkdirs(dir);
    } catch (AccessDeniedException e) {
      println("Unable to create directory %s", dir);
      println("If this is a read-only filesystem, this is normal%n");
      println("Please supply a R/W filesystem for more testing.");
      throw e;
    }

    // Directory ops
    try (DurationInfo ignored = new DurationInfo(LOG,
        "Creating a directory %s", dir)) {
      FileStatus status = fs.getFileStatus(dir);
      if (!status.isDirectory()) {
        throw new IOException("Not a directory: " + status);
      }
    }

    // after this point the directory is created;
    // do a file underneath
    try {
      Path file = new Path(dir, "file");
      try (DurationInfo ignored = new DurationInfo(LOG,
          "Creating a file %s", file)) {
        FSDataOutputStream data = fs.create(file, true);
        data.writeUTF(HELLO);
        data.close();
      }
      try (DurationInfo ignored = new DurationInfo(LOG,
          "Listing  %s", dir)) {
        fs.listFiles(dir, false);
      }
      FSDataInputStream in = null;
      try (DurationInfo ignored = new DurationInfo(LOG,
          "Reading a file %s", file)) {
        in = fs.open(file);
        String utf = in.readUTF();
        in.close();
        if (!HELLO.equals(utf)) {
          throw new IOException("Expected " + file + " to contain the text "
              + HELLO + " -but it has the text \"" + utf + "\"");
        }
      } finally {
        IOUtils.closeStream(in);
      }
      // delete the file
      try (DurationInfo ignored = new DurationInfo(LOG,
          "Deleting file %s", file)) {
        fs.delete(file, true);
      }
      

    } finally {
      // teardown: attempt to delete the directory
      try (DurationInfo ignored = new DurationInfo(LOG,
          "Deleting directory %s", dir)) {
        try {
          fs.delete(dir, true);
        } catch (Exception e) {
          LOG.warn("When deleting {}: ", dir, e);
        }
      }
    }
  }

  /**
   * Create a URI, raise an IOE on parsing.
   * @param origin origin for error text
   * @param uri URI.
   * @return instantiated URI.
   * @throws IOException parsing problem
   */
  public static URI toURI(String origin, String uri) throws IOException {
    try {
      return new URI(uri);
    } catch (URISyntaxException e) {
      throw new IOException("From " + origin + " URI: " + uri +
          " - " + e.getMessage(), e);
    }
  }

  public static URI toURI(URL url) throws IOException {
    try {
      return url.toURI();
    } catch (URISyntaxException e) {
      throw new IOException("From " + url +
          " - " + e.getMessage(), e);
    }
  }


  /**
   * Parse CLI arguments and returns the position arguments.
   * The options are stored in {@link #commandFormat}.
   *
   * @param args command line arguments.
   * @return the position arguments from CLI.
   */
  private List<String> parseArgs(String[] args) {
    return args.length > 0 ? commandFormat.parse(args, 0)
        : new ArrayList<String>(0);
  }
  
  private String getOption(String opt) {
    return commandFormat.getOptValue(opt);
  }
  

  /**
   * Get a sorted list of all the JARs on the classpath
   * @return the set of JARs; the iterator will be sorted.
   */
  private Map<String, String> jarsOnClasspath() {
    final String cp = System.getProperty(CLASSPATH);
    final String[] split = cp.split(System.getProperty("path.separator"));
    final Map<String, String> jars = new HashMap<>(split.length);
    final String dir = System.getProperty("file.separator");
    for (String entry : split) {
      final String file = entry.substring(entry.lastIndexOf(dir) + 1);
      jars.put(file, entry);
    }
    return jars;
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param conf configuration to pass in
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int diagnostics(Configuration conf, String... args)
      throws Exception {

    return ToolRunner.run(conf, new StoreDiag(), args);
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new StoreDiag(), args);
  }

  /**
   * Main entry point. Calls {@code System.exit()} on all execution paths.
   * @param args argument list
   */
  public static void main(String[] args) {
    try {
      exit(exec(args), "");
    } catch (CommandFormat.UnknownOptionException e) {
      errorln(e.getMessage());
      exit(E_USAGE, e.getMessage());
    } catch (Throwable e) {
      e.printStackTrace(System.err);
      exit(E_ERROR, e.toString());
    }
  }

}
