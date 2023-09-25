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


## Command `cloudup` -upload and download files; optimised for cloud storage 

Bulk download of everything from s3a://bucket/qelogs/ to the local dir localquelogs (assuming the default fs is file://)

Usage

```
Usage: cloudup [options] <source> <dest>
        -D <key=value>  Define a property
        -ignore ignore errors
        -largest <largest>      number of large files to upload first
        -overwrite      overwrite files
        -threads <threads>      number of worker threads
        -tokenfile <file>       Hadoop token file to load
        -verbose        print verbose output
        -xmlfile <file> XML config file to load


```

Algorithm

1. source files are listed.
2. A pool of worker threads is created
3. the largest N files are queued for upload first, where N is a default or the value set by `-l`.
4. The remainder of the files are randomized to avoid throttling and then queued
5. the program waits for everything to complete.
6. Source and dest FS stats are printed.

This is not `distcp` run across a cluster; it's a single process with some threads. 
Works best for reading lots of small files from an object store or when you have a 
mix of large and small files to download or uplaod.



```
bin/hadoop jar cloudstore-1.0.jar cloudup \
 -s s3a://bucket/qelogs/ \
 -d localqelogs \
 -t 32 -o
```

and the other way

```
bin/hadoop jar cloudstore-1.0.jar cloudup \
 -d localqelogs \
 -s s3a://bucket/qelogs/ \
 -t 32 -o  -l 4
```
