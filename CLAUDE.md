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

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Cloudstore is a Java 8 / Maven diagnostics CLI bundled as a single JAR and invoked via `hadoop jar`. It sits on top of the Apache Hadoop FileSystem APIs and adds tools for troubleshooting cloud connectors (S3A, ABFS, ADL, GCS) — classpath/credential/network checks, faster listings, bandwidth probes, AWS V2 SDK probes, etc. It is intentionally not part of the Hadoop release: agile cadence, lighter testing, and the freedom to compile against multiple Hadoop versions are explicit goals.

All source lives under `org.apache.hadoop.fs.*` deliberately, so it can call package-private Hadoop APIs.

## Build and run

Bytecode is pinned to Java 8 (compiler plugin + enforcer rule `[1.8,)`). The build itself runs fine on JDK 11+, and **needs** JDK 11+ if you invoke `spotless:apply` (palantir-java-format requires it).

```bash
mvn clean install                          # compile + unit tests + jar (default: hadoop.version=3.4.0)
mvn clean verify                           # adds: ITest*, apache-rat:check, spotless:check
mvn install -Pnext                         # next hadoop release
mvn install -Dtrunk                        # trunk p;roifle
mvn install -P7.3.2                        # CDH-shaped 3.4.1.7.3.2.0-SNAPSHOT
mvn install -Dgcs                          # adds src/main/extra to source roots
mvn install -Ds3a-it                       # adds hadoop-aws test-jar + ITestS3AStorediagContract
mvn install -P snapshots-and-staging       # adds ASF staging/snapshot repos

mvn spotless:apply                         # auto-format Java to palantir-java-format
mvn org.apache.rat:apache-rat-plugin:check # license-header audit only
mvn site                                   # render src/site → target/site (fluido skin)
```

The active build wires `apache-rat-plugin`, `spotless-maven-plugin`, and `maven-failsafe-plugin` into the `verify` phase. RAT and Spotless will fail the build if a newly added file is missing the ASF header or hasn't been run through `spotless:apply`.

Hadoop, AWS SDK v2, and the GCS connector are all `provided` scope — the JAR is designed to be dropped onto an existing Hadoop install.

Run a command:

```bash
hadoop jar target/cloudstore-1.1.jar <command> [options] <args...>
hadoop jar target/cloudstore-1.1.jar help        # lists every command
```

Releasing is manual and tag-driven — see `BUILDING.md`. Bump the version with `mvn versions:set -DnewVersion=…` *and* search-and-replace `cloudstore-<old>.jar` references; the `-SNAPSHOT` suffix is intentionally never used.

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
- `org.apache.hadoop.fs.store.diag.DiagnosticsEntryPoint extends StoreEntryPoint` — extra helpers shared by diagnostics commands (`storediag`, `constval`, `tlsinfo`, `tarhardened`, etc.).

Each implementation class follows the same pattern: `createCommandFormat(min, max)` in the constructor, `run(String[] args)` does the work, plus a `static int exec(String...)` that delegates to `ToolRunner.run` and a `static void main(...)` that calls `exec` and exits.

### Per-store diagnostics

`org.apache.hadoop.fs.store.diag.StoreDiagnosticsInfo` is the polymorphism point for `storediag`. Subclasses describe how to inspect a particular connector: `S3ADiagnosticsInfo`, `ABFSDiagnosticsInfo` (note the `Abfs…` filename), `ADLDiagnosticsInfo`, `GCSDiagnosticsInfo`, `HDFSDiagnosticsInfo`, `WasbDiagnosticsInfo`, `HBossConstants`, plus a `TemplateDiagnosticsInfo` to copy when adding a new store. Each contributes the option keys to print, environment variables to surface, classpath probes, and so on.

### Generated sources

Avro schemas under `src/main/avro` are compiled into `target/generated-sources/avro` during `generate-sources`. The output is added to the compile source roots automatically. The `auditlogs` command uses these.

## Conventions

- Logging is SLF4J. Many entry points also write to `System.out` directly (with `@SuppressWarnings("UseOfSystemOutOrSystemErr")`) because the output is meant for humans running the tool.
- Configuration flows through Hadoop's `Configuration`. Do not add a separate config system.
- Don't introduce dependencies that aren't `provided` or `test` — the JAR has to stay slim because it's deployed onto whatever Hadoop install the user already has.
