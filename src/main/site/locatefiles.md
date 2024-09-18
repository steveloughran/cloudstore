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

# Command `locatefiles`

Use the mapreduce `LocatedFileStatusFetcher` to scan for all non-hidden
files under a path. This matches exactly the scan process used in `FileInputFormat`,
so offers a command line way to view and tune scans of object stores.
It can also be used in comparison with the `list` command to compare the
difference between the maximum performance of scanning the directory
tree with the actual performance you are likely to see during query planning.

To control the number of threads used to scan directories in production
jobs, set the value in the configuration option
`mapreduce.input.fileinputformat.list-status.num-threads` 

Usage:

```
hadoop jar cloudstore-1.0.jar locatefiles
Usage: locatefiles
  -D <key=value>    Define a property
  -tokenfile <file> Hadoop token file to load
  -xmlfile <file>   XML config file to load
  -threads <threads> number of threads
  -verbose          print verbose output
[<path>|<pattern>]```
```

Example

```
> hadoop jar cloudstore-1.0.jar locatefiles \
 -threads 8 -verbose \
 s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/


Locating files under s3a://landsat-pds/L8/001/002/LC80010022016230LGN00 with thread count 8
===========================================================================================

2019-07-29 16:48:19,844 [main] INFO  tools.LocateFiles (DurationInfo.java:<init>(53)) - Starting: List located files
2019-07-29 16:48:19,847 [main] INFO  tools.LocateFiles (DurationInfo.java:<init>(53)) - Starting: LocateFileStatus execution
2019-07-29 16:48:24,645 [main] INFO  tools.LocateFiles (DurationInfo.java:close(100)) - LocateFileStatus execution: duration 0:04:798
[0001] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B1.TIF 63,786,465 stevel stevel [encrypted]
[0002] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B1.TIF.ovr 8,475,353 stevel stevel [encrypted]
[0003] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B10.TIF 35,027,713 stevel stevel [encrypted]
[0004] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_B10.TIF.ovr 6,029,012 stevel stevel [encrypted]
...
[0039] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_thumb_large.jpg 270,427 stevel stevel [encrypted]
[0040] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/LC80010022016230LGN00_thumb_small.jpg 12,873 stevel stevel [encrypted]
[0041] s3a://landsat-pds/L8/001/002/LC80010022016230LGN00/index.html 3,780 stevel stevel [encrypted]
2019-07-29 16:48:24,652 [main] INFO  tools.LocateFiles (DurationInfo.java:close(100)) - List located files: duration 0:04:810

Found 41 files, 117 milliseconds per file
Data size 923,518,099 bytes, 22,524,831 bytes per file

Storage Statistics
==================

directories_created 0
directories_deleted 0
files_copied 0
files_copied_bytes 0
files_created 0
files_deleted 0
files_delete_rejected 0
fake_directories_created 0
fake_directories_deleted 0
ignored_errors 0
op_copy_from_local_file 0
op_create 0
op_create_non_recursive 0
op_delete 0
op_exists 0
op_get_delegation_token 0
op_get_file_checksum 0
op_get_file_status 2
op_glob_status 1
op_is_directory 0
op_is_file 0
op_list_files 0
op_list_located_status 1
op_list_status 0
op_mkdirs 0
op_open 0
op_rename 0
object_copy_requests 0
object_delete_requests 0
object_list_requests 3
object_continue_list_requests 0
object_metadata_requests 4
object_multipart_initiated 0
object_multipart_aborted 0
object_put_requests 0
object_put_requests_completed 0
object_put_requests_active 0
object_put_bytes 0
object_put_bytes_pending 0
object_select_requests 0
...
store_io_throttled 0
```

You get the metrics with the `-verbose` option;

There is plenty room for improvements in S3A directory tree scanning.
Patches welcome! 

You can also explore what directory tree structure is most efficient here.
