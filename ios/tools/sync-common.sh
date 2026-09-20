#!/usr/bin/env bash
#
# Copies the shared reference data out of common/ and into the iOS package's resources.
#
#   ./ios/tools/sync-common.sh
#
# common/data holds the master copies of the WHO growth tables and the vaccination schedules,
# and both platforms read those rather than each keeping their own. Android does this with a
# Gradle task (see syncIllustrations and syncCommonData in android/app/build.gradle.kts); iOS
# does it here, and from an Xcode run-script phase once the app target exists.

set -euo pipefail

IOS_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE_ROOT="$(cd -- "$IOS_ROOT/.." && pwd)"

SOURCE="$WORKSPACE_ROOT/common/data"
DEST="$IOS_ROOT/KilkariCore/Sources/KilkariCore/Resources"

[ -d "$SOURCE" ] || { echo "no common/data at $SOURCE" >&2; exit 1; }

mkdir -p "$DEST"
rm -f "$DEST"/*.json
cp "$SOURCE"/*.json "$DEST"/

echo "synced $(ls -1 "$DEST"/*.json | wc -l | tr -d ' ') file(s) from common/data"
