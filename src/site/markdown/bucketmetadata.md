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

# Command `bucketmetadata`

Retrieves metadata from an S3 Bucket (v2 SDK only) by probing the store, provided
the caller has the permission to issue this request -sometimes it is disabled.

For S3 Express buckets, this includes the Availability Zone in which the region is deployed, as well as its type (which will be `AvailabilityZone`).

For S3 standard buckets, the location type and name will be `null`.

Although it is possible to identify S3 Express buckets by their name, this is not the case when referenced through AWS Access Points; this probe against the store will 

## Example: S3 Standard

```
 bin/hadoop jar $CLOUDSTORE bucketmetadata s3a://example-london/

Getting bucket info for s3a://example-london/
=============================================

Bucket metadata from S3
Region eu-west-2
Location Name null
Location Type null
```

The bucket region is eu-west-2; it has no location type or name.

## Example: Access point

Here an access point is used to reference the same bucket, using the per bucket-option
`fs.s3a.bucket.example-ap.accesspoint.arn` of a "virtual" bucket name `example-ap`
to remap the request to the Access Point.

```xml
  <property>
    <name>fs.s3a.bucket.example-ap.accesspoint.arn</name>
    <value>arn:aws:s3:eu-west-2:152813711128:accesspoint/ap-example-london</value>
    <description>AccessPoint bound to example-ap which relays to example-london</description>
  </property>
```

The response indicates that this is mapped to an S3 Standard bucket.

```
bin/hadoop jar cloudstore-1.1.jar bucketmetadata s3a://example-ap

Getting bucket info for s3a://example-ap
========================================

2024-09-25 11:24:32,908 [main] INFO  s3a.S3AFileSystem (S3AFileSystem.java:initialize(578)) - Using AccessPoint ARN "arn:aws:s3:eu-west-2:152813711128:accesspoint/ap-example-london" for bucket example-ap
Bucket metadata from S3
Region eu-west-2
Location Name null
Location Type null
```


## Example: S3 Express

When probing an S3 Express bucket, the location type and name is returned.

```
hadoop jar cloudstore-1.1.jar bucketmetadata s3a://example--usw2-az1--x-s3

Getting bucket info for s3a://example--usw2-az1--x-s3
=====================================================

Bucket metadata from S3
Region us-west-2
Location Name usw2-az1
Location Type AvailabilityZone
```

## Third Party stores

The result of the probe against third party stores is undefined, and will vary with the store.

Here is an example response from a probe of a Dell ECS store:

```
bin/hadoop jar cloudstore-1.1.jar bucketmetadata s3a://ecsbucket/

Getting bucket info for s3a://ecsbucket/
=========================================

Bucket metadata from S3
Region null
Location Name null
Location Type null

```