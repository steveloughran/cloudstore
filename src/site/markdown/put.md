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



#  Command `put`

Put a file from a source path (local or remote), to a destination.

* Uses the newer openFile/createFile API calls and allows for a property file listing options to set on the createFile call.
* Prints IO statistics
* Supports the `-debug` mode for a low level trace of the network IO.

```bash
bin/hadoop jar cloudstore-1.1.jar put -options options -debug bin/hadoop s3a://target-london/hadoop
```

Here the options file adds a custom header to the object, and forces multipart IO on hadoop releases with
[HADOOP-19256.S3A: Support S3 Conditional Writes](https://issues.apache.org/jira/browse/HADOOP-19256) (Hadoop 3.4.2+).

```properties
# add a header, just PoC
fs.s3a.create.header.custom-header=value
# force multipart
fs.s3a.create.multipart=true
```

