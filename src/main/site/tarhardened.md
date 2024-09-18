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

# Command `tarhardened`

Verify the hadoop release has had its untar command hardened and will
not evaluate commands passed in as filenames.

```bash
bin/hadoop jar $CLOUDSTORE tarhardened "file.tar; true"
```

*Bad*

```
Attempting to untar file with name "file.tar; true"
untar operation reported success

2023-01-27 16:42:35,931 [main] INFO  util.ExitUtil (ExitUtil.java:terminate(124)) - Exiting with status 0
```

Although the file doesn't exist, the bash "true" command was executed after the untar, so
the operation was reported as a success.

*Good*

```
2023-01-27 16:48:44,461 [main] INFO  util.ExitUtil (ExitUtil.java:terminate(210)) - Exiting with status -1: ExitCodeException exitCode=1: tar: Error opening archive: Failed to open 'file.tar; true'

```

The file `file.tar; true` was attempted to be opened; as it is not present the operation failed.
Expect a stack trace in the report
