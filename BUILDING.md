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

# Building

With maven, with profiles for many different hadoop versions.

To build a production release
1. Use java8
1. And compile against a shipping hadoop version, with `-Pextra` for the extra stuff


```bash
mvn clean install -Phadoop-3.3.2 -Pextra
```

To publish the release use the gui or the github command line

```bash
set -gx date 2023-03-03
git add .
git commit -S -m "release $date"
git push
gh release create tag-release-$date -t release-$date -n "release of $date" -d target/cloudstore-1.0.jar
# then go to the web ui to review and finalize the relese
```

* If a new release is made the same day, remember to create a new tag
* The version `cloudstore-1.0.jar` is always used, not just from laziness but because it allows
for bash scripts to always be able to fetch the latest version through curl then execute it.


