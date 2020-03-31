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
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.RemoteIterator;

public class StoreLambda {

  /**
   * An interface for use in lambda-expressions working with
   * directory tree listings.
   */
  @FunctionalInterface
  public interface CallOnLocatedFileStatus {

    void call(LocatedFileStatus status) throws IOException;
  }

  /**
   * An interface for use in lambda-expressions working with
   * directory tree listings.
   */
  @FunctionalInterface
  public interface LocatedFileStatusMap<T> {

    T call(LocatedFileStatus status) throws IOException;
  }

  /**
   * Apply an operation to every {@link LocatedFileStatus} in a remote
   * iterator.
   * @param iterator iterator from a list
   * @param eval closure to evaluate
   * @return the number of files processed
   * @throws IOException anything in the closure, or iteration logic.
   */
  public static long applyLocatedFiles(
      RemoteIterator<LocatedFileStatus> iterator,
      CallOnLocatedFileStatus eval) throws IOException {
    long count = 0;
    while (iterator.hasNext()) {
      count++;
      eval.call(iterator.next());
    }
    return count;
  }

  /**
   * Map an operation to every {@link LocatedFileStatus} in a remote
   * iterator, returning a list of the results.
   * @param <T> return type of map
   * @param iterator iterator from a list
   * @param eval closure to evaluate
   * @return the list of mapped results.
   * @throws IOException anything in the closure, or iteration logic.
   */
  public static <T> List<T> mapLocatedFiles(
      RemoteIterator<LocatedFileStatus> iterator,
      LocatedFileStatusMap<T> eval) throws IOException {
    final List<T> results = new ArrayList<>();
    applyLocatedFiles(iterator,
        (s) -> results.add(eval.call(s)));
    return results;
  }


}
