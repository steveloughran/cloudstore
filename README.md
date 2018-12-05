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

Example

```bash
> hadoop jar cloudstore-0.1-SNAPSHOT.jar fetchdt -p -r file:/tmm/secrets.bin file:///

Collecting tokens for 1 filesystem to to file:/tmm/secrets.bin
2018-12-05 17:47:00,970 INFO fs.FetchTokens: Starting: Fetching token for file:/
No token for file:/
2018-12-05 17:47:00,972 INFO fs.FetchTokens: Fetching token for file:/: duration 0:00:003
2018-12-05 17:47:00,973 INFO util.ExitUtil: Exiting with status 44: No token issued by filesystem file:///
```
