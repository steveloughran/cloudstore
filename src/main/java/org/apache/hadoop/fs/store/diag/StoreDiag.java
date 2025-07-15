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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.security.CodeSource;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

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
import org.apache.hadoop.fs.StreamCapabilities;
import org.apache.hadoop.fs.UnsupportedFileSystemException;
import org.apache.hadoop.fs.store.PathCapabilityChecker;
import org.apache.hadoop.fs.store.StoreDurationInfo;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.STANDARD_OPTS;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_ERROR;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_NOT_FOUND;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_NO_ACCESS;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_SUCCESS;
import static org.apache.hadoop.fs.store.diag.OptionSets.CLUSTER_OPTIONS;
import static org.apache.hadoop.fs.store.diag.OptionSets.HADOOP_TOKEN;
import static org.apache.hadoop.fs.store.diag.OptionSets.HADOOP_TOKEN_FILE_LOCATION;
import static org.apache.hadoop.fs.store.diag.OptionSets.SECURITY_OPTIONS;
import static org.apache.hadoop.fs.store.diag.OptionSets.TLS_SYSPROPS;
import static org.apache.hadoop.io.IOUtils.closeStream;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CharsetObjectCanBeUsed"})
public class StoreDiag extends DiagnosticsEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(StoreDiag.class);

  /** {@value}. */
  private static final String HELLO = "Hello";

  /** {@value}. */
  public static final String ENVARS = "e";

  /** hide all secrets: {@value}. */
  public static final String HIDE = "h";

  /** {@value}. */
  public static final String OPTIONAL = "o";

  /** {@value}. */
  public static final String LOGDUMP = "l";

  /** {@value}. */
  public static final String READONLY = "r";

  /** {@value}. */
  public static final String WRITE = "w";

  /** {@value}. */
  public static final String SYSPROPS = "s";

  /** {@value}. */
  public static final String DELEGATION = "t";

  /** {@value}. */
  public static final String LOG_4_PROPERTIES = "log4.properties";

  /** {@value}. */
  public static final int LIST_LIMIT = 25;

  /** {@value}. */
  public static final String USAGE =
      "Usage: storediag [options] <filesystem>\n"
          + STANDARD_OPTS
          + optusage(DELEGATION, "Require delegation tokens to be issued")
          + optusage(ENVARS, "List the environment variables. *danger: does not redact secrets*")
          + optusage(HIDE, "redact all chars in sensitive options")
          + optusage(JARS, "List the JARs on the classpath")
          + optusage(LOGDUMP, "Dump the Log4J settings")
          + optusage(MD5, "Print MD5 checksums of the jars listed (requires -j)")
          + optusage(OPTIONAL, "Downgrade all 'required' classes to optional")
          + optusage(PRINCIPAL, "principal", "kerberos principal to request a token for")
          + optusage(REQUIRED, "file", "text file of extra classes+resources to require")
          + optusage(SYSPROPS, "List the JVM System Properties")
          + optusage(WRITE, "attempt write operations on the filesystem");

  private StoreDiagnosticsInfo storeInfo;

  public StoreDiag() {
    createCommandFormat(1, 1,
        DELEGATION,
        ENVARS,
        HIDE,
        JARS,
        LOGDUMP,
        MD5,
        OPTIONAL,
        READONLY,
        WRITE
    );
    addValueOptions(
        PRINCIPAL,
        REQUIRED
    );
  }

  @Override
  public final int run(String[] args) throws Exception {
    return run(args, System.out);
  }

  public int run(String[] args, PrintStream stream) throws Exception {
    setOut(stream);
    List<String> paths = processArgs(args,1, 1, USAGE);

    heading("Store Diagnostics for %s on %s",
        UserGroupInformation.getCurrentUser(),
        NetUtils.getHostname());
    println("Collected at at %s%n", Instant.now());

    // path on the CLI
    String pathString = paths.get(0);
    if (!pathString.endsWith("/")) {
      pathString = pathString + "/";
    }
    Path path = new Path(pathString);


    // and its FS URI
    storeInfo = bindToStore(path.toUri());
    final boolean writeOperations = hasOption(WRITE);
    setHideAllSensitiveChars(hasOption(HIDE));

    printHadoopVersionInfo();
    printOSVersion();
    if (hasOption(SYSPROPS)) {
      printJVMOptions();
    } else {
      // only print selected ones
      printSystemProperties(storeInfo.getSelectedSystemProperties());
    }

    printSecurityProperties(storeInfo.getSecurityProperties());

    if (hasOption(ENVARS)) {
      dumpEnvVars();
    }
    if (hasOption(LOGDUMP)) {
      dumpLog4J();
    }
    if (hasOption(JARS)) {
      printJARS(hasOption(MD5));
    }
    printEnvVars(storeInfo.getEnvVars());
    printHadoopXMLSources();
    printSecurityState();
    printStoreConfiguration();
    probeRequiredAndOptionalClasses(hasOption(OPTIONAL));
    probeRequiredAndOptionalResources();
    storeInfo.validateConfig(this, getConf(), writeOperations);
    printPerformanceHints();
    probeForFileSystemClass(storeInfo.getScheme());
    if (storeInfo.printTLSInfo()) {
      tlsInfo();
    }
    probeAllEndpoints();
    storeInfo.preflightFilesystemChecks(this, path);

    // and the filesystem operations
    final boolean completed = executeFileSystemOperations(path, writeOperations);

    // dump JVM status
    printJVMStats();

    if (completed) {
      heading("Success!");
    } else {
      heading("Failed to complete file operations");
    }
    return completed ? E_SUCCESS : E_ERROR;
  }

  /**
   * Probe all the endpoints.
   * @throws IOException IO Failure
   */
  public void probeAllEndpoints() throws IOException {
    heading("Endpoints");

    try {
      probeEndpoints(storeInfo.listEndpointsToProbe(getConf()));
      probeOptionalEndpoints(storeInfo.listOptionalEndpointsToProbe(getConf()));
    } catch (URISyntaxException e) {
      LOG.warn("Bad URI", e);
    }
  }

  /**
   * Print information about TLS.
   */
  public void tlsInfo() {

    lookupAndPrintSanitizedValues(TLS_SYSPROPS,
        "TLS System Properties",
        System::getProperty);
    println();
    try {
      final SSLContext sslContext = SSLContext.getDefault();
      final SSLParameters sslParameters = sslContext.getSupportedSSLParameters();
      final String[] protocols = sslParameters.getProtocols();
      heading("HTTPS supported protocols");
      for (String protocol : protocols) {
        println("    %s", protocol);
      }
    } catch (NoSuchAlgorithmException e) {
      LOG.warn("failed to create SSL context", e);
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
   * print any performance hints.
   */
  private void printPerformanceHints() {
    storeInfo.performanceHints(this, getConf());
  }

  @SuppressWarnings("NestedAssignment")
  public boolean printOSVersion() throws IOException {
    heading("Determining OS version");
    try {
      String[] commands = {"uname", "-a"};
      Process proc = Runtime.getRuntime().exec(commands);
      try (BufferedReader stdin = new BufferedReader(new
          InputStreamReader(proc.getInputStream()));
           BufferedReader stderr = new BufferedReader(new
               InputStreamReader(proc.getErrorStream()))) {
        String s;
        while ((s = stdin.readLine()) != null) {
          println(s);
        }
        while ((s = stderr.readLine()) != null) {
          println(s);
        }
        proc.waitFor();
        return proc.exitValue() == 0;
      }
    } catch (IOException e) {
      println("Failed to determine OS Version: %s", e);
      LOG.debug("Failed to determine OS Version", e);
      return false;
    } catch (InterruptedException e) {
      throw (IOException) new InterruptedIOException(e.toString())
          .initCause(e);
    }
  }

  /**
   * Print any JVM stats we can get hold of.
   */
  public void printJVMStats() {
    Runtime runtime = Runtime.getRuntime();
    println("JVM: memory=%d", runtime.freeMemory());
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
        // and if they have expired the fact is not always obvious.
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

    StoreDiagnosticsInfo store = StoreDiagnosticsInfo.bindToStore(fsURI, this);

    println("%s%n%s%n%s",
        store.getName(), store.getDescription(), store.getHomepage());

    Configuration conf = createPreconfiguredConfig();
    setConf(store.patchConfigurationToInitalization(conf));

    return store;
  }

  /**
   * Probe the list of endpoints.
   * @param endpoints list to probe (unauthed)
   * @throws IOException IO Failure
   */
  public void probeEndpoints(final List<URI> endpoints)
      throws IOException {
    if (endpoints.isEmpty()) {
      println("No endpoints determined for this filesystem");
    } else {
      println("Attempting to list and connect to public service endpoints,");
      println("without any authentication credentials.\n");
      println("- This is just testing the reachability of the URLs.");

      println("- If the request fails with any network error it is likely");

      println("  to be configuration problem with address, proxy, etc.");

      println("- If it is some authentication error, then don't worry:\n"
          + "    the results of the filesystem operations are what really matters");

      for (URI endpoint : endpoints) {
        try {
          probeOneEndpoint(endpoint);
        } catch (UnknownHostException | NoRouteToHostException e) {
          errorln("Major connectivity problem connecting to: %s: %s", endpoint, e);
          errorln("Check definition of endpoints and network status");
          LOG.warn("Stack trace", e);
        } catch (IOException e) {
          LOG.warn("Failed to probe {}", endpoint, e);
        }
      }
    }
  }

  /**
   * Probe the list of endpoints.
   * @param endpoints list to probe (unauthed)
   * @throws IOException IO Failure
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
   * Probe one endpoint, print proxy values, etc. No auth.
   * Ignores "0.0.0.0" addresses as they are silly.
   * @param endpoint endpoint
   * @throws IOException network problem
   */
  public void probeOneEndpoint(URI endpoint)
      throws IOException {
    final String host = endpoint.getHost();

    heading("Endpoint: %s", endpoint);
    try {
      printCanonicalHostname(this, host);
    } catch (UnknownHostException e) {
      warn("Host %s unknown", endpoint);
      return;
    }

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
    String body;
    InputStream in = success ? conn.getInputStream() : conn.getErrorStream();
    if (in != null) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      IOUtils.copyBytes(
          in,
          out, 4096, true);
      body = out.toString();
    } else {
      body = "(empty response)";
    }

    println("%n%s%n",
        body.substring(0, Math.min(1024, body.length())));
    if (success) {
      println("WARNING: this unauthenticated operation was not rejected.%n "
          + "This may mean the store is world-readable.%n "
          + "Check this by pasting %s into your browser", url);
    }
  }

  /**
   * Determine the canonical name of a host.
   * @param out printer
   * @param host host to resolve
   * @throws UnknownHostException if the host doesn't exist
   */
  public static void printCanonicalHostname(final Printout out, final String host)
      throws UnknownHostException {
    InetAddress addr = InetAddress.getByName(host);
    out.println("Canonical hostname %s%n  IP address %s",
        addr.getCanonicalHostName(),
        addr.getHostAddress());
  }

  /**
   * Probe all the required classes from base settings,
   * the FS diags, and any file passed in as -required.
   * @param optional is this a probe for optional classes
   * @throws ClassNotFoundException no class
   * @throws IOException other faillure
   */
  public void probeRequiredAndOptionalClasses(boolean optional)
      throws ClassNotFoundException, IOException {
    String[] requiredClasses = storeInfo.getClassnames(getConf());
    if (!optional) {
      probeRequiredClasses(requiredClasses);
    } else {
      probeOptionalClasses(requiredClasses);
    }
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
              Files.newInputStream(f.toPath()),
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
        probeRequiredClass(name, true);
      }
    }
  }


  /**
   * Probe all the required resources from base settings,
   * the FS diags,
   * @throws FileNotFoundException no resource
   * @throws IOException other failure
   */
  public void probeRequiredAndOptionalResources()
      throws ClassNotFoundException, IOException {

    heading("Required Resources");
    for (String resource : storeInfo.getRequiredResources(getConf())) {
      if (!resource.isEmpty()) {
        probeResource(resource, true);
      }
    }

    heading("Optional Resources");
    for (String resource : storeInfo.getOptionalResources(getConf())) {
      if (!resource.isEmpty()) {
        probeResource(resource, false);
      }
    }

  }

  /**
   * Look for the filesystem class.
   * @param scheme fs scheme
   * @throws UnsupportedFileSystemException fs wasn't registered.
   * @throws IOException failure to load
   */
  private void probeForFileSystemClass(String scheme) throws IOException {
    heading("Locating implementation class for Filesystem scheme %s://", scheme);
    try {
      Class<? extends FileSystem> clazz = FileSystem.getFileSystemClass(
          scheme, getConf());
      println("FileSystem for %s:// is: %s", scheme, clazz.getName());
      CodeSource source = clazz.getProtectionDomain().getCodeSource();
      if (source != null) {
        println("Loaded from: %s via %s", source.getLocation(), clazz.getClassLoader());
      }
    } catch (IOException e) {
      if (e instanceof UnsupportedFileSystemException
          || e.toString().contains("No FileSystem for scheme")) {

        errorln("No binding for the FileSystem scheme %s", scheme);
        errorln("Check core-default.xml, core-site.xml");
        errorln("If the FS is self-registering, check for an entry in" +
            " META-INF/services/org.apache.hadoop.fs.FileSystem");
        errorln("If there was a stack trace during the scan, it means an FS class failed to load");
        errorln("if so: check its dependencies");
      }
      throw e;
    }
  }

  /**
   * Dump env vars. all AWS entries are obfuscated.
   */
  public void dumpEnvVars() throws IOException {
    heading("All Environment Variables");
    Map<String, String> vars = new TreeMap<>(System.getenv());
    int i = 1;
    for (Map.Entry<String, String> entry : vars.entrySet()) {
      String key = entry.getKey();
      String value = maybeSanitize(entry.getValue(),
          key.startsWith("AWS_"));
      println("[%03d] %s=\"%s\"", i, key, value);
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
    Enumeration<URL> logjJs = getClass()
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
            getClass().getResourceAsStream(resource),
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

  /**
   * Execute the FS level operations, one by one.
   * @return true if everything worked.
   */
  public boolean executeFileSystemOperations(
      final Path baseDir,
      final boolean attempWriteOperations) throws IOException {
    final Configuration conf = getConf();
    heading("Test filesystem %s", baseDir);

    if (!attempWriteOperations) {
      println("Trying some list and read operations");

    } else {
      println("Trying some operations against the filesystem");
      println("Starting with some read operations, then trying to write");
    }

    FileSystem fs;

    subheading("Filesystem client Instantiation");
    try (StoreDurationInfo ignored = new StoreDurationInfo(
        LOG, "Creating filesystem for %s", baseDir)) {
      fs = FileSystem.newInstance(baseDir.toUri(), conf);
    }
    URI fsUri = fs.getUri();

    println("%s", fs);
    println("Implementation class %s", fs.getClass());

    final String[] pathCapabilites = storeInfo.getOptionalPathCapabilites();
    if (pathCapabilites.length > 0) {
      final PathCapabilityChecker checker = new PathCapabilityChecker(fs);
      if (checker.methodAvailable()) {
        heading("Path Capabilities");
        String capability = "";
        for (String s : pathCapabilites) {
          try {
            capability = s;
            println("%s\t%s", s, checker.hasPathCapability(baseDir, s));
          } catch (IOException | ExitUtil.ExitException e) {

            // problem
            warn("When checking path capability %s: %s", capability, e.toString());
            LOG.debug("checking path capability {}", capability, e);
            break;
          }
        }
        println();
      }
    }

    storeInfo.validateFilesystem(this, baseDir, fs);


    subheading("Reading root path");
    Path root = fs.makeQualified(new Path("/"));
    String operation = "Examine root path";
    try (StoreDurationInfo ignored = new StoreDurationInfo(getOut(),
        "Examine root path", root)) {
      operation = "getFileStatus(/)";
      println("root entry %s", fs.getFileStatus(root));

      operation = "listStatus(/)";
      println("list /");
      final FileStatus[] rootListing = fs.listStatus(root);
      final int len = rootListing.length;

      println("ls / contains %s entries; first entry %s",
          len,
          (len > 0 ?
              statusToString(rootListing[0])
              : "n/a" ));
    } catch (FileNotFoundException e) {
      errorln("%s failed: the remote filesystem doesn't seem to exist: %s",
          operation, root);
      errorln("There is no store of that name for that account at that endpoint");
      println("Possible causes:");
      println("  - wrong store/endpoint is being probed; check endpoint");
      println("  - filesystem is only visible to the account and wrong account was used: check account");
      println("  - the store is mis-spelled. check URL spelling");
      println("  - the store never existed: check console for existence; create if desired");
      println("  - the store has been deleted: check console for history");
      println("There's nothing else which can be done here");

      throw new StoreDiagException(E_NOT_FOUND,
          "Store not found %s: %s", root, e.toString())
          .initCause(e);
    }

    subheading("Listing %s", baseDir);

    FileStatus firstFile = null;
    int limit = LIST_LIMIT;
    boolean baseDirFound;
    boolean accessDenied = false;
    try (StoreDurationInfo ignored = new StoreDurationInfo(getOut(),
        "First %d entries of listStatus(%s)", limit, baseDir)) {
      RemoteIterator<FileStatus> statuses = fs.listStatusIterator(baseDir);
      int statusCount = 0;
      baseDirFound = true;
      while (statuses.hasNext() &&
          (limit > 0 || firstFile == null)) {
        statusCount++;
        FileStatus status = statuses.next();
        if (status.isFile() && firstFile == null) {
          firstFile = status;
        }
        limit--;
        if (limit > 0) {
          println(statusToString(status));
        }
      }
      println("%s : scanned %d entries", baseDir, statusCount);
    } catch (FileNotFoundException e) {
      // dir doesn't exist
      println("Directory %s does not exist", baseDir);
      baseDirFound = false;
    }

    println("Listing the directory %s has succeeded", baseDir);
    println("The store is reachable and the client has list permissions");


    // =======================================

    if (firstFile != null) {

      // found a file to read
      subheading("Reading file %s", firstFile);
      accessDenied = readFile(fs, firstFile);
    } else {
      println("no file found to attempt to read");
    }

    // should we do a deep or shallow tree list.
    final boolean deepTreeList = storeInfo.deepTreeList();

    subheading("listfiles(%s, %s)", baseDir, deepTreeList);
    // =======================================

    // now work with the full path
    limit = LIST_LIMIT;
    try (StoreDurationInfo ignored = new StoreDurationInfo(getOut(),
        "First %d entries of listFiles(%s)", limit, baseDir)) {
      RemoteIterator<LocatedFileStatus> files = fs.listFiles(baseDir, deepTreeList);
      try {
        while (files.hasNext() && (limit--) > 0) {
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


    subheading("Security and Delegation Tokens");
    // =======================================

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
      //
      Credentials cred = new Credentials();
      try (StoreDurationInfo ignored = new StoreDurationInfo(getOut(),
          "collecting delegation tokens")) {
        try {
          String renewer = "yarn@EXAMPLE";
          if (securityEnabled) {
            // set the renewer to the current user
            renewer = UserGroupInformation.getCurrentUser().getUserName();
          }
          String principal = getOption(PRINCIPAL);
          if (principal != null) {
            renewer = principal;
          }
          println("Token Renewer: %s", renewer);
          fs.addDelegationTokens(renewer, cred);
        } catch (Throwable e) {
          if (requireToken) {
            throw e;
          } else {
            LOG.warn("Failed to fetch token", e);
            outcome = "failed to";
            error = ": " + e;
          }
        }
      }
      Collection<Token<? extends TokenIdentifier>> tokens = cred.getAllTokens();
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
      throw new StoreDiagException(E_NO_ACCESS,
          "No delegation token issued by filesystem %s", fsUri);
    }


    // now create a directory

    Path dir = new Path(baseDir, "dir-" + UUID.randomUUID());
    subheading("Directory Creation: initial probe for %s", dir);

    try (StoreDurationInfo ignored = new StoreDurationInfo(getOut(),
        "probe for a directory which does not yet exist %s", dir)) {
      FileStatus status = fs.getFileStatus(dir);
      println("Unexpectedly got the status of a file which should not exist%n"
          + "    %s", status);
    } catch (FileNotFoundException expected) {
      // expected this; ignore it.
    }
    if (accessDenied) {
      println("Client lacks read access to the store; aborting");
      return false;
    }
    if (!attempWriteOperations) {
      subheading("All read operations succeeded: client has read access");
      println("Tests are read only; to test write permissions rerun with -%s", WRITE);
      return true;
    }
    heading("Filesystem Write Operations");

    try (StoreDurationInfo ignored = new StoreDurationInfo(getOut(),
        "creating a directory %s", dir)) {
      fs.mkdirs(dir);
    } catch (AccessDeniedException e) {
      println("Unable to create directory %s", dir);
      println("If this is a read-only filesystem, this is normal%n");
      println("Please supply a R/W filesystem or a path in this store which is writable");
      throw e;
    }

    // Directory ops
    try (StoreDurationInfo ignored = new StoreDurationInfo(getOut(),
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

      // creation timestamp
      long creationTime = System.currentTimeMillis();
      long closeTime;
      long completionTime;
      FSDataOutputStream data = null;
      subheading("Creating file %s", file);

      try (StoreDurationInfo ignored = new StoreDurationInfo(getOut(),
          "Creating file %s", file)) {
        data = fs.create(file, true);

        printStreamCapabilities(data, CapabilityKeys.OUTPUTSTREAM_CAPABILITIES);
        storeInfo.validateOutputStream(this, fs, file, data);

        subheading("Writing data to %s", file);
        data.writeUTF(HELLO);

        try {
          data.hflush();
          println("Stream does not reject hflush() calls");
        } catch (Exception e) {
          println("Stream rejects hflush() calls: %s", e);
        }
        try {
          data.hsync();
          println("Stream does not reject hsync() calls");
        } catch (Exception e) {
          println("Stream rejects hsync() calls: %s", e);
        }
        closeTime = System.currentTimeMillis();
        data.close();
        completionTime = System.currentTimeMillis();
        println("Output stream summary: %s", data);
      } finally {
        closeStream(data);
      }

      subheading("Listing %s", dir);

      try (StoreDurationInfo ignored = new StoreDurationInfo(getOut(),
          "ListFiles(%s)", dir)) {
        final RemoteIterator<LocatedFileStatus> listing =
            fs.listFiles(dir, false);
        boolean found = false;
        FileStatus stat = null;
        while (listing.hasNext()) {
          final LocatedFileStatus next = listing.next();
          println(" %s", next.getPath());
          if (file.equals(next.getPath())) {
            found = true;
            stat = next;
          }
        }
        if (!found) {
          error("listFiles(%s) failed to find created file %s", dir, file);
        }
      }

      subheading("Reading file %s", file);

      FSDataInputStream in = null;
      try (StoreDurationInfo ignored = new StoreDurationInfo(getOut(),
          "Reading file %s", file)) {
        in = fs.open(file);
        printStreamCapabilities(in, CapabilityKeys.INPUTSTREAM_CAPABILITIES);
        storeInfo.validateInputStream(this, fs, file, in.getWrappedStream());
        String utf = in.readUTF();
        in.close();
        println("input stream summary: %s", in);
        if (!HELLO.equals(utf)) {
          throw new StoreDiagException("Expected %s to contain the text %s"
              + " -but it has the text \"%s\"",
              HELLO, file, utf);
        }
      } finally {
        closeStream(in);
      }
      final FileStatus status = fs.getFileStatus(file);
      final String userName = UserGroupInformation.getCurrentUser().getShortUserName();
      if (!userName.equals(status.getOwner())) {
        warn("Expected file owner to be %s but was reported as %s in %s",
            userName, status.getOwner(), status);
      }
      final long modtime = status.getModificationTime();
      final long offsetFromCreation = modtime - creationTime;
      final long offsetFromClose = modtime - closeTime;
      final long offsetFromCompletion = modtime - completionTime;
      boolean closerToCompletion = offsetFromCompletion < offsetFromCreation;
      println("File modtime after creation = %,d millis,"
              + "\n\tafter close invoked = %,d millis"
              + "\n\tafter close completed = %,d millis",
          offsetFromCreation,
          offsetFromClose,
          offsetFromCompletion);
      if (offsetFromCreation < 0) {
        warn("Timestamp of created file is %,d milliseconds before the local clock",
            offsetFromCreation);
      } else {
        println("Timestamp of created file is %,d milliseconds after the local clock",
            offsetFromCreation);
        if (closerToCompletion) {
          println("The file timestamp is closer to the write completion time.");
          println("If the store is an object store, the object is\n"
              + "likely to have been created at the end of the write");
        }
      }

      // ask the fs for any validation here
      storeInfo.validateFile(this, fs, file, status);


      subheading("Renaming");

      // move the file into a subdir
      Path subdir = new Path(dir, "subdir");
      Path subdir2 = new Path(dir, "subdir2");
      Path subfile = new Path(subdir, "subfile");
      try (StoreDurationInfo ignored = new StoreDurationInfo(getOut(),
          "Renaming file %s under %s", file, subdir)) {
        fs.mkdirs(subdir);
        fs.rename(file, subfile);
        fs.rename(subdir, subdir2);
      }
      verifyPathNotFound(fs, subfile);
      // delete the file
      subheading("Deleting dir %s", subdir2);
      deleteDir(fs, subdir2);
      verifyPathNotFound(fs, subdir2);

      println("All read and write operations succeeded: good to go");
    } finally {
      // teardown: attempt to delete the directory
      subheading("Deleting directory %s", dir);
      deleteDir(fs, dir);
      if (!baseDirFound) {
        deleteDir(fs, baseDir);
      }
      fs.close();
    }
    return true;
  }


  /**
   * Read a file.
   */
  private boolean readFile(final FileSystem fs,
      final FileStatus status) throws IOException {
    boolean accessWasDenied = false;
    Path path = status.getPath();
    try (StoreDurationInfo ignored = new StoreDurationInfo(getOut(),
        "Reading file %s", path);
         FSDataInputStream in = fs.open(path)) {
      // read the first char or -1
      int c = in.read();
      println("First character of file %s is 0x%02x: '%s'",
          path,
          c,
          (c > ' ') ? Character.toString((char) c) : "(n/a)");
      printStreamCapabilities(in, CapabilityKeys.INPUTSTREAM_CAPABILITIES);
      println("Stream summary: %s", in);
    } catch (FileNotFoundException ex) {
      warn("file %s: not found/readable %s", path, ex);
    } catch (AccessDeniedException ex) {
      warn("client lacks access to file %s: %s", path, ex);
      accessWasDenied = true;
    }
    return accessWasDenied;
  }

  private void printStreamCapabilities(final StreamCapabilities in,
      final String[] capabilities) {
    println("Capabilities:");
    for (String s : capabilities) {
      if (in.hasCapability(s)) {
        println("    %s", s);
      }
    }
  }

  public void deleteDir(final FileSystem fs, final Path dir) {
    try (StoreDurationInfo ignored = new StoreDurationInfo(getOut(),
        "delete directory %s", dir)) {
      try {
        fs.delete(dir, true);
      } catch (Exception e) {
        LOG.warn("When deleting {}: ", dir, e);
      }
    }
  }

  protected void verifyPathNotFound(FileSystem fs, Path path)
      throws IOException {
    try (StoreDurationInfo ignored = new StoreDurationInfo(getOut(),
        "probing path %s", path)) {
      final FileStatus st = fs.getFileStatus(path);
      throw new StoreDiagException(
          "Found path which should be absent %s", st);
    } catch (FileNotFoundException ignored) {
    }
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
    } catch (Throwable e) {
      exitOnThrowable(e);
    }
  }

}
