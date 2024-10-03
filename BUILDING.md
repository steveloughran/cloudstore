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

With maven, with profiles for AWS java v1 and v2 SDK.

To build a production release
1. Use java8
2. compile against a shipping hadoop version (see the profiles)
3. Use with `-Pextra` for the AWS v1 SDK integration
4. Build with `-Psdk2` for the aws sdk v2.

V1 SDK build
```bash
mvn clean install -Pextra
```

V2 SDK build
```bash
mvn clean install -Psdk2
```

Joint build
```bash
mvn clean install -Pextra && mvn install -Psdk2
```

## Releasing

To publish the release use the gui or the github command line through the `fish` shell.

```bash
set -gx now (date '+%Y-%m-%d-%H.%M'); echo [$now]
git add .; git status
git commit -S --allow-empty -m "release $now"; git push
gh release create tag-release-$now -t release-$now -n "release of $now" -d target/cloudstore-1.0.jar
# then go to the web ui to review and finalize the release
```

* If a new release is made the same day, remember to create a new tag.
* The version `cloudstore-1.0.jar` is always used, not just from laziness but because it allows
for bash scripts to always be able to fetch the latest version through curl then execute it.


