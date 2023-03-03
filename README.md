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
3. Ability to compile against older versions. We've currently switched to Hadoop 3.3+
   due to the need to make API calls and operations not in older versions.
 
*Author*: Steve Loughran, Hadoop Committer, plus anyone else who has debugging needs.


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
primarily due to authentication, classpath and network settings

## See also

* [Security](./SECURITY.md)
* [Building](./BUILDING.md)

# Command Index

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
hadoop jar cloudstore-1.0.jar storediag -j -5 s3a://landsat-pds/
hadoop jar cloudstore-1.0.jar storediag --tokenfile mytokens.bin s3a://my-readwrite-bucket/
hadoop jar cloudstore-1.0.jar storediag wasb://container@user/subdir
hadoop jar cloudstore-1.0.jar storediag abfs://container@user/
```
 
The remote store is required to grant full R/W access to the caller, otherwise
the creation tests will fail.

The `--tokenfile` option loads tokens saved with `hdfs fetchdt`. It does
not need Kerberos, though most filesystems expect Kerberos enabled for
them to pick up tokens (not S3A, potentially other stores).

### Options

```
-w    Attempt writes to as well as reads from the filesystem
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
hadoop jar cloudstore-1.0.jar storediag -j -5 -required required.txt s3a://something/
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


## Command `bandwidth`

Measure upload/download bandwidth.

```bash
 bin/hadoop jar cloudstore-1.0.jar bandwidth
Usage: bandwidth [options] size <path>
        -D <key=value>  Define a property
        -tokenfile <file>       Hadoop token file to load
        -verbose        print verbose output
        -xmlfile <file> XML config file to load
```

See [bandwidth](src/main/site/bandwidth.md) for details.

## Command `bucketstate`

Prints some of the low level diagnostics information about an S3 bucket which
can be obtained via the AWS APIs.

```
hadoop jar cloudstore-1.0.jar \
            bucketstate \
            s3a://mybucket/

2019-07-25 16:54:50,678 [main] INFO  tools.BucketState (DurationInfo.java:<init>(53)) - Starting: Bucket State
2019-07-25 16:54:54,216 [main] WARN  s3a.S3AFileSystem (S3AFileSystem.java:getAmazonS3ClientForTesting(675)) - Access to S3A client requested, reason Diagnostics
Bucket owner is alice (ID=593...e1)
Bucket policy:
NONE
```

If you don't have the permissions to read the bucket policy, you get a stack trace.

```
hadoop jar cloudstore-1.0.jar \
            bucketstate \
            s3a://mybucket/

2019-07-25 16:55:23,023 [main] INFO  tools.BucketState (DurationInfo.java:<init>(53)) - Starting: Bucket State
2019-07-25 16:55:25,993 [main] WARN  s3a.S3AFileSystem (S3AFileSystem.java:getAmazonS3ClientForTesting(675)) - Access to S3A client requested, reason Diagnostics
Bucket owner is alice (ID=593...e1)
2019-07-25 16:55:26,883 [main] INFO  tools.BucketState (DurationInfo.java:close(100)) - Bucket State: duration 0:03:862
com.amazonaws.services.s3.model.AmazonS3Exception: The specified method is not allowed against this resource. (Service: Amazon S3; Status Code: 405; Error Code: MethodNotAllowed; Request ID: 3844E3089E3801D8; S3 Extended Request ID: 3HJVN5+MvOGit087AFqKLUyOUCU9inCakvJ44GW5Wb4toiVipEiv5uK6A54LQBjdKFYUU8ZI5XQ=), S3 Extended Request ID: 3HJVN5+MvOGit087AFqKLUyOUCU9inCakvJ44GW5Wb4toiVipEiv5uK6A54LQBjdKFYUU8ZI5XQ=

```

## Command `cloudup` -upload and download files; optimised for cloud storage 

Bulk download of everything from s3a://bucket/qelogs/ to the local dir localquelogs (assuming the default fs is file://)

Usage

```
cloudup -s source -d dest [-o] [-i] [-l <largest>] [-t threads] 

-s <uri> : source
-d <uri> : dest
-o: overwrite
-i: ignore failures
-t <n> : number of threads
-l <n> : number of "largest" files to start uploading before just randomly picking files.

```

Algorithm

1. source files are listed.
1. A pool of worker threads is created
2. the largest N files are queued for upload first, where N is a default or the value set by `-l`.
1. The remainder of the files are randomized to avoid throttling and then queued
1. the program waits for everything to complete.
1. Source and dest FS stats are printed.

This is not `distcp` run across a cluster; it's a single process with some threads. 
Works best for reading lots of small files from an object store or when you have a 
mix of large and small files to download or uplaod.



```
bin/hadoop jar cloudstore-1.0.jar cloudup \
 -s s3a://bucket/qelogs/ \
 -d localqelogs \
 -t 32 -o
```

and the other way

```
bin/hadoop jar cloudstore-1.0.jar cloudup \
 -d localqelogs \
 -s s3a://bucket/qelogs/ \
 -t 32 -o  -l 4
```

## Command `committerinfo`

Tries to instantiate a committer using the Hadoop 3.1+ committer factory mechanism, printing out
what committer a specific path will create.

If this command fails with a `ClassNotFoundException` it can mean that the version of hadoop the command is being
run against doesn't have this new API. The committer is therefore explicitly the classic `FileOutputCommitter`.


*Good*: ABFS container with the `ManifestCommitter`

```

hadoop jar cloudstore-1.0.jar committerinfo abfs://testing@ukwest.dfs.core.windows.net/

2021-09-16 19:42:59,731 [main] INFO commands.CommitterInfo (StoreDurationInfo.java:<init>(53)) - Starting: Create committer Committer factory for path abfs://testing@ukwest.dfs.core.windows.net/ is org.apache.hadoop.mapreduce.lib.output.committer.manifest.ManifestCommitterFactory@3315d2d7
(classname org.apache.hadoop.mapreduce.lib.output.committer.manifest.ManifestCommitterFactory)
2021-09-16 19:43:00,897 [main] INFO manifest.ManifestCommitter (ManifestCommitter.java:<init>(144)) - Created ManifestCommitter with JobID job__0000, Task Attempt attempt__0000_r_000000_1 and destination abfs://testing@ukwest.dfs.core.windows.net/ Created committer of class org.apache.hadoop.mapreduce.lib.output.committer.manifest.ManifestCommitter:
ManifestCommitter{ManifestCommitterConfig{destinationDir=abfs://testing@ukwest.dfs.core.windows.net/, role='task committer', taskAttemptDir=abfs://testing@ukwest.dfs.core.windows.net/_temporary/manifest_job__0000/0/_temporary/attempt__0000_r_000000_1, createJobMarker=true, jobUniqueId='job__0000', jobUniqueIdSource='JobID', jobAttemptNumber=0, jobAttemptId='job__0000_0', taskId='task__0000_r_000000', taskAttemptId='attempt__0000_r_000000_1'}, iostatistics=counters=();

gauges=();

minimums=();

maximums=();

means=(); }


```

This is the new committer optimised for performance on abfs and gcs, where
directory listings are performed in task commit, and the lists of files to
rename and directories to create saved in manifest files.
Job commit consists of loading the manifests and renaming all the files,
operations which can all be parallelized.
ABFS adds extra integration, including rate limiting of rename operations
and recovery from rename failures under load. If your hadoop release
supports this committer, do try it.

*Danger*: S3A Bucket with the classic `FileOutputCommitter`

```
> hadoop jar cloudstore-1.0.jar committerinfo s3a://landsat-pds/
  2019-08-05 17:38:38,213 [main] INFO  commands.CommitterInfo (DurationInfo.java:<init>(53)) - Starting: Create committer
  2019-08-05 17:38:40,968 [main] WARN  commit.AbstractS3ACommitterFactory (S3ACommitterFactory.java:createTaskCommitter(90)) - Using standard FileOutputCommitter to commit work. This is slow and potentially unsafe.
  2019-08-05 17:38:40,968 [main] INFO  output.FileOutputCommitter (FileOutputCommitter.java:<init>(141)) - File Output Committer Algorithm version is 2
  2019-08-05 17:38:40,968 [main] INFO  output.FileOutputCommitter (FileOutputCommitter.java:<init>(156)) - FileOutputCommitter skip cleanup _temporary folders under output directory:false, ignore cleanup failures: false
  2019-08-05 17:38:40,970 [main] INFO  commit.AbstractS3ACommitterFactory (AbstractS3ACommitterFactory.java:createOutputCommitter(54)) - Using Commmitter FileOutputCommitter{PathOutputCommitter{context=TaskAttemptContextImpl{JobContextImpl{jobId=job__0000}; taskId=attempt__0000_r_000000_1, status=''}; org.apache.hadoop.mapreduce.lib.output.FileOutputCommitter@6cc0bcf6}; outputPath=s3a://landsat-pds/, workPath=s3a://landsat-pds/_temporary/0/_temporary/attempt__0000_r_000000_1, algorithmVersion=2, skipCleanup=false, ignoreCleanupFailures=false} for s3a://landsat-pds/
  Created committer of class org.apache.hadoop.mapreduce.lib.output.FileOutputCommitter: FileOutputCommitter{PathOutputCommitter{context=TaskAttemptContextImpl{JobContextImpl{jobId=job__0000}; taskId=attempt__0000_r_000000_1, status=''}; org.apache.hadoop.mapreduce.lib.output.FileOutputCommitter@6cc0bcf6}; outputPath=s3a://landsat-pds/, workPath=s3a://landsat-pds/_temporary/0/_temporary/attempt__0000_r_000000_1, algorithmVersion=2, skipCleanup=false, ignoreCleanupFailures=false}
  2019-08-05 17:38:40,970 [main] INFO  commands.CommitterInfo (DurationInfo.java:close(100)) - Create committer: duration 0:02:758
```


*Good* : S3A bucket with a staging committer:

```
>  hadoop jar  cloudstore-1.0.jar committerinfo s3a://hwdev-steve-ireland-new/
  2019-08-05 17:42:53,563 [main] INFO  commands.CommitterInfo (DurationInfo.java:<init>(53)) - Starting: Create committer
  Committer factory for path s3a://hwdev-steve-ireland-new/ is org.apache.hadoop.fs.s3a.commit.S3ACommitterFactory@3088660d (classname org.apache.hadoop.fs.s3a.commit.S3ACommitterFactory)
  2019-08-05 17:42:55,433 [main] INFO  output.FileOutputCommitter (FileOutputCommitter.java:<init>(141)) - File Output Committer Algorithm version is 1
  2019-08-05 17:42:55,434 [main] INFO  output.FileOutputCommitter (FileOutputCommitter.java:<init>(156)) - FileOutputCommitter skip cleanup _temporary folders under output directory:false, ignore cleanup failures: false
  2019-08-05 17:42:55,434 [main] INFO  commit.AbstractS3ACommitterFactory (S3ACommitterFactory.java:createTaskCommitter(83)) - Using committer directory to output data to s3a://hwdev-steve-ireland-new/
  2019-08-05 17:42:55,435 [main] INFO  commit.AbstractS3ACommitterFactory (AbstractS3ACommitterFactory.java:createOutputCommitter(54)) - Using Commmitter StagingCommitter{AbstractS3ACommitter{role=Task committer attempt__0000_r_000000_1, name=directory, outputPath=s3a://hwdev-steve-ireland-new/, workPath=file:/tmp/hadoop-stevel/s3a/job__0000/_temporary/0/_temporary/attempt__0000_r_000000_1}, conflictResolution=APPEND, wrappedCommitter=FileOutputCommitter{PathOutputCommitter{context=TaskAttemptContextImpl{JobContextImpl{jobId=job__0000}; taskId=attempt__0000_r_000000_1, status=''}; org.apache.hadoop.mapreduce.lib.output.FileOutputCommitter@5a865416}; outputPath=file:/Users/stevel/Projects/Releases/hadoop-3.3.0-SNAPSHOT/tmp/staging/stevel/job__0000/staging-uploads, workPath=null, algorithmVersion=1, skipCleanup=false, ignoreCleanupFailures=false}} for s3a://hwdev-steve-ireland-new/
  Created committer of class org.apache.hadoop.fs.s3a.commit.staging.DirectoryStagingCommitter: StagingCommitter{AbstractS3ACommitter{role=Task committer attempt__0000_r_000000_1, name=directory, outputPath=s3a://hwdev-steve-ireland-new/, workPath=file:/tmp/hadoop-stevel/s3a/job__0000/_temporary/0/_temporary/attempt__0000_r_000000_1}, conflictResolution=APPEND, wrappedCommitter=FileOutputCommitter{PathOutputCommitter{context=TaskAttemptContextImpl{JobContextImpl{jobId=job__0000}; taskId=attempt__0000_r_000000_1, status=''}; org.apache.hadoop.mapreduce.lib.output.FileOutputCommitter@5a865416}; outputPath=file:/Users/stevel/Projects/Releases/hadoop-3.3.0-SNAPSHOT/tmp/staging/stevel/job__0000/staging-uploads, workPath=null, algorithmVersion=1, skipCleanup=false, ignoreCleanupFailures=false}}
  2019-08-05 17:42:55,435 [main] INFO  commands.CommitterInfo (DurationInfo.java:close(100)) - Create committer: duration 0:01:874
```

The log entry about a `FileOutputCommitter` appears because the Staging Committers use the cluster filesystem (HDFS, etc) to safely pass information from the workers to the application master.

The classic filesystem committer v1 is used because it works well here: the filesystem is consistent and operations are fast. Neither of those conditions are met with AWS S3.


*Good* : S3A bucket with a magic committer:

```
> hadoop jar cloudstore-1.0.jar committerinfo s3a://hwdev-steve-ireland-new/

2019-08-05 17:37:42,615 [main] INFO  commands.CommitterInfo (DurationInfo.java:<init>(53)) - Starting: Create committer
2019-08-05 17:37:44,462 [main] INFO  commit.AbstractS3ACommitterFactory (S3ACommitterFactory.java:createTaskCommitter(83)) - Using committer magic to output data to s3a://hwdev-steve-ireland-new/
2019-08-05 17:37:44,462 [main] INFO  commit.AbstractS3ACommitterFactory (AbstractS3ACommitterFactory.java:createOutputCommitter(54)) - Using Commmitter MagicCommitter{} for s3a://hwdev-steve-ireland-new/
Created committer of class org.apache.hadoop.fs.s3a.commit.magic.MagicS3GuardCommitter: MagicCommitter{}
2019-08-05 17:37:44,462 [main] INFO  commands.CommitterInfo (DurationInfo.java:close(100)) - Create committer: duration 0:01:849
```


## Command `dux`  "Du, extended"

A variant on the hadoop `du` command which does a recursive `listFiles()`
call on every directory immediately under the source path -in separate threads.

For any store which supports higher performance deep tree listing (S3A in particular)
This can be significantly faster than du's normal treewalk.

Even without that, because lists are done in separate threads, a speedup is
almost guaranteed. 

There is no scheduling of work into separate threads within a directory; those stores
which do prefetching in separate threads (recent ABFS and S3A builds) do add some
paralellism here.


```
Usage: dux
        -D <key=value>  Define a property
        -tokenfile <file>       Hadoop token file to load
        -xmlfile <file> XML config file to load
        -threads <threads>      number of threads
        -limit <limit>  limit of files to list
        -verbose        print verbose output
        <path>
```

The `-verbose` option prints out more filesystem statistics, and of
the list iterators (useful if they publish statistics)

`-limit` puts a limit on the total number of files to scan; this
is useful when doing deep scans of buckets so as to put an upper bound
on the scan. Note, when used against S3 an ERROR may be printed in the AWS SDK.
This is harmless; it comes from the SDK thread pool being closed while
a list page prefetch is in progress.




##  Command `fetchdt`

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
> bin/hadoop jar cloudstore-1.0.jar fetchdt -p -r file:/tmp/secrets.bin s3a://landsat-pds/
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
> hadoop jar cloudstore-1.0.jar fetchdt -p -r file:/tmm/secrets.bin file:///

Collecting tokens for 1 filesystem to to file:/tmm/secrets.bin
2018-12-05 17:47:00,970 INFO fs.FetchTokens: Starting: Fetching token for file:/
No token for file:/
2018-12-05 17:47:00,972 INFO fs.FetchTokens: Fetching token for file:/: duration 0:00:003
2018-12-05 17:47:00,973 INFO util.ExitUtil: Exiting with status 44: No token issued by filesystem file:///
```

Same command, without the -r. 

```bash
> hadoop jar cloudstore-1.0.jar fetchdt -p file:/tmm/secrets.bin file:///
Collecting tokens for 1 filesystem to to file:/tmp/secrets.bin
2018-12-05 17:54:26,776 INFO fs.FetchTokens: Starting: Fetching token for file:/tmp
No token for file:/tmp
2018-12-05 17:54:26,778 INFO fs.FetchTokens: Fetching token for file:/tmp: duration 0:00:002
No tokens collected, file file:/tmp/secrets.bin unchanged
```

The token file is not modified.


## Command `filestatus`

Calls `getFileStatus` on the listed paths, prints the values. For stores
which have more detail on the toString value of any subclass of `FileStatus`,
this can be more meaningful.

Also prints the time to execute each operation (including instantiating the store),
and with the `-verbose` option, the store statistics.

```
hadoop jar  cloudstore-1.0.jar \
            filestatus  \
            s3a://guarded-table/example

2019-07-31 21:48:34,963 [main] INFO  commands.PrintStatus (DurationInfo.java:<init>(53)) - Starting: get path status
s3a://guarded-table/example S3AFileStatus{path=s3a://guarded-table/example; isDirectory=false; length=0; replication=1; 
  blocksize=33554432; modification_time=1564602680000;
  access_time=0; owner=stevel; group=stevel;
  permission=rw-rw-rw-; isSymlink=false; hasAcl=false; isEncrypted=true; isErasureCoded=false}
  isEmptyDirectory=FALSE eTag=d41d8cd98f00b204e9800998ecf8427e versionId=null
2019-07-31 21:48:37,182 [main] INFO  commands.PrintStatus (DurationInfo.java:close(100)) - get path status: duration 0:02:221
```

## Command `gcscreds`

Help debug gcs credential bindings as set in `fs.gs.auth.service.account.private.key`

it does ths with some better diagnostics of parsing problems.

warning: at -verbose, this prints your private key

```
hadoop jar cloudstore-1.0.jar gcscreds gs://bucket/

key uses \n for separator -gs connector must convert to line endings
2022-01-19 17:55:51,016 [main] INFO  gs.PemReader (PemReader.java:readNextSection(86)) - title match  at line 1
2022-01-19 17:55:51,020 [main] INFO  gs.PemReader (PemReader.java:readNextSection(88)) - scanning for end 
Parsed private key -entry length 28 lines
factory com.google.cloud.hadoop.repackaged.gcs.com.google.cloud.hadoop.util.CredentialFactory@d706f19
```

## Command `iampolicy`

Generate AWS IAM policy for a given bucket
```
hadoop jar cloudstore-1.0.jar iampolicy s3a://example-bucket/

{
  "Version" : "2012-10-17",
  "Statement" : [ {
    "Sid" : "7",
    "Effect" : "Allow",
    "Action" : [ "s3:GetBucketLocation", "s3:ListBucket*" ],
    "Resource" : "arn:aws:s3:::example-bucket"
  }, {
    "Sid" : "8",
    "Effect" : "Allow",
    "Action" : [ "s3:Get*", "s3:PutObject", "s3:PutObjectAcl", "s3:DeleteObject", "s3:AbortMultipartUpload" ],
    "Resource" : "arn:aws:s3:::example-bucket/*"
  }, {
    "Sid" : "1",
    "Effect" : "Allow",
    "Action" : "kms:*",
    "Resource" : "*"
  } ]
}

```
Notes:
* KMS policy is always added in case data is encrypted/decrypted with S3-KMS; it is not need if this is not the case.
* Read and Write access up the tree is needed. Maybe if you enable directory marker retention writing from root becomes optional.
* "s3:GetBucketLocation" is used by the bucket existence v2 check. If the probe is at 0, it is never called.


## Command `list`

Do a recursive listing of a path. Uses `listFiles(path, recursive)`, so for any object store
which can do this as a deep paginated scan, is much, much faster.

```
Usage: list
  -D <key=value>    Define a property
  -tokenfile <file> Hadoop token file to load
  -limit <limit>    limit of files to list
  -verbose          print verbose output
  -xmlfile <file>   XML config file to load
```

Example: list some of the AWS public landsat store.

```bash
> bin/hadoop jar cloudstore-1.0.jar list -limit 10 s3a://landsat-pds/

Listing up to 10 files under s3a://landsat-pds/
2019-04-05 21:32:14,523 [main] INFO  tools.ListFiles (StoreDurationInfo.java:<init>(53)) - Starting: Directory list
2019-04-05 21:32:14,524 [main] INFO  tools.ListFiles (StoreDurationInfo.java:<init>(53)) - Starting: First listing
2019-04-05 21:32:15,754 [main] INFO  tools.ListFiles (DurationInfo.java:close(100)) - First listing: duration 0:01:230
[1] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B1.TIF 63,786,465 stevel stevel [encrypted]
[2] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B1.TIF.ovr 8,475,353 stevel stevel [encrypted]
[3] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B10.TIF 35,027,713 stevel stevel [encrypted]
[4] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B10.TIF.ovr 6,029,012 stevel stevel [encrypted]
[5] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B10_wrk.IMD 10,213 stevel stevel [encrypted]
[6] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B11.TIF 34,131,348 stevel stevel [encrypted]
[7] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B11.TIF.ovr 5,891,395 stevel stevel [encrypted]
[8] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B11_wrk.IMD 10,213 stevel stevel [encrypted]
[9] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B1_wrk.IMD 10,213 stevel stevel [encrypted]
[10] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B2.TIF 64,369,211 stevel stevel [encrypted]
2019-04-05 21:32:15,757 [main] INFO  tools.ListFiles (DurationInfo.java:close(100)) - Directory list: duration 0:01:235

Found 10 files, 124 milliseconds per file
Data size 217,741,136 bytes, 21,774,113 bytes per file
```
## Command `listobjects`

List all objects and a path through the low-level S3 APIs.
This bypasses the filesystem metaphor and gives the real view
of the object store.

The `-purge` option will remove all directory markers.

```
Usage: listobjects <path>
    -D <key=value> Define a property
    -limit <limit> limit of files to list
    -purge         purge directory markers
    -tokenfile <file> Hadoop token file to load
    -verbose print verbose output
    -xmlfile <file> XML config file to load
```

## Command `listversions`

See [listversions](./src/main/site/versioned-objects.md).

## Command `localhost`

Print out localhost information from java APIs and then the hadoop network APIs.

## Command `locatefiles`

Use the mapreduce `LocatedFileStatusFetcher` to scan for all non-hidden
files under a path. This matches exactly the scan process used in `FileInputFormat`,
so offers a command line way to view and tune scans of object stores.
It can also be used in comparison with the `list` command to compare the
difference between the maximum performance of scanning the directory
tree with the actual performance you are likely to see during query planning.

To control the number of threads used to scan directories in production
jobs, set the value in the configuration option
`mapreduce.input.fileinputformat.list-status.num-threads` 

Usage:

```
hadoop jar cloudstore-1.0.jar locatefiles
Usage: locatefiles
  -D <key=value>    Define a property
  -tokenfile <file> Hadoop token file to load
  -xmlfile <file>   XML config file to load
  -threads <threads> number of threads
  -verbose          print verbose output
[<path>|<pattern>]```
```

Example

```
> hadoop jar cloudstore-1.0.jar locatefiles \
 -threads 8 -verbose \
 s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/


Locating files under s3a://landsat-pds/L8/001/002/LC80010022016230LGN00 with thread count 8
===========================================================================================

2019-07-29 16:48:19,844 [main] INFO  tools.LocateFiles (DurationInfo.java:<init>(53)) - Starting: List located files
2019-07-29 16:48:19,847 [main] INFO  tools.LocateFiles (DurationInfo.java:<init>(53)) - Starting: LocateFileStatus execution
2019-07-29 16:48:24,645 [main] INFO  tools.LocateFiles (DurationInfo.java:close(100)) - LocateFileStatus execution: duration 0:04:798
[0001] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B1.TIF 63,786,465 stevel stevel [encrypted]
[0002] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B1.TIF.ovr 8,475,353 stevel stevel [encrypted]
[0003] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B10.TIF 35,027,713 stevel stevel [encrypted]
[0004] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B10.TIF.ovr 6,029,012 stevel stevel [encrypted]
...
[0039] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_thumb_large.jpg 270,427 stevel stevel [encrypted]
[0040] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_thumb_small.jpg 12,873 stevel stevel [encrypted]
[0041] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/index.html 3,780 stevel stevel [encrypted]
2019-07-29 16:48:24,652 [main] INFO  tools.LocateFiles (DurationInfo.java:close(100)) - List located files: duration 0:04:810

Found 41 files, 117 milliseconds per file
Data size 923,518,099 bytes, 22,524,831 bytes per file

Storage Statistics
==================

directories_created 0
directories_deleted 0
files_copied 0
files_copied_bytes 0
files_created 0
files_deleted 0
files_delete_rejected 0
fake_directories_created 0
fake_directories_deleted 0
ignored_errors 0
op_copy_from_local_file 0
op_create 0
op_create_non_recursive 0
op_delete 0
op_exists 0
op_get_delegation_token 0
op_get_file_checksum 0
op_get_file_status 2
op_glob_status 1
op_is_directory 0
op_is_file 0
op_list_files 0
op_list_located_status 1
op_list_status 0
op_mkdirs 0
op_open 0
op_rename 0
object_copy_requests 0
object_delete_requests 0
object_list_requests 3
object_continue_list_requests 0
object_metadata_requests 4
object_multipart_initiated 0
object_multipart_aborted 0
object_put_requests 0
object_put_requests_completed 0
object_put_requests_active 0
object_put_bytes 0
object_put_bytes_pending 0
object_select_requests 0
...
store_io_throttled 0
```

You get the metrics with the `-verbose` option;


There is plenty room for improvements in S3A directory tree scanning.
Patches welcome! 

You can also explore what directory tree structure is most efficient here.

## Command `mkcsv`

Creates a large CSV file designed to trigger/validate the ABFS prefetching bug which
came in HADOOP-17156/

See [mkcsv](src/main/site/mkcsv.md)

## Command `pathcapability`

Probes a filesystem for offering a specific named capability on the given path.

Requires a version of Hadoop with the `PathCapabilities` interface, which includes Hadoop 3.3 onwards.

```
bin/hadoop jar cloudstore-1.0.jar pathcapability
Usage: pathcapability [options] <capability> <path>
    -D <key=value> Define a property
    -tokenfile <file> Hadoop token file to load
    -verbose print verbose output
    -xmlfile <file> XML config file to load
```

```bash
hadoop jar cloudstore-1.0.jar pathcapability fs.s3a.capability.select.sql s3a://landsat-pds/

Using filesystem s3a://landsat-pds
Path s3a://landsat-pds/ has capability fs.s3a.capability.select.sql
```

The exit code of the command is 0 if the capability is present, -1 if absent, and 55 if the hadoop version does not support the API.
 Approximate HTTP equivalent: `505: Version Not Supported`. 
 
As it is in Hadoop 3.3, all APIs new to that release (including `openFile()`) can absolutely be probed for. Otherwise, the 55 response may mean "an API is implemented, just not the probe". 

## Command `regions`

Invokes the AWS region provider chain to see if the client can automatically determine the region of AWS SDK calls.

This is how all AWS service clients determine the region for sending/signing requests if
not explicitly set.

```bash
hadoop jar cloudstore-1.0.jar regions

Determining AWS region for SDK clients
======================================


Determining region using AwsEnvVarOverrideRegionProvider
========================================================

Use environment variable AWS_REGION
2021-06-22 12:04:59,277 [main] INFO  extra.Regions (StoreDurationInfo.java:<init>(53)) - Starting: AwsEnvVarOverrideRegionProvider.getRegion()
2021-06-22 12:04:59,284 [main] INFO  extra.Regions (StoreDurationInfo.java:close(100)) - AwsEnvVarOverrideRegionProvider.getRegion(): duration 0:00:010
region is not known

Determining region using AwsSystemPropertyRegionProvider
========================================================

System property aws.region
2021-06-22 12:04:59,286 [main] INFO  extra.Regions (StoreDurationInfo.java:<init>(53)) - Starting: AwsSystemPropertyRegionProvider.getRegion()
2021-06-22 12:04:59,287 [main] INFO  extra.Regions (StoreDurationInfo.java:close(100)) - AwsSystemPropertyRegionProvider.getRegion(): duration 0:00:000
region is not known

Determining region using AwsProfileRegionProvider
=================================================

Region info in ~/.aws/config
2021-06-22 12:04:59,336 [main] INFO  extra.Regions (StoreDurationInfo.java:<init>(53)) - Starting: AwsProfileRegionProvider.getRegion()
2021-06-22 12:04:59,359 [main] INFO  extra.Regions (StoreDurationInfo.java:close(100)) - AwsProfileRegionProvider.getRegion(): duration 0:00:023
Region is determined as "eu-west-2"

Determining region using InstanceMetadataRegionProvider
=======================================================

EC2 metadata; will only work in AWS infrastructure
2021-06-22 12:04:59,361 [main] INFO  extra.Regions (StoreDurationInfo.java:<init>(53)) - Starting: InstanceMetadataRegionProvider.getRegion()
2021-06-22 12:04:59,363 [main] INFO  extra.Regions (StoreDurationInfo.java:close(100)) - InstanceMetadataRegionProvider.getRegion(): duration 0:00:002
WARNING: Provider raised an exception com.amazonaws.AmazonClientException:
    AWS_EC2_METADATA_DISABLED is set to true, not loading region from EC2 Instance Metadata service
region is not known

Region found: "eu-west-2"
=========================

Region was determined by AwsProfileRegionProvider as  "eu-west-2"


```

This setup has set the environment variable `AWS_EC2_METADATA_DISABLED`; if this variable was unset
and the command executed outside AWS infrastructure then after a 15 second delay a stack trace warning of
a failure to connect to the instance metadata server.

```
2021-06-22 11:54:15,774 [main] WARN  util.EC2MetadataUtils (EC2MetadataUtils.java:getItems(410)) -
 Unable to retrieve the requested metadata (/latest/dynamic/instance-identity/document).
 Failed to connect to service endpoint: 
    com.amazonaws.SdkClientException: Failed to connect to service endpoint:
```

This is to be expected, given that the service isn't there.

## Command `restore`

Restores a versioned S3 Object to a path within the same bucket.

See [versioned objects](./src/main/site/versioned-objects.md).

## Command `safeprefetch`

Probes an abfs store for being vulnerable to
prefetch data corruption, providing the configuration
information to disable it if so.

See [safeprefetch](src/main/site/safeprefetch.md)

## Command `tarhardened`

Verify the hadoop release has had its untar command hardened and will
not evaluate commands passed in as filenames.

```bash
bin/hadoop jar $CLOUDSTORE tarhardened "file.tar; true"
```

*Bad*

```
Attempting to untar file with name "file.tar; true"
untar operation reported success

2023-01-27 16:42:35,931 [main] INFO  util.ExitUtil (ExitUtil.java:terminate(124)) - Exiting with status 0
```

Although the file doesn't exist, the bash "true" command was executed after the untar, so
the operation was reported as a success.

*Good*

```
2023-01-27 16:48:44,461 [main] INFO  util.ExitUtil (ExitUtil.java:terminate(210)) - Exiting with status -1: ExitCodeException exitCode=1: tar: Error opening archive: Failed to open 'file.tar; true'

```

The file `file.tar; true` was attempted to be opened; as it is not present the operation failed.
Expect a stack trace in the report

## Command `tlsinfo`

Print out tls information. The `storediag` command prints the same information;
this command purely looks at the JVM settings.


## Command `undelete`

"undeletes" S3 objects by removing directory tombstones from a bucket path.

See [versioned objects](./src/main/site/versioned-objects.md).

## S3A Diagnostics Credential Provider

[DiagnosticsAWSCredentialsProvider](src/main/site/diagnosticsawscredentialsprovider.md)
is an AWS credential provider which logs the fs.s3a login secrets (obfuscated and md5).


## Development and Future Work

_Roadmap_: Whatever we need to debug things.

This file can be grabbed via `curl` statements and executed to help automate
testing of cluster deployments.

To help with doing this with the latest releases, it may be enhanced regularly,
with new releases. 

There is no real release plan other than this.

Possible future work

* Exploration of higher performance IO.
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

## Building

To build against the latest hadoop 3.3
```bash
mvn clean install -Phadoop-3.3 -Pextra
```

The `extra` profile pulls in extra source which calls some S3A FS API calls not in earlier hadoop versions
(note: they are in CDP 7.x/CDP cloud).

To build against Hadoop 3.2

```bash
mvn clean install -Phadoop-3.2
```


Building against older versions.
This is generally done only in an emergency and hasn't been done for a while; it's probably not going to compile. Sorry.
