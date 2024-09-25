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
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.StorageStatistics;
import org.apache.hadoop.fs.shell.CommandFormat;
import org.apache.hadoop.fs.store.diag.Printout;
import org.apache.hadoop.fs.store.diag.StoreLogExactlyOnce;
import org.apache.hadoop.fs.store.logging.LogControl;
import org.apache.hadoop.fs.store.logging.LogControllerFactory;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.Tool;

import static java.util.logging.Level.ALL;
import static org.apache.hadoop.fs.store.CommonParameters.DEBUG;
import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.VERBOSE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreDiagConstants.IOSTATISTICS_LOGGING_LEVEL;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;
import static org.apache.hadoop.fs.store.StoreUtils.split;
import static org.apache.hadoop.fs.store.diag.OptionSets.CLOUD_CONNECTOR_LOGS;
import static org.apache.hadoop.fs.store.diag.S3ADiagnosticsInfo.DIRECTORY_MARKER_RETENTION;
import static org.apache.hadoop.fs.store.diag.S3ADiagnosticsInfo.FS_S3A_CONNECTION_MAXIMUM;
import static org.apache.hadoop.fs.store.diag.S3ADiagnosticsInfo.FS_S3A_THREADS_MAX;
import static org.apache.hadoop.fs.store.diag.S3ADiagnosticsInfo.INPUT_FADVISE;
import static org.apache.hadoop.fs.store.diag.S3ADiagnosticsInfo.INPUT_FADV_NORMAL;

/**
 * Entry point for store applications
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "SpellCheckingInspection"})
public class StoreEntryPoint extends Configured implements Tool, Closeable, Printout {

  protected static final int MB_1 = (1024 * 1024);

  private static final Logger LOG = LoggerFactory.getLogger(StoreEntryPoint.class);

  /**
   * Exit code when a usage message was printed: {@value}.
   */
  public static final int EXIT_USAGE = StoreExitCodes.E_USAGE;

  public static final boolean DEFAULT_HIDE_ALL_SENSITIVE_CHARS = false;

  private final StoreLogExactlyOnce LogJceksFailureOnce = new StoreLogExactlyOnce(LOG);

  /**
   * Hide all sensitive data.
   */
  protected boolean hideAllSensitiveChars = DEFAULT_HIDE_ALL_SENSITIVE_CHARS;

  protected CommandFormat commandFormat;

  private PrintStream out = System.out;

  public static String optusage(String opt) {
    return "[-" + opt + "] ";
  }

  public static String optusage(String opt, String text) {
    return String.format("\t-%s\t%s%n", opt, text);
  }

  public static String optusage(String opt, String second, String text) {
    return String.format("\t-%s <%s>\t%s%n", opt, second, text);
  }

  /**
   * Dump token info from the credentials, with resilience to failure
   * @param cred credentials
   * @return the tokens
   */
  protected Collection<Token<? extends TokenIdentifier>> dumpTokens(final Credentials cred) {
    final Collection<Token<? extends TokenIdentifier>> tokens
        = cred.getAllTokens();
    for (Token<?> token : tokens) {
      try {
        println("Fetched token: %s", token);
      } catch (Exception e) {
        warn("Failed to unmarshall token %s", e);
        LOG.warn("exception", e);
      }
    }
    return tokens;
  }

  protected final String plural(int n) {
    return n == 1 ? "" : "s";
  }

  @Override
  public int run(String[] args) throws Exception {
    return 0;
  }

  @Override
  public void close() throws IOException {

  }

  public final PrintStream getOut() {
    return out;
  }

  public final void setOut(PrintStream out) {
    this.out = out;
  }

  @Override
  public final void println() {
    out.println();
    flush();
  }

  /**
   * Print a formatted string followed by a newline to the output stream.
   * @param format format string
   */
  @Override
  public void println(String format) {
    print(format);
    out.println();
    flush();
  }

  /**
   * Print a formatted string followed by a newline to the output stream.
   * @param format format string
   * @param args optional arguments
   */
  public void println(String format, Object... args) {
    print(format, args);
    out.println();
    flush();
  }

  /**
   * Flush the stream.
   */
  @Override
  public void flush() {
    out.flush();
  }

  /**
   * Print a formatted string without any newline
   * @param format format string
   * @param args optional arguments
   */
  @Override
  public final void print(String format, Object... args) {
    if (args.length == 0) {
      out.print(format);
    } else {
      out.printf(format, args);
    }
  }


  public final void warn(String format, Object... args) {
    println("WARNING: " + String.format(format, args));
  }

  public final void error(String format, Object... args) {
    errorln("ERROR: " + format, args);
  }

  public static void errorln(String format, Object... args) {
    System.err.printf(format + "%n", args);
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
  public final void debug(String format, Object... args) {
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

  public final CommandFormat getCommandFormat() {
    return commandFormat;
  }

  public final void setCommandFormat(CommandFormat commandFormat) {
    this.commandFormat = commandFormat;
  }

  /**
   * Create the command format.
   * Use {@link #addValueOptions(String...)} to declare the value
   * options afterwards.
   * automatically add the standard opts
   * @param min minimum number of non-option arguments.
   * @param max max number of non-option arguments.
   * @param options simple options.
   */
  protected final void createCommandFormat(
      int min,
      int max,
      String... options) {
    List<String> ol = new ArrayList<>(Arrays.asList(options));
    ol.add(DEBUG);
    ol.add(VERBOSE);
    setCommandFormat(new CommandFormat(min, max, ol.toArray(new String[0])));
    addStandardValueOptions();
  }

  /**
   * Add the standard value options; subclasses can remove any.
   */
  protected void addStandardValueOptions() {

    addValueOptions(
        DEFINE,
        TOKENFILE,
        XMLFILE);
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
   * This is also where java debug logging is enabled if the
   * {@code -debug} option is set.
   * @param args command line arguments.
   * @param min min number of args if positive
   * @param max max number of args if positive
   * @param error error text
   * @return the position arguments from CLI.
   * @throws IOException failure to load token from -tokenfile
   * @throws ExitUtil.ExitException if the number of arguments is wrong.
   */
  protected List<String> processArgs(String[] args, int min, final int max, String error) throws IOException {
    final List<String> parsed = parseArgs(args);

    if ((min >= 0 && parsed.size() < min)
        || (max >= 0 && parsed.size() > max)) {
      errorln(error);
      parsed.forEach(s -> errorln("  %s", s));
      throw new ExitUtil.ExitException(E_USAGE,
          "invalid argment count: expected between " + min + " and "
              + max + " but got " + parsed.size());
    }
    maybeEnableDebugLogging();
    maybeAddTokens(TOKENFILE);
    return parsed;
  }

  /**
   * Parse CLI arguments and returns the position arguments.
   * The options are stored in {@link #commandFormat}.
   *
   * This is also where java debug logging is enabled if the
   * {@code -debug} option is set.
   *
   * @param args command line arguments.
   * @return the position arguments from CLI.
   */
  protected List<String> parseArgs(String[] args) {
    return args.length > 0 ? getCommandFormat().parse(args, 0)
        : new ArrayList<>(0);
  }

  /**
   * Maybe enable JVM and cloud connector debug logging.
   */
  protected void maybeEnableDebugLogging() {
    if (hasOption(DEBUG)) {
      println("Enabling debug logging");
      enableJvmLogging();
      enableCloudConnectorLogging(LogControl.LogLevel.DEBUG);
    }
  }


  /**
   * Enable JVM logging.
   */
  protected void enableJvmLogging() {
    println("Enabling JVM logging");
    ConsoleHandler handler = new ConsoleHandler();
    handler.setLevel(ALL);
    java.util.logging.Logger log = LogManager.getLogManager().getLogger("");
    log.addHandler(handler);
    log.setLevel(ALL);
  }

  /**
   * Enable cloud connector logging.
   * @param level desired level
   */
  protected void enableCloudConnectorLogging(LogControl.LogLevel level) {
    final Optional<LogControl> control =
        LogControllerFactory.createController(LogControllerFactory.LOG4J);
    control.ifPresent(c ->
        Arrays.stream(CLOUD_CONNECTOR_LOGS).forEach(
            log -> c.setLogLevel(log, level)));
  }

  /**
   * Get the value of a key-val option.
   * @param opt option.
   * @return the value or null
   */
  protected final String getOption(String opt) {
    return getCommandFormat().getOptValue(opt);
  }

  /**
   * Get the value of a key-val option.
   * @param opt option.
   * @param defval default value
   * @return the value or null
   */
  protected final String getOption(String opt, String defval) {
    return hasOption(opt) ? getCommandFormat().getOptValue(opt) : defval;
  }

  /**
   * Did the command line have a specific option.
   * @param opt option.
   * @return true iff it was set.
   */
  protected final boolean hasOption(String opt) {
    return getCommandFormat().getOpt(opt);
  }

  /**
   * Get the value of a key-val option.
   * @param opt option.
   * @return the value or null
   */
  protected final Optional<String> getOptional(String opt) {
    return Optional.ofNullable(getCommandFormat().getOptValue(opt));
  }

  /**
   * get an integer option.
   * @param opt option
   * @param defval default value
   * @return the value to use
   */
  protected final int getIntOption(String opt, int defval) {
    return getOptional(opt).map(Integer::valueOf).orElse(defval);
  }

  /**
   * Add all the various configuration files.
   */
  protected final void addAllDefaultXMLFiles() {
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

  protected final void addDefaultResources(String... resources) {
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
        throw new ExitUtil.ExitException(
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
    // TODO: use reflection to find this
    String report = ""; //ioStatisticsSourceToString(fs);
    if (!report.isEmpty()) {
      heading("IO Statistics");

      println("%s", report);
    } else {
      // fall back
      StorageStatistics st = fs.getStorageStatistics();

      Iterator<StorageStatistics.LongStatistic> it
          = st.getLongStatistics();
      if (it.hasNext()) {
        // there is data
        heading("Storage Statistics");
        while (it.hasNext()) {
          StorageStatistics.LongStatistic next = it.next();
          println("%s\t%s", next.getName(), next.getValue());
        }
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

  /**
   * Set up the config with CLI config options.
   * XML file, -D and abfs/s3a
   * to log their IOStats at debug.
   * @return a new config.
   * @throws FileNotFoundException XML file was requested but not found.
   * @throws MalformedURLException problems setting up default XML files.
   */
  protected Configuration createPreconfiguredConfig()
      throws FileNotFoundException, MalformedURLException {
    addAllDefaultXMLFiles();

    final Configuration conf = new Configuration(getConf());

    maybeAddXMLFileOption(conf, XMLFILE);
    maybePatchDefined(conf, DEFINE);
    conf.set(IOSTATISTICS_LOGGING_LEVEL, "info");

    return conf;
  }

  /**
   * Patch the configuration for maximum S3A performance.
   * @param conf config
   * @return the now updated config
   */
  protected Configuration patchForMaxS3APerformance(Configuration conf) {
    conf.set(DIRECTORY_MARKER_RETENTION, "keep");
    final int workers = 256;
    conf.setInt(FS_S3A_CONNECTION_MAXIMUM, workers * 2);
    conf.setInt(FS_S3A_THREADS_MAX, workers);
    conf.set(INPUT_FADVISE, INPUT_FADV_NORMAL);
    return conf;
  }

  protected void printFSInfoInVerbose(FileSystem fs) {
    if (isVerbose()) {
      println();

      println("FileSystem %s", fs.getUri());
      println();
      println("%s", fs);
      println();
    }
  }

  /**
   * @param operation
   * @param tracker
   * @param sizeBytes
   * @param blockName
   * @param blockSummary
   */
  protected void summarize(String operation,
      StoreDurationInfo tracker,
      long sizeBytes,
      final String blockName,
      final MinMeanMax blockSummary) {
    heading("%s Summary", operation);
    println("Data size %,d bytes", sizeBytes);
    println("%s duration %s", operation, tracker.getDurationString());
    println();
    final long durationMillis = tracker.value();
    // now calculated it in MBits/GBits
    double seconds = durationMillis / 1000.0;
    if (seconds < 1) {
      seconds = 1;
    }
    double bitsPerSecond = sizeBytes * 8.0 / seconds;
    double megabitsPerSecond = bitsPerSecond / MB_1;
    double megabytesPerSecond = megabitsPerSecond / 8;

    println("%s bandwidth in Megabits/second %,.3f Mbit/s", operation, megabitsPerSecond);
    println("%s bandwidth in Megabytes/second %,.3f MB/s", operation, megabytesPerSecond);
    if (blockSummary != null) {
      println("%s %d: min %.3f seconds, max %.3f seconds, mean %.3f seconds,",
          blockName,
          blockSummary.samples(),
          blockSummary.min() / 1000.0,
          blockSummary.max()  / 1000.0,
          blockSummary.mean() / 1000.0);
    }
    println();
  }

  protected Optional<Long> getOptionalLong(final String s) {
    return getOptional(s).map(Long::valueOf);
  }

  protected long getLongOption(final String s, long def) {
    return getOptional(s).map(Long::valueOf).orElse(def);
  }

  /**
   * Print the selected options in a config.
   * This is an array of (name, secret, obfuscate) entries.
   * @param title heading to print
   * @param conf source configuration
   * @param options map of options
   */
  @Override
  public final void printOptions(String title, Configuration conf,
      Object[][] options)
      throws IOException {
    int index = 0;
    if (options.length > 0) {
      heading(title);
      for (final Object[] option : options) {
        printOption(conf,
            ++index,
            (String) option[0],
            (Boolean) option[1],
            (Boolean) option[2]);
      }
    }
  }

  /**
   * Sanitize a value if needed.
   * @param value option value.
   * @param obfuscate should it be obfuscated?
   * @return string safe to log; in quotes
   */
  @Override
  public String maybeSanitize(String value, boolean obfuscate) {
    return obfuscate ? StoreUtils.sanitize(value, hideAllSensitiveChars) :
        ("\"" + value + "\"");
  }

  @Override
  public void printOption(Configuration conf,
      final int index,
      final String key,
      final boolean secret,
      boolean obfuscate)
      throws IOException {
    if (key.isEmpty()) {
      return;
    }
    String source = "";
    String option;
    if (secret) {
      try {
        final char[] password = conf.getPassword(key);
        if (password != null) {
          option = new String(password).trim();
          source = "<credentials>";
        } else {
          option = null;
        }
      } catch (IOException e) {
        // can be triggered by jceks
        LogJceksFailureOnce.warn("Failed to read key {}", key, e);
        option = "failed: " + e;
        obfuscate = false;
      }
    } else {
      option = conf.get(key);
    }
    String full;
    if (option == null) {
      full = "(unset)";
    } else {
      option = maybeSanitize(option, obfuscate);
      source = getOrigins(conf, key, source);
      full = option + " " + source;
    }
    // pretty inefficient to do this every option, but
    // it's a bit late to fix.
    final Set<String> finalParameters = conf.getFinalParameters();
    if (finalParameters.contains(key)) {
      full = full + "[final]";
    }
    println("[%03d]  %s = %s", index, key, full);
  }

  /**
   * Get the origin of a config as a string.
   * @param conf configuration
   * @param key key
   * @param sourceDefault default string.
   * @return a source string
   */
  public static String getOrigins(final Configuration conf, final String key, String sourceDefault) {
    String source = sourceDefault;
    String[] origins = conf.getPropertySources(key);
    if (origins != null && origins.length != 0) {
      source = "[" + StringUtils.join(",", origins) + "]";
    }
    return source;
  }

  protected static final class LimitReachedException extends IOException {

    private static final long serialVersionUID = -8594688586071585301L;

    public LimitReachedException() {
      super("Limit reached");
    }
  }
}
