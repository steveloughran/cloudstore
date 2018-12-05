# cloudstore

This is going to be for a general cloudstore CLI command for Hadoop.

Initally its a diagnostics entry point, designed to work with Hadoop 2.8+

Why? 

1. Sometimes things fail, and the first problem is classpath;
1. The second, invariably some client-side config. 
1. Then there's networking and permissions...
1. The Hadoop FS connectors all assume a well configured system, and don't
do much in terms of meaningful diagnostics.
1. This is compounded by the fact that we dare not log secret credentials.
1. And in support calls, it's all to easy to get those secrets, even
though its a major security breach to get them.

## StoreDiag

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
-tokenfile <file>  Hadoop token file to load
-r   Readonly filesystem: do not attempt writes
-t    Require delegation tokens to be issued
-j    List the JARs
-5    Print MD5 checksums of the jars listed (requires -j)
```

## fetchdt

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

Examples

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

