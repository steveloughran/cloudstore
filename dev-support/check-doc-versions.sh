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
# Fail if any committed markdown file references a `cloudstore-<X.Y>.jar`
# whose version disagrees with the most recently released cloudstore
# version (passed in $1; the pom plugin wires this to
# ${cloudstore.docs.version}). Used as a verify-phase guard; pair with
# dev-support/update-site-docs.sh which moves both the property and the
# markdown references together at release time.

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <expected-version>" >&2
  exit 2
fi

EXPECTED="$1"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Search README, AGENTS, BUILDING, and src/site/markdown for stale references.
# Matches any artifact filename: cloudstore-<v>.jar, cloudstore-<v>-cyclonedx.json,
# .xml, and their .asc siblings. Anything whose <v> != $EXPECTED is drift.
BAD=()
while IFS= read -r -d '' f; do
  while IFS= read -r match; do
    # Strip leading "cloudstore-" then take the X.Y prefix.
    rest="${match#cloudstore-}"
    found="${rest%%[!0-9.]*}"
    found="${found%.}"     # drop a trailing '.' if the next char was '.jar' etc.
    if [[ "$found" != "$EXPECTED" ]]; then
      BAD+=("$f: $match (expected cloudstore-${EXPECTED}-)")
    fi
  done < <(grep -oE "cloudstore-[0-9]+\.[0-9]+(-cyclonedx\.(json|xml)|\.jar)(\.asc)?" "$f" || true)
done < <(find . \( -path ./target -o -path ./releases -o -path './.git' \) -prune -o \
              \( -name '*.md' -print0 \) 2>/dev/null)

# BUILDING.md declares `set -gx ver <v>` in fish for the release block
# (fish reserves `version`, so we use `ver`). That version must also match.
if [[ -f BUILDING.md ]]; then
  while IFS= read -r found; do
    if [[ "$found" != "$EXPECTED" ]]; then
      BAD+=("BUILDING.md: set -gx ver $found, expected $EXPECTED")
    fi
  done < <(grep -oE "^set -gx ver [0-9]+\.[0-9]+" BUILDING.md | awk '{print $4}')
fi

if [[ ${#BAD[@]} -gt 0 ]]; then
  echo "Stale artifact version references in docs (expected ${EXPECTED}):" >&2
  printf '  %s\n' "${BAD[@]}" >&2
  echo >&2
  echo "Run: dev-support/update-site-docs.sh ${EXPECTED}" >&2
  exit 1
fi
