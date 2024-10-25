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

# storediag

The `storediag` command is the initial command of this library, and the most heavily
used.

The `storediag` entry point is designed to pick up the FS settings, dump them
with sanitized secrets, and display their provenance. It then
bootstraps connectivity with an attempt to initiate (unauthed) HTTP connections
to the store's endpoints. This should be sufficient to detect proxy and
endpoint configuration problems.

Then it tries to perform some reads and optionally rites against the store. If these
fail, then there's clearly a problem. Hopefully though, there's now enough information
to begin determining what it is.

Finally, if things do fail, the printed configuration obfuscates the login secrets,
and any other property considered sensitive.
This is to supprt for safer reporting of issues in bug reports.

```bash
hadoop jar cloudstore-1.0.jar storediag -j -5 s3a://landsat-pds/
hadoop jar cloudstore-1.0.jar storediag -w --tokenfile mytokens.bin s3a://my-readwrite-bucket/subdirectory
hadoop jar cloudstore-1.0.jar storediag -w --tokenfile mytokens.bin hdfs://namenode/user/alice/subdir
hadoop jar cloudstore-1.0.jar storediag abfs://container@user/
```

The remote store is required to grant read access to the caller.
If the `-w` option is provided, the caller must have write permission for the target directory.

### Usage

```
Usage: storediag [options] <filesystem>
        -D <key=value>  Define a single configuration option
        -sysprop <file> Property file of system properties
        -tokenfile <file>       Hadoop token file to load
        -xmlfile <file> XML config file to load
        -verbose        verbose output
        -t      Require delegation tokens to be issued
        -e      List the environment variables. *danger: does not redact secrets*
        -h      redact all chars in sensitive options
        -j      List the JARs on the classpath
        -l      Dump the Log4J settings
        -5      Print MD5 checksums of the jars listed (requires -j)
        -o      Downgrade all 'required' classes to optional
        -principal <principal>  kerberos principal to request a token for
        -required <file>        text file of extra classes+resources to require
        -s      List the JVM System Properties
        -w      attempt write operations on the filesystem
```

The `-required` option takes a text file where every line is one of
a #-prefixed comment, a blank line, a classname, a resource (with "/" in).
These are all loaded

```bash
hadoop jar cloudstore-1.0.jar storediag -5 -required required.txt s3a://something/
```

and with a `required.txt` listing extra classes which must be on the classpath

```
# S3A
org.apache.hadoop.fs.s3a.auth.delegation.S3ADelegationTokens
# Misc
org.apache.commons.configuration.Configuration
org.apache.commons.lang3.StringUtils
``` 

This is useful to dynamically add some extra mandatory classes to
the list of classes you need to work with a store...most useful when either
you are developing new features and want to verify they are on the classpath,
or you are working with an unknown object store and just want to check its depencies
up front.

A missing file or resource will result in an error and the command failing.

The comments are printed too. This means you can use them in the reports.

## Example:

```
bin/hadoop jar cloudstore-1.0 storediag -j -w s3a://stevel-london/temp/subdir > output 2>&1
```

This does diagnostics test including a write against the bucket
`s3a://stevel-london`, working wih the
subdirectory `temp/subdir`

The `-w` option indicates that a file write followed by a rename shall be attempted after
a list and any read of an existing file.

The `-h` option obfuscates all secrets. Store names and paths should still be
reviewed to make sure they do not leak information.

It is a genuine test against a London store through a V2 client and a local build of
hadoop 3.4.2

```

Store Diagnostics for stevel (auth:SIMPLE) on stevel-MBP16/192.168.86.27
========================================================================

Collected at at 2024-10-21T19:07:07.627Z


Diagnostics for filesystem s3a://stevel-london/temp/subdir
==========================================================

S3A FileSystem Connector
ASF Filesystem Connector to Amazon S3 Storage and compatible stores
https://hadoop.apache.org/docs/current/hadoop-aws/tools/hadoop-aws/index.html

Hadoop information
==================

  Hadoop 3.4.0-SNAPSHOT
  Compiled by stevel on 2024-01-29T14:12Z
  Compiled with protoc 3.7.1
  From source with checksum bf6e206593b31fae5c42eaf9fee773d

Determining OS version
======================

Darwin stevel-MBP16 23.5.0 Darwin Kernel Version 23.5.0: Wed May  1 20:12:58 PDT 2024; root:xnu-10063.121.3~5/RELEASE_ARM64_T6000 arm64

Selected System Properties
==========================

[001]  aws.accessKeyId = (unset)
[002]  aws.secretKey = (unset)
[003]  aws.sessionToken = (unset)
[004]  aws.binaryIonEnabled = (unset)
[005]  aws.cborEnabled = (unset)
[006]  aws.configFile = (unset)
[007]  aws.containerAuthorizationToken = (unset)
[008]  aws.containerCredentialsFullUri = (unset)
[009]  aws.containerCredentialsPath = (unset)
[010]  aws.containerServiceEndpoint = (unset)
[011]  aws.defaultsMode = (unset)
[012]  aws.disableEc2Metadata = (unset)
[013]  aws.disableRequestCompression = (unset)
[014]  aws.disableRequestCompression = (unset)
[015]  aws.disableS3ExpressAuth = (unset)
[016]  aws.ec2MetadataServiceEndpoint = (unset)
[017]  aws.ec2MetadataServiceEndpointMode = (unset)
[018]  aws.endpointDiscoveryEnabled = (unset)
[019]  aws.executionEnvironment = (unset)
[020]  aws.maxAttempts = (unset)
[021]  aws.profile = (unset)
[022]  aws.region = (unset)
[023]  aws.requestMinCompressionSizeBytes = (unset)
[024]  aws.retryMode = (unset)
[025]  aws.roleArn = (unset)
[026]  aws.roleSessionName = (unset)
[027]  aws.s3UseUsEast1RegionalEndpoint = (unset)
[028]  aws.secretAccessKey = (unset)
[029]  aws.sharedCredentialsFile = (unset)
[030]  aws.useDualstackEndpoint = (unset)
[031]  aws.useFipsEndpoint = (unset)
[032]  aws.webIdentityTokenFile = (unset)
[033]  aws.java.v1.printLocation = (unset)
[034]  aws.java.v1.disableDeprecationAnnouncement = (unset)
[035]  com.amazonaws.regions.RegionUtils.disableRemote = (unset)
[036]  com.amazonaws.regions.RegionUtils.fileOverride = (unset)
[037]  com.amazonaws.sdk.disableCertChecking = (unset)
[038]  com.amazonaws.sdk.disableEc2Metadata = (unset)
[039]  com.amazonaws.sdk.ec2MetadataServiceEndpointOverride = (unset)
[040]  com.amazonaws.sdk.enableDefaultMetrics = (unset)
[041]  com.amazonaws.sdk.enableInRegionOptimizedMode = (unset)
[042]  com.amazonaws.sdk.enableRuntimeProfiling = (unset)
[043]  com.amazonaws.sdk.enableThrottledRetry = (unset)
[044]  com.amazonaws.sdk.maxAttempts = (unset)
[045]  com.amazonaws.sdk.retryMode = (unset)
[046]  com.amazonaws.sdk.s3.defaultStreamBufferSize = (unset)
[047]  com.amazonaws.services.s3.disableGetObjectMD5Validation = (unset)
[048]  com.amazonaws.services.s3.disableImplicitGlobalClients = (unset)
[049]  com.amazonaws.services.s3.disablePutObjectMD5Validation = (unset)
[050]  com.amazonaws.services.s3.enableV4 = (unset)
[051]  com.amazonaws.services.s3.enforceV4 = (unset)
[052]  org.wildfly.openssl.path = (unset)
[053]  software.amazon.awssdk.http.service.impl = (unset)
[054]  software.amazon.awssdk.http.async.service.impl = (unset)
[055]  java.version = "1.8.0_362"
[056]  java.specification.version = "1.8"
[057]  java.class.version = "52.0"
[058]  https.proxyHost = (unset)
[059]  https.proxyPort = (unset)
[060]  https.nonProxyHosts = (unset)
[061]  https.proxyPassword = (unset)
[062]  http.proxyHost = (unset)
[063]  http.proxyPort = (unset)
[064]  http.proxyPassword = (unset)
[065]  http.nonProxyHosts = (unset)
[066]  java.net.preferIPv4Stack = "true"
[067]  java.net.preferIPv6Addresses = (unset)
[068]  networkaddress.cache.ttl = (unset)
[069]  networkaddress.cache.negative.ttl = (unset)
[070]  socksProxyHost = (unset)
[071]  socksProxyPort = (unset)
[072]  sun.net.client.defaultConnectTimeout = (unset)
[073]  sun.net.client.defaultReadTimeout = (unset)
[074]  sun.net.inetaddr.ttl = (unset)
[075]  sun.net.inetaddr.negative.ttl = (unset)

JAR listing
===========

HikariCP-4.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/HikariCP-4.0.3.jar ([159,222])
animal-sniffer-annotations-1.17.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/animal-sniffer-annotations-1.17.jar ([3,448])
aopalliance-1.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/aopalliance-1.0.jar ([4,467])
asm-commons-9.6.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/asm-commons-9.6.jar ([72,194])
asm-tree-9.6.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/asm-tree-9.6.jar ([51,935])
audience-annotations-0.12.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/audience-annotations-0.12.0.jar ([20,891])
avro-1.9.2.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/avro-1.9.2.jar ([587,956])
azure-data-lake-store-sdk-2.3.9.jar	./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/azure-data-lake-store-sdk-2.3.9.jar ([113,966])
bcpkix-jdk15on-1.70.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/bcpkix-jdk15on-1.70.jar ([963,713])
bcprov-jdk15on-1.70.jar	./home/steve/hadoop-3.4.0/share/hadoop/common/lib/bcprov-jdk15on-1.70.jar ([5,867,298])
bcutil-jdk15on-1.70.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/bcutil-jdk15on-1.70.jar ([482,530])
bundle-2.21.41.jar	./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/bundle-2.21.41.jar ([546,710,800])
checker-qual-2.5.2.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/checker-qual-2.5.2.jar ([193,322])
codemodel-2.6.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/codemodel-2.6.jar ([152,436])
commons-beanutils-1.9.4.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/commons-beanutils-1.9.4.jar ([246,918])
commons-cli-1.5.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/commons-cli-1.5.0.jar ([58,284])
commons-codec-1.15.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/commons-codec-1.15.jar ([353,793])
commons-collections-3.2.2.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/commons-collections-3.2.2.jar ([588,337])
commons-compress-1.24.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/commons-compress-1.24.0.jar ([1,076,223])
commons-configuration2-2.8.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/commons-configuration2-2.8.0.jar ([632,505])
commons-daemon-1.0.13.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/commons-daemon-1.0.13.jar ([24,239])
commons-io-2.14.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/commons-io-2.14.0.jar ([494,227])
commons-lang-2.6.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/commons-lang-2.6.jar ([284,220])
commons-lang3-3.12.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/commons-lang3-3.12.0.jar ([587,402])
commons-logging-1.1.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/commons-logging-1.1.3.jar ([62,050])
commons-math3-3.6.1.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/commons-math3-3.6.1.jar ([2,213,560])
commons-net-3.9.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/commons-net-3.9.0.jar ([316,431])
commons-text-1.10.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/commons-text-1.10.0.jar ([238,400])
curator-client-5.2.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/curator-client-5.2.0.jar ([2,983,237])
curator-framework-5.2.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/curator-framework-5.2.0.jar ([336,384])
curator-recipes-5.2.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/curator-recipes-5.2.0.jar ([315,569])
dnsjava-3.4.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/dnsjava-3.4.0.jar ([456,514])
ehcache-3.3.1.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/ehcache-3.3.1.jar ([1,726,527])
failureaccess-1.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/failureaccess-1.0.jar ([3,727])
fst-2.50.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/fst-2.50.jar ([387,689])
geronimo-jcache_1.0_spec-1.0-alpha-1.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/geronimo-jcache_1.0_spec-1.0-alpha-1.jar ([55,236])
gson-2.9.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/gson-2.9.0.jar ([249,277])
guava-27.0-jre.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/guava-27.0-jre.jar ([2,747,878])
guice-4.2.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/guice-4.2.3.jar ([856,934])
guice-servlet-4.2.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/guice-servlet-4.2.3.jar ([81,553])
hadoop	./home/steve/hadoop-3.4.0/etc/hadoop ([directory])
hadoop-annotations-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/hadoop-annotations-3.4.0-SNAPSHOT.jar ([25,484])
hadoop-auth-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/hadoop-auth-3.4.0-SNAPSHOT.jar ([110,062])
hadoop-aws-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar ([815,543])
hadoop-azure-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/common/lib/hadoop-azure-3.4.0-SNAPSHOT.jar ([623,355])
hadoop-azure-datalake-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-azure-datalake-3.4.0-SNAPSHOT.jar ([33,290])
hadoop-common-3.4.0-SNAPSHOT-tests.jar	./home/steve/hadoop-3.4.0/share/hadoop/common/hadoop-common-3.4.0-SNAPSHOT-tests.jar ([3,584,700])
hadoop-common-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/common/hadoop-common-3.4.0-SNAPSHOT.jar ([4,661,506])
hadoop-hdfs-3.4.0-SNAPSHOT-tests.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/hadoop-hdfs-3.4.0-SNAPSHOT-tests.jar ([6,359,885])
hadoop-hdfs-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/hadoop-hdfs-3.4.0-SNAPSHOT.jar ([6,432,433])
hadoop-hdfs-client-3.4.0-SNAPSHOT-tests.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/hadoop-hdfs-client-3.4.0-SNAPSHOT-tests.jar ([130,236])
hadoop-hdfs-client-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/hadoop-hdfs-client-3.4.0-SNAPSHOT.jar ([5,673,519])
hadoop-hdfs-httpfs-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/hadoop-hdfs-httpfs-3.4.0-SNAPSHOT.jar ([270,769])
hadoop-hdfs-native-client-3.4.0-SNAPSHOT-tests.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/hadoop-hdfs-native-client-3.4.0-SNAPSHOT-tests.jar ([10,116])
hadoop-hdfs-native-client-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/hadoop-hdfs-native-client-3.4.0-SNAPSHOT.jar ([10,116])
hadoop-hdfs-nfs-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/hadoop-hdfs-nfs-3.4.0-SNAPSHOT.jar ([115,847])
hadoop-hdfs-rbf-3.4.0-SNAPSHOT-tests.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/hadoop-hdfs-rbf-3.4.0-SNAPSHOT-tests.jar ([584,829])
hadoop-hdfs-rbf-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/hadoop-hdfs-rbf-3.4.0-SNAPSHOT.jar ([1,279,917])
hadoop-kms-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/common/hadoop-kms-3.4.0-SNAPSHOT.jar ([96,809])
hadoop-mapreduce-client-app-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/mapreduce/hadoop-mapreduce-client-app-3.4.0-SNAPSHOT.jar ([592,052])
hadoop-mapreduce-client-common-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/mapreduce/hadoop-mapreduce-client-common-3.4.0-SNAPSHOT.jar ([806,539])
hadoop-mapreduce-client-core-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/mapreduce/hadoop-mapreduce-client-core-3.4.0-SNAPSHOT.jar ([1,827,982])
hadoop-mapreduce-client-hs-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/mapreduce/hadoop-mapreduce-client-hs-3.4.0-SNAPSHOT.jar ([183,358])
hadoop-mapreduce-client-hs-plugins-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/mapreduce/hadoop-mapreduce-client-hs-plugins-3.4.0-SNAPSHOT.jar ([10,407])
hadoop-mapreduce-client-jobclient-3.4.0-SNAPSHOT-tests.jar	./home/steve/hadoop-3.4.0/share/hadoop/mapreduce/hadoop-mapreduce-client-jobclient-3.4.0-SNAPSHOT-tests.jar ([1,663,676])
hadoop-mapreduce-client-jobclient-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/mapreduce/hadoop-mapreduce-client-jobclient-3.4.0-SNAPSHOT.jar ([50,248])
hadoop-mapreduce-client-nativetask-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/mapreduce/hadoop-mapreduce-client-nativetask-3.4.0-SNAPSHOT.jar ([90,937])
hadoop-mapreduce-client-shuffle-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/mapreduce/hadoop-mapreduce-client-shuffle-3.4.0-SNAPSHOT.jar ([64,120])
hadoop-mapreduce-client-uploader-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/mapreduce/hadoop-mapreduce-client-uploader-3.4.0-SNAPSHOT.jar ([22,739])
hadoop-mapreduce-examples-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/mapreduce/hadoop-mapreduce-examples-3.4.0-SNAPSHOT.jar ([281,626])
hadoop-nfs-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/common/hadoop-nfs-3.4.0-SNAPSHOT.jar ([170,458])
hadoop-registry-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/common/hadoop-registry-3.4.0-SNAPSHOT.jar ([191,068])
hadoop-shaded-guava-1.1.1.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/hadoop-shaded-guava-1.1.1.jar ([3,362,359])
hadoop-shaded-protobuf_3_7-1.1.1.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/hadoop-shaded-protobuf_3_7-1.1.1.jar ([1,477,052])
hadoop-yarn-api-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-api-3.4.0-SNAPSHOT.jar ([3,927,351])
hadoop-yarn-applications-distributedshell-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-applications-distributedshell-3.4.0-SNAPSHOT.jar ([82,102])
hadoop-yarn-applications-mawo-core-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-applications-mawo-core-3.4.0-SNAPSHOT.jar ([33,234])
hadoop-yarn-applications-unmanaged-am-launcher-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-applications-unmanaged-am-launcher-3.4.0-SNAPSHOT.jar ([20,440])
hadoop-yarn-client-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-client-3.4.0-SNAPSHOT.jar ([317,423])
hadoop-yarn-common-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-common-3.4.0-SNAPSHOT.jar ([2,532,964])
hadoop-yarn-registry-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-registry-3.4.0-SNAPSHOT.jar ([8,195])
hadoop-yarn-server-applicationhistoryservice-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-server-applicationhistoryservice-3.4.0-SNAPSHOT.jar ([258,842])
hadoop-yarn-server-common-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-server-common-3.4.0-SNAPSHOT.jar ([1,861,356])
hadoop-yarn-server-globalpolicygenerator-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-server-globalpolicygenerator-3.4.0-SNAPSHOT.jar ([59,577])
hadoop-yarn-server-nodemanager-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-server-nodemanager-3.4.0-SNAPSHOT.jar ([1,791,110])
hadoop-yarn-server-resourcemanager-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-server-resourcemanager-3.4.0-SNAPSHOT.jar ([2,725,044])
hadoop-yarn-server-router-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-server-router-3.4.0-SNAPSHOT.jar ([277,673])
hadoop-yarn-server-sharedcachemanager-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-server-sharedcachemanager-3.4.0-SNAPSHOT.jar ([59,210])
hadoop-yarn-server-tests-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-server-tests-3.4.0-SNAPSHOT.jar ([8,917])
hadoop-yarn-server-timeline-pluginstorage-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-server-timeline-pluginstorage-3.4.0-SNAPSHOT.jar ([61,478])
hadoop-yarn-server-web-proxy-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-server-web-proxy-3.4.0-SNAPSHOT.jar ([61,251])
hadoop-yarn-services-api-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-services-api-3.4.0-SNAPSHOT.jar ([72,474])
hadoop-yarn-services-core-3.4.0-SNAPSHOT.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-services-core-3.4.0-SNAPSHOT.jar ([633,068])
hdfs	./home/steve/hadoop-3.4.0/share/hadoop/hdfs ([directory])
httpclient-4.5.13.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/httpclient-4.5.13.jar ([780,321])
httpcore-4.4.13.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/httpcore-4.4.13.jar ([328,593])
j2objc-annotations-1.1.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/j2objc-annotations-1.1.jar ([8,782])
jackson-annotations-2.12.7.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jackson-annotations-2.12.7.jar ([75,705])
jackson-core-2.12.7.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jackson-core-2.12.7.jar ([365,538])
jackson-databind-2.12.7.1.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jackson-databind-2.12.7.1.jar ([1,512,418])
jackson-jaxrs-base-2.12.7.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/jackson-jaxrs-base-2.12.7.jar ([35,847])
jackson-jaxrs-json-provider-2.12.7.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/jackson-jaxrs-json-provider-2.12.7.jar ([16,433])
jackson-module-jaxb-annotations-2.12.7.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/jackson-module-jaxb-annotations-2.12.7.jar ([36,576])
jakarta.activation-api-1.2.1.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jakarta.activation-api-1.2.1.jar ([44,399])
jakarta.xml.bind-api-2.3.2.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/jakarta.xml.bind-api-2.3.2.jar ([115,498])
javax-websocket-client-impl-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/javax-websocket-client-impl-9.4.53.v20231009.jar ([168,048])
javax-websocket-server-impl-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/javax-websocket-server-impl-9.4.53.v20231009.jar ([47,850])
javax.inject-1.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/javax.inject-1.jar ([2,497])
javax.servlet-api-3.1.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/javax.servlet-api-3.1.0.jar ([95,806])
javax.websocket-api-1.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/javax.websocket-api-1.0.jar ([36,611])
javax.websocket-client-api-1.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/javax.websocket-client-api-1.0.jar ([27,011])
jaxb-api-2.2.11.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jaxb-api-2.2.11.jar ([102,244])
jaxb-impl-2.2.3-1.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jaxb-impl-2.2.3-1.jar ([890,168])
jcip-annotations-1.0-1.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jcip-annotations-1.0-1.jar ([4,722])
jersey-client-1.19.4.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/jersey-client-1.19.4.jar ([134,066])
jersey-core-1.19.4.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jersey-core-1.19.4.jar ([436,731])
jersey-guice-1.19.4.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/jersey-guice-1.19.4.jar ([16,151])
jersey-json-1.20.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jersey-json-1.20.jar ([158,695])
jersey-server-1.19.4.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jersey-server-1.19.4.jar ([705,276])
jersey-servlet-1.19.4.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jersey-servlet-1.19.4.jar ([128,990])
jettison-1.5.4.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jettison-1.5.4.jar ([90,184])
jetty-annotations-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/jetty-annotations-9.4.53.v20231009.jar ([86,694])
jetty-client-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/jetty-client-9.4.53.v20231009.jar ([327,897])
jetty-http-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jetty-http-9.4.53.v20231009.jar ([249,062])
jetty-io-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jetty-io-9.4.53.v20231009.jar ([183,014])
jetty-jndi-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/jetty-jndi-9.4.53.v20231009.jar ([46,751])
jetty-plus-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/jetty-plus-9.4.53.v20231009.jar ([65,605])
jetty-security-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jetty-security-9.4.53.v20231009.jar ([118,497])
jetty-server-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jetty-server-9.4.53.v20231009.jar ([736,758])
jetty-servlet-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jetty-servlet-9.4.53.v20231009.jar ([146,065])
jetty-util-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jetty-util-9.4.53.v20231009.jar ([588,870])
jetty-util-ajax-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jetty-util-ajax-9.4.53.v20231009.jar ([66,643])
jetty-webapp-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jetty-webapp-9.4.53.v20231009.jar ([140,307])
jetty-xml-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jetty-xml-9.4.53.v20231009.jar ([68,896])
jline-3.9.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jline-3.9.0.jar ([707,273])
jna-5.2.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/jna-5.2.0.jar ([1,488,769])
jsch-0.1.55.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jsch-0.1.55.jar ([282,591])
json-simple-1.1.1.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/json-simple-1.1.1.jar ([23,931])
jsonschema2pojo-core-1.0.2.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/jsonschema2pojo-core-1.0.2.jar ([163,419])
jsp-api-2.1.jar	./home/steve/hadoop-3.4.0/share/hadoop/common/lib/jsp-api-2.1.jar ([100,636])
jsr305-3.0.2.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jsr305-3.0.2.jar ([19,936])
jsr311-api-1.1.1.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/jsr311-api-1.1.1.jar ([46,367])
jul-to-slf4j-1.7.30.jar	./home/steve/hadoop-3.4.0/share/hadoop/common/lib/jul-to-slf4j-1.7.30.jar ([4,592])
kerb-admin-2.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/kerb-admin-2.0.3.jar ([101,457])
kerb-client-2.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/kerb-client-2.0.3.jar ([115,643])
kerb-common-2.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/kerb-common-2.0.3.jar ([71,296])
kerb-core-2.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/kerb-core-2.0.3.jar ([223,129])
kerb-crypto-2.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/kerb-crypto-2.0.3.jar ([115,065])
kerb-identity-2.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/kerb-identity-2.0.3.jar ([17,834])
kerb-server-2.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/kerb-server-2.0.3.jar ([85,371])
kerb-simplekdc-2.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/kerb-simplekdc-2.0.3.jar ([20,507])
kerb-util-2.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/kerb-util-2.0.3.jar ([36,361])
kerby-asn1-2.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/kerby-asn1-2.0.3.jar ([100,095])
kerby-config-2.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/kerby-config-2.0.3.jar ([30,190])
kerby-pkix-2.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/kerby-pkix-2.0.3.jar ([200,581])
kerby-util-2.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/kerby-util-2.0.3.jar ([40,787])
kerby-xdr-2.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/kerby-xdr-2.0.3.jar ([31,007])
leveldbjni-all-1.8.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/leveldbjni-all-1.8.jar ([1,045,744])
listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar ([2,199])
log4j-1.2.17.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/log4j-1.2.17.jar ([489,884])
metrics-core-3.2.4.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/metrics-core-3.2.4.jar ([136,314])
mssql-jdbc-6.2.1.jre7.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/mssql-jdbc-6.2.1.jre7.jar ([792,442])
netty-all-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-all-4.1.100.Final.jar ([4,473])
netty-buffer-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-buffer-4.1.100.Final.jar ([306,739])
netty-codec-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-codec-4.1.100.Final.jar ([345,293])
netty-codec-dns-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-codec-dns-4.1.100.Final.jar ([66,908])
netty-codec-haproxy-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-codec-haproxy-4.1.100.Final.jar ([37,778])
netty-codec-http-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-codec-http-4.1.100.Final.jar ([657,672])
netty-codec-http2-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-codec-http2-4.1.100.Final.jar ([486,355])
netty-codec-memcache-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-codec-memcache-4.1.100.Final.jar ([44,692])
netty-codec-mqtt-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-codec-mqtt-4.1.100.Final.jar ([113,931])
netty-codec-redis-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-codec-redis-4.1.100.Final.jar ([45,961])
netty-codec-smtp-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-codec-smtp-4.1.100.Final.jar ([21,293])
netty-codec-socks-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-codec-socks-4.1.100.Final.jar ([120,979])
netty-codec-stomp-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-codec-stomp-4.1.100.Final.jar ([34,547])
netty-codec-xml-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-codec-xml-4.1.100.Final.jar ([19,774])
netty-common-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-common-4.1.100.Final.jar ([660,474])
netty-handler-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-handler-4.1.100.Final.jar ([561,288])
netty-handler-proxy-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-handler-proxy-4.1.100.Final.jar ([25,492])
netty-handler-ssl-ocsp-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-handler-ssl-ocsp-4.1.100.Final.jar ([26,516])
netty-resolver-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-resolver-4.1.100.Final.jar ([37,795])
netty-resolver-dns-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-resolver-dns-4.1.100.Final.jar ([171,593])
netty-resolver-dns-classes-macos-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-resolver-dns-classes-macos-4.1.100.Final.jar ([9,094])
netty-resolver-dns-native-macos-4.1.100.Final-osx-aarch_64.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-resolver-dns-native-macos-4.1.100.Final-osx-aarch_64.jar ([19,546])
netty-resolver-dns-native-macos-4.1.100.Final-osx-x86_64.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-resolver-dns-native-macos-4.1.100.Final-osx-x86_64.jar ([19,279])
netty-transport-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-transport-4.1.100.Final.jar ([489,999])
netty-transport-classes-epoll-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-transport-classes-epoll-4.1.100.Final.jar ([147,139])
netty-transport-classes-kqueue-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-transport-classes-kqueue-4.1.100.Final.jar ([108,428])
netty-transport-native-epoll-4.1.100.Final-linux-aarch_64.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-transport-native-epoll-4.1.100.Final-linux-aarch_64.jar ([40,892])
netty-transport-native-epoll-4.1.100.Final-linux-x86_64.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-transport-native-epoll-4.1.100.Final-linux-x86_64.jar ([39,373])
netty-transport-native-epoll-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-transport-native-epoll-4.1.100.Final.jar ([5,726])
netty-transport-native-kqueue-4.1.100.Final-osx-aarch_64.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-transport-native-kqueue-4.1.100.Final-osx-aarch_64.jar ([25,582])
netty-transport-native-kqueue-4.1.100.Final-osx-x86_64.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-transport-native-kqueue-4.1.100.Final-osx-x86_64.jar ([25,020])
netty-transport-native-unix-common-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-transport-native-unix-common-4.1.100.Final.jar ([43,968])
netty-transport-rxtx-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-transport-rxtx-4.1.100.Final.jar ([18,192])
netty-transport-sctp-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-transport-sctp-4.1.100.Final.jar ([50,764])
netty-transport-udt-4.1.100.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/netty-transport-udt-4.1.100.Final.jar ([32,137])
nimbus-jose-jwt-9.31.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/nimbus-jose-jwt-9.31.jar ([759,031])
objenesis-2.6.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/objenesis-2.6.jar ([55,684])
protobuf-java-2.5.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/protobuf-java-2.5.0.jar ([533,455])
re2j-1.1.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/re2j-1.1.jar ([128,414])
slf4j-api-1.7.30.jar	./home/steve/hadoop-3.4.0/share/hadoop/common/lib/slf4j-api-1.7.30.jar ([41,472])
slf4j-log4j12-1.7.30.jar	./home/steve/hadoop-3.4.0/share/hadoop/common/lib/slf4j-log4j12-1.7.30.jar ([12,211])
snakeyaml-2.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/snakeyaml-2.0.jar ([334,803])
snappy-java-1.1.10.4.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/snappy-java-1.1.10.4.jar ([2,112,099])
stax2-api-4.2.1.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/stax2-api-4.2.1.jar ([195,909])
swagger-annotations-1.5.4.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/swagger-annotations-1.5.4.jar ([16,045])
token-provider-2.0.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/token-provider-2.0.3.jar ([19,116])
websocket-api-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/websocket-api-9.4.53.v20231009.jar ([52,192])
websocket-client-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/websocket-client-9.4.53.v20231009.jar ([45,602])
websocket-common-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/websocket-common-9.4.53.v20231009.jar ([214,599])
websocket-server-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/websocket-server-9.4.53.v20231009.jar ([45,510])
websocket-servlet-9.4.53.v20231009.jar	./home/steve/hadoop-3.4.0/share/hadoop/yarn/lib/websocket-servlet-9.4.53.v20231009.jar ([30,305])
wildfly-openssl-1.1.3.Final.jar	./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/wildfly-openssl-1.1.3.Final.jar ([436,580])
woodstox-core-5.4.0.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/woodstox-core-5.4.0.jar ([522,679])
zookeeper-3.8.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/zookeeper-3.8.3.jar ([1,321,779])
zookeeper-jute-3.8.3.jar	./home/steve/hadoop-3.4.0/share/hadoop/hdfs/lib/zookeeper-jute-3.8.3.jar ([254,933])

Environment Variables
=====================

[001]  AWS_ACCESS_KEY_ID = (unset)
[002]  AWS_ACCESS_KEY = (unset)
[003]  AWS_SECRET_KEY = (unset)
[004]  AWS_SECRET_ACCESS_KEY = (unset)
[005]  AWS_SESSION_TOKEN = (unset)
[006]  AWS_REGION = (unset)
[007]  AWS_CBOR_DISABLE = (unset)
[008]  AWS_CONFIG_FILE = (unset)
[009]  AWS_CONTAINER_AUTHORIZATION_TOKEN = (unset)
[010]  AWS_CONTAINER_CREDENTIALS_FULL_URI = (unset)
[011]  AWS_CONTAINER_CREDENTIALS_RELATIVE_URI = (unset)
[012]  AWS_CSM_CLIENT_ID = (unset)
[013]  AWS_CSM_HOST = (unset)
[014]  AWS_CSM_PORT = (unset)
[015]  AWS_EC2_METADATA_DISABLED = "true"
[016]  AWS_EC2_METADATA_SERVICE_ENDPOINT = (unset)
[017]  AWS_ENDPOINT_URL = (unset)
[018]  AWS_ENDPOINT_URL_S3 = (unset)
[019]  AWS_IGNORE_CONFIGURED_ENDPOINT_URLS = (unset)
[020]  AWS_JAVA_V1_DISABLE_DEPRECATION_ANNOUNCEMENT = (unset)
[021]  AWS_JAVA_V1_PRINT_LOCATION = (unset)
[022]  AWS_MAX_ATTEMPTS = (unset)
[023]  AWS_METADATA_SERVICE_TIMEOUT = (unset)
[024]  AWS_METADATA_SERVICE_TIMEOUT = (unset)
[025]  AWS_PROFILE = (unset)
[026]  AWS_RETRY_MODE = (unset)
[027]  AWS_ROLE_ARN = (unset)
[028]  AWS_ROLE_SESSION_NAME = (unset)
[029]  AWS_S3_US_EAST_1_REGIONAL_ENDPOINT = (unset)
[030]  AWS_WEB_IDENTITY_TOKEN_FILE = (unset)
[031]  PATH = "/Users/stevel/.cargo/bin:/Users/stevel/bin/google-cloud-sdk/bin:/Users/stevel/Library/Python/3.9/bin:/Users/stevel/java/maven/bin:/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home:/usr/local/smlnj/bin:~/.local/bin:/Users/stevel/bin:/Users/stevel/bin/scripts:/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin:/System/Cryptexes/App/usr/bin:/usr/bin:/bin:/usr/sbin:/sbin:/var/run/com.apple.security.cryptexd/codex.system/bootstrap/usr/local/bin:/var/run/com.apple.security.cryptexd/codex.system/bootstrap/usr/bin:/var/run/com.apple.security.cryptexd/codex.system/bootstrap/usr/appleinternal/bin:/opt/X11/bin:/Library/Apple/usr/bin:/usr/local/MacGPG2/bin:/Library/TeX/texbin:/usr/local/share/dotnet:~/.dotnet/tools:/Users/stevel/.cargo/bin:./bin:/Users/stevel/Projects/gocode/bin"
[032]  HADOOP_CONF_DIR = "./home/steve/hadoop-3.4.0/etc/hadoop"
[033]  HADOOP_CLASSPATH = (unset)
[034]  HADOOP_CREDSTORE_PASSWORD = (unset)
[035]  HADOOP_HEAPSIZE = (unset)
[036]  HADOOP_HEAPSIZE_MIN = (unset)
[037]  HADOOP_HOME = "./home/steve/hadoop-3.4.0"
[038]  HADOOP_LOG_DIR = (unset)
[039]  HADOOP_OPTIONAL_TOOLS = "hadoop-azure,hadoop-aws"
[040]  HADOOP_OPTS = "-Djava.net.preferIPv4Stack=true  -Dyarn.log.dir=./home/steve/hadoop-3.4.0/logs -Dyarn.log.file=hadoop.log -Dyarn.home.dir=./home/steve/hadoop-3.4.0 -Dyarn.root.logger=INFO,console -Dhadoop.log.dir=./home/steve/hadoop-3.4.0/logs -Dhadoop.log.file=hadoop.log -Dhadoop.home.dir=./home/steve/hadoop-3.4.0 -Dhadoop.id.str=stevel -Dhadoop.root.logger=INFO,console -Dhadoop.policy.file=hadoop-policy.xml -Dhadoop.security.logger=INFO,NullAppender"
[041]  HADOOP_SHELL_SCRIPT_DEBUG = (unset)
[042]  HADOOP_TOKEN = (unset)
[043]  HADOOP_TOKEN_FILE_LOCATION = (unset)
[044]  HADOOP_KEYSTORE_PASSWORD = (unset)
[045]  HADOOP_TOOLS_HOME = (unset)
[046]  HADOOP_TOOLS_OPTIONS = (unset)
[047]  HADOOP_YARN_HOME = "./home/steve/hadoop-3.4.0"
[048]  HDP_VERSION = (unset)
[049]  JAVA_HOME = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home"
[050]  LD_LIBRARY_PATH = (unset)
[051]  LOCAL_DIRS = (unset)
[052]  OPENSSL_ROOT_DIR = "/usr/local/opt/openssl/"
[053]  PYSPARK_DRIVER_PYTHON = (unset)
[054]  SPARK_HOME = (unset)
[055]  SPARK_CONF_DIR = (unset)
[056]  SPARK_SCALA_VERSION = (unset)
[057]  YARN_CONF_DIR = (unset)
[058]  http_proxy = (unset)
[059]  https_proxy = (unset)
[060]  no_proxy = (unset)

Hadoop XML Configurations
=========================

resource: core-default.xml
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/common/hadoop-common-3.4.0-SNAPSHOT.jar!/core-default.xml
resource: core-site.xml
       file:./home/steve/hadoop-3.4.0/etc/hadoop/core-site.xml
resource: hdfs-default.xml
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/hdfs/hadoop-hdfs-3.4.0-SNAPSHOT.jar!/hdfs-default.xml
resource: hdfs-site.xml
       file:./home/steve/hadoop-3.4.0/etc/hadoop/hdfs-site.xml
resource: mapred-default.xml
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/mapreduce/hadoop-mapreduce-client-core-3.4.0-SNAPSHOT.jar!/mapred-default.xml
resource: mapred-site.xml
       file:./home/steve/hadoop-3.4.0/etc/hadoop/mapred-site.xml
resource: yarn-default.xml
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/yarn/hadoop-yarn-api-3.4.0-SNAPSHOT.jar!/yarn-default.xml
resource: yarn-site.xml
       file:./home/steve/hadoop-3.4.0/etc/hadoop/yarn-site.xml

Security
========

Security Enabled: false
Keytab login: false
Ticket login: false
Current user: stevel (auth:SIMPLE)
Token count: 0

Hadoop Options
==============

[001]  fs.defaultFS = "file:///" [core-default.xml]
[002]  fs.default.name = "file:///" 
[003]  fs.creation.parallel.count = "64" [core-default.xml]
[004]  fs.permissions.umask-mode = "022" [core-default.xml]
[005]  fs.trash.classname = (unset)
[006]  fs.trash.interval = "0" [core-default.xml]
[007]  fs.trash.checkpoint.interval = "0" [core-default.xml]
[008]  fs.file.impl = (unset)
[009]  hadoop.tmp.dir = "/tmp/hadoop-stevel" [core-default.xml]
[010]  hdp.version = (unset)
[011]  yarn.resourcemanager.address = "0.0.0.0:8032" [yarn-default.xml]
[012]  yarn.resourcemanager.principal = (unset)
[013]  yarn.resourcemanager.webapp.address = "0.0.0.0:8088" [yarn-default.xml]
[014]  yarn.resourcemanager.webapp.https.address = "0.0.0.0:8090" [yarn-default.xml]
[015]  mapreduce.input.fileinputformat.list-status.num-threads = "1" [mapred-default.xml]
[016]  mapreduce.jobtracker.kerberos.principal = (unset)
[017]  mapreduce.job.hdfs-servers.token-renewal.exclude = (unset)
[018]  mapreduce.application.framework.path = (unset)
[019]  fs.iostatistics.logging.level = "info" [programmatically]
[020]  fs.iostatistics.thread.level.enabled = "true" [core-default.xml]
[021]  parquet.hadoop.vectored.io.enabled = (unset)

Security Options
================

[001]  dfs.data.transfer.protection = (unset)
[002]  hadoop.http.authentication.simple.anonymous.allowed = "true" [core-default.xml]
[003]  hadoop.http.authentication.type = "simple" [core-default.xml]
[004]  hadoop.kerberos.min.seconds.before.relogin = "60" [core-default.xml]
[005]  hadoop.kerberos.keytab.login.autorenewal.enabled = "false" [core-default.xml]
[006]  hadoop.security.authentication = "simple" [core-default.xml]
[007]  hadoop.security.authorization = "false" [core-default.xml]
[008]  hadoop.security.credential.provider.path = (unset)
[009]  hadoop.security.credstore.java-keystore-provider.password-file = (unset)
[010]  hadoop.security.credential.clear-text-fallback = "true" [core-default.xml]
[011]  hadoop.security.key.provider.path = (unset)
[012]  hadoop.security.crypto.jceks.key.serialfilter = (unset)
[013]  hadoop.rpc.protection = "authentication" [core-default.xml]
[014]  hadoop.tokens = (unset)
[015]  hadoop.token.files = (unset)

Selected Configuration Options
==============================

[001]  fs.s3a.access.key = "********************" [20] [core-site.xml]
[002]  fs.s3a.secret.key = "****************************************" [40] [core-site.xml]
[003]  fs.s3a.session.token = (unset)
[004]  fs.s3a.server-side-encryption-algorithm = (unset)
[005]  fs.s3a.server-side-encryption.key = (unset)
[006]  fs.s3a.encryption.algorithm = "SSE-KMS" [fs.s3a.bucket.stevel-london.encryption.algorithm via [core-site.xml]]
[007]  fs.s3a.encryption.key = "*************************************************************************" [75] [fs.s3a.bucket.stevel-london.encryption.key via [core-site.xml]]
[008]  fs.s3a.aws.credentials.provider = "
        org.apache.hadoop.fs.s3a.TemporaryAWSCredentialsProvider,
        org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider,
      " [core-site.xml]
[009]  fs.s3a.endpoint = "s3.eu-west-2.amazonaws.com" [fs.s3a.bucket.stevel-london.endpoint via [core-site.xml]]
[010]  fs.s3a.endpoint.region = (unset)
[011]  fs.s3a.endpoint.fips = (unset)
[012]  fs.s3a.signing-algorithm = (unset)
[013]  fs.s3a.aws.credentials.provider.mapping = (unset)
[014]  fs.s3a.acl.default = (unset)
[015]  fs.s3a.attempts.maximum = "2" [core-site.xml]
[016]  fs.s3a.authoritative.path = (unset)
[017]  fs.s3a.aws.credentials.provider.mapping = (unset)
[018]  fs.s3a.block.size = "32M" [core-default.xml]
[019]  fs.s3a.bucket.probe = "0" [core-site.xml]
[020]  fs.s3a.buffer.dir = "/tmp/hadoop-stevel/s3a" [core-default.xml]
[021]  fs.s3a.bulk.delete.page.size = (unset)
[022]  fs.s3a.change.detection.source = "versionid" [fs.s3a.bucket.stevel-london.change.detection.source via [core-site.xml]]
[023]  fs.s3a.change.detection.mode = "server" [core-default.xml]
[024]  fs.s3a.change.detection.version.required = "true" [core-default.xml]
[025]  fs.s3a.checksum.validation = (unset)
[026]  fs.s3a.connection.ssl.enabled = "true" [core-default.xml]
[027]  fs.s3a.connection.keepalive = (unset)
[028]  fs.s3a.connection.maximum = "512" [core-site.xml]
[029]  fs.s3a.connection.establish.timeout = "5s" [core-default.xml]
[030]  fs.s3a.connection.request.timeout = "300000" [core-site.xml]
[031]  fs.s3a.connection.timeout = "200s" [core-default.xml]
[032]  fs.s3a.create.performance = (unset)
[033]  fs.s3a.create.storage.class = (unset)
[034]  fs.s3a.cross.region.access.enabled = (unset)
[035]  fs.s3a.custom.signers = (unset)
[036]  fs.s3a.directory.operations.purge.uploads = "true" [core-site.xml]
[037]  fs.s3a.directory.marker.retention = "keep" [fs.s3a.bucket.stevel-london.directory.marker.retention via [core-site.xml]]
[038]  fs.s3a.downgrade.syncable.exceptions = "false" [core-site.xml]
[039]  fs.s3a.etag.checksum.enabled = "true" [core-site.xml]
[040]  fs.s3a.executor.capacity = "16" [core-default.xml]
[041]  fs.s3a.experimental.input.fadvise = (unset)
[042]  fs.s3a.input.async.drain.threshold = "1k" [core-site.xml]
[043]  fs.s3a.experimental.aws.s3.throttling = (unset)
[044]  fs.s3a.experimental.optimized.directory.operations = (unset)
[045]  fs.s3a.fast.buffer.size = (unset)
[046]  fs.s3a.fast.upload.buffer = "disk" [core-default.xml]
[047]  fs.s3a.fast.upload.active.blocks = "4" [core-default.xml]
[048]  fs.s3a.impl.disable.cache = (unset)
[049]  fs.s3a.list.version = "2" [core-default.xml]
[050]  fs.s3a.max.total.tasks = "32" [core-default.xml]
[051]  fs.s3a.multiobjectdelete.enable = "true" [core-default.xml]
[052]  fs.s3a.multipart.size = "64M" [core-default.xml]
[053]  fs.s3a.multipart.uploads.enabled = (unset)
[054]  fs.s3a.multipart.purge = "false" [core-site.xml]
[055]  fs.s3a.multipart.purge.age = "3600000" [core-site.xml]
[056]  fs.s3a.multipart.threshold = "128M" [core-default.xml]
[057]  fs.s3a.optimized.copy.from.local.enabled = (unset)
[058]  fs.s3a.copy.from.local.enabled = (unset)
[059]  fs.s3a.paging.maximum = "5000" [core-default.xml]
[060]  fs.s3a.prefetch.enabled = (unset)
[061]  fs.s3a.performance.flags = "*" [core-site.xml]
[062]  fs.s3a.prefetch.block.count = (unset)
[063]  fs.s3a.prefetch.block.size = (unset)
[064]  fs.s3a.path.style.access = "false" [core-site.xml]
[065]  fs.s3a.proxy.host = (unset)
[066]  fs.s3a.proxy.port = (unset)
[067]  fs.s3a.proxy.username = (unset)
[068]  fs.s3a.proxy.password = (unset)
[069]  fs.s3a.proxy.domain = (unset)
[070]  fs.s3a.proxy.workstation = (unset)
[071]  fs.s3a.rename.raises.exceptions = "true" [core-site.xml]
[072]  fs.s3a.readahead.range = "32k" [core-site.xml]
[073]  fs.s3a.retry.http.5xx.errors = (unset)
[074]  fs.s3a.retry.limit = "7" [core-default.xml]
[075]  fs.s3a.retry.interval = "500ms" [core-default.xml]
[076]  fs.s3a.retry.throttle.limit = "20" [core-default.xml]
[077]  fs.s3a.retry.throttle.interval = "100ms" [core-default.xml]
[078]  fs.s3a.ssl.channel.mode = "default_jsse" [core-default.xml]
[079]  fs.s3a.s3.client.factory.impl = (unset)
[080]  fs.s3a.threads.max = "256" [core-site.xml]
[081]  fs.s3a.threads.keepalivetime = "60s" [core-default.xml]
[082]  fs.s3a.user.agent.prefix = "APN/1.0" [fs.s3a.bucket.stevel-london.user.agent.prefix via [core-site.xml]]
[083]  fs.s3a.vectored.read.min.seek.size = (unset)
[084]  fs.s3a.vectored.read.max.merged.size = (unset)
[085]  fs.s3a.vectored.active.ranged.reads = (unset)
[086]  fs.s3a.assumed.role.arn = "arn:aws:iam::152813717728:role/stevel-assumed-role" [core-site.xml]
[087]  fs.s3a.assumed.role.sts.endpoint = "sts.eu-west-2.amazonaws.com" [core-site.xml]
[088]  fs.s3a.assumed.role.sts.endpoint.region = "eu-west-2" [core-site.xml]
[089]  fs.s3a.assumed.role.session.name = (unset)
[090]  fs.s3a.assumed.role.session.duration = "12h" [core-site.xml]
[091]  fs.s3a.assumed.role.credentials.provider = "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider" [core-default.xml]
[092]  fs.s3a.assumed.role.external.id = "arbitrary.value" [core-site.xml]
[093]  fs.s3a.assumed.role.policy = (unset)
[094]  fs.s3a.committer.name = "file" [core-default.xml]
[095]  fs.s3a.committer.magic.enabled = "true" [core-default.xml]
[096]  fs.s3a.committer.staging.abort.pending.uploads = (unset)
[097]  fs.s3a.committer.staging.conflict-mode = "append" [core-default.xml]
[098]  fs.s3a.committer.staging.tmp.path = "tmp/staging" [core-default.xml]
[099]  fs.s3a.committer.threads = "128" [core-site.xml]
[100]  fs.s3a.committer.staging.unique-filenames = "false" [core-site.xml]
[101]  mapreduce.outputcommitter.factory.scheme.s3a = "org.apache.hadoop.fs.s3a.commit.S3ACommitterFactory" [mapred-default.xml]
[102]  mapreduce.fileoutputcommitter.marksuccessfuljobs = (unset)
[103]  fs.s3a.delegation.token.binding = (unset)
[104]  fs.s3a.signature.cache.max.size = (unset)
[105]  fs.s3a.audit.enabled = "true" [core-site.xml]
[106]  fs.s3a.audit.referrer.enabled = (unset)
[107]  fs.s3a.audit.referrer.filter = (unset)
[108]  fs.s3a.audit.reject.out.of.span.operations = (unset)
[109]  fs.s3a.audit.request.handlers = (unset)
[110]  fs.s3a.audit.execution.interceptors = (unset)
[111]  fs.s3a.audit.service.classname = (unset)
[112]  fs.s3a.accesspoint.arn = (unset)
[113]  fs.s3a.accesspoint.required = "false" [core-default.xml]
[114]  fs.s3a.prefetch.enabled = (unset)
[115]  fs.s3a.prefetch.block.size = (unset)
[116]  fs.s3a.prefetch.block.count = (unset)
[117]  fs.hboss.data.uri = (unset)
[118]  fs.hboss.sync.impl = (unset)
[119]  fs.hboss.lock-wait.interval.warning = (unset)
[120]  fs.hboss.sync.zk.connectionString = (unset)
[121]  fs.hboss.sync.zk.sleep.base.ms = (unset)
[122]  fs.hboss.sync.zk.sleep.max.retries = (unset)

Required Classes
================

All these classes must be on the classpath

class: org.apache.hadoop.fs.s3a.S3AFileSystem
resource: org/apache/hadoop/fs/s3a/S3AFileSystem.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar!/org/apache/hadoop/fs/s3a/S3AFileSystem.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar
class: java.lang.System
resource: java/lang/System.class
       jar:file:/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home/jre/lib/rt.jar!/java/lang/System.class

Optional Classes
================

These classes are needed in some versions of Hadoop.
And/or for optional features to work.

class: com.amazonaws.services.s3.AmazonS3
resource: com/amazonaws/services/s3/AmazonS3.class
       Not found on classpath: com.amazonaws.services.s3.AmazonS3
class: com.amazonaws.ClientConfiguration
resource: com/amazonaws/ClientConfiguration.class
       Not found on classpath: com.amazonaws.ClientConfiguration
class: com.amazonaws.auth.EnvironmentVariableCredentialsProvider
resource: com/amazonaws/auth/EnvironmentVariableCredentialsProvider.class
       Not found on classpath: com.amazonaws.auth.EnvironmentVariableCredentialsProvider
class: com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
resource: com/amazonaws/services/securitytoken/AWSSecurityTokenServiceClient.class
       Not found on classpath: com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
class: com.amazonaws.regions.AwsRegionProvider
resource: com/amazonaws/regions/AwsRegionProvider.class
       Not found on classpath: com.amazonaws.regions.AwsRegionProvider
class: com.amazonaws.regions.AwsEnvVarOverrideRegionProvider
resource: com/amazonaws/regions/AwsEnvVarOverrideRegionProvider.class
       Not found on classpath: com.amazonaws.regions.AwsEnvVarOverrideRegionProvider
class: com.amazonaws.regions.AwsSystemPropertyRegionProvider
resource: com/amazonaws/regions/AwsSystemPropertyRegionProvider.class
       Not found on classpath: com.amazonaws.regions.AwsSystemPropertyRegionProvider
class: com.amazonaws.regions.InstanceMetadataRegionProvider
resource: com/amazonaws/regions/InstanceMetadataRegionProvider.class
       Not found on classpath: com.amazonaws.regions.InstanceMetadataRegionProvider
class: com.amazonaws.internal.TokenBucket
resource: com/amazonaws/internal/TokenBucket.class
       Not found on classpath: com.amazonaws.internal.TokenBucket
class: com.fasterxml.jackson.annotation.JacksonAnnotation
resource: com/fasterxml/jackson/annotation/JacksonAnnotation.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/common/lib/jackson-annotations-2.12.7.jar!/com/fasterxml/jackson/annotation/JacksonAnnotation.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/common/lib/jackson-annotations-2.12.7.jar
class: com.fasterxml.jackson.core.JsonParseException
resource: com/fasterxml/jackson/core/JsonParseException.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/common/lib/jackson-core-2.12.7.jar!/com/fasterxml/jackson/core/JsonParseException.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/common/lib/jackson-core-2.12.7.jar
class: com.fasterxml.jackson.databind.ObjectMapper
resource: com/fasterxml/jackson/databind/ObjectMapper.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/common/lib/jackson-databind-2.12.7.1.jar!/com/fasterxml/jackson/databind/ObjectMapper.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/common/lib/jackson-databind-2.12.7.1.jar
class: org.apache.hadoop.fs.s3a.commit.staging.StagingCommitter
resource: org/apache/hadoop/fs/s3a/commit/staging/StagingCommitter.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar!/org/apache/hadoop/fs/s3a/commit/staging/StagingCommitter.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar
class: org.apache.hadoop.fs.s3a.commit.magic.MagicS3GuardCommitter
resource: org/apache/hadoop/fs/s3a/commit/magic/MagicS3GuardCommitter.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar!/org/apache/hadoop/fs/s3a/commit/magic/MagicS3GuardCommitter.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar
class: org.apache.hadoop.fs.s3a.Invoker
resource: org/apache/hadoop/fs/s3a/Invoker.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar!/org/apache/hadoop/fs/s3a/Invoker.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar
class: org.apache.hadoop.fs.s3a.auth.AssumedRoleCredentialProvider
resource: org/apache/hadoop/fs/s3a/auth/AssumedRoleCredentialProvider.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar!/org/apache/hadoop/fs/s3a/auth/AssumedRoleCredentialProvider.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar
class: org.apache.hadoop.fs.s3a.auth.delegation.S3ADelegationTokens
resource: org/apache/hadoop/fs/s3a/auth/delegation/S3ADelegationTokens.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar!/org/apache/hadoop/fs/s3a/auth/delegation/S3ADelegationTokens.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar
class: com.amazonaws.services.s3.model.SelectObjectContentRequest
resource: com/amazonaws/services/s3/model/SelectObjectContentRequest.class
       Not found on classpath: com.amazonaws.services.s3.model.SelectObjectContentRequest
class: org.apache.hadoop.fs.s3a.select.SelectInputStream
resource: org/apache/hadoop/fs/s3a/select/SelectInputStream.class
       Not found on classpath: org.apache.hadoop.fs.s3a.select.SelectInputStream
class: org.apache.hadoop.fs.s3a.impl.RenameOperation
resource: org/apache/hadoop/fs/s3a/impl/RenameOperation.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar!/org/apache/hadoop/fs/s3a/impl/RenameOperation.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar
class: org.apache.hadoop.fs.s3a.impl.NetworkBinding
resource: org/apache/hadoop/fs/s3a/impl/NetworkBinding.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar!/org/apache/hadoop/fs/s3a/impl/NetworkBinding.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar
class: org.apache.hadoop.fs.s3a.impl.DirectoryPolicy
resource: org/apache/hadoop/fs/s3a/impl/DirectoryPolicy.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar!/org/apache/hadoop/fs/s3a/impl/DirectoryPolicy.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar
class: org.apache.hadoop.fs.s3a.audit.AuditManagerS3A
resource: org/apache/hadoop/fs/s3a/audit/AuditManagerS3A.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar!/org/apache/hadoop/fs/s3a/audit/AuditManagerS3A.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar
class: org.apache.hadoop.util.WeakReferenceMap
resource: org/apache/hadoop/util/WeakReferenceMap.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/common/hadoop-common-3.4.0-SNAPSHOT.jar!/org/apache/hadoop/util/WeakReferenceMap.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/common/hadoop-common-3.4.0-SNAPSHOT.jar
class: org.apache.hadoop.fs.EtagSource
resource: org/apache/hadoop/fs/EtagSource.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/common/hadoop-common-3.4.0-SNAPSHOT.jar!/org/apache/hadoop/fs/EtagSource.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/common/hadoop-common-3.4.0-SNAPSHOT.jar
class: org.apache.hadoop.fs.s3a.ArnResource
resource: org/apache/hadoop/fs/s3a/ArnResource.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar!/org/apache/hadoop/fs/s3a/ArnResource.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar
class: org.wildfly.openssl.OpenSSLProvider
resource: org/wildfly/openssl/OpenSSLProvider.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/wildfly-openssl-1.1.3.Final.jar!/org/wildfly/openssl/OpenSSLProvider.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/wildfly-openssl-1.1.3.Final.jar
class: com.sun.security.cert.internal.x509.X509V1CertImpl
resource: com/sun/security/cert/internal/x509/X509V1CertImpl.class
       jar:file:/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home/jre/lib/rt.jar!/com/sun/security/cert/internal/x509/X509V1CertImpl.class
class: org.apache.hadoop.fs.impl.prefetch.PrefetchingStatistics
resource: org/apache/hadoop/fs/impl/prefetch/PrefetchingStatistics.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/common/hadoop-common-3.4.0-SNAPSHOT.jar!/org/apache/hadoop/fs/impl/prefetch/PrefetchingStatistics.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/common/hadoop-common-3.4.0-SNAPSHOT.jar
class: org.apache.hadoop.fs.s3a.prefetch.S3ACachingBlockManager
resource: org/apache/hadoop/fs/s3a/prefetch/S3ACachingBlockManager.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar!/org/apache/hadoop/fs/s3a/prefetch/S3ACachingBlockManager.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar
class: org.apache.knox.gateway.cloud.idbroker.s3a.IDBDelegationTokenBinding
resource: org/apache/knox/gateway/cloud/idbroker/s3a/IDBDelegationTokenBinding.class
       Not found on classpath: org.apache.knox.gateway.cloud.idbroker.s3a.IDBDelegationTokenBinding
class: org.apache.ranger.raz.hook.s3.RazS3ADelegationTokenIdentifier
resource: org/apache/ranger/raz/hook/s3/RazS3ADelegationTokenIdentifier.class
       Not found on classpath: org.apache.ranger.raz.hook.s3.RazS3ADelegationTokenIdentifier
class: org.apache.hadoop.hbase.oss.HBaseObjectStoreSemantics
resource: org/apache/hadoop/hbase/oss/HBaseObjectStoreSemantics.class
       Not found on classpath: org.apache.hadoop.hbase.oss.HBaseObjectStoreSemantics
class: org.apache.hadoop.fs.store.s3a.DiagnosticsAWSCredentialsProvider
resource: org/apache/hadoop/fs/store/s3a/DiagnosticsAWSCredentialsProvider.class
       file:/var/folders/4n/w4cjr_d95kg9bxkl6sz3n3ym0000gr/T/hadoop-unjar6467007022610316910/org/apache/hadoop/fs/store/s3a/DiagnosticsAWSCredentialsProvider.class
       Not found on classpath: org.apache.hadoop.fs.store.s3a.DiagnosticsAWSCredentialsProvider
class: software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
resource: software/amazon/awssdk/auth/credentials/AwsCredentialsProvider.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/bundle-2.21.41.jar!/software/amazon/awssdk/auth/credentials/AwsCredentialsProvider.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/bundle-2.21.41.jar
class: software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
resource: software/amazon/awssdk/auth/credentials/EnvironmentVariableCredentialsProvider.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/bundle-2.21.41.jar!/software/amazon/awssdk/auth/credentials/EnvironmentVariableCredentialsProvider.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/bundle-2.21.41.jar
class: software.amazon.awssdk.core.exception.SdkException
resource: software/amazon/awssdk/core/exception/SdkException.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/bundle-2.21.41.jar!/software/amazon/awssdk/core/exception/SdkException.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/bundle-2.21.41.jar
class: software.amazon.awssdk.crt.s3.S3MetaRequest
resource: software/amazon/awssdk/crt/s3/S3MetaRequest.class
       Not found on classpath: software.amazon.awssdk.crt.s3.S3MetaRequest
class: software.amazon.eventstream.MessageDecoder
resource: software/amazon/eventstream/MessageDecoder.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/bundle-2.21.41.jar!/software/amazon/eventstream/MessageDecoder.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/bundle-2.21.41.jar
class: software.amazon.awssdk.transfer.s3.progress.TransferListener
resource: software/amazon/awssdk/transfer/s3/progress/TransferListener.class
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/bundle-2.21.41.jar!/software/amazon/awssdk/transfer/s3/progress/TransferListener.class
       file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/bundle-2.21.41.jar
class: software.amazon.awssdk.services.s3.s3express.S3ExpressConfiguration
resource: software/amazon/awssdk/services/s3/s3express/S3ExpressConfiguration.class
       Not found on classpath: software.amazon.awssdk.services.s3.s3express.S3ExpressConfiguration

At least one optional class was missing -the filesystem client *may* still work

Required Resources
==================

resource: mozilla/public-suffix-list.txt
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/common/lib/httpclient-4.5.13.jar!/mozilla/public-suffix-list.txt

Optional Resources
==================

resource: log4j.properties
       file:./home/steve/hadoop-3.4.0/etc/hadoop/log4j.properties
resource: com/amazonaws/internal/config/awssdk_config_default.json
       resource not found on classpath
resource: awssdk_config_override.json
       resource not found on classpath
resource: com/amazonaws/endpointdiscovery/endpoint-discovery.json
       resource not found on classpath
resource: software/amazon/awssdk/global/handlers/execution.interceptors
       resource not found on classpath
resource: software/amazon/awssdk/services/s3/execution.interceptors
       jar:file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/bundle-2.21.41.jar!/software/amazon/awssdk/services/s3/execution.interceptors

S3A Configuration validation
============================


Output Buffering
================

Buffer configuration option fs.s3a.buffer.dir = /tmp/hadoop-stevel/s3a
Number of buffer directories: 1
Buffer path /tmp/hadoop-stevel/s3a:
	* exists and is writable
	* contains 0 file(s) with total size 0 bytes

Attempting to create a temporary file

Temporary file successfully created in /tmp/hadoop-stevel/s3a

Encryption
==========


Endpoint validation
===================

fs.s3a.endpoint = "s3.eu-west-2.amazonaws.com"
fs.s3a.endpoint.region = ""
fs.s3a.path.style.access = "false"
fs.s3a.signing-algorithm = ""
fs.s3a.connection.ssl.enabled = "true"

Endpoint is an AWS S3 store
For reliable signing and performance the AWS region SHOULD be set in fs.s3a.endpoint.region

This client is configured to connect to AWS S3

Important: if you are working with a third party store,
Expect failure until fs.s3a.endpoint is set to the private endpoint

Bucket Name validation
======================

bucket name = "stevel-london"

Analyzing login credentials
===========================

access key "******************" [20]
Secret key length is 40 characters
Connector has access key, secret key and no session token

Configuration options with prefix fs.s3a.ext.
=============================================

fs.s3a.ext.multipart.copy.threshold="64M"

Performance Hints
=================

Option fs.s3a.threads.max is unset. Recommend a value of at least 512
Option fs.s3a.connection.maximum is unset. Recommend a value of at least 1024
Option fs.s3a.committer.threads is unset. Recommend a value of at least 256
Option fs.s3a.connection.timeout is unset. Recommend a value of at least 60000
Option fs.s3a.connection.establish.timeout is unset. Recommend a value of at least 60000
Option fs.s3a.connection.request.timeout is unset. Recommend a value of at least 900000

Seek policy: normal
===================

Policy starts 'sequential' and switches to 'random' on a backwards seek
This is adaptive and suitable for most workloads

Locating implementation class for Filesystem scheme s3a://
==========================================================

FileSystem for s3a:// is: org.apache.hadoop.fs.s3a.S3AFileSystem
Loaded from: file:./home/steve/hadoop-3.4.0/share/hadoop/tools/lib/hadoop-aws-3.4.0-SNAPSHOT.jar via sun.misc.Launcher$AppClassLoader@68de145

TLS System Properties
=====================

[001]  java.version = "1.8.0_362"
[002]  java.library.path = "/Users/stevel/Library/Java/Extensions:/Library/Java/Extensions:/Network/Library/Java/Extensions:/System/Library/Java/Extensions:/usr/lib/java:."
[003]  https.protocols = (unset)
[004]  javax.net.ssl.keyStore = (unset)
[005]  javax.net.ssl.keyStorePassword = (unset)
[006]  javax.net.ssl.trustStore = (unset)
[007]  javax.net.ssl.trustStorePassword = (unset)
[008]  jdk.certpath.disabledAlgorithms = (unset)
[009]  jdk.tls.client.cipherSuites = (unset)
[010]  jdk.tls.client.protocols = (unset)
[011]  jdk.tls.disabledAlgorithms = (unset)
[012]  jdk.tls.legacyAlgorithms = (unset)
[013]  com.sun.net.ssl.checkRevocation = (unset)


HTTPS supported protocols
=========================

    TLSv1.3
    TLSv1.2
    TLSv1.1
    TLSv1
    SSLv3
    SSLv2Hello

Endpoints
=========

Attempting to list and connect to public service endpoints,
without any authentication credentials.

- This is just testing the reachability of the URLs.
- If the request fails with any network error it is likely
  to be configuration problem with address, proxy, etc
- If it is some authentication error, then don't worry so much
    -look for the results of the filesystem operations

Endpoint: https://stevel-london.s3.eu-west-2.amazonaws.com/
===========================================================

Canonical hostname s3-r-w.eu-west-2.amazonaws.com
  IP address 52.95.191.2
Proxy: none

Connecting to https://stevel-london.s3.eu-west-2.amazonaws.com/

Response: 403 : Forbidden
HTTP response 403 from https://stevel-london.s3.eu-west-2.amazonaws.com/: Forbidden
Using proxy: false 
Transfer-Encoding: chunked
null: HTTP/1.1 403 Forbidden
Server: AmazonS3
x-amz-request-id: 67JNPPBCHP66FAEC
x-amz-id-2: klvUMp840bpltHMareDACPlgHocOC8I4dRKiK3tGse55goRTLYlytEn3NHPIZ21zVo3Gp36L3ME=
Date: Mon, 21 Oct 2024 19:07:07 GMT
x-amz-bucket-region: eu-west-2
Content-Type: application/xml

<?xml version="1.0" encoding="UTF-8"?>
<Error><Code>AccessDenied</Code><Message>Access Denied</Message><RequestId>67JNPPBCHP66FAEC</RequestId><HostId>klvUMp840bpltHMareDACPlgHocOC8I4dRKiK3tGse55goRTLYlytEn3NHPIZ21zVo3Gp36L3ME=</HostId></Error>


Endpoint: https://sts.eu-west-2.amazonaws.com/
==============================================

Canonical hostname 52.94.56.47
  IP address 52.94.56.47
Proxy: none

Connecting to https://sts.eu-west-2.amazonaws.com/

Response: 200 : Found
HTTP response 200 from https://sts.eu-west-2.amazonaws.com/: Found
Using proxy: false 
Transfer-Encoding: chunked
null: HTTP/1.1 200
X-Cache: Miss from cloudfront
Server: Server
vary: accept-encoding
X-Content-Type-Options: nosniff
X-Amz-Cf-Pop: FRA60-P1
Connection: keep-alive
Last-Modified: Mon, 21 Oct 2024 18:03:41 GMT
Date: Mon, 21 Oct 2024 19:07:09 GMT
Via: 1.1 d954dd318e06aa0e69375f36dcd819de.cloudfront.net (CloudFront)
X-Frame-Options: SAMEORIGIN
Strict-Transport-Security: max-age=63072000
X-Amz-Cf-Id: pzTgMUvxmBIR52jf3YxifuU9A37Xv4ACXmnKhCXwsidPrfAlvLQ2rw==
Set-Cookie: aws_lang=en; Domain=.amazon.com; Path=/,aws-priv=eyJ2IjoxLCJldSI6MCwic3QiOjB9; Version=1; Comment="Anonymous cookie for privacy regulations"; Domain=.aws.amazon.com; Max-Age=31536000; Expires=Tue, 21 Oct 2025 19:07:09 GMT; Path=/; Secure
x-amz-id-1: 5872E6FBCFB0454A867B
X-XSS-Protection: 1; mode=block
Content-Type: text/html;charset=UTF-8

<!doctype html>
<html class="no-js aws-lng-en_US aws-with-target" lang="en-US" data-static-assets="https://a0.awsstatic.com" data-js-version="1.0.590" data-css-version="1.0.506">
 <head> 
  <meta http-equiv="Content-Security-Policy" content="default-src 'self' data: https://a0.awsstatic.com https://prod.us-east-1.ui.gcr-chat.marketing.aws.dev; base-uri 'none'; connect-src 'self' https://*.analytics.console.aws.a2z.com https://*.panorama.console.api.aws https://*.prod.chc-features.uxplatform.aws.dev https://112-tzm-766.mktoresp.com https://112-tzm-766.mktoutil.com https://a0.awsstatic.com https://a0.p.awsstatic.com https://a1.awsstatic.com https://amazonwebservices.d2.sc.omtrdc.net https://amazonwebservicesinc.tt.omtrdc.net https://api.regional-table.region-services.aws.a2z.com https://api.us-west-2.prod.pricing.aws.a2z.com https://auth.aws.amazon.com https://aws.amazon.com https://aws.amazon.com/p/sf/ https://aws.demdex.net https://b0.p.awsstatic.com https://c0.b0.p.awsstatic.com https://calculator.aws https:

WARNING: this unauthenticated operation was not rejected.
 This may mean the store is world-readable.
 Check this by pasting https://sts.eu-west-2.amazonaws.com/ into your browser

Test filesystem s3a://stevel-london/temp/subdir
===============================================

Trying some operations against the filesystem
Starting with some read operations, then trying to write
[0m2024-10-21 20:07:09,746 [main] INFO  diag.StoreDiag (StoreDurationInfo.java:<init>(91)) - Starting: Creating filesystem for s3a://stevel-london/temp/subdir
[33;1m2024-10-21 20:07:09,908 [main] WARN  impl.ConfigurationHelper (LogExactlyOnce.java:warn(39)) - Option fs.s3a.connection.establish.timeout is too low (5,000 ms). Setting to 15,000 ms instead
[0m2024-10-21 20:07:10,363 [main] INFO  diag.StoreDiag (StoreDurationInfo.java:close(193)) - Duration of Creating filesystem for s3a://stevel-london/temp/subdir: 0:00:00.618
S3AFileSystem{uri=s3a://stevel-london, workingDir=s3a://stevel-london/user/stevel, partSize=67108864, enableMultiObjectsDelete=true, maxKeys=5000, OpenFileSupport{changePolicy=VersionIdChangeDetectionPolicy mode=Server, defaultReadAhead=32768, defaultBufferSize=4194304, defaultAsyncDrainThreshold=1024, defaultInputPolicy=default}, blockSize=33554432, multiPartThreshold=134217728, s3EncryptionAlgorithm='SSE_KMS', blockFactory=org.apache.hadoop.fs.s3a.S3ADataBlocks$DiskBlockFactory@226b143b, auditManager=Service ActiveAuditManagerS3A in state ActiveAuditManagerS3A: STARTED, auditor=LoggingAuditor{ID='b356eaa0-f54e-486a-a5c3-00f48f0d9b15', headerEnabled=true, rejectOutOfSpan=false, isMultipartUploadEnabled=true}}, authoritativePath=[], useListV1=false, magicCommitter=true, boundedExecutor=BlockingThreadPoolExecutorService{SemaphoredDelegatingExecutor{permitCount=544, available=544, waiting=0}, activeCount=0}, unboundedExecutor=java.util.concurrent.ThreadPoolExecutor@682bd3c4[Running, pool size = 0, active threads = 0, queued tasks = 0, completed tasks = 0], credentials=AWSCredentialProviderList name=; refcount= 1; size=2: [TemporaryAWSCredentialsProvider, SimpleAWSCredentialsProvider{accessKey.empty=false, secretKey.empty=false}], delegation tokens=disabled, DirectoryMarkerRetention{policy='keep'}, instrumentation {S3AInstrumentation{}}, ClientSideEncryption=false}
Implementation class class org.apache.hadoop.fs.s3a.S3AFileSystem

Path Capabilities
=================

fs.capability.directory.listing.inconsistent	false
fs.capability.etags.available	true
fs.capability.paths.checksums	true
fs.capability.multipart.uploader	true
fs.capability.outputstream.abortable	true
fs.s3a.capability.aws.v2	true
fs.s3a.capability.directory.marker.aware	true
fs.s3a.capability.directory.marker.policy.keep	true
fs.s3a.capability.directory.marker.policy.delete	false
fs.s3a.capability.directory.marker.policy.authoritative	false
fs.s3a.capability.directory.marker.action.keep	true
fs.s3a.capability.directory.marker.action.delete	false
fs.s3a.capability.multipart.uploads.enabled	true
fs.s3a.capability.magic.committer	true
fs.s3a.capability.select.sql	false
fs.s3a.capability.s3express.storage	false
fs.s3a.create.performance	true
fs.s3a.create.performance.enabled	false
fs.s3a.create.header	true
fs.s3a.directory.operations.purge.uploads	true
fs.s3a.endpoint.fips	false
fs.s3a.optimized.copy.from.local.enabled	true
org.apache.hadoop.hbase.hboss	false

Starting: Examine root path
root entry S3AFileStatus{path=s3a://stevel-london/; isDirectory=true; modification_time=0; access_time=0; owner=stevel; group=stevel; permission=rwxrwxrwx; isSymlink=false; hasAcl=false; isEncrypted=true; isErasureCoded=false} isEmptyDirectory=UNKNOWN eTag=null versionId=null
list /
ls / contains 5 entries; first entry s3a://stevel-london/bundle-2.24.6.jar	[558001584]
Duration of Examine root path: 0:00:00.473
Starting: First 25 entries of listStatus(s3a://stevel-london/temp/subdir)
Duration of First 25 entries of listStatus(s3a://stevel-london/temp/subdir): 0:00:00.206
Directory s3a://stevel-london/temp/subdir does not exist

Listing the directory s3a://stevel-london/temp/subdir has succeeded
===================================================================

The store is reachable and the client has list permissions

Attempt to read a file
======================

no file found to attempt to read

listfiles(s3a://stevel-london/temp/subdir, true)
================================================

Starting: First 25 entries of listFiles(s3a://stevel-london/temp/subdir)
Duration of First 25 entries of listFiles(s3a://stevel-london/temp/subdir): 0:00:00.211

Security and Delegation Tokens
==============================

Security is disabled
Filesystem s3a://stevel-london does not/is not configured to issue delegation tokens (at least while security is disabled)
Starting: probe for a directory which does not yet exist s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac
Duration of probe for a directory which does not yet exist s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac: 0:00:00.160

Filesystem Write Operations
===========================

Starting: creating a directory s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac
Duration of creating a directory s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac: 0:00:00.456
Starting: create directory s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac
Duration of create directory s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac: 0:00:00.128
Starting: probing path s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/file
Duration of probing path s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/file: 0:00:00.133
Starting: Creating file s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/file
Capabilities:
    fs.capability.outputstream.abortable
    iostatistics
    fs.capability.iocontext.supported

Writing data to s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/file
=============================================================================================

Stream rejects hsync() calls: java.lang.UnsupportedOperationException: S3A streams are not Syncable. See HADOOP-17597.
Output stream summary: FSDataOutputStream{wrappedStream=S3ABlockOutputStream{WriteOperationHelper {bucket=stevel-london}, blockSize=67108864, isMultipartUploadEnabled=true Statistics=counters=((object_multipart_initiated=0) (object_put_request=1) (action_executor_acquired.failures=0) (object_put_request.failures=0) (object_multipart_aborted.failures=0) (committer_magic_marker_put.failures=0) (multipart_upload_completed=0) (multipart_upload_part_put=0) (op_abort=0) (stream_write_queue_duration=0) (stream_write_exceptions_completing_upload=0) (multipart_upload_completed.failures=0) (committer_magic_marker_put=0) (op_abort.failures=0) (stream_write_total_data=7) (op_hflush=0) (stream_write_total_time=0) (object_multipart_initiated.failures=0) (multipart_upload_part_put.failures=0) (stream_write_bytes=7) (object_multipart_aborted=0) (op_hsync=1) (stream_write_exceptions=0) (stream_write_block_uploads=1) (action_executor_acquired=1));
gauges=((stream_write_block_uploads_data_pending=0) (stream_write_block_uploads_pending=1));
minimums=((object_multipart_initiated.min=-1) (object_multipart_aborted.min=-1) (action_executor_acquired.failures.min=-1) (action_executor_acquired.min=0) (multipart_upload_part_put.min=-1) (op_abort.min=-1) (object_multipart_aborted.failures.min=-1) (committer_magic_marker_put.failures.min=-1) (committer_magic_marker_put.min=-1) (object_multipart_initiated.failures.min=-1) (op_abort.failures.min=-1) (multipart_upload_completed.failures.min=-1) (object_put_request.failures.min=-1) (object_put_request.min=147) (multipart_upload_completed.min=-1) (multipart_upload_part_put.failures.min=-1));
maximums=((multipart_upload_part_put.failures.max=-1) (committer_magic_marker_put.failures.max=-1) (committer_magic_marker_put.max=-1) (object_multipart_aborted.max=-1) (object_multipart_initiated.failures.max=-1) (multipart_upload_completed.max=-1) (multipart_upload_part_put.max=-1) (object_multipart_aborted.failures.max=-1) (op_abort.max=-1) (object_put_request.failures.max=-1) (action_executor_acquired.failures.max=-1) (action_executor_acquired.max=0) (object_put_request.max=147) (object_multipart_initiated.max=-1) (multipart_upload_completed.failures.max=-1) (op_abort.failures.max=-1));
means=((multipart_upload_part_put.mean=(samples=0, sum=0, mean=0.0000)) (committer_magic_marker_put.mean=(samples=0, sum=0, mean=0.0000)) (object_put_request.mean=(samples=1, sum=147, mean=147.0000)) (object_multipart_initiated.mean=(samples=0, sum=0, mean=0.0000)) (multipart_upload_completed.failures.mean=(samples=0, sum=0, mean=0.0000)) (object_multipart_aborted.failures.mean=(samples=0, sum=0, mean=0.0000)) (object_multipart_aborted.mean=(samples=0, sum=0, mean=0.0000)) (op_abort.failures.mean=(samples=0, sum=0, mean=0.0000)) (op_abort.mean=(samples=0, sum=0, mean=0.0000)) (object_multipart_initiated.failures.mean=(samples=0, sum=0, mean=0.0000)) (multipart_upload_part_put.failures.mean=(samples=0, sum=0, mean=0.0000)) (action_executor_acquired.failures.mean=(samples=0, sum=0, mean=0.0000)) (action_executor_acquired.mean=(samples=1, sum=0, mean=0.0000)) (committer_magic_marker_put.failures.mean=(samples=0, sum=0, mean=0.0000)) (multipart_upload_completed.mean=(samples=0, sum=0, mean=0.0000)) (object_put_request.failures.mean=(samples=0, sum=0, mean=0.0000)));
}}
Duration of Creating file s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/file: 0:00:00.229
Starting: Listing  s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac
Duration of Listing  s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac: 0:00:00.066
Starting: Reading file s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/file
Capabilities:
    iostatistics
    fs.capability.iocontext.supported
    in:readahead
    in:unbuffer
    in:readvectored
input stream summary: org.apache.hadoop.fs.FSDataInputStream@42d73c61: S3AInputStream{s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/file wrappedStream=closed read policy=default pos=7 nextReadPos=7 contentLength=7 contentRangeStart=0 contentRangeFinish=7 remainingInCurrentRequest=0 ChangeTracker{VersionIdChangeDetectionPolicy mode=Server, revisionId='PS1tl.dDZ0SqXsLpGGOWdgcUFAxy.f7c'} VectoredIOContext{minSeekForVectorReads=4896, maxReadSizeForVectorReads=1253376}
StreamStatistics{counters=((stream_read_seek_operations=0) (stream_read_seek_policy_changed=1) (stream_read_opened=1) (stream_read_version_mismatches=0) (action_http_get_request.failures=0) (stream_read_remote_stream_drain=1) (stream_file_cache_eviction=0) (stream_read_prefetch_operations.failures=0) (stream_read_bytes_discarded_in_abort=0) (action_executor_acquired=0) (action_http_get_request=1) (stream_read_vectored_incoming_ranges=0) (stream_read_bytes=7) (stream_read_fully_operations=0) (stream_read_block_read=0) (stream_read_close_operations=1) (stream_read_prefetch_operations=0) (action_file_opened.failures=0) (stream_read_vectored_operations=0) (stream_read_seek_forward_operations=0) (action_executor_acquired.failures=0) (action_file_opened=1) (stream_file_cache_eviction.failures=0) (stream_read_seek_bytes_skipped=0) (stream_read_block_acquire_read.failures=0) (stream_read_seek_backward_operations=0) (stream_read_operations=1) (stream_read_vectored_read_bytes_discarded=0) (stream_read_remote_stream_aborted.failures=0) (stream_read_remote_stream_drain.failures=0) (stream_read_seek_bytes_discarded=0) (stream_evict_blocks_from_cache=0) (stream_read_remote_stream_aborted=0) (stream_read_bytes_backwards_on_seek=0) (stream_read_bytes_discarded_in_close=0) (stream_read_total_bytes=7) (stream_read_exceptions=0) (stream_aborted=0) (stream_read_closed=1) (stream_read_unbuffered=0) (stream_read_block_read.failures=0) (stream_read_operations_incomplete=0) (stream_read_vectored_combined_ranges=0) (stream_read_block_acquire_read=0));
gauges=((stream_read_active_memory_in_use=0) (stream_read_gauge_input_policy=0) (stream_read_active_prefetch_operations=0) (stream_read_blocks_in_cache=0));
minimums=((action_http_get_request.failures.min=-1) (action_file_opened.failures.min=-1) (stream_file_cache_eviction.failures.min=-1) (action_executor_acquired.failures.min=-1) (stream_file_cache_eviction.min=-1) (stream_read_block_read.min=-1) (stream_read_prefetch_operations.min=-1) (action_file_opened.min=64) (stream_read_remote_stream_aborted.failures.min=-1) (stream_read_remote_stream_drain.failures.min=-1) (stream_read_block_acquire_read.min=-1) (stream_read_remote_stream_drain.min=0) (action_executor_acquired.min=-1) (stream_read_block_acquire_read.failures.min=-1) (action_http_get_request.min=91) (stream_read_block_read.failures.min=-1) (stream_read_remote_stream_aborted.min=-1) (stream_read_prefetch_operations.failures.min=-1));
maximums=((stream_read_remote_stream_aborted.max=-1) (stream_read_remote_stream_drain.failures.max=-1) (stream_read_remote_stream_aborted.failures.max=-1) (stream_read_block_read.max=-1) (action_file_opened.max=64) (stream_file_cache_eviction.max=-1) (action_http_get_request.max=91) (action_http_get_request.failures.max=-1) (stream_read_block_acquire_read.failures.max=-1) (stream_file_cache_eviction.failures.max=-1) (action_file_opened.failures.max=-1) (stream_read_prefetch_operations.max=-1) (action_executor_acquired.failures.max=-1) (action_executor_acquired.max=-1) (stream_read_block_read.failures.max=-1) (stream_read_remote_stream_drain.max=0) (stream_read_prefetch_operations.failures.max=-1) (stream_read_block_acquire_read.max=-1));
means=((action_http_get_request.failures.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_prefetch_operations.mean=(samples=0, sum=0, mean=0.0000)) (stream_file_cache_eviction.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_block_read.failures.mean=(samples=0, sum=0, mean=0.0000)) (action_executor_acquired.mean=(samples=0, sum=0, mean=0.0000)) (action_executor_acquired.failures.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_block_acquire_read.failures.mean=(samples=0, sum=0, mean=0.0000)) (stream_file_cache_eviction.failures.mean=(samples=0, sum=0, mean=0.0000)) (action_file_opened.mean=(samples=1, sum=64, mean=64.0000)) (stream_read_remote_stream_aborted.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_remote_stream_drain.mean=(samples=1, sum=0, mean=0.0000)) (stream_read_block_read.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_block_acquire_read.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_remote_stream_drain.failures.mean=(samples=0, sum=0, mean=0.0000)) (action_file_opened.failures.mean=(samples=0, sum=0, mean=0.0000)) (action_http_get_request.mean=(samples=1, sum=91, mean=91.0000)) (stream_read_remote_stream_aborted.failures.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_prefetch_operations.failures.mean=(samples=0, sum=0, mean=0.0000)));
}}
Duration of Reading file s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/file: 0:00:00.173
File modtime after creation = 862 millis,
	after close invoked = 795 millis
	after close completed = 635 millis
Timestamp of created file is 862 milliseconds after the local clock
The file timestamp is closer to the write completion time.
If the store is an object store, the object is
likely to have been created at the end of the write

Reviewing bucket versioning
===========================

The bucket is using versioning.
directory marker retention is enabled, so performance will suffer less


Renaming
========

Starting: Renaming file s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/file under s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/subdir
Duration of Renaming file s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/file under s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/subdir: 0:00:01.476
Starting: probing path s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/subdir/subfile
Duration of probing path s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/subdir/subfile: 0:00:00.133
Starting: delete dir s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/subdir2
Duration of delete dir s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/subdir2: 0:00:00.864
Starting: probing path s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/subdir2
Duration of probing path s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac/subdir2: 0:00:00.157

All read and write operations succeeded: good to go
===================================================

Starting: delete directory s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac
Duration of delete directory s3a://stevel-london/temp/subdir/dir-026f7908-5ca2-4df7-8c26-4bb76c345eac: 0:00:00.337
Starting: delete directory s3a://stevel-london/temp/subdir
Duration of delete directory s3a://stevel-london/temp/subdir: 0:00:00.266

JVM: memory=403383544

Success!
========


```