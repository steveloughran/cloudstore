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

package org.apache.hadoop.fs.store.commands;/*
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

import org.assertj.core.api.Assertions;
import org.junit.Test;

import static org.apache.hadoop.fs.store.StoreExitCodes.E_NOT_FOUND;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_NOT_FOUND2;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.fs.store.commands.Constval.lookupFieldValue;
import static org.apache.hadoop.fs.store.commands.FieldsForTesting.FIELDS;
import static org.apache.hadoop.tools.store.StoreTestUtils.expectExitException;

public class TestConstval {


  public static final String CONSTVAL = "org.apache.hadoop.fs.store.commands.Constval";

  private static void expectValue(String classname, String field, String expected) {
    String value = lookupFieldValue(classname, field);
    Assertions.assertThat(value)
        .describedAs("Field " + field + " in " + classname)
        .isEqualTo(expected);
  }
  @Test
  public void testRun() {
  }

  @Test
  public void testLookupString() {
    expectValue(CONSTVAL, "USAGE", Constval.USAGE);
  }
  @Test
  public void testLookupInt() {
    expectValue(FIELDS, "INT", FieldsForTesting.INT + "");
  }

  @Test
  public void testLookupBool() {
    expectValue(FIELDS, "BOOL", FieldsForTesting.BOOL + "");
  }



  @Test
  public void testLookupNull() {
    expectValue(FIELDS, "NULLSTR", Constval.NULL);
  }

  @Test
  public void testMissingClass() throws Exception {
    expectExitException(E_NOT_FOUND,
        () -> lookupFieldValue(CONSTVAL + "2", "not_found"));
  }

  @Test
  public void testMissingField() throws Exception {
    expectExitException(E_NOT_FOUND2,
        () -> lookupFieldValue(CONSTVAL, "not_found"));
  }

  @Test
  public void testExecNoArgs() throws Exception {
    expectExitException(E_USAGE,
        () -> Constval.exec());
  }
  @Test
  public void testExecBool() throws Exception {
    Constval.exec(FIELDS, "BOOL");
  }

}