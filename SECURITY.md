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

# Security

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL
NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED",  "MAY", and
"OPTIONAL" in this document are to be interpreted as described in
RFC 2119.

## Before filing a report (including AI-assisted reports)

Cloudstore is a diagnostics CLI invoked manually by a cluster operator via
`hadoop jar`.
It is not a service, accepts no untrusted network input other than filenames, and the
operator running it is trusted. Many findings that look like vulnerabilities
in a server context are not vulnerabilities here.

You *MUST NOT* file a report for:

- Issues that require the operator to pass malicious arguments to their own
  invocation, edit their own Hadoop site configuration, or place malicious
  files on their own classpath.
- Issues that require the attacker to have access to your hard disk or already
  possess credentials to access the cluster/remote storage.
- Output of `sessionkeys` or `gcscreds` containing credentials — these
  commands exist to print credentials.
- Output of any command run with `-debug` or `-verbose` containing secrets —
  documented behaviour; file a regular bug, not a security report.
- `storediag` revealing bucket names, endpoints, regions, or non-secret
  configuration — that is the command's purpose.
- Transitive CVEs in Hadoop, the AWS SDK, the GCS connector, or other
  `provided`-scope dependencies — cloudstore does not ship them; they come
  from the host installation
- CVEs in remote stores interacted with.
- Scanner output (Snyk, Dependabot, Trivy, Zizmor, etc.) without a working
- reproducer against the current `main` branch.
- Relaxed or introspective TLS behaviour in `tlsinfo` or `storediag` — these
  commands exist to diagnose TLS configuration.
- Theoretical findings ("an attacker who could X might Y") without a
  reproduction.

AI-assisted reports are accepted only if the submitter has verified the
finding by hand against current source and includes a runnable reproducer
running as a non-root user.

*Unverified LLM-generated reports waste maintainer time and will be closed
without further response.*

A valid report includes: 
- the cloudstore version (git SHA).
- the exact command run.
- endpoint information.
- the observed credential leak or other in-scope failure, and what was expected instead.

## Reporting a bug in cloudstore

Report security bugs in cloudstore to security@hadoop.apache.org

## Reporting a bug in a third-party module

Security bugs in third-party modules should be reported to their respective
maintainers.

## The Cloudstore threat model

In the Cloudstore threat model, there are trusted elements such as the
underlying operating system. Vulnerabilities that require the compromise
of these trusted elements are outside the scope of the cloudstore threat
model.

* The cloudstore application may be deployed in production environments to
  execute operations including `storediag`, `bandwidth`, `cloudup` and more.
* It is executed by hand at a console on a host system connected to the cluster, or on a standalone system
  with credentials to access the target store.
* The output of these commands may be collected into a text file which is then passed
  to internal or third-party support teams assisting in troubleshooting cloud connectivity and performance issues.
  These are individuals who may be employed by separate companies, and who are not to be
  given access to the stores. That is: they MUST NOT see credentials.
* Storediag output is not expected to be attached to public issue reports, as they leak information about
  cluster configuration, which would assist in malicious cluster reconnaissance.
* Some commands `sessionkeys`, `gcscreds` do log secrets.

### Deployment Threat Model

Storediag is designed to be run by users without elevated privileges on a single host,
either standalone or within/adjacent to a hadoop cluster.

- The user is a normal unprivileged account; they have read/write access to part of the filesystem,
including where temporary files are created by stores, and any paths in the local fs passed as parameters.
- Input files shall be read as that user, relying on OS protection.
- Paths provided for output shall be written by that user, relying on OS protection.
- There may or may not be checks on overwriting output files; report a normal bug if this an issue.

The user issues commands to examine the local fs, local hadoop configuration and perform operations against them, such as uploading files to remote stores, debugging connectivity issues or measuring bandwidth.

For remote access the user will require access to the remote store
- If it is an HDFS cluster where security is disabled, the user has full read/write access to the store,
  this is inherent in security being disabled. Note: this is the default deployment standalone and
  often the configuration used in transient cloud clusters where the entire DFS is for the single user,
  short-lived, and protected from the rest of the network through firewall mechanisms.
- For cloud storage, authentication may come in a number of ways
    - host environment. For example AWS IAM. Here the cloud credentials are available to all.
    - hadoop configuration files, cluster wide or local
    - JCEKS files, local or in a filesystem whose schema does not match that of the store for which authentication is required. These SHOULD have passwords, but MAY NOT.
    - authentication mechanisms picked by any underlying SDK. For example the AWS SDK examines environment
      variables and configuration files under ` ~/.aws`.
It is not a security issue if storediag can access cloud storage with credentials provided to it.
It is a security issue in the storediag code if the credentials are logged other than in conditions previously listed (`-debug` mode and specific commands).
If credential logging is performed within the storediag code, it is something to report, as it is the classic [CWE-532](https://cwe.mitre.org/data/definitions/532.html) : Insertion of Sensitive Information into Log File.
If it is observed within the hadoop codebase, it is potentially more significant, so do report it to security@hadoop.apache.org, *provided the issue can be replicated with a build of the latest commit of hadoop trunk.
If it is observed from within third party logs, file through their security reporting mechanisms, after verifying that
    - the issue is in scope of their threat model and can be replicated with their latest release.

### Development Environment Threat Model

The project is built on developer systems, and in CI systems.

* All external PRs SHALL be considered untrusted; their inputs MUST NOT be fed directly or indirectly to shell commands without sanitization.
* Upstream dependencies from non ASF-projects MAY be subverted by malicious attacks; a cooldown period of at least 72 hours SHALL be kept before manual or automated dependency update.
* ASF projects SHALL be considered trusted as their manual upvote release process includes an implicit buffer and defense against package
  ecosystem worms.
* Components such as maven plugins execute code on developer systems. Their security MUST be evaluated before adoption.
* Third-party libraries which production code compiles against may be executed during testing. Their security MUST be evaluated.
* Some developers may use VS.Code. This IDE has a notion of [a trusted workspace](https://code.visualstudio.com/docs/editing/workspaces/workspace-trust), which allows for files in the directory tree to declare executables, files such as `.env' and `tasks.json`. These files must be considered sensitive.
* 
The CI build output is publicly visible, so the threat model includes unobfuscated logging of any cloud credentials provided by CI runs, or leakage of other secrets.

The threat model includes the risk of subverted github actions and build tooling.
* All inputs from pull requests, including titles, comments, authors and code SHALL be considered untrusted.
* Any PR which adds VS.code specific mechanisms to execute code SHALL be rejected.
* PRs which modify the maven pom.xml file or tests under `src/test/java` SHALL be audited for security risks.
* Git checksum references MUST be made to GitHub actions, rather than tags; include the version as a comment so dependabot will track and maintain them.
* [Zizmor](https://zizmor.sh/) SHALL be used to audit GHAs.
* Github Action triggers on PRs MUST NOT be triggers which provide unrestricted github tokens to the actions.
   For example, there MUST NOT be `pull_request_target`, `workflow_run`, or `issue_comment` triggers. 
* Github Actions SHALL follow GitHub's [secure use](https://docs.github.com/en/actions/reference/security/secure-use) guidelines, and in particular use [Intermediate Environment Variables](https://docs.github.com/en/actions/reference/security/secure-use#use-an-intermediate-environment-variable) to safely process untrusted inputs.




## Not in the Threat Model

* Any attack which requires the user to manually enter credentials or configuration options.
* Vulnerabilities in dependencies.
* Any attack which requires the local operating system to be configured in an unusual configuration/misconfigured as a step in the process.
* Any attack which requires a hadoop site reconfiguration.
* Any attack which requires the download and execution of external binaries.
* Any attack contains an assumption about artifacts on the classpath which is not true in production systems (example, spring jars).
* Any attack which grants more privileges accessing a remote cloud store/hdfs than the client has through the account credentials is has and/or the kerberos principal.
* Any attack which accesses arbitrary data against an HDFS cluster for which kerberos is not enabled
* Any attack against cloud storage where the credentials are supplied by the VM/container within which cloudstore is being executed.
* Any report of credential printing in `sessionkeys`: it generates then prints session keys of limited duration.
* Any report of credential printing in `gcscreds`: it prints GCS credentials as part of a diagnostics process.
* Detailed printing of http communications between client and store when enabled.
* Any vulnerability which cannot be reproduced in the latest version of the `main` branch.
* Any vulnerability when running on a version of hadoop older than the latest release and which cannot be reproduced on a build of hadoop trunk branch.
* Bugs in the test runs which do not leak secrets. File as normal bugs.

## Do report

- Any logging of secrets other than of * and length in storediag operations when `-reveal` is not passed in.
- Any incompletely unobfuscated logging of secrets in storediag operations when `-reveal` is passed in.
- Any unobfuscated logging of credentials in test runs.

Note that some low level `-debug` options may print secrets; issues reported here should be considered bugs rather than security issues.

## Scanner Calibration Rules

A scanner should treat a finding as higher-confidence only if
it plausibly shows one of the following:

- Exposure of a secret or delegated credential to a new audience
- CREATION of a new unauthorized capability in an component owned by this project.
- The existence of this on HEAD of the branch `main`

A finding should be downgraded or rejected by default if it instead depends
primarily on:

- Malformed-input robustness or denial-of-service behavior
- A malicious catalog, metastore, or external service.
- A principal that already has equivalent power through legitimate write or maintenance capabilities.
- A vulnerability that only exists in previous releases.
