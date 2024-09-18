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

# Security Policy

## Supported Versions

None. You are on your own. Sorry.

(this isn't quite true...so do ask: bugs will be fixed on a best-effort basis)

## Reporting a Vulnerability

* file an issue
* if you have a fix, file a PR
* if the issue is in hadoop, file an apache JIRA.
* if the issue is in an transient dependency of hadoop, see
  [Transitive Issues](https://steveloughran.blogspot.com/2022/08/transitive-issues.html)
  then solve the entire software-versioning problem in java. please.

This library is actually written by Hadoop committers at cloudera;
if you are using Apache Hadoop -you are already running our code.

The builds take place on our local machines, reading in all dependencies
from our private maven artifact server -the same one used for all
cloudera releases.

The maven binaries used are pulled direct from apache, with
their GPG signatures checked before installation.

This means the risk of supply chain attack or deliberate
malicious code is pretty low.