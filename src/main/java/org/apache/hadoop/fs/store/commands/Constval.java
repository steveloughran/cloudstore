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

package org.apache.hadoop.fs.store.commands;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.fs.store.diag.DiagnosticsEntryPoint;
import org.apache.hadoop.fs.store.diag.StoreDiagException;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.StoreExitCodes.E_EXCEPTION_THROWN;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_NOT_FOUND;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_NOT_FOUND2;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_NO_ACCESS;

/**
 * Uses reflection to get the const value of a static final field.
 */
public class Constval extends DiagnosticsEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(Constval.class);

  public static final String USAGE
      = "Usage: constval class field";

  public Constval() {
    createCommandFormat(2, 2);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = processArgs(args, 2, 2, USAGE);
    String classname = paths.get(0);
    String field = paths.get(1);
    String value = lookupFieldValue(classname, field);
    println("Value of %s.%s = \"%s\"", classname, field, value);

    println();
    return 0;
  }

  /**
   * Get the value of a field.
   * @param classname class name
   * @param field field name
   * @return the value as a string,
   * with different exit codes.
   * @throws StoreDiagException failure
   */
  public static String lookupFieldValue(String classname, String field) throws StoreDiagException {
    final Class<?> clazz;
    try {
      clazz = Class.forName(classname);
    } catch (ClassNotFoundException e) {
      throw new StoreDiagException(E_NOT_FOUND,
          "Class not found: " + classname)
          .initCause(e);
    }
    final Field f;
    try {
      f = clazz.getField(field);
    } catch (NoSuchFieldException e) {
      throw new StoreDiagException(E_NOT_FOUND2,
          "Field not found: " + field + " in " + classname)
          .initCause(e);

    } catch (SecurityException e) {
      throw new StoreDiagException(E_NO_ACCESS,
          "No access to " + field + " in " + classname)
          .initCause(e);
    }
    // set it to accessible
    f.setAccessible(true);
    try {
      return f.get(null).toString();
    } catch (Throwable e) {
      throw new StoreDiagException(E_EXCEPTION_THROWN,
          "No access to " + field + " in " + classname + ": " + e)
          .initCause(e);
    }
  }

  /**
   * Get the value of a field, or return an empty optional if it fails.
   * @param classname classname
   * @param field field
   * @return const value
   */
  public static Optional<String> optionalValueOf(String classname,
      String field) {
    try {
      return Optional.of(lookupFieldValue(classname, field));
    } catch (StoreDiagException e) {
      LOG.error("Failed to get const value of {} in {}: {}", field, classname, e);
      return Optional.empty();
    }
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new Constval(), args);
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
