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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.base.Function;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.store.StoreEntryPoint;

import static org.apache.hadoop.fs.store.StoreUtils.checkArgument;
import static org.apache.hadoop.fs.store.diag.OptionSets.SYSPROPS_TO_SKIP;
import static org.apache.hadoop.util.VersionInfo.getDate;
import static org.apache.hadoop.util.VersionInfo.getProtocVersion;
import static org.apache.hadoop.util.VersionInfo.getSrcChecksum;
import static org.apache.hadoop.util.VersionInfo.getUser;
import static org.apache.hadoop.util.VersionInfo.getVersion;

public class DiagnosticsEntryPoint extends StoreEntryPoint  {

  /** {@value}. */
  public static final String PRINCIPAL = "principal";

  /** {@value}. */
  public static final String REQUIRED = "required";

  /** {@value}. */
  public static final String MD5 = "5";

  /** {@value}. */
  public static final String JARS = "j";

  /**
   * Sort the keys.
   * @param keys keys to sort.
   * @return new set of sorted keys
   */
  public static Set<String> sortKeys(final Iterable<?> keys) {
    Set<String> sorted = new TreeSet<>();
    for (Object k : keys) {
      sorted.add(k.toString());
    }
    return sorted;
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
   * Print all JVM options except those excluded in the list
   * {@link OptionSets#SYSPROPS_TO_SKIP}.
   */
  protected final void printJVMOptions() {
    heading("System Properties");
    final List<String> skipList = Arrays.asList(SYSPROPS_TO_SKIP);
    Properties sysProps = System.getProperties();
    for (String s : DiagnosticsEntryPoint.sortKeys(sysProps.keySet())) {
      if (!skipList.contains(s)) {
        println("%s = \"%s\"", s, sysProps.getProperty(s));
      }
    }
  }

  /**
   * Print the JARS -but only the JARs, not the paths to them. And
   * sort the list.
   * @param md5 create MD5 checksums
   */
  protected final void printJARS(final boolean md5)
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
  public final void printEnvVars(Object[][] vars) {
    lookupAndPrintSanitizedValues(vars, "Environment Variables",
        System::getenv);
  }

  /**
   * Print the environment variables.
   * This is an array of (name, obfuscate) entries.
   * @param vars variables.
   */
  public final void printSystemProperties(Object[][] vars) {
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
  public final void lookupAndPrintSanitizedValues(Object[][] vars,
      String section,
      Function<String, String> lookup) {
    int index = 0;

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
        println("[%03d]  %s = %s", ++index, var, value);
      }
    }
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
      throws ClassNotFoundException, FileNotFoundException {
    if (requiredClasses.length > 0) {
      heading("Required Classes");
      println("All these classes must be on the classpath");
      println("");
      for (String classname : requiredClasses) {
        probeRequiredClass(classname, false);
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
    probeResource("core-default.xml", true);
    probeResource("core-site.xml", false);
    probeResource("hdfs-default.xml", false);
    probeResource("hdfs-site.xml", false);
    probeResource("mapred-default.xml", false);
    probeResource("mapred-site.xml", false);
    probeResource("yarn-default.xml", false);
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
   * @param verbose verbose output
   * @throws ClassNotFoundException if the class was not found.
   */
  public void probeRequiredClass(final String classname, boolean verbose)
      throws ClassNotFoundException, FileNotFoundException {
    String name = classname.trim();
    if (name.isEmpty()) {
      return;
    }
    println("class: %s", name);
    probeClassResource(name, true);
    Class<?> clazz = this.getClass().getClassLoader().loadClass(name);
    CodeSource source = clazz.getProtectionDomain().getCodeSource();
    if (source != null) {
      println("       %s", source.getLocation());
    }
    if (verbose) {
      println("       %s", clazz.getClassLoader());
    }

  }

  private void probeClassResource(final String classname,
      final boolean required) throws FileNotFoundException {
    String resource = classname.replace('.','/') + ".class";
    probeResource(resource, required);
  }

  /**
   * Look for a class; print its origin if found, else print the
   * fact that it is missing.
   * @param classname classname
   */
  public boolean probeOptionalClass(final String classname) {
    try {
      probeRequiredClass(classname, false);
      return true;
    } catch (LinkageError | Exception e) {
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
        suffix = "";
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
    final String cp = System.getProperty(OptionSets.CLASSPATH);
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
