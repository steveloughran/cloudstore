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



#  Command `fetchdt`

This is an extension of `hdfs fetchdt` which collects delegation tokens
from a list of filesystems, saving them to a file.

```bash
hadoop jar cloudstore-0.1-SNAPSHOT fetchdt hdfs://tokens.bin s3a://landsat-pds/ s3a://bucket2
```

### Options

```
Usage: fetchdt <file> [-renewer <renewer>] [-r] [-p] <url1> ... <url999> 
 -r: require each filesystem to issue a token
 -p: protobuf format
```

### Examples

Successful query of an S3A session delegation token.

```bash
> bin/hadoop jar cloudstore-1.1.jar fetchdt -p -r file:/tmp/secrets.bin s3a://landsat-pds/
  Collecting tokens for 1 filesystem to to file:/tmp/secrets.bin
  2018-12-05 17:50:44,276 INFO fs.FetchTokens: Starting: Fetching token for s3a://landsat-pds/
  2018-12-05 17:50:44,399 INFO impl.MetricsConfig: Loaded properties from hadoop-metrics2.properties
  2018-12-05 17:50:44,458 INFO impl.MetricsSystemImpl: Scheduled Metric snapshot period at 10 second(s).
  2018-12-05 17:50:44,459 INFO impl.MetricsSystemImpl: s3a-file-system metrics system started
  2018-12-05 17:50:44,474 INFO delegation.S3ADelegationTokens: Filesystem s3a://landsat-pds is using delegation tokens of kind S3ADelegationToken/Session
  2018-12-05 17:50:44,547 INFO delegation.S3ADelegationTokens: No delegation tokens present: using direct authentication
  2018-12-05 17:50:44,547 INFO delegation.S3ADelegationTokens: S3A Delegation support token (none) with Session token binding for user hrt_qa, with STS endpoint "", region "" and token duration 30:00
  2018-12-05 17:50:46,604 INFO delegation.S3ADelegationTokens: Starting: Creating New Delegation Token
  2018-12-05 17:50:46,620 INFO delegation.SessionTokenBinding: Creating STS client for Session token binding for user hrt_qa, with STS endpoint "", region "" and token duration 30:00
  2018-12-05 17:50:46,646 INFO auth.STSClientFactory: Requesting Amazon STS Session credentials
  2018-12-05 17:50:47,099 INFO delegation.S3ADelegationTokens: Created S3A Delegation Token: Kind: S3ADelegationToken/Session, Service: s3a://landsat-pds, Ident: (S3ATokenIdentifier{S3ADelegationToken/Session; uri=s3a://landsat-pds; timestamp=1544032247065; encryption=(no encryption); 80fc87d2-0da2-4438-9ba6-7ae82751aba5; Created on HW13176.local/192.168.99.1 at time 2018-12-05T17:50:46.608Z.}; session credentials expiring on Wed Dec 05 18:20:46 GMT 2018; (valid))
  2018-12-05 17:50:47,099 INFO delegation.S3ADelegationTokens: Creating New Delegation Token: duration 0:00.495s
  Fetched token: Kind: S3ADelegationToken/Session, Service: s3a://landsat-pds, Ident: (S3ATokenIdentifier{S3ADelegationToken/Session; uri=s3a://landsat-pds; timestamp=1544032247065; encryption=(no encryption); 80fc87d2-0da2-4438-9ba6-7ae82751aba5; Created on HW13176.local/192.168.99.1 at time 2018-12-05T17:50:46.608Z.}; session credentials expiring on Wed Dec 05 18:20:46 GMT 2018; (valid))
  2018-12-05 17:50:47,100 INFO fs.FetchTokens: Fetching token for s3a://landsat-pds/: duration 0:02:825
  Saved 1 token to file:/tmp/secrets.bin
  2018-12-05 17:50:47,166 INFO impl.MetricsSystemImpl: Stopping s3a-file-system metrics system...
  2018-12-05 17:50:47,166 INFO impl.MetricsSystemImpl: s3a-file-system metrics system stopped.
  2018-12-05 17:50:47,166 INFO impl.MetricsSystemImpl: s3a-file-system metrics system shutdown complete.
  ~/P/h/h/t/hadoop-3.1.2-SNAPSHOT (cloud/BUG-99335-HADOOP-15364-S3Select-HDP-3.0.100 ⚡↩) 
```


Failure to get anything from fs, with `-r` option to require them

```bash
> hadoop jar cloudstore-1.1.jar fetchdt -p -r file:/tmm/secrets.bin file:///

Collecting tokens for 1 filesystem to to file:/tmm/secrets.bin
2018-12-05 17:47:00,970 INFO fs.FetchTokens: Starting: Fetching token for file:/
No token for file:/
2018-12-05 17:47:00,972 INFO fs.FetchTokens: Fetching token for file:/: duration 0:00:003
2018-12-05 17:47:00,973 INFO util.ExitUtil: Exiting with status 44: No token issued by filesystem file:///
```

Same command, without the -r. 

```bash
> hadoop jar cloudstore-1.1.jar fetchdt -p file:/tmm/secrets.bin file:///
Collecting tokens for 1 filesystem to to file:/tmp/secrets.bin
2018-12-05 17:54:26,776 INFO fs.FetchTokens: Starting: Fetching token for file:/tmp
No token for file:/tmp
2018-12-05 17:54:26,778 INFO fs.FetchTokens: Fetching token for file:/tmp: duration 0:00:002
No tokens collected, file file:/tmp/secrets.bin unchanged
```

The token file is not modified.


