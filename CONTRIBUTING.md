o <!---
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

# Contributing

This project is a subsidiary project of [Apache Hadoop](https://hadoop.apache.org/)

All governance is by the Apache Software Foundation, including release process.

To discuss the project, please get on the [common developer mailing list]( https://hadoop.apache.org/mailing_lists.html).

Do read the Hadoop [How to Contribute](https://cwiki.apache.org/confluence/display/HADOOP/How+To+Contribute) wiki page.

## Issues and Patches

1. Please create issues on the github project, rather than JIRA.
2. Create an issue before creating a Pull Request, referring to the issue number in the PR.

## Testing

All PRs will need a local test run against an S3 service endpoint of your choice.
It doesn't have to be AWS S3 standard: diversity is always interesting, what is
key is that it's there.

You MUST declare what S3 endpoint you tested against, such as "S3 Frankfurt" or "Local Minio docker image".

See [Testing the S3A filesystem client and its features](https://hadoop.apache.org/docs/current/hadoop-aws/tools/hadoop-aws/testing.html) for instructions.

Place your secrets (or better, an XInclude reference to them elsewhere in your local filesystem) in the file `src/test/resources/auth-keys.xml`.
DO NOT place this file under revision control.
If you do ever commit cloud credentials to any revision control system: rotate the keys.
That's even if that's your private internal repo: it's all to easy to accidentally make that public.
Your IT team will be happier with you saying "I just committed my keys, can I replace them even though I haven't pushed those changes anywhere" to having to deal with the consequences of a leak of keys.

Consider also using a restricted role for the credentials you are storing in XML/JCEKs files. They only need R/W access to test buckets and the ability to create session credentials for `ITestS3ASessionKeys`.  

If testing against third party stores or when running all tests with temporary credentials, session credentials will not be available. Set `test.fs.s3a.sts.enabled` to false to disable tests in `ITestS3ASessionKeys`.



## _Roadmap_: Whatever we need to debug and improve client cloud connectivity


* Exploration of higher-performance IO.
* Diagnostics/testing of integration with foundational Hadoop operations.
* Improving CLI testing with various probes designed to be invoked in a shell
  and fail with meaningful exit codes. E.g: verifying that a filesystem has a specific (key, val)
  property, that a specific env var made it through.
* Something to scan hadoop installations for duplicate artifacts, which knowledge
  of JARS which main contain the same classes (aws-shaded, aws-s3, etc),
  and the knowledge of required consistencies (hadoop-*, jackson-*).

Contributions through PRs welcome.

Bug reports: please include environment and ideally patches.
