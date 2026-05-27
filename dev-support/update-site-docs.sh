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
# Roll the site documentation forward to a newly-released cloudstore version.
# Rewrites every `cloudstore-<old>.jar` and `cloudstore-<old>-cyclonedx`
# reference in README.md, AGENTS.md, BUILDING.md, and src/site/markdown/*.md,
# and bumps the <cloudstore.docs.version> property in pom.xml so the verify
# gate (dev-support/check-doc-versions.sh) tracks the documented release.
#
# Release versions only — SNAPSHOT artifacts are not documented downloads.
#
# Usage:   dev-support/update-site-docs.sh <new-version>
# Example: dev-support/update-site-docs.sh 1.4

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <new-version>   (release form only, e.g. 1.4)" >&2
  exit 2
fi

NEW="$1"

if [[ ! "$NEW" =~ ^[0-9]+\.[0-9]+$ ]]; then
  echo "error: site docs track release versions only; got '$NEW'" >&2
  echo "       expected X.Y (no -SNAPSHOT, no qualifiers)" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

OLD="$(mvn -q help:evaluate -Dexpression=cloudstore.docs.version \
        -DforceStdout --non-recursive 2>/dev/null \
        | tail -n1 | tr -d '[:space:]')"

if [[ -z "$OLD" ]]; then
  echo "could not read cloudstore.docs.version from pom.xml" >&2
  exit 1
fi

if [[ "$OLD" == "$NEW" ]]; then
  echo "site docs already at $NEW; nothing to do"
  exit 0
fi

echo "rolling site docs $OLD -> $NEW"

mvn -q versions:set-property -Dproperty=cloudstore.docs.version \
    -DnewVersion="$NEW" -DgenerateBackupPoms=false

# Files to rewrite. README + AGENTS + BUILDING at top level, plus all site
# markdown. Add new doc files here as they appear.
FILES=(
  README.md
  AGENTS.md
  BUILDING.md
)
while IFS= read -r f; do FILES+=("$f"); done < <(find src/site/markdown -name '*.md')

for f in "${FILES[@]}"; do
  [[ -f "$f" ]] || continue
  changed=0
  # cloudstore-<v>.jar (covers cloudstore-<v>.jar.asc as a substring).
  if grep -q "cloudstore-${OLD}\.jar" "$f"; then
    sed -i.bak "s|cloudstore-${OLD}\.jar|cloudstore-${NEW}.jar|g" "$f"
    changed=1
  fi
  # cloudstore-<v>-cyclonedx.* (covers .json/.xml + their .asc siblings).
  if grep -q "cloudstore-${OLD}-cyclonedx" "$f"; then
    sed -i.bak "s|cloudstore-${OLD}-cyclonedx|cloudstore-${NEW}-cyclonedx|g" "$f"
    changed=1
  fi
  rm -f "${f}.bak"
  if [[ $changed -eq 1 ]]; then
    echo "  rewrote $f"
  fi
done

echo "done. Review with: git diff"
