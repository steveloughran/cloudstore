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

## S3 Versioned Object support

AWS S3 supports [object versioning](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html),
where old copies of objects are retained after being overwritten or deleted.

The versioned object commands use AWS SDK operations not directly accessible
through the Hadoop Filesystem APIs to view and retrieve versioned
objects.

Points to note
* This is lower level and a bit brittle.
* S3A Auditing is bypassed.
* If custom S3A signers are used, they need to able to handle S3 Requests (ListVersions) which
  are not normally used.
* The caller must be in an IAM Role with the permissions to invoke this, and for `undelete` to be able
  to delete versioned objects.

# Command `listversions`

Lists all versions of files under a path.

## Usage

```
hadoop jar cloudstore-1.1.jar listversions
Usage: listversions <path>
        -D <key=value>  Define a property
        -deleted        include delete markers
        -dirs   include directory markers
        -limit <limit>  limit of files to list
        -out <file>     output file
        -q      quiet output
        -separator <string>     Separator if not <tab>
        -age <seconds>  Only include versions created in this time interval
        -since <epoch-time>     Only include versions after this time
        -tokenfile <file>       Hadoop token file to load
        -verbose        print verbose output
        -xmlfile <file> XML config file to load


```

## list to a file

```bash
hadoop jar cloudstore-1.1.jar listversions -out out.tsv -limit 10  s3a://stevel-london/
```

Exports a version listing as a tab separated file. Any hadoop filesystem URI is supported as a destination.

```csv
"index"	"key"	"path"	"restore"	"latest"	"size"	"tombstone"	"directory"	"date"	"timestamp"	"version"	"etag"
1	"FileSystemContractBaseTest"	"s3a://stevel-london/FileSystemContractBaseTest"	0	1	0	1	0	"2023-03-01+000001:58:03"	1677679083000	"9y71EYQombOjkq3I1143NF7146NKx7cU"	""
2	"FileSystemContractBaseTest"	"s3a://stevel-london/FileSystemContractBaseTest"	1	0	2048	0	0	"2023-03-01+000001:58:02"	1677679082000	"zn8c734o2CaH2ZfsS.I7_mpDl6kgh63O"	"54cf2dd2eb4a8a5f157afb44dee11761"
3	"FileSystemContractBaseTest"	"s3a://stevel-london/FileSystemContractBaseTest"	0	0	0	1	0	"2023-03-01+000001:57:38"	1677679058000	"NE6GmW4NOL_flfn3DZCDjw0j9RuqPGER"	""
4	"FileSystemContractBaseTest"	"s3a://stevel-london/FileSystemContractBaseTest"	1	0	2048	0	0	"2023-03-01+000001:57:38"	1677679058000	"_iQNRagGNEXrjotZ4LT84GgpBIVsJklY"	"0959a0ff20f313b8ddc81a525ca2c3c3"
5	"ITestS3AFailureHandling/missingFile"	"s3a://stevel-london/ITestS3AFailureHandling/missingFile"	0	1	0	1	0	"2023-03-01+000001:56:08"	1677678968000	"BMRq06R30ePuCl8K_QWh634WsI8DO9Zq"	""
6	"ITestS3AFailureHandling/missingFile"	"s3a://stevel-london/ITestS3AFailureHandling/missingFile"	0	0	0	1	0	"2023-03-01+000001:56:08"	1677678968000	"8ceq9Uda5myt7.puCcJY000AW_77eMXq"	""
7	"Users/stevel/Projects/hadoop-trunk/hadoop-tools/hadoop-aws/target/test-dir/4/"	"s3a://stevel-london/Users/stevel/Projects/hadoop-trunk/hadoop-tools/hadoop-aws/target/test-dir/4"	0	1	0	1	1	"2023-03-01+000001:59:30"	1677679170000	"aoyBEEYq2W4.MthXEhh4dzgAhnWaWQhE"	""
8	"Users/stevel/Projects/hadoop-trunk/hadoop-tools/hadoop-aws/target/test-dir/4/"	"s3a://stevel-london/Users/stevel/Projects/hadoop-trunk/hadoop-tools/hadoop-aws/target/test-dir/4"	0	0	0	0	1	"2023-03-01+000001:55:26"	1677678926000	"t0jV3f3h0HnZ0muKZ3bcahSlZ71kERC5"	"be721d84a0fc359d66e0370e0ec76193"
9	"Users/stevel/Projects/hadoop-trunk/hadoop-tools/hadoop-aws/target/test-dir/4/0eKEU4DVOz/test/"	"s3a://stevel-london/Users/stevel/Projects/hadoop-trunk/hadoop-tools/hadoop-aws/target/test-dir/4/0eKEU4DVOz/test"	0	1	0	1	1	"2023-03-01+000001:56:37"	1677678997000	"nWSgpqtidc0euYUIXd7G0vkvCRMdg3k9"	""
10	"Users/stevel/Projects/hadoop-trunk/hadoop-tools/hadoop-aws/target/test-dir/4/0eKEU4DVOz/test/"	"s3a://stevel-london/Users/stevel/Projects/hadoop-trunk/hadoop-tools/hadoop-aws/target/test-dir/4/0eKEU4DVOz/test"	0	0	0	0	1	"2023-03-01+000001:56:36"	1677678996000	"OFlnJeZlT9DgkQ6VxiOCHm46iD5Nyn7O"	"8338c064e7ad08dd583e4090f8435f48"
```

## Table column meaning

| **column** | **type** | **meaning** |
|-----|------|---------|
| `index` | `long` | index in table |
| `key` | `string` | bucket key |
| `path` | `URI` | Hadoop Path |
| `restore` | `boolean` |  restore this? |
| `latest` | `boolean` | is this the latest? |
| `tombstone` | `boolean` | tombstone marker? |
| `directory` | `boolean` | directory marker? |
| `date` | `string` | date |
| `timestamp` | `long` | epoch timestamp  |
| `version` | `string` | version reference or "" for unversioned file |
| `etag` | `string` | etag or "" for tombstones |

The `restore` column is a cue for restoring an object; it is set to true for hidden keys which aren't markers or tombstones

## Summarizing a bucket

The `-q` option scans a bucket but only prints the summary

```

hadoop jar cloudstore-1.1.jar listversions -q  s3a://stevel-london/
2023-03-01 17:37:43,931 [main] INFO  extra.ListVersions (StoreDurationInfo.java:<init>(56)) - Starting: listversions
2023-03-01 17:37:44,622 [main] WARN  s3a.S3AFileSystem (S3AFileSystem.java:getAmazonS3ClientForTesting(1221)) - Access to S3A client requested, reason listversions

Listing s3a://stevel-london/
============================


Found 9,594 objects under s3a://stevel-london/ with total size 23,720,367 bytes
Hidden file count 2,451 with hidden data size 23,720,367 bytes
Hidden zero-byte file count 424
Hidden directory markers 2,123
Tombstone entries 5,017 comprising 2,463 files and 2,554 dir markers
2023-03-01 17:37:47,663 [main] INFO  extra.ListVersions (StoreDurationInfo.java:close(115)) - Duration of listversions: 0:03:733
```

This summary shows some aspects of a bucket only used for `hadoop-aws` test runs, where all data is cleaned
up after each test case. This means that there is at least one tombstone per file created during
a test run.

* A bucket may be empty yet actually contain billed data -here 23MB.
* Directory markers tombstones may be a large fraction of the data.
  This is because the S3 client will issue "blind" DELETE requests for
  compatibility with older releases whenever files are created/renamed underneath a path.
  Disable this by setting `fs.s3a.directory.marker.retention` to `keep`.
  This option is faster to write files on an unversioned bucket and significantly improves listing performance
  on versioned buckets.

## Restoring a whole list

Taking a TSV file and restoring all those files with restore=1 to a different location is yet to be implemented.


## `restore` command

A single file can be restored using the `restore` command, which will take a file and version
and create a new copy in a different location in the same bucket.

```bash
> hadoop jar cloudstore-1.1.jar restore s3a://stevel-london/FileSystemContractBaseTest "zn8c734o2CaH2ZfsS.I7_mpDl6kgh63O" /restored.txt

restoring s3a://stevel-london/FileSystemContractBaseTest @ zn8c734o2CaH2ZfsS.I7_mpDl6kgh63O to s3a://stevel-london/restored.txt
Starting: restore
Duration of restore: 0:00:715
Restored object of size 2,048 bytes to s3a://stevel-london/restored.txt
```

## `undelete` command

"undeletes" S3 objects by removing directory tombstones from a bucket path.

By undeleting all tombstones added since a specific date, all objects deleted after that
point in time will become visible.

Usage
```
 undelete 
        -D <key=value>  Define a property
        -limit <limit>  limit of files to list
        -age <seconds>  Only include versions created in this time interval
        -since <epoch-time>     Only include versions after this time
        -tokenfile <file>       Hadoop token file to load
        -xmlfile <file> XML config file to load
```

### Example

Preparation: create then delete a file 

```bash
# create a file
> hadoop fs -touch s3a://stevel-london/undelete.txt

# delete it
> bin/hadoop fs -rm  s3a://stevel-london/undelete.txt
Deleted s3a://stevel-london/undelete.txt

# list versions including tombstones
bin/hadoop jar cloudstore-1.1.jar listversions -deleted s3a://stevel-london/undelete.txt
Starting: listversions
2023-03-03 15:28:16,552 [main] WARN  s3a.S3AFileSystem (S3AFileSystem.java:getAmazonS3ClientForTesting(1216)) - Access to S3A client requested, reason listversions

Processing s3a://stevel-london/undelete.txt
===========================================

"index" "key"   "path"  "restore"       "latest"        "size"  "tombstone"     "directory"     "date"  "timestamp"     "version"       "etag"
1       "undelete.txt"  "s3a://stevel-london/undelete.txt"      0       1       0       1       0       "2023-03-03+000003:27:12"       1677857232000   ".KZMHdxWduHoSRHYVhN2NhjW7ar2VVPZ"      ""
2       "undelete.txt"  "s3a://stevel-london/undelete.txt"      1       0       0       0       0       "2023-03-03+000003:26:29"       1677857189000   "ZJMKH.5PrEwiy9z1HbDqPHOaTxhUkhxC"      "e7331e043593313055a187f255354442"

Found 2 objects under s3a://stevel-london/undelete.txt with total size 0 bytes
Hidden file count 1 with hidden data size 0 bytes
Hidden zero-byte file count 1
Hidden directory markers 0
Tombstone entries 1 comprising 1 files and 0 dir markers

Duration of listversions: 0:00:648
```

The object `s3a://stevel-london/undelete.txt` at version "ZJMKH.5PrEwiy9z1HbDqPHOaTxhUkhxC" is not visible as there is a tombstone above it.

Calling the undelete command will remove the tombstone

```bash
hadoop jar cloudstore-1.1.jar undelete s3a://stevel-london/undelete.txt

2023-03-03 15:30:59,755 [main] WARN  s3a.S3AFileSystem (S3AFileSystem.java:getAmazonS3ClientForTesting(1216)) - Access to S3A client requested, reason undelete
Starting: undelete
2023-03-03 15:30:59,757 [main] WARN  s3a.S3AFileSystem (S3AFileSystem.java:getAmazonS3ClientForTesting(1216)) - Access to S3A client requested, reason undelete

Processing s3a://stevel-london/undelete.txt
===========================================

s3a://stevel-london/undelete.txt @ .KZMHdxWduHoSRHYVhN2NhjW7ar2VVPZ
Starting: deleting 1 tombstones
Duration of deleting 1 tombstones: 0:00:100

Removed 1 tombstones

Duration of undelete: 0:00:744
```

which can be verified

```bash
> hadoop fs -ls  s3a://stevel-london/undelete.txt

-rw-rw-rw-   1 stevel stevel          0 2023-03-03 15:26 s3a://stevel-london/undelete.txt


```

The `-since` and `-age` options can 

* `-since` a time in seconds since 1970-01-01 which all tombstones
must have been created on or after (hint: there are web pages to calculate this)
  
* `-age` a time in seconds; all tombstones created up to that many seconds before the invocation
  of the command. 

```bash
> hadoop jar cloudstore-1.1.jar undelete -age 24000 -limit 10 s3a://stevel-london/

2023-03-03 15:39:18,964 [main] WARN  s3a.S3AFileSystem (S3AFileSystem.java:getAmazonS3ClientForTesting(1216)) - Access to S3A client requested, reason undelete
Starting: undelete
Skipping entries older than 2023-03-03T08:59:18Z
2023-03-03 15:39:18,971 [main] WARN  s3a.S3AFileSystem (S3AFileSystem.java:getAmazonS3ClientForTesting(1216)) - Access to S3A client requested, reason undelete

Processing s3a://stevel-london/
===============================

s3a://stevel-london/fork-0002/test/select.dat @ JqxWt1daEIqFJx7G0ifQjV93Oblb0YHa
s3a://stevel-london/fork-0002/test/select.dat @ CCSW8wU9mCu5iazVDhTxFiCYTrC3GjFL
s3a://stevel-london/fork-0002/test/select.dat @ 3.Vtzm6Z3ph6FkNqphp_jpEyRok5GvDA
s3a://stevel-london/fork-0002/test/select.dat @ KuDfH6Ml62QeNgfGmaizP5146lDX8zs_
s3a://stevel-london/fork-0002/test/select.dat @ VKmq3L0DCfLTKIhghk1N_o6CNUeL4Enj
s3a://stevel-london/fork-0002/test/select1.dat @ 4ysnIFMiPLBrwM8sQ7K7XR6IOv2BDvQ8
s3a://stevel-london/fork-0002/test/select1.dat @ gQFSM65vjLTITX6PRj8FBIg7sQc6VZZ6
s3a://stevel-london/fork-0002/test/select1.dat @ yC2xiuOaJ5YM_sfWNx0uwUblSlO39Z0P
s3a://stevel-london/fork-0002/test/select1.dat @ sCNAxSVqNYWYsSBIx3LKl3OiURvtYFOX
s3a://stevel-london/fork-0002/test/select1.dat @ IeIcnBOAkppxOth5NFkjnECL4pV_YDNe
Starting: deleting 10 tombstones
Duration of deleting 10 tombstones: 0:00:355

Removed 10 tombstones

Duration of undelete: 0:01:968
```

Note that there may be multiple tombstones one on top of each other *without any intervening file*.
All these tombstones need to be removed before the file becomes visible.
Deleting tombstones *older* than the most recent file does not affect the
visibility of the file.
