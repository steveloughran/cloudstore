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

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class StoreUtils {

  /**
   * Take an exception from a Future and convert to an IOE.
   * @param ex exception
   * @return the extracted or wrapped exception
   * @throws RuntimeException if that is the cause
   * @throws IOException if the underlying ex was one, otherwise a wrapper
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
}
