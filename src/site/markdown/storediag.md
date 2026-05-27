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

The `storediag` entry point will, given a URL to a store (cloud, local file://, hdfs):
1. Pick up the FS settings, print them
with all secrets sanitized partially obfuscated, and display their provenance. 
2. Print system properties and environment variables relevant to the target filesystem.
3. Looks for required classes, prints their location or fails if they are not found
4. Looks for optional classes, and if found, prints their location
5. Bootstraps connectivity with an attempt to initiate (unauthed) HTTP connections
to the store's endpoints. This should be sufficient to detect proxy and
endpoint configuration problems.
6. Tries to perform a listing of the store and read the first few bytes of any file it finds. If this fails, then there's clearly a problem.
   Hopefully though, there's now enough information to begin determining what it is.
7. Optionally, writes can also be attempted.

Iif things do fail, the printed configuration obfuscates the login secrets,
and any other property considered sensitive.
This is to support safer reporting of issues in bug reports within an organisation, and with third-parties whom you trust.
It must still be considered insufficient obfuscation to permit a diagnostics report to be shared publicly, as it will leak information about your target store and client configuration.

```bash
# diagnose a 
hadoop jar cloudstore-1.3.jar storediag s3a://noaa-cors-pds/raw/2023/017/
hadoop jar cloudstore-1.3.jar storediag -w --tokenfile mytokens.bin s3a://my-readwrite-bucket/subdirectory
hadoop jar cloudstore-1.3.jar storediag -w --tokenfile mytokens.bin hdfs://namenode/user/alice/subdir
hadoop jar cloudstore-1.3.jar storediag abfs://container@user/
```

The remote store is required to grant read access to the caller.
If the `-w` option is provided, the caller must have write permission for the target directory.

## Important: storediag does not collect information across the entire cluster.

The `storediag` command only collects and reports the connectivity status *on the host it is running on*.

It doesn't run across the cluster, therefore *must be executed on the host which is exhibiting problems*

Running it on a different host will report the configuration details, but otherwise is not very informative.

### Usage

```
Usage: storediag [options] <filesystem>
        -D <key=value>  Define a single configuration option
        -sysprop <file> Property file of system properties
        -tokenfile <file>       Hadoop token file to load
        -xmlfile <file> XML config file to load
        -verbose        verbose output
        -debug  enable JVM logs (ALL) and override log4j levels (DEBUG) on specified packages or classes
        -logoverrides <file>    A newline separated list of package and class names
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
        -logfile <file> also write the diagnostics output to <file>

```

The `-required` option takes a text file where every line is one of
a #-prefixed comment, a blank line, a classname, a resource (with "/" in).
These are all loaded

```bash
hadoop jar cloudstore-1.3.jar storediag -5 -required required.txt s3a://something/
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
bin/hadoop jar cloudstore-1.3.jar storediag -w s3a://example-london/temp/subdir > output 2>&1
```

This does diagnostics test including a write against the bucket
`s3a://example-london`, working wih the
subdirectory `temp/subdir`

The `-w` option indicates that a file write followed by a rename shall be attempted after
a list and any read of an existing file.

The `-h` option obfuscates all secrets. Store names and paths should still be
reviewed to make sure they do not leak information.


### Full Successful Run: S3A
Here is a genuine test against a London store.

```

1. Store Diagnostics for alice (auth:SIMPLE) on VXM63P4JG2/192.168.50.139
==========================================================================

Collected at at 2026-05-27T18:16:58.695126Z


2. Diagnostics for filesystem s3a://alice-london/temp/subdir
=============================================================

S3A FileSystem Connector
ASF Filesystem Connector to Amazon S3 Storage and compatible stores
https://hadoop.apache.org/docs/current/hadoop-aws/tools/hadoop-aws/index.html

3. Hadoop information
=====================

  Hadoop 3.4.3
  Compiled by alice on 2026-02-13T14:23Z
  Compiled with protoc 3.23.4
  From source with checksum 2331238c4c2929e66645316a32a8613

4. Determining OS version
=========================

Darwin VXM63P4JG2 25.4.0 Darwin Kernel Version 25.4.0: Thu Mar 19 19:30:44 PDT 2026; root:xnu-12377.101.15~1/RELEASE_ARM64_T6000 x86_64

5. Selected System Properties
=============================

[001]  aws.accessKeyId = (unset)
[002]  aws.secretKey = (unset)
[003]  aws.sessionToken = (unset)
[004]  aws.accountIdEndpointMode = (unset)
[005]  aws.binaryIonEnabled = (unset)
[006]  aws.cborEnabled = (unset)
[007]  aws.configFile = (unset)
[008]  aws.containerAuthorizationToken = (unset)
[009]  aws.containerAuthorizationTokenFile = (unset)
[010]  aws.containerCredentialsFullUri = (unset)
[011]  aws.containerCredentialsPath = (unset)
[012]  aws.containerServiceEndpoint = (unset)
[013]  aws.defaultsMode = (unset)
[014]  aws.disableEc2Metadata = (unset)
[015]  aws.disableEc2MetadataV1 = (unset)
[016]  aws.disableRequestCompression = (unset)
[017]  aws.disableS3ExpressAuth = (unset)
[018]  aws.ec2MetadataServiceEndpoint = (unset)
[019]  aws.ec2MetadataServiceEndpointMode = (unset)
[020]  aws.ec2MetadataServiceTimeout = (unset)
[021]  aws.endpointDiscoveryEnabled = (unset)
[022]  aws.executionEnvironment = (unset)
[023]  aws.maxAttempts = (unset)
[024]  aws.partitionsFile = (unset)
[025]  aws.profile = (unset)
[026]  aws.region = (unset)
[027]  aws.requestChecksumCalculation = (unset)
[028]  aws.responseChecksumValidation = (unset)
[029]  aws.requestMinCompressionSizeBytes = (unset)
[030]  aws.retryMode = (unset)
[031]  aws.roleArn = (unset)
[032]  aws.roleSessionName = (unset)
[033]  aws.s3UseUsEast1RegionalEndpoint = (unset)
[034]  aws.secretAccessKey = (unset)
[035]  aws.sharedCredentialsFile = (unset)
[036]  aws.useDualstackEndpoint = (unset)
[037]  aws.useFipsEndpoint = (unset)
[038]  aws.webIdentityTokenFile = (unset)
[039]  org.wildfly.openssl.path = (unset)
[040]  org.wildfly.openssl.libwfssl.path = (unset)
[041]  sdk.ua.appId = (unset)
[042]  software.amazon.awssdk.http.service.impl = (unset)
[043]  software.amazon.awssdk.http.async.service.impl = (unset)
[044]  java.version = "17.0.17"
[045]  java.specification.version = "17"
[046]  java.class.version = "61.0"
[047]  https.proxyHost = (unset)
[048]  https.proxyPort = (unset)
[049]  https.nonProxyHosts = (unset)
[050]  https.proxyPassword = (unset)
[051]  http.proxyHost = (unset)
[052]  http.keepAlive = (unset)
[053]  http.proxyPort = (unset)
[054]  http.proxyPassword = (unset)
[055]  http.nonProxyHosts = (unset)
[056]  java.net.preferIPv4Stack = "true"
[057]  java.net.preferIPv6Addresses = (unset)
[058]  jsse.enableSNIExtension = (unset)
[059]  networkaddress.cache.ttl = (unset)
[060]  networkaddress.cache.negative.ttl = (unset)
[061]  socksProxyHost = (unset)
[062]  socksProxyPort = (unset)
[063]  sun.net.client.defaultConnectTimeout = (unset)
[064]  sun.net.client.defaultReadTimeout = (unset)
[065]  sun.net.inetaddr.ttl = (unset)
[066]  sun.net.inetaddr.negative.ttl = (unset)

6. JVM Security Properties
==========================

[001]  jdk.certpath.disabledAlgorithms = "MD2, MD5, SHA1 jdkCA & usage TLSServer, RSA keySize < 1024, DSA keySize < 1024, EC keySize < 224, SHA1 usage SignedJAR & denyAfter 2019-01-01"
[002]  jdk.tls.disabledAlgorithm = (unset)
[003]  jdk.tls.keyLimits = "AES/GCM/NoPadding KeyUpdate 2^37, ChaCha20-Poly1305 KeyUpdate 2^37"
[004]  networkaddress.cache.ttl = (unset)
[005]  ssl.KeyManagerFactory = (unset)
[006]  ssl.KeyManagerFactory.algorithm = "SunX509"
[007]  ssl.TrustManagerFactory = (unset)

7. Environment Variables
========================

[001]  AWS_ACCESS_KEY_ID = (unset)
[002]  AWS_ACCESS_KEY = (unset)
[003]  AWS_SECRET_KEY = (unset)
[004]  AWS_SECRET_ACCESS_KEY = (unset)
[005]  AWS_SESSION_TOKEN = (unset)
[006]  AWS_REGION = (unset)
[007]  AWS_CA_BUNDLE = (unset)
[008]  AWS_CBOR_DISABLE = (unset)
[009]  AWS_CLI_AUTO_PROMPT = (unset)
[010]  AWS_CLI_FILE_ENCODING = (unset)
[011]  AWS_CLI_S3_MV_VALIDATE_SAME_S3_PATHS = (unset)
[012]  AWS_CONFIG_FILE = (unset)
[013]  AWS_CONTAINER_AUTHORIZATION_TOKEN = (unset)
[014]  AWS_CONTAINER_CREDENTIALS_FULL_URI = (unset)
[015]  AWS_CONTAINER_CREDENTIALS_RELATIVE_URI = (unset)
[016]  AWS_CSM_CLIENT_ID = (unset)
[017]  AWS_CSM_HOST = (unset)
[018]  AWS_CSM_PORT = (unset)
[019]  AWS_DATA_PATH = (unset)
[020]  AWS_DEFAULT_OUTPUT = (unset)
[021]  AWS_DEFAULT_REGION = (unset)
[022]  AWS_DEFAULTS_MODE = (unset)
[023]  AWS_EC2_METADATA_DISABLED = "true"
[024]  AWS_EC2_METADATA_SERVICE_ENDPOINT = (unset)
[025]  AWS_ENDPOINT_URL = (unset)
[026]  AWS_ENDPOINT_URL_S3 = (unset)
[027]  AWS_IGNORE_CONFIGURED_ENDPOINT_URLS = (unset)
[028]  AWS_JAVA_V1_DISABLE_DEPRECATION_ANNOUNCEMENT = (unset)
[029]  AWS_JAVA_V1_PRINT_LOCATION = (unset)
[030]  AWS_MAX_ATTEMPTS = (unset)
[031]  AWS_METADATA_SERVICE_TIMEOUT = (unset)
[032]  AWS_PROFILE = (unset)
[033]  AWS_REQUEST_CHECKSUM_CALCULATION = (unset)
[034]  AWS_RESPONSE_CHECKSUM_VALIDATION = (unset)
[035]  AWS_RETRY_MODE = (unset)
[036]  AWS_ROLE_ARN = (unset)
[037]  AWS_ROLE_SESSION_NAME = (unset)
[038]  AWS_SDK_UA_APP_ID = (unset)
[039]  AWS_S3_US_EAST_1_REGIONAL_ENDPOINT = (unset)
[040]  AWS_SHARED_CREDENTIALS_FILE = (unset)
[041]  AWS_SIGV4A_SIGNING_REGION_SET = (unset)
[042]  AWS_USE_DUALSTACK_ENDPOINT = (unset)
[043]  AWS_USE_FIPS_ENDPOINT = (unset)
[044]  AWS_WEB_IDENTITY_TOKEN_FILE = (unset)
[045]  PATH = "/Users/alice/.cargo/bin:/Users/alice/bin/google-cloud-sdk/bin:/Users/alice/.local/bin:/Users/alice/Library/Python/3.9/bin:/Users/alice/java/maven/bin:/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home:/usr/local/smlnj/bin:~/.local/bin:/Users/alice/bin:/Users/alice/bin/scripts:/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin:/System/Cryptexes/App/usr/bin:/usr/bin:/bin:/usr/sbin:/sbin:/var/run/com.apple.security.cryptexd/codex.system/bootstrap/usr/local/bin:/var/run/com.apple.security.cryptexd/codex.system/bootstrap/usr/bin:/var/run/com.apple.security.cryptexd/codex.system/bootstrap/usr/appleinternal/bin:/opt/pkg/env/active/bin:/opt/pmk/env/global/bin:/opt/X11/bin:/Library/Apple/usr/bin:/usr/local/MacGPG2/bin:/Applications/Privileges.app/Contents/MacOS:/Library/TeX/texbin:/Applications/Wireshark.app/Contents/MacOS:/usr/local/share/dotnet:~/.dotnet/tools:/Users/alice/.cargo/bin:./bin:/Users/alice/bin/port/venv_port/bin:/Users/alice/Projects/gocode/bin"
[046]  HADOOP_CONF_DIR = "/Users/alice/Projects/Releases/hadoop-3.4.3/etc/hadoop"
[047]  HADOOP_CLASSPATH = (unset)
[048]  HADOOP_CLIENT_OPTS = (unset)
[049]  HADOOP_CREDSTORE_PASSWORD = (unset)
[050]  HADOOP_HEAPSIZE = (unset)
[051]  HADOOP_HEAPSIZE_MIN = (unset)
[052]  HADOOP_HOME = "/Users/alice/Projects/Releases/hadoop-3.4.3"
[053]  HADOOP_LOG_DIR = (unset)
[054]  HADOOP_OPTIONAL_TOOLS = "hadoop-azure,hadoop-aws"
[055]  HADOOP_OPTS = "-Djava.net.preferIPv4Stack=true  -Dyarn.log.dir=/Users/alice/Projects/Releases/hadoop-3.4.3/logs -Dyarn.log.file=hadoop.log -Dyarn.home.dir=/Users/alice/Projects/Releases/hadoop-3.4.3 -Dyarn.root.logger=INFO,console -Djava.library.path=/Users/alice/Projects/Releases/hadoop-3.4.3/lib/native -Dhadoop.log.dir=/Users/alice/Projects/Releases/hadoop-3.4.3/logs -Dhadoop.log.file=hadoop.log -Dhadoop.home.dir=/Users/alice/Projects/Releases/hadoop-3.4.3 -Dhadoop.id.str=alice -Dhadoop.root.logger=INFO,console -Dhadoop.policy.file=hadoop-policy.xml -Dhadoop.security.logger=INFO,NullAppender -XX:+IgnoreUnrecognizedVMOptions --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.math=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.text=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.zip=ALL-UNNAMED --add-opens=java.base/sun.security.util=ALL-UNNAMED --add-opens=java.base/sun.security.x509=ALL-UNNAMED"
[056]  HADOOP_SHELL_SCRIPT_DEBUG = (unset)
[057]  HADOOP_TOKEN = (unset)
[058]  HADOOP_TOKEN_FILE_LOCATION = (unset)
[059]  HADOOP_KEYSTORE_PASSWORD = (unset)
[060]  HADOOP_TOOLS_HOME = (unset)
[061]  HADOOP_TOOLS_OPTIONS = (unset)
[062]  HADOOP_YARN_HOME = "/Users/alice/Projects/Releases/hadoop-3.4.3"
[063]  HDP_VERSION = (unset)
[064]  JAVA_HOME = "/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home"
[065]  LD_LIBRARY_PATH = (unset)
[066]  LD_PRELOAD = (unset)
[067]  LOCAL_DIRS = (unset)
[068]  OPENSSL_ROOT_DIR = "/usr/local/opt/openssl/"
[069]  OPENSSL_CONF = (unset)
[070]  OPENSSL_CONF_INCLUDE = (unset)
[071]  OPENSSL_MODULES = (unset)
[072]  OPENSSL_TRACE = (unset)
[073]  PYSPARK_DRIVER_PYTHON = (unset)
[074]  SASL_MECHANISM = (unset)
[075]  SPARK_HOME = (unset)
[076]  SPARK_CONF_DIR = (unset)
[077]  SPARK_SCALA_VERSION = (unset)
[078]  YARN_CONF_DIR = (unset)
[079]  http_proxy = (unset)
[080]  https_proxy = (unset)
[081]  no_proxy = (unset)

8. Hadoop XML Configurations
============================

resource: core-default.xml
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/hadoop-common-3.4.3.jar!/core-default.xml
resource: core-site.xml
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/etc/hadoop/core-site.xml
resource: hdfs-default.xml
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/hdfs/hadoop-hdfs-3.4.3.jar!/hdfs-default.xml
resource: hdfs-site.xml
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/etc/hadoop/hdfs-site.xml
resource: mapred-default.xml
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/mapreduce/hadoop-mapreduce-client-core-3.4.3.jar!/mapred-default.xml
resource: mapred-site.xml
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/etc/hadoop/mapred-site.xml
resource: yarn-default.xml
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/yarn/hadoop-yarn-common-3.4.3.jar!/yarn-default.xml
resource: yarn-site.xml
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/etc/hadoop/yarn-site.xml

9. Security
===========

Security Enabled: false
Keytab login: false
Ticket login: false
Current user: alice (auth:SIMPLE)
Token count: 0

10. Hadoop Options
==================

[001]  fs.defaultFS = "file:///" [core-default.xml]
[002]  fs.default.name = "file:///" 
[003]  fs.creation.parallel.count = "64" [core-default.xml]
[004]  fs.permissions.umask-mode = "022" [core-default.xml]
[005]  fs.trash.classname = (unset)
[006]  fs.trash.interval = "0" [core-default.xml]
[007]  fs.trash.checkpoint.interval = "0" [core-default.xml]
[008]  fs.file.impl = (unset)
[009]  hadoop.tmp.dir = "/tmp/hadoop-alice" [core-default.xml]; ("/tmp/hadoop-${user.name}")
[010]  hdp.version = (unset)
[011]  yarn.resourcemanager.address = "0.0.0.0:8032" [yarn-default.xml]; ("${yarn.resourcemanager.hostname}:8032")
[012]  yarn.resourcemanager.principal = (unset)
[013]  yarn.resourcemanager.webapp.address = "0.0.0.0:8088" [yarn-default.xml]; ("${yarn.resourcemanager.hostname}:8088")
[014]  yarn.resourcemanager.webapp.https.address = "0.0.0.0:8090" [yarn-default.xml]; ("${yarn.resourcemanager.hostname}:8090")
[015]  mapreduce.input.fileinputformat.list-status.num-threads = "1" [mapred-default.xml]
[016]  mapreduce.jobtracker.kerberos.principal = (unset)
[017]  mapreduce.job.hdfs-servers.token-renewal.exclude = (unset)
[018]  mapreduce.application.framework.path = (unset)
[019]  fs.iostatistics.logging.level = "info" [core-site.xml]
[020]  fs.iostatistics.thread.level.enabled = "true" [core-default.xml]
[021]  parquet.hadoop.vectored.io.enabled = (unset)

11. Security Options
====================

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

12. Selected Configuration Options
==================================

[001]  fs.s3a.access.key = "AK**************IO47" [20] [core-site.xml]
[002]  fs.s3a.secret.key = "ow**********************************qFwA" [40] [core-site.xml]
[003]  fs.s3a.session.token = (unset)
[004]  fs.s3a.server-side-encryption-algorithm = (unset)
[005]  fs.s3a.server-side-encryption.key = (unset)
[006]  fs.s3a.encryption.algorithm = "SSE-KMS" [fs.s3a.bucket.alice-london.encryption.algorithm via [core-site.xml]]
[007]  fs.s3a.encryption.key = "ar*********************************************************************4443" [75] [fs.s3a.bucket.alice-london.encryption.key via [core-site.xml]]
[008]  fs.s3a.encryption.cse.kms.region = "eu-west-2" [fs.s3a.bucket.alice-london.encryption.cse.kms.region via [core-site.xml]]
[009]  fs.s3a.aws.credentials.provider = "
        org.apache.hadoop.fs.s3a.TemporaryAWSCredentialsProvider,
        org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider,
      " [core-site.xml]
[010]  fs.s3a.endpoint = (unset)
[011]  fs.s3a.endpoint.region = "eu-west-2" [fs.s3a.bucket.alice-london.endpoint.region via [core-site.xml]]; ("${london.region}")
[012]  fs.s3a.endpoint.fips = (unset)
[013]  fs.s3a.signing-algorithm = (unset)
[014]  fs.s3a.aws.credentials.provider.mapping = (unset)
[015]  fs.s3a.acl.default = (unset)
[016]  fs.s3a.attempts.maximum = "2" [core-site.xml]
[017]  fs.s3a.authoritative.path = (unset)
[018]  fs.s3a.aws.credentials.provider.mapping = (unset)
[019]  fs.s3a.block.size = "32M" [core-default.xml]
[020]  fs.s3a.bucket.probe = "0" [core-site.xml]
[021]  fs.s3a.buffer.dir = "/tmp/hadoop-alice/s3a" [fs.s3a.bucket.alice-london.buffer.dir via [core-site.xml]]; ("${env.LOCAL_DIRS:-${hadoop.tmp.dir}}/s3a")
[022]  fs.s3a.bulk.delete.page.size = (unset)
[023]  fs.s3a.change.detection.source = "etag" [core-default.xml]
[024]  fs.s3a.change.detection.mode = "server" [core-default.xml]
[025]  fs.s3a.change.detection.version.required = "true" [core-default.xml]
[026]  fs.s3a.checksum.generation = (unset)
[027]  fs.s3a.checksum.validation = (unset)
[028]  fs.s3a.classloader.isolation = (unset)
[029]  fs.s3a.connection.ssl.enabled = "true" [core-default.xml]
[030]  fs.s3a.connection.keepalive = (unset)
[031]  fs.s3a.connection.maximum = "512" [core-site.xml]
[032]  fs.s3a.connection.acquisition.timeout = (unset)
[033]  fs.s3a.connection.establish.timeout = "30s" [core-default.xml]
[034]  fs.s3a.connection.expect.continue = "false" [fs.s3a.bucket.alice-london.connection.expect.continue via [core-site.xml]]
[035]  fs.s3a.connection.idle.time = (unset)
[036]  fs.s3a.connection.part.upload.timeout = "15m" [core-site.xml]
[037]  fs.s3a.connection.request.timeout = (unset)
[038]  fs.s3a.connection.timeout = "15000" [core-site.xml]
[039]  fs.s3a.connection.ttl = "5m" [core-default.xml]
[040]  fs.s3a.create.checksum.algorithm = (unset)
[041]  fs.s3a.create.conditional.enabled = (unset)
[042]  fs.s3a.create.performance = (unset)
[043]  fs.s3a.create.storage.class = (unset)
[044]  fs.s3a.cross.region.access.enabled = (unset)
[045]  fs.s3a.custom.signers = (unset)
[046]  fs.s3a.http.signer.enabled = (unset)
[047]  fs.s3a.http.signer.class = (unset)
[048]  fs.s3a.directory.operations.purge.uploads = (unset)
[049]  fs.s3a.directory.marker.retention = "keep" [fs.s3a.bucket.alice-london.directory.marker.retention via [core-site.xml]]
[050]  fs.s3a.downgrade.syncable.exceptions = "true" [core-default.xml]
[051]  fs.s3a.etag.checksum.enabled = "true" [core-site.xml]
[052]  fs.s3a.executor.capacity = "16" [core-default.xml]
[053]  fs.s3a.experimental.input.fadvise = (unset)
[054]  fs.s3a.input.async.drain.threshold = "1k" [core-site.xml]
[055]  fs.s3a.experimental.aws.s3.throttling = (unset)
[056]  fs.s3a.experimental.optimized.directory.operations = (unset)
[057]  fs.s3a.fast.buffer.size = (unset)
[058]  fs.s3a.fast.upload.buffer = "disk" [core-default.xml]
[059]  fs.s3a.fast.upload.active.blocks = "4" [core-site.xml]
[060]  fs.s3a.impl.disable.cache = (unset)
[061]  fs.s3a.input.stream.type = (unset)
[062]  fs.s3a.list.version = "2" [core-default.xml]
[063]  fs.s3a.max.total.tasks = "32" [core-default.xml]
[064]  fs.s3a.multiobjectdelete.enable = "true" [core-default.xml]
[065]  fs.s3a.multipart.size = "32M" [core-site.xml]
[066]  fs.s3a.multipart.uploads.enabled = (unset)
[067]  fs.s3a.multipart.purge = "false" [core-site.xml]
[068]  fs.s3a.multipart.purge.age = "24h" [core-default.xml]
[069]  fs.s3a.multipart.threshold = "32M" [core-site.xml]; ("${fs.s3a.multipart.size}")
[070]  fs.s3a.optimized.copy.from.local.enabled = (unset)
[071]  fs.s3a.copy.from.local.enabled = (unset)
[072]  fs.s3a.paging.maximum = "5000" [core-default.xml]
[073]  fs.s3a.prefetch.enabled = (unset)
[074]  fs.s3a.performance.flags = (unset)
[075]  fs.s3a.prefetch.block.count = (unset)
[076]  fs.s3a.prefetch.block.size = (unset)
[077]  fs.s3a.path.style.access = "false" [core-default.xml]
[078]  fs.s3a.proxy.host = (unset)
[079]  fs.s3a.proxy.port = (unset)
[080]  fs.s3a.proxy.username = (unset)
[081]  fs.s3a.proxy.password = (unset)
[082]  fs.s3a.proxy.domain = (unset)
[083]  fs.s3a.proxy.workstation = (unset)
[084]  fs.s3a.rename.raises.exceptions = "true" [core-site.xml]
[085]  fs.s3a.readahead.range = "32k" [core-site.xml]
[086]  fs.s3a.request.md5.header = (unset)
[087]  fs.s3a.retry.http.5xx.errors = (unset)
[088]  fs.s3a.retry.limit = "7" [core-default.xml]
[089]  fs.s3a.retry.interval = "500ms" [core-default.xml]
[090]  fs.s3a.retry.throttle.limit = "20" [core-default.xml]
[091]  fs.s3a.retry.throttle.interval = "100ms" [core-default.xml]
[092]  fs.s3a.ssl.channel.mode = "default_jsse" [core-default.xml]
[093]  fs.s3a.s3.client.factory.impl = (unset)
[094]  fs.s3a.threads.max = "80" [core-site.xml]
[095]  fs.s3a.threads.keepalivetime = "60s" [core-default.xml]
[096]  fs.s3a.user.agent.prefix = (unset)
[097]  fs.s3a.vectored.read.min.seek.size = (unset)
[098]  fs.s3a.vectored.read.max.merged.size = (unset)
[099]  fs.s3a.vectored.active.ranged.reads = (unset)
[100]  fs.s3a.assumed.role.arn = "arn:aws:iam::152813717728:role/alice-assumed-role" [core-site.xml]
[101]  fs.s3a.assumed.role.sts.endpoint = "sts.eu-west-2.amazonaws.com" [core-site.xml]; ("${sts.london.endpoint}")
[102]  fs.s3a.assumed.role.sts.endpoint.region = "eu-west-2" [core-site.xml]; ("${sts.london.region}")
[103]  fs.s3a.assumed.role.session.name = (unset)
[104]  fs.s3a.assumed.role.session.duration = "12h" [core-site.xml]
[105]  fs.s3a.assumed.role.credentials.provider = "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider" [core-default.xml]
[106]  fs.s3a.assumed.role.external.id = "arbitrary.value" [core-site.xml]
[107]  fs.s3a.assumed.role.policy = (unset)
[108]  fs.s3a.committer.name = "magic" [fs.s3a.bucket.alice-london.committer.name via [core-site.xml]]
[109]  fs.s3a.committer.magic.enabled = "true" [core-default.xml]
[110]  fs.s3a.committer.staging.abort.pending.uploads = (unset)
[111]  fs.s3a.committer.staging.conflict-mode = "append" [core-default.xml]
[112]  fs.s3a.committer.staging.tmp.path = "tmp/staging" [core-default.xml]
[113]  fs.s3a.committer.threads = "128" [core-site.xml]
[114]  fs.s3a.committer.staging.unique-filenames = "true" [core-default.xml]
[115]  mapreduce.outputcommitter.factory.scheme.s3a = "org.apache.hadoop.fs.s3a.commit.S3ACommitterFactory" [mapred-default.xml]
[116]  mapreduce.fileoutputcommitter.marksuccessfuljobs = (unset)
[117]  fs.s3a.delegation.token.binding = "org.apache.hadoop.fs.s3a.auth.delegation.SessionTokenBinding" [core-site.xml]
[118]  fs.s3a.signature.cache.max.size = (unset)
[119]  fs.s3a.audit.enabled = "true" [core-site.xml]
[120]  fs.s3a.audit.referrer.enabled = (unset)
[121]  fs.s3a.audit.referrer.filter = (unset)
[122]  fs.s3a.audit.reject.out.of.span.operations = "true" [core-site.xml]
[123]  fs.s3a.audit.request.handlers = (unset)
[124]  fs.s3a.audit.execution.interceptors = (unset)
[125]  fs.s3a.audit.service.classname = (unset)
[126]  fs.s3a.accesspoint.arn = (unset)
[127]  fs.s3a.accesspoint.required = "false" [core-default.xml]

13. Required Classes
====================

All these classes must be on the classpath

class: org.apache.hadoop.fs.s3a.S3AFileSystem
resource: org/apache/hadoop/fs/s3a/S3AFileSystem.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/hadoop-aws-3.4.3.jar!/org/apache/hadoop/fs/s3a/S3AFileSystem.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/hadoop-aws-3.4.3.jar
class: java.lang.System
resource: java/lang/System.class
       jrt:/java.base/java/lang/System.class
class: software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
resource: software/amazon/awssdk/auth/credentials/AwsCredentialsProvider.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar!/software/amazon/awssdk/auth/credentials/AwsCredentialsProvider.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar
class: software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
resource: software/amazon/awssdk/auth/credentials/EnvironmentVariableCredentialsProvider.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar!/software/amazon/awssdk/auth/credentials/EnvironmentVariableCredentialsProvider.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar
class: software.amazon.awssdk.core.exception.SdkException
resource: software/amazon/awssdk/core/exception/SdkException.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar!/software/amazon/awssdk/core/exception/SdkException.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar
class: software.amazon.awssdk.services.s3.model.S3Object
resource: software/amazon/awssdk/services/s3/model/S3Object.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar!/software/amazon/awssdk/services/s3/model/S3Object.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar

14. Optional Classes
====================

These classes are needed in some versions of Hadoop.
And/or for optional features to work.

class: org.wildfly.openssl.OpenSSLProvider
resource: org/wildfly/openssl/OpenSSLProvider.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/wildfly-openssl-2.2.5.Final.jar!/org/wildfly/openssl/OpenSSLProvider.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/wildfly-openssl-2.2.5.Final.jar
class: org.wildfly.openssl.OpenSSLContextSPI$OpenSSLTLSContextSpi
resource: org/wildfly/openssl/OpenSSLContextSPI$OpenSSLTLSContextSpi.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/wildfly-openssl-2.2.5.Final.jar!/org/wildfly/openssl/OpenSSLContextSPI$OpenSSLTLSContextSpi.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/wildfly-openssl-2.2.5.Final.jar
class: com.sun.security.cert.internal.x509.X509V1CertImpl
resource: com/sun/security/cert/internal/x509/X509V1CertImpl.class
       Not found on classpath: com.sun.security.cert.internal.x509.X509V1CertImpl
class: org.apache.hadoop.fs.s3a.S3AStore
resource: org/apache/hadoop/fs/s3a/S3AStore.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/hadoop-aws-3.4.3.jar!/org/apache/hadoop/fs/s3a/S3AStore.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/hadoop-aws-3.4.3.jar
class: org.apache.hadoop.io.wrappedio.WrappedIO
resource: org/apache/hadoop/io/wrappedio/WrappedIO.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/hadoop-common-3.4.3.jar!/org/apache/hadoop/io/wrappedio/WrappedIO.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/hadoop-common-3.4.3.jar
class: org.apache.hadoop.io.wrappedio.WrappedStatistics
resource: org/apache/hadoop/io/wrappedio/WrappedStatistics.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/hadoop-common-3.4.3.jar!/org/apache/hadoop/io/wrappedio/WrappedStatistics.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/hadoop-common-3.4.3.jar
class: org.apache.hadoop.util.functional.LazyAutoCloseableReference
resource: org/apache/hadoop/util/functional/LazyAutoCloseableReference.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/hadoop-common-3.4.3.jar!/org/apache/hadoop/util/functional/LazyAutoCloseableReference.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/hadoop-common-3.4.3.jar
class: org.apache.hadoop.fs.s3a.impl.UploadContentProviders
resource: org/apache/hadoop/fs/s3a/impl/UploadContentProviders.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/hadoop-aws-3.4.3.jar!/org/apache/hadoop/fs/s3a/impl/UploadContentProviders.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/hadoop-aws-3.4.3.jar
class: org.apache.knox.gateway.cloud.idbroker.s3a.IDBDelegationTokenBinding
resource: org/apache/knox/gateway/cloud/idbroker/s3a/IDBDelegationTokenBinding.class
       Not found on classpath: org.apache.knox.gateway.cloud.idbroker.s3a.IDBDelegationTokenBinding
class: org.apache.ranger.raz.hook.s3.RazS3ADelegationTokenIdentifier
resource: org/apache/ranger/raz/hook/s3/RazS3ADelegationTokenIdentifier.class
       Not found on classpath: org.apache.ranger.raz.hook.s3.RazS3ADelegationTokenIdentifier
class: org.apache.ranger.raz.hook.s3.RazAnonymousAWSCredentialsProvider
resource: org/apache/ranger/raz/hook/s3/RazAnonymousAWSCredentialsProvider.class
       Not found on classpath: org.apache.ranger.raz.hook.s3.RazAnonymousAWSCredentialsProvider
class: software.amazon.awssdk.crt.s3.S3MetaRequest
resource: software/amazon/awssdk/crt/s3/S3MetaRequest.class
       Not found on classpath: software.amazon.awssdk.crt.s3.S3MetaRequest
class: software.amazon.eventstream.MessageDecoder
resource: software/amazon/eventstream/MessageDecoder.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar!/software/amazon/eventstream/MessageDecoder.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar
class: software.amazon.awssdk.transfer.s3.progress.TransferListener
resource: software/amazon/awssdk/transfer/s3/progress/TransferListener.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar!/software/amazon/awssdk/transfer/s3/progress/TransferListener.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar
class: software.amazon.awssdk.core.checksums.RequestChecksumCalculation
resource: software/amazon/awssdk/core/checksums/RequestChecksumCalculation.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar!/software/amazon/awssdk/core/checksums/RequestChecksumCalculation.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar
class: org.apache.hadoop.fs.impl.LeakReporter
resource: org/apache/hadoop/fs/impl/LeakReporter.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/hadoop-common-3.4.3.jar!/org/apache/hadoop/fs/impl/LeakReporter.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/hadoop-common-3.4.3.jar
class: org.apache.hadoop.fs.s3a.impl.CSEMaterials
resource: org/apache/hadoop/fs/s3a/impl/CSEMaterials.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/hadoop-aws-3.4.3.jar!/org/apache/hadoop/fs/s3a/impl/CSEMaterials.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/hadoop-aws-3.4.3.jar
class: org.apache.hadoop.fs.s3a.impl.streams.ClassicObjectInputStreamFactory
resource: org/apache/hadoop/fs/s3a/impl/streams/ClassicObjectInputStreamFactory.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/hadoop-aws-3.4.3.jar!/org/apache/hadoop/fs/s3a/impl/streams/ClassicObjectInputStreamFactory.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/hadoop-aws-3.4.3.jar
class: software.amazon.s3.analyticsaccelerator.S3SeekableInputStreamFactory
resource: software/amazon/s3/analyticsaccelerator/S3SeekableInputStreamFactory.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/analyticsaccelerator-s3-1.3.1.jar!/software/amazon/s3/analyticsaccelerator/S3SeekableInputStreamFactory.class
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/analyticsaccelerator-s3-1.3.1.jar
class: org/reactivestreams/Processor
resource: org/reactivestreams/Processor.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar!/org/reactivestreams/Processor.class
       Not found on classpath: org/reactivestreams/Processor

At least one optional class was missing -the filesystem client *may* still work

15. Required Resources
======================

resource: mozilla/public-suffix-list.txt
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar!/mozilla/public-suffix-list.txt

16. Optional Resources
======================

resource: log4j.properties
       file:/Users/alice/Projects/Releases/hadoop-3.4.3/etc/hadoop/log4j.properties
resource: software/amazon/awssdk/global/handlers/execution.interceptors
       resource not found on classpath
resource: software/amazon/awssdk/services/s3/execution.interceptors
       jar:file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/bundle-2.35.4.jar!/software/amazon/awssdk/services/s3/execution.interceptors

17. S3A Configuration validation
================================


18. Output Buffering and performance 
=====================================

fs.s3a.fast.upload.buffer = disk
File Output is buffered to disk.
The maximum file size of a single buffered block is 33,554,432 bytes
Note that many blocks may be queued for upload

19. Validating buffer directories
=================================

Buffer configuration option fs.s3a.buffer.dir = "/tmp/hadoop-alice/s3a"
Raw configuration option "${env.LOCAL_DIRS:-${hadoop.tmp.dir}}/s3a"
Number of buffer directories: 1
Buffer path "/tmp/hadoop-alice/s3a":
	* Supports creating temporary files
	* Exists and is writable
	* Free space on device 18,081,521,664
	* Usable space on device 18,081,521,664
	* Contains 0 file(s) with total size 0 bytes

No output buffer issues identified

Attempting to create a temporary file through the allocator

Temporary file successfully created in /tmp/hadoop-alice/s3a

20. Encryption
==============


21. Endpoint validation
=======================

fs.s3a.endpoint = ""
fs.s3a.endpoint.region = "eu-west-2"
fs.s3a.cross.region.access.enabled = "true"
fs.s3a.path.style.access = "false"
fs.s3a.signing-algorithm = ""
fs.s3a.connection.ssl.enabled = "true"
fs.s3a.ssl.channel.mode = "default_jsse"


22. SSL implementation from fs.s3a.ssl.channel.mode
===================================================

This option controls whether the JVM or OpenSSL is used for the TLS channel
OpenSSL is faster, but requires the wildfly library and OpenSSL installed
See: https://github.com/wildfly-security/wildfly-openssl/blob/main/README.md#installing-the-native-library
It is also somewhat brittle;
If HTTPS problems are encountered, try switching to default_jsse

Some third party stores and/or proxies require the GCM ciphers.
These are disabled for performance reasons.
If TLS negotiation errors surface, try using default_jsse_with_gcm

SSL Channel Mode set from fs.s3a.ssl.channel.mode is default_jsse:
This uses the JVM only, with GCM ciphers disabled on Java 8; on Java 11+ GCM is re-enabled

23. Endpoint
============

AWS Endpoint is not declared, but the region is defined.
  -the S3 client will use the region-specific endpoint

This client is configured to connect to AWS S3

Important: if you are working with a third party store,
Expect failure until fs.s3a.endpoint is set to the private endpoint

24. Bucket Name validation
==========================

bucket name = "alice-london"

25. Delegation Tokens
=====================

Delegation token binding org.apache.hadoop.fs.s3a.auth.delegation.SessionTokenBinding is active
This will take over authentication from the settings in fs.s3a.aws.credentials.provider

26. Analyzing login credentials
===============================

access key "AK**************IO44" [20]
Secret key length is 40 characters
Connector has access key, secret key and no session token

27. Extra options with prefix "fs.s3a.ext." :
=============================================

No configuration options with prefix "fs.s3a.ext."

28. Extra options with prefix "fs.s3a.analytics.accelerator." :
===============================================================

No configuration options with prefix "fs.s3a.analytics.accelerator."

29. Performance Hints
=====================


29.1 CPUs/cores
---------------

This diagnostics process was launched with 10 cores
Processes interacting with cloud stores generally benefit from having as many cores as possible
That is: for applications such as distcp, it is better to have fewer processes with more cores

29.2 Size options
-----------------
Option fs.s3a.threads.max (source [core-site.xml]) has value 80. Recommend a value of at least 512

Option fs.s3a.connection.maximum (source [core-site.xml]) has value 512. Recommend a value of at least 1024

Option fs.s3a.committer.threads (source [core-site.xml]) has value 128. Recommend a value of at least 256

Option fs.s3a.executor.capacity (source [core-default.xml]) has value 16. Recommend a value of at least 64

This controls the amount of parallel operations during directory delete and renames.
Larger values may make these slightly faster, especially rename.

29.3 Time options
-----------------

Releases before Hadoop 3.4.0 may not support ms/m/h/... units
If use of a time unit is rejected: supply a value in milliseconds

fs.s3a.connection.timeout: Time limit sending/receiving TCP packet
Option fs.s3a.connection.timeout is unset. Recommend a value of at least 200000ms


fs.s3a.connection.establish.timeout: Time limit establishing a TLS connection to the store
Option fs.s3a.connection.establish.timeout is unset. Recommend a value of at least 60000ms


fs.s3a.connection.request.timeout: Request timeout:
Maximum time for an HTTP request to return a 200 response.
A low value can cause slow uploads to fail.

fs.s3a.connection.ttl: Maximum HTTP connection duration in the connection pool.
Reduces the risk of broken connections being reused.
Only available in later versions.
Option fs.s3a.connection.ttl is unset. Recommend a value of at least 300000ms


29.4 On 2024+ releases with HADOOP-18915 (hadoop 3.4.0+/CDP 7.2.18.0+)
----------------------------------------------------------------------

fs.s3a.connection.acquisition.timeout: Maximum wait to acquire a connection from the connection pool.

fs.s3a.connection.idle.time: Maximum idle time for connections in the pool

29.5 On 2024+ releases with HADOOP-19295 (hadoop 3.4.1+):
---------------------------------------------------------

fs.s3a.connection.part.upload.timeout: 
Option fs.s3a.connection.part.upload.timeout is unset. Recommend a value of at least 900000ms


29.6 Input stream type and read policy
--------------------------------------

29.7 Read policy set by fs.s3a.experimental.input.fadvise: normal
-----------------------------------------------------------------
Adaptive: Reads start 'sequential' but switch to 'random' if backward/random IO is detected
This is adaptive and suitable for most workloads

fs.s3a.readahead.range = 32768

29.8 Miscellaneous options
--------------------------


29.9 Bulk Delete behavior
-------------------------
fs.s3a.multiobjectdelete.enable = true
fs.s3a.bulk.delete.page.size = 250

Multi object delete is enabled; page size is 250

30. Locating implementation class for Filesystem scheme s3a://
==============================================================

FileSystem for s3a:// is: org.apache.hadoop.fs.s3a.S3AFileSystem
Loaded from: file:/Users/alice/Projects/Releases/hadoop-3.4.3/share/hadoop/common/lib/hadoop-aws-3.4.3.jar via jdk.internal.loader.ClassLoaders$AppClassLoader@5ffd2b27

31. TLS System Properties
=========================

[001]  java.version = "17.0.17"
[002]  java.library.path = "/Users/alice/Projects/Releases/hadoop-3.4.3/lib/native"
[003]  com.sun.net.ssl.checkRevocation = (unset)
[004]  https.protocols = (unset)
[005]  javax.net.debug = (unset)
[006]  javax.net.ssl.keyStore = (unset)
[007]  javax.net.ssl.keyStorePassword = (unset)
[008]  javax.net.ssl.trustStore = (unset)
[009]  javax.net.ssl.trustStorePassword = (unset)
[010]  jdk.certpath.disabledAlgorithms = (unset)
[011]  jdk.tls.client.cipherSuites = (unset)
[012]  jdk.tls.client.protocols = (unset)
[013]  jdk.tls.disabledAlgorithms = (unset)
[014]  jdk.tls.legacyAlgorithms = (unset)
[015]  jsse.enableSNIExtension = (unset)


32. HTTPS supported protocols
=============================

    TLSv1.3
    TLSv1.2
    TLSv1.1
    TLSv1
    SSLv3
    SSLv2Hello

33. Endpoints
=============

Attempting to list and connect to public service endpoints,
without any authentication credentials.

- This is just testing the reachability of the URLs.
- If the request fails with any network error it is likely
  to be configuration problem with address, proxy, etc.
- If it is some authentication error, then don't worry:
    the results of the filesystem operations are what really matters

33.1 Endpoint: https://alice-london.s3.amazonaws.com/
------------------------------------------------------
S3 store for bucket
virtual hostname prepended to endpoint
Canonical hostname s3-w.eu-west-2.amazonaws.com
  IP address 52.95.143.99
Proxy: DIRECT
[18:17:00.695] Starting: GET https://alice-london.s3.amazonaws.com/
Response: 403 : Forbidden
HTTP response 403 from https://alice-london.s3.amazonaws.com/: Forbidden
Using proxy: false 
Transfer-Encoding: chunked
null: HTTP/1.1 403 Forbidden
Server: AmazonS3
x-amz-request-id: 63ZAN8K6AE3AG9PP
x-amz-id-2: ZgNv1LhYOa4XfjzMoX+STkr5FiS286YuBo1HftBiGJL6MsKYxkOsxILxmpqm/x3kE5b95pPTu6Q=
Date: Wed, 27 May 2026 18:17:01 GMT
x-amz-bucket-region: eu-west-2
Content-Type: application/xml

<?xml version="1.0" encoding="UTF-8"?>
<Error><Code>AccessDenied</Code><Message>Access Denied</Message><RequestId>63ZAN8K6AE3AG9PP</RequestId><HostId>ZgNv1LhYOa4XfjzMoX+STkr5FiS286YuBo1HftBiGJL6MsKYxkOsxILxmpqm/x3kE5b95pPTu6Q=</HostId></Error>

Duration of GET https://alice-london.s3.amazonaws.com/: 00:00:00.458

33.2 Endpoint: https://sts.eu-west-2.amazonaws.com/
---------------------------------------------------
Endpoint of STS Service
fs.s3a.assumed.role.sts.endpoint
Canonical hostname 52.94.52.40
  IP address 52.94.52.40
Proxy: DIRECT
[18:17:01.162] Starting: GET https://sts.eu-west-2.amazonaws.com/
Response: 200 : Found
HTTP response 200 from https://sts.eu-west-2.amazonaws.com/: Found
Using proxy: false 
Transfer-Encoding: chunked
null: HTTP/1.1 200
X-Cache: Miss from cloudfront
Server: Server
vary: accept-encoding
X-Content-Type-Options: nosniff
X-Amz-Cf-Pop: AMS1-P1
Connection: keep-alive
Last-Modified: Wed, 27 May 2026 15:08:54 GMT
Date: Wed, 27 May 2026 18:17:01 GMT
Via: 1.1 4ab1227a56c7dfaf7a8f7750683df1be.cloudfront.net (CloudFront)
X-Frame-Options: SAMEORIGIN
Strict-Transport-Security: max-age=47304000; includeSubDomains
X-Amz-Cf-Id: nekhSIp4hLV5KQVF7oEJseQVvQOMo6se6TtEnYLSOjN0xG5elbN87g==
Set-Cookie: aws-target-data=%7B%22mboxPage%22%3A%221779905821911-235879%22%2C%22environmentId%22%3A%22350%22%2C%22campaignId%22%3A%22662315%22%2C%22mbox%22%3A%22en_new_temp_test_awswt-936%22%7D; Domain=.amazon.com; Expires=Thu, 27 May 2027 18:17:01 GMT; Path=/,aws_lang=en; Domain=.amazon.com; Path=/,aws-priv=eyJ2IjoxLCJldSI6MSwic3QiOjB9; Version=1; Comment="Anonymous cookie for privacy regulations"; Domain=.aws.amazon.com; Max-Age=31536000; Expires=Thu, 27 May 2027 18:17:01 GMT; Path=/; Secure
x-amz-id-1: FBE6694A7CA04CF39CE0
X-XSS-Protection: 1; mode=block
Content-Type: text/html;charset=utf-8

<!doctype html>
<html lang="en-US" data-static-assets="https://a0.awsstatic.com" class="aws-lng-en_US">
 <head>
  <meta http-equiv="Content-Security-Policy" content="default-src 'self' data: https://a0.awsstatic.com https://prod.us-east-1.ui.gcr-chat.marketing.aws.dev; base-uri 'none'; connect-src 'self' https://*.analytics.console.aws.a2z.com https://*.corrivium.live https://*.harmony.a2z.com https://*.marketing.aws.dev https://*.panorama.console.api.aws https://*.prod.chc-features.uxplatform.aws.dev https://*.prod.cxm.marketing.aws.dev https://*.us-east-1.prod.mrc-sunrise.marketing.aws.dev https://0vctm1i0if.execute-api.eusc-de-east-1.amazonaws.eu/gamma/spot.json https://112-tzm-766.mktoresp.com https://112-tzm-766.mktoutil.com https://8810.clrt.ai https://a0.awsstatic.com https://a0.p.awsstatic.com https://a1.awsstatic.com https://amazonwebservices.d2.sc.omtrdc.net https://amazonwebservicesinc.tt.omtrdc.net https://api-v2.builderprofile.aws.dev https://api.regional-table.region-services.aws.a2z.com https:/

Duration of GET https://sts.eu-west-2.amazonaws.com/: 00:00:00.984

34. Test filesystem s3a://alice-london/temp/subdir
===================================================

Trying some operations against the filesystem
Starting with some read operations, then trying to write

34.1 Filesystem client Instantiation
------------------------------------
2026-05-27 19:17:02,163 [main] INFO  diag.StoreDiag (StoreDurationInfo.java:<init>(84)) - Starting: Creating filesystem for s3a://alice-london/temp/subdir
2026-05-27 19:17:03,309 [main] INFO  diag.StoreDiag (StoreDurationInfo.java:close(190)) - Duration of Creating filesystem for s3a://alice-london/temp/subdir: 00:00:01.158
S3AFileSystem{uri=s3a://alice-london, workingDir=s3a://alice-london/user/alice, partSize=33554432, enableMultiObjectsDelete=true, maxKeys=5000, performanceFlags={}, OpenFileSupport{changePolicy=ETagChangeDetectionPolicy mode=Server, defaultReadAhead=32768, defaultBufferSize=4194304, defaultAsyncDrainThreshold=1024, defaultInputPolicy=default}, blockSize=33554432, multiPartThreshold=33554432, s3EncryptionAlgorithm='SSE_KMS', blockFactory=org.apache.hadoop.fs.s3a.S3ADataBlocks$DiskBlockFactory@49a6f486, auditManager=Service ActiveAuditManagerS3A in state ActiveAuditManagerS3A: STARTED, auditor=LoggingAuditor{ID='1e5e4c31-6f50-49c9-863a-4edbf7958de9', headerEnabled=true, rejectOutOfSpan=true, isMultipartUploadEnabled=true}}, authoritativePath=[], useListV1=false, magicCommitter=true, boundedExecutor=BlockingThreadPoolExecutorService{SemaphoredDelegatingExecutor{permitCount=192, available=192, waiting=0}, activeCount=0}, unboundedExecutor=java.util.concurrent.ThreadPoolExecutor@14fded9d[Running, pool size = 0, active threads = 0, queued tasks = 0, completed tasks = 0], credentials=AWSCredentialProviderList name=; refcount= 1; size=2: [TemporaryAWSCredentialsProvider, SimpleAWSCredentialsProvider{accessKey.empty=false, secretKey.empty=false}], delegation tokens=S3ADelegationTokens{canonicalServiceURI=s3a://alice-london; owner=alice; isBoundToDT=false; token creation count=0; tokenManager=Service SessionTokens/001 in state SessionTokens/001: STARTED token kind = S3ADelegationToken/Session; token=(none)}, DirectoryMarkerRetention{policy='keep'}, instrumentation {S3AInstrumentation{}}, ClientSideEncryption=false}
Implementation class class org.apache.hadoop.fs.s3a.S3AFileSystem

35. Path Capabilities
=====================

fs.capability.outputstream.abortable	true
fs.capability.directory.listing.inconsistent	false
fs.capability.etags.available	true
fs.capability.paths.checksums	true
fs.capability.multipart.uploader	true
fs.option.create.conditional.overwrite	true
fs.option.create.conditional.overwrite.etag	true
fs.option.create.in.close	true
stream_leaks	true
fs.s3a.capability.aws.v2	true
fs.s3a.capability.directory.marker.aware	true
fs.s3a.capability.directory.marker.policy.keep	true
fs.s3a.capability.directory.marker.policy.delete	false
fs.s3a.capability.directory.marker.policy.authoritative	false
fs.s3a.capability.directory.marker.action.keep	true
fs.s3a.capability.directory.marker.action.delete	false
fs.s3a.capability.multipart.uploads.enabled	true
fs.s3a.capability.magic.committer	true
fs.s3a.capability.s3express.storage	false
fs.s3a.create.performance	true
fs.s3a.create.performance.enabled	false
fs.s3a.create.conditional.enabled	true
fs.s3a.create.header	true
fs.s3a.directory.operations.purge.uploads	false
fs.s3a.endpoint.fips	false
fs.s3a.input.stream.type.classic	false
fs.s3a.input.stream.type.prefetching	false
fs.s3a.input.stream.type.analytics	true
fs.s3a.optimized.copy.from.local.enabled	true


35.1 Reading root path
----------------------
[18:17:03.321] Starting: Examine root path
root entry S3AFileStatus{path=s3a://alice-london/; isDirectory=true; modification_time=0; access_time=0; owner=alice; group=alice; permission=rwxrwxrwx; isSymlink=false; hasAcl=false; isEncrypted=true; isErasureCoded=false} isEmptyDirectory=UNKNOWN eTag=null versionId=null
list /
ls / contains 9 entries; first entry s3a://alice-london/file1	[0]
Duration of Examine root path: 00:00:00.835

35.2 Listing s3a://alice-london/temp/subdir
--------------------------------------------
[18:17:04.157] Starting: First 25 entries of listStatus(s3a://alice-london/temp/subdir)
s3a://alice-london/temp/subdir/dir-e8c530f3-26be-4033-8ddc-f6094eb5e4a3/
s3a://alice-london/temp/subdir : scanned 1 entries
Duration of First 25 entries of listStatus(s3a://alice-london/temp/subdir): 00:00:00.164
Listing the directory s3a://alice-london/temp/subdir has succeeded
The store is reachable and the client has list permissions
no file found to attempt to read

35.3 listfiles(s3a://alice-london/temp/subdir, true)
-----------------------------------------------------
[18:17:04.321] Starting: First 25 entries of listFiles(s3a://alice-london/temp/subdir)
Files listing provided by: FunctionRemoteIterator{FileStatusListingIterator[Object listing iterator against s3a://alice-london/temp/subdir; listing count 1; isTruncated=false; counters=((object_list_request.failures=0) (object_continue_list_request.failures=0) (object_list_request=1) (object_continue_list_request=0));
gauges=();
minimums=((object_list_request.min=137) (object_continue_list_request.failures.min=-1) (object_list_request.failures.min=-1) (object_continue_list_request.min=-1));
maximums=((object_continue_list_request.max=-1) (object_continue_list_request.failures.max=-1) (object_list_request.failures.max=-1) (object_list_request.max=137));
means=((object_list_request.mean=(samples=1, sum=137, mean=137.0000)) (object_continue_list_request.failures.mean=(samples=0, sum=0, mean=0.0000)) (object_continue_list_request.mean=(samples=0, sum=0, mean=0.0000)) (object_list_request.failures.mean=(samples=0, sum=0, mean=0.0000)));
]}
Duration of First 25 entries of listFiles(s3a://alice-london/temp/subdir): 00:00:00.397

35.4 Security and Delegation Tokens
-----------------------------------
Security is disabled
[18:17:04.718] Starting: collecting delegation tokens
Token Renewer: yarn@EXAMPLE
2026-05-27 19:17:04,724 [main] INFO  delegation.S3ADelegationTokens (DurationInfo.java:<init>(77)) - Starting: Creating New Delegation Token
2026-05-27 19:17:04,781 [main] INFO  auth.STSClientFactory (STSClientFactory.java:lambda$requestSessionCredentials$0(227)) - Requesting Amazon STS Session credentials
2026-05-27 19:17:05,045 [main] INFO  delegation.S3ADelegationTokens (S3ADelegationTokens.java:noteTokenCreated(443)) - Created S3A Delegation Token: Kind: S3ADelegationToken/Session, Service: s3a://alice-london, Ident: (S3ATokenIdentifier{S3ADelegationToken/Session; uri=s3a://alice-london; timestamp=1779905824994; renewer=yarn; encryption=SSE-KMS; 481c51bc-9332-436c-8bce-5b5c03a64e8b; Created on VXM63P4JG2/192.168.50.139 at time 2026-05-27T18:17:04.728951Z.}; session credentials, expiry 2026-05-28T06:17:04Z; (valid))
2026-05-27 19:17:05,046 [main] INFO  delegation.S3ADelegationTokens (DurationInfo.java:close(98)) - Creating New Delegation Token: duration 0:00.322s
Duration of collecting delegation tokens: 00:00:00.329
Number of tokens issued by filesystem: 1
Token Kind: S3ADelegationToken/Session, Service: s3a://alice-london, Ident: (S3ATokenIdentifier{S3ADelegationToken/Session; uri=s3a://alice-london; timestamp=1779905824994; renewer=yarn; encryption=SSE-KMS; 481c51bc-9332-436c-8bce-5b5c03a64e8b; Created on VXM63P4JG2/192.168.50.139 at time 2026-05-27T18:17:04.728951Z.}; session credentials, expiry 2026-05-28T06:17:04Z; (valid))

35.5 Directory Creation: initial probe for s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc
-------------------------------------------------------------------------------------------------------------------
[18:17:05.049] Starting: probe for a directory which does not yet exist s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc
Duration of probe for a directory which does not yet exist s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc: 00:00:00.173

36. Filesystem Write Operations
===============================

[18:17:05.225] Starting: creating a directory s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc
Duration of creating a directory s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc: 00:00:00.753
[18:17:05.981] Starting: create directory s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc
Duration of create directory s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc: 00:00:00.152
[18:17:06.133] Starting: probing path s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/file
Duration of probing path s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/file: 00:00:00.143

36.1 Creating file s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/file
------------------------------------------------------------------------------------------------
[18:17:06.277] Starting: Creating file s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/file
Capabilities:
    fs.capability.outputstream.abortable
    iostatistics
    fs.capability.iocontext.supported

36.2 Writing data to s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/file
--------------------------------------------------------------------------------------------------
Stream does not reject hflush() calls
2026-05-27 19:17:06,455 [main] WARN  s3a.S3ABlockOutputStream (LogExactlyOnce.java:warn(39)) - Application invoked the Syncable API against stream writing to temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/file. This is Unsupported
Stream does not reject hsync() calls
Output stream summary: FSDataOutputStream{wrappedStream=S3ABlockOutputStream{WriteOperationHelper {bucket=alice-london}, blockSize=33554432, isMultipartUploadEnabled=true Statistics=counters=((stream_write_exceptions_completing_upload=0) (stream_write_block_uploads=1) (stream_write_exceptions=0) (multipart_upload_part_put=0) (object_multipart_initiated=0) (stream_write_total_data=7) (committer_magic_marker_put.failures=0) (op_hsync=1) (object_multipart_aborted.failures=0) (object_put_request=1) (conditional_create_failed=0) (object_put_request.failures=0) (op_abort.failures=0) (object_multipart_initiated.failures=0) (action_executor_acquired.failures=0) (op_hflush=1) (multipart_upload_completed=0) (stream_write_queue_duration=0) (op_abort=0) (committer_magic_marker_put=0) (object_multipart_aborted=0) (conditional_create=0) (stream_write_total_time=0) (multipart_upload_part_put.failures=0) (multipart_upload_completed.failures=0) (stream_write_bytes=7) (action_executor_acquired=0));
gauges=((stream_write_block_uploads_data_pending=0) (stream_write_block_uploads_active=0) (stream_write_block_uploads_pending=0));
minimums=((op_abort.failures.min=-1) (committer_magic_marker_put.failures.min=-1) (object_multipart_aborted.failures.min=-1) (action_executor_acquired.min=1) (object_multipart_aborted.min=-1) (committer_magic_marker_put.min=-1) (multipart_upload_completed.min=-1) (object_put_request.min=226) (multipart_upload_part_put.failures.min=-1) (multipart_upload_completed.failures.min=-1) (action_executor_acquired.failures.min=-1) (object_multipart_initiated.failures.min=-1) (object_put_request.failures.min=-1) (op_abort.min=-1) (object_multipart_initiated.min=-1) (multipart_upload_part_put.min=-1));
maximums=((action_executor_acquired.failures.max=-1) (multipart_upload_part_put.failures.max=-1) (multipart_upload_completed.failures.max=-1) (op_abort.max=-1) (object_multipart_aborted.failures.max=-1) (multipart_upload_part_put.max=-1) (object_multipart_initiated.max=-1) (object_put_request.failures.max=-1) (object_multipart_initiated.failures.max=-1) (op_abort.failures.max=-1) (action_executor_acquired.max=1) (committer_magic_marker_put.failures.max=-1) (object_multipart_aborted.max=-1) (object_put_request.max=226) (multipart_upload_completed.max=-1) (committer_magic_marker_put.max=-1));
means=((op_abort.mean=(samples=0, sum=0, mean=0.0000)) (action_executor_acquired.mean=(samples=1, sum=1, mean=1.0000)) (object_multipart_aborted.failures.mean=(samples=0, sum=0, mean=0.0000)) (op_abort.failures.mean=(samples=0, sum=0, mean=0.0000)) (multipart_upload_completed.failures.mean=(samples=0, sum=0, mean=0.0000)) (multipart_upload_part_put.mean=(samples=0, sum=0, mean=0.0000)) (object_put_request.mean=(samples=1, sum=226, mean=226.0000)) (committer_magic_marker_put.mean=(samples=0, sum=0, mean=0.0000)) (multipart_upload_completed.mean=(samples=0, sum=0, mean=0.0000)) (object_multipart_initiated.failures.mean=(samples=0, sum=0, mean=0.0000)) (committer_magic_marker_put.failures.mean=(samples=0, sum=0, mean=0.0000)) (object_multipart_initiated.mean=(samples=0, sum=0, mean=0.0000)) (object_multipart_aborted.mean=(samples=0, sum=0, mean=0.0000)) (multipart_upload_part_put.failures.mean=(samples=0, sum=0, mean=0.0000)) (action_executor_acquired.failures.mean=(samples=0, sum=0, mean=0.0000)) (object_put_request.failures.mean=(samples=0, sum=0, mean=0.0000)));
}}
Duration of Creating file s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/file: 00:00:00.449

36.3 Listing s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc
-------------------------------------------------------------------------------------
[18:17:06.727] Starting: ListFiles(s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc)
 s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/file
Duration of ListFiles(s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc): 00:00:00.099

36.4 Reading file s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/file
-----------------------------------------------------------------------------------------------
[18:17:06.826] Starting: Reading file s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/file
Capabilities:
    iostatistics
    stream_leaks
input stream summary: org.apache.hadoop.fs.FSDataInputStream@2f9b21d6: ObjectInputStream{streamType=Analytics, uri='s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/file', contentLength=7, inputPolicy=default, vectoredIOContext=VectoredIOContext{minSeekForVectorReads=0, maxReadSizeForVectorReads=2097152, vectoredActiveRangeReads=4}} org.apache.hadoop.fs.s3a.impl.streams.AnalyticsStream@2e0fdbe9{counters=((stream_read_prefetch_operations=0) (action_http_get_request.failures=0) (stream_file_cache_eviction=0) (stream_read_remote_stream_drain.failures=0) (stream_read_close_operations=1) (stream_read_bytes_backwards_on_seek=0) (stream_read_seek_bytes_discarded=0) (action_file_opened=1) (stream_read_vectored_operations=0) (action_http_head_request=0) (stream_read_fully_operations=0) (stream_read_block_read=0) (stream_read_remote_stream_drain=0) (stream_read_vectored_incoming_ranges=0) (stream_read_operations=3) (stream_read_seek_policy_changed=1) (stream_read_cache_hit=3) (stream_read_prefetched_bytes=7) (stream_aborted=0) (stream_read_total_bytes=7) (stream_read_exceptions=0) (action_executor_acquired=0) (stream_read_version_mismatches=0) (stream_read_block_acquire_read=0) (stream_read_unbuffered=0) (stream_leaks=0) (stream_read_vectored_combined_ranges=0) (stream_read_operations_incomplete=0) (action_http_get_request=1) (stream_read_bytes=7) (stream_read_bytes_discarded_in_abort=0) (stream_read_parquet_footer_parsing_failed=0) (action_executor_acquired.failures=0) (stream_read_remote_stream_aborted.failures=0) (stream_read_opened=1) (stream_read_vectored_read_bytes_discarded=0) (stream_read_prefetch_operations.failures=0) (stream_read_seek_bytes_skipped=0) (stream_evict_blocks_from_cache=0) (stream_read_block_acquire_read.failures=0) (stream_read_remote_stream_aborted=0) (action_file_opened.failures=0) (stream_read_analytics_opened=1) (stream_read_seek_backward_operations=0) (stream_read_closed=0) (stream_file_cache_eviction.failures=0) (stream_read_seek_operations=0) (stream_read_seek_forward_operations=0) (stream_read_bytes_discarded_in_close=0) (stream_read_block_read.failures=0));
gauges=((stream_read_gauge_input_policy=0) (stream_read_active_prefetch_operations=0) (stream_read_active_memory_in_use=0) (stream_read_blocks_in_cache=0));
minimums=((action_file_opened.min=115) (stream_read_prefetch_operations.min=-1) (stream_read_remote_stream_drain.failures.min=-1) (stream_file_cache_eviction.failures.min=-1) (stream_read_block_acquire_read.failures.min=-1) (action_executor_acquired.min=-1) (stream_read_remote_stream_aborted.min=-1) (action_http_get_request.min=-1) (stream_read_block_read.failures.min=-1) (action_file_opened.failures.min=-1) (stream_read_remote_stream_aborted.failures.min=-1) (action_executor_acquired.failures.min=-1) (stream_read_block_acquire_read.min=-1) (action_http_get_request.failures.min=-1) (stream_read_remote_stream_drain.min=-1) (stream_read_prefetch_operations.failures.min=-1) (stream_file_cache_eviction.min=-1) (stream_read_block_read.min=-1));
maximums=((action_executor_acquired.failures.max=-1) (stream_read_block_read.max=-1) (action_file_opened.failures.max=-1) (stream_read_remote_stream_aborted.failures.max=-1) (action_http_get_request.failures.max=-1) (stream_read_prefetch_operations.failures.max=-1) (stream_file_cache_eviction.max=-1) (stream_read_remote_stream_drain.max=-1) (stream_read_prefetch_operations.max=-1) (stream_read_remote_stream_drain.failures.max=-1) (stream_file_cache_eviction.failures.max=-1) (stream_read_block_acquire_read.failures.max=-1) (action_executor_acquired.max=-1) (stream_read_block_acquire_read.max=-1) (stream_read_remote_stream_aborted.max=-1) (action_http_get_request.max=-1) (stream_read_block_read.failures.max=-1) (action_file_opened.max=115));
means=((action_http_get_request.failures.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_block_read.failures.mean=(samples=0, sum=0, mean=0.0000)) (action_executor_acquired.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_block_read.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_block_acquire_read.failures.mean=(samples=0, sum=0, mean=0.0000)) (stream_file_cache_eviction.mean=(samples=0, sum=0, mean=0.0000)) (stream_file_cache_eviction.failures.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_block_acquire_read.mean=(samples=0, sum=0, mean=0.0000)) (action_file_opened.mean=(samples=1, sum=115, mean=115.0000)) (stream_read_remote_stream_aborted.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_remote_stream_drain.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_prefetch_operations.failures.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_prefetch_operations.mean=(samples=0, sum=0, mean=0.0000)) (action_http_get_request.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_remote_stream_drain.failures.mean=(samples=0, sum=0, mean=0.0000)) (action_file_opened.failures.mean=(samples=0, sum=0, mean=0.0000)) (action_executor_acquired.failures.mean=(samples=0, sum=0, mean=0.0000)) (stream_read_remote_stream_aborted.failures.mean=(samples=0, sum=0, mean=0.0000)));
}
Duration of Reading file s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/file: 00:00:00.332
File modtime after creation = 723 millis,
	after close invoked = 544 millis
	after close completed = 282 millis
Timestamp of created file is 723 milliseconds after the local clock
The file timestamp is closer to the write completion time.
If the store is an object store, the object is
likely to have been created at the end of the write

36.5 Reviewing bucket versioning
--------------------------------
The bucket is using versioning.
Directory marker retention is enabled, so performance will suffer less


36.6 Checking overwrite detection behaviors
-------------------------------------------
Store performs normal overwrite checks during creation

36.7 Renaming
-------------
[18:17:07.577] Starting: Renaming file s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/file under s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/subdir
Duration of Renaming file s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/file under s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/subdir: 00:00:02.461
[18:17:10.040] Starting: probing path s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/subdir/subfile
Duration of probing path s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/subdir/subfile: 00:00:00.364

36.8 Deleting dir s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/subdir2
--------------------------------------------------------------------------------------------------
[18:17:10.405] Starting: delete directory s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/subdir2
Duration of delete directory s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/subdir2: 00:00:00.465
[18:17:10.871] Starting: probing path s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/subdir2
Duration of probing path s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc/subdir2: 00:00:00.162
All read and write operations succeeded: good to go

36.9 Deleting directory s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc
------------------------------------------------------------------------------------------------
[18:17:11.034] Starting: delete directory s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc
Duration of delete directory s3a://alice-london/temp/subdir/dir-46b80f03-3372-4b17-a3b3-c64b5cb2b8bc: 00:00:00.535
2026-05-27 19:17:11,600 [main] INFO  statistics.IOStatisticsLogging (IOStatisticsLogging.java:logIOStatisticsAtLevel(269)) - IOStatistics: counters=((action_file_opened=1)
(action_http_get_request=1)
(action_http_head_request=20)
(analytics_stream_factory_closed=1)
(audit_request_execution=55)
(audit_span_creation=22)
(delegation_tokens_issued=1)
(directories_created=2)
(directories_deleted=1)
(files_copied=2)
(files_copied_bytes=14)
(files_created=1)
(files_deleted=4)
(filesystem_close=1)
(filesystem_initialization=1)
(object_bulk_delete_request=1)
(object_copy_requests=2)
(object_delete_objects=5)
(object_delete_request=3)
(object_list_request=25)
(object_metadata_request=20)
(object_put_bytes=7)
(object_put_request=3)
(object_put_request_completed=3)
(op_create=1)
(op_createfile=2)
(op_createfile.failures=1)
(op_delete=2)
(op_get_delegation_token=1)
(op_get_file_status=7)
(op_get_file_status.failures=4)
(op_hflush=1)
(op_hsync=1)
(op_list_files=2)
(op_list_status=2)
(op_mkdirs=2)
(op_open=1)
(op_rename=2)
(store_client_creation=1)
(store_io_request=55)
(stream_read_analytics_opened=1)
(stream_read_bytes=7)
(stream_read_cache_hit=3)
(stream_read_close_operations=1)
(stream_read_opened=1)
(stream_read_operations=3)
(stream_read_prefetched_bytes=7)
(stream_read_seek_policy_changed=1)
(stream_read_total_bytes=7)
(stream_write_block_uploads=2)
(stream_write_bytes=7)
(stream_write_queue_duration=1)
(stream_write_total_data=14)
(stream_write_total_time=229));

gauges=();

minimums=((action_executor_acquired.min=1)
(action_file_opened.min=115)
(action_http_head_request.min=59)
(delegation_tokens_issued.min=274)
(filesystem_close.min=22)
(filesystem_initialization.min=809)
(object_bulk_delete_request.min=120)
(object_delete_request.min=71)
(object_list_request.min=64)
(object_put_request.min=99)
(op_create.min=167)
(op_createfile.failures.min=71)
(op_delete.min=76)
(op_get_delegation_token.min=325)
(op_get_file_status.failures.min=142)
(op_get_file_status.min=1)
(op_list_files.min=94)
(op_list_status.min=157)
(op_mkdirs.min=524)
(op_rename.min=812)
(store_client_creation.min=691)
(store_io_rate_limited_duration.min=0));

maximums=((action_executor_acquired.max=1)
(action_file_opened.max=115)
(action_http_head_request.max=346)
(delegation_tokens_issued.max=274)
(filesystem_close.max=22)
(filesystem_initialization.max=809)
(object_bulk_delete_request.max=120)
(object_delete_request.max=86)
(object_list_request.max=798)
(object_put_request.max=226)
(op_create.max=167)
(op_createfile.failures.max=71)
(op_delete.max=251)
(op_get_delegation_token.max=325)
(op_get_file_status.failures.max=363)
(op_get_file_status.max=335)
(op_list_files.max=387)
(op_list_status.max=828)
(op_mkdirs.max=745)
(op_rename.max=1121)
(store_client_creation.max=691)
(store_io_rate_limited_duration.max=0));

means=((action_executor_acquired.mean=(samples=1, sum=1, mean=1.0000))
(action_file_opened.mean=(samples=1, sum=115, mean=115.0000))
(action_http_head_request.mean=(samples=20, sum=2465, mean=123.2500))
(delegation_tokens_issued.mean=(samples=1, sum=274, mean=274.0000))
(filesystem_close.mean=(samples=1, sum=22, mean=22.0000))
(filesystem_initialization.mean=(samples=1, sum=809, mean=809.0000))
(object_bulk_delete_request.mean=(samples=1, sum=120, mean=120.0000))
(object_delete_request.mean=(samples=3, sum=232, mean=77.3333))
(object_list_request.mean=(samples=25, sum=3763, mean=150.5200))
(object_put_request.mean=(samples=3, sum=495, mean=165.0000))
(op_create.mean=(samples=1, sum=167, mean=167.0000))
(op_createfile.failures.mean=(samples=1, sum=71, mean=71.0000))
(op_delete.mean=(samples=2, sum=327, mean=163.5000))
(op_get_delegation_token.mean=(samples=1, sum=325, mean=325.0000))
(op_get_file_status.failures.mean=(samples=4, sum=834, mean=208.5000))
(op_get_file_status.mean=(samples=3, sum=487, mean=162.3333))
(op_list_files.mean=(samples=2, sum=481, mean=240.5000))
(op_list_status.mean=(samples=2, sum=985, mean=492.5000))
(op_mkdirs.mean=(samples=2, sum=1269, mean=634.5000))
(op_rename.mean=(samples=2, sum=1933, mean=966.5000))
(store_client_creation.mean=(samples=1, sum=691, mean=691.0000))
(store_io_rate_limited_duration.mean=(samples=4, sum=0, mean=0.0000)));

JVM: memory=203162280

37. Success!
============


```

### Failing run against Azure ABFS: no credentials

```

> hadoop jar cloudstore-1.3.jar storediag abfs://alice-testing@alicewales.dfs.core.windows.net/ 

1. Store Diagnostics for alice (auth:SIMPLE) on VXM63P4JG2/192.168.50.139
==========================================================================

Collected at at 2026-05-27T19:21:37.827501Z


2. Diagnostics for filesystem abfs://alice-testing@alicewales.dfs.core.windows.net/
===================================================================================

Azure Abfs connector
ASF Filesystem Connector to Microsoft Azure ABFS Storage
https://hadoop.apache.org/docs/current/hadoop-azure/index.html

3. Hadoop information
=====================

  Hadoop 3.5.0
  Compiled by cnauroth on 2026-03-24T16:56Z
  Compiled with protoc 3.25.5
  From source with checksum 64582ed0a62a169c39a19c734a8f33b5

4. Determining OS version
=========================

Darwin VXM63P4JG2 25.4.0 Darwin Kernel Version 25.4.0: Thu Mar 19 19:30:44 PDT 2026; root:xnu-12377.101.15~1/RELEASE_ARM64_T6000 x86_64

5. Selected System Properties
=============================

[001]  https.proxyHost = (unset)
[002]  https.proxyPort = (unset)
[003]  https.nonProxyHosts = (unset)
[004]  https.proxyPassword = (unset)
[005]  http.proxyHost = (unset)
[006]  http.keepAlive = (unset)
[007]  http.proxyPort = (unset)
[008]  http.proxyPassword = (unset)
[009]  http.nonProxyHosts = (unset)
[010]  java.net.preferIPv4Stack = "true"
[011]  java.net.preferIPv6Addresses = (unset)
[012]  jsse.enableSNIExtension = (unset)
[013]  networkaddress.cache.ttl = (unset)
[014]  networkaddress.cache.negative.ttl = (unset)
[015]  socksProxyHost = (unset)
[016]  socksProxyPort = (unset)
[017]  sun.net.client.defaultConnectTimeout = (unset)
[018]  sun.net.client.defaultReadTimeout = (unset)
[019]  sun.net.inetaddr.ttl = (unset)
[020]  sun.net.inetaddr.negative.ttl = (unset)
[021]  java.version = "17.0.17"
[022]  java.specification.version = "17"
[023]  java.class.version = "61.0"

6. JVM Security Properties
==========================

[001]  jdk.certpath.disabledAlgorithms = "MD2, MD5, SHA1 jdkCA & usage TLSServer, RSA keySize < 1024, DSA keySize < 1024, EC keySize < 224, SHA1 usage SignedJAR & denyAfter 2019-01-01"
[002]  jdk.tls.disabledAlgorithm = (unset)
[003]  jdk.tls.keyLimits = "AES/GCM/NoPadding KeyUpdate 2^37, ChaCha20-Poly1305 KeyUpdate 2^37"
[004]  networkaddress.cache.ttl = (unset)
[005]  ssl.KeyManagerFactory = (unset)
[006]  ssl.KeyManagerFactory.algorithm = "SunX509"
[007]  ssl.TrustManagerFactory = (unset)

7. Environment Variables
========================

[001]  AZURE_AUTHORITY_HOST = (unset)
[002]  AZURE_CLIENT_ID = (unset)
[003]  AZURE_FEDERATED_TOKEN_FILE = (unset)
[004]  AZURE_TENANT_ID = (unset)
[005]  PATH = "/Users/alice/.cargo/bin:/Users/alice/bin/google-cloud-sdk/bin:/Users/alice/.local/bin:/Users/alice/Library/Python/3.9/bin:/Users/alice/java/maven/bin:/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home:/usr/local/smlnj/bin:~/.local/bin:/Users/alice/bin:/Users/alice/bin/scripts:/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin:/System/Cryptexes/App/usr/bin:/usr/bin:/bin:/usr/sbin:/sbin:/var/run/com.apple.security.cryptexd/codex.system/bootstrap/usr/local/bin:/var/run/com.apple.security.cryptexd/codex.system/bootstrap/usr/bin:/var/run/com.apple.security.cryptexd/codex.system/bootstrap/usr/appleinternal/bin:/opt/pkg/env/active/bin:/opt/pmk/env/global/bin:/opt/X11/bin:/Library/Apple/usr/bin:/usr/local/MacGPG2/bin:/Applications/Privileges.app/Contents/MacOS:/Library/TeX/texbin:/Applications/Wireshark.app/Contents/MacOS:/usr/local/share/dotnet:~/.dotnet/tools:/Users/alice/.cargo/bin:./bin:/Users/alice/bin/port/venv_port/bin:/Users/alice/Projects/gocode/bin"
[006]  HADOOP_CONF_DIR = "/Users/alice/Projects/Releases/hadoop-3.5.0/etc/hadoop"
[007]  HADOOP_CLASSPATH = (unset)
[008]  HADOOP_CLIENT_OPTS = (unset)
[009]  HADOOP_CREDSTORE_PASSWORD = (unset)
[010]  HADOOP_HEAPSIZE = (unset)
[011]  HADOOP_HEAPSIZE_MIN = (unset)
[012]  HADOOP_HOME = "/Users/alice/Projects/Releases/hadoop-3.5.0"
[013]  HADOOP_LOG_DIR = (unset)
[014]  HADOOP_OPTIONAL_TOOLS = "hadoop-azure,hadoop-aws"
[015]  HADOOP_OPTS = "-Djava.net.preferIPv4Stack=true  -Dyarn.log.dir=/Users/alice/Projects/Releases/hadoop-3.5.0/logs -Dyarn.log.file=hadoop.log -Dyarn.home.dir=/Users/alice/Projects/Releases/hadoop-3.5.0 -Dyarn.root.logger=INFO,console -Djava.library.path=/Users/alice/Projects/Releases/hadoop-3.5.0/lib/native -Dhadoop.log.dir=/Users/alice/Projects/Releases/hadoop-3.5.0/logs -Dhadoop.log.file=hadoop.log -Dhadoop.home.dir=/Users/alice/Projects/Releases/hadoop-3.5.0 -Dhadoop.id.str=alice -Dhadoop.root.logger=INFO,console -Dhadoop.policy.file=hadoop-policy.xml -Dhadoop.security.logger=INFO,NullAppender -XX:+IgnoreUnrecognizedVMOptions --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.math=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.text=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.zip=ALL-UNNAMED --add-opens=java.base/sun.security.util=ALL-UNNAMED --add-opens=java.base/sun.security.x509=ALL-UNNAMED --enable-native-access=ALL-UNNAMED"
[016]  HADOOP_SHELL_SCRIPT_DEBUG = (unset)
[017]  HADOOP_TOKEN = (unset)
[018]  HADOOP_TOKEN_FILE_LOCATION = (unset)
[019]  HADOOP_KEYSTORE_PASSWORD = (unset)
[020]  HADOOP_TOOLS_HOME = (unset)
[021]  HADOOP_TOOLS_OPTIONS = (unset)
[022]  HADOOP_YARN_HOME = "/Users/alice/Projects/Releases/hadoop-3.5.0"
[023]  HDP_VERSION = (unset)
[024]  JAVA_HOME = "/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home"
[025]  LD_LIBRARY_PATH = (unset)
[026]  LD_PRELOAD = (unset)
[027]  LOCAL_DIRS = (unset)
[028]  OPENSSL_ROOT_DIR = "/usr/local/opt/openssl/"
[029]  OPENSSL_CONF = (unset)
[030]  OPENSSL_CONF_INCLUDE = (unset)
[031]  OPENSSL_MODULES = (unset)
[032]  OPENSSL_TRACE = (unset)
[033]  PYSPARK_DRIVER_PYTHON = (unset)
[034]  SASL_MECHANISM = (unset)
[035]  SPARK_HOME = (unset)
[036]  SPARK_CONF_DIR = (unset)
[037]  SPARK_SCALA_VERSION = (unset)
[038]  YARN_CONF_DIR = (unset)
[039]  http_proxy = (unset)
[040]  https_proxy = (unset)
[041]  no_proxy = (unset)

8. Hadoop XML Configurations
============================

resource: core-default.xml
       jar:file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/hadoop-common-3.5.0.jar!/core-default.xml
resource: core-site.xml
       file:/Users/alice/Projects/Releases/hadoop-3.5.0/etc/hadoop/core-site.xml
resource: hdfs-default.xml
       jar:file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/hdfs/hadoop-hdfs-3.5.0.jar!/hdfs-default.xml
resource: hdfs-site.xml
       file:/Users/alice/Projects/Releases/hadoop-3.5.0/etc/hadoop/hdfs-site.xml
resource: mapred-default.xml
       jar:file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/mapreduce/hadoop-mapreduce-client-core-3.5.0.jar!/mapred-default.xml
resource: mapred-site.xml
       file:/Users/alice/Projects/Releases/hadoop-3.5.0/etc/hadoop/mapred-site.xml
resource: yarn-default.xml
       jar:file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/yarn/hadoop-yarn-common-3.5.0.jar!/yarn-default.xml
resource: yarn-site.xml
       file:/Users/alice/Projects/Releases/hadoop-3.5.0/etc/hadoop/yarn-site.xml

9. Security
===========

Security Enabled: false
Keytab login: false
Ticket login: false
Current user: alice (auth:SIMPLE)
Token count: 0

10. Hadoop Options
==================

[001]  fs.defaultFS = "file:///" [core-default.xml]
[002]  fs.default.name = "file:///" 
[003]  fs.creation.parallel.count = "64" [core-default.xml]
[004]  fs.permissions.umask-mode = "022" [core-default.xml]
[005]  fs.trash.classname = (unset)
[006]  fs.trash.interval = "0" [core-default.xml]
[007]  fs.trash.checkpoint.interval = "0" [core-default.xml]
[008]  fs.file.impl = (unset)
[009]  hadoop.tmp.dir = "/tmp/hadoop-alice" [core-default.xml]; ("/tmp/hadoop-${user.name}")
[010]  hdp.version = (unset)
[011]  yarn.resourcemanager.address = "0.0.0.0:8032" [yarn-default.xml]; ("${yarn.resourcemanager.hostname}:8032")
[012]  yarn.resourcemanager.principal = (unset)
[013]  yarn.resourcemanager.webapp.address = "0.0.0.0:8088" [yarn-default.xml]; ("${yarn.resourcemanager.hostname}:8088")
[014]  yarn.resourcemanager.webapp.https.address = "0.0.0.0:8090" [yarn-default.xml]; ("${yarn.resourcemanager.hostname}:8090")
[015]  mapreduce.input.fileinputformat.list-status.num-threads = "1" [mapred-default.xml]
[016]  mapreduce.jobtracker.kerberos.principal = (unset)
[017]  mapreduce.job.hdfs-servers.token-renewal.exclude = (unset)
[018]  mapreduce.application.framework.path = (unset)
[019]  fs.iostatistics.logging.level = "info" [core-site.xml]
[020]  fs.iostatistics.thread.level.enabled = "true" [core-default.xml]
[021]  parquet.hadoop.vectored.io.enabled = (unset)

11. Security Options
====================

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

12. Selected Configuration Options
==================================

[001]  abfs.external.authorization.class = (unset)
[002]  fs.abfs.impl = "org.apache.hadoop.fs.azurebfs.AzureBlobFileSystem" [core-default.xml]
[003]  fs.abfss.impl = "org.apache.hadoop.fs.azurebfs.SecureAzureBlobFileSystem" [core-default.xml]
[004]  fs.azure.abfs.endpoint = (unset)
[005]  fs.azure.abfs.latency.track = (unset)
[006]  fs.azure.account.auth.type = (unset)
[007]  fs.azure.account.hns.enabled = "true" [core-site.xml]
[008]  fs.azure.account.keyprovider = (unset)
[009]  fs.azure.account.oauth.provider.type = (unset)
[010]  fs.azure.account.oauth2.client.endpoint = (unset)
[011]  fs.azure.account.oauth2.client.id = (unset)
[012]  fs.azure.account.oauth2.client.secret = (unset)
[013]  fs.azure.account.oauth2.msi.authority = (unset)
[014]  fs.azure.account.oauth2.msi.endpoint = (unset)
[015]  fs.azure.account.oauth2.msi.tenant = (unset)
[016]  fs.azure.account.oauth2.refresh.token = (unset)
[017]  fs.azure.account.oauth2.refresh.token.endpoint = (unset)
[018]  fs.azure.account.oauth2.token.file = (unset)
[019]  fs.azure.account.oauth2.user.name = (unset)
[020]  fs.azure.account.oauth2.user.password = (unset)
[021]  fs.azure.account.operation.idle.timeout = (unset)
[022]  fs.azure.account.throttling.enabled = (unset)
[023]  fs.azure.analysis.period = (unset)
[024]  fs.azure.shellkeyprovider.script = (unset)
[025]  fs.azure.always.use.https = (unset)
[026]  fs.azure.apache.http.client.idle.connection.ttl = (unset)
[027]  fs.azure.apache.http.client.max.cache.connection.size = (unset)
[028]  fs.azure.apache.http.client.max.io.exception.retries = (unset)
[029]  fs.azure.appendblob.directories = (unset)
[030]  fs.azure.atomic.rename.key = (unset)
[031]  fs.azure.block.location.impersonatedhost = (unset)
[032]  fs.azure.block.size = (unset)
[033]  fs.azure.buffered.pread.disable = (unset)
[034]  fs.azure.client-provided-encryption-key = (unset)
[035]  fs.azure.client.correlationid = (unset)
[036]  fs.azure.cluster.name = (unset)
[037]  fs.azure.cluster.type = (unset)
[038]  fs.azure.concurrentRequestCount.in = (unset)
[039]  fs.azure.concurrentRequestCount.out = (unset)
[040]  fs.azure.createRemoteFileSystemDuringInitialization = (unset)
[041]  fs.azure.custom.token.fetch.retry.count = (unset)
[042]  fs.azure.delegation.token.provider.type = (unset)
[043]  fs.azure.data.blocks.buffer = (unset)
[044]  fs.azure.buffer.dir = "/tmp/hadoop-alice/abfs" [core-default.xml]; ("${env.LOCAL_DIRS:-${hadoop.tmp.dir}}/abfs")
[045]  fs.azure.block.upload.active.blocks = (unset)
[046]  fs.azure.disable.outputstream.flush = (unset)
[047]  fs.azure.enable.abfslistiterator = (unset)
[048]  fs.azure.enable.autothrottling = (unset)
[049]  fs.azure.enable.check.acces = (unset)
[050]  fs.azure.enable.checksum.validation = (unset)
[051]  fs.azure.enable.conditional.create.overwrite = (unset)
[052]  fs.azure.enable.delegation.token = (unset)
[053]  fs.azure.enable.flush = (unset)
[054]  fs.azure.enable.mkdir.overwrite = (unset)
[055]  fs.azure.enable.readahead = "true" [core-default.xml]
[056]  fs.azure.enable.readahead.v2 = (unset)
[057]  fs.azure.enable.rename.resilience = (unset)
[058]  fs.azure.encryption.context.provider.type = (unset)
[059]  fs.azure.encryption.encoded.client-provided-key = (unset)
[060]  fs.azure.encryption.encoded.client-provided-key-sha = (unset)
[061]  fs.azure.identity.transformer.class = (unset)
[062]  fs.azure.identity.transformer.domain.name = (unset)
[063]  fs.azure.identity.transformer.enable.short.name = (unset)
[064]  fs.azure.identity.transformer.local.service.group.mapping.file.path = (unset)
[065]  fs.azure.identity.transformer.local.service.principal.mapping.file.path = (unset)
[066]  fs.azure.identity.transformer.service.principal.id = (unset)
[067]  fs.azure.identity.transformer.service.principal.substitution.list = (unset)
[068]  fs.azure.identity.transformer.skip.superuser.replacement = (unset)
[069]  fs.azure.infinite-lease.directories = (unset)
[070]  fs.azure.io.rate.limit = (unset)
[071]  fs.azure.io.read.tolerate.concurrent.append = (unset)
[072]  fs.azure.io.retry.backoff.interval = (unset)
[073]  fs.azure.io.retry.max.backoff.interval = (unset)
[074]  fs.azure.io.retry.max.retries = (unset)
[075]  fs.azure.io.retry.min.backoff.interval = (unset)
[076]  fs.azure.lease.threads = (unset)
[077]  fs.azure.list.max.results = (unset)
[078]  fs.azure.metric.analysis.timeout = (unset)
[079]  fs.azure.networking.library = (unset)
[080]  fs.azure.oauth.token.fetch.retry.max.backoff.interval = (unset)
[081]  fs.azure.oauth.token.fetch.retry.max.retries = (unset)
[082]  fs.azure.oauth.token.fetch.retry.min.backoff.interval = (unset)
[083]  fs.azure.objectmapper.threadlocal.enabled = (unset)
[084]  fs.azure.read.alwaysReadBufferSize = (unset)
[085]  fs.azure.read.optimizefooterread = (unset)
[086]  fs.azure.networking.library = (unset)
[087]  fs.azure.apache.http.client.idle.connection.ttl = (unset)
[088]  fs.azure.apache.http.client.max.cache.connection.size = (unset)
[089]  fs.azure.apache.http.client.max.io.exception.retries = (unset)
[090]  fs.azure.read.readahead.blocksize = (unset)
[091]  fs.azure.read.request.size = (unset)
[092]  fs.azure.read.smallfilescompletely = (unset)
[093]  fs.azure.readahead.range = (unset)
[094]  fs.azure.readaheadqueue.depth = (unset)
[095]  fs.azure.rename.raises.exceptions = "false" [core-site.xml]
[096]  fs.azure.sas.token.provider.type = (unset)
[097]  fs.azure.sas.fixed.token = (unset)
[098]  fs.azure.sas.token.renew.period.for.streams = (unset)
[099]  fs.azure.secure.mode = "true" [core-site.xml]
[100]  fs.azure.shellkeyprovider.script = (unset)
[101]  fs.azure.skipUserGroupMetadataDuringInitialization = (unset)
[102]  fs.azure.ssl.channel.mode = (unset)
[103]  fs.azure.tracingheader.format = (unset)
[104]  fs.azure.use.upn = (unset)
[105]  fs.azure.user.agent.prefix = "unknown" [core-default.xml]
[106]  fs.azure.write.enableappendwithflush = (unset)
[107]  fs.azure.write.max.concurrent.requests = (unset)
[108]  fs.azure.write.max.requests.to.queue = (unset)
[109]  fs.azure.write.request.size = (unset)
[111]  mapreduce.outputcommitter.factory.scheme.abfs = "org.apache.hadoop.fs.azurebfs.commit.AzureManifestCommitterFactory" [mapred-default.xml]
[112]  mapreduce.manifest.committer.cleanup.parallel.delete = (unset)
[113]  mapreduce.manifest.committer.cleanup.parallel.delete.base.first = (unset)
[114]  mapreduce.manifest.committer.delete.target.files = (unset)
[115]  mapreduce.manifest.committer.diagnostics.manifest.directory = (unset)
[116]  mapreduce.manifest.committer.io.thread.count = (unset)
[117]  mapreduce.manifest.committer.manifest.save.attempts = (unset)
[118]  mapreduce.manifest.committer.store.operations.classname = (unset)
[119]  mapreduce.manifest.committer.summary.report.directory = (unset)
[120]  mapreduce.manifest.committer.validate.output = (unset)
[121]  mapreduce.manifest.committer.writer.queue.capacity = (unset)
[122]  mapreduce.fileoutputcommitter.cleanup.skipped = (unset)
[123]  mapreduce.fileoutputcommitter.marksuccessfuljobs = (unset)
[124]  mapreduce.fileoutputcommitter.algorithm.version.v1.experimental.mv.threads = (unset)
[125]  mapreduce.fileoutputcommitter.algorithm.version.v1.experimental.parallel.task.commit = (unset)
[126]  mapreduce.fileoutputcommitter.algorithm.version.v1.experimental.parallel.rename.recovery = (unset)
[127]  fs.azure.test.account.name = "alicewasbwales.blob.core.windows.net" [core-site.xml]; ("${fs.azure.wasb.account.name}")
[128]  fs.azure.test.namespace.enabled = "true" [core-site.xml]
[129]  fs.azure.abfs.account.name = "alicewales.dfs.core.windows.net" [core-site.xml]; ("${abfs.account.full.name}")
[130]  fs.contract.test.fs.abfs = "abfs://alice-testing@alicewales.dfs.core.windows.net" [core-site.xml]; ("abfs://alice-testing@${abfs.account.full.name}")
[132]  fs.azure.account.key.alicewales.dfs.core.windows.net = "v1**********************************************************************************mA==" [88] [core-site.xml]
[133]  fs.azure.account.oauth.provider.type.alicewales.dfs.core.windows.net = (unset)
[134]  fs.azure.account.oauth2.client.endpoint.alicewales.dfs.core.windows.net = (unset)
[135]  fs.azure.account.oauth2.client.id.alicewales.dfs.core.windows.net = (unset)
[136]  fs.azure.account.oauth2.client.secret.alicewales.dfs.core.windows.net = (unset)
[137]  fs.azure.account.oauth2.msi.authority.alicewales.dfs.core.windows.net = (unset)
[138]  fs.azure.account.oauth2.msi.endpoint.alicewales.dfs.core.windows.net = (unset)
[139]  fs.azure.account.oauth2.msi.tenant.alicewales.dfs.core.windows.net = (unset)
[140]  fs.azure.account.oauth2.user.name.alicewales.dfs.core.windows.net = (unset)
[141]  fs.azure.account.oauth2.refresh.token.alicewales.dfs.core.windows.net = (unset)
[142]  fs.azure.account.oauth2.token.file.alicewales.dfs.core.windows.net = (unset)
[143]  fs.azure.account.oauth2.user.name.alicewales.dfs.core.windows.net = (unset)
[144]  fs.azure.account.oauth2.user.password.alicewales.dfs.core.windows.net = (unset)
[145]  fs.azure.account.keyprovider.alicewales.dfs.core.windows.net = (unset)

13. Required Classes
====================

All these classes must be on the classpath

class: com.fasterxml.jackson.annotation.JsonProperty
resource: com/fasterxml/jackson/annotation/JsonProperty.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/jackson-annotations-2.18.6.jar!/com/fasterxml/jackson/annotation/JsonProperty.class
       file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/jackson-annotations-2.18.6.jar
class: com.fasterxml.jackson.core.JsonFactory
resource: com/fasterxml/jackson/core/JsonFactory.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/jackson-core-2.18.6.jar!/com/fasterxml/jackson/core/JsonFactory.class
       file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/jackson-core-2.18.6.jar
class: com.fasterxml.jackson.databind.ObjectReader
resource: com/fasterxml/jackson/databind/ObjectReader.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/jackson-databind-2.18.6.jar!/com/fasterxml/jackson/databind/ObjectReader.class
       file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/jackson-databind-2.18.6.jar
class: org.apache.http.client.utils.URIBuilder
resource: org/apache/http/client/utils/URIBuilder.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/httpclient-4.5.13.jar!/org/apache/http/client/utils/URIBuilder.class
       file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/httpclient-4.5.13.jar
class: org.apache.hadoop.fs.azurebfs.AzureBlobFileSystem
resource: org/apache/hadoop/fs/azurebfs/AzureBlobFileSystem.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/hadoop-azure-3.5.0.jar!/org/apache/hadoop/fs/azurebfs/AzureBlobFileSystem.class
       file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/hadoop-azure-3.5.0.jar

14. Optional Classes
====================

These classes are needed in some versions of Hadoop.
And/or for optional features to work.

class: org.wildfly.openssl.OpenSSLProvider
resource: org/wildfly/openssl/OpenSSLProvider.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/wildfly-openssl-2.2.5.Final.jar!/org/wildfly/openssl/OpenSSLProvider.class
       file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/wildfly-openssl-2.2.5.Final.jar
class: com.sun.security.cert.internal.x509.X509V1CertImpl
resource: com/sun/security/cert/internal/x509/X509V1CertImpl.class
       Not found on classpath: com.sun.security.cert.internal.x509.X509V1CertImpl
class: com.google.common.base.Preconditions
resource: com/google/common/base/Preconditions.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/guava-33.4.8-jre.jar!/com/google/common/base/Preconditions.class
       file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/guava-33.4.8-jre.jar
class: org.apache.hadoop.thirdparty.com.google.common.base.Preconditions
resource: org/apache/hadoop/thirdparty/com/google/common/base/Preconditions.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/hadoop-shaded-guava-1.5.0.jar!/org/apache/hadoop/thirdparty/com/google/common/base/Preconditions.class
       file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/hadoop-shaded-guava-1.5.0.jar
class: org.apache.hadoop.fs.EtagSource
resource: org/apache/hadoop/fs/EtagSource.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/hadoop-common-3.5.0.jar!/org/apache/hadoop/fs/EtagSource.class
       file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/hadoop-common-3.5.0.jar
class: org.apache.hadoop.mapreduce.lib.output.committer.manifest.ManifestCommitter
resource: org/apache/hadoop/mapreduce/lib/output/committer/manifest/ManifestCommitter.class
       jar:file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/mapreduce/hadoop-mapreduce-client-core-3.5.0.jar!/org/apache/hadoop/mapreduce/lib/output/committer/manifest/ManifestCommitter.class
       file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/mapreduce/hadoop-mapreduce-client-core-3.5.0.jar
class: org.apache.hadoop.mapreduce.lib.output.ResilientCommitByRenameHelper
resource: org/apache/hadoop/mapreduce/lib/output/ResilientCommitByRenameHelper.class
       Not found on classpath: org.apache.hadoop.mapreduce.lib.output.ResilientCommitByRenameHelper
class: org.apache.knox.gateway.cloud.idbroker.abfs.AbfsIDBTokenIdentifier
resource: org/apache/knox/gateway/cloud/idbroker/abfs/AbfsIDBTokenIdentifier.class
       Not found on classpath: org.apache.knox.gateway.cloud.idbroker.abfs.AbfsIDBTokenIdentifier

At least one optional class was missing -the filesystem client *may* still work

15. Required Resources
======================


16. Optional Resources
======================

resource: log4j.properties
       file:/Users/alice/Projects/Releases/hadoop-3.5.0/etc/hadoop/log4j.properties

17. Output Buffering
====================

Written data is buffered to disk with up to 20 blocks queued per stream

18. Validating buffer directories
=================================

Buffer configuration option fs.azure.buffer.dir = "/tmp/hadoop-alice/abfs"
Raw configuration option "${env.LOCAL_DIRS:-${hadoop.tmp.dir}}/abfs"
Number of buffer directories: 1
Buffer path "/tmp/hadoop-alice/abfs":
	* Supports creating temporary files
	* Exists and is writable
	* Free space on device 17,316,941,824
	* Usable space on device 17,316,941,824
	* Contains 0 file(s) with total size 0 bytes

No output buffer issues identified

Rerun storediag with the -w option to test write access to the store

19. Extra options with prefix "fs.abfs.ext." :
==============================================

No configuration options with prefix "fs.abfs.ext."

20. Authentication
==================

Filesystem name: alice-testing
Account: alicewales.dfs.core.windows.net
Authentication type in fs.azure.account.auth.type is "SharedKey" [fs.azure.account.auth.type.alicewales.dfs.core.windows.net]
Authentication is SharedKey
Resolving secrets in Hadoop configuration class/JCEKS
Secret key for authentication: " v***********************************************************************************mA==" [89] (source fs.azure.account.key.alicewales.dfs.core.windows.net)

21. Locating implementation class for Filesystem scheme abfs://
===============================================================

FileSystem for abfs:// is: org.apache.hadoop.fs.azurebfs.AzureBlobFileSystem
Loaded from: file:/Users/alice/Projects/Releases/hadoop-3.5.0/share/hadoop/common/lib/hadoop-azure-3.5.0.jar via jdk.internal.loader.ClassLoaders$AppClassLoader@5ffd2b27

22. TLS System Properties
=========================

[001]  java.version = "17.0.17"
[002]  java.library.path = "/Users/alice/Projects/Releases/hadoop-3.5.0/lib/native"
[003]  com.sun.net.ssl.checkRevocation = (unset)
[004]  https.protocols = (unset)
[005]  javax.net.debug = (unset)
[006]  javax.net.ssl.keyStore = (unset)
[007]  javax.net.ssl.keyStorePassword = (unset)
[008]  javax.net.ssl.trustStore = (unset)
[009]  javax.net.ssl.trustStorePassword = (unset)
[010]  jdk.certpath.disabledAlgorithms = (unset)
[011]  jdk.tls.client.cipherSuites = (unset)
[012]  jdk.tls.client.protocols = (unset)
[013]  jdk.tls.disabledAlgorithms = (unset)
[014]  jdk.tls.legacyAlgorithms = (unset)
[015]  jsse.enableSNIExtension = (unset)


23. HTTPS supported protocols
=============================

    TLSv1.3
    TLSv1.2
    TLSv1.1
    TLSv1
    SSLv3
    SSLv2Hello

24. Endpoints
=============

Attempting to list and connect to public service endpoints,
without any authentication credentials.

- This is just testing the reachability of the URLs.
- If the request fails with any network error it is likely
  to be configuration problem with address, proxy, etc.
- If it is some authentication error, then don't worry:
    the results of the filesystem operations are what really matters

24.1 Endpoint: https://alicewales.dfs.core.windows.net/
--------------------------------------------------------
Store
Determined from filesystem URL
Canonical hostname 20.209.7.2
  IP address 20.209.7.2
Proxy: DIRECT
[19:21:38.549] Starting: GET https://alicewales.dfs.core.windows.net/
Response: 400 : The request URI is invalid.
HTTP response 400 from https://alicewales.dfs.core.windows.net/: The request URI is invalid.
Using proxy: false 
null: HTTP/1.1 400 The request URI is invalid.
x-ms-version: 2018-03-28
Server: Windows-Azure-HDFS/1.0 Microsoft-HTTPAPI/2.0
x-ms-error-code: InvalidUri
Content-Length: 154
x-ms-request-id: 73082376-c01f-0012-6f0e-eea0cd000000
Date: Wed, 27 May 2026 19:21:38 GMT
Content-Type: application/json;charset=utf-8

{"error":{"code":"InvalidUri","message":"The request URI is invalid.\nRequestId:73082376-c01f-0012-6f0e-eea0cd000000\nTime:2026-05-27T19:21:38.9157285Z"}}

Duration of GET https://alicewales.dfs.core.windows.net/: 00:00:00.409

24.2 Endpoint: https://login.microsoftonline.com/Common/oauth2/token
--------------------------------------------------------------------
Oauth refresh token endpoint
From configuration key fs.azure.account.oauth2.refresh.token.endpoint
Canonical hostname 20.190.160.65
  IP address 20.190.160.65
Proxy: DIRECT
[19:21:39.012] Starting: GET https://login.microsoftonline.com/Common/oauth2/token
Response: 200 : OK
HTTP response 200 from https://login.microsoftonline.com/Common/oauth2/token: OK
Using proxy: false 
null: HTTP/1.1 200 OK
x-ms-ests-server: 2.1.24362.7 - NEULR1 ProdSlices
X-Content-Type-Options: nosniff
Pragma: no-cache
P3P: CP="DSP CUR OTPi IND OTRi ONL FIN"
Date: Wed, 27 May 2026 19:21:38 GMT
x-ms-srs: 1.P
Strict-Transport-Security: max-age=31536000; includeSubDomains
Cache-Control: no-store, no-cache
Set-Cookie: stsservicecookie=estsfd; path=/; secure; samesite=none; httponly,x-ms-gateway-slice=estsfd; path=/; secure; samesite=none; httponly,esctx=PAQABBwEAAAAdDD7nC9b5Q7JPd_okEQRFRXZvU3RzQXJ0aWZhY3RzDQAAAAAAbiVYAEVbxnm9U1MYYXLSjWh-xVci8uUhN94FSbUhpsPcuKetC3Aw6jlJjNSctmx5QxeLNOolVqXBbW9F9RoJZGtgKMMo1Gt7Avkd14P0hflqXQKqzl6Kg-2bJYUqnMZZEmsWuye0aHCNFZvl6JROLtpl928z4xd_mXMIvf4-UgkgAA; domain=.login.microsoftonline.com; path=/; secure; HttpOnly; SameSite=None,fpc=AqdnaFjJ27BEsQX6WsDLvS8; expires=Fri, 26-Jun-2026 19:21:39 GMT; path=/; secure; HttpOnly; SameSite=None
X-DNS-Prefetch-Control: on
Expires: -1
Content-Length: 23749
X-XSS-Protection: 0
Content-Security-Policy-Report-Only: object-src 'none'; base-uri 'self'; script-src 'self' 'nonce-g73gGvFPO1J008Xd8cqPYA' 'unsafe-inline' 'unsafe-eval' https://*.msauth.net https://*.msftauth.net https://*.msftauthimages.net https://*.msauthimages.net https://*.msidentity.com https://*.microsoftonline-p.com https://*.microsoftazuread-sso.com https://*.azureedge.net https://*.outlook.com https://*.office.com https://*.office365.com https://*.microsoft.com https://*.bing.com 'report-sample'; report-uri https://csp.microsoft.com/report/ESTS-UX-All
x-ms-request-id: ebde0920-c292-4413-ac7b-e1f6bbe29d00
Link: <https://aadcdn.msauth.net>; rel=preconnect; crossorigin,<https://aadcdn.msauth.net>; rel=dns-prefetch,<https://aadcdn.msftauth.net>; rel=dns-prefetch,<https://aadcdn.msauth.net>; rel=preconnect; crossorigin,<https://aadcdn.msauth.net>; rel=dns-prefetch,<https://aadcdn.msauth.net>; rel=preconnect; crossorigin
Content-Type: text/html; charset=utf-8



<!-- Copyright (C) Microsoft Corporation. All rights reserved. -->
<!DOCTYPE html>
<html dir="ltr" class="" lang="en">
<head>
    <title>Sign in to your account</title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes">
    <meta http-equiv="Pragma" content="no-cache">
    <meta http-equiv="Expires" content="-1">
    <link rel="preconnect" href="https://aadcdn.msauth.net" crossorigin>
<meta http-equiv="x-dns-prefetch-control" content="on">
<link rel="dns-prefetch" href="//aadcdn.msauth.net">
<link rel="dns-prefetch" href="//aadcdn.msftauth.net">

    <meta name="PageID" content="ConvergedError" />
    <meta name="SiteID" content="" />
    <meta name="ReqLC" content="1033" />
    <meta name="LocLC" content="en-US" />

        <meta name="referrer" content="origin" />

        <meta name="format-d

Duration of GET https://login.microsoftonline.com/Common/oauth2/token: 00:00:00.242
2026-05-27 20:21:39,272 [main] INFO  diag.AbfsDiagnosticsInfo (StoreDurationInfo.java:<init>(84)) - Starting: Probing for account name being a valid host
Canonical hostname 20.209.7.2
  IP address 20.209.7.2
2026-05-27 20:21:39,277 [main] INFO  diag.AbfsDiagnosticsInfo (StoreDurationInfo.java:close(190)) - Duration of Probing for account name being a valid host: 00:00:00.019

25. Test filesystem abfs://alice-testing@alicewales.dfs.core.windows.net/
===========================================================================

Trying some list and read operations

25.1 Filesystem client Instantiation
------------------------------------
2026-05-27 20:21:39,278 [main] INFO  diag.StoreDiag (StoreDurationInfo.java:<init>(84)) - Starting: Creating filesystem for abfs://alice-testing@alicewales.dfs.core.windows.net/
2026-05-27 20:21:39,486 [main] WARN  fs.FileSystem (FileSystem.java:createFileSystem(3624)) - Failed to initialize filesystem abfs://alice-testing@alicewales.dfs.core.windows.net/
2026-05-27 20:21:39,487 [main] INFO  diag.StoreDiag (StoreDurationInfo.java:close(190)) - Duration of Creating filesystem for abfs://alice-testing@alicewales.dfs.core.windows.net/: 00:00:00.208
java.io.IOException: Invalid azure account value
	at org.apache.hadoop.fs.store.diag.StoreDiag.executeFileSystemOperations(StoreDiag.java:760)
	at org.apache.hadoop.fs.store.diag.StoreDiag.runDiagnostics(StoreDiag.java:241)
	at org.apache.hadoop.fs.store.diag.StoreDiag.run(StoreDiag.java:171)
	at org.apache.hadoop.fs.store.diag.StoreDiag.run(StoreDiag.java:156)
	at org.apache.hadoop.util.ToolRunner.run(ToolRunner.java:82)
	at org.apache.hadoop.util.ToolRunner.run(ToolRunner.java:97)
	at org.apache.hadoop.fs.store.Cloudstore.exec(Cloudstore.java:178)
	at org.apache.hadoop.fs.store.Cloudstore.main(Cloudstore.java:188)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at org.apache.hadoop.util.RunJar.run(RunJar.java:333)
	at org.apache.hadoop.util.RunJar.main(RunJar.java:254)
2026-05-27 20:21:39,488 [main] INFO  util.ExitUtil (ExitUtil.java:terminate(248)) - Exiting with status -1: java.io.IOException: Invalid azure account value

```
