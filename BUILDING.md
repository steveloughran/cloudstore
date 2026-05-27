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
1. Compile on a JDK17 JVM; the output is still generated for java 8 JVMs.
2. Compile against a shipping hadoop version (see the profiles).


```bash
mvn spotless:apply              # auto-format Java sources to palantir-java-format
mvn clean install               # compile + unit tests + jar
mvn clean verify                # adds: ITest*, apache-rat:check, spotless:check
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

For a long time the artifact version was fixed at 1.0 so that curl and other
tools could fetch a stable URL. That was convenient but produced a different
problem: support tickets kept attaching the same `cloudstore-<old>.jar`
long after new releases had shipped, with no version change to make the staleness
visible. Release number increments are now required for anything other than
a rapid-iteration multiple-releases-in-a-day workflow.

The project follows the conventional Maven SNAPSHOT lifecycle:

| Phase                     | pom `<version>`    | docs (`cloudstore-X.Y.jar`) |
|---------------------------|--------------------|-----------------------------|
| Day-to-day development    | `X.Y-SNAPSHOT`     | last released `X.Y`         |
| Cutting release `X.Y`     | `X.Y`              | `X.Y`                       |
| Immediately after release | `X.(Y+1)-SNAPSHOT` | `X.Y`                       |

`-SNAPSHOT` appears in the pom only; the published artifact and every
documented `cloudstore-X.Y.jar` reference is always a bare release form.

Two scripts in `dev-support/` keep these in sync:


**`bump-version.sh <new-version>`**

Bumps `pom.xml`'s `<version>` only.
Accepts either `X.Y-SNAPSHOT` (for development bumps) or `X.Y`
(when cutting a release).
Also rewrites `BUILDING.md`'s `set -gx ver <v>` line so the release command block
below stays in step (the line always reflects the bare release
version — a trailing `-SNAPSHOT` is stripped before substituting).
Does *not* touch `README.md`, `AGENTS.md`, or `src/site/markdown/*`.

**`update-site-docs.sh <new-version>`**
Updates site documentation for releases.
Rewrites every `cloudstore-<old>.jar` /
  `cloudstore-<old>-cyclonedx` reference in `README.md`, `AGENTS.md`,
  `BUILDING.md`, and `src/site/markdown/*.md`, and bumps the
  `<cloudstore.docs.version>` property in `pom.xml`.
Rejects attempts to switch to a `-SNAPSHOT`; SNAPSHOT artifacts are not released and so not documented.

The `verify` phase enforces this: `dev-support/check-doc-versions.sh`
runs as part of `mvn verify` and fails the build if any markdown doc
references a `cloudstore-X.Y.jar` that disagrees with
`${cloudstore.docs.version}` (independent of `${project.version}`, so
SNAPSHOT pom states do not trip the gate).

### Worked example: cutting release 1.4 then returning to dev

```bash
# 1. Cut the release: pom 1.4-SNAPSHOT -> 1.4, site docs 1.3 -> 1.4.
dev-support/bump-version.sh 1.4
dev-support/update-site-docs.sh 1.4
# (build, tag, publish — see "Releasing" below.)

# 2. Back to development: pom 1.4 -> 1.5-SNAPSHOT. Docs stay at 1.4.
dev-support/bump-version.sh 1.5-SNAPSHOT
```


## Releasing

To publish the release use the github command line through the `fish` shell, with a final
git UI interaction.

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

Commands (for fish)
```bash
set -gx ver 1.3                           # bumped by dev-support/bump-version.sh (always release form, no -SNAPSHOT)
mvn clean install -Prelease,sign -DskipTests
set -gx now (date '+%Y-%m-%d-%H.%M'); echo [$now]
git commit -S --allow-empty -m "release $now"; git push
gh release create tag-release-$now -t release-$now -n "release of $now" -d \
    target/cloudstore-$ver.jar \
    target/cloudstore-$ver.jar.asc \
    target/cloudstore-$ver-cyclonedx.json \
    target/cloudstore-$ver-cyclonedx.json.asc \
    target/cloudstore-$ver-cyclonedx.xml \
    target/cloudstore-$ver-cyclonedx.xml.asc \
    LICENSE-binary \
    NOTICE-binary
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

1. An OpenPGP key in the Hadoop committer KEYS file.
2. `gpg-agent` running with the release key unlocked. A quick way to
   warm the agent before the build is:
   `echo test | gpg --clearsign -u <keyid> > /dev/null`.

Flags:

- `-Dgpg.keyName=<keyid>` — pick a specific key. If unset, gpg's
  default secret key is used.
- `-Dgpg.skip=true` — disable signing even when `-Psign` is active.

`-Prelease` on its own (without `sign`) still produces a valid jar and
SBOM — useful for local smoke tests on machines without the release
key.

Verify a downloaded release:

```bash
gpg --verify target/cloudstore-$ver.jar.asc target/cloudstore-$ver.jar
```

The `gh release create` command above attaches the jar, the SBOM
(JSON + XML), and each of their `.asc` signatures in one shot. For an
already-published release, append signatures with `gh release upload`:

```bash
gh release upload tag-release-$now \
    target/cloudstore-$ver.jar.asc \
    target/cloudstore-$ver-cyclonedx.json.asc \
    target/cloudstore-$ver-cyclonedx.xml.asc
```

## How to bypass buildnumber checks

```bash
mvn clean install -Prelease -DskipTests -Dbuildnumber.check=false -Dbuildnumber.update=false
```


