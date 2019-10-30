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

package org.apache.hadoop.fs.tools.cloudup;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;

final class UploadEntry implements Serializable, Comparable<UploadEntry> {

  public long getDuration() {
    return endTime -startTime;
  }

  public enum State {
    ready,
    queued,
    active,
    succeeded,
    failed
  }

  private State state= State.ready;

  /** Source. Must be absolute. */
  private final Path source;

  /** Size in bytes. */
  private long size;

  /**
   * Destination path. Need not be qualified for dest FS, but
   * must be absolute.
   */
  private Path dest;

  /**
   * Start time: millis.
   */
  private long startTime;

  /**
   * End time: millis.
   */
  private long endTime;

  /**
   * Exception, only non-null if {@code state == failed}.
   */
  private Exception exception;

  public UploadEntry(Path source) {
    this.source = source;
  }

  public UploadEntry(FileStatus status) {
    source = status.getPath();
    size = status.getLen();
  }

  /**
   * Is the upload in one of the active states?
   * @return true iff the upload is in succeeded or failed states
   */
  public boolean isCompleted() {
    return state == State.succeeded || state == State.failed;
  }

  /**
   * Is the file ready to upload?
   * @return
   */
  public boolean isReady() {
    return state == State.ready;
  }

  public State getState() {
    return state;
  }

  public void setState(State state) {
    this.state = state;
  }

  public boolean inState(State state) {
    return this.state == state;
  }

  public Path getSource() {
    return source;
  }

  public Path getDest() {
    return dest;
  }

  public void setDest(Path dest) {
    this.dest = dest;
  }

  public long getSize() {
    return size;
  }

  public long getStartTime() {
    return startTime;
  }

  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }

  public long getEndTime() {
    return endTime;
  }

  public void setEndTime(long endTime) {
    this.endTime = endTime;
  }

  public Exception getException() {
    return exception;
  }

  public void setException(Exception exception) {
    this.exception = exception;
  }

  /**
   * Equality checks (final) source field only.
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) { return true; }
    if (o == null || getClass() != o.getClass()) { return false; }
    UploadEntry that = (UploadEntry) o;
    return Objects.equals(source, that.source);
  }

  /**
   * Hash code uses checks (final) source field only.
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return Objects.hash(source);
  }

  /**
   * Comparator is on size.
   * @param o other entry
   * @return -1 if this object it smaller than the other, 1 if it is the other
   * way around, and 0 if they are equal.
   */
  @Override
  public int compareTo(@Nonnull UploadEntry o) {
    return Long.compare(size, o.getSize());
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(
        "UploadEntry{");
    sb.append("state=").append(state);
    sb.append(", source=").append(source);
    sb.append(", dest=").append(dest);
    sb.append(", size=").append(size);
    sb.append('}');
    return sb.toString();
  }

  static class SizeComparator implements Comparator<UploadEntry> {

    @Override
    public int compare(UploadEntry o1, UploadEntry o2) {
      return Long.compare(o1.getSize(), o2.getSize());

    }

  }

}
