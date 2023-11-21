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

# Cloudstore support for AWS V2 SDK

As we move the S3A connector to the v2 sdk, we are updating the cloudstore JAR to follow.

If cloudstore is not built with the `-Psdk2` profile, these commands are unavailable.


* All classes which use the v1 sdk in the `extra` source tree are left as it, including calling
  methods in the `S3AFileSystem` class intended for exclusive use within the module itself.
* A new source tree `source/sdk2` has been created which imports the V2 SDK and uses them as needed
* This also imports hadoop-3.4.x and uses the `S3AInternals` interface to get offical access to internal
  operations.
* Equivalent commands to the V1 SDK commands will be added as needed.
* Maybe this time with tests.
* And ideally, the ability to be invoked by spark.
* Being an open source project, contributions are welcome. With tests and documentation.
* All new commands will have the suffix `2` to indicate these are v2 sdk compatible.


## Available Commands

### `regions2`: list AWS regions

This uses the AWS SDK internal class `software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain` and its equivalents to determine what region the SDK comes up with.