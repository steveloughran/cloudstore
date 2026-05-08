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

# Command `bulkdelete`

Uses the Hadoop Filesystem bulkdelete API to read in a list of files to delete and delete them in batches, 

The files to delete are provided in a text file whose path is supplied in the command;
empty lines and lines beginning with `#` are ignored.

This command requires Hadoop 3.4.1 or later, or any
other Hadoop distribution with 
[HADOOP-18679](https://issues.apache.org/jira/browse/HADOOP-18679)
_Add API for bulk/paged delete of files and objects_.

```
Usage: bulkdelete [-verbose] [-page <pagesize>] <path> <file>
<file> is a text file with full/relative paths to files under <path>
   Empty lines and lines starting with # are ignored.
   As are root paths of stores
<pagesize> is the page size if less than the store page size 
```


The batch size defined is the minimum of the filesystem's page size and the value supplied with the `-page` option, if that is supplied and greater than zero.

The path resolution process is such that paths with a trailing "/" are coverted to
paths without the path, so that `/scratch/object/` is converted to `/scratch/object`.
This means that
* This command cannot be used for deleting directory markers.
* If there is an object/file at the path `/scratch/object` is will be deleted


All filesystems support bulk delete with a page size of at least 1.
That is: either a store offers a custom large-page delete operation (as S3A does),
or bulk delete is simply implemented as a series of *file* deletions.
If a directory is passed down to other stores, it is an error. 

## Example 1: S3A bulk delete

Object store bulk deletion is exactly what this is designed for, the
[AWS Multiple Object Delete](https://docs.aws.amazon.com/AmazonS3/latest/userguide/delete-multiple-objects.html) is the API used
through S3a when more than one object is to be deleted.

The S3A implementation has a maximum page size set by the option
`fs.s3a.bulk.delete.page.size`. Although the AWS limit is 1000,
we have encountered serious problems using a page size this large
on heavily loaded systems [HADOOP-16823
Large DeleteObject requests are their own Thundering Herd](https://issues.apache.org/jira/browse/HADOOP-16823).

It will only delete files at the target path; if there
is a directory there it will be ignored.

```
 bin/hadoop jar cloudstore-1.1.jar bulkdelete -page 4 -verbose s3a://stevel-london/ delete.txt
Bulk delete under s3a://stevel-london/
======================================

5 files to delete
Store page size = 250
Delete page size = 4

  s3a://stevel-london/scratch/object
  s3a://stevel-london/scratch/object
  s3a://stevel-london/scratch/object
  s3a://stevel-london/scratch
  s3a://stevel-london/scratch/object/subdir

Summary
=======

Bulk delete of 5 file(s) finished, duration:  0:00:00.898
Batch count: 2. Failure count: 0

Statistics
==========


Statistics
counters=((audit_request_execution=2)
(audit_span_creation=2)
(filesystem_initialization=1)
(object_bulk_delete_request=1)
(object_delete_objects=5)
(object_delete_request=1)
(store_client_creation=1)
(store_io_request=2));

gauges=();

minimums=((filesystem_initialization.min=451)
(object_bulk_delete_request.min=649)
(object_delete_request.min=240)
(store_client_creation.min=402)
(store_io_rate_limited_duration.min=0));

maximums=((filesystem_initialization.max=451)
(object_bulk_delete_request.max=649)
(object_delete_request.max=240)
(store_client_creation.max=402)
(store_io_rate_limited_duration.max=0));

means=((filesystem_initialization.mean=(samples=1, sum=451, mean=451.0000))
(object_bulk_delete_request.mean=(samples=1, sum=649, mean=649.0000))
(object_delete_request.mean=(samples=1, sum=240, mean=240.0000))
(store_client_creation.mean=(samples=1, sum=402, mean=402.0000))
(store_io_rate_limited_duration.mean=(samples=2, sum=0, mean=0.0000)));

```



### Example 2: S3A with multiobject delete disabled.

If multi object delete has been disabled, the page size provided by the filesystem client is 1, so the behavior is the same as with other filesystems: only one object is
deleted per batch


```xml
  <property>
    <name>fs.s3a.multiobjectdelete.enable</name>
    <value>false</value>
  </property>
```

```
 > hadoop jar cloudstore-1.1.jar bulkdelete -Dfs.s3a.multiobjectdelete.enable=false -page 4 -verbose s3a://stevel-london/ delete.txt


Bulk delete under s3a://stevel-london/
======================================

5 files to delete
Store page size = 1

  s3a://stevel-london/scratch/object
  s3a://stevel-london/scratch/object
  s3a://stevel-london/scratch/object
  s3a://stevel-london/scratch
  s3a://stevel-london/scratch/object/subdir

Summary
=======

Bulk delete of 5 file(s) finished, duration: 0:00:01.433
Batch count: 5. Failure count: 0

Statistics
==========


Statistics
counters=((audit_request_execution=5)
(audit_span_creation=2)
(filesystem_initialization=1)
(object_delete_objects=5)
(object_delete_request=5)
(store_client_creation=1)
(store_io_request=5));

gauges=();

minimums=((filesystem_initialization.min=483)
(object_delete_request.min=67)
(store_client_creation.min=427)
(store_io_rate_limited_duration.min=0));

maximums=((filesystem_initialization.max=483)
(object_delete_request.max=1422)
(store_client_creation.max=427)
(store_io_rate_limited_duration.max=0));

means=((filesystem_initialization.mean=(samples=1, sum=483, mean=483.0000))
(object_delete_request.mean=(samples=5, sum=1707, mean=341.4000))
(store_client_creation.mean=(samples=1, sum=427, mean=427.0000))
(store_io_rate_limited_duration.mean=(samples=5, sum=0, mean=0.0000)));


```

Note how the operation duration has increased from 898 milliseconds to
1433 milliseconds, even with only 5 objects to delete.
The more files to delete, the more significant this delay is.
"