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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import static org.apache.hadoop.fs.store.StoreUtils.checkArgument;

/**
 * Enumeration mapping configuration keys to command line options.
 * Lifted from {@code distcp}.
 */
public enum OptionSwitch {

  /**
   * Ignores any failures during copy, and continues with rest.
   * Logs failures in a file
   */
  IGNORE_FAILURES(
      new Option("i", "ignore", false, "Ignore failures during copy")),

  /**
   * Specify worker threads.
   */
  THREADS(new Option("t", "threads", true, "Threads to perform upload")),

  /**
   * Specify worker threads.
   */
  LARGEST(new Option("l", "largest", true, "Largest files to upload first")),

  /**
   * Overwrite target-files unconditionally.
   */
  OVERWRITE(new Option("o", "overwrite", false,
          "Overwrite target files even if they exist.")),

  SOURCE(new Option("s", "source", true, "source path")),

  DEST(new Option("d", "dest", true, "destination path"));

  private final Option option;

  OptionSwitch(Option option) {
    this.option = option;
  }

  OptionSwitch(Option option, int argNum) {
    this.option = option;
    this.option.setArgs(argNum);
  }

  /**
   * Get CLI Option corresponding to the distcp option.
   * @return option
   */
  public Option getOption() {
    return option;
  }

  /**
   * Get Switch symbol.
   * @return switch symbol char
   */
  public String getOptionName() {
    return option.getOpt();
  }

  @Override
  public String toString() {
    return  super.name() + " {" +
        "option=" + option + '}';
  }

  /**
   * Add this option.
   * @param cliOptions option set
   */
  public void add(Options cliOptions) {
    cliOptions.addOption(option);
  }


  /**
   * Get the value
   * @param command
   * @return the value
   * @throws IllegalArgumentException if it is unset/empty
   */
  public String required(CommandLine command) {
    String r = eval(command, null);
    checkArgument(r != null  && !r.isEmpty(),
        "Unset option: " + getOptionName());
    return r;
  }

  /**
   * Get the value
   * @param command
   * @param defVal
   * @return
   */
  public String eval(CommandLine command, String defVal) {
    String optionValue = command.getOptionValue(getOptionName());
    return optionValue == null ? defVal : optionValue.trim();
  }

  public boolean hasOption(CommandLine command) {
    return command.hasOption(getOptionName());
  }

  public int eval(CommandLine command, int defVal) {
    return Integer.valueOf(eval(command, Integer.toString(defVal)));
  }

  /**
   * Enum all the options and add them.
   * @param cliOptions option set
   * @return the options
   */
  static Options addAllOptions(Options cliOptions) {
    values();
    for (OptionSwitch opt : values()) {
      opt.add(cliOptions);
    }
    return cliOptions;
  }
}
