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

# Command `mkbucket`

Creates a bucket.

Usage
```
Usage: mkbucket <region> <S3A path>
```

```bash
hadoop jar cloudstore-1.1.jar mkbucket us-east-2 s3a://new-bucket-name/
```

The per-bucket settings of the target bucket name are used to create the bucket,
for example the endpoint and login details.
However, if you are attempting complex configurations, e.g. creating buckets for
a different account, it is a lot safer to set the base configuration options.
