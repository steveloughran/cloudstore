<!---
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License. See accompanying LICENSE file.
-->

# Cloudstore

Cloudstore is a diagnostics library for troubleshooting problems
interacting with cloud storage through Apache Hadoop

*License*: Apache ASF 2.0

All the implementation classes are under the `org.apache.hadoop.fs` package
tree but it is not part of the apache hadoop artifacts.

1. Faster release cycle, so the diagnostics can evolve to track features going
   into Hadoop-trunk.
2. Fewer test requirements. This is naughty, but as much of this code is written
   in a hurry to diagnose problems on remote sites, problems which are hard to test,
   it is under-tested.
3. Ability to compile against older versions. We've currently switched to Hadoop 3.3+
   due to the need to make API calls and operations not in older versions.


## Features

### Primarily: diagnostics

Why? 

1. Sometimes things fail, and the first problem is classpath;
2. The second, invariably some client-side config. 
3. Then there's networking and permissions...
4. The Hadoop FS connectors all assume a well configured system, and don't
do much in terms of meaningful diagnostics.
5. This is compounded by the fact that we dare not log secret credentials.
6. And in support calls, it's all to easy to get those secrets, even
though its a major security breach to get them.

### Secondary: higher performance cloud IO

The main hadoop `hadoop fs` commands are written assuming a filesystem, where:

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


# Commands

## Common arguments

There are a set of arguments common to all commands
```
-D <key=value>          Define a property
-sysprops <file>        Java system properties to set
-tokenfile <file>       Hadoop token file to load
-verbose                Verbose output
-debug                  Extra debug logs (JVM and Log4j overrides)
-logoverrides <file>    A newline separated list of packages and classes for Log4j overrides
-xmlfile <file>         XML config file to load
```

### `-D <key=value>` Define a single Hadoop configuration option

Define a single hadoop option.
For defining multiple options, use `-xmlfile`

### `-sysprops <file>`: Java system properties to set

This loads a Java properties file containing java system
properties as key=value pairs. Each of these
sets the named java system property.

Blank lines and comment lines beginning with `#` are ignored.

### `-tokenfile -tokenfile <file>` : Load hadoop tokens

The `--tokenfile` option loads tokens saved with `hdfs fetchdt`. It does
not need Kerberos, though most filesystems expect Kerberos enabled for
them to pick up tokens (not S3A, potentially other stores).

### `-xmlfile <file>`: XML configuration file to load

This loads a hadoop configuration file and adds its values to
the Hadoop configuration used in the command.



## Command `storediag`

Examine store and print diagnostics, including testing read and optionally write
operations

See [storediag](src/main/site/storediag.md) for details.

## Command `auditlogs`

Parse AWS S3 Server logs into avro files, extracting http referrer-encoded
audit information where found.

See [auditlogs](src/main/site/auditlogs.md) for details.

## Command `bandwidth`

Measure upload/download bandwidth, optionally saving data to a CSV file.

See [bandwidth](src/main/site/bandwidth.md) for details.

## Command `bucketmetadata`

Retrieves metadata from an S3 Bucket (v2 SDK only) by probing the store.

See [bucketmetadata](src/main/site/bucketmetadata.md) for details.

## Command `bulkdelete`

Performs a bulk delete of files from a store, with higher performance
against some stores (S3).
Requires hadoop libraries with the 2024 bulk delete API (hadoop 3.4.1+).

See [bulkdelete](src/main/site/bulkdelete.md) for details.

## Command `cloudup` -upload and download files; optimised for cloud storage 

See [cloudup](src/main/site/cloudup.md)

## Command `committerinfo`

Tries to instantiate a committer using the Hadoop 3.1+ committer factory mechanism, printing out
what committer a specific path will create.

See [committerinfo](src/main/site/committerinfo.md).

## Command `constval`

Loads a class, resolves a constant/static final field and prints its value. 

See [constval](src/main/site/constval.md)


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
        -bfs            do a breadth first search of the path
        <path>
```

The `-verbose` option prints out more filesystem statistics, and of
the list iterators (useful if they publish statistics)

`-limit` puts a limit on the total number of files to scan; this
is useful when doing deep scans of buckets so as to put an upper bound
on the scan. Note, when used against S3 an ERROR may be printed in the AWS SDK.
This is harmless; it comes from the SDK thread pool being closed while
a list page prefetch is in progress.


##  Command `etag`

Prints the etag of an object, when implemented by the filesystem
and returned by the object store.

See [etag](src/main/site/etag.md)

##  Command `fetchdt`

This is an extension of `hdfs fetchdt` which collects delegation tokens
from a list of filesystems, saving them to a file.

See [fetchdt](src/main/site/fetchdt.md)

## Command `filestatus`

Calls `getFileStatus` on the listed paths, prints the values. For stores
which have more detail on the toString value of any subclass of `FileStatus`,
this can be more meaningful.

Also prints the time to execute each operation (including instantiating the store),
and with the `-verbose` option, the store statistics.

```
hadoop jar  cloudstore-1.1.jar \
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
hadoop jar cloudstore-1.1.jar gcscreds gs://bucket/

key uses \n for separator -gs connector must convert to line endings
2022-01-19 17:55:51,016 [main] INFO  gs.PemReader (PemReader.java:readNextSection(86)) - title match  at line 1
2022-01-19 17:55:51,020 [main] INFO  gs.PemReader (PemReader.java:readNextSection(88)) - scanning for end 
Parsed private key -entry length 28 lines
factory com.google.cloud.hadoop.repackaged.gcs.com.google.cloud.hadoop.util.CredentialFactory@d706f19
```

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
> bin/hadoop jar cloudstore-1.1.jar list -limit 10 s3a://landsat-pds/

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

## Command `localhost`

Print out localhost information from java APIs and then the hadoop network APIs.

## Command `locatefiles`

Use the mapreduce `LocatedFileStatusFetcher` to scan for all non-hidden
files under a path.


See [locatefiles](src/main/site/locatefiles.md)

## Command `mkcsv`

Creates a large CSV file designed to trigger/validate the ABFS prefetching bug which
came in HADOOP-17156/

See [mkcsv](src/main/site/mkcsv.md)

## Command `pathcapability`

Probes a filesystem for offering a specific named capability on the given path.

Requires a version of Hadoop with the `PathCapabilities` interface, which includes Hadoop 3.3 onwards.

```bash
bin/hadoop jar cloudstore-1.1.jar pathcapability
Usage: pathcapability [options] <capability> <path>
    -D <key=value> Define a property
    -tokenfile <file> Hadoop token file to load
    -verbose print verbose output
    -xmlfile <file> XML config file to load
```

```bash
hadoop jar cloudstore-1.1.jar pathcapability fs.s3a.capability.select.sql s3a://landsat-pds/

Using filesystem s3a://landsat-pds
Path s3a://landsat-pds/ has capability fs.s3a.capability.select.sql
```

The exit code of the command is 0 if the capability is present, -1 if absent, and 55 if the hadoop version does not support the API.
 Approximate HTTP equivalent: `505: Version Not Supported`. 
 
As it is in Hadoop 3.3, all APIs new to that release (including `openFile()`) can absolutely be probed for. Otherwise, the 55 response may mean "an API is implemented, just not the probe". 

## Command `put`

Uploads/copies a file, with the ability to set `createFile()` parameters.

See [put](src/main/site/put.md)

## Command `safeprefetch`

Probes an abfs store for being vulnerable to
prefetch data corruption, providing the configuration
information to disable it if so.

See [safeprefetch](src/main/site/safeprefetch.md)

## Command `tarhardened`

Verify the hadoop release has had its untar command hardened and will
not evaluate commands passed in as filenames.


See [tarhardened](src/main/site/tarhardened.md)

## Command `tlsinfo`

Print out TLS information including X509 certificates.

See [tlsinfo](src/main/site/tlsinfo.md)

## AWS SDK commands

See [S3 operations through the AWS V2 SDK](src/main/site/sdk.md).

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


See [BUILDING](BUILDING.md)