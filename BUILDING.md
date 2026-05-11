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
1. Use java8 for the *bytecode* (`source`/`target` are pinned to 1.8 by the compiler plugin and by the enforcer plugin's lower bound).
2. Run the build under a JDK that the toolchain supports — JDK 11+ is required for `spotless:apply` because palantir-java-format pulls in JDK 11 APIs at format time.
3. Compile against a shipping hadoop version (see the profiles).


```bash
mvn clean install               # compile + unit tests + jar
mvn clean verify                # adds: ITest*, apache-rat:check, spotless:check
mvn spotless:apply              # auto-format Java sources to palantir-java-format
mvn org.apache.rat:apache-rat-plugin:check    # license-header audit only
mvn site                        # render src/site → target/site (fluido skin)
```

The `verify` lifecycle is the one CI runs. To run cloud-backed contract tests
opt in via profile + credentials in `src/test/resources/auth-keys.xml`:

# Hadoop versions

There's a dependency on hadoop 3.4.2 to be compatible with its avro version and to
have non-reflective access to the bulk delete API.
You should still code targeting hadoop 3.4.0 as the minimum version *outside these specific
cli commands*


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

Search and replace all uses in markdown files of `cloudstore-X.Y.jar` (where `X.Y` is the previous version) with the new version of the artifact.

*Note:* there's currently no use of the `-SNAPSHOT` suffix, used in downstream builds for the tools
to recognise this should be updated nightly.
This artifact is not currently intended for such use.


## Releasing

To publish the release use the gui or the github command line through the `fish` shell.

```bash
set -gx now (date '+%Y-%m-%d-%H.%M'); echo [$now]
git add .; git status
git commit -S --allow-empty -m "release $now"; git push
gh release create tag-release-$now -t release-$now -n "release of $now" -d target/cloudstore-1.2.jar
# then go to the web ui to review and finalize the release
```

* If a new release is made the same day, remember to create a new tag.
* If you have an env var pointing to the cloudstore JAR, update it!




