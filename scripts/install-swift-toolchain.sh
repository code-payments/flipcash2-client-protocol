#!/usr/bin/env bash
#
# Install the pinned Swift codegen toolchain into .tools/.
#
# scripts/generate-swift.sh puts .tools/bin first on PATH, so local runs and CI use the
# same generators regardless of what is in /opt/homebrew. Versions live in
# scripts/toolchain.env.
#
# Binaries that already report the pinned version are left alone, so re-running is cheap.
# The two Swift plugins have no prebuilt releases and take a couple of minutes to build
# the first time.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BIN="$ROOT/.tools/bin"
INCLUDE="$ROOT/.tools/include"
# shellcheck source=toolchain.env
. "$ROOT/scripts/toolchain.env"

mkdir -p "$BIN"

installed() { # <binary> <expected-version-substring>
  [ -x "$BIN/$1" ] && "$BIN/$1" --version 2>&1 | grep -qF "$2"
}

# ---------------------------------------------------------------- protoc
if installed protoc "$PROTOC_VERSION" && [ -d "$INCLUDE/google/protobuf" ]; then
  echo "protoc $PROTOC_VERSION already installed"
else
  case "$(uname -s)-$(uname -m)" in
    Darwin-arm64)  asset="osx-aarch_64" ;;
    Darwin-x86_64) asset="osx-x86_64" ;;
    Linux-aarch64) asset="linux-aarch_64" ;;
    Linux-x86_64)  asset="linux-x86_64" ;;
    *) echo "no pinned protoc build for $(uname -s)-$(uname -m)" >&2; exit 1 ;;
  esac
  echo "==> protoc $PROTOC_VERSION ($asset)"
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/protoc.zip" \
    "https://github.com/protocolbuffers/protobuf/releases/download/v${PROTOC_VERSION}/protoc-${PROTOC_VERSION}-${asset}.zip"
  unzip -q -o "$tmp/protoc.zip" -d "$tmp/protoc"
  # protoc unpacks read-only, so a reinstall has to remove it first.
  rm -f "$BIN/protoc"
  cp "$tmp/protoc/bin/protoc" "$BIN/protoc"
  chmod +x "$BIN/protoc"
  # The release zip ships the well-known types next to the binary. A brew protoc finds
  # them implicitly; this one has to be told, so keep them and pass -I at generation.
  rm -rf "$INCLUDE"
  cp -R "$tmp/protoc/include" "$INCLUDE"
  rm -rf "$tmp"
fi

# ---------------------------------------------------------------- Swift plugins
# Neither plugin publishes binaries, so both are built from their tags.
clone_at_tag() { # <repo-url> <tag> <dest>
  # swift-protobuf vendors upstream protobuf as a submodule and one of its targets points
  # into it, so SwiftPM refuses to load the manifest without it -- even to build a product
  # that does not use it.
  git clone --quiet --depth 1 --branch "$2" --recurse-submodules --shallow-submodules "$1" "$3"
}

if installed protoc-gen-swift "$SWIFT_PROTOBUF_VERSION"; then
  echo "protoc-gen-swift $SWIFT_PROTOBUF_VERSION already installed"
else
  echo "==> protoc-gen-swift from swift-protobuf $SWIFT_PROTOBUF_VERSION"
  tmp="$(mktemp -d)"
  clone_at_tag https://github.com/apple/swift-protobuf.git "$SWIFT_PROTOBUF_VERSION" "$tmp/src"
  ( cd "$tmp/src" && swift build -c release --product protoc-gen-swift )
  rm -f "$BIN/protoc-gen-swift"
  cp "$tmp/src/.build/release/protoc-gen-swift" "$BIN/protoc-gen-swift"
  chmod +x "$BIN/protoc-gen-swift"
  rm -rf "$tmp"
fi

# The plugin binary reports no version of its own, so a stamp file records what it was
# built from -- both versions, because the transitive one changes the output too.
stamp="$BIN/.grpc-swift-protobuf-version"
want="$GRPC_SWIFT_PROTOBUF_VERSION+grpc-swift-2-$GRPC_SWIFT_VERSION"
if [ -x "$BIN/protoc-gen-grpc-swift-2" ] && [ "$(cat "$stamp" 2>/dev/null)" = "$want" ]; then
  echo "protoc-gen-grpc-swift-2 $want already installed"
else
  echo "==> protoc-gen-grpc-swift-2 from grpc-swift-protobuf $GRPC_SWIFT_PROTOBUF_VERSION"
  tmp="$(mktemp -d)"
  clone_at_tag https://github.com/grpc/grpc-swift-protobuf.git "$GRPC_SWIFT_PROTOBUF_VERSION" "$tmp/src"
  # Pin the transitive dependency the generator actually renders with. Left alone, its
  # `from:` requirement floats to the newest 2.x and silently changes the output.
  perl -0pi -e 's{(url: "https://github\.com/grpc/grpc-swift-2\.git",\s*\n\s*)from: "[^"]*"}{$1exact: "'"$GRPC_SWIFT_VERSION"'"}' \
    "$tmp/src/Package.swift"
  grep -q "exact: \"$GRPC_SWIFT_VERSION\"" "$tmp/src/Package.swift" || {
    echo "could not pin grpc-swift-2 in grpc-swift-protobuf's manifest" >&2; exit 1; }
  ( cd "$tmp/src" && swift build -c release --product protoc-gen-grpc-swift-2 )
  # protoc derives its --grpc-swift-2_* flags from the binary name, so keep the name.
  rm -f "$BIN/protoc-gen-grpc-swift-2"
  cp "$tmp/src/.build/release/protoc-gen-grpc-swift-2" "$BIN/protoc-gen-grpc-swift-2"
  chmod +x "$BIN/protoc-gen-grpc-swift-2"
  echo "$want" > "$stamp"
  rm -rf "$tmp"
fi

echo
echo "toolchain ready in .tools/bin:"
"$BIN/protoc" --version
echo "protoc-gen-swift $("$BIN/protoc-gen-swift" --version 2>&1 | tail -1)"
echo "protoc-gen-grpc-swift-2 $want"
