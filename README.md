# Cloudstore

This is a general cloudstore CLI command for Hadoop which can evolve fast
as it can be released daily, if need be.

*License*: Apache ASF 2.0

All the implementation classes are under the `org.apache.hadoop.fs` package
tree with a goal of ultimately moving this into Hadoop itself; it's been
kept out right now for various reasons

1. Faster release cycle, so the diagnostics can evolve to track features going
   into Hadoop-trunk.
2. Fewer test requirements. This is naughty, but...
3. Ability to compile against older versions. We've currently switched to Hadoop 3.x+
   due to the need to make API calls and operations not in older versions.
   That said, we try to make the core storediag command work with older versions,
   even while some of the other commands fail. 
 
*Author*: Steve Loughran, Hadoop Committer.
 
## Features

### Primarily: diagnostics

Why? 

1. Sometimes things fail, and the first problem is classpath;
1. The second, invariably some client-side config. 
1. Then there's networking and permissions...
1. The Hadoop FS connectors all assume a well configured system, and don't
do much in terms of meaningful diagnostics.
1. This is compounded by the fact that we dare not log secret credentials.
1. And in support calls, it's all to easy to get those secrets, even
though its a major security breach to get them.

### Secondary: higher performance cloud IO

The main hadoop `hadoop fs` commands are written assuming a filesystem, where

* Recursive treewalks are the way to traverse the store.
* The code was written for Hadoop 1.0 and uses the filesystem APIs of that era.
* The commands are often used in shell scripts and workflows, including parsing
the output: we do not dare change the behaviour or output for this reason.
* And the shell removes stack traces on failures, making it of "limited value"
when things don't work. And object stores are fairly fussy to get working, 
primarily due to authentication. (Note: HDFS needs Keberos Auth, which has its
own issues &mdash; which is why I wrote KDiag).

## Command `storediag`

The `storediag` entry point is designed to pick up the FS settings, dump them
with sanitized secrets, and display their provenance. It then
bootstraps connectivity with an attempt to initiate (unauthed) HTTP connections
to the store's endpoints. This should be sufficient to detect proxy and
endpoint configuration problems.

Then it tries to perform some reads and writes against the store. If these
fail, then there's clearly a problem. Hopefully though, there's now enough information
to begin determining what it is.

Finally, if things do fail, the printed configuration excludes the login secrets,
for safer reporting of issues in bug reports.

```bash
hadoop jar cloudstore-0.1-SNAPSHOT.jar storediag -r -j -5 s3a://landsat-pds/
hadoop jar cloudstore-0.1-SNAPSHOT.jar storediag --tokenfile mytokens.bin s3a://my-readwrite-bucket/
hadoop jar cloudstore-0.1-SNAPSHOT.jar storediag wasb://container@user/subdir
hadoop jar cloudstore-0.1-SNAPSHOT.jar storediag abfs://container@user/
```
 
The remote store is required to grant full R/W access to the caller, otherwise
the creation tests will fail.

The `--tokenfile` option loads tokens saved with `hdfs fetchdt`. It does
not need Kerberos, though most filesystems expect Kerberos enabled for
them to pick up tokens (not S3A, potentially other stores).

### Options

```
-r    Readonly filesystem: do not attempt writes
-t    Require delegation tokens to be issued
-j    List the JARs
-5    Print MD5 checksums of the jars listed (requires -j)
-tokenfile <file>   Hadoop token file to load
-xmlfile <file>     Hadoop XML file to load
-require <file>     Text file of classes and resources to require
```

The `-require` option takes a text file where every line is one of
a #-prefixed comment, a blank line, a classname, a resource (with "/" in).
These are all loaded

```bash
hadoop jar cloudstore-0.1-SNAPSHOT.jar storediag -j -5  -required required.txt s3a://something/
```

and with a `required.txt` listing things you require

```
# S3A
org.apache.hadoop.fs.s3a.auth.delegation.S3ADelegationTokens
# Misc
org.apache.commons.configuration.Configuration
org.apache.commons.lang3.StringUtils
``` 

This is useful to dynamically add some extra mandatory classes to 
the list of classes you need to work with a store...most useful when either
you are developing new features and want to verify they are on the classpath,
or you are working with an unknown object store and just want to check its depencies
up front.

Missing file or resource will result in an error and the command failing.

The comments are printed too! This means you can use them in the reports.


##  Command`fetchdt`

This is an extension of `hdfs fetchdt` which collects delegation tokens
from a list of filesystems, saving them to a file.

```bash
hadoop jar cloudstore-0.1-SNAPSHOT fetchdt hdfs://tokens.bin s3a://landsat-pds/ s3a://bucket2
```

### Options

```
Usage: fetchdt <file> [-renewer <renewer>] [-r] [-p] <url1> ... <url999> 
 -r: require each filesystem to issue a token
 -p: protobuf format
```

### Examples

Successful query of an S3A session delegation token.

```bash
> bin/hadoop jar cloudstore-0.1-SNAPSHOT.jar fetchdt -p -r file:/tmp/secrets.bin s3a://landsat-pds/
  Collecting tokens for 1 filesystem to to file:/tmp/secrets.bin
  2018-12-05 17:50:44,276 INFO fs.FetchTokens: Starting: Fetching token for s3a://landsat-pds/
  2018-12-05 17:50:44,399 INFO impl.MetricsConfig: Loaded properties from hadoop-metrics2.properties
  2018-12-05 17:50:44,458 INFO impl.MetricsSystemImpl: Scheduled Metric snapshot period at 10 second(s).
  2018-12-05 17:50:44,459 INFO impl.MetricsSystemImpl: s3a-file-system metrics system started
  2018-12-05 17:50:44,474 INFO delegation.S3ADelegationTokens: Filesystem s3a://landsat-pds is using delegation tokens of kind S3ADelegationToken/Session
  2018-12-05 17:50:44,547 INFO delegation.S3ADelegationTokens: No delegation tokens present: using direct authentication
  2018-12-05 17:50:44,547 INFO delegation.S3ADelegationTokens: S3A Delegation support token (none) with Session token binding for user hrt_qa, with STS endpoint "", region "" and token duration 30:00
  2018-12-05 17:50:46,604 INFO delegation.S3ADelegationTokens: Starting: Creating New Delegation Token
  2018-12-05 17:50:46,620 INFO delegation.SessionTokenBinding: Creating STS client for Session token binding for user hrt_qa, with STS endpoint "", region "" and token duration 30:00
  2018-12-05 17:50:46,646 INFO auth.STSClientFactory: Requesting Amazon STS Session credentials
  2018-12-05 17:50:47,099 INFO delegation.S3ADelegationTokens: Created S3A Delegation Token: Kind: S3ADelegationToken/Session, Service: s3a://landsat-pds, Ident: (S3ATokenIdentifier{S3ADelegationToken/Session; uri=s3a://landsat-pds; timestamp=1544032247065; encryption=(no encryption); 80fc87d2-0da2-4438-9ba6-7ae82751aba5; Created on HW13176.local/192.168.99.1 at time 2018-12-05T17:50:46.608Z.}; session credentials expiring on Wed Dec 05 18:20:46 GMT 2018; (valid))
  2018-12-05 17:50:47,099 INFO delegation.S3ADelegationTokens: Creating New Delegation Token: duration 0:00.495s
  Fetched token: Kind: S3ADelegationToken/Session, Service: s3a://landsat-pds, Ident: (S3ATokenIdentifier{S3ADelegationToken/Session; uri=s3a://landsat-pds; timestamp=1544032247065; encryption=(no encryption); 80fc87d2-0da2-4438-9ba6-7ae82751aba5; Created on HW13176.local/192.168.99.1 at time 2018-12-05T17:50:46.608Z.}; session credentials expiring on Wed Dec 05 18:20:46 GMT 2018; (valid))
  2018-12-05 17:50:47,100 INFO fs.FetchTokens: Fetching token for s3a://landsat-pds/: duration 0:02:825
  Saved 1 token to file:/tmp/secrets.bin
  2018-12-05 17:50:47,166 INFO impl.MetricsSystemImpl: Stopping s3a-file-system metrics system...
  2018-12-05 17:50:47,166 INFO impl.MetricsSystemImpl: s3a-file-system metrics system stopped.
  2018-12-05 17:50:47,166 INFO impl.MetricsSystemImpl: s3a-file-system metrics system shutdown complete.
  ~/P/h/h/t/hadoop-3.1.2-SNAPSHOT (cloud/BUG-99335-HADOOP-15364-S3Select-HDP-3.0.100 ⚡↩) 
```


Failure to get anything from fs, with `-r` option to require them

```bash
> hadoop jar cloudstore-0.1-SNAPSHOT.jar fetchdt -p -r file:/tmm/secrets.bin file:///

Collecting tokens for 1 filesystem to to file:/tmm/secrets.bin
2018-12-05 17:47:00,970 INFO fs.FetchTokens: Starting: Fetching token for file:/
No token for file:/
2018-12-05 17:47:00,972 INFO fs.FetchTokens: Fetching token for file:/: duration 0:00:003
2018-12-05 17:47:00,973 INFO util.ExitUtil: Exiting with status 44: No token issued by filesystem file:///
```

Same command, without the -r. 

```bash
> hadoop jar cloudstore-0.1-SNAPSHOT.jar fetchdt -p file:/tmm/secrets.bin file:///
Collecting tokens for 1 filesystem to to file:/tmp/secrets.bin
2018-12-05 17:54:26,776 INFO fs.FetchTokens: Starting: Fetching token for file:/tmp
No token for file:/tmp
2018-12-05 17:54:26,778 INFO fs.FetchTokens: Fetching token for file:/tmp: duration 0:00:002
No tokens collected, file file:/tmp/secrets.bin unchanged
```

The token file is not modified.


## Command: `list`

Do a recursive listing of a path. Uses `listFiles(path, recursive)`, so for any object store
which can do this as a deep paginated scan, is much, much faster.

```
Usage: list
  -D <key=value>	Define a property
  -tokenfile <file>	Hadoop token file to load
  -xmlfile <file>	XML config file to load
  -limit <limit>	limit of files to list
```

Example: list some of the AWS public landsat store.

```bash
> bin/hadoop jar cloudstore-0.1-SNAPSHOT.jar list -limit 10 s3a://landsat-pds/

Listing up to 10 files under s3a://landsat-pds/
2019-04-05 21:32:14,523 [main] INFO  tools.ListFiles (DurationInfo.java:<init>(53)) - Starting: Directory list
2019-04-05 21:32:14,524 [main] INFO  tools.ListFiles (DurationInfo.java:<init>(53)) - Starting: First listing
2019-04-05 21:32:15,754 [main] INFO  tools.ListFiles (DurationInfo.java:close(100)) - First listing: duration 0:01:230
[1]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B1.TIF	63,786,465	stevel	stevel	[encrypted]
[2]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B1.TIF.ovr	8,475,353	stevel	stevel	[encrypted]
[3]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B10.TIF	35,027,713	stevel	stevel	[encrypted]
[4]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B10.TIF.ovr	6,029,012	stevel	stevel	[encrypted]
[5]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B10_wrk.IMD	10,213	stevel	stevel	[encrypted]
[6]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B11.TIF	34,131,348	stevel	stevel	[encrypted]
[7]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B11.TIF.ovr	5,891,395	stevel	stevel	[encrypted]
[8]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B11_wrk.IMD	10,213	stevel	stevel	[encrypted]
[9]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B1_wrk.IMD	10,213	stevel	stevel	[encrypted]
[10]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B2.TIF	64,369,211	stevel	stevel	[encrypted]
2019-04-05 21:32:15,757 [main] INFO  tools.ListFiles (DurationInfo.java:close(100)) - Directory list: duration 0:01:235

Found 10 files, 124 milliseconds per file
Data size 217,741,136 bytes, 21,774,113 bytes per file
```

## Command `bucketstate`

Prints some of the low level diagnostics information about a bucket which
can be obtained via the AWS APIs.

```bash
bin/hadoop jar cloudstore-0.1-SNAPSHOT.jar \
            bucketstate \
            s3a://mybucket/

2019-07-25 16:54:50,678 [main] INFO  tools.BucketState (DurationInfo.java:<init>(53)) - Starting: Bucket State
2019-07-25 16:54:54,216 [main] WARN  s3a.S3AFileSystem (S3AFileSystem.java:getAmazonS3ClientForTesting(675)) - Access to S3A client requested, reason Diagnostics
Bucket owner is alice (ID=593...e1)
Bucket policy:
NONE
```

If you don't have the permissions to read the bucket policy, you get a stack trace.

```bash
bin/hadoop jar cloudstore-0.1-SNAPSHOT.jar \
            bucketstate \
            s3a://mybucket/

2019-07-25 16:55:23,023 [main] INFO  tools.BucketState (DurationInfo.java:<init>(53)) - Starting: Bucket State
2019-07-25 16:55:25,993 [main] WARN  s3a.S3AFileSystem (S3AFileSystem.java:getAmazonS3ClientForTesting(675)) - Access to S3A client requested, reason Diagnostics
Bucket owner is alice (ID=593...e1)
2019-07-25 16:55:26,883 [main] INFO  tools.BucketState (DurationInfo.java:close(100)) - Bucket State: duration 0:03:862
com.amazonaws.services.s3.model.AmazonS3Exception: The specified method is not allowed against this resource. (Service: Amazon S3; Status Code: 405; Error Code: MethodNotAllowed; Request ID: 3844E3089E3801D8; S3 Extended Request ID: 3HJVN5+MvOGit087AFqKLUyOUCU9inCakvJ44GW5Wb4toiVipEiv5uK6A54LQBjdKFYUU8ZI5XQ=), S3 Extended Request ID: 3HJVN5+MvOGit087AFqKLUyOUCU9inCakvJ44GW5Wb4toiVipEiv5uK6A54LQBjdKFYUU8ZI5XQ=
  at com.amazonaws.http.AmazonHttpClient$RequestExecutor.handleErrorResponse(AmazonHttpClient.java:1712)
  at com.amazonaws.http.AmazonHttpClient$RequestExecutor.executeOneRequest(AmazonHttpClient.java:1367)
  at com.amazonaws.http.AmazonHttpClient$RequestExecutor.executeHelper(AmazonHttpClient.java:1113)
  at com.amazonaws.http.AmazonHttpClient$RequestExecutor.doExecute(AmazonHttpClient.java:770)
  at com.amazonaws.http.AmazonHttpClient$RequestExecutor.executeWithTimer(AmazonHttpClient.java:744)
  at com.amazonaws.http.AmazonHttpClient$RequestExecutor.execute(AmazonHttpClient.java:726)
  at com.amazonaws.http.AmazonHttpClient$RequestExecutor.access$500(AmazonHttpClient.java:686)
  at com.amazonaws.http.AmazonHttpClient$RequestExecutionBuilderImpl.execute(AmazonHttpClient.java:668)
  at com.amazonaws.http.AmazonHttpClient.execute(AmazonHttpClient.java:532)
  at com.amazonaws.http.AmazonHttpClient.execute(AmazonHttpClient.java:512)
  at com.amazonaws.services.s3.AmazonS3Client.invoke(AmazonS3Client.java:4920)
  at com.amazonaws.services.s3.AmazonS3Client.invoke(AmazonS3Client.java:4866)
  at com.amazonaws.services.s3.AmazonS3Client.getBucketPolicy(AmazonS3Client.java:2917)
  at com.amazonaws.services.s3.AmazonS3Client.getBucketPolicy(AmazonS3Client.java:2890)
  at org.apache.hadoop.fs.tools.BucketState.run(BucketState.java:93)
  at org.apache.hadoop.util.ToolRunner.run(ToolRunner.java:76)
  at org.apache.hadoop.util.ToolRunner.run(ToolRunner.java:90)
  at org.apache.hadoop.fs.tools.BucketState.exec(BucketState.java:111)
  at org.apache.hadoop.fs.tools.BucketState.main(BucketState.java:120)
  at bucketstate.main(bucketstate.java:24)
  at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
  at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
  at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
  at java.lang.reflect.Method.invoke(Method.java:498)
  at org.apache.hadoop.util.RunJar.run(RunJar.java:318)
  at org.apache.hadoop.util.RunJar.main(RunJar.java:232)
2019-07-25 16:55:26,886 [main] INFO  util.ExitUtil (ExitUtil.java:terminate(210)) - Exiting with status -1: com.amazonaws.services.s3.model.AmazonS3Exception: The specified method is not allowed against this resource. (Service: Amazon S3; Status Code: 405; Error Code: MethodNotAllowed; Request ID: 3844E3089E3801D8; S3 Extended Request ID: 3HJVN5+MvOGit087AFqKLUyOUCU9inCakvJ44GW5Wb4toiVipEiv5uK6A54LQBjdKFYUU8ZI5XQ=), S3 Extended Request ID: 3HJVN5+MvOGit087AFqKLUyOUCU9inCakvJ44GW5Wb4toiVipEiv5uK6A54LQBjdKFYUU8ZI5XQ=

```


## Development and Future Work

_Roadmap_: Whatever we need to debug things.

This file can be grabbed via `curl` statements and executed to help automate
testing of cluster deployments.

To help with doing this with the latest releases, it may be enhanced regularly,
with new releases. 

There is no real release plan other than this.

Possible future work

* Exploration of higher performance IO
* Diagnostics/testing of integration with foundational Hadoop operations.
* Improving CLI testing with various probes designed to be invoked in a shell
  and fail with meaningful exit codes. E.g: verifying that a filesystem has a specific (key, val)
  property, that a specific env var made it through.
* something to scan hadoop installations for duplicate artifacts, which knowledge
  of JARS which main contain the same classes (aws-shaded, aws-s3, etc),
  and the knowledge of required consistencies (hadoop-*, jackson-*).
* And extend to SPARK_HOME, Hive, etc.

Contributions through PRs welcome.

Bug reports: please include environment and ideally patches. 

There is no formal support for this. Sorry. 

