#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Bump the cloudstore project version and rewrite every `cloudstore-<old>.jar`
# reference in markdown docs to `cloudstore-<new>.jar`.
#
# Usage:  dev-support/bump-version.sh <new-version>
# Example: dev-support/bump-version.sh 1.3

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <new-version>" >&2
  exit 2
fi

NEW="$1"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

OLD="$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' \
        --non-recursive exec:exec 2>/dev/null)"

if [[ -z "$OLD" ]]; then
  echo "could not read current version" >&2
  exit 1
fi

if [[ "$OLD" == "$NEW" ]]; then
  echo "current version already $NEW; nothing to do"
  exit 0
fi

echo "bumping $OLD -> $NEW"

mvn -q versions:set -DnewVersion="$NEW" -DgenerateBackupPoms=false

# Files to rewrite. README + AGENTS + BUILDING at top level, plus all site
# markdown. Add new doc files here as they appear.
FILES=(
  README.md
  AGENTS.md
  BUILDING.md
)
while IFS= read -r f; do FILES+=("$f"); done < <(find src/site/markdown -name '*.md')

for f in "${FILES[@]}"; do
  if [[ -f "$f" ]] && grep -q "cloudstore-${OLD}.jar" "$f"; then
    # macOS / BSD sed compatible
    sed -i.bak "s|cloudstore-${OLD}\.jar|cloudstore-${NEW}.jar|g" "$f"
    rm -f "${f}.bak"
    echo "  rewrote $f"
  fi
done

echo "done. Review with: git diff"
