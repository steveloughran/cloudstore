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

package org.apache.hadoop.fs.store.diag;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class DiagUtils {

  public static Pattern ipV4pattern() {
    return Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");

  }

  public static boolean isIpV4String(String input) {
    return ipV4pattern().matcher(input).matches();
  }

  /**
   * Determine the suffix for a time unit.
   * @param unit time unit
   * @return string value for conf files
   */
  public static String suffixTimeUnit(TimeUnit unit) {
    return ParsedTimeDuration.unitFor(unit).suffix();
  }

  /**
   * Go from a time unit to a string suffix.
   * This is not the right way to give an eum string, but it's what conf
   * does.
   */
  public enum ParsedTimeDuration {
    NS {
      public TimeUnit unit() { return TimeUnit.NANOSECONDS; }
      public String suffix() { return "ns"; }
    },
    US {
      public TimeUnit unit() { return TimeUnit.MICROSECONDS; }
      public String suffix() { return "us"; }
    },
    MS {
      public TimeUnit unit() { return TimeUnit.MILLISECONDS; }
      public String suffix() { return "ms"; }
    },
    S {
      public TimeUnit unit() { return TimeUnit.SECONDS; }
      public String suffix() { return "s"; }
    },
    M {
      public TimeUnit unit() { return TimeUnit.MINUTES; }
      public String suffix() { return "m"; }
    },
    H {
      public TimeUnit unit() { return TimeUnit.HOURS; }
      public String suffix() { return "h"; }
    },
    D {
      public TimeUnit unit() { return TimeUnit.DAYS; }
      public String suffix() { return "d"; }
    };
    public abstract TimeUnit unit();
    public abstract String suffix();
    public static ParsedTimeDuration unitFor(String s) {
      for (ParsedTimeDuration ptd : values()) {
        // iteration order is in decl order, so SECONDS matched last
        if (s.endsWith(ptd.suffix())) {
          return ptd;
        }
      }
      return null;
    }
    public static ParsedTimeDuration unitFor(TimeUnit unit) {
      for (ParsedTimeDuration ptd : values()) {
        if (ptd.unit() == unit) {
          return ptd;
        }
      }
      return null;
    }
  }
}
