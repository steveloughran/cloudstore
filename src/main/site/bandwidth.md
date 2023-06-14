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

Measure upload/download bandwidth.

```bash
 bin/hadoop jar cloudstore-1.0.jar bandwidth
Usage: bandwidth [options] size <path>
        -D <key=value>  Define a property
        -keep   do not delete the file
        -rename rename file to suffix .renamed
        -tokenfile <file>       Hadoop token file to load
        -verbose        print verbose output
        -xmlfile <file> XML config file to load
```

90tions

* `-keep` : keep the file (or the renamed file) rather than delete it after the download
* `-rename` : rename the file before the download
## Example
```bash
> bin/hadoop jar cloudstore-1.0.jar bandwidth 64M s3a://stevel-london/bw

Bandwidth test against s3a://stevel-london/bw with data size 64m
2022-07-22 19:34:07,014 [main] INFO  Configuration.deprecation (Configuration.java:logDeprecation(1441)) - fs.s3a.server-side-encryption.key is deprecated. Instead, use fs.s3a.encryption.key
2022-07-22 19:34:07,015 [main] INFO  Configuration.deprecation (Configuration.java:logDeprecation(1441)) - fs.s3a.server-side-encryption-algorithm is deprecated. Instead, use fs.s3a.encryption.algorithm
2022-07-22 19:34:07,543 [main] INFO  impl.DirectoryPolicyImpl (DirectoryPolicyImpl.java:getDirectoryPolicy(189)) - Directory markers will be kept
Using filesystem s3a://stevel-london
Upload size in Megabytes 64 MB
2022-07-22 19:34:07,556 [main] INFO  commands.Bandwidth (StoreDurationInfo.java:<init>(56)) - Starting: Opening s3a://stevel-london/bw for upload
2022-07-22 19:34:08,177 [main] INFO  commands.Bandwidth (StoreDurationInfo.java:close(115)) - Opening s3a://stevel-london/bw for upload: duration 0:00:622
0
1
2
3
4
5
6
7
8
9
10
11
12
13
14
15
16
17
18
19
20
21
22
23
24
25
26
27
28
29
30
31
32
33
34
35
36
37
38
39
40
41
42
43
44
45
46
47
48
49
50
51
52
53
54
55
56
57
58
59
60
61
62
63

2022-07-22 19:34:08,272 [main] INFO  commands.Bandwidth (StoreDurationInfo.java:<init>(56)) - Starting: upload stream close()
2022-07-22 19:34:16,924 [main] INFO  commands.Bandwidth (StoreDurationInfo.java:close(115)) - upload stream close(): duration 0:08:652

Download
========

2022-07-22 19:34:16,924 [main] INFO  commands.Bandwidth (StoreDurationInfo.java:<init>(56)) - Starting: open s3a://stevel-london/bw
2022-07-22 19:34:16,967 [main] INFO  commands.Bandwidth (StoreDurationInfo.java:close(115)) - open s3a://stevel-london/bw: duration 0:00:043
0
1
2
3
4
5
6
7
8
9
10
11
12
13
14
15
16
17
18
19
20
21
22
23
24
25
26
27
28
29
30
31
32
33
34
35
36
37
38
39
40
41
42
43
44
45
46
47
48
49
50
51
52
53
54
55
56
57
58
59
60
61
62
63

2022-07-22 19:34:20,795 [main] INFO  commands.Bandwidth (StoreDurationInfo.java:<init>(56)) - Starting: download stream close()
2022-07-22 19:34:20,797 [main] INFO  commands.Bandwidth (StoreDurationInfo.java:close(115)) - download stream close(): duration 0:00:002
2022-07-22 19:34:20,797 [main] INFO  commands.Bandwidth (StoreDurationInfo.java:<init>(56)) - Starting: delete file s3a://stevel-london/bw
2022-07-22 19:34:20,851 [main] INFO  commands.Bandwidth (StoreDurationInfo.java:close(115)) - delete file s3a://stevel-london/bw: duration 0:00:054

Upload Summary
==============

Upload duration 0:09:369

Upload bandwidth in Megabits/second 54.648 Mbit/s

Upload bandwidth in Megabytes/second 6.831 MB/s


Download Summary
================

Download duration 0:03:873

Download bandwidth in Megabits/second 132.197 Mbit/s

Download bandwidth in Megabytes/second 16.525 MB/s

2022-07-22 19:34:20,855 [shutdown-hook-0] INFO  statistics.IOStatisticsLogging (IOStatisticsLogging.java:logIOStatisticsAtLevel(269)) - IOStatistics: counters=((action_http_get_request=1)
(action_http_head_request=2)
(audit_request_execution=9)
(audit_span_creation=4)
(files_created=1)
(files_deleted=1)
(multipart_upload_completed=1)
(object_delete_objects=1)
(object_delete_request=1)
(object_list_request=1)
(object_metadata_request=2)
(object_multipart_initiated=1)
(object_put_bytes=67108864)
(object_put_request_completed=2)
(op_create=1)
(op_delete=1)
(op_open=1)
(store_io_request=10)
(stream_read_bytes=67108864)
(stream_read_close_operations=1)
(stream_read_closed=1)
(stream_read_fully_operations=64)
(stream_read_opened=1)
(stream_read_operations=7775)
(stream_read_operations_incomplete=7711)
(stream_read_seek_policy_changed=1)
(stream_read_total_bytes=67108864)
(stream_write_block_uploads=4)
(stream_write_bytes=67108864)
(stream_write_queue_duration=8)
(stream_write_total_data=134217728)
(stream_write_total_time=16295));

gauges=();

minimums=((action_executor_acquired.min=0)
(action_http_get_request.min=50)
(action_http_head_request.min=22)
(object_delete_request.min=27)
(object_list_request.min=593)
(object_multipart_initiated.min=68)
(op_create.min=617)
(op_delete.min=29));

maximums=((action_executor_acquired.max=8)
(action_http_get_request.max=50)
(action_http_head_request.max=27)
(object_delete_request.max=27)
(object_list_request.max=593)
(object_multipart_initiated.max=68)
(op_create.max=617)
(op_delete.max=29));

means=((action_executor_acquired.mean=(samples=2, sum=8, mean=4.0000))
(action_http_get_request.mean=(samples=1, sum=50, mean=50.0000))
(action_http_head_request.mean=(samples=2, sum=49, mean=24.5000))
(object_delete_request.mean=(samples=1, sum=27, mean=27.0000))
(object_list_request.mean=(samples=1, sum=593, mean=593.0000))
(object_multipart_initiated.mean=(samples=1, sum=68, mean=68.0000))
(op_create.mean=(samples=1, sum=617, mean=617.0000))
(op_delete.mean=(samples=1, sum=29, mean=29.0000)));


________________________________________________________
Executed in   14.68 secs    fish           external
   usr time    6.08 secs    0.09 millis    6.08 secs
   sys time    0.67 secs    2.10 millis    0.67 secs

```

This example was executed on hadoop 3.3.4 with s3a configured to dump
iostats in `FileSystem.close()`; an MBP M1Pro connected to Gigabit FTTH connection by
ethernet cable. The AWS S3 store was 100-120 miles away.

Note how most of the upload happened in the `close()` call.
This is typical when the data being written is generated faster than the upload bandwidth
of the uplink; the `close()` call blocks until the upload is complete.

This is also why application code which assumes that closing a stream is fast is at risk
of problems when this happens, such as timing out if heartbeats are required to be
generated during the upload.


