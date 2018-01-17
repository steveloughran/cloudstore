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

package org.apache.hadoop.fs.s3a;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;

import org.apache.hadoop.fs.s3native.S3xLoginHelper;
import org.apache.hadoop.fs.shell.CommandFormat;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.fs.store.StoreExitCodes;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.s3a.Constants.*;

public class AssumeRole extends StoreEntryPoint {

  private static final String PROPERTY_FORMAT
      = "<property><name>%s</name><value>%s</value></property>";

  CommandFormat commandFormat = new CommandFormat(2, 3);

  protected CommandFormat getCommandFormat() {
    return commandFormat;
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
          : new ArrayList<String>(0);
  }

  public String usage() {
    return "Usage: org.apache.hadoop.fs.s3a.AssumeRole <roleArn> [bucket] [filename]";
  }

  protected String param(List<String> params, int pos) {
    if (params.size() <= pos) {
      throw new IllegalArgumentException("Argument count of " + params.size()
          + " is less than expected");
    } else {
      return params.get(pos);
    }
  }

  protected String paramOpt(List<String> params, int pos, String defVal) {
    if (params.size() <= pos) {
      return defVal; 
    } else {
      return param(params, pos);
    }
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> params = parseArgs(args);
    String role = param(params, 0);
    String fs = paramOpt(params, 1, "");
    String destfile = paramOpt(params, 2, "");

    Map<String, String> map = assumeRole(role, fs);
    TreeSet<String> s = new TreeSet<>(map.keySet());

    if (!destfile.isEmpty()) {
      File f = new File(destfile).getAbsoluteFile();
      println("Saving credentials to property file %s", f.getAbsolutePath());
      try(PrintStream o = new PrintStream(new FileOutputStream(destfile))) {
        for (String k : s) {
          o.println(String.format(PROPERTY_FORMAT, k, map.get(k)));
        }
      }
    } else {
      for (String k : s) {
        println(PROPERTY_FORMAT, k, map.get(k));
      }
    }
    return 0;
  }

  public String key(String prop, String bucket) {
    if (bucket.isEmpty()) {
      return prop;
    }
    return String.format("fs.s3a.bucket.%s.%s", bucket,
        prop.substring("fs.s3a.".length()));
  }

  public void set(Map<String, String> result, String bucket, String prop,
      String val) {
    result.put(key(prop, bucket), val);
  }

  public Map<String, String> assumeRole(String roleArn, String bucket) throws IOException {
    S3xLoginHelper.Login login = S3AUtils.getAWSAccessKeys(
        URI.create("s3a://foobar"), getConf());
    BasicAWSCredentialsProvider parentCredentials
        = new BasicAWSCredentialsProvider(login.getUser(), login.getPassword());
    AWSSecurityTokenServiceClient stsClient
        = new AWSSecurityTokenServiceClient(parentCredentials);
    int duration = 900;
    AssumeRoleRequest request = new AssumeRoleRequest();
    request.setDurationSeconds(duration);
    request.setRoleArn(roleArn);
    request.setRoleSessionName("session");
    AssumeRoleResult role = stsClient.assumeRole(request);
    Map<String, String> result = new HashMap<>(4);
    Credentials roleCredentials = role.getCredentials();
    set(result, bucket, ACCESS_KEY, roleCredentials.getAccessKeyId());
    set(result, bucket, SECRET_KEY, roleCredentials.getSecretAccessKey());
    set(result, bucket, SESSION_TOKEN, roleCredentials.getSessionToken());
    set(result, bucket, AWS_CREDENTIALS_PROVIDER,
        TemporaryAWSCredentialsProvider.NAME);
    return result;

  }

  /**
   * Main entry point. Calls {@code System.exit()} on all execution paths.
   * @param args argument list
   */
  public static void main(String[] args) {
    try {

      exit(ToolRunner.run( new AssumeRole(), args), "");
    } catch (IllegalArgumentException e) {
      errorln(e.getMessage());
      exit(StoreExitCodes.E_USAGE, e.getMessage());
    } catch (Throwable e) {
      e.printStackTrace(System.err);
      exit(StoreExitCodes.E_ERROR, e.toString());
    }
  }
}
