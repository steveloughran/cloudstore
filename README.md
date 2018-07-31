# cloudstore

This is going to be for a general cloudstore CLI command for Hadoop

Initally it'll be the a diagnostics entry point, designed to work with Hadoop 2.7+

Why? 

1. Sometimes things fail, and the first problem is invariably some
client-side config. 
1. The Hadoop FS connectors all assume a well configured system, and don't
do much in terms of meaningful diagnostics.
1. This is compounded by the fact that we dare not log secret credentials.
1. And in support calls, it's all to easy to get those secrets, even
though its a major security breach to get them.

The StoreDiag entry point is designed to pick up the FS settings, dump them
with sanitized secrets, and display their provenance. It then
bootstraps connectivity with an attempt to initiate (unauthed) HTTP connections
to the store's endpoints. This should be sufficient to detect proxy and
endpoint configuration problems.

Then it tries to perform some reads and writes against the store. If these
fail, then there's clearly a problem. Hopefully though, there's now enough information
to begin determining what it is.


```bash
bin/hadoop jar cloudstore-2.8.jar s3a://my-readwrite-bucket/
bin/hadoop jar cloudstore-2.8.jar s3a://my-readwrite-bucket/
```
 
The remote store is required to grant full R/W access to the caller, otherwise
the creation tests will fail. 
