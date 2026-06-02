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

# Low-level S3 operations

There are a set of lower-level commands against S3 stores.
These require the AWS V2 SDK `bundle.jar` on the classpath

## Command bucketstate

Prints some of the low level diagnostics information about an S3 bucket which
can be obtained via the AWS APIs.

```
hadoop jar cloudstore-1.4.jar \
            bucketstate \
            s3a://mybucket/

2019-07-25 16:54:50,678 [main] INFO  tools.BucketState (DurationInfo.java:<init>(53)) - Starting: Bucket State
2019-07-25 16:54:54,216 [main] WARN  s3a.S3AFileSystem (S3AFileSystem.java:getAmazonS3ClientForTesting(675)) - Access to S3A client requested, reason Diagnostics
Bucket owner is alice (ID=593...e1)
Bucket policy:
NONE
```

If you don't have the permissions to read the bucket policy, you get a stack trace.

```
hadoop jar cloudstore-1.4.jar \
            bucketstate \
            s3a://mybucket/

2019-07-25 16:55:23,023 [main] INFO  tools.BucketState (DurationInfo.java:<init>(53)) - Starting: Bucket State
2019-07-25 16:55:25,993 [main] WARN  s3a.S3AFileSystem (S3AFileSystem.java:getAmazonS3ClientForTesting(675)) - Access to S3A client requested, reason Diagnostics
Bucket owner is alice (ID=593...e1)
2019-07-25 16:55:26,883 [main] INFO  tools.BucketState (DurationInfo.java:close(100)) - Bucket State: duration 0:03:862
com.amazonaws.services.s3.model.AmazonS3Exception: The specified method is not allowed against this resource. (Service: Amazon S3; Status Code: 405; Error Code: MethodNotAllowed; Request ID: 3844E3089E3801D8; S3 Extended Request ID: 3HJVN5+MvOGit087AFqKLUyOUCU9inCakvJ44GW5Wb4toiVipEiv5uK6A54LQBjdKFYUU8ZI5XQ=), S3 Extended Request ID: 3HJVN5+MvOGit087AFqKLUyOUCU9inCakvJ44GW5Wb4toiVipEiv5uK6A54LQBjdKFYUU8ZI5XQ=

```


## Command iampolicy

Generate AWS IAM policy for a given bucket
```
hadoop jar cloudstore-1.4.jar iampolicy s3a://example-bucket/

{
  "Version" : "2012-10-17",
  "Statement" : [ {
    "Sid" : "7",
    "Effect" : "Allow",
    "Action" : [ "s3:GetBucketLocation", "s3:ListBucket*" ],
    "Resource" : "arn:aws:s3:::example-bucket"
  }, {
    "Sid" : "8",
    "Effect" : "Allow",
    "Action" : [ "s3:Get*", "s3:PutObject", "s3:PutObjectAcl", "s3:DeleteObject", "s3:AbortMultipartUpload" ],
    "Resource" : "arn:aws:s3:::example-bucket/*"
  }, {
    "Sid" : "1",
    "Effect" : "Allow",
    "Action" : "kms:*",
    "Resource" : "*"
  } ]
}

```
Notes:
* KMS policy is always added in case data is encrypted/decrypted with S3-KMS; it is not need if this is not the case.
* Read and Write access up the tree is needed. Maybe if you enable directory marker retention writing from root becomes optional.
* "s3:GetBucketLocation" is used by the bucket existence v2 check. If the probe is at 0, it is never called.

## Command listobjects

List all objects and a path through the low-level S3 APIs.
This bypasses the filesystem metaphor and gives the real view
of the object store.

The `-purge` option will remove all directory markers.

```
Usage: listobjects <path>
        -D <key=value>  Define a single configuration option
        -sysprop <file> Property file of system properties
        -tokenfile <file>       Hadoop token file to load
        -xmlfile <file> XML config file to load
        -verbose        verbose output
        -debug  enable JVM logs (ALL) and override log4j levels (DEBUG) on specified packages or classes
        -logoverrides <file>    A newline separated list of package and class names
        -delete delete the objects
        -limit <limit>  limit of files to list
        -purge  purge directory markers
        -q      quiet output
```

Example
```
>  hadoop jar cloudstore-1.4.jar listobjects -limit 500  s3a://aws-public-blockchain/v1.0/eth/

2026-05-28 19:42:27,734 [main] INFO  sdk.ListObjects (StoreDurationInfo.java:<init>(84)) - Starting: listobjects

1. Listing Objects under s3a://aws-public-blockchain/v1.0/eth
=============================================================

[00001] "v1.0/eth/blocks/date=2015-07-30/part-00000-32767f69-9150-49ac-9c03-45f34b103c34-c000.snappy.parquet"   size: [1824714] 2025-09-30T19:55:10Z    tag: "b2ace0db3694ecfab548c41e9fb3e2af"
[00002] "v1.0/eth/blocks/date=2015-07-31/part-00000-62c9c86c-8a10-4196-b54c-01a2a139f4ec-c000.snappy.parquet"   size: [1772330] 2025-09-30T19:54:47Z    tag: "5459b7ee09eec3a7cce717aac934fc0e"
[00003] "v1.0/eth/blocks/date=2015-08-01/part-00000-5438c668-b9c9-4b0a-8a35-64ff30b73cdf-c000.snappy.parquet"   size: [1368754] 2025-09-30T19:54:53Z    tag: "0c4264e7f07134ac7cff7e788fc7903d"
[00004] "v1.0/eth/blocks/date=2015-08-02/part-00000-e0818341-7c32-4d1d-8fa5-a6fb563777ea-c000.snappy.parquet"   size: [1390119] 2025-09-30T19:55:10Z    tag: "4c999729e2bbe89e39f0c7f35f972ecb"
[00005] "v1.0/eth/blocks/date=2015-08-03/part-00000-70e7bc53-8610-4048-b386-93edcd06465c-c000.snappy.parquet"   size: [1374373] 2025-09-30T19:55:53Z    tag: "2929120aa1a13061e25a70914ad3ece4"
[00006] "v1.0/eth/blocks/date=2015-08-04/part-00000-d101c8a0-5c86-4553-9acb-b62f581fcaea-c000.snappy.parquet"   size: [1395598] 2025-09-30T19:55:10Z    tag: "a7041889f879387bb750be1b10af102e"
[00007] "v1.0/eth/blocks/date=2015-08-05/part-00000-6f9c0e66-8518-4611-aa66-b74c74204f8e-c000.snappy.parquet"   size: [1358453] 2025-09-30T19:54:48Z    tag: "ff65486266cae0be6c684a7f3ed3f6f2"
[00008] "v1.0/eth/blocks/date=2015-08-06/part-00000-2630a26b-4e5f-4ef6-9dcd-9697cc9848d2-c000.snappy.parquet"   size: [1343443] 2025-09-30T19:55:39Z    tag: "9315a41d92dd9e95486a6eb9d5b7c4a5"
[00009] "v1.0/eth/blocks/date=2015-08-07/part-00000-6e063101-19d0-4453-8227-f4abea6e3ed8-c000.snappy.parquet"   size: [1590919] 2025-09-30T19:54:57Z    tag: "bc694628b5ec12bdbf7fbd7ba56462a9"
[00010] "v1.0/eth/blocks/date=2015-08-08/part-00000-a28cb51d-dbf1-47eb-8f10-46c6f6c0bd5f-c000.snappy.parquet"   size: [1572347] 2025-09-30T19:55:11Z    tag: "0f3587fae96b28e0e072427bfc94a198"
...
[00497] "v1.0/eth/blocks/date=2016-12-07/part-00000-ee37bd0d-22ec-49ff-8a00-d0a7aee10f65-c000.snappy.parquet"   size: [2209384] 2025-09-30T19:56:12Z    tag: "033363fc3a605cfe56c8bc05e10a3667"
[00498] "v1.0/eth/blocks/date=2016-12-08/part-00000-fe6f2b7e-d465-4580-90ec-7bd1a53bd1f9-c000.snappy.parquet"   size: [2198113] 2025-09-30T19:55:51Z    tag: "5ab3fb69f6368e9212f544e31b869663"
[00499] "v1.0/eth/blocks/date=2016-12-09/part-00000-b390bb9c-b273-46df-9206-1d58b38d2057-c000.snappy.parquet"   size: [2196953] 2025-09-30T19:56:15Z    tag: "d7a6bece8194810b88c227120cf3e054"

Found 500 objects with total size 995837325 bytes
2026-05-28 19:42:31,881 [main] INFO  sdk.ListObjects (StoreDurationInfo.java:close(190)) - Duration of listobjects: 00:00:04.146
```

## Command listversions

See [listversions](versioned-objects.md).


## Command mkbucket

Creates a new bucket

See [mkbucket](mkbucket.md)


## Command regions

Invokes the AWS region provider chain to see if the client can automatically determine the region of AWS SDK calls.

This is how all AWS service clients determine the region for sending/signing requests if
not explicitly set.

```bash
hadoop jar cloudstore-1.4.jar regions

Determining AWS region for SDK clients
======================================


Determining region using AwsEnvVarOverrideRegionProvider
========================================================

Use environment variable AWS_REGION
2021-06-22 12:04:59,277 [main] INFO  extra.Regions (StoreDurationInfo.java:<init>(53)) - Starting: AwsEnvVarOverrideRegionProvider.getRegion()
2021-06-22 12:04:59,284 [main] INFO  extra.Regions (StoreDurationInfo.java:close(100)) - AwsEnvVarOverrideRegionProvider.getRegion(): duration 0:00:010
region is not known

Determining region using AwsSystemPropertyRegionProvider
========================================================

System property aws.region
2021-06-22 12:04:59,286 [main] INFO  extra.Regions (StoreDurationInfo.java:<init>(53)) - Starting: AwsSystemPropertyRegionProvider.getRegion()
2021-06-22 12:04:59,287 [main] INFO  extra.Regions (StoreDurationInfo.java:close(100)) - AwsSystemPropertyRegionProvider.getRegion(): duration 0:00:000
region is not known

Determining region using AwsProfileRegionProvider
=================================================

Region info in ~/.aws/config
2021-06-22 12:04:59,336 [main] INFO  extra.Regions (StoreDurationInfo.java:<init>(53)) - Starting: AwsProfileRegionProvider.getRegion()
2021-06-22 12:04:59,359 [main] INFO  extra.Regions (StoreDurationInfo.java:close(100)) - AwsProfileRegionProvider.getRegion(): duration 0:00:023
Region is determined as "eu-west-2"

Determining region using InstanceMetadataRegionProvider
=======================================================

EC2 metadata; will only work in AWS infrastructure
2021-06-22 12:04:59,361 [main] INFO  extra.Regions (StoreDurationInfo.java:<init>(53)) - Starting: InstanceMetadataRegionProvider.getRegion()
2021-06-22 12:04:59,363 [main] INFO  extra.Regions (StoreDurationInfo.java:close(100)) - InstanceMetadataRegionProvider.getRegion(): duration 0:00:002
WARNING: Provider raised an exception com.amazonaws.AmazonClientException:
    AWS_EC2_METADATA_DISABLED is set to true, not loading region from EC2 Instance Metadata service
region is not known

Region found: "eu-west-2"
=========================

Region was determined by AwsProfileRegionProvider as  "eu-west-2"


```

This setup has set the environment variable `AWS_EC2_METADATA_DISABLED`; if this variable was unset
and the command executed outside AWS infrastructure then after a fifteen second delay a stack trace warning of
a failure to connect to the instance metadata server.

This is to be expected, given that the service isn't there.

## Command restore

Restores a versioned S3 Object to a path within the same bucket.

See [versioned objects](versioned-objects.md).


## Command undelete

"undeletes" S3 objects by removing directory tombstones from a bucket path.

See [versioned objects](versioned-objects.md).
