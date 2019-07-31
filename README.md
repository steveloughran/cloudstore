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

## Command `locatefiles`

Use the mapreduce `LocatedFileStatusFetcher` to scan for all non-hidden
files under a path. This matches exactly the scan process used in `FileInputFormat`,
so offers a command line way to view and tune scans of object stores.
It can also be used in comparison with the `list` command to compare the
difference between the maximum performance of scanning the directory
tree with the actual performance you are likely to see during query planning.

Usage:

```
hadoop jar cloudstore-0.1-SNAPSHOT.jar locatefiles
Usage: locatefiles
  -D <key=value>	Define a property
  -tokenfile <file>	Hadoop token file to load
  -xmlfile <file>	XML config file to load
  -threads <threads>	number of threads
  -verbose	print verbose output
[<path>|<pattern>]```
```

Example

```
> hadoop jar cloudstore-0.1-SNAPSHOT.jar locatefiles \
 -threads 8 -verbose \
 s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/


Locating files under s3a://landsat-pds/L8/001/002/LC80010022016230LGN00 with thread count 8
===========================================================================================

2019-07-29 16:48:19,844 [main] INFO  tools.LocateFiles (DurationInfo.java:<init>(53)) - Starting: List located files
2019-07-29 16:48:19,847 [main] INFO  tools.LocateFiles (DurationInfo.java:<init>(53)) - Starting: LocateFileStatus execution
2019-07-29 16:48:24,645 [main] INFO  tools.LocateFiles (DurationInfo.java:close(100)) - LocateFileStatus execution: duration 0:04:798
[0001]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B1.TIF	63,786,465	stevel	stevel	[encrypted]
[0002]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B1.TIF.ovr	8,475,353	stevel	stevel	[encrypted]
[0003]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B10.TIF	35,027,713	stevel	stevel	[encrypted]
[0004]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B10.TIF.ovr	6,029,012	stevel	stevel	[encrypted]
[0005]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B10_wrk.IMD	10,213	stevel	stevel	[encrypted]
[0006]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B11.TIF	34,131,348	stevel	stevel	[encrypted]
[0007]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B11.TIF.ovr	5,891,395	stevel	stevel	[encrypted]
[0008]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B11_wrk.IMD	10,213	stevel	stevel	[encrypted]
[0009]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B1_wrk.IMD	10,213	stevel	stevel	[encrypted]
[0010]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B2.TIF	64,369,211	stevel	stevel	[encrypted]
[0011]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B2.TIF.ovr	8,554,522	stevel	stevel	[encrypted]
[0012]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B2_wrk.IMD	10,213	stevel	stevel	[encrypted]
[0013]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B3.TIF	64,624,022	stevel	stevel	[encrypted]
[0014]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B3.TIF.ovr	8,586,772	stevel	stevel	[encrypted]
[0015]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B3_wrk.IMD	10,213	stevel	stevel	[encrypted]
[0016]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B4.TIF	65,359,653	stevel	stevel	[encrypted]
[0017]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B4.TIF.ovr	8,673,306	stevel	stevel	[encrypted]
[0018]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B4_wrk.IMD	10,213	stevel	stevel	[encrypted]
[0019]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B5.TIF	66,093,949	stevel	stevel	[encrypted]
[0020]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B5.TIF.ovr	8,763,806	stevel	stevel	[encrypted]
[0021]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B5_wrk.IMD	10,213	stevel	stevel	[encrypted]
[0022]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B6.TIF	64,164,626	stevel	stevel	[encrypted]
[0023]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B6.TIF.ovr	8,593,010	stevel	stevel	[encrypted]
[0024]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B6_wrk.IMD	10,213	stevel	stevel	[encrypted]
[0025]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B7.TIF	62,973,644	stevel	stevel	[encrypted]
[0026]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B7.TIF.ovr	8,453,473	stevel	stevel	[encrypted]
[0027]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B7_wrk.IMD	10,213	stevel	stevel	[encrypted]
[0028]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B8.TIF	250,149,053	stevel	stevel	[encrypted]
[0029]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B8.TIF.ovr	33,608,245	stevel	stevel	[encrypted]
[0030]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B8_wrk.IMD	10,213	stevel	stevel	[encrypted]
[0031]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B9.TIF	39,328,679	stevel	stevel	[encrypted]
[0032]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B9.TIF.ovr	3,834,730	stevel	stevel	[encrypted]
[0033]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B9_wrk.IMD	10,213	stevel	stevel	[encrypted]
[0034]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_BQA.TIF	3,033,084	stevel	stevel	[encrypted]
[0035]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_BQA.TIF.ovr	586,478	stevel	stevel	[encrypted]
[0036]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_BQA_wrk.IMD	10,213	stevel	stevel	[encrypted]
[0037]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_MTL.json	9,269	stevel	stevel	[encrypted]
[0038]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_MTL.txt	7,645	stevel	stevel	[encrypted]
[0039]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_thumb_large.jpg	270,427	stevel	stevel	[encrypted]
[0040]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_thumb_small.jpg	12,873	stevel	stevel	[encrypted]
[0041]	s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/index.html	3,780	stevel	stevel	[encrypted]
2019-07-29 16:48:24,652 [main] INFO  tools.LocateFiles (DurationInfo.java:close(100)) - List located files: duration 0:04:810

Found 41 files, 117 milliseconds per file
Data size 923,518,099 bytes, 22,524,831 bytes per file

Storage Statistics
==================

directories_created	0
directories_deleted	0
files_copied	0
files_copied_bytes	0
files_created	0
files_deleted	0
files_delete_rejected	0
fake_directories_created	0
fake_directories_deleted	0
ignored_errors	0
op_copy_from_local_file	0
op_create	0
op_create_non_recursive	0
op_delete	0
op_exists	0
op_get_delegation_token	0
op_get_file_checksum	0
op_get_file_status	2
op_glob_status	1
op_is_directory	0
op_is_file	0
op_list_files	0
op_list_located_status	1
op_list_status	0
op_mkdirs	0
op_open	0
op_rename	0
object_copy_requests	0
object_delete_requests	0
object_list_requests	3
object_continue_list_requests	0
object_metadata_requests	4
object_multipart_initiated	0
object_multipart_aborted	0
object_put_requests	0
object_put_requests_completed	0
object_put_requests_active	0
object_put_bytes	0
object_put_bytes_pending	0
object_select_requests	0
...
store_io_throttled	0
```

You get the metrics with the `-verbose` option;


There is plenty room for improvements in directory tree scanning. 

1. There are needless HEAD/`getObjectMetadata` requests because
`S3AFileSystem.listLocatedStatus` probes the path for being a file or dir
before initiating the real LIST. It could do the LIST first and only fall back
to a HEAD after, on the basis that the invocations in query planning only call
this on directories, and in a real-world data source tree, there are fewer empty
directories to worry about, than dirs with real data.
1. when there are no wildcards in the supplied path, the globber should switch to
using the `locateFiles` API.

You can also explore what directory tree structure is most efficient here.


## Command `bucketstate`

Prints some of the low level diagnostics information about a bucket which
can be obtained via the AWS APIs.

```
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

```
hadoop jar cloudstore-0.1-SNAPSHOT.jar \
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


## Command `filestatus`

Calls `getFileStatus` on the listed paths, prints the values. For stores
which have more detail on the toString value of any subclass of `FileStatus`,
this can be more meaningful.

Also prints the time to execute each operation (including instantiating the store),
and with the `-verbose` option, the store statistics.

```
hadoop jar  cloudstore-0.1-SNAPSHOT.jar \
            filestatus  \
            s3a://guarded-table/example

2019-07-31 21:48:34,963 [main] INFO  commands.PrintStatus (DurationInfo.java:<init>(53)) - Starting: get path status
s3a://guarded-table/example	S3AFileStatus{path=s3a://guarded-table/example; isDirectory=false; length=0; replication=1; 
  blocksize=33554432; modification_time=1564602680000;
  access_time=0; owner=stevel; group=stevel;
  permission=rw-rw-rw-; isSymlink=false; hasAcl=false; isEncrypted=true; isErasureCoded=false}
  isEmptyDirectory=FALSE eTag=d41d8cd98f00b204e9800998ecf8427e versionId=null
2019-07-31 21:48:37,182 [main] INFO  commands.PrintStatus (DurationInfo.java:close(100)) - get path status: duration 0:02:221
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

