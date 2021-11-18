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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.base.Function;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.StringUtils;

import static org.apache.hadoop.fs.store.StoreUtils.checkArgument;
import static org.apache.hadoop.util.VersionInfo.getDate;
import static org.apache.hadoop.util.VersionInfo.getProtocVersion;
import static org.apache.hadoop.util.VersionInfo.getSrcChecksum;
import static org.apache.hadoop.util.VersionInfo.getUser;
import static org.apache.hadoop.util.VersionInfo.getVersion;

public class DiagnosticsEntryPoint extends StoreEntryPoint implements Printout {

  public static final String CLASSPATH = "java.class.path";

  public static final String PRINCIPAL = "principal";

  public static final String REQUIRED = "required";

  public static final String MD5 = "5";

  public static final String JARS = "j";


  protected static final int HIDE_PREFIX = 2;

  protected static final int HIDE_SUFFIX = 4;

  protected static final int HIDE_THRESHOLD = HIDE_PREFIX + HIDE_SUFFIX;

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
    int prefix = HIDE_PREFIX;
    int suffix = HIDE_SUFFIX;
    if (len > HIDE_THRESHOLD) {
      b.append(value, 0, prefix);
      b.append(DiagnosticsEntryPoint.stars(len - prefix - suffix));
      b.append(value, len - suffix, len);
      safe = b.toString();
    } else {
      // short values get special treatment
      safe = DiagnosticsEntryPoint.stars(HIDE_THRESHOLD);
    }
    return String.format("\"%s\" [%d]", safe, len);
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
   * Print all JVM options.
   */
  protected void printJVMOptions() {
    heading("System Properties");
    Properties sysProps = System.getProperties();
    for (String s : DiagnosticsEntryPoint.sortKeys(sysProps.keySet())) {
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
  protected void printJARS(final boolean md5)
      throws IOException, NoSuchAlgorithmException {
    heading("JAR listing");
    final Map<String, String> jars = jarsOnClasspath();
    for (String s : DiagnosticsEntryPoint.sortKeys(jars.keySet())) {

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
        String hex = "";
        if (isFile) {
          hex = hash(file);
        }
        if (hex.isEmpty()) {
          // 32 spaces
          hex = "                                ";
        }
        text = hex + "  " + text;
      }
      println(text);
    }
  }

  /**
   * Print the environment variables.
   * This is an array of (name, obfuscate) entries.
   * @param vars variables.
   */
  public void printEnvVars(Object[][] vars) {
    lookupAndPrintSanitizedValues(vars, "Environment Variables",
        System::getenv);
  }

  /**
   * Print the environment variables.
   * This is an array of (name, obfuscate) entries.
   * @param vars variables.
   */
  public void printSystemProperties(Object[][] vars) {
    lookupAndPrintSanitizedValues(vars, "Selected System Properties",
        System::getProperty);
  }

  /**
   * Resolve and print values.
   * This is an array of (name, obfuscate) entries.
   * @param vars variables/properties.
   * @param section section name
   * @param lookup lookup function
   */
  private void lookupAndPrintSanitizedValues(Object[][] vars,
      String section,
      Function<String, String> lookup) {
    if (vars.length > 0) {
      heading(section);
      for (final Object[] option : vars) {
        String var = (String) option[0];
        if (var == null || var.isEmpty()) {
          continue;
        }
        String value = lookup.apply(var);
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
    return obfuscate ? DiagnosticsEntryPoint.sanitize(value) :
        ("\"" + value + "\"");
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
      if (origins != null && origins.length != 0) {
        source = "[" + StringUtils.join(",", origins) + "]";
      }
      full = option + " " + source;
    }
    println("%s = %s", key, full);
  }

  /**
   * Look at optional classes.
   * @param optionalClasses list of optional classes; may be null.
   * @return true if 1+ class was missing.
   */
  public boolean probeOptionalClasses(final String...optionalClasses) {
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
        println();
        println("At least one optional class was missing"
            + " -the filesystem client *may* still work");
      }
      return missing;
    } else {
      return false;
    }
  }

  public void probeRequiredClasses(final String... requiredClasses)
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
    probeResource("yarn-site.xml", false);
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
    CodeSource source = clazz.getProtectionDomain().getCodeSource();
    if (source != null) {
      println("       %s", source.getLocation());
    }
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

  protected String statusToString(FileStatus status) {
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
    /*return toHex(Files.getDigest(file, MessageDigest.getInstance("MD5")));*/
    //return "guava broke this, sorry";
    return "";
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
}
