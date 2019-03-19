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

package org.apache.hadoop.fs.store.diag.mr;

import java.io.IOException;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.fs.store.StoreExitException;
import org.apache.hadoop.fs.store.diag.StoreDiag;
import org.apache.hadoop.fs.store.diag.StoreDiagnosticsInfo;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.yarn.conf.YarnConfiguration;

/**
 * This is just the WC job reworked.
 */
public class StoreDiagWC {

  private static final Logger LOG =
      LoggerFactory.getLogger(StoreDiagWC.class);

  public static final String STOREDIAG_SRC = "storediag.src";
  public static final String STOREDIAG_DEST = "storediag.dest";

  public static class TokenizerMapper
      extends Mapper<Object, Text, Text, IntWritable> {

    private final static IntWritable one = new IntWritable(1);

    private Text word = new Text();

    public void map(Object key, Text value, Context context) throws IOException, InterruptedException {

      StoreDiag diags = new StoreDiag();
      Configuration conf = context.getConfiguration();
      diags.setConf(conf);
      diags.setOut(System.out);
      String dest = conf.get(STOREDIAG_DEST, "");
      if (!dest.isEmpty()) {
        StoreDiagnosticsInfo diagnosticsInfo = diags.bindToStore(dest);
      }
      diags.dumpUserTokens();
      
      
      StringTokenizer itr = new StringTokenizer(value.toString());
      while (itr.hasMoreTokens()) {
        word.set(itr.nextToken());
        context.write(word, one);
      }
    }
  }

  public static class IntSumReducer
      extends Reducer<Text, IntWritable, Text, IntWritable> {

    private IntWritable result = new IntWritable();

    public void reduce(Text key, Iterable<IntWritable> values,
        Context context
    ) throws IOException, InterruptedException {
      int sum = 0;
      for (IntWritable val : values) {
        sum += val.get();
      }
      result.set(sum);
      context.write(key, result);
    }
  }

  public int run(Configuration conf, String[] args) throws Exception {
    String[] otherArgs = new GenericOptionsParser(conf,
        args).getRemainingArgs();
    if (otherArgs.length != 2) {
      throw new StoreExitException(StoreEntryPoint.EXIT_USAGE,
          "Usage: StoreDiagWC <in> <out>");
    }
    Path source = new Path(otherArgs[0]);
    Path dest = new Path(otherArgs[otherArgs.length - 1]);
    FileSystem destFS = dest.getFileSystem(conf);
    FileSystem sourceFS = source.getFileSystem(conf);

    // source and dest options
    conf.set(STOREDIAG_SRC, source.makeQualified(sourceFS).toUri().toString());
    conf.set(STOREDIAG_DEST, dest.makeQualified(destFS).toUri().toString());
        
    
    // jobconf clones conf at this point.
    Job job = Job.getInstance(conf, "word count");
    job.setJarByClass(StoreDiagWC.class);
    job.setMapperClass(TokenizerMapper.class);
    job.setCombinerClass(IntSumReducer.class);
    job.setReducerClass(IntSumReducer.class);
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(IntWritable.class);
    
    for (int i = 0; i < otherArgs.length - 1; ++i) {
      FileInputFormat.addInputPath(job, new Path(otherArgs[i]));
    }
    // only delete dest if empty
    destFS.delete(dest, false);
    FileOutputFormat.setOutputPath(job, dest);
    return job.waitForCompletion(true) ? 0 : 1;
  }

  public static void main(String[] args) throws Exception {
    YarnConfiguration conf = new YarnConfiguration();
    int exitcode;
    try {
      exitcode = new StoreDiagWC().run(conf, args);
    } catch (StoreExitException e) {
      LOG.info("{}", e.toString());
      exitcode = e.getExitCode();
    } catch (Exception e) {
      LOG.error("{}", e);
      exitcode = -1;
    }
    System.exit(exitcode);
  }
}
