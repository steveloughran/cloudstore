<!---
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License. See accompanying LICENSE file.
-->


This file governs AI-assisted work on the Apache Hadoop Cloudstore codebase.


## Project

Cloudstore is a Java 8 / Maven diagnostics CLI bundled as a single JAR and invoked via `hadoop jar`. It sits on top of the Apache Hadoop FileSystem APIs and adds tools for troubleshooting cloud connectors (S3A, ABFS, ADL, GCS) — classpath/credential/network checks, faster listings, bandwidth probes, AWS V2 SDK probes, etc. It is intentionally not part of the Hadoop release: agile cadence, lighter testing, and the freedom to compile against multiple Hadoop versions are explicit goals.

All source lives under `org.apache.hadoop.fs.*` deliberately, so it can call package-private Hadoop APIs.

### Key Technologies

*   **Language:** Java 8
*   **Build:** Apache Maven
*   **Core Dependencies:**
    *   Apache Hadoop (various modules: `hadoop-client`, `hadoop-common`, `hadoop-cloud-storage`)
    *   AWS SDK for Java 2.x
    *   Google Cloud Storage Connector

Hadoop, AWS SDK v2, and the GCS connector are all `provided` scope — the JAR is designed to be dropped onto an existing Hadoop install.

## Build and run

See [BUILDING.md](./BUILDING.md).

Bytecode is pinned to Java 8 (compiler plugin + enforcer rule `[1.8,)`). The build itself runs fine on JDK 11+, and **needs** JDK 11+ if you invoke `spotless:apply` (palantir-java-format requires it).

```bash
mvn clean install                          # compile + unit tests + jar (default: hadoop.version=3.4.0)
mvn clean verify                           # adds: ITest*, apache-rat:check, spotless:check
mvn install -Pnext                         # next hadoop release
mvn install -Dtrunk                        # trunk p;roifle
mvn install -P snapshots-and-staging       # adds ASF staging/snapshot repos

mvn spotless:apply                         # auto-format Java to palantir-java-format
mvn org.apache.rat:apache-rat-plugin:check # license-header audit only
mvn site                                   # render src/site → target/site (fluido skin)
```

The active build wires `apache-rat-plugin`, `spotless-maven-plugin`, and `maven-failsafe-plugin` into the `verify` phase. RAT and Spotless will fail the build if a newly added file is missing the ASF header or hasn't been run through `spotless:apply`.


Releasing is manual and tag-driven — see `BUILDING.md`. Bump the version with `mvn versions:set -DnewVersion=…` *and* search-and-replace `cloudstore-<old>.jar` references; the `-SNAPSHOT` suffix is intentionally never used.

## Running Commands

The tools are executed via the `hadoop jar` command. The general syntax is:

```sh
hadoop jar target/cloudstore-1.2.jar <command> [options] <arguments...>
```

-   `<command>`: The name of the tool to run (e.g., `list`, `dux`, `storediag`).
-   `[options]`: Common options include:
    -   `-D <key=value>`: Define a Hadoop configuration property.
    -   `-xmlfile <file>`: Load a Hadoop configuration XML file.
    -   `-verbose`: Enable verbose output.
    -   `-debug`: Enable low-level debug logging for the JVM and connectors.

**Example: Listing files in an S3 bucket**

```sh
hadoop jar target/cloudstore-1.2.jar list -limit 10 s3a://my-bucket/path/
```


## Tests

JUnit 4 + AssertJ. Convention drives which runner picks up a class:

- `Test*` — unit tests, run by surefire under `mvn test` / `mvn install`.
- `ITest*` — integration tests, run by failsafe under `mvn verify` (forked, 600s timeout, `reuseForks=false`).

Single test / single method:

```bash
mvn test -Dtest=TestConstval
mvn test -Dtest=TestConstval#methodName
mvn verify -Dit.test=ITestLocalStorediagContract
```

Most tests are local; cloud-touching integration tests need credentials supplied via `src/test/resources/auth-keys.xml` (gitignored). Storediag-specific contract tests live under `org.apache.hadoop.fs.store.contract` and extend `AbstractFSContractTestBase`. The S3A contract test (`ITestS3AStorediagContract`) is gated by the `s3a-it` profile because the `hadoop-aws` test-jar is not consistently published to Maven Central.

## Architecture

### Two-layer command dispatch

Each user-facing command has two classes:

1. A short-name shim in the **default package**, e.g. `src/main/java/dux.java`. It is just `public static void main(String[] args) { ExtendedDu.main(args); }` plus a `help()` method called from `help.java`. This is what `hadoop jar … dux` resolves to — `hadoop jar` uses the bare class name as the entry point, and Java's default package makes it short.
2. The real implementation under `org.apache.hadoop.fs.store.commands.*` (or `…s3a.sdk*`, `…store.abfs`, `…store.audit`, `…tools.cloudup`, `…tools.csv`, `…gs`).

When adding a new command: create the implementation class extending `StoreEntryPoint`, add a default-package shim, and register it in `help.java` (alphabetically, in the right section — note the AWS V2 SDK commands are listed separately because they require S3A's V2 SDK at runtime).

### Entry-point hierarchy

- `org.apache.hadoop.fs.store.StoreEntryPoint` — `Configured implements Tool, Closeable, Printout`. Provides argument parsing for the common options (`-D`, `-xmlfile`, `-tokenfile`, `-verbose`, `-debug`, `-sysprops`, `-logoverrides`), token loading, log-level overrides, and `Printout` helpers used by diagnostics. Standard option keys live in `CommonParameters`.
- `org.apache.hadoop.fs.store.diag.DiagnosticsEntryPoint extends StoreEntryPoint` — extra helpers shared by diagnostics commands (`storediag`, `constval`, `tlsinfo`, etc.).

Each implementation class follows the same pattern: `createCommandFormat(min, max)` in the constructor, `run(String[] args)` does the work, plus a `static int exec(String...)` that delegates to `ToolRunner.run` and a `static void main(...)` that calls `exec` and exits.

### Per-store diagnostics

`org.apache.hadoop.fs.store.diag.StoreDiagnosticsInfo` is the polymorphism point for `storediag`. Subclasses describe how to inspect a particular connector: `S3ADiagnosticsInfo`, `ABFSDiagnosticsInfo` (note the `Abfs…` filename), `ADLDiagnosticsInfo`, `GCSDiagnosticsInfo`, `HDFSDiagnosticsInfo`, `WasbDiagnosticsInfo`, `HBossConstants`, plus a `TemplateDiagnosticsInfo` to copy when adding a new store. Each contributes the option keys to print, environment variables to surface, classpath probes, and so on.

### Generated sources

Avro schemas under `src/main/avro` are compiled into `target/generated-sources/avro` during `generate-sources`. The output is added to the compile source roots automatically. The `auditlogs` command uses these.

## Development Conventions

- Logging is SLF4J. Many entry points also write to `System.out` directly (with `@SuppressWarnings("UseOfSystemOutOrSystemErr")`) because the output is meant for humans running the tool.
- Configuration flows through Hadoop's `Configuration`. Do not add a separate config system.
- Don't introduce dependencies that aren't `provided` or `test` — the JAR has to stay slim because it's deployed onto whatever Hadoop install the user already has.

### Configuration

The project uses the standard Hadoop `Configuration` framework. Configuration can be supplied through:
1.  Default Hadoop configuration files (`core-site.xml`, `hdfs-site.xml`, etc.).
2.  Custom XML files specified with the `-xmlfile` option.
3.  Individual properties set with the `-D` flag.


### Testing

-   Unit tests are located in `src/test/java`.
-   Tests are run as part of the `mvn clean install` build process.
-   The project uses JUnit for testing.
-   In tests, always use assertJ assertions instead of junit asserts

### Security

See [SECURITY.MD](./SECURITY.md) for details.

- Review all changes to see if they introduce security issues, especially with logging secrets.


## ASF Legal Compliance (Third-Party Code)

This is an [Apache Software Foundation (ASF)](https://www.apache.org/) project released under the **Apache License 2.0**.
The AI **must** actively enforce and monitor ASF licensing policy:

- **Proactively flag conflicts**: Before introducing any dependency, snippet, or code derived from an external source, verify its license is compatible with Apache 2.0.
  Incompatible licenses include (non-exhaustive): GPL, AGPL, SSPL, BUSL, CC-BY-NC.
  Compatible examples: MIT, BSD-2/3, Apache 2.0, ISC, MPL 2.0 (with caveats).
- **Category X / Category A**: Follow the [ASF Third-Party Licensing Policy](https://www.apache.org/legal/resolved.html).
  Category A licenses may be included; Category X licenses must **never** be introduced.
- **Update `LICENSE` and `NOTICE`**: When adding third-party code or binaries that require attribution, add the appropriate notices to `LICENSE` and/or `NOTICE` following the [ASF guide on licenses and notices](https://www.apache.org/dev/licensing-howto.html).
  If in doubt whether an entry is required, **add it and flag it in the PR description** for committer review.
- **Generative AI output**: The [ASF Generative Tooling Guidance](https://www.apache.org/legal/generative-tooling.html) applies. Be aware that AI-generated code may unintentionally reproduce copyrighted material. Flag any non-trivial generated blocks in commit messages or PR descriptions.

## 3. Pull Request Requirements

Follow [`CONTRIBUTING.md`](./CONTRIBUTING.md) in full. Key points:

- One commit per issue (squash before submitting).
- All significant changes need a JIRA ticket.
- Provide tests for every submitted change.
- Verify coding standards: `make style`.
- Branch name convention: use the JIRA ticket ID, e.g. `THRIFT-9999`.
- PRs go from your fork branch → `apache:master`.

---

## 4. AI-Generated Contributions

Per [`CONTRIBUTING.md § AI generated content`](CONTRIBUTING.md#ai-generated-content) and the [ASF Generative Tooling Guidance](https://www.apache.org/legal/generative-tooling.html):

- **Always** label AI-assisted commits and PRs. Use one or both of:
  ```
  Co-Authored-By: <AI tool name and version>
  Generated-by: <AI tool name and version>
  ```
  Example:
  ```
  HADOOP-9999: Fix connection timeout

  Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
  ```
- Apply this label even when AI only generated a portion of the change.
- The human author remains responsible for reviewing, testing, and standing behind all submitted code.
