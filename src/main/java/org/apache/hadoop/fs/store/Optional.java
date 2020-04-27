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

import java.util.NoSuchElementException;

/**
 * Full minimal implementation of some methods from optional.
 * Because of java 8 to java 7 backport.
 * @param <T>
 */
public class Optional<T> {

  private final T value;
  private static final Optional<?> EMPTY = new Optional<>();

  public Optional(T value) {
    this.value = value;
  }

  public Optional() {
    this.value = null;
  }

  public static <T> Optional<T> ofNullable(T value) {
    return (Optional<T>) (value == null ? empty() : of(value));
  }

  public static <T> Optional<T> empty() {
    @SuppressWarnings("unchecked")
    Optional<T> t = (Optional<T>) EMPTY;
    return t;
  }

  public static <T> Optional<T> of(T value) {
    return new Optional<>(value);
  }

  public T get() {
    if (value == null) {
      throw new NoSuchElementException("No value present");
    }
    return value;
  }

  public boolean isPresent() {
    return value != null;
  }

}
