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
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.AccessDeniedException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Date;
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
import org.apache.hadoop.fs.store.DurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_SUCCESS;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.fs.store.StoreUtils.checkArgument;
import static org.apache.hadoop.fs.store.diag.OptionSets.CLUSTER_OPTIONS;
import static org.apache.hadoop.fs.store.diag.OptionSets.HADOOP_TOKEN;
import static org.apache.hadoop.fs.store.diag.OptionSets.HADOOP_TOKEN_FILE_LOCATION;
import static org.apache.hadoop.fs.store.diag.OptionSets.SECURITY_OPTIONS;
import static org.apache.hadoop.util.VersionInfo.*;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CharsetObjectCanBeUsed"})
public class StoreDiag extends StoreEntryPoint
  implements Printout {

  private static final Logger LOG = LoggerFactory.getLogger(StoreDiag.class);

  private static final String HELLO = "Hello";

  protected static final int THRESHOLD = 4;

  public static final String CLASSPATH = "java.class.path";

  public static final String PRINCIPAL = "principal";

  public static final String REQUIRED = "required";


  public static final String MD5 = "5";

  public static final String JARS = "j";
  public static final String LOGDUMP = "l";
  public static final String READONLY = "r";
  public static final String SYSPROPS = "s";
  public static final String DELEGATION = "t";

  public static final String LOG_4_PROPERTIES = "log4.properties";

  public static final int LIST_LIMIT = 25;

  public static final String USAGE =
      "Usage: storediag%n"
          + optusage(JARS, "List the JARs on the classpath")
          + optusage(READONLY, "Readonly filesystem: do not attempt writes")
          + optusage(MD5, "Print MD5 checksums of the jars listed (requires -j)")
          + optusage(LOGDUMP, "Dump the Log4J settings")
          + optusage(DELEGATION, "Require delegation tokens to be issued")
          + optusage(SYSPROPS, "List the JVMs System Properties")
          + optusage(DEFINE, "key=value", "Define a property")
          + optusage(TOKENFILE, "file", "Hadoop token file to load")
          + optusage(XMLFILE, "file", "XML config file to load")
          + optusage(REQUIRED, "file",
          "text file of extra classes+resources to require")
          + optusage(PRINCIPAL, "principal",
          "kerberos principal to request a DT for")
          + "<filesystem>";

  private StoreDiagnosticsInfo storeInfo;

  public StoreDiag() {
    createCommandFormat(1, 1,
        VERBOSE,
        JARS,
        DELEGATION,
        READONLY,
        LOGDUMP,
        MD5,
        SYSPROPS);
    addValueOptions(TOKENFILE, XMLFILE, DEFINE, REQUIRED, PRINCIPAL);
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
   * Create a list of star characters.
   * @param n number to create.
   * @return a string of stars
   */
  private static String stars(int n) {
    StringBuilder b = new StringBuilder(n);
    for (int i = 0; i < n; i++) {
      b.append('*');
    }
    return b.toString();
  }

  /**
   * Sanitize a sensitive option.
   * @param value option value.
   * @return sanitized value.
   */
  public static String sanitize(final String value) {
    String safe = value;
    int len = safe.length();
    StringBuilder b = new StringBuilder(len);
    int prefix = 2;
    int suffix = 1;
    if (len > THRESHOLD) {
      b.append(value, 0, prefix);
      b.append(stars(len - prefix - suffix));
      b.append(value, len - suffix, len);
      safe = b.toString();
    } else {
      // short values get special treatment
      safe = stars(THRESHOLD);
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
    addAllDefaultXMLFiles();
    setOut(stream);
    List<String> paths = parseArgs(args);
    if (paths.size() != 1) {
      errorln(USAGE);
      return E_USAGE;
    }
/*

    if (paths.isEmpty()) {
      String defaultFS = new Configuration().get(FS_DEFAULT_NAME_KEY,
          FS_DEFAULT_NAME_DEFAULT);
      println("Using default filesystem: %s", defaultFS);
      paths.add(defaultFS);
    }
*/

    println("Store Diagnostics for %s on %s",
      UserGroupInformation.getCurrentUser(),
      NetUtils.getHostname());

    // process the options
    maybeAddTokens(TOKENFILE);

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
    }
    if (hasOption(LOGDUMP)) {
      dumpLog4J();
    }
    if (hasOption(JARS)) {
      printJARS(hasOption(MD5));
    }
    printEnvVars(storeInfo.getEnvVars());
    printSecurityState();
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
   * Print some security stuff, though KDiag is your friend there.
   */
  public void printSecurityState() throws IOException {

    heading("Security");
    println("Security Enabled: %s",
        UserGroupInformation.isSecurityEnabled());
    println("Keytab login: %s",
        UserGroupInformation.isLoginKeytabBased());
    println("Ticket login: %s",
        UserGroupInformation.isLoginKeytabBased());

    UserGroupInformation currentUser = UserGroupInformation.getCurrentUser();
    println("Current user: %s", currentUser);

    String tokenPath = System.getenv(HADOOP_TOKEN);
    if (tokenPath == null) {
      tokenPath = System.getenv(HADOOP_TOKEN_FILE_LOCATION);
    }
    if (tokenPath != null) {
      println("Token file is %s", tokenPath);
      File tokenFile = new File(tokenPath);
      if (!tokenFile.exists()) {
        warn("Token file does not exist");
      } else {
        // Because tokens can take priority over other auth mechanisms,
        // and if they have expired the fact is not alway obvious.
        long modified = tokenFile.lastModified();
        long age = System.currentTimeMillis() - modified;
        long ageInMin = age / 60_000;
        long ageInHours = ageInMin / 60;
        long minutes = ageInHours > 0 ? (ageInMin % ageInHours) : 0;
        println("Token file updated on %s; age is %d:%02d hours",
            new Date(modified), ageInHours, minutes);
        if (ageInHours > 12) {
          warn("Token file is old: tokens may have expired");
        }
      }
    }
    Collection<Token<? extends TokenIdentifier>> tokens
        = currentUser.getTokens();
    println("Token count: %d", tokens.size());
    for (Token<? extends TokenIdentifier> token : tokens) {
      println("  %s", token);
    }
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
    maybeAddXMLFileOption(conf, XMLFILE);
    setConf(store.patchConfigurationToInitalization(conf));

    // now add any -D value
    maybePatchDefined(conf, DEFINE);
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
      println("without any authentication credentials. ");
      println("This is just testing the reachability of the URLs.");

      println("If the request fails with any network error it is likely");

      println("to be configuration problem with address, proxy, etc%n");

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

  public void printHadoopXMLSources() throws FileNotFoundException {
    heading("Hadoop XML Configurations");
    probeResource("core-site.xml", true);
    probeResource("hdfs-site.xml", false);
    probeResource("mapred-site.xml", false);
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
    InetAddress addr = null;
    try {
      addr = InetAddress.getByName(host);
    } catch (UnknownHostException e) {
      warn("Host %s unknown", endpoint);
      return;
    }
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
              new FileInputStream(f),
              Charset.forName("UTF-8")));
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
        probeResource(name, true);
      } else {
        probeRequiredClass(name);
      }
    }
  }

  /**
   * Look for a resource; print its origin.
   * @param resource resource
   * @param required is the resource required?
   */
  public void probeResource(final String resource,
      final boolean required)
      throws FileNotFoundException {
    String name = resource.trim();
    println("resource: %s", name);
    URL r = this.getClass().getClassLoader().getResource(name);
    if (r == null) {
      if (required) {
        throw new FileNotFoundException("Resource not found: " + name);
      } else {
        println("       resource not found on classpath");
      }
    } else {
      println("       %s", r);
    }
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
  public void executeFileSystemOperations(final Path baseDir,
      final boolean attempWriteOperations) throws IOException {
    final Configuration conf = getConf();
    heading("Test filesystem %s", baseDir);

    println("This call tests a set of operations against the filesystem");
    println("Starting with some read operations, then trying to write%n");

    FileSystem fs;

    try(DurationInfo ignored = new DurationInfo(
        LOG, "Creating filesystem %s", baseDir)) {
      fs = baseDir.getFileSystem(conf);
    }
    URI fsUri = fs.getUri();

    println("%s", fs);
    println("Implementation class %s", fs.getClass());

    storeInfo.validateFilesystem(this, baseDir, fs);


    Path root = fs.makeQualified(new Path("/"));
    try (DurationInfo ignored = new DurationInfo(LOG,
        "GetFileStatus %s", root)) {
      println("root entry %s", fs.getFileStatus(root));
    } catch (FileNotFoundException e) {
      errorln("The remote store doesn't seem to exist: %s", root);
      throw e;
    }

    FileStatus firstFile = null;
    int limit = LIST_LIMIT;
    boolean baseDirFound;
    try (DurationInfo ignored = new DurationInfo(LOG,
        "First %d entries of listStatus(%s)", limit, baseDir)) {
      FileStatus[] statuses = fs.listStatus(baseDir);
      println("%s entry count: %d", baseDir, statuses.length);
      baseDirFound = true;
      for (FileStatus status : statuses) {
        if (status.isFile() && firstFile == null) {
          firstFile = status;
        }
        limit--;
        if (limit > 0) {
          println(statusToString(status));
        } else {
          // finished our listing, if a file is found
          // then its time to leave.
          if (firstFile != null) {
            break;
          }
        }
      }
    } catch (FileNotFoundException e) {
      // dir doesn't exist
      println("Directory %s does not exist", baseDir);
      baseDirFound = false;
    }

    if (firstFile != null) {
      // found a file to read
      Path firstFilePath = firstFile.getPath();
      heading("reading file %s", firstFilePath);
      FSDataInputStream in = null;
      try (DurationInfo ignored = new DurationInfo(LOG,
          "Reading file %s", firstFilePath)) {
        in = fs.open(firstFilePath);
        // read the first char or -1
        int c = in.read();
        println("First character of file %s is 0x%02x: '%s'",
            firstFilePath,
            c,
            (c > ' ') ? Character.toString((char) c) : "(n/a)");
        in.close();
      } finally {
        IOUtils.closeStream(in);
      }
    }

    // now work with the full path
    limit = LIST_LIMIT;
    try(DurationInfo ignored = new DurationInfo(LOG,
        "First %d entries of listFiles(%s)", limit, baseDir)) {
      RemoteIterator<LocatedFileStatus> files = fs.listFiles(baseDir, true);
      try {
        while (files.hasNext() && (limit--)> 0) {
          FileStatus status = files.next();
          println(statusToString(status));
        }
      } catch (AccessControlException e) {
        // didn't have permissions to scan everything down the tree
        // continue rather than fail
        LOG.warn("Permission denied during recursive scan of {}",
            baseDir, e);
      }
      println("Files listing provided by: %s", files);
    } catch (FileNotFoundException e) {
      // this is fine.
    }


    heading("Security and Delegation Tokens");
    boolean requireToken = hasOption(DELEGATION);
    boolean issued = false;
    String outcome = "did not";
    String error = "";
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
      println("Filesystem %s does not/is not configured to issue delegation tokens%s",
          fsUri,
          securityEnabled ? "" : " (at least while security is disabled)");
    } else {
      Credentials cred = new Credentials();
      try (DurationInfo ignored = new DurationInfo(LOG,
          "collecting delegation tokens")) {
        try {
          String renewer = "yarn@EXAMPLE";
          String principal = getOption(PRINCIPAL);
          if (principal != null) {
            renewer = principal;
          }
          fs.addDelegationTokens(renewer, cred);
        } catch (IOException e) {
          if (requireToken) {
            throw e;
          } else {
            LOG.warn("Failed to fetch DT", e);
            outcome = "failed to";
            error = ": " + e;
          }
        }
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
        println("Filesystem %s %s issue any delegation tokens %s",
            fsUri, outcome, error);
      }
    }
    if (requireToken && !issued) {
      throw new StoreDiagException("No delegation token issued by filesystem %s",
          fsUri);
    }

    if (!attempWriteOperations) {
      return;
    }
    heading("Filesystem Write Operations");

    // now create a directory
    Path dir = new Path(baseDir, "dir-" + UUID.randomUUID());

    try (DurationInfo ignored = new DurationInfo(LOG,
        "probe for a directory which does not yet exist %s", dir)) {
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
      println("Please supply a R/W filesystem or use the CLI option " + READONLY);
      throw e;
    }

    // Directory ops
    try (DurationInfo ignored = new DurationInfo(LOG,
        "create directory %s", dir)) {
      FileStatus status = fs.getFileStatus(dir);
      if (!status.isDirectory()) {
        throw new StoreDiagException("Not a directory: %s", status);
      }
    }

    // after this point the directory is created;
    // do a file underneath
    try {
      Path file = new Path(dir, "file");
      verifyPathNotFound(fs, file);

      try (DurationInfo ignored = new DurationInfo(LOG,
          "creating a file %s", file)) {
        FSDataOutputStream data = fs.create(file, true);
        data.writeUTF(HELLO);
        data.close();
        println("Output stream summary: %s", data);
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
        println("input stream summary: %s", in);
        if (!HELLO.equals(utf)) {
          throw new StoreDiagException("Expected  %s to contain the text %s" 
              + " -but it has the text \"%s",
              HELLO, file, utf);
        }
      } finally {
        IOUtils.closeStream(in);
      }

      // move the file into a subdir
      Path subdir = new Path(dir, "subdir");
      Path subdir2 = new Path(dir, "subdir2");
      Path subfile = new Path(subdir, "subfile");
      try (DurationInfo ignored = new DurationInfo(LOG,
          "Renaming file %s under %s", file, subdir)) {
        fs.mkdirs(subdir);
        fs.rename(file, subfile);
        fs.rename(subdir, subdir2);
      }
      verifyPathNotFound(fs, subfile);
      // delete the file
      try (DurationInfo ignored = new DurationInfo(LOG,
          "delete dir %s", subdir2)) {
        fs.delete(subdir2, true);
      }
      verifyPathNotFound(fs, subdir2);

    } finally {
      // teardown: attempt to delete the directory
      deleteDir(fs, dir);
      if (!baseDirFound) {
        deleteDir(fs, baseDir);
      }
    }
  }

  public void deleteDir(final FileSystem fs, final Path dir) {
    try (DurationInfo ignored = new DurationInfo(LOG,
        "delete directory %s", dir)) {
      try {
        fs.delete(dir, true);
      } catch (Exception e) {
        LOG.warn("When deleting {}: ", dir, e);
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

  protected void verifyPathNotFound(FileSystem fs, Path path)
      throws IOException {
    try (DurationInfo ignored = new DurationInfo(LOG,
        "probing path %s", path)) {
      final FileStatus st = fs.getFileStatus(path);
      throw new StoreDiagException(
          "Found path which should be absent %s", st);
    } catch (FileNotFoundException ignored) {
    }
  }

  private String hash(File file) throws IOException, NoSuchAlgorithmException {
    /*return toHex(Files.getDigest(file, MessageDigest.getInstance("MD5")));*/
    return "guava broke this, sorry";
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
  public static int exec(String...args) throws Exception {
    return ToolRunner.run(new StoreDiag(), args);
  }

  /**
   * Main entry point. Calls {@code System.exit()} on all execution paths.
   * @param args argument list
   */
  public static void main(String[] args) {
    try {
      exit(exec(args), "");
    } catch (Throwable e) {
      exitOnThrowable(e);
    }
  }

}
