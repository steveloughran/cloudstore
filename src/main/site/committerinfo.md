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

# Command `committerinfo`

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

