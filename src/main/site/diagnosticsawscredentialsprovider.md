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

# DiagnosticsAWSCredentialsProvider

A new credential provider which prints obfuscated and MD5 values of the AWS secrets.

This leaks some information and so the logs must be considered as sensitive as the output
of storediag commands. 

It does not attempt to do any authentication, simply print those values used by the temporary/simple
credential providers.

## Usage

1. Get into the same classloader as the s3a FS, which means into `share/hadoop/common/lib`
2. Add to the list of credential providers

```xml
<property>
  <name>fs.s3a.aws.credentials.provider</name>
  <value>
    org.apache.hadoop.fs.store.s3a.DiagnosticsAWSCredentialsProvider,
    org.apache.hadoop.fs.s3a.TemporaryAWSCredentialsProvider,
    org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider,
  </value>
</property>
```

*Notes* 

* If using S3A Delegation Tokens, the delegation token binding takes over
authenticating with s3 -the values in fs.s3a.aws.credentials.provider _may_ not be read.
* It's not enough to set this option and invoke via cloudstore commands; the class isn't found.
  This may be related to the change for HADOOP-17372, but that was forced by odd things happening
  if a HiveConfig or similar was passed in.

## Output from an operation

```
2022-10-03 16:41:15,135 [main] INFO  s3a.DiagnosticsAWSCredentialsProvider (DiagnosticsAWSCredentialsProvider.java:printSecretOption(135))
 - Option fs.s3a.access.key = "AK**************66YB" [20] D51E40E203A4137FFE7CAB1BA000000 from [core-site.xml]
2022-10-03 16:41:15,135 [main] INFO  s3a.DiagnosticsAWSCredentialsProvider (DiagnosticsAWSCredentialsProvider.java:printSecretOption(135))
 - Option fs.s3a.secret.key = "Bq**********************************dfix" [40] BAA1DCAB58875154AA0B77A000000E0 from [core-site.xml]
2022-10-03 16:41:15,135 [main] INFO  s3a.DiagnosticsAWSCredentialsProvider (DiagnosticsAWSCredentialsProvider.java:printSecretOption(138)) -
 Option fs.s3a.session.token unset
```

