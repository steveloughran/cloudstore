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

package org.apache.hadoop.fs.shim.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;

/**
 * A method which can be invoked.
 * @param <T> return type.
 */
public final class Invocation<T> {

  /**
   * Method name for error messages.
   */
  private final String name;

  /**
   * Method to invoke.
   */
  private final Method method;

  /**
   * Create.
   * @param name invocation name for error messages.
   * @param method method to invoke.
   */
  public Invocation(final String name, final Method method) {
    this.name = name;
    this.method = method;
  }

  /**
   * Is the method available.
   * @return true if the invocation is available.
   */
  public boolean available() {
    return method != null;
  }

  /**
   * Invoke the method with exception unwrap/uprate.
   * If {@link #method} is null, raise UnsupportedOperationException
   * @param instance instance to invoke
   * @param parameters parameters
   * @return the result
   * @throws UnsupportedOperationException if the method is null
   * @throws RuntimeException for all RTEs raised by invoked methods except UncheckedIOEs
   * @throws IOException when converting/unwrappping thrown exceptions
   */
 public T invoke(
     final Object instance,
     final Object... parameters) throws IOException {
    return (T) ShimReflectionSupport.invokeOperation(name, instance, method, parameters);
  }

  /**
   * Invoke the method with exception unwrap/uprate.
   * If {@link #method} is null, raise UnsupportedOperationException
   * @param instance instance to invoke
   * @param parameters parameters
   * @return the result
   * @throws UnsupportedOperationException if the method is null
   * @throws RuntimeException for all RTEs raised by invoked methods except UncheckedIOEs
   * @throws UncheckedIOException wrapped IOE
   */
 public T invokeUnchecked(
     final Object instance,
     final Object... parameters) {
    return (T) ShimReflectionSupport.invokeUnchecked(name, instance, method, parameters);
  }

  /**
   * Generate an invocation which is always unavailable.
   * @param name name for the exception text.
   * @return an invocation which always raises
   */
  public static <T> Invocation<T> unavailable(String name) {
    return new Invocation<T>(name, null);
  }
}
