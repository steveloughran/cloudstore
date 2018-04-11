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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CreateFlag;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileContext;
import org.apache.hadoop.fs.Options;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.shell.CommandFormat;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import static org.apache.hadoop.fs.store.StoreExitCodes.E_ERROR;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_SUCCESS;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;


/**
 MR job for diagnostics. WiP
 */
public class StoreDiagJob extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(StoreDiagJob.class);

  protected static final String UTF_8 = "UTF-8";

  private final JobConf jobConf;


  public StoreDiagJob(final JobConf jobConf) {
    this.jobConf = jobConf;
  }

  /**
   * Option: run the file IO tests?
   */
  public static final String DIAG_IO_TESTS = "storediag.io.tests";

  public static final String DELIMITER = "textinputformat.record.delimiter";

  /**
   * Runs Store Diagnostics as a mapper.
   */
  public static class DiagMapper extends Mapper<Text, Text, Text, Text> {

    @Override
    protected void map(final Text key, final Text value, final Context context)
        throws IOException, InterruptedException {
      Configuration conf = context.getConfiguration();

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(baos);
      try {
        String uri = value.toString();
        Path p = new Path(uri);
        URI fsUri = StoreDiag.toURI("Key " + key, uri);
        StoreDiag diags = new StoreDiag();
        diags.setConf(conf);
        diags.setOut(out);
        diags.bindToStore(fsUri);
        diags.printStoreConfiguration();
        diags.probeRequiredAndOptionalClasses();
        diags.probeAllEndpoints();
        if (conf.getBoolean(DIAG_IO_TESTS, true)) {
          diags.executeFileSystemOperations(p, true);
        }

      } catch (IOException | ClassNotFoundException e) {
        // failure
        LOG.error("In diagnostics", e);
        e.printStackTrace(out);
      }
      // now save it
      out.flush();

      String results = baos.toString(UTF_8);
      LOG.info("{}", results);
      value.set(results.getBytes(UTF_8));
      super.map(key, value, context);
    }
  }


  public static boolean execute(Configuration conf, List<String> targets)
      throws IOException, ClassNotFoundException, InterruptedException {
    JobConf jobConf = new JobConf(conf);
    StoreDiagJob diagJob = new StoreDiagJob(jobConf);

    Job job = Job.getInstance(jobConf, "Store Diag");
    job.setJarByClass(StoreDiagJob.class);
    job.setMapperClass(DiagMapper.class);
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(Text.class);

    String filename = UUID.randomUUID().toString();

    FileContext clusterFC = FileContext.getFileContext(jobConf);
    Path home = clusterFC.getHomeDirectory();
    Path jobdir = new Path(home, filename);
    Path srcFile = new Path(jobdir, "input.txt");
    Path destDir = new Path(jobdir, "output");

    // one entry per line
    try(FSDataOutputStream stream = clusterFC.create(srcFile,
        EnumSet.of(CreateFlag.CREATE, CreateFlag.OVERWRITE),
        Options.CreateOpts.createParent());) {
      for (String target : targets) {
        stream.writeChars(target);
        stream.writeChar('\n');
      }
    }
    jobConf.set(DELIMITER, "\n");
    job.setInputFormatClass(TextInputFormat.class);
    job.setOutputFormatClass(TextOutputFormat.class);
    FileInputFormat.addInputPath(job, srcFile);
    FileOutputFormat.setOutputPath(job, destDir);
    return job.waitForCompletion(true);
  }


  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {

    return execute(new Configuration(), Arrays.asList(args))?
        E_SUCCESS : E_ERROR;
  }

  /**
   * Main entry point. Calls {@code System.exit()} on all execution paths.
   * @param args argument list
   */
  public static void main(String[] args) {
    try {
      exit(exec(args), "");
    } catch (CommandFormat.UnknownOptionException e) {
      errorln(e.getMessage());
      exit(E_USAGE, e.getMessage());
    } catch (Throwable e) {
      e.printStackTrace(System.err);
      exit(E_ERROR, e.toString());
    }
  }

}
