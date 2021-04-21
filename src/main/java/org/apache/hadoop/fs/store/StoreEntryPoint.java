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

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.StorageStatistics;
import org.apache.hadoop.fs.shell.CommandFormat;
/*
import org.apache.hadoop.fs.statistics.IOStatistics;
import org.apache.hadoop.fs.statistics.IOStatisticsSource;
*/
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.Tool;

/*
import static org.apache.hadoop.fs.statistics.IOStatisticsLogging.ioStatisticsSourceToString;
import static org.apache.hadoop.fs.statistics.IOStatisticsSupport.retrieveIOStatistics;
*/
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.StoreUtils.split;

/**
 * Entry point for store applications
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class StoreEntryPoint extends Configured implements Tool, Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(StoreEntryPoint.class);

  /**
   * Exit code when a usage message was printed: {@value}.
   */
  public static int EXIT_USAGE = StoreExitCodes.E_USAGE;

  protected CommandFormat commandFormat;

  private PrintStream out = System.out;

  private static String optusage(String opt) {
    return "[-" + opt + "] ";
  }

  protected static String optusage(String opt, String text) {
    return String.format("\t-%s\t%s%n", opt, text);
  }

  protected static String optusage(String opt, String second, String text) {
    return String.format("\t-%s <%s>\t%s%n", opt, second, text);
  }

  @Override
  public int run(String[] args) throws Exception {
    return 0;
  }

  @Override
  public void close() throws IOException {

  }

  public PrintStream getOut() {
    return out;
  }

  public void setOut(PrintStream out) {
    this.out = out;
  }

  /**
   * Print a formatted string followed by a newline to the output stream.
   * @param format format string
   * @param args optional arguments
   */
  public void println(String format, Object... args) {
    if (args.length == 0) {
      out.printf(format);
      out.println();
    } else {
      out.printf(format, args);
      out.println();
    }
    out.flush();
  }

  public void warn(String format, Object... args) {
    println("WARNING: " + String.format(format, args));
  }

  public static void errorln(String format, Object... args) {
    System.err.println(String.format(format, args));
    System.err.println();
    System.err.flush();
  }

  public void heading(String format, Object... args) {
    String text = String.format(format, args);
    int l = text.length();
    StringBuilder sb = new StringBuilder(l);
    for (int i = 0; i < l; i++) {
      sb.append("=");
    }
    println("\n%s\n%s\n", text, sb.toString());
  }

  /**
   * Debug message.
   * @param format format string
   * @param args arguments.
   */
  public void debug(String format, Object... args) {
    LOG.debug(format, args);
/*
    if (LOG.isDebugEnabled()) {
      println(format, args);
    }
*/
  }
  protected static void exit(int status, String text) {
    ExitUtil.terminate(status, text);
  }

  protected static void exit(ExitUtil.ExitException ex) {
    ExitUtil.terminate(ex.getExitCode(), ex.getMessage());
  }

  public CommandFormat getCommandFormat() {
    return commandFormat;
  }

  public void setCommandFormat(CommandFormat commandFormat) {
    this.commandFormat = commandFormat;
  }

  /**
   * Create the command format.
   * Use {@link #addValueOptions(String...)} to declare the value
   * options afterwards.
   * @param min minimum number of non-option arguments.
   * @param max max number of non-option arguments.
   * @param options simple options.
   */
  protected final void createCommandFormat(
      int min,
      int max,
      String... options) {
    setCommandFormat(new CommandFormat(min, max, options));
  }

  /**
   * Add a list of value options.
   * @param names option names.
   */
  protected final void addValueOptions(String...names) {
    for (String s : names) {
      commandFormat.addOptionWithValue(s);
    }
  }

  /**
   * Parse CLI arguments and returns the position arguments.
   * The options are stored in {@link #commandFormat}.
   *
   * @param args command line arguments.
   * @return the position arguments from CLI.
   */
  protected List<String> parseArgs(String[] args) {
    return args.length > 0 ? getCommandFormat().parse(args, 0)
        : new ArrayList<>(0);
  }

  /**
   * Get the value of a key-val option.
   * @param opt option.
   * @return the value or null
   */
  protected String getOption(String opt) {
    return getCommandFormat().getOptValue(opt);
  }

  /**
   * Did the command line have a specific option.
   * @param opt option.
   * @return true iff it was set.
   */
  protected boolean hasOption(String opt) {
    return getCommandFormat().getOpt(opt);
  }

  /**
   * Get the value of a key-val option.
   * @param opt option.
   * @return the value or null
   */
  protected Optional<String> getOptional(String opt) {
    return Optional.ofNullable(getCommandFormat().getOptValue(opt));
  }
  /**
   * Add all the various configuration files.
   */
  protected void addAllDefaultXMLFiles() {
    addDefaultResources("hdfs-default.xml",
      "hdfs-site.xml",
      // this order is what JobConf does via
      // org.apache.hadoop.mapreduce.util.ConfigUtil.loadResources()
      "mapred-default.xml",
      "mapred-site.xml",
      "yarn-default.xml",
      "yarn-site.xml",
      "hive-site.xml",
      "hive-default.xml",
      "hbase-default.xml",
      "hbase-site.xml");
  }

  protected void addDefaultResources(String... resources) {
    for (String resource : resources) {
      Configuration.addDefaultResource(resource);
    }
  }

  /**
   * For subclasses: exit after a throwable was raised.
   * @param ex exception caught
   */
  protected static void exitOnThrowable(Throwable ex) {
    if (ex instanceof CommandFormat.UnknownOptionException) {
      errorln(ex.getMessage());
      exit(EXIT_USAGE, ex.getMessage());
    } else if (ex instanceof ExitUtil.ExitException) {
      LOG.debug("Command failure", ex);
      exit((ExitUtil.ExitException) ex);
    } else {
      ex.printStackTrace(System.err);
      exit(StoreExitCodes.E_ERROR, ex.toString());
    }
  }

  protected void maybeAddXMLFileOption(
      final Configuration conf,
      final String opt)
      throws FileNotFoundException, MalformedURLException {
    String xmlfile = getOption(opt);
    if (xmlfile != null) {
      if (xmlfile.isEmpty()) {
        throw new StoreExitException(
            StoreExitCodes.E_INVALID_ARGUMENT,
            "XML file option " + opt
            + " found but no value was provided");
      }
      File f = new File(xmlfile);
      if (!f.exists()) {
        throw new FileNotFoundException(f.toString());
      }
      println("Adding XML configuration file %s", f);
      conf.addResource(f.toURI().toURL());
    }
  }

  /**
   * Add a token file from the command line.
   * @param opt option
   * @throws IOException failures
   */
  protected void maybeAddTokens(String opt) throws IOException {
    String tokenfile = getOption(opt);
    if (tokenfile != null) {
      heading("Adding tokenfile %s", tokenfile);
      Credentials credentials = Credentials.readTokenStorageFile(
          new File(tokenfile), getConf());
      Collection<Token<? extends TokenIdentifier>> tokens
          = credentials.getAllTokens();
      println("Loaded %d token(s)", tokens.size());
      for (Token<? extends TokenIdentifier> token : tokens) {
        println(token.toString());
      }
      UserGroupInformation.getCurrentUser().addCredentials(credentials);
    }
  }

  protected void maybePatchDefined(final Configuration conf, final String opt) {
    getOptional(opt).ifPresent(d -> {
          Map.Entry<String, String> pair = split(d, "true");
      println("Patching configuration with \"%s\"=\"%s\"",
          pair.getKey(), pair.getValue());
          conf.set(pair.getKey(), pair.getValue());
        });
  }


  protected void printStatus(final int index, final FileStatus status) {
    println("[%04d]\t%s\t%,d\t(%s)\t%s\t%s\t%s%s",
        index,
        status.getPath(),
        status.getLen(),
        FileUtils.byteCountToDisplaySize(status.getLen()),
        status.getLen(),
        status.getOwner(),
        status.getGroup(),
        status.isEncrypted() ? "\t[encrypted]" : "",
        isVerbose() ? ("\t" + status) : "");
  }

  /**
   * Did this command have the verbose option?
   * @return true if -verbose was on the command line
   */
  protected boolean isVerbose() {
    return hasOption(VERBOSE);
  }

  /**
   * Dump the filesystem Storage Statistics iff the
   * verbose flag was set.
   * @param fs filesystem; can be null
   */

  protected void maybeDumpStorageStatistics(final FileSystem fs) {
    if (isVerbose()) {
      dumpFileSystemStatistics(fs);
    }
  }

  /**
   * Dump the filesystem Storage Statistics.
   * @param fs filesystem; can be null
   */
  protected void dumpFileSystemStatistics(FileSystem fs) {
    if (fs == null) {
      return;
    }
    heading("Storage Statistics");
    String report = ""; //ioStatisticsSourceToString(fs);
    if (!report.isEmpty()) {
      println("%s", report);
    } else {
      // fall back
      StorageStatistics st = fs.getStorageStatistics();
      Iterator<StorageStatistics.LongStatistic> it
          = st.getLongStatistics();
      while (it.hasNext()) {
        StorageStatistics.LongStatistic next = it.next();
        println("%s\t%s", next.getName(), next.getValue());
      }
    }
  }

  protected void printIfVerbose(String format, Object o) {
    if (isVerbose()) {
      println(format, o);
    }
  }

  protected void maybeClose(Object o) {
    if (o instanceof Closeable) {
      IOUtils.closeStreams((Closeable) o);
    }
  }

  protected static final class LimitReachedException extends IOException {

    private static final long serialVersionUID = -8594688586071585301L;

    public LimitReachedException() {
      super("Limit reached");
    }
  }
}
