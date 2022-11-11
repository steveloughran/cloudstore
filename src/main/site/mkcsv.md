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

Creates a CSV file with a given path; useful for scale testing CSV processing.

```bash
hadoop jar cloudstore-1.0.jar mkcsv  -header -quote -verbose  10000 s3a://bucket/file.csv
```

The format is a variable width sequence, with entries cross referencing each other for validation.
```csv
"start","rowId","length","dataCrc","data","rowId2","rowCrc","end"
"start","1","87","691051183","bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","1","2707924207","end"
"start","2","40","2886466480","cccccccccccccccccccccccccccccccccccccccc","2","2141198053","end"
"start","3","98","3320970725","dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","3","4203069111","end"
"start","4","8","1257926895","eeeeeeee","4","189792478","end"
"start","5","25","1630497970","fffffffffffffffffffffffff","5","1034603103","end"
"start","6","38","557554018","gggggggggggggggggggggggggggggggggggggg","6","1412646710","end"
"start","7","86","951894681","hhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhh","7","2062289315","end"
"start","8","45","3065088391","iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii","8","3774714774","end"
"start","9","70","2839984696","jjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjj","9","303056462","end"
```

## Invariants

For each row
```java
start == "start"
rowId == rowId2
length == a random int >= 0    
data = string where data.length() == length
       elements of data == char c where c in "[a-z][A-Z][0-9]"
dataCrc == new CRC32().update(data.getBytes(StandardCharsets.UTF_8))
rowCrC == crc32 of all previous fields, including quotes, *excluding separators*
end == "end"
// and ignoring headers    
forall n: row[n].rowID == n
```


## Schemas for Apache Spark
```scala

/**
 * Dataset class.
 * Latest build is "start","rowId","length","dataCrc","data","rowId2","rowCrc","end"
 */
case class CsvRecord(
    start: String,
    rowId: Long,
    length: Long,
    dataCrc: Long,
    data: String,
    rowId2: Long,
    rowCrc: Long,
    end: String)

/**
 * The StructType of the CSV data.
 * "start","rowId","length","dataCrc","data","rowId2","rowCrc","end"
 */
val csvSchema: StructType = {
  new StructType().
    add("start", StringType).
    add("rowId", LongType).
    add("length", LongType).
    add("dataCrc", LongType).
    add("data", StringType).
    add("rowId2", LongType).
    add("rowCrc", LongType).
    add("end", StringType)
}

```