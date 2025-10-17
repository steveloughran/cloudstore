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

# Command `etag`

Retrieves the etag from an object whose filestore implementation provides this information.

This is only supported on Hadoop releases with [HADOOP-17979](https://issues.apache.org/jira/browse/HADOOP-17979)
_Interface `EtagSource` to allow `FileStatus` subclasses to provide etags_
and it requires the filesystem implementation to implement this interface on the `FileStatus`
instances they return from a `getFileStatus()` call.

In ASF Hadoop, it is available on 3.3.2+ for S3A and ABFS stores.

## S3 Examples
### S3 Standard

Zero byte file, S3 standard, no default encryption.

```
> bin/hadoop jar cloudstore-1.1.jar etag s3a://stevel-london/file

2025-10-21 12:40:02,204 [main] INFO  commands.EtagCommand (StoreDurationInfo.java:<init>(91)) - Starting: get path status for s3a://stevel-london/file
2025-10-21 12:40:02,580 [main] INFO  commands.EtagCommand (StoreDurationInfo.java:close(200)) - Duration of get path status for s3a://stevel-london/file: 00:00:00.378
Etag of s3a://stevel-london/file = "d41d8cd98f00b204e9800998ecf8427e"
```


### Example: S3 Express

```
> bin/hadoop jar cloudstore-1.1.jar etag s3a://stevel--usw2-az1--x-s3/file

2025-10-21 12:28:22,878 [main] INFO  commands.EtagCommand (StoreDurationInfo.java:<init>(91)) - Starting: get path status for s3a://stevel--usw2-az1--x-s3/file
2025-10-21 12:28:23,863 [main] INFO  commands.EtagCommand (StoreDurationInfo.java:close(200)) - Duration of get path status for s3a://stevel--usw2-az1--x-s3/file: 00:00:00.986
Etag of s3a://stevel--usw2-az1--x-s3/file = "3708fc5526564ad19b2fe536b45a6fe1"
```

S3 Express always encrypts data, which is why it has a different checksum.

### Third Party S3 stores

The result of the probe against third party stores is undefined, and will vary with the store.

Here is an example response from a probe of a Dell ECS store:

```
> bin/hadoop jar cloudstore-1.1.jar etag s3a://ecsstore/file

2025-10-21 12:18:54,817 [main] INFO  commands.EtagCommand (StoreDurationInfo.java:<init>(91)) - Starting: get path status for s3a://ecsstore/file
2025-10-21 12:18:55,523 [main] INFO  commands.EtagCommand (StoreDurationInfo.java:close(200)) - Duration of get path status for s3a://ecsstore/file: 00:00:00.707
Etag of s3a://ecsstore/file = "d41d8cd98f00b204e9800998ecf8427e"
```

Note that this is the same checksum as for the S3 Standard bucket.

### S3 Stores and a directory path

Although leaf directories without children are mimicked by objects with a trailing /,
the etag returned is `null`, which is considered an error."


```
> bin/hadoop fs -mkdir -p s3a://stevel-london/dir/subdir

> bin/hadoop jar cloudstore-1.1.jar etag s3a://stevel-london/dir/subdir

2025-10-21 13:00:59,697 [main] INFO  commands.EtagCommand (StoreDurationInfo.java:<init>(91)) - Starting: get path status for s3a://stevel-london/dir/subdir
2025-10-21 13:01:00,160 [main] INFO  commands.EtagCommand (StoreDurationInfo.java:close(200)) - Duration of get path status for s3a://stevel-london/dir/subdir: 00:00:00.464
Etag of s3a://stevel-london/dir/subdir = null
File status of path s3a://stevel-london/dir/subdir is an EtagSource but the value is null:
S3AFileStatus{path=s3a://stevel-london/dir/subdir; isDirectory=true; modification_time=0; access_time=0; owner=stevel; group=stevel; permission=rwxrwxrwx; isSymlink=false; hasAcl=false; isEncrypted=true; isErasureCoded=false} isEmptyDirectory=FALSE eTag=null versionId=null

2025-10-21 13:01:00,161 [main] INFO  util.ExitUtil (ExitUtil.java:terminate(248)) - Exiting with status 53: Etag is null
```

This holds for parent directories too, which may or may not have a directory marker object.

## Azure ABFS

```
> bin/hadoop jar cloudstore-1.1.jar etag abfs://stevel-testing@stevelwales.dfs.core.windows.net/file

2025-10-21 12:42:45,257 [main] INFO  commands.EtagCommand (StoreDurationInfo.java:<init>(91)) - Starting: get path status for abfs://stevel-testing@stevelwales.dfs.core.windows.net/file
2025-10-21 12:42:45,456 [main] INFO  commands.EtagCommand (StoreDurationInfo.java:close(200)) - Duration of get path status for abfs://stevel-testing@stevelwales.dfs.core.windows.net/file: 00:00:00.200
Etag of abfs://stevel-testing@stevelwales.dfs.core.windows.net/file = 0x8DE1096EF738A3D
```

## Local FileSystem

```
> bin/hadoop jar cloudstore-1.1.jar etag file

2025-10-21 12:46:30,236 [main] INFO  commands.EtagCommand (StoreDurationInfo.java:<init>(91)) - Starting: get path status for file
2025-10-21 12:46:30,240 [main] INFO  commands.EtagCommand (StoreDurationInfo.java:close(200)) - Duration of get path status for file: 00:00:00.005
File status of path file is not an EtagSource:
DeprecatedRawLocalFileStatus{path=file:/Users/stevel/Projects/Releases/hadoop-3.5.0-SNAPSHOT/file; isDirectory=false; length=0; replication=1; blocksize=33554432; modification_time=1761047017929; access_time=1761047017000; owner=; group=; permission=rw-rw-rw-; isSymlink=false; hasAcl=false; isEncrypted=false; isErasureCoded=false}

2025-10-21 12:46:30,241 [main] INFO  util.ExitUtil (ExitUtil.java:terminate(248)) - Exiting with status 51: Filesystem does not provide Etag information
```

Error code 51: Unimplemented.

And a probe of a nonexistent file
```
> bin/hadoop jar cloudstore-1.1.jar etag file2

2025-10-21 12:51:19,515 [main] INFO  commands.EtagCommand (StoreDurationInfo.java:<init>(91)) - Starting: get path status for file2
2025-10-21 12:51:19,515 [main] INFO  commands.EtagCommand (StoreDurationInfo.java:close(200)) - Duration of get path status for file2: 00:00:00.001
2025-10-21 12:51:19,516 [main] INFO  util.ExitUtil (ExitUtil.java:terminate(248)) - Exiting with status 44: Not found: file2
```

Exit code 44, "404", Not found.

## Exit codes

| Exit code | Meaning                                            |
|-----------|----------------------------------------------------|
| 0         | Successfully retrieved etag from path              |
| 44        | File Not Found                                     |
| 51        | Unimplemented: the store doesn't implement the API |
| 53        | Unavailable: a null or empty etag was returned     |
| -1        | Generic failure                                    |