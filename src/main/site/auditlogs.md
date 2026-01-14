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

# `auditlogs`: convert AWS S3 logs to avro records

The `auditlogs` command takes a file or directory of AWS S3 logs, generating a single aggregate avro file of these logs.
Any records which contain S3A Audit headers will have these parsed and added to the `audit` map in the schema.

The audit log files are created by S3 storage (if enabled) every hour, so many, possibly small files, are created.

The `auditlogs` command will take a directory (local or remote)

## Usage

```
> bin/hadoop jar $CLOUDSTORE auditlogs -overwrite ../logs/stevel-london 2026-stevel.avro
Processing logs in source directory ../logs/stevel-london
Writing output to file 2026-stevel.avro
2026-01-14 16:46:25,725 [main] INFO  audit.AuditTool (DurationInfo.java:<init>(77)) - Starting: Log Source ../logs/stevel-london
2026-01-14 16:46:27,291 [main] INFO  audit.AuditLogProcessor (DurationInfo.java:<init>(77)) - Starting: [00001] Processing file:/Users/stevel/Projects/Releases/logs/stevel-london/log-2026-01-02-14-32-17-E552136FFD8BB1DE
2026-01-14 16:46:27,365 [main] INFO  audit.AuditLogProcessor (DurationInfo.java:close(98)) - [00001] Processing file:/Users/stevel/Projects/Releases/logs/stevel-london/log-2026-01-02-14-32-17-E552136FFD8BB1DE: duration 0:00.074s
2026-01-14 16:46:27,412 [main] INFO  audit.AuditLogProcessor (DurationInfo.java:<init>(77)) - Starting: [00002] Processing file:/Users/stevel/Projects/Releases/logs/stevel-london/log-2025-12-19-08-31-24-FC1D799A1C284943
2026-01-14 16:46:27,419 [main] INFO  audit.AuditLogProcessor (DurationInfo.java:close(98)) - [00002] Processing file:/Users/stevel/Projects/Releases/logs/stevel-london/log-2025-12-19-08-31-24-FC1D799A1C284943: duration 0:00.006s

...

2026-01-14 16:55:11,108 [main] INFO  audit.AuditLogProcessor (DurationInfo.java:<init>(77)) - Starting: [03101] Processing file:/Users/stevel/Projects/Releases/logs/stevel-london/log-2025-12-16-12-33-08-D2A6B5894C47AE33
2026-01-14 16:55:11,109 [main] INFO  audit.AuditLogProcessor (DurationInfo.java:close(98)) - [03101] Processing file:/Users/stevel/Projects/Releases/logs/stevel-london/log-2025-12-16-12-33-08-D2A6B5894C47AE33: duration 0:00.001s
2026-01-14 16:55:11,117 [main] INFO  audit.AuditLogProcessor (AuditLogProcessor.java:mergeAndParseAuditLogFiles(298)) - Successfully parsed: 64765 records with 39156 referrer headers in the logs
Read 3101 source files
Processed 64765 records of which 39156 had audit information
Total processing time: 2:23.363s
Saved output to 2026-stevel.avro

> ls -al 2026-stevel.avro 
  -rw-r--r--@ 1 stevel  staff  65175013 Jan 14 16:55 2026-stevel.avro
  
> java -jar avro-tools-1.13.0-SNAPSHOT.jar getschema 2026-stevel.avro 
{
  "type" : "record",
  "name" : "AvroS3LogEntryRecord",
  "namespace" : "org.apache.hadoop.fs.store.audit",
  "fields" : [ {
    "name" : "owner",
    "type" : "string"
  }, {
    "name" : "bucket",
    "type" : "string"
  }, {
    "name" : "tstamp",
    "type" : "string"
  }, {
    "name" : "remoteip",
    "type" : "string"
  }, {
    "name" : "requester",
    "type" : "string"
  }, {
    "name" : "requestid",
    "type" : "string"
  }, {
    "name" : "verb",
    "type" : "string"
  }, {
    "name" : "key",
    "type" : "string"
  }, {
    "name" : "requesturi",
    "type" : "string"
  }, {
    "name" : "http",
    "type" : "string"
  }, {
    "name" : "awserrorcode",
    "type" : "string"
  }, {
    "name" : "bytessent",
    "type" : [ "long", "null" ]
  }, {
    "name" : "objectsize",
    "type" : [ "long", "null" ]
  }, {
    "name" : "totaltime",
    "type" : [ "long", "null" ]
  }, {
    "name" : "turnaroundtime",
    "type" : [ "long", "null" ]
  }, {
    "name" : "referrer",
    "type" : "string"
  }, {
    "name" : "useragent",
    "type" : "string"
  }, {
    "name" : "version",
    "type" : "string"
  }, {
    "name" : "hostid",
    "type" : "string"
  }, {
    "name" : "sigv",
    "type" : "string"
  }, {
    "name" : "cypher",
    "type" : "string"
  }, {
    "name" : "auth",
    "type" : "string"
  }, {
    "name" : "endpoint",
    "type" : "string"
  }, {
    "name" : "tls",
    "type" : "string"
  }, {
    "name" : "tail",
    "type" : "string"
  }, {
    "name" : "audit",
    "type" : {
      "type" : "map",
      "values" : "string"
    }
  } ]
}

> java -jar avro-tools-1.13.0-SNAPSHOT.jar count 2026-stevel.avro 
64765

```

## Avro Record Schema
```json
{
    "type" : "record", "name" : "AvroS3LogEntryRecord",
    "namespace" : "org.apache.hadoop.fs.store.audit",
    "fields" : [
      { "name" : "owner", "type" : "string" },
      { "name" : "bucket", "type" : "string" },
      { "name" : "tstamp", "type" : "string" },
      { "name" : "remoteip", "type" : "string" },
      { "name" : "requester", "type" : "string" },
      { "name" : "requestid", "type" : "string" },
      { "name" : "verb", "type" : "string" },
      { "name" : "key", "type" : "string" },
      { "name" : "requesturi", "type" : "string" },
      { "name" : "http", "type" : "string" },
      { "name" : "awserrorcode", "type" : "string" },
      { "name" : "bytessent", "type" : ["long", "null"] },
      { "name" : "objectsize", "type" : ["long", "null"] },
      { "name" : "totaltime", "type" : ["long", "null"] },
      { "name" : "turnaroundtime" , "type" : ["long", "null"] },
      { "name" : "referrer", "type" : "string" },
      { "name" : "useragent", "type" : "string" },
      { "name" : "version", "type" : "string" },
      { "name" : "hostid", "type" : "string" },
      { "name" : "sigv", "type" : "string" },
      { "name" : "cypher", "type" : "string" },
      { "name" : "auth", "type" : "string" },
      { "name" : "endpoint", "type" : "string" },
      { "name" : "tls", "type" : "string" },
      { "name" : "tail", "type" : "string" },
      { "name" : "audit", "type" : {"type": "map", "values": "string"} }
    ]
}
```

### Spark SQL Schema

```sql
CREATE TABLE avro_table
    (owner STRING,
     bucket STRING,
     tstamp STRING,
     remoteip STRING,
     requester STRING,
     requestid STRING,
     verb STRING,
     key STRING,
     requesturi STRING,
     http STRING,
     awserrorcode STRING,
     bytessent BIGINT,
     objectsize BIGINT,
     totaltime BIGINT,
     turnaroundtime BIGINT,
     referrer STRING,
     useragent STRING,
     version STRING,
     hostid STRING,
     sigv STRING,
     cypher STRING,
     auth STRING,
     endpoint STRING,
     tls STRING,
     tail STRING,
     audit MAP<STRING, STRING>)
    USING avro
    LOCATION ... ;
```

### Performance enhancements

The process is slow because it reads a file at a time; this
is for simplicity and to guarantee the generated file is a concatenation of the source data.

If anyone has large amounts of data, converting the processing
to a spark job would be straightforward.

### Importing the records to a database

The `audit` column is a map of arbitrary data; this is where the `VARIANT` type
of Apache Iceberg excels.