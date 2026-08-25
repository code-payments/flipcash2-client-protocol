#!/usr/bin/env bash
#
# Gate for phase 1: what this repo generates must match what the apps generate today.
# Run it against real checkouts of both apps:
#
#   VERIFY_ANDROID=../code-android-app VERIFY_IOS=../code-ios-app scripts/verify-parity.sh
#
# Either side can be skipped by leaving its variable unset. This script is expected to be
# deleted once both apps consume the published artifacts -- at that point the apps have no
# independent output left to compare against.
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID="${VERIFY_ANDROID:-}"
IOS="${VERIFY_IOS:-}"
status=0

hr() { printf '%s\n' "------------------------------------------------------------"; }

# ---------------------------------------------------------------- Swift
if [ -n "$IOS" ]; then
  hr; echo "Swift: this repo vs $IOS"
  ref="$IOS/FlipcashAPI/Sources/FlipcashAPI/Core/Generated"
  if [ ! -d "$ref" ]; then
    echo "  SKIP: $ref not found"
  else
    "$ROOT/scripts/generate-swift.sh" >/dev/null || { echo "  FAIL: codegen errored"; status=1; }
    # The app prefixes messaging_v1_messaging_service.* with `flipcash_` in its Scripts/run,
    # because both protocols land in one merged FlipcashAPI module and the OCP messaging
    # service would otherwise collide. Separate SPM modules make that unnecessary, so the
    # comparison replays the rename rather than treating it as drift.
    stage="$(mktemp -d)"
    trap 'rm -rf "$stage"' EXIT
    cp -R "$ROOT/Sources/Flipcash2ClientProtocol/." "$stage/"
    for f in "$stage"/messaging_v1_messaging_service.*; do
      [ -e "$f" ] || continue
      mv "$f" "$stage/flipcash_$(basename "$f")"
    done
    if /usr/bin/diff -rq "$stage" "$ref" >/tmp/vp-swift.txt 2>&1; then
      echo "  OK: $(find "$ref" -name '*.swift' | wc -l | tr -d ' ') file(s) identical (modulo the flipcash_ rename)"
    else
      echo "  DRIFT:"; sed 's/^/    /' /tmp/vp-swift.txt; status=1
    fi
  fi
fi

# ---------------------------------------------------------------- Kotlin
if [ -n "$ANDROID" ]; then
  hr; echo "Kotlin: this repo vs $ANDROID"
  ref="$ANDROID/definitions/flipcash/models/build/generated/java/generateDebugProto"
  if [ ! -d "$ref" ]; then
    echo "  SKIP: $ref not found -- build it first with:"
    echo "        (cd $ANDROID && ./gradlew :definitions:flipcash:models:generateDebugProto)"
  else
    "$ROOT/gradlew" -p "$ROOT" generateProto --quiet || { echo "  FAIL: codegen errored"; status=1; }
    mine="$ROOT/build/generated/sources/proto/main"
    if /usr/bin/diff -rq "$mine" "$ref" >/tmp/vp-kt.txt 2>&1; then
      echo "  OK: $(find "$ref" -type f | wc -l | tr -d ' ') file(s) identical"
    else
      echo "  DRIFT:"; sed 's/^/    /' /tmp/vp-kt.txt; status=1
    fi
  fi
fi

if [ -z "$ANDROID" ] && [ -z "$IOS" ]; then
  echo "nothing to compare: set VERIFY_ANDROID and/or VERIFY_IOS" >&2
  exit 2
fi

hr
[ "$status" = 0 ] && echo "PARITY OK" || echo "PARITY FAILED"
exit "$status"
