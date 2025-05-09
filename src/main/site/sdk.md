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

# S3 operations through the AWS V2 SDK

There are a set of commands which require a version of the S3A connector
built against the AWS v2 SDK on the classpath

## Command `bucketstate`

Prints some of the low level diagnostics information about an S3 bucket which
can be obtained via the AWS APIs.

```
hadoop jar cloudstore-1.0.jar \
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
hadoop jar cloudstore-1.0.jar \
            bucketstate \
            s3a://mybucket/

2019-07-25 16:55:23,023 [main] INFO  tools.BucketState (DurationInfo.java:<init>(53)) - Starting: Bucket State
2019-07-25 16:55:25,993 [main] WARN  s3a.S3AFileSystem (S3AFileSystem.java:getAmazonS3ClientForTesting(675)) - Access to S3A client requested, reason Diagnostics
Bucket owner is alice (ID=593...e1)
2019-07-25 16:55:26,883 [main] INFO  tools.BucketState (DurationInfo.java:close(100)) - Bucket State: duration 0:03:862
com.amazonaws.services.s3.model.AmazonS3Exception: The specified method is not allowed against this resource. (Service: Amazon S3; Status Code: 405; Error Code: MethodNotAllowed; Request ID: 3844E3089E3801D8; S3 Extended Request ID: 3HJVN5+MvOGit087AFqKLUyOUCU9inCakvJ44GW5Wb4toiVipEiv5uK6A54LQBjdKFYUU8ZI5XQ=), S3 Extended Request ID: 3HJVN5+MvOGit087AFqKLUyOUCU9inCakvJ44GW5Wb4toiVipEiv5uK6A54LQBjdKFYUU8ZI5XQ=

```


## Command `iampolicy`

Generate AWS IAM policy for a given bucket
```
hadoop jar cloudstore-1.0.jar iampolicy s3a://example-bucket/

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

## Command `listobjects`

List all objects and a path through the low-level S3 APIs.
This bypasses the filesystem metaphor and gives the real view
of the object store.

The `-purge` option will remove all directory markers.

```
Usage: listobjects <path>
    -D <key=value> Define a property
    -limit <limit> limit of files to list
    -purge         purge directory markers
    -tokenfile <file> Hadoop token file to load
    -verbose print verbose output
    -xmlfile <file> XML config file to load
```

## Command `listversions`

See [listversions](versioned-objects.md).


## Command `mkbucket`

Creates a new bucket

See [mkbucket](mkbucket.md)


## Command `regions`

Invokes the AWS region provider chain to see if the client can automatically determine the region of AWS SDK calls.

This is how all AWS service clients determine the region for sending/signing requests if
not explicitly set.

```bash
hadoop jar cloudstore-1.0.jar regions

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
and the command executed outside AWS infrastructure then after a 15 second delay a stack trace warning of
a failure to connect to the instance metadata server.

```
2021-06-22 11:54:15,774 [main] WARN  util.EC2MetadataUtils (EC2MetadataUtils.java:getItems(410)) -
 Unable to retrieve the requested metadata (/latest/dynamic/instance-identity/document).
 Failed to connect to service endpoint: 
    com.amazonaws.SdkClientException: Failed to connect to service endpoint:
```

This is to be expected, given that the service isn't there.

## Command `restore`

Restores a versioned S3 Object to a path within the same bucket.

See [versioned objects](versioned-objects.md).



## Command `undelete`

"undeletes" S3 objects by removing directory tombstones from a bucket path.

See [versioned objects](versioned-objects.md).



## S3A Diagnostics Credential Provider

[DiagnosticsAWSCredentialsProvider](diagnosticsawscredentialsprovider.md)
is an AWS credential provider which logs the fs.s3a login secrets (obfuscated and md5).

