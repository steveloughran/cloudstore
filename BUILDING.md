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

## Compiling

With maven

To build a production release
1. Use java8
2. compile against a shipping hadoop version (see the profiles)

Joint build
```bash
mvn clean install
```
## Updating cloudstore release versions

For a long time the version was fixed at 1.0 so that curl and other tools could retrieve it
This was convenient for some use cases, but has led to a condition where the JAR downloaded
for support calls was never updated, even after new releases were made, because without
a version change this wasn't apparent.

Therefore release number increments are required for anything other than a rapid-iteration multiple-releases-in-a-day workflow.

Update the version, for example from 1.0 to 1.1:
```bash
mvn versions:set -DnewVersion=1.1
```

Search and replace all uses of `cloudstore-1.0.jar` with the new version of the artifact.

*Note:* there's currently no use of the `-SNAPSHOT` suffix, used in downstream builds for the tools
to recognise this should be updated nightly.
This artifact is not currently intended for such use.


## Releasing

To publish the release use the gui or the github command line through the `fish` shell.

```bash
# Make sure it is java 8
java -version

# actual build
mvn clean install
set -gx now (date '+%Y-%m-%d-%H.%M'); echo [$now]
git add .; git status
git commit -S --allow-empty -m "release $now"; git push
gh release create tag-release-$now -t release-$now -n "release of $now" -d target/cloudstore-1.1.jar
# then go to the web ui to review and finalize the release
```

* If a new release is made the same day, remember to create a new tag.
* If you have an env var pointing to the cloudstore JAR, update it!




