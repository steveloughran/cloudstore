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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.AccessDeniedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
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
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.ToolRunner;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_ERROR;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_SUCCESS;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.fs.store.diag.OptionSets.CLUSTER_OPTIONS;
import static org.apache.hadoop.fs.store.diag.OptionSets.SECURITY_OPTIONS;
import static org.apache.hadoop.util.VersionInfo.*;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class StoreDiag extends StoreEntryPoint
  implements Printout {

  private static final Logger LOG = LoggerFactory.getLogger(StoreDiag.class);

  private static final String HELLO = "Hello";

  protected static final int THRESHOLD = 4;

  public static final String CLASSPATH = "java.class.path";

  public static final String TOKENFILE = "tokenfile";

  public static final String XMLFILE = "xmlfile";
  
  public static final String REQUIRED = "required";
  
  public static final String DELEGATION = "t";
  public static final String JARS = "j";
  public static final String LOGDUMP = "l";
  public static final String MD5 = "5";
  public static final String READONLY = "r";
  public static final String SYSPROPS = "s";

  public static final String LOG_4_PROPERTIES = "log4.properties";

  public static final int LIST_LIMIT = 25;

  private static String optusage(String opt) {
    return "[-" + opt + "] ";
  }
  
  private static String optusage(String opt, String second, String text) {
    return String.format("-%s <%s>\t%s%n", opt, second, text);
  }

  public static final String USAGE =
      "Usage: storediag [-tokenfile <file>] "
          + optusage(JARS)
          + optusage(READONLY)
          + optusage(DELEGATION)
          + optusage(MD5)
          + optusage(LOGDUMP)
          + optusage(SYSPROPS)
          + "<filesystem>" 
          + "\n" 
          + optusage(TOKENFILE, "file", "Hadoop token file to load")
          + optusage(XMLFILE, "file", "XML config file to load")
          + optusage(REQUIRED, "file", "text file of extra classes+resources to require")
          + "-r   Readonly filesystem: do not attempt writes\n"
          + "-t    Require delegation tokens to be issued\n"
          + "-j    List the JARs on the classpath\n"
          + "-s    List the JVMs System Properties\n"
          + "-5    Print MD5 checksums of the jars listed (requires -j)\n";
  
  private StoreDiagnosticsInfo storeInfo;
  
  public StoreDiag() {
     setCommandFormat(new CommandFormat(1, 1,
         JARS,
         DELEGATION,
         READONLY,
         LOGDUMP,
         MD5,
         SYSPROPS));
     getCommandFormat().addOptionWithValue(TOKENFILE);
     getCommandFormat().addOptionWithValue(XMLFILE);
     getCommandFormat().addOptionWithValue(REQUIRED);
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
   * @param md5 create MD5 checksums
   */
  private void printJARS(final boolean md5)
      throws IOException, NoSuchAlgorithmException {
    heading("JAR listing");
    final Map<String, String> jars = jarsOnClasspath();
    for (String s : sortKeys(jars.keySet())) {

      File file = new File(jars.get(s));
      boolean isFile = file.isFile();
      boolean exists = file.exists();
      String size;
      if (!exists) {
        size = "[missing]";
      } else {
        size = isFile ?
            String.format("[%,d]", file.length())
            : "[directory]";
      }
      String text = String.format("%s\t%s (%s)", s, jars.get(s), size);
      if (md5) {
        String hex;
        if (isFile) {
          hex = hash(file);
        } else {
          // 32 spaces
          hex = "                                ";
        }
        text = hex + "  " + text;
      }
      println(text);
    }
  }
  
  /**
   * Sort the keys.
   * @param keys keys to sort.
   * @return new set of sorted keys
   */
  public static Set<String> sortKeys(final Iterable<?> keys) {
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
  public static String sanitize(final String value) {
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
    addAllDefaultXMLFiles();

    println("Store Diagnostics for %s on %s",
      UserGroupInformation.getCurrentUser(),
      NetUtils.getHostname());

    // process the options
    String tokenfile = getOption(TOKENFILE);
    if (tokenfile != null) {
      heading("Adding tokenfile %s", tokenfile);
      Credentials credentials = Credentials.readTokenStorageFile(
          new File(tokenfile), getConf());
      Collection<Token<? extends TokenIdentifier>> tokens
          = credentials.getAllTokens();
      println("Loaded %d token(s)", tokens.size());
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
    if (hasOption(SYSPROPS)) {
      printJVMOptions();
      dumpLog4J();
    }
    if (hasOption(LOGDUMP)) {
      dumpLog4J();
    }
    if (hasOption(JARS)) {
      printJARS(hasOption(MD5));
    }
    printEnvVars(storeInfo.getEnvVars());
    printStoreConfiguration();
    probeRequiredAndOptionalClasses();
    storeInfo.validateConfig(this, getConf());
    probeAllEndpoints();

    // and the filesystem operations
    executeFileSystemOperations(path, !hasOption(READONLY));

    // Validate parameters.
    return E_SUCCESS;
  }

  /**
   * Probe all the endpoints.
   * @throws IOException IO Failure
   */
  public void probeAllEndpoints() throws IOException  {
    heading("Endpoints");

    try {
      probeEndpoints(storeInfo.listEndpointsToProbe(getConf()));
      probeOptionalEndpoints(storeInfo.listOptionalEndpointsToProbe(getConf()));
    } catch (URISyntaxException e) {
      LOG.warn("Bad URI", e);
    }
  }

  /**
   * Print the base configuration of the store.
   */
  public void printStoreConfiguration() throws IOException {

    final Configuration conf = getConf();
    printOptions("Hadoop Options", conf, CLUSTER_OPTIONS);
    printOptions("Security Options", conf, SECURITY_OPTIONS);
    printOptions("Selected Configuration Options",
        conf, storeInfo.getFilesystemOptions());
  }

  /**
   * Bind the diagnostics to a store.
   * @param fsURI filesystem
   * @return the store's diagnostics.
   */
  public StoreDiagnosticsInfo bindToStore(final String fsURI)
      throws IOException {

      return bindToStore(toURI("command", fsURI));
  }
  
  /**
   * Bind the diagnostics to a store.
   * @param fsURI filesystem
   * @return the store's diagnostics.
   */
  public StoreDiagnosticsInfo bindToStore(final URI fsURI)
      throws IOException {
    heading("Diagnostics for filesystem %s", fsURI);

    StoreDiagnosticsInfo store = StoreDiagnosticsInfo.bindToStore(fsURI);

    println("%s%n%s%n%s",
        store.getName(), store.getDescription(), store.getHomepage());

    Configuration conf = getConf();
    // load XML file
    String file = getOption(XMLFILE);
    if (file != null) {
      File f = new File(file);
      if (!f.exists()) {
        throw new FileNotFoundException(f.toString());
      }
      println("Adding XML configuration file %s", f);
      conf.addResource(f.toURI().toURL());
    }
    setConf(store.patchConfigurationToInitalization(conf));
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
   * Probe the list of endpoints.
   * @param endpoints list to probe (unauthed)
   * @throws IOException  IO Failure
   */
  public void probeOptionalEndpoints(final List<URI> endpoints)
      throws IOException {
    for (URI endpoint : endpoints) {
      try {
        probeOneEndpoint(endpoint);
      } catch (IOException e) {
        LOG.info("Connecting to {}", endpoint, e);
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
    conn.setConnectTimeout(1_000);
    conn.setReadTimeout(1_000);
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
  public void probeRequiredAndOptionalClasses() throws ClassNotFoundException,
      IOException {
    probeRequiredClasses(storeInfo.getClassnames(getConf()));
    probeOptionalClasses(storeInfo.getOptionalClassnames(getConf()));
    // load any .classes files
    String file = getOption(REQUIRED);
    if (file != null) {
      File f = new File(file);
      if (!f.exists()) {
        throw new FileNotFoundException(f.toString());
      }
      heading("Probing required classes listed in %s", f);
      probeRequiredClassesOrResources(
          org.apache.commons.io.IOUtils.readLines(
              new FileInputStream(f), Charsets.UTF_8));
    }
  }

  /**
   * Take a list of lines containing: comments, classes, resources
   * and possibly blankness, trip and either probe for or print the comment.
   * @param lines lines to scan
   * @throws ClassNotFoundException class was not found
   */
  private void probeRequiredClassesOrResources(List<String> lines)
      throws ClassNotFoundException, FileNotFoundException {
    for (String line : lines) {
      String name = line.trim();
      if (name.isEmpty() || name.startsWith("#")) {
        println(name);
        continue;
      }
      if (name.contains("/")) {
        probeRequiredResource(name);
      } else {
        probeRequiredClass(name);
      }
    }
  }

  /**
   * Look for a resource; print its origin.
   * @param resource resource
   */
  public void probeRequiredResource(final String resource)
      throws FileNotFoundException {
    String name = resource.trim();
    println("resource: %s", name);
    URL r = this.getClass().getClassLoader().getResource(name);
    if (r == null) {
      throw new FileNotFoundException("Resource not found: " + name); 
    }
    println("       %s", r);
  }
  
  /**
   * Look for a class; print its origin.
   * @param classname classname
   * @throws ClassNotFoundException if the class was not found.
   */
  public void probeRequiredClass(final String classname)
      throws ClassNotFoundException {
    String name = classname.trim();
    if (name.isEmpty()) {
      return;
    }
    println("class: %s", name);
    Class<?> clazz = this.getClass().getClassLoader().loadClass(name);
    println("       %s",
        clazz.getProtectionDomain().getCodeSource().getLocation());
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
   * Dump log4J files; special handling for the case that >1 ends up on
   * the classpath.
   * @throws IOException IO failure.
   */
  public void dumpLog4J() throws IOException {
    heading("Log4J");
    String resource = "/" + LOG_4_PROPERTIES;
    Enumeration<URL> logjJs = this.getClass()
        .getClassLoader()
        .getResources(resource);
    int found = 0;
    while (logjJs.hasMoreElements()) {
      found++;
      println("Found %s at %s%n%s", LOG_4_PROPERTIES, logjJs.nextElement());
    }
    if (found == 0) {
      warn("Failed to find %s", LOG_4_PROPERTIES);
      return;
    }
    if (found > 1) {
      warn("Found multiple log4j.properties files");
    }
    
    println("%n%s",
      CharStreams.toString(new InputStreamReader(
          this.getClass().getResourceAsStream(resource),
          Charsets.UTF_8)));
  }

  /**
   * Dump all the user's tokens.
   * @throws IOException failure.
   */
  public void dumpUserTokens() throws IOException {
    UserGroupInformation currentUser = UserGroupInformation.getCurrentUser();
    Credentials credentials = currentUser.getCredentials();
    Collection<Token<? extends TokenIdentifier>> allTokens
        = credentials.getAllTokens();
    for (Token<? extends TokenIdentifier> token : allTokens) {
      println("%s", token);
    }
  }
  
  private String statusToString(FileStatus status) {
    String suffix;
    if (status.isFile()) {
      suffix = "\t[" + status.getLen() + "]";
    } else {
      if (!status.getPath().toString().endsWith("/")) {
        suffix = "/";
      } else {
        suffix = "/";
      }
    }
    return String.format("%s%s",
        status.getPath(),
        suffix);
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
    println("Implementation class %s", fs.getClass());

    storeInfo.validateFilesystem(this, path, fs);


    Path root = fs.makeQualified(new Path("/"));
    try (DurationInfo ignored = new DurationInfo(LOG,
        "GetFileStatus %s", root)) {
      println("root entry %s", fs.getFileStatus(root));
    }

    FileStatus rootFile = null;
    try (DurationInfo ignored = new DurationInfo(LOG,
        "Listing %s", root)) {
      FileStatus[] statuses = fs.listStatus(root);
      println("%s root entry count: %d", root, statuses.length);
      int limit = LIST_LIMIT;
      for (FileStatus status : statuses) {
        if (status.isFile() && rootFile == null) {
          rootFile = status;
        }
        limit--;
        if (limit > 0) {
          println(statusToString(status));
        } else {
          // finished our listing, if a file is found
          // then its time to leave.
          if (rootFile != null) {
            break;
          }
        }
        
      }
    }

    if (rootFile != null) {
      // found a file to read
      Path rootFilePath = rootFile.getPath();
      heading("reading file %s", rootFilePath);
      FSDataInputStream in = null;
      try (DurationInfo ignored = new DurationInfo(LOG,
          "Reading file %s", rootFilePath)) {
        in = fs.open(rootFilePath);
        // read the first char or -1
        int c = in.read();
        println("First character of file %s is 0x%02x: '%s'",
            rootFilePath,
            c,
            (c > 32) ? Character.toString((char) c) : "(n/a)");
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
      int limit = LIST_LIMIT;
      RemoteIterator<LocatedFileStatus> files = fs.listFiles(path, true);
      while (files.hasNext() && (limit--)> 0) {
        status = files.next();
        println(statusToString(status));
      }
    } catch (FileNotFoundException e) {
      // this is fine.
    }

    if (!attempWriteOperations) {
      return;
    }

    heading("Security and Delegation Tokens");
    boolean issued = false;
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
        issued = true;
        for (Token<? extends TokenIdentifier> token : tokens) {
          println("Token %s", token);
        }
      } else {
        println("Filesystem did not issue any delegation tokens");
      }
    }
    if (hasOption(DELEGATION) && !issued) {
      throw new IOException("No delegation token issued by filesystem");
    }
    heading("Filesystem Write Operations");

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
      println(
          "Please supply a R/W filesystem or use the CLI option " + READONLY);
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
    checkArgument(uri != null && !uri.isEmpty(), "No URI");
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
   * Get a sorted list of all the JARs on the classpath
   * @return the set of JARs; the iterator will be sorted.
   */
  public Map<String, String> jarsOnClasspath() {
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


  private String hash(File file) throws IOException, NoSuchAlgorithmException {
    return toHex(Files.getDigest(file, MessageDigest.getInstance("MD5")));
  }

  private byte[] md5sum(File file)
      throws NoSuchAlgorithmException, IOException {
    return Files.getDigest(file, MessageDigest.getInstance("MD5"));
  }

  private String toHex(byte[] digest32) {

    // Convert message digest into hex value 
    String hashtext = new BigInteger(1, digest32)
        .toString(16);
    while (hashtext.length() < 32) {
      hashtext = "0" + hashtext;
    }
    return hashtext;
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
    } catch (ExitUtil.ExitException e) {
      LOG.debug("Command failure", e);
      exit(e);
    } catch (Throwable e) {
      e.printStackTrace(System.err);
      exit(E_ERROR, e.toString());
    }
  }

}
