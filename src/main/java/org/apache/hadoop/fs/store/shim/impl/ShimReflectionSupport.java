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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.fs.shim.api.IsImplemented;

import static org.apache.hadoop.fs.shim.impl.Invocation.unavailable;

/**
 * Shim utilities.
 */
public final class ShimReflectionSupport {

  private static final Logger LOG = LoggerFactory.getLogger(ShimReflectionSupport.class);

  /**
   * convert any wrapped exception to an IOE.
   * If there is no cause, convert the supplied exception instead.
   * @param e exception
   * @return an IOException to throw.
   */
  public static IOException unwrapAndconvertToIOException(Exception e) {
    Throwable cause = e.getCause();
    return convertUnwrappedExceptionToIOE(cause != null ? cause : e);
  }

  /**
   * Convert to an IOE and return for throwing.
   * Wrapper exceptions (invocation, execution)
   * are unwrapped first.
   * If the cause is actually a RuntimeException
   * other than UncheckedIOException
   * or Error, it is thrown
   * @param e exception
   * @throws RuntimeException if that is the type
   * @throws Error if that is the type
   */
  public static IOException convertToIOException(Exception e) {
    if (e instanceof InvocationTargetException
        || e instanceof ExecutionException) {
      return unwrapAndconvertToIOException(e);
    } else {
      return convertUnwrappedExceptionToIOE(e);
    }
  }

  /**
   * Convert to an IOE and return for throwing.
   * If the cause is actually a RuntimeException
   * other than UncheckedIOException
   * or Error, it is thrown.
   * @param thrown exception
   * @throws RuntimeException if that is the type of {@code thrown}.
   * @throws Error if that is the type  of {@code thrown}.
   */
  public static IOException convertUnwrappedExceptionToIOE(final Throwable thrown) {
    if (thrown instanceof UncheckedIOException) {
      return ((UncheckedIOException) thrown).getCause();
    }
    if (thrown instanceof RuntimeException) {
      throw (RuntimeException) thrown;
    }
    if (thrown instanceof Error) {
      throw (Error) thrown;
    }
    if (thrown instanceof IOException) {
      return (IOException) thrown;
    }
    return new IOException(thrown);
  }

  /**
   * Get a method from the source class, or null if not found.
   * @param source source
   * @param name method name
   * @param parameterTypes parameters
   * @return the method or null
   */
  public static Method getMethod(Class<?> source, String name, Class<?>... parameterTypes) {
    try {
      return source.getMethod(name, parameterTypes);
    } catch (NoSuchMethodException | SecurityException e) {
      LOG.debug("Class {} does not implement {}", source, name);
      return null;
    }
  }

  /**
   * Get a method from the source class, or null if not found.
   * @param source source
   * @param name method name
   * @param parameterTypes parameters
   * @return the method or null
   */
  public static <T> Invocation<T> getInvocation(
      Class<?> source, String name, Class<?>... parameterTypes) {
    return (Invocation<T>) loadInvocation(source, null, name, parameterTypes);
  }

  /**
   * Get an invocation from the source class, which will be unavailable() if
   * the class is null or the method isn't found.
   *
   * @param <T> return type
   * @param source source
   * @param returnType return type class for the compiler to be happy
   * @param name method name
   * @param parameterTypes parameters
   *
   * @return the method or "unavailable"
   */
  public static <T> Invocation<T> loadInvocation(
      Class<?> source, Class<? extends T> returnType, String name, Class<?>... parameterTypes) {
    if (source == null) {
      return unavailable(name);
    }
    try {
      return new Invocation<T>(name, source.getMethod(name, parameterTypes));
    } catch (NoSuchMethodException | SecurityException e) {
      LOG.debug("Class {} does not implement {}", source, name);
      return unavailable(name);
    }
  }
  /**
   * Get an invocation from the source class, which will be unavailable() if
   * the class is null or the method isn't found.
   *
   * @param source source
   * @param parameterTypes parameters
   *
   * @return the method or null
   */
  public static <T> Constructor<?> ctor(
      Class<?> source, Class<?>... parameterTypes) {
    if (source == null) {
      return null;
    }
    try {
      return source.getConstructor(parameterTypes);
    } catch (NoSuchMethodException | SecurityException e) {

      LOG.debug("Could not load constructor for {}", source, e);
      return null;
    }
  }

  /**
   * Get the method as a possibly empty Optional value.
   * @param source source
   * @param name method name
   * @param parameterTypes parameters
   * @return the method or Optional.empty()
   */
  public static Optional<Method> loadMethodOptional(Class<?> source,
      String name,
      Class<?>... parameterTypes) {
    return Optional.ofNullable(getMethod(source, name, parameterTypes));
  }

  /**
   * Invoke a method with exception unwrap/uprate.
   * If the method is null, raise UnsupportedOperationException
   * @param operation operation name for errors
   * @param instance instance to invoke
   * @param method method, may be null
   * @param parameters parameters
   * @return the result
   * @throws UnsupportedOperationException if the method is null
   * @throws RuntimeException for all RTEs raised by invoked methods except UncheckedIOEs
   * @throws IOException when converting/unwrappping thrown exceptions
   */
  public static Object invokeOperation(
      String operation,
      Object instance,
      Method method,
      Object... parameters) throws IOException {
    if (method == null) {
      throw new UnsupportedOperationException("No " +
          operation + " in " + instance);
    }
    try {
      return method.invoke(instance, parameters);
    } catch (IllegalAccessException
             | InvocationTargetException
             | IllegalArgumentException  ex) {
      throw convertToIOException(ex);
    }
  }

  /**
   * Invoke a method with exception unwrap/uprate.
   * If the method is null, raise UnsupportedOperationException
   * @param operation operation name for errors
   * @param instance instance to invoke
   * @param method method, may be null
   * @param parameters parameters
   * @return the result
   * @throws UnsupportedOperationException if the method is null
   * @throws RuntimeException for all RTEs raised by invoked methods except UncheckedIOEs
   * @throws UncheckedIOException wrapped IOE
   */
  public static Object invokeUnchecked(
      String operation,
      Object instance,
      Method method,
      Object... parameters) {
    if (method == null) {
      throw new UnsupportedOperationException("No " +
          operation + " in " + instance);
    }
    try {
      return method.invoke(instance, parameters);
    }  catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException)cause;
      } else if (cause instanceof IOException) {
        throw new UncheckedIOException((IOException) cause);
      } else {
        throw new RuntimeException(cause);
      }
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * get a list of which features are implemented in the underlying
   * instance.
   * @param source
   * @param features list of features
   * @return enumeration for string value
   */
  public static <T> String availability(final IsImplemented source, String... features) {
    StringBuilder result = new StringBuilder();
    for (String feature : features) {
      result.append(feature)
          .append("=")
          .append(source.isImplemented(feature))
          .append("\n");
    }
    return result.toString();
  }
}
