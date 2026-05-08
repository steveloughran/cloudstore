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

# Command `constval`

Loads a class, resolves a constant/static final field and prints its value.


```bash

hadoop jar cloudstore-1.1.jar constval org.apache.hadoop.fs.s3a.Constants REQUEST_TIMEOUT
Value of org.apache.hadoop.fs.s3a.Constants.REQUEST_TIMEOUT = "fs.s3a.connection.request.timeout"

hadoop jar cloudstore-1.1.jar constval org.apache.hadoop.fs.s3a.Constants DEFAULT_REQUEST_TIMEOUT_DURATION
Value of org.apache.hadoop.fs.s3a.Constants.DEFAULT_REQUEST_TIMEOUT_DURATION = "PT0S"

hadoop jar cloudstore-1.1.jar constval org.apache.hadoop.fs.s3a.Constants DEFAULT_REQUEST_TIMEOUT
Value of org.apache.hadoop.fs.s3a.Constants.DEFAULT_REQUEST_TIMEOUT = "0"

```
