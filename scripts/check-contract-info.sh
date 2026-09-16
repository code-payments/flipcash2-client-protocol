#!/usr/bin/env bash
#
# Asserts that the generated contract metadata agrees with flipcash2.lock.
#
# The Swift file is committed and excluded from the `git diff -- Sources` codegen check
# (its version line moves at release time, which that check would read as drift), so this
# is what guards it instead. The Kotlin file is generated into build/ on every build and
# is checked here for the same reason: one script, both languages, run by CI and by hand.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCK="$ROOT/flipcash2.lock"
SWIFT="$ROOT/Sources/Flipcash2ClientProtocol/ContractInfo.swift"
KOTLIN="$ROOT/build/generated/sources/contractinfo/main/kotlin/com/codeinc/flipcash/gen/Flipcash2ContractInfo.kt"

fail() { echo "ERROR: $*" >&2; exit 1; }

expected="$(awk '/^commit:/ {print $2}' "$LOCK")"
[ -n "$expected" ] || fail "$LOCK has no 'commit:' line"

[ -f "$SWIFT" ] || fail "missing $SWIFT -- run scripts/generate-swift.sh"
swift_commit="$(sed -n 's/.*protoCommit = "\([^"]*\)".*/\1/p' "$SWIFT")"
[ "$swift_commit" = "$expected" ] || \
  fail "ContractInfo.swift protoCommit is '$swift_commit', flipcash2.lock says '$expected'"

[ -f "$KOTLIN" ] || fail "missing $KOTLIN -- run ./gradlew generateContractInfo"
kotlin_commit="$(sed -n 's/.*PROTO_COMMIT: String = "\([^"]*\)".*/\1/p' "$KOTLIN")"
[ "$kotlin_commit" = "$expected" ] || \
  fail "Flipcash2ContractInfo.kt PROTO_COMMIT is '$kotlin_commit', flipcash2.lock says '$expected'"

echo "contract metadata agrees with flipcash2.lock: $expected"
