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

# Command `safeprefetch`: validate prefetch safety of abfs client

Command to probe an abfs store for prefetch safety.

If safe: returns a status code of 0

If unsafe, prints configuration options to disable prefetching
and then returns the exit code -1.

The safety probe considers an abfs store safe if *any* of the conditions are met

* It is from a release *before* `HADOOP-17156. Clear abfs readahead requests on stream close`,
* It has the fix `HADOOP-18546. ABFS. disable purging list of in progress reads in abfs stream close()`
* The `fs.azure.readaheadqueue.depth` is 0
* Cloudera releases: readahead has been completely disabled
  (`HADOOP-18517. ABFS: Add fs.azure.enable.readahead option to disable readahead` is in all CDH releases with the bug)

The probe for the fix relies on `HADOOP-18577. ABFS: Add probes of readahead fix`; a pathcapabilities probe
`fs.azure.capability.readahead.safe` has been added to all abfs releases with the fix.

## Example, probe of a (safe) hadoop 3.3.5


```
bin/hadoop jar $CLOUDSTORE safeprefetch abfs://stevel-testing@stevelukwest.dfs.core.windows.net/user

Probing abfs://stevel-testing@stevelukwest.dfs.core.windows.net/user for prefetch safety
Using filesystem abfs://stevel-testing@stevelukwest.dfs.core.windows.net
Filesystem abfs://stevel-testing@stevelukwest.dfs.core.windows.net/user has prefetch issue fixed (has path capability fs.azure.capability.readahead.safe)
```

## Example, probe of hadoop 3.3.4 -unsafe

```
bin/hadoop jar $CLOUDSTORE safeprefetch abfs://stevel-testing@stevelukwest.dfs.core.windows.net/user
Probing abfs://stevel-testing@stevelukwest.dfs.core.windows.net/user for prefetch safety
Using filesystem abfs://stevel-testing@stevelukwest.dfs.core.windows.net
Store is vulnerable to inconsistent prefetching. This MUST be disabled

WARNING: Filesystem is vulnerable until prefetching is disabled
hadoop XML: 
<configuration>
<fs.azure.readaheadqueue.depth>
  0
</fs.azure.readaheadqueue.depth>
</configuration>



spark: 
spark.hadoop.fs.azure.readaheadqueue.depth 0 


2022-12-19 12:32:06,003 [main] INFO  util.ExitUtil (ExitUtil.java:terminate(241)) - Exiting with status -1: 

```

