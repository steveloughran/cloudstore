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

The project is built on developer systems, and in CI systems.
The threat model includes the risk of subverted github actions
* git checksum references MUST be made to GitHub actions, rather than tags; include the version as a comment so dependabot will track and maintain them.
* [Zizmor](https://zizmor.sh/) SHALL be used to audit GHAs.
* GHA triggers on PRs MUST NOT be triggers which provide unrestricted github tokens to the actions.
   For example, there MUST NOT be `pull_request_target`, `workflow_run`, or `issue_comment` triggers. 
* Github Actions SHALL follow GitHub's [secure use](https://docs.github.com/en/actions/reference/security/secure-use) guidelines, and in particular use [Intermediate Environment Variables](https://docs.github.com/en/actions/reference/security/secure-use#use-an-intermediate-environment-variable) to safely process untrusted inputs.
* All inputs from pull requests, including titles, comments, authors and code SHALL be considered untrusted.

The CI build output will be publicly visible, so the threat model includes
- unobfuscated logging of any cloud credentials provided by CI runs.
  

## Not in the Threat Model

* Any attack which requires the user to manually enter credentials or configuration options.
* Vulnerabilities in dependencies.
* Any attack which requires the local operating system to be configured in an unusual configuration/misconfigured as a step in the process.
* Any attack which requires a hadoop site reconfiguration.
* Any attack which requires the download and execution of external binaries.
* Any attack contains an assumption about artifacts on the classpath which is not true in production systems (example, spring jars).
* `sessionkeys`: it generates then prints session keys of limited duration.
* `gcscreds`: it prints GCS credentials as part of a diagnostics process.
* Detailed printing of http communications between client and store when enabled.
* Any vulnerability reported against an older release of cloudstore which cannot be reproduced in the latest version of the `main` branch.
* Any vulnerability when running on a version of hadoop older than the latest apache hadoop release and which cannot be reproduced on a build of apache trunk.
* Bugs in the test runs which do not leak secrets.

## Do report

- Any unobfuscated logging of credentials in storediag operations.
- Any unobfuscated logging of credentials in test runs.

Note that some low level `-debug` options may print secrets; issues reported here should be considered bugs rather than security issues.


