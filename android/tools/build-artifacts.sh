#!/usr/bin/env bash
#
# Builds every shippable Kilkari artifact in one go and collects them in release/.
#
#   ./android/tools/build-artifacts.sh            # release bundle + release APK + debug APK
#   ./android/tools/build-artifacts.sh --clean    # same, from a clean build directory
#   ./android/tools/build-artifacts.sh --help     # all options
#
# Signing is automatic: drop a filled-in keystore.properties in android/ and the
# release artifacts come out signed and uploadable. Without one they are unsigned, and
# the script additionally emits a debug-signed copy of the release APK so the minified
# build can still be installed and smoke-tested.
#
# Targets bash 3.2, which is what macOS ships.

set -euo pipefail

# The Gradle project lives in android/; everything it produces is collected at the repo
# root, which is one level further up now that the repo carries an iOS tree beside it.
REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE_ROOT="$(cd -- "$REPO_ROOT/.." && pwd)"
APP_NAME="kilkari"

# ---------------------------------------------------------------- options

DO_CLEAN=false
BUILD_DEBUG=true
BUILD_RELEASE=true
OUT_DIR="$WORKSPACE_ROOT/release"

usage() {
    sed -n '3,12p' "${BASH_SOURCE[0]}" | sed 's/^#\{1,\} \{0,1\}//'
    cat <<'USAGE'

Options:
  --clean          Wipe build/ first. Slower, but rules out stale-output confusion.
  --debug-only     Skip the release bundle and APK.
  --release-only   Skip the debug APK.
  --out DIR        Collect artifacts somewhere other than release/.
  -h, --help       This message.
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --clean)        DO_CLEAN=true ;;
        --debug-only)   BUILD_RELEASE=false ;;
        --release-only) BUILD_DEBUG=false ;;
        --out)          shift; [[ $# -gt 0 ]] || { echo "--out needs a directory" >&2; exit 2; }
                        OUT_DIR="$1" ;;
        -h|--help)      usage; exit 0 ;;
        *)              echo "Unknown option: $1" >&2; echo >&2; usage >&2; exit 2 ;;
    esac
    shift
done

if ! $BUILD_RELEASE && ! $BUILD_DEBUG; then
    echo "--debug-only and --release-only cancel each other out; nothing to build." >&2
    exit 2
fi

# ---------------------------------------------------------------- output helpers

if [[ -t 1 ]]; then
    B=$'\033[1m'; DIM=$'\033[2m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; R=$'\033[0m'
else
    B=; DIM=; GREEN=; YELLOW=; RED=; R=
fi

step() { printf '\n%s==>%s %s%s%s\n' "$GREEN" "$R" "$B" "$*" "$R"; }
info() { printf '    %s\n' "$*"; }
warn() { printf '%s !  %s%s\n' "$YELLOW" "$*" "$R"; }
die()  { printf '%s !  %s%s\n' "$RED" "$*" "$R" >&2; exit 1; }

# Human-readable size, portable across the BSD and GNU flavours of stat.
filesize() {
    local bytes
    bytes=$(stat -f%z "$1" 2>/dev/null || stat -c%s "$1")
    awk -v b="$bytes" 'BEGIN {
        if (b < 1048576) printf "%.0f KB", b/1024; else printf "%.2f MB", b/1048576
    }'
}

sha256() {
    if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | cut -d' ' -f1
    else sha256sum "$1" | cut -d' ' -f1; fi
}

# ---------------------------------------------------------------- locate the SDK

sdk_dir() {
    local from_props candidate
    from_props=$(sed -n 's/^sdk\.dir=//p' "$REPO_ROOT/local.properties" 2>/dev/null | tail -1)
    for candidate in "$from_props" "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" \
                     "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        if [[ -n "$candidate" && -d "$candidate" ]]; then printf '%s' "$candidate"; return; fi
    done
}

SDK="$(sdk_dir)"
[[ -n "$SDK" ]] || die "Android SDK not found. Set sdk.dir in local.properties or \$ANDROID_HOME."

# Newest build-tools wins; that is where aapt2 and apksigner live.
BUILD_TOOLS="$(find "$SDK/build-tools" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -1)"
[[ -n "$BUILD_TOOLS" ]] || die "No build-tools installed under $SDK/build-tools."
AAPT2="$BUILD_TOOLS/aapt2"
APKSIGNER="$BUILD_TOOLS/apksigner"
[[ -x "$AAPT2" ]] || die "aapt2 missing from $BUILD_TOOLS."
[[ -x "$APKSIGNER" ]] || die "apksigner missing from $BUILD_TOOLS."

# ---------------------------------------------------------------- build

cd "$REPO_ROOT"

SIGNED_RELEASE=false
[[ -s keystore.properties ]] && SIGNED_RELEASE=true

GIT_DESC="not a git repo"
GIT_DIRTY=false
if git rev-parse HEAD >/dev/null 2>&1; then
    GIT_DESC="$(git rev-parse --short HEAD) on $(git rev-parse --abbrev-ref HEAD)"
    [[ -n "$(git status --porcelain)" ]] && { GIT_DIRTY=true; GIT_DESC="$GIT_DESC (working tree dirty)"; }
fi

step "Kilkari build"
info "repo         $REPO_ROOT"
info "commit       $GIT_DESC"
info "build-tools  $(basename "$BUILD_TOOLS")"
if $BUILD_RELEASE; then
    if $SIGNED_RELEASE; then
        info "signing      keystore.properties found — release will be signed"
    else
        info "signing      no keystore.properties — release will be UNSIGNED"
    fi
fi

GRADLE_TASKS=()
$DO_CLEAN && GRADLE_TASKS+=(clean)
$BUILD_RELEASE && GRADLE_TASKS+=(:app:bundleRelease :app:assembleRelease)
$BUILD_DEBUG && GRADLE_TASKS+=(:app:assembleDebug)

step "Gradle: ${GRADLE_TASKS[*]}"
./gradlew --console=plain "${GRADLE_TASKS[@]}"

# ---------------------------------------------------------------- collect

# Prints the first of its arguments that exists, or nothing. Always succeeds, so callers
# get to report a useful error instead of `set -e` killing the script mid-assignment.
first_match() {
    local f
    for f in "$@"; do
        if [[ -f "$f" ]]; then printf '%s' "$f"; return 0; fi
    done
    return 0
}

# Pull one field off aapt2's `package:` line. The fields are matched whole rather than by
# substring: a loose pattern also matches versionName, platformBuildVersionName and
# compileSdkVersionCodename when asked for `name`.
badging_field() {
    local line
    # grep -m1 stops early and SIGPIPEs aapt2, which pipefail would otherwise treat as a
    # build failure; the read is deliberately allowed to come back empty instead.
    line="$("$AAPT2" dump badging "$1" 2>/dev/null | grep -m1 '^package:' || true)"
    [[ -n "$line" ]] || return 0
    printf '%s\n' "${line#package: }" | tr ' ' '\n' | sed -n "s/^$2='\(.*\)'\$/\1/p"
}

# Read the version out of the built APK rather than the build file, so the names on disk
# can never disagree with what is actually inside the package.
VERSION_SOURCE="$(first_match \
    app/build/outputs/apk/release/app-release.apk \
    app/build/outputs/apk/release/app-release-unsigned.apk \
    app/build/outputs/apk/debug/app-debug.apk)"
[[ -n "$VERSION_SOURCE" ]] || die "No APK produced — nothing to collect."

VERSION_NAME="$(badging_field "$VERSION_SOURCE" versionName)"
VERSION_CODE="$(badging_field "$VERSION_SOURCE" versionCode)"
[[ -n "$VERSION_NAME" ]] || die "Could not read versionName from $VERSION_SOURCE."
# The debug build carries the .debug suffix; report the real package either way.
PKG_BASE="$(badging_field "$VERSION_SOURCE" name)"

mkdir -p "$OUT_DIR"
# Clear out only the artifacts this script produces, so a rebuild never leaves a mix of
# versions sitting side by side. Anything else in the directory is left untouched.
rm -f "$OUT_DIR/$APP_NAME"-*.apk "$OUT_DIR/$APP_NAME"-*.aab "$OUT_DIR/$APP_NAME"-*.apk.idsig \
      "$OUT_DIR/$APP_NAME"-*-mapping.txt "$OUT_DIR/BUILD-INFO.md"

ART_PATHS=()
ART_DESCS=()
collect() {
    cp "$1" "$OUT_DIR/$2"
    ART_PATHS+=("$OUT_DIR/$2")
    ART_DESCS+=("$3")
}

step "Collecting into ${OUT_DIR/#$REPO_ROOT\//}/"

if $BUILD_RELEASE; then
    SUFFIX=""
    $SIGNED_RELEASE || SUFFIX="-unsigned"

    AAB="$(first_match app/build/outputs/bundle/release/app-release.aab)"
    [[ -n "$AAB" ]] || die "Release bundle missing."
    collect "$AAB" "$APP_NAME-$VERSION_NAME$SUFFIX.aab" \
        "App Bundle — the format Play requires for new apps"

    RAPK="$(first_match app/build/outputs/apk/release/app-release.apk \
                        app/build/outputs/apk/release/app-release-unsigned.apk)"
    [[ -n "$RAPK" ]] || die "Release APK missing."
    collect "$RAPK" "$APP_NAME-$VERSION_NAME$SUFFIX.apk" \
        "Universal APK for sideloading"

    # R8 renames everything, so without this file a Play crash report is unreadable.
    MAPPING="$(first_match app/build/outputs/mapping/release/mapping.txt)"
    if [[ -n "$MAPPING" ]]; then
        collect "$MAPPING" "$APP_NAME-$VERSION_NAME-mapping.txt" \
            "R8 mapping — upload with the bundle to deobfuscate crash reports"
    fi

    # An unsigned APK cannot be installed, which would leave the minified build untestable.
    # Sign a throwaway copy with the SDK's public debug key purely to make that possible.
    if ! $SIGNED_RELEASE; then
        DEBUG_KEYSTORE="$HOME/.android/debug.keystore"
        if [[ -f "$DEBUG_KEYSTORE" ]]; then
            TESTING="$OUT_DIR/$APP_NAME-$VERSION_NAME-DEBUGSIGNED-testing-only.apk"
            cp "$RAPK" "$TESTING"
            "$APKSIGNER" sign --ks "$DEBUG_KEYSTORE" --ks-pass pass:android \
                --key-pass pass:android --ks-key-alias androiddebugkey \
                --v4-signing-enabled false "$TESTING"
            ART_PATHS+=("$TESTING")
            ART_DESCS+=("Release build signed with the public debug key — installable for testing, NEVER publish")
        else
            warn "No $DEBUG_KEYSTORE, so the unsigned release APK cannot be made installable."
        fi
    fi
fi

if $BUILD_DEBUG; then
    DAPK="$(first_match app/build/outputs/apk/debug/app-debug.apk)"
    [[ -n "$DAPK" ]] || die "Debug APK missing."
    collect "$DAPK" "$APP_NAME-$VERSION_NAME-debug.apk" \
        "Debug build (applicationId com.kilkari.debug — installs alongside the release)"
fi

[[ ${#ART_PATHS[@]} -gt 0 ]] || die "Nothing was collected."

# ---------------------------------------------------------------- verify

# Every APK is opened and checked rather than assumed good: a build can succeed and still
# produce something that will not install.
step "Verifying"
i=0
while [[ $i -lt ${#ART_PATHS[@]} ]]; do
    path="${ART_PATHS[$i]}"
    name="$(basename "$path")"
    case "$path" in
        *.apk)
            pkg="$(badging_field "$path" name)"
            vn="$(badging_field "$path" versionName)"
            vc="$(badging_field "$path" versionCode)"
            [[ -n "$pkg" ]] || die "$name is not a readable APK."
            if "$APKSIGNER" verify "$path" >/dev/null 2>&1; then sig="signed"; else sig="UNSIGNED"; fi
            info "$name — $pkg $vn ($vc), $sig"
            ;;
        *.aab)
            info "$name — bundle, $(filesize "$path")"
            ;;
        *)
            info "$name — $(filesize "$path")"
            ;;
    esac
    i=$((i + 1))
done

# ---------------------------------------------------------------- record

BUILD_INFO="$OUT_DIR/BUILD-INFO.md"
{
    echo "# Kilkari $VERSION_NAME (versionCode $VERSION_CODE) — $PKG_BASE"
    echo
    echo "Generated by \`tools/build-artifacts.sh\` on $(date '+%Y-%m-%d %H:%M %Z'), from $GIT_DESC."
    if $GIT_DIRTY; then
        echo
        echo "> **The working tree was dirty at build time**, so these artifacts do not"
        echo "> correspond to any commit. Rebuild from a clean tree before publishing."
    fi
    echo
    echo "| File | Size | SHA-256 | What it is |"
    echo "| --- | --- | --- | --- |"
    i=0
    while [[ $i -lt ${#ART_PATHS[@]} ]]; do
        p="${ART_PATHS[$i]}"
        echo "| \`$(basename "$p")\` | $(filesize "$p") | \`$(sha256 "$p" | cut -c1-16)…\` | ${ART_DESCS[$i]} |"
        i=$((i + 1))
    done
    echo
    if $SIGNED_RELEASE; then
        echo "Release artifacts are signed with the key named in \`keystore.properties\`."
    elif $BUILD_RELEASE; then
        echo "Release artifacts are **unsigned**. See \`README.md\` here for how to sign them."
    fi
} > "$BUILD_INFO"

# ---------------------------------------------------------------- summary

step "Done — $APP_NAME $VERSION_NAME (versionCode $VERSION_CODE)"
i=0
while [[ $i -lt ${#ART_PATHS[@]} ]]; do
    p="${ART_PATHS[$i]}"
    printf '    %-54s %9s\n      %s%s%s\n' "$(basename "$p")" "$(filesize "$p")" "$DIM" "${ART_DESCS[$i]}" "$R"
    i=$((i + 1))
done
echo
if $BUILD_RELEASE && ! $SIGNED_RELEASE; then
    warn "Release artifacts are unsigned and cannot be uploaded to Play."
    info "Add keystore.properties (see keystore.properties.example) and re-run to get signed ones."
fi
info "Details written to ${BUILD_INFO/#$REPO_ROOT\//}"
