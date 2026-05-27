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
# Bump the cloudstore project (pom) version. Accepts a SNAPSHOT or release
# form, e.g. "1.4-SNAPSHOT" or "1.4". Does NOT rewrite site documentation —
# release-time doc updates are handled by dev-support/update-site-docs.sh.
#
# Also updates BUILDING.md's fish `set -gx ver <v>` line so the release
# command block always points at the bare release artifact name (a trailing
# -SNAPSHOT is stripped before substituting).
#
# Usage:   dev-support/bump-version.sh <new-version>
# Example: dev-support/bump-version.sh 1.4-SNAPSHOT
# Example: dev-support/bump-version.sh 1.4

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <new-version>" >&2
  echo "       e.g. 1.4-SNAPSHOT  (development)" >&2
  echo "       or   1.4           (release)" >&2
  exit 2
fi

NEW="$1"

# Accept X.Y or X.Y-SNAPSHOT only.
if [[ ! "$NEW" =~ ^[0-9]+\.[0-9]+(-SNAPSHOT)?$ ]]; then
  echo "error: version must be X.Y or X.Y-SNAPSHOT (got '$NEW')" >&2
  exit 2
fi

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

# BUILDING.md's release block declares `set -gx ver <v>` in fish (`version`
# is a reserved variable name in fish). That line is consumed by the
# release command sequence, which names artifacts via `cloudstore-$ver.jar`,
# so it must always reflect the bare release version — strip any trailing
# -SNAPSHOT.
RELEASE_VER="${NEW%-SNAPSHOT}"
if [[ -f BUILDING.md ]] && grep -qE "^set -gx ver [0-9]+\.[0-9]+( |$|	)" BUILDING.md; then
  sed -i.bak -E "s|^set -gx ver [0-9]+\.[0-9]+\\b|set -gx ver ${RELEASE_VER}|" BUILDING.md
  rm -f BUILDING.md.bak
  echo "  rewrote BUILDING.md (set -gx ver ${RELEASE_VER})"
fi

echo "done. Review with: git diff"

# Remind: if this was a release bump, the site docs still reference the
# previous release until update-site-docs.sh is run.
if [[ "$NEW" != *-SNAPSHOT ]]; then
  echo
  echo "Release version detected. To roll site docs forward, run:"
  echo "  dev-support/update-site-docs.sh ${NEW}"
fi
