<!---
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License. See accompanying LICENSE file.
-->

# Cloudstore

Cloudstore is a diagnostics library for troubleshooting problems interacting
with cloud storage through Apache Hadoop. The implementation classes live
under the `org.apache.hadoop.fs` package tree but the artifact is not (yet)
part of the Apache Hadoop release.

## Why a separate library

1. Faster release cycle — diagnostics evolve to track features going into
   Hadoop trunk.
2. Lower test bar — code is often written in a hurry to diagnose problems on
   remote sites that are themselves hard to test.
3. Compile against multiple Hadoop versions — currently 3.3+ for the API
   surface that the diagnostics rely on.

## Commands

The full per-command reference is in the **Commands** sidebar. Run

```bash
hadoop jar target/cloudstore-1.2.jar help
```

for a list of every command bundled in the jar.

## Source

TODO