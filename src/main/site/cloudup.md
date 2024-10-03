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


# Command `cloudup` -upload and download files; optimized for cloud storage 

This utility is designed to optimise upload/download between
cloud storage and local or cluster filesystems.

It is a single process, multithreaded program, which assumes that
the bandwidth to the cloud infrastructure is limited enough that a single
process can use a lot of the bandwidth given the opportunity. 
That is: this program is efficient enough that distcp would only
be faster if the cloud store bandwidth was many hundreds of megabits.

When uploading over long haul links, or via VPNs, the remote bandwidth
is often limited to the extent that distcp doesn't deliver speedup
-only complexity.

And unlike distcp, it can read from/write to the local filesystem,
as well as HDFS.

Finally, it can copy between cloud stores.
This is generally more efficient if initiated within one of the cloud infrastructures,
both in cost of data downloaded and throughput.

### Usage

```
Usage: cloudup [options] <source> <dest>
        -block <size>   block size in megabytes
        -D <key=value>  Define a property
        -flush  flush the output after writing each block
        -hflush hflush() the output after writing each block
        -ignore ignore errors
        -largest <largest>      number of large files to upload first
        -overwrite      overwrite files
        -threads <threads>      number of worker threads
        -tokenfile <file>       Hadoop token file to load
        -update only copy up new or more recent files
        -verbose        print verbose output
        -xmlfile <file> XML config file to load
```

### Algorithm

1. Source files are listed (deep listing of the source path) to build a list of files to upload.
2. A pool of worker threads is created.
3. The largest N files are queued for upload first, where N is a default or the value set by `-largest`.
4. The remainder of the files are randomized to avoid throttling and then queued.
5. The files are queued for upload in the worker pool.
6. For incremental uploads, the destination is probed: if a file of the same size exists which is newer than the source file, it is not uploaded.
7. Otherwise, the source file is read in blocks of "block size"; the block is then uploaded.
8. Optionally, the output stream can have `flush()` or `hflush()` called after writing each
   block. This is not recommended as it may slow down the operation; the options are there mainly
   to measure the performance impact.
9. The program waits for all uploads to complete.
10. Summary statistics printed.

This is not `distcp` run across a cluster; it's a single process with some threads. 
It is very efficient for small files.

Current Limitations
* Source directory tree scanning is single-threaded. It does use the deep recursive list which is
  optimal on S3 storage.
* No rate limiting on download or upload bandwidth.
* No attempt to retry on a failed upload.
* IOStatistics are not collected and reported, because of the desire to support older hadoop releases.

All those limits could be addressed, along with some other optional features
* Drive the upload from a supplied listing file
* Create CSV output summary files.
* Reflection based IOStatistics support.


Download logs from `s3a://bucket/qelogs` and save to the local (relative) path `localquelogs`
```bash
hadoop jar cloudstore-1.0.jar cloudup \
 -threads 32 -update s3a://bucket/qelogs/  localqelogs
```

And the other way, only updating new files, and uploading the largest 16 files first.
Use a block size of 16 MB.

```bash
hadoop jar cloudstore-1.0.jar cloudup \
 -threads 32 -update -largest 16 -block 8 localqelogs s3a://bucket/qelogs/  
```

From HDFS to google GCS. All data is downloaded from HDFS and then uploaded to GCS block by block;
there is no buffering to local disk.

```bash
 hadoop jar cloudstore-1.0.jar  cloudup -threads 60 -largest 16 -update -hflush -verbose \
   hdfs://tmp/share/hadoop/common  gs://stevel-london/share/common 
```

Then download to the local fs

```
hadoop jar cloudstore-1.0.jar  cloudup -threads 60 -largest 16 -update -block 32  gs://stevel-london/share/common ./scratch/gcs

...

File copies attempted: 149; size 85,877,554 bytes
Files skipped: 0, size 0 bytes
Listing duration: (HH:MM:ss) : 0:00:01.247
Copy duration: (HH:MM:ss) : 0:00:56.233
Effective bandwidth 1.456 MiB/s, 11.652 Megabits/s
Seconds per file: 0.377s


```

## upload from local to S3A

```bash

hadoop jar cloudstore-1.0.jar cloudup -threads 60 -largest 32 -block 64 -update share/hadoop/common  s3a://stevel-london/share/common

Summary of copy from file:/Users/stevel/Projects/Releases/cdp-7.1.9/share/hadoop/common to s3a://stevel-london/share/common
===========================================================================================================================

File copies attempted: 149; size 85,877,554 bytes
Files skipped: 0, size 0 bytes

Listing duration: (HH:MM:ss) : 0:00:01.635
Copy duration: (HH:MM:ss) : 0:00:27.723

Effective bandwidth 2.954 MiB/s, 23.635 Megabits/s
Seconds per file: 0.186s


2023-09-26 17:06:15,385 [shutdown-hook-0] INFO  statistics.IOStatisticsLogging (IOStatisticsLogging.java:logIOStatisticsAtLevel(269)) - IOStatistics: counters=((action_http_head_request=2)
(audit_request_execution=302)
(audit_span_creation=152)
(files_created=149)
(object_list_request=151)
(object_metadata_request=2)
(object_put_bytes=85877554)
(object_put_request=149)
(object_put_request_completed=149)
(op_create=149)
(op_get_file_status=2)
(op_get_file_status.failures=2)
(store_io_request=304)
(stream_write_block_uploads=149)
(stream_write_bytes=85877554)
(stream_write_total_data=176129636));

gauges=((stream_write_block_uploads_pending=149));

minimums=((action_http_head_request.min=36)
(object_list_request.min=35)
(object_put_request.min=127)
(op_create.min=41)
(op_get_file_status.failures.min=71));

maximums=((action_http_head_request.max=315)
(object_list_request.max=1349)
(object_put_request.max=27117)
(op_create.max=1350)
(op_get_file_status.failures.max=390));

means=((action_http_head_request.mean=(samples=2, sum=351, mean=175.5000))
(object_list_request.mean=(samples=151, sum=42419, mean=280.9205))
(object_put_request.mean=(samples=149, sum=932881, mean=6260.9463))
(op_create.mean=(samples=149, sum=42412, mean=284.6443))
(op_get_file_status.failures.mean=(samples=2, sum=461, mean=230.5000)));

```

This upload was executed against S3A with the store configured to log its IOStatistics when shutdown;
recent versions of the S3A and ABFS connectors do this -it can be enabled for all applications by
editing the `core-site.xml` settings of your deployment

```xml
  <property>
    <name>fs.iostatistics.logging.level</name>
    <value>info</value>
  </property>
```

```bash
hadoop jar cloudstore-1.0.jar cloudup -threads 60 -largest 32 -block 64 -update share/hadoop/common  s3a://stevel-london/share/common

Copying from file:/Users/stevel/Projects/Releases/cdp-7.1.9/share/hadoop/common to s3a://stevel-london/share/common; threads=60; large files=32; block size=67108864n; overwrite=false; update=true verbose=false; ignore failures=false
Listing source files under file:/Users/stevel/Projects/Releases/cdp-7.1.9/share/hadoop/common
2023-09-26 17:07:31,573 [main] DEBUG store.StoreEntryPoint (StoreEntryPoint.java:debug(235)) - Destination prepared: s3a://stevel-london/share/common
Files to copy = 149; preparation  = 0:00:01.538

[01]: size = 4,675,711 bytes: file:/Users/stevel/Projects/Releases/cdp-7.1.9/share/hadoop/common/lib/gcs-connector-2.1.2.7.1.9.0-SNAPSHOT-shaded.jar
[pool-4-thread-3] [0001] Copying file:/Users/stevel/Projects/Releases/cdp-7.1.9/share/hadoop/common/lib/gcs-connector-2.1.2.7.1.9.0-SNAPSHOT-shaded.jar to s3a://stevel-london/share/common/lib/gcs-connector-2.1.2.7.1.9.0-SNAPSHOT-shaded.jar (size: 4,675,711 bytes)
[02]: size = 4,448,398 bytes: file:/Users/stevel/Projects/Releases/cdp-7.1.9/share/hadoop/common/hadoop-common-3.1.1.7.1.9.0-SNAPSHOT.jar
[pool-4-thread-4] [0002] Copying file:/Users/stevel/Projects/Releases/cdp-7.1.9/share/hadoop/common/hadoop-common-3.1.1.7.1.9.0-SNAPSHOT.jar to s3a://stevel-london/share/common/hadoop-common-3.1.1.7.1.9.0-SNAPSHOT.jar (size: 4,448,398 bytes)
[pool-4-thread-5] [0003] Copying file:/Users/stevel/Projects/Releases/cdp-7.1.9/share/hadoop/common/lib/curator-client-5.4.0.7.1.9.0-SNAPSHOT.jar to s3a://stevel-london/share/common/lib/curator-client-5.4.0.7.1.9.0-SNAPSHOT.jar (size: 3,276,219 bytes)
[03]: size = 3,276,219 bytes: file:/Users/stevel/Projects/Releases/cdp-7.1.9/share/hadoop/common/lib/curator-client-5.4.0.7.1.9.0-SNAPSHOT.jar
...
[pool-4-thread-60] [0138] Skipped copy of file:/Users/stevel/Projects/Releases/cdp-7.1.9/share/hadoop/common/lib/jetty-security-9.4.48.v20220622.jar to s3a://stevel-london/share/common/lib/jetty-security-9.4.48.v20220622.jar  (size: 118,511 bytes) in 2023-09-26 17:07:33,616 [pool-4-thread-1] DEBUG store.StoreEntryPoint (StoreEntryPoint.java:debug(235)) - Skipping copy of %s to %s
0:00:00.152s
[pool-4-thread-1] [0137] Skipped copy of file:/Users/stevel/Projects/Releases/cdp-7.1.9/share/hadoop/common/lib/netty-buffer-4.1.86.Final.jar to s3a://stevel-london/share/common/lib/netty-buffer-4.1.86.Final.jar  (size: 305,047 bytes) in 0:00:00.152s

Summary of copy from file:/Users/stevel/Projects/Releases/cdp-7.1.9/share/hadoop/common to s3a://stevel-london/share/common
===========================================================================================================================

File copies attempted: 149; size 85,877,554 bytes
Files skipped: 149, size 85,877,554 bytes

Listing duration: (HH:MM:ss) : 0:00:01.538
Copy duration: (HH:MM:ss) : 0:00:00.655

No files copied



2023-09-26 17:07:33,693 [shutdown-hook-0] INFO  statistics.IOStatisticsLogging (IOStatisticsLogging.java:logIOStatisticsAtLevel(269)) - IOStatistics: counters=((action_http_head_request=151)
(audit_request_execution=153)
(audit_span_creation=152)
(object_list_request=2)
(object_metadata_request=151)
(op_get_file_status=151)
(store_io_request=153));

gauges=();

minimums=((action_http_head_request.min=34)
(object_list_request.min=68)
(op_get_file_status.min=35));

maximums=((action_http_head_request.max=519)
(object_list_request.max=87)
(op_get_file_status.max=520));

means=((action_http_head_request.mean=(samples=151, sum=28493, mean=188.6954))
(object_list_request.mean=(samples=2, sum=155, mean=77.5000))
(op_get_file_status.mean=(samples=151, sum=28664, mean=189.8278)));


```

As this was an `-update` call, every destination file was probed to see if it existed and what its size and timestamp was.

Here, no copies took place at all. The IOStatistics report 151 HEAD requests against s3, a mean response time of 188 milliseconds.

And download it
```bash
hadoop jar cloudstore-1.0.jar cloudup -threads 60 -largest 16 -update -block 32 s3a://stevel-london/share/common ./scratch/s3a

Copying from s3a://stevel-london/share/common to file:/Users/stevel/Projects/Releases/cdp-7.1.9/scratch/s3a; threads=60; large files=16; block size=33554432n; overwrite=false; update=true verbose=false; ignore failures=false
Listing source files under s3a://stevel-london/share/common
2023-09-26 17:12:40,655 [main] DEBUG store.StoreEntryPoint (StoreEntryPoint.java:debug(235)) - Destination prepared: file:/Users/stevel/Projects/Releases/cdp-7.1.9/scratch/s3a
Files to copy = 149; preparation  = 0:00:00.123

...

Summary of copy from s3a://stevel-london/share/common to file:/Users/stevel/Projects/Releases/cdp-7.1.9/scratch/s3a
===================================================================================================================

File copies attempted: 149; size 85,877,554 bytes
Files skipped: 0, size 0 bytes

Listing duration: (HH:MM:ss) : 0:00:00.123
Copy duration: (HH:MM:ss) : 0:00:12.896

Effective bandwidth 6.351 MiB/s, 50.809 Megabits/s
Seconds per file: 0.087s

2023-09-26 17:12:53,727 [shutdown-hook-0] INFO  statistics.IOStatisticsLogging (IOStatisticsLogging.java:logIOStatisticsAtLevel(269)) - IOStatistics: counters=((action_http_get_request=149)
(action_http_head_request=1)
(audit_request_execution=152)
(audit_span_creation=152)
(object_list_request=2)
(object_metadata_request=1)
(op_get_file_status=1)
(op_list_files=1)
(op_open=149)
(store_io_request=152)
(stream_read_bytes=85877554)
(stream_read_close_operations=149)
(stream_read_closed=149)
(stream_read_opened=149)
(stream_read_operations=9923)
(stream_read_operations_incomplete=9774)
(stream_read_seek_policy_changed=149)
(stream_read_total_bytes=85877554));

gauges=();

minimums=((action_http_get_request.min=359)
(action_http_head_request.min=329)
(object_list_request.min=62)
(op_get_file_status.min=398)
(op_list_files.min=116));

maximums=((action_http_get_request.max=2132)
(action_http_head_request.max=329)
(object_list_request.max=101)
(op_get_file_status.max=398)
(op_list_files.max=116));

means=((action_http_get_request.mean=(samples=149, sum=54812, mean=367.8658))
(action_http_head_request.mean=(samples=1, sum=329, mean=329.0000))
(object_list_request.mean=(samples=2, sum=163, mean=81.5000))
(op_get_file_status.mean=(samples=1, sum=398, mean=398.0000))
(op_list_files.mean=(samples=1, sum=116, mean=116.0000)));

```

Note that here 149 GET requests were issues, but only 1 HEAD request.
This is because the tool uses the optimized `openFile()` API, passing in the `FileStatus` of the source file retrieved
in the listing process: this is used to skip issuing the HEAD request in `open()` as the length and existence
of the file is known. Marginal reductions in the overhead of HTTP requests to object stores do make a difference
in performance, risk of IO throttling, *and* cost.


## Maximizing performance

### Block Size

A larger block size `-block` can be more efficient when reading data, especially from object stores.
This is because it avoids the need of the store to guess how much data to prefetch.

```bash
hadoop jar cloudstore-1.0.jar cloudup \
 -threads 32 -update -largest 16 -block 32 s3a://bucket/qelogs/ ./scratch  
```

### File Size

Smaller files are slower to upload.
* More data to scan in a listing.
* More operations to queue.
* With `-update`: the overhead of probing destination before skipping/uploading.
* Overhead of creation operation.
* The nominal gains of a a larger `-block` value are not realized as the files are too short.
* The stores throttle on IOPS, not necessarily on quantity of data read/written.

### Thread count

Increasing the number of threads copying files with the `-threads` offers speedup when there are many small files

```bash
 hadoop jar cloudstore-1.0.jar cloudup -threads 100 -largest 32 -block 64 -update share/hadoop/common  s3a://stevel-london/share/common

...

Summary of copy from file:/Users/stevel/Projects/Releases/cdp-7.1.9/share/hadoop/common to s3a://stevel-london/share/common
===========================================================================================================================

File copies attempted: 149; size 85,877,554 bytes
Files skipped: 0, size 0 bytes

Listing duration: (HH:MM:ss) : 0:00:01.740
Copy duration: (HH:MM:ss) : 0:00:23.134

Effective bandwidth 3.540 MiB/s, 28.322 Megabits/s
Seconds per file: 0.155s


```

And with 120 threads

```bash
 hadoop jar cloudstore-1.0.jar cloudup -threads 120 -largest 32 -block 64 -update share/hadoop/common  s3a://stevel-london/share/common

...

Summary of copy from file:/Users/stevel/Projects/Releases/cdp-7.1.9/share/hadoop/common to s3a://stevel-london/share/common
===========================================================================================================================

File copies attempted: 149; size 85,877,554 bytes
Files skipped: 0, size 0 bytes

Listing duration: (HH:MM:ss) : 0:00:01.560
Copy duration: (HH:MM:ss) : 0:00:22.842

Effective bandwidth 3.586 MiB/s, 28.685 Megabits/s
Seconds per file: 0.153s

```

The upload bandwidth has topped out at just under 29 Megabits/second;
this is clearly the bandwidth between the test host and the remote S3 store.

Asking for more threads simply becomes counterproductive as there is more contention for CPU time on the host, and no more bandwidth.


## Avoiding throttling

Issuing too many requests per second may trigger throttling, where the store sends a "throttled" response to the client
(generally 503, though 429 is also encountered with google cloud).
The store clients respond with a sleep and retry, d.with the sleep interval increasing if the repeated attempts are also
throttled.

Avoiding throttling is often more efficient than reacting to throttle events, because of the sleep delays introduced.
* IOStatistics from s3a will include throttling statistics
* Google gcs will log 429 errors at WARN in the output.

To minimize/avoid throttling
* Try to reduce other IO taking place against the same store, *including deletion*
* Ask for fewer threads.
* Use the most recent release of hadoop that you can, as speeding up cloud IO is always a focus of the team's work.

# AWS V2 SDK and large S3 File uploads over slow connections.

Hadoop releases without the fix for
[HADOOP-19295. S3A: fs.s3a.connection.request.timeout too low for large uploads over slow links
](https://issues.apache.org/jira/browse/HADOOP-19295)
may time out when uploading a large file to a remote store if the connection is slow, with the
error message "Client execution did not complete before the specified timeout configuration: 15000 millis"

```
2024-10-03 12:07:32,246 [s3a-transfer-stevel-london-bounded-pool1-t4] INFO  s3a.WriteOperationHelper (WriteOperationHelper.java:operationRetried(184)) - upload part #4 upload ID s4TDe4.rV1PdcesERDuWAhFeGFdpcaWlEG5Eno3Xpa5YL.CWWJRiSEmuT19Pu.K4dFLvRp18z4cuGJClTc52eKVe3LKzu.MAfWp5qipIgdHqgiUDl68swKeXcEbbvzfS on hadoop-3.4.1.tar.gz: Retried 0: org.apache.hadoop.fs.s3a.AWSApiCallTimeoutException: upload part #4 upload ID s4TDe4.rV1PdcesERDuWAhFeGFdpcaWlEG5Eno3Xpa5YL.CWWJRiSEmuT19Pu.K4dFLvRp18z4cuGJClTc52eKVe3LKzu.MAfWp5qipIgdHqgiUDl68swKeXcEbbvzfS on hadoop-3.4.1.tar.gz: software.amazon.awssdk.core.exception.ApiCallTimeoutException: Client execution did not complete before the specified timeout configuration: 15000 millis
```

If this occurs, set the property `fs.s3a.connection.request.timeout` to a larger number, such as `15m`.
We recommend doing this on the command line itself, rather than set it site-wide:

```bash
bin/hadoop jar cloudstore-1.0.jar cloudup -D fs.s3a.connection.request.timeout=15m -overwrite \
../releases/hadoop-3.4.1-RC2/hadoop-3.4.1.tar.gz \
s3a://stevel-london/
```