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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.hadoop.conf.StorageUnit;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.StoreUtils.StringPair;
import org.apache.hadoop.util.ExitUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestStoreUtils {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  // ---------- sanitize ----------

  @Test
  public void sanitizeHideTrueMasksEverything() {
    String secret = "AKIAIOSFODNN7EXAMPLE";
    String out = StoreUtils.sanitize(secret, true);
    assertThat(out).isEqualTo("\"************\" [" + secret.length() + "]").doesNotContain("AKIA")
        .doesNotContain("EXAMPLE");
  }

  @Test
  public void sanitizeShortValueIsFullyMasked() {
    // length 8 <= HIDE_THRESHOLD (12); strict > means fully masked
    String v = "12345678";
    assertThat(StoreUtils.sanitize(v, false)).isEqualTo("\"************\" [8]");
  }

  @Test
  public void sanitizeEmptyStringIsFullyMasked() {
    assertThat(StoreUtils.sanitize("", false)).isEqualTo("\"************\" [0]");
  }

  @Test
  public void sanitizeJustAboveThresholdShowsPrefixAndSuffix() {
    // length 13 (just above HIDE_THRESHOLD 12): 2-char prefix + 7 stars + 4-char suffix
    String v = "abcdefghijklm";
    assertThat(StoreUtils.sanitize(v, false)).isEqualTo("\"ab*******jklm\" [13]");
  }

  @Test
  public void sanitizeLongValueRetainsPrefixAndSuffixOnly() {
    String v = "AKIAIOSFODNN7EXAMPLEKEYEXTRA1234567890XX"; // 40 chars
    String out = StoreUtils.sanitize(v, false);
    assertThat(out).startsWith("\"AK").endsWith("90XX\" [40]");
    // 40 - 2 - 4 = 34 stars in the middle
    long stars = out.chars().filter(c -> c == '*').count();
    assertThat(stars).isEqualTo(34L);
  }

  // ---------- stars ----------

  @Test
  public void starsZero() {
    assertThat(StoreUtils.stars(0)).isEmpty();
  }

  @Test
  public void starsN() {
    assertThat(StoreUtils.stars(5)).isEqualTo("*****");
  }

  // ---------- split ----------

  @Test
  public void splitKeyValue() {
    Map.Entry<String, String> p = StoreUtils.split("foo=bar", "default");
    assertThat(p.getKey()).isEqualTo("foo");
    assertThat(p.getValue()).isEqualTo("bar");
  }

  @Test
  public void splitOnlyFirstEqualsCounts() {
    Map.Entry<String, String> p = StoreUtils.split("foo=bar=baz", "default");
    assertThat(p.getKey()).isEqualTo("foo");
    assertThat(p.getValue()).isEqualTo("bar=baz");
  }

  @Test
  public void splitNoEqualsUsesDefault() {
    Map.Entry<String, String> p = StoreUtils.split("foo", "default");
    assertThat(p.getKey()).isEqualTo("foo");
    assertThat(p.getValue()).isEqualTo("default");
  }

  @Test
  public void splitNoEqualsNullDefault() {
    Map.Entry<String, String> p = StoreUtils.split("foo", null);
    assertThat(p.getKey()).isEqualTo("foo");
    assertThat(p.getValue()).isNull();
  }

  @Test
  public void splitLeadingEqualsRejected() {
    assertThatThrownBy(() -> StoreUtils.split("=bar", "x"))
        .isInstanceOf(ExitUtil.ExitException.class).hasMessageContaining("=bar")
        .extracting("status").isEqualTo(StoreEntryPoint.EXIT_USAGE);
  }

  @Test
  public void splitTrailingEqualsRejected() {
    assertThatThrownBy(() -> StoreUtils.split("foo=", "x"))
        .isInstanceOf(ExitUtil.ExitException.class).extracting("status")
        .isEqualTo(StoreEntryPoint.EXIT_USAGE);
  }

  @Test
  public void splitBareEqualsRejected() {
    assertThatThrownBy(() -> StoreUtils.split("=", "x")).isInstanceOf(ExitUtil.ExitException.class);
  }

  // ---------- StringPair ----------

  @Test
  public void stringPairSetValueMutates() {
    StringPair pair = (StringPair) StoreUtils.split("k=v", null);
    assertThat(pair.setValue("new")).isEqualTo("new");
    assertThat(pair.getValue()).isEqualTo("new");
    assertThat(pair.getKey()).isEqualTo("k");
  }

  // ---------- cat ----------

  @Test
  public void catTwoNonEmpty() {
    String[] left = {"a", "b"};
    String[] right = {"c", "d", "e"};
    String[] out = StoreUtils.cat(left, right);
    assertThat(out).containsExactly("a", "b", "c", "d", "e");
    assertThat(out).isNotSameAs(left).isNotSameAs(right);
  }

  @Test
  public void catEmptyLeftReturnsRight() {
    String[] left = new String[0];
    String[] right = {"c"};
    assertThat(StoreUtils.cat(left, right)).isSameAs(right);
  }

  @Test
  public void catEmptyRightReturnsLeft() {
    String[] left = {"a"};
    String[] right = new String[0];
    assertThat(StoreUtils.cat(left, right)).isSameAs(left);
  }

  @Test
  public void catBothEmpty() {
    String[] left = new String[0];
    String[] right = new String[0];
    String[] out = StoreUtils.cat(left, right);
    assertThat(out).isEmpty();
    assertThat(out).isSameAs(right);
  }

  @Test
  public void catPreservesComponentType() {
    Integer[] left = {1, 2};
    Integer[] right = {3};
    Integer[] out = StoreUtils.cat(left, right);
    assertThat(out).containsExactly(1, 2, 3);
    assertThat(out.getClass()).isEqualTo(Integer[].class);
  }

  // ---------- getDataSize ----------

  @Test
  public void getDataSizePlainBytes() {
    assertThat(StoreUtils.getDataSize("1024", StorageUnit.BYTES)).isEqualTo(1024.0);
  }

  @Test
  public void getDataSizePlainLongIgnoresUnit() {
    // bare numeric values bypass the unit-aware parser and are returned verbatim
    assertThat(StoreUtils.getDataSize("1024", StorageUnit.KB)).isEqualTo(1024.0);
  }

  @Test
  public void getDataSizeMegabytesAutoAppendsB() {
    assertThat(StoreUtils.getDataSize("1M", StorageUnit.BYTES)).isEqualTo(1024.0 * 1024.0);
  }

  @Test
  public void getDataSizeMbInMb() {
    assertThat(StoreUtils.getDataSize("1MB", StorageUnit.MB)).isEqualTo(1.0);
  }

  @Test
  public void getDataSizeMixedCaseWhitespace() {
    assertThat(StoreUtils.getDataSize("  2g  ", StorageUnit.BYTES))
        .isEqualTo(2.0 * 1024.0 * 1024.0 * 1024.0);
  }

  @Test
  public void getDataSizeLowerCaseK() {
    assertThat(StoreUtils.getDataSize("1k", StorageUnit.BYTES)).isEqualTo(1024.0);
  }

  // ---------- readLines ----------

  @Test
  public void readLinesSkipsBlanksAndComments() throws Exception {
    File f = tmp.newFile("lines.txt");
    Files.write(f.toPath(),
        ("# a comment\n" + "foo\n" + "\n" + "  # indented comment\n" + "bar\n" + "   \n" + "baz\n")
            .getBytes(StandardCharsets.UTF_8));
    List<String> lines = StoreUtils.readLines(f);
    assertThat(lines).containsExactly("foo", "bar", "baz");
  }

  @Test
  public void readLinesEmptyFile() throws Exception {
    File f = tmp.newFile("empty.txt");
    assertThat(StoreUtils.readLines(f)).isEmpty();
  }

  @Test
  public void readLinesMissingFileThrows() {
    File f = new File(tmp.getRoot(), "does-not-exist");
    assertThatThrownBy(() -> StoreUtils.readLines(f)).isInstanceOf(FileNotFoundException.class);
  }

  // ---------- isNullOrEmpty ----------

  @Test
  public void isNullOrEmptyNull() {
    assertThat(StoreUtils.isNullOrEmpty(null)).isTrue();
  }

  @Test
  public void isNullOrEmptyEmpty() {
    assertThat(StoreUtils.isNullOrEmpty("")).isTrue();
  }

  @Test
  public void isNullOrEmptyWhitespaceIsNotEmpty() {
    assertThat(StoreUtils.isNullOrEmpty(" ")).isFalse();
  }

  @Test
  public void isNullOrEmptyNonEmpty() {
    assertThat(StoreUtils.isNullOrEmpty("x")).isFalse();
  }

  @Test
  public void isNullOrEmptyStringBuilder() {
    assertThat(StoreUtils.isNullOrEmpty(new StringBuilder("x"))).isFalse();
    assertThat(StoreUtils.isNullOrEmpty(new StringBuilder())).isTrue();
  }

  // ---------- uprate ----------

  @Test
  public void uprateIOExceptionReturnedAsIs() {
    IOException ioe = new IOException("boom");
    ExecutionException ee = new ExecutionException(ioe);
    assertThat(StoreUtils.uprate(ee)).isSameAs(ioe);
  }

  @Test
  public void uprateIOExceptionSubclassPreserved() {
    FileNotFoundException fnf = new FileNotFoundException("404");
    ExecutionException ee = new ExecutionException(fnf);
    assertThat(StoreUtils.uprate(ee)).isSameAs(fnf);
  }

  @Test
  public void uprateRuntimeExceptionRethrown() {
    RuntimeException rte = new IllegalStateException("bad");
    ExecutionException ee = new ExecutionException(rte);
    assertThatThrownBy(() -> StoreUtils.uprate(ee)).isSameAs(rte);
  }

  @Test
  public void uprateCheckedNonIoeWrapped() {
    Exception cause = new Exception("checked");
    ExecutionException ee = new ExecutionException(cause);
    IOException out = StoreUtils.uprate(ee);
    assertThat(out).isInstanceOf(IOException.class).hasCause(cause);
    assertThat(out.getMessage()).contains(cause.toString());
  }

  // ---------- await ----------

  @Test
  public void awaitReturnsValue() throws Exception {
    Future<String> f = CompletableFuture.completedFuture("ok");
    assertThat(StoreUtils.await(f)).isEqualTo("ok");
  }

  @Test
  public void awaitPropagatesIOException() {
    IOException ioe = new IOException("io-fail");
    CompletableFuture<String> f = new CompletableFuture<>();
    f.completeExceptionally(ioe);
    assertThatThrownBy(() -> StoreUtils.await(f)).isSameAs(ioe);
  }

  @Test
  public void awaitPropagatesRuntimeException() {
    RuntimeException rte = new IllegalArgumentException("rt-fail");
    CompletableFuture<String> f = new CompletableFuture<>();
    f.completeExceptionally(rte);
    assertThatThrownBy(() -> StoreUtils.await(f)).isSameAs(rte);
  }

  // ---------- prettyIOStatistics ----------

  @Test
  public void prettyIOStatisticsOnNullSourceDoesNotThrow() {
    assertThat(StoreUtils.prettyIOStatistics(null)).isNotNull();
  }

  @Test
  public void prettyIOStatisticsOnPlainObjectDoesNotThrow() {
    assertThat(StoreUtils.prettyIOStatistics(new Object())).isNotNull();
  }

  // ---------- isParentOf ----------

  @Test
  public void isParentOfValidAncestor() {
    Path parent = new Path("s3a://bucket/parent");
    Path child = new Path("s3a://bucket/parent/child/grandchild");
    assertThat(StoreUtils.isParentOf(parent, child))
        .describedAs("parent should be an ancestor of child").isTrue();
  }

  @Test
  public void isParentOfAdjacentPathDoesNotMatch() {
    Path parent = new Path("s3a://bucket/parent");
    Path adjacent = new Path("s3a://bucket/other/child");
    assertThat(StoreUtils.isParentOf(parent, adjacent))
        .describedAs("adjacent path should not match as child of parent").isFalse();
  }

  @Test
  public void isParentOfRootDoesNotMatch() {
    Path root = new Path("s3a://bucket/");
    Path child = new Path("s3a://bucket/parent/child");
    assertThat(StoreUtils.isParentOf(root, child))
        .describedAs("root should not be considered a valid parent").isFalse();
  }

  @Test
  public void isParentOfSelfDoesNotMatch() {
    Path path = new Path("s3a://bucket/parent/child");
    assertThat(StoreUtils.isParentOf(path, path))
        .describedAs("a path should not be considered a parent of itself").isFalse();
  }

  @Test
  public void isParentOfChildIsNotParentOfParent() {
    Path parent = new Path("s3a://bucket/parent");
    Path child = new Path("s3a://bucket/parent/child");
    assertThat(StoreUtils.isParentOf(child, parent))
        .describedAs("child should not be considered a parent of its own parent").isFalse();
  }
}
