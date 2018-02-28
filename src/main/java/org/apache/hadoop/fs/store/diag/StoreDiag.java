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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang.StringUtils;
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
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.util.VersionInfo.getDate;
import static org.apache.hadoop.util.VersionInfo.getProtocVersion;
import static org.apache.hadoop.util.VersionInfo.getSrcChecksum;
import static org.apache.hadoop.util.VersionInfo.getUser;
import static org.apache.hadoop.util.VersionInfo.getVersion;

public class StoreDiag extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(StoreDiag.class);

  private static final String HELLO = "Hello";

  protected static final int THRESHOLD = 4;


  CommandFormat commandFormat = new CommandFormat(0, Integer.MAX_VALUE);


  // Exit codes
  static final int SUCCESS = 0;

  static final int E_USAGE = 42;

  static final int ERROR = -1;

  static final String USAGE = "Usage: StoreDiag <filesystem>";

  private void printJVMOptions() {
    heading("System Properties");
    Properties sysProps = System.getProperties();
    for (String s : sortKeys(sysProps.keySet())) {
      println("%s = \"%s\"", s, sysProps.getProperty(s));
    }
  }

  private TreeSet<String> sortKeys(final Iterable<?> keySet) {
    TreeSet<String> sorted = new TreeSet<>();
    for (Object k : keySet) {
      sorted.add(k.toString());
    }
    return sorted;
  }

  private void printEnvVars(Object[][] vars) {
    if (vars.length > 9) {
      heading("Environment Variables");
      for (final Object[] option : vars) {
        String var = (String) option[0];
        String value = System.getenv(var);
        if (value != null) {
          value = maybeSanitize(value, (Boolean) option[1]);
        }
        println("%s=%s", var, value);
      }
    }
  }

  private void printOptions(Configuration conf, Object[][] options) {
    if (options.length > 0) {
      heading("Selected and Sanitized Configuration Options");
      for (final Object[] option : options) {
        printOption(conf, (String) option[0], (Boolean) option[1]);
      }
    }
  }

  private String maybeSanitize(String option, boolean sensitive) {
    return sensitive ? sanitize(option) : option;
  }

  private String sanitize(String option) {
    String r = option;
    int len = r.length();
    if (len > THRESHOLD) {
      StringBuilder b = new StringBuilder(len);
      b.append(option.charAt(0));
      for (int i = 1; i < len - 1; i++) {
        b.append('*');
      }
      b.append(option.charAt(len - 1));
      option = b.toString();
    } else {
      // short values get special treatment
      option = "**";
    }
    return option;
  }


  private void printOption(Configuration conf, String key, boolean sensitive) {
    if (key.isEmpty()) {
      return;
    }
    String option = conf.get(key);
    String full;
    if (option == null) {
      full = "(unset)";
    } else {
      String source = "";
      option = maybeSanitize(option, sensitive);
      String[] origins = conf.getPropertySources(key);
      if (origins.length !=0) {
        source = " [" + StringUtils.join(origins, ",") + "]";
      }
      full = '"' + option + '"' + source;
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
    heading("Hadoop information");
    println("  Hadoop %s", getVersion());
    println("  Compiled by %s on %s", getUser(), getDate());
    println("  Compiled with protoc %s", getProtocVersion());
    println("  From source with checksum %s", getSrcChecksum());


    Configuration conf = getConf();
    // path on the CLI
    Path path = new Path(paths.get(0));

    // and its FS URI
    URI fsURI = path.toUri();

    heading("Diagnostics for filesystem %s", fsURI);

    StoreDiagnosticsInfo store;
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
    default:
      store = new StoreDiagnosticsInfo(fsURI);
    }

    println("%s\n%s\n%s",
        store.getName(), store.getDescription(), store.getHomepage());

    printJVMOptions();

    conf = store.patchConfigurationToInitalization(conf);

    printOptions(conf, store.getFilesystemOptions());

    printEnvVars(store.getEnvVars());


    String[] requiredClasses = store.getClassnames(conf);
    if (requiredClasses.length > 0) {
      heading("Required Classes");
      println("All these classes must be on the classpath");
      println("");
      for (String classname : requiredClasses) {
        probeOneClassname(classname);
      }
    }

    String[] optionalClasses = store.getOptionalClassnames(conf);
    if (optionalClasses.length > 0) {
      heading("Optional Classes");

      println("These classes are needed in some versions of Hadoop.");
      println("And/or for optional features to work.");
      println("");

      boolean missing = false;
      for (String classname : optionalClasses) {
        missing |= probeOptionalClassname(classname);
      }
      if (missing) {
        println("%nAt least one optional class was missing"
            + " -the filesystem client *may* still work");
      }
    }

    heading("Endpoints");

    List<URI> endpoints = store.listEndpointsToProbe(conf);

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

    // and the filesystem operations
    executeFileSystemOperations(conf, path);

    // Validate parameters.
    return SUCCESS;
  }

  private void probeOneEndpoint(URI endpoint)
      throws IOException, URISyntaxException {
    final String host = endpoint.getHost();

    heading("Endpoint: %s", endpoint);
    InetAddress addr = InetAddress.getByName(host);
    println("Canonical hostname %s\n  IP address %s",
        addr.getCanonicalHostName(),
        addr.getHostAddress());
    if ("0.0.0.0".equals(host)) {
      return;
    }
    URL url = endpoint.toURL();

    List<Proxy> proxies = ProxySelector.getDefault()
        .select(url.toURI());
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
            StringUtils.join(headerFields.get(header), ","));
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    IOUtils.copyBytes(
        success ? conn.getInputStream() : conn.getErrorStream(),
        out, 4096, true);
    String body = out.toString();
    println("%n%s%n",
        body.substring(0, Math.min(1024,body.length())));
  }

  /**
   * Look for a class; print its origin.
   * @param classname classname
   * @throws ClassNotFoundException if the class was not found.
   */
  private void probeOneClassname(final String classname)
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
  private boolean probeOptionalClassname(final String classname) {
    try {
      probeOneClassname(classname);
      return true;
    } catch (ClassNotFoundException e) {
      println("       Not found on classpath: %s", classname);
      return false;
    }
  }

  /**
   * Execute the FS level operations, one by one.
   */
  private void executeFileSystemOperations(final Configuration conf,
      final Path path) throws IOException {

    heading("Test filesystem %s", path);

    println("This call tests a set of operations against the filesystem");
    println("Starting with some read operations, then trying to write%n");

    FileSystem fs = path.getFileSystem(conf);

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


    // now create a file underneath and look at it.


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
    try (DurationInfo ignored = new DurationInfo(LOG,
        "Creating a directory %s", dir)) {
      FileStatus status = fs.getFileStatus(dir);
      if (!status.isDirectory()) {
        throw new IOException("Not a directory: " + status);
      }
    }

    // after this point the directory is created, so delete it
    // teardown
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
      try (DurationInfo ignored = new DurationInfo(LOG,
          "Deleting file %s", file)) {
        fs.delete(file, true);
      }
    } finally {
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
   * Parse CLI arguments and returns the position arguments.
   * The options are stored in {@link #commandFormat}.
   *
   * @param args command line arguments.
   * @return the position arguments from CLI.
   */
  List<String> parseArgs(String[] args) {
    return args.length > 0 ? commandFormat.parse(args, 0)
        : new ArrayList<String>(0);
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
      exit(ERROR, e.toString());
    }
  }

  protected static void exit(int status, String text) {
    ExitUtil.terminate(status, text);
  }
}
