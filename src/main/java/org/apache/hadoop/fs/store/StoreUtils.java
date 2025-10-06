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

package org.apache.hadoop.fs.store;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.StorageUnit;
import org.apache.hadoop.util.ExitUtil;

import static org.apache.hadoop.fs.statistics.IOStatisticsLogging.ioStatisticsToPrettyString;
import static org.apache.hadoop.fs.statistics.IOStatisticsSupport.retrieveIOStatistics;

public class StoreUtils {

  /** {@value}. */
  protected static final int HIDE_PREFIX = 2;

  /** {@value}. */
  protected static final int HIDE_SUFFIX = 4;

  /** {@value}. */
  protected static final int HIDE_THRESHOLD = HIDE_PREFIX * 2 + HIDE_SUFFIX;

  /** life without Guava. */
  public static void checkArgument(boolean condition, String text) {
    if (!condition) {
      throw new IllegalArgumentException(text);
    }
  }

  public static void checkState(boolean condition, String text) {
    if (!condition) {
      throw new IllegalStateException(text);
    }
  }


  /**
   * Take an exception from a Future and convert to an IOE.
   * @param ex exception
   * @return the extracted or wrapped exception
   */
  public static IOException uprate(ExecutionException ex) {
    Throwable cause = ex.getCause();
    if (cause instanceof RuntimeException) {
      throw (RuntimeException) cause;
    }
    if (cause instanceof IOException) {
      return (IOException) cause;
    }
    return new IOException(cause.toString(), cause);
  }

  /**
   * Await a future completing; uprate failures.
   * @param future future to exec
   * @param <T> return type
   * @return the result
   * @throws IOException failure
   * @throws InterruptedException work interrupted
   */
  public static <T> T await(Future<T> future)
      throws IOException, InterruptedException {
    try {
      return future.get();
    } catch (ExecutionException e) {
      throw uprate(e);
    }
  }

  /**
   * split a key=value pair. Why not return a Pair class? Commons-lang
   * versions: don't want to commit.
   * @param param param to split
   * @return a param split key = val,
   */
  public static Map.Entry<String, String> split(String param, String defVal) {
    int split = param.indexOf('=');
    int len = param.length();
    if (split == 0 || split + 1 == len) {
      throw new ExitUtil.ExitException(StoreEntryPoint.EXIT_USAGE,
          "Unable to parse argument " + param);
    }
    String key = split > 0 ? param.substring(0, split) : param;
    String value = split > 0 ? param.substring(split + 1, len) : defVal;
    return new StringPair(key, value);
  }

  /**
   * Concatenate two arrays into a new one.
   * If either array is empty, the other array is returned; no new
   * array is created.
   * see https://stackoverflow.com/questions/80476/how-can-i-concatenate-two-arrays-in-java
   * @param left left side
   * @param right right side
   * @param <T> type of arrays
   * @return the expanded array.
   */
  public static <T> T[] cat(T[] left, T[] right) {
    int aLen = left.length;
    int bLen = right.length;
    if (aLen == 0) {
      return right;
    }
    if (bLen == 0) {
      return left;
    }

    T[] dest = (T[]) Array.newInstance(left.getClass().getComponentType(),
        aLen + bLen);
    System.arraycopy(left, 0, dest, 0, aLen);
    System.arraycopy(right, 0, dest, aLen, bLen);

    return dest;
  }

  /**
   * get the storage size from a string, uses M, G, T etc
   * @param size data size
   * @param storageUnit desired size unit.
   * @return size as a double.
   */
  public static double getDataSize(final String size, StorageUnit storageUnit) {
    double uploadSize;

    String s = size.trim().toUpperCase(Locale.ROOT);
    try {
      // look for a long value,
      uploadSize = Long.parseLong(s);
    } catch (NumberFormatException e) {
      // parse the size values via Configuration
      // this is only possible on hadoop 3.1+.
      if (!s.endsWith("B")) {
        s = s + "B";
      }
      final Configuration sizeConf = new Configuration(false);


      // upload in MB.
      uploadSize = sizeConf.getStorageSize("size", s, storageUnit);
    }
    return uploadSize;
  }

  /**
   * Create a list of star characters.
   * @param n number to create.
   * @return a string of stars
   */
  public static String stars(int n) {
    StringBuilder b = new StringBuilder(n);
    for (int i = 0; i < n; i++) {
      b.append('*');
    }
    return b.toString();
  }

  /**
   * Sanitize a sensitive option.
   * @param value option value.
   * @param hide flag to hide everything
   * @return sanitized value.
   */
  public static String sanitize(final String value, boolean hide) {
    String safe;
    int len = value.length();
    if (!hide && len > HIDE_THRESHOLD) {
      StringBuilder b = new StringBuilder(len);
      int prefix = HIDE_PREFIX;
      int suffix = HIDE_SUFFIX;
      b.append(value, 0, prefix);
      b.append(stars(len - prefix - suffix));
      b.append(value, len - suffix, len);
      safe = b.toString();
    } else {
      // short values get special treatment
      safe = stars(HIDE_THRESHOLD);
    }
    return String.format("\"%s\" [%d]", safe, len);
  }

  /**
   * Get any IOStatistics from an object.
   * @param source source
   * @return any IOStats, prettified -else ""
   */
  public static String prettyIOStatistics(final Object source) {
    return ioStatisticsToPrettyString(retrieveIOStatistics(source));
  }

  public static final class StringPair implements Map.Entry<String, String> {

    private String key, value;

    private StringPair(final String left, final String right) {
      this.key = left;
      this.value = right;
    }

    public String getKey() {
      return key;
    }

    public String getValue() {
      return value;
    }

    @Override
    public String setValue(final String value) {
      this.value = value;
      return value;
    }
  }

  /**
   * Read lines, skipping those with # or blank.
   * (generated by copilot.)
   * @param file path to file
   * @return the list of lines
   * @throws IOException failure to read
   */
  public static List<String> readLines(File file) throws IOException {
    List<String> lines = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (!line.trim().isEmpty() && !line.trim().startsWith("#")) {
          lines.add(line);
        }
      }
    }
    return lines;
  }
}
