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


# Hadoop versions

There's a dependency on hadoop 3.4.2 to be compatible with its avro version and to
have non-reflective access to the bulk delete API.
You should still code targeting hadoop 3.4.0 as the minimum version *outside these specific
cli commands*

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

## Testing

There are unit tests for store operations against the local fs and a mini hdfs cluster.

There are Integration Tests which run with the `mvn verify` command, currently against S3 stores only.
The tests are minimal, any basic S3 implementation will suffice.

To bind to a store, follow the [testing s3a](https://hadoop.apache.org/docs/stable/hadoop-aws/tools/hadoop-aws/testing.html#File_auth-keys.xml) docs.

## Updating cloudstore release versions

For a long time the version was fixed at 1.0 so that curl and other tools could retrieve it
This was convenient for some use cases, but has led to a condition where the JAR downloaded
for support calls was never updated, even after new releases were made, because without
a version change this wasn't apparent.

Therefore release number increments are required for anything other than a rapid-iteration multiple-releases-in-a-day workflow.

Update the version, for example from 1.0 to 1.1:
```bash
dev-support/bump-version.sh 1.1
```

This wraps `mvn versions:set -DnewVersion=1.1` and rewrites every
`cloudstore-<old>.jar` reference in `README.md`, `AGENTS.md`, `BUILDING.md`,
and `src/site/markdown/*.md` to the new version.

The `verify` phase enforces this: `dev-support/check-doc-versions.sh` runs
as part of `mvn verify` and fails the build if any markdown doc references
a `cloudstore-X.Y.jar` that disagrees with `${project.version}`.

*Note:* there's currently no use of the `-SNAPSHOT` suffix, used in downstream builds for the tools
to recognise this should be updated nightly.
This artifact is not currently intended for such use.


## Releasing

To publish the release use the gui or the github command line through the `fish` shell.

Release builds activate the `release` profile, which (a) enforces a clean git
tree via `buildnumber-maven-plugin` and (b) emits a CycloneDX SBOM next to the
jar:

- `target/cloudstore-<version>-cyclonedx.json`
- `target/cloudstore-<version>-cyclonedx.xml`

The SBOM is compile-scope only — `provided` deps (Hadoop, AWS SDK v2, GCS
connector) are not in it because they are not shipped in the jar.

It also generates a build version, with the buildnumber plugin.
On release builds, this will fail the build if there are uncommitted changes.

Commit all changes before starting a release build.

```bash
mvn clean install -Prelease,sign -DskipTests
set -gx now (date '+%Y-%m-%d-%H.%M'); echo [$now]
git commit -S --allow-empty -m "release $now"; git push
gh release create tag-release-$now -t release-$now -n "release of $now" -d \
    target/cloudstore-1.3.jar \
    target/cloudstore-1.3.jar.asc \
    target/cloudstore-1.2-cyclonedx.json \
    target/cloudstore-1.2-cyclonedx.json.asc \
    target/cloudstore-1.2-cyclonedx.xml \
    target/cloudstore-1.2-cyclonedx.xml.asc
# then go to the web ui to review and finalize the release
```

* If a new release is made the same day, remember to create a new tag.
* If you have an env var pointing to the cloudstore JAR, update it!

## Signing release artifacts

Activate the `sign` profile alongside `release` (as in the command
above) to GPG-sign every attached artifact. The plugin produces a
detached `.asc` next to each of:

- `target/cloudstore-<version>.jar`
- `target/cloudstore-<version>.pom`
- `target/cloudstore-<version>-cyclonedx.json`
- `target/cloudstore-<version>-cyclonedx.xml`

Prerequisites:

1. A published OpenPGP key.
2. `gpg-agent` running with the release key unlocked. A quick way to
   warm the agent before the build is:
   `echo test | gpg --clearsign -u <keyid> > /dev/null`.

Flags:

- `-Dgpg.keyName=<keyid>` — pick a specific key. If unset, gpg's
  default secret key is used.
- `-Dgpg.skip=true` — disable signing even when `-Psign` is active
  (rarely useful since the profile is already opt-in).

`-Prelease` on its own (without `sign`) still produces a valid jar and
SBOM — useful for local smoke tests on machines without the release
key.

Verify a downloaded release:

```bash
gpg --verify target/cloudstore-1.3.jar.asc target/cloudstore-1.3.jar
```

The `gh release create` command above attaches the jar, the SBOM
(JSON + XML), and each of their `.asc` signatures in one shot. For an
already-published release, append signatures with `gh release upload`:

```bash
gh release upload tag-release-$now \
    target/cloudstore-1.3.jar.asc \
    target/cloudstore-1.2-cyclonedx.json.asc \
    target/cloudstore-1.2-cyclonedx.xml.asc
```

Use `--clobber` to overwrite an existing asset of the same name.

## How to bypass buildnumber checks

```bash
mvn clean install -Prelease -DskipTests -Dbuildnumber.check=false -Dbuildnumber.update=false
```


