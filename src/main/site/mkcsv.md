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

# Command `mkcsv`

Creates a CSV file with a given path; useful
for scale testing CSV processing .

```
hadoop jar cloudstore-1.0.jar mkcsv  -header -quote -verbose  10000 s3a://bucket/file.csv
```

The format is a variable width sequence, with entries cross referencing each other for ease of validation.
```csv
"rowId","dataCrc","data","rowId2","rowCrc"
"1","4098016739","0008-0009-000a-000b-000c-000d-000e-000f-0010-0011-0012-0013-0014-0015-0016-0017-0018-0019-001a-001b-001c-001d-001e-001f-0020-0021-0022-0023-0024-0025-0026-0027-0028-0029-002a-002b-002c-002d-002e-002f-0030-0031-0032-0033-0034-0035-0036-0037-0038-0039-003a-003b-003c-003d-003e-003f-0040-0041-0042-0043","1","2526808319"
"2","4102619375","005b","2","3614304611"
"3","2808119570","005e-005f-0060","3","3847878359"
```

## Invariants

For each row
```java
rowId == rowId2

dataCrc == new CRC32().update(data.getBytes(StandardCharsets.UTF_8))
rowCrC == crc32 of all previous fields, including quotes, *excluding separators*
// and ignoring headers    
forall n: row[n].rowID == n
```


## Schemas for Apache Spark
```scala

/**
 * Dataset case class.
 */
case class CsvRecord(
    rowId: Long,
    dataCrc: Long,
    data: String,
    rowId2: Long,
    rowCrc: Long)

/**
 * The StructType of the CSV data.
 */
val csvSchema: StructType = {
    new StructType().
    add("rowId", LongType).
    add("dataCrc", LongType).
    add("data", StringType).
    add("rowId2", LongType).
    add("rowCrc", LongType)
}

```