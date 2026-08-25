#!/usr/bin/env bash
#
# Sync the Flipcash contract protos from flipcash2-protobuf-api at a pinned commit.
#
#   scripts/sync-protos.sh                 # sync at the SHA in flipcash2.lock
#   scripts/sync-protos.sh <sha|ref>       # re-pin to <sha|ref>, then sync
#
# Set FLIPCASH_UPSTREAM_URL to a local path to sync from a mirror instead of the network.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UPSTREAM_URL="${FLIPCASH_UPSTREAM_URL:-git@github.com:code-payments/flipcash2-protobuf-api.git}"
LOCK="$ROOT/flipcash2.lock"
DEST="$ROOT/proto"

# Unlike the OCP contract, flipcash2 already declares
# option java_package = "com.codeinc.flipcash.gen.*" upstream, so this repo has no
# namespace rewrite to do. The check below fails loudly if that ever stops being true.
EXPECTED_PKG='com.codeinc.flipcash.gen.'

requested="${1:-}"
if [ -z "$requested" ]; then
  [ -f "$LOCK" ] || { echo "no flipcash2.lock and no ref given; pass a sha to pin" >&2; exit 1; }
  requested="$(awk '/^commit:/ {print $2}' "$LOCK")"
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "==> cloning $UPSTREAM_URL"
git clone --quiet "$UPSTREAM_URL" "$tmp/upstream"
git -C "$tmp/upstream" checkout --quiet "$requested"
sha="$(git -C "$tmp/upstream" rev-parse HEAD)"
subject="$(git -C "$tmp/upstream" log -1 --format='%s')"
echo "==> pinned at $sha  ($subject)"

[ -d "$tmp/upstream/proto" ] || { echo "upstream has no proto/ directory" >&2; exit 1; }

# Contract protos only. buf.yaml / buf.lock / buf.gen.yaml describe how the *contract*
# repo builds Go; they are not part of what this SDK ships.
rm -rf "$DEST"
mkdir -p "$DEST"
( cd "$tmp/upstream/proto" && find . -name '*.proto' -type f -print0 ) \
  | ( cd "$tmp/upstream/proto" && xargs -0 -I{} sh -c 'mkdir -p "$1/$(dirname "{}")" && cp "{}" "$1/{}"' _ "$DEST" )

# Verify rather than rewrite: if upstream ever drops or changes java_package, the Android
# app's com.codeinc.flipcash.gen.* imports would silently move, so fail here instead.
missing=0
while IFS= read -r f; do
  if ! grep -q "option java_package = \"${EXPECTED_PKG}" "$f"; then
    echo "  no ${EXPECTED_PKG}* java_package in ${f#$DEST/}" >&2
    missing=$((missing + 1))
  fi
done < <(find "$DEST" -name '*.proto' -type f)
if [ "$missing" != 0 ]; then
  echo "ERROR: $missing proto file(s) lack the expected java_package" >&2
  exit 1
fi
echo "==> verified java_package ${EXPECTED_PKG}* on all contract files"

cat > "$LOCK" <<LOCK_EOF
# Pinned upstream contract. Regenerate with scripts/sync-protos.sh <sha>.
upstream: code-payments/flipcash2-protobuf-api
commit: $sha
subject: $subject
LOCK_EOF

echo "==> synced $(find "$DEST" -name '*.proto' | wc -l | tr -d ' ') proto file(s) into proto/"
echo "==> wrote $LOCK"
