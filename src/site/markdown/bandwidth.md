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

# bandwidth

## Command

Measure upload/download bandwidth; support different read policies, and optionally save the output to a CSV file.

```
> hadoop jar cloudstore-1.1.jar bandwidth

Usage: bandwidth [options] size <path>
        -D <key=value>  Define a property
        -block <block-size>     block size in megabytes
        -csv <file>     CSV file to log operation details
        -flush  flush the output after writing each block
        -hflush hflush() the output after writing each block
        -keep   do not delete the file
        -rename rename file to suffix .renamed
        -policy <policy>        read policy for file (whole-file, sequential, random...)
        -tokenfile <file>       Hadoop token file to load
        -verbose        print verbose output
        -xmlfile <file> XML config file to load
```


## Example

Upload 128M of data to s3 with a block size of 8 megabytes; use `-verbose` output to print stream and filesystem
statistics. Save the summary to a CSV file for review.

```
> hadoop jar cloudstore-1.1.jar bandwidth -csv tmp/s3a128m.csv -block 8 -verbose -policy whole-file 128m s3a://stevel-london/tmp

Bandwidth test against s3a://stevel-london/tmp with data size 128m
==================================================================

Block size 8 MB
Saving statistics as CSV data to tmp/s3a128m.csv
2023-09-14 13:57:00,959 [main] INFO  impl.DirectoryPolicyImpl (DirectoryPolicyImpl.java:getDirectoryPolicy(189)) - Directory markers will be kept
Using filesystem s3a://stevel-london
Upload size in Megabytes 128 MB
Writing data as 16 blocks each of size 8,388,608 bytes
Starting: Opening s3a://stevel-london/tmp for upload
Duration of Opening s3a://stevel-london/tmp for upload: 0:00.397
Write block 0 in 0.002 seconds
Write block 1 in 0.002 seconds
Write block 2 in 0.002 seconds
Write block 3 in 0.099 seconds
Write block 4 in 0.002 seconds
Write block 5 in 0.002 seconds
Write block 6 in 0.003 seconds
Write block 7 in 0.002 seconds
Write block 8 in 0.002 seconds
Write block 9 in 0.002 seconds
Write block 10 in 0.002 seconds
Write block 11 in 11.093 seconds
Write block 12 in 0.004 seconds
Write block 13 in 0.003 seconds
Write block 14 in 0.002 seconds
Write block 15 in 8.248 seconds

Starting: upload stream close()
Duration of upload stream close(): 0:11.706
Progress callbacks 16420; in close 5734
Upload Stream: FSDataOutputStream{wrappedStream=S3ABlockOutputStream{WriteOperationHelper {bucket=stevel-london}, blockSize=33554432 Statistics=counters=((stream_write_bytes=134217728) (multipart_upload_completed.failures=0) (op_hsync=0) (multipart_upload_completed=1) (stream_write_exceptions=0) (stream_write_block_uploads=4) (stream_write_queue_duration=0) (op_hflush=0) (action_executor_acquired.failures=0) (stream_write_total_time=0) (stream_write_exceptions_completing_upload=0) (action_executor_acquired=0) (op_abort.failures=0) (op_abort=0) (object_multipart_aborted.failures=0) (stream_write_total_data=134217728) (object_multipart_aborted=0));
gauges=((stream_write_block_uploads_pending=0) (stream_write_block_uploads_data_pending=0));
minimums=((action_executor_acquired.failures.min=-1) (multipart_upload_completed.min=119) (object_multipart_aborted.failures.min=-1) (op_abort.min=-1) (action_executor_acquired.min=1) (object_multipart_aborted.min=-1) (multipart_upload_completed.failures.min=-1) (op_abort.failures.min=-1));
maximums=((action_executor_acquired.max=11092) (object_multipart_aborted.failures.max=-1) (op_abort.max=-1) (object_multipart_aborted.max=-1) (multipart_upload_completed.failures.max=-1) (op_abort.failures.max=-1) (action_executor_acquired.failures.max=-1) (multipart_upload_completed.max=119));
means=((object_multipart_aborted.mean=(samples=0, sum=0, mean=0.0000)) (op_abort.failures.mean=(samples=0, sum=0, mean=0.0000)) (action_executor_acquired.mean=(samples=4, sum=19349, mean=4837.2500)) (op_abort.mean=(samples=0, sum=0, mean=0.0000)) (action_executor_acquired.failures.mean=(samples=0, sum=0, mean=0.0000)) (multipart_upload_completed.failures.mean=(samples=0, sum=0, mean=0.0000)) (multipart_upload_completed.mean=(samples=1, sum=119, mean=119.0000)) (object_multipart_aborted.failures.mean=(samples=0, sum=0, mean=0.0000)));
}}

FileSystem s3a://stevel-london

S3AFileSystem{uri=s3a://stevel-london, workingDir=s3a://stevel-london/user/stevel, inputPolicy=normal, partSize=33554432, enableMultiObjectsDelete=true, maxKeys=5000, readAhead=32768, blockSize=33554432, multiPartThreshold=134217728, s3EncryptionAlgorithm='SSE_KMS', blockFactory=org.apache.hadoop.fs.s3a.S3ADataBlocks$DiskBlockFactory@a7f0ab6, auditManager=Service ActiveAuditManagerS3A in state ActiveAuditManagerS3A: STARTED, auditor=LoggingAuditor{ID='ee1422f2-dd3f-489a-a9a3-fb5fee3db23e', headerEnabled=true, rejectOutOfSpan=false}}, authoritativePath=[], useListV1=false, magicCommitter=true, boundedExecutor=BlockingThreadPoolExecutorService{SemaphoredDelegatingExecutor{permitCount=384, available=384, waiting=0}, activeCount=0}, unboundedExecutor=java.util.concurrent.ThreadPoolExecutor@41f35f7c[Running, pool size = 0, active threads = 0, queued tasks = 0, completed tasks = 0], credentials=AWSCredentialProviderList[refcount= 1: [TemporaryAWSCredentialsProvider, SimpleAWSCredentialsProvider] last provider: SimpleAWSCredentialsProvider, delegation tokens=disabled, DirectoryMarkerRetention{policy='keep'}, instrumentation {S3AInstrumentation{}}, ClientSideEncryption=false}


Download s3a://stevel-london/tmp
================================

Starting: open s3a://stevel-london/tmp
Read block 0 in 1.773 seconds
Read block 1 in 3.491 seconds
Read block 2 in 4.255 seconds
Read block 3 in 4.929 seconds
Read block 4 in 5.030 seconds
Read block 5 in 4.648 seconds
Read block 6 in 3.951 seconds
Read block 7 in 5.080 seconds
Read block 8 in 3.548 seconds
Read block 9 in 5.085 seconds
Read block 10 in 4.298 seconds
Read block 11 in 3.824 seconds
Read block 12 in 3.883 seconds
Read block 13 in 5.594 seconds
Read block 14 in 5.318 seconds
Read block 15 in 4.743 seconds

Starting: download stream close()
Duration of download stream close(): 0:00.003
Download Stream: org.apache.hadoop.fs.FSDataInputStream@4784013e: S3AInputStream{s3a://stevel-london/tmp wrappedStream=closed read policy=normal pos=134217728 nextReadPos=0 contentLength=134217728 contentRangeStart=0 contentRangeFinish=134217728 remainingInCurrentRequest=0 ChangeTracker{VersionIdChangeDetectionPolicy mode=Server, revisionId='_p9gzHB4V256F6gROEMs5dP8MQqFGvik'}
StreamStatistics{counters=((stream_read_fully_operations=16) (stream_read_seek_backward_operations=0) (stream_read_seek_policy_changed=1) (stream_read_seek_operations=0) (stream_read_seek_bytes_skipped=0) (action_http_get_request=1) (stream_read_total_bytes=134217728) (stream_read_bytes=134217728) (stream_read_version_mismatches=0) (stream_read_unbuffered=0) (stream_read_opened=1) (stream_read_closed=1) (stream_read_exceptions=0) (stream_read_close_operations=1) (stream_read_seek_forward_operations=0) (stream_read_seek_bytes_discarded=0) (stream_read_bytes_discarded_in_abort=0) (stream_read_operations=8323) (stream_read_bytes_backwards_on_seek=0) (stream_read_bytes_discarded_in_close=0) (stream_read_operations_incomplete=8307) (stream_aborted=0) (action_http_get_request.failures=0));
gauges=((stream_read_gauge_input_policy=0));
minimums=((action_http_get_request.min=62) (action_http_get_request.failures.min=-1));
maximums=((action_http_get_request.failures.max=-1) (action_http_get_request.max=62));
means=((action_http_get_request.failures.mean=(samples=0, sum=0, mean=0.0000)) (action_http_get_request.mean=(samples=1, sum=62, mean=62.0000)));
}}
Starting: delete file s3a://stevel-london/tmp
Duration of delete file s3a://stevel-london/tmp: 0:00.161

Upload Summary
==============

Data size 134,217,728 bytes
Upload duration 0:31.598

Upload bandwidth in Megabits/second 32.407 Mbit/s
Upload bandwidth in Megabytes/second 4.051 MB/s
Blocks uploaded (ignoring close() overhead): 16: min 0.002 seconds, max 11.093 seconds, mean 1.217 seconds,

Close() duration: 0:11.706 (minute:seconds)
Mean Upload duration/block including close() overhead 3.191 seconds

Download Summary
================

Data size 134,217,728 bytes
Download duration 1:09.531

Download bandwidth in Megabits/second 14.727 Mbit/s
Download bandwidth in Megabytes/second 1.841 MB/s
Blocks downloaded: 16: min 1.773 seconds, max 5.594 seconds, mean 4.341 seconds,

CSV formatted data saved to tmp/s3a128m.csv

```

This example was executed on hadoop 3.3.4 against a remote AWS S3 store.

Note how most of the upload happened in the `close()` call.
This is typical when the data being written is generated faster than the upload bandwidth
of the uplink; the `close()` call blocks until the upload is complete.

This is also why application code which assumes that closing a stream is fast is at risk
of problems when this happens, such as timing out if heartbeats are required to be
generated during the upload.


## CSV output

The CSV file records the operations which have taken place, bytes processed and duration.

### Columns



| Column    | Meaning                         |
|-----------|---------------------------------|
| operation | operation which took place      |
| bytes     | bytes processed in operation    |
| total     | total bytes in ongoing sequence |
| duration  | duration in milliseconds        |

### operations

| Operation         | Meaning                                          |
|-------------------|--------------------------------------------------|
| create-file       | file creation                                    |
| upload-block      | upload an individual block                       |
| close-upload      | close() the output stream to complete the upload |
| upload            | total upload time                                |
| open-for-download | open the file for download                       |
| download-block    | download an individual block                     |
| download          | total download time                              |

### CSV example

Here the the CSV output from the previous example.

Note how the upload operations initially take on a few milliseconds,
but there are some which take seconds.

This operation is to the AWS S3 store, where writes are done in blocks; the 99 mS delay on
block four is probably the multipart upload being initiated and the first block being asynchronously
queued for upload. The threshold is set in `fs.s3a.multipart.size`
```xml

  <property>
    <name>fs.s3a.fast.upload.active.blocks</name>
    <value>2</value>
  </property>

  <property>
    <name>fs.s3a.multipart.size</name>
    <value>32M</value>
  </property>

```
The next big delays of 110993 and 8248 ms are due not to blocks being uploaded, but
in waiting for the block queue to have space to queue another asynchronous block upload
-that is, to wait for an ongoing upload to complete.

The `close-upload` operation is when the upload completes and all buffers being
written to are complete, then the final multipart operation is finished.

On S3A, for data below the multipart threshold, all the upload takes place in the close() call.
(Excluding the "magic" uploads of the magic s3a committer; these are always multipart writes.)

The download bandwidth in this experiment (Macbook Pro M1; WiFi) is less than the upload.
This is because a single HTTP stream is being used for download; there is no parallelism
and the application has to wait for data to be streamed in.

There are two ways in some recent Hadoop releases to speed this up.
* Vector IO (hadoop-3.3.5): parallelized reads of explicit ranges; out of order arrival.
  This requires application/library code to be aware of the API to explicitly use it.
* Prefetching (hadoop-3.3.6): prefetching blocks of data in parallel GET requests.
  This requires no application changes, but is less optimal for parquet/orc libraries
  if they have explicit support for vector IO

```csv
"operation","bytes","total","duration"
"create-file",0,0,397
"upload-block",8388608,8388608,2
"upload-block",8388608,16777216,2
"upload-block",8388608,25165824,2
"upload-block",8388608,33554432,99
"upload-block",8388608,41943040,2
"upload-block",8388608,50331648,2
"upload-block",8388608,58720256,3
"upload-block",8388608,67108864,2
"upload-block",8388608,75497472,2
"upload-block",8388608,83886080,2
"upload-block",8388608,92274688,2
"upload-block",8388608,100663296,11093
"upload-block",8388608,109051904,4
"upload-block",8388608,117440512,3
"upload-block",8388608,125829120,2
"upload-block",8388608,134217728,8248
"close-upload",0,134217728,11706
"upload",8388608,8388608,31598
"open-for-download",0,0,47
"download-block",8388608,8388608,1773
"download-block",8388608,16777216,3491
"download-block",8388608,25165824,4255
"download-block",8388608,33554432,4929
"download-block",8388608,41943040,5030
"download-block",8388608,50331648,4648
"download-block",8388608,58720256,3951
"download-block",8388608,67108864,5080
"download-block",8388608,75497472,3548
"download-block",8388608,83886080,5085
"download-block",8388608,92274688,4298
"download-block",8388608,100663296,3824
"download-block",8388608,109051904,3883
"download-block",8388608,117440512,5594
"download-block",8388608,125829120,5318
"download-block",8388608,134217728,4743
"download",134217728,134217728,69531
```
Here's the experiment repeated with prefetching enabled and changes to block upload policy

```xml

  <property>
    <name>fs.s3a.fast.upload.active.blocks</name>
    <value>4</value>
  </property>

  <property>
    <name>fs.s3a.multipart.size</name>
    <value>24M</value>
  </property>

```
```bash
bin/hadoop jar cloudstore-1.1.jar  bandwidth -D fs.s3a.prefetch.enabled=true -csv tmp/s3a128mp.csv -block 8 -verbose -policy whole-file 128m s3a://stevel-london

Upload Summary
==============

Data size 134,217,728 bytes
Upload duration 1:19.081

Upload bandwidth in Megabits/second 12.949 Mbit/s
Upload bandwidth in Megabytes/second 1.619 MB/s
Blocks uploaded (ignoring close() overhead): 16: min 0.000 seconds, max 49.304 seconds, mean 3.092 seconds,

Close() duration: 0:29.135 (minute:seconds)
Mean Upload duration/block including close() overhead 8.034 seconds

Download Summary
================

Data size 134,217,728 bytes
Download duration 0:29.669

Download bandwidth in Megabits/second 34.514 Mbit/s
Download bandwidth in Megabytes/second 4.314 MB/s
Blocks downloaded: 16: min 0.001 seconds, max 11.143 seconds, mean 1.851 seconds,

```

The changed upload settings: smaller blocks and a bigger queue actually seemed to slow down the upload performance.

Possible causes
1. Changes in test setup; the benchmarks should be done with a physical ethernet connection and no other network traffic
2. More blocks == more HTTPS connections to set up with TLS negotiation and flow control ramp up delays
3. Contention for bandwidth between the multiple streams.

Download time was *significantly* faster, more than doubling its bandwidth.

```csv
"operation","bytes","total","duration"
"create-file",0,0,453
"upload-block",8388608,8388608,0
"upload-block",8388608,16777216,1
"upload-block",8388608,25165824,148
"upload-block",8388608,33554432,3
"upload-block",8388608,41943040,0
"upload-block",8388608,50331648,1
"upload-block",8388608,58720256,4
"upload-block",8388608,67108864,1
"upload-block",8388608,75497472,0
"upload-block",8388608,83886080,4
"upload-block",8388608,92274688,1
"upload-block",8388608,100663296,0
"upload-block",8388608,109051904,3
"upload-block",8388608,117440512,1
"upload-block",8388608,125829120,49304
"upload-block",8388608,134217728,1
"close-upload",0,134217728,29135
"upload",8388608,8388608,79081
```

The CSV file implies that most blocks were written straight to disk cache;
one block write blocked for 49 seconds waiting for more capacity.



```csv
"open-for-download",0,0,22
"download-block",8388608,8388608,1862
"download-block",8388608,16777216,11143
"download-block",8388608,25165824,588
"download-block",8388608,33554432,502
"download-block",8388608,41943040,3443
"download-block",8388608,50331648,1
"download-block",8388608,58720256,1
"download-block",8388608,67108864,1
"download-block",8388608,75497472,1
"download-block",8388608,83886080,1848
"download-block",8388608,92274688,2380
"download-block",8388608,100663296,1
"download-block",8388608,109051904,6393
"download-block",8388608,117440512,1
"download-block",8388608,125829120,1
"download-block",8388608,134217728,1457
"download",134217728,134217728,29669

```

Download performance shows a slow read for the first blocks, but then subsequent reads are either very fast (data already downloaded and cached to disk), or a read needs to complete.

Again, more experiments would be needed to reach conclusions here.
