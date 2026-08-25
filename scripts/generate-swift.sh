#!/usr/bin/env bash
#
# Generate the Swift client from proto/ into Sources/Flipcash2ClientProtocol/.
# Output is committed: SPM ships plain source, so the generated code is the artifact.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROTO_DIR="$ROOT/proto"
DEPS_DIR="$ROOT/proto_deps"
WKT_DIR="$ROOT/.tools/include"
OUT="$ROOT/Sources/Flipcash2ClientProtocol"

# Prefer the pinned toolchain. Generated Swift is version-sensitive -- a newer
# protoc-gen-swift rewrites hundreds of lines with no contract change -- so an ambient
# brew install is not a substitute. See scripts/toolchain.env.
export PATH="$ROOT/.tools/bin:$PATH"

for tool in protoc protoc-gen-swift protoc-gen-grpc-swift-2; do
  command -v "$tool" >/dev/null || {
    echo "missing $tool -- run scripts/install-swift-toolchain.sh" >&2; exit 1; }
done

rm -rf "$OUT"
mkdir -p "$OUT"

# validate/validate.proto is an include-path dependency only. It is never generated --
# swift-protobuf would emit a validate_validate.pb.swift that the app then has to delete,
# which is what the iOS Scripts/run hack was working around.
while IFS= read -r f; do
  rel="${f#"$PROTO_DIR"/}"

  protoc -I"$PROTO_DIR" -I"$DEPS_DIR" -I"$WKT_DIR" "$rel" \
    --swift_opt=Visibility=Public \
    --swift_opt=FileNaming=PathToUnderscores \
    --swift_out="$OUT"

  # gRPC stubs only for files that declare a service.
  if grep -q '^service ' "$f"; then
    protoc -I"$PROTO_DIR" -I"$DEPS_DIR" -I"$WKT_DIR" "$rel" \
      --grpc-swift-2_opt=Visibility=Public \
      --grpc-swift-2_opt=Server=false \
      --grpc-swift-2_opt=Client=true \
      --grpc-swift-2_opt=FileNaming=PathToUnderscores \
      --grpc-swift-2_out="$OUT"
  fi
done < <(cd "$PROTO_DIR" && find . -name '*.proto' -type f | sed "s#^\./#$PROTO_DIR/#" | sort)

echo "==> generated $(find "$OUT" -name '*.swift' | wc -l | tr -d ' ') Swift file(s) into Sources/Flipcash2ClientProtocol/"
