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

# sessionkeys

Generates a set of session keys from the AWS credentials used to log in to a bucket;
prints them as: XML, bash env vars, fish env vars, key=val properties.
For the XML and properties files, also prints the credential providers option
to use temporary credentials

Validity: 36h

This is to aid with generating temp keys to use with throwaway test clusters
that may be shared with colleagues, avoids having to 

*Note*: only available on cloudstore builds with the "extra" profile enabled; and when
 executed against a version of Hadoop (3.2+) which provides API access to the credential
 chain. 

```
bin/hadoop jar $CLOUDSTORE sessionkeys s3a://landsat-pds/
2020-08-25 14:02:36,996 [main] INFO  extra.SessionKeys (DurationInfo.java:<init>(53)) - Starting: session
2020-08-25 14:02:40,295 [main] INFO  extra.STSClientFactory2 (STSClientFactory2.java:lambda$requestSessionCredentials$0(146)) -
 Requesting Amazon STS Session credentials

XML settings
============

<fs.s3a.access.key>
  ASIASHFDIJDQGFIYYOJ7V
</fs.s3a.access.key>
<fs.s3a.secret.key>
  ApNyF4qyAFupyypY2aB/QZxyCVNb
</fs.s3a.secret.key>
<fs.s3a.session.token>
  ABCDEF00000000000=
</fs.s3a.session.token>
<fs.s3a.aws.credentials.provider>
  org.apache.hadoop.fs.s3a.TemporaryAWSCredentialsProvider
</fs.s3a.aws.credentials.provider>
<fs.s3a.bucket.landsat-pds.endpoint>
  s3.amazonaws.com
</fs.s3a.bucket.landsat-pds.endpoint>


Properties
==========

fs.s3a.aws.credentials.provider=org.apache.hadoop.fs.s3a.TemporaryAWSCredentialsProvider
fs.s3a.access.key=ASIASHFDIJDQGFIYYOJ7V
fs.s3a.secret.key=ApNyF4qyAFupyypY2aB/QZxyCVNb
fs.s3a.session.token=ABCDEF00000000000=
fs.s3a.bucket.landsat-pds.endpoint=s3.amazonaws.com


Bash
====

export AWS_ACCESS_KEY_ID=ASIASHFDIJDQGFIYYOJ7V
export AWS_SECRET_ACCESS_KEY=ApNyF4qyAFupyypY2aB/QZxyCVNb
export AWS_SESSION_TOKEN=ABCDEF00000000000=


Fish
====

set -gx AWS_ACCESS_KEY_ID ASIASHFDIJDQGFIYYOJ7V
set -gx AWS_SECRET_ACCESS_KEY ApNyF4qyAFupyypY2aB/QZxyCVNb
set -gx AWS_SESSION_TOKEN ABCDEF00000000000=

2020-08-25 14:02:40,840 [main] INFO  extra.SessionKeys (DurationInfo.java:close(100)) - session: duration 0:03:849
```