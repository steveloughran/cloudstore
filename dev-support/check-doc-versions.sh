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
# whose version disagrees with the current ${project.version} (passed in $1).
# Used as a verify-phase guard; pair with dev-support/bump-version.sh.

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <expected-version>" >&2
  exit 2
fi

EXPECTED="$1"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Search README, AGENTS, BUILDING, and src/site/markdown for stale references.
# Anything matching cloudstore-<digits.digits>.jar that isn't the expected
# version is a drift.
BAD=()
while IFS= read -r -d '' f; do
  while IFS= read -r match; do
    found="${match#*cloudstore-}"
    found="${found%%.jar*}"
    if [[ "$found" != "$EXPECTED" ]]; then
      BAD+=("$f: found cloudstore-${found}.jar, expected cloudstore-${EXPECTED}.jar")
    fi
  done < <(grep -oE "cloudstore-[0-9]+\.[0-9]+\.jar" "$f" || true)
done < <(find . \( -path ./target -o -path ./releases -o -path './.git' \) -prune -o \
              \( -name '*.md' -print0 \) 2>/dev/null)

if [[ ${#BAD[@]} -gt 0 ]]; then
  echo "Stale jar version references in docs (expected cloudstore-${EXPECTED}.jar):" >&2
  printf '  %s\n' "${BAD[@]}" >&2
  echo >&2
  echo "Run: dev-support/bump-version.sh ${EXPECTED}" >&2
  exit 1
fi
