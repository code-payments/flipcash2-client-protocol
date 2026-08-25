# flipcash2-client-protocol

Kotlin and Swift client SDKs for the Flipcash services, generated from the contract in
[`flipcash2-protobuf-api`](https://github.com/code-payments/flipcash2-protobuf-api) and
published so the apps consume a versioned dependency instead of vendoring `.proto` files and
running protoc themselves.

`code-android-app` and `code-ios-app` used to each vendor their own copy of these protos and
generate independently — two copies of the contract, two generator toolchains, and no mechanism
that made them agree. This repo is the single generation point that replaced them.

It is the sibling of [`ocp-client-protocol`](https://github.com/code-payments/ocp-client-protocol).
The two are separate packages because the contracts are independent — flipcash2 does not import
ocp — and because they belong to different orgs once the split lands.

## Status

Released. `0.1.0` is on Maven Central and tagged for SPM. Android consumes it in
[code-android-app#1325](https://github.com/code-payments/code-android-app/pull/1325) and iOS in
[code-ios-app#645](https://github.com/code-payments/code-ios-app/pull/645), which together delete
both vendored copies.

Before the apps migrated, `scripts/verify-parity.sh` proved this repo is a drop-in replacement
for what they generated: 526 Kotlin/Java files against
`:definitions:flipcash:models:generateDebugProto` and 50 Swift files against
`FlipcashAPI/.../Core/Generated`, both identical, with the Kotlin split matching per generator
(17 grpc, 17 grpckt, 33 java, 257 kotlin, 202 validate-kt). Two Swift filenames differed —
`messaging_v1_messaging_service.{pb,grpc}.swift` against the app's `flipcash_`-prefixed copies,
byte-identical contents, see below. That gate is retired with the copies it compared against —
an app that no longer generates has no second output to disagree with. What guards the output
now is `scripts/toolchain.env`, since a floating generator moves the Swift without any contract
change.

## Layout

```
proto/                            contract, synced from upstream at the SHA in flipcash2.lock
proto_deps/validate/              include-path dependency, never generated
Sources/Flipcash2ClientProtocol/  generated Swift, committed (SPM ships source)
build.gradle.kts                  Kotlin generation + publishing
scripts/
  sync-protos.sh                  pull upstream at a pinned SHA, verify namespacing
  install-swift-toolchain.sh      pinned generators into .tools/
  toolchain.env                   the pins
  generate-swift.sh               regenerate Sources/
```

Generated Kotlin is not committed. It is a build input to a published JAR, so the
reviewable-diff argument that applies to Swift does not apply here.

## Updating the contract

```bash
scripts/install-swift-toolchain.sh      # once: pinned generators into .tools/
scripts/sync-protos.sh <upstream-sha>   # re-pins flipcash2.lock
scripts/generate-swift.sh               # refresh committed Swift
./gradlew build                         # Kotlin regenerates as part of the build
```

## Things worth knowing

- **Upstream already owns the namespace here.** flipcash2 protos declare
  `com.codeinc.flipcash.gen.*` themselves, so unlike the OCP repo there is nothing to rewrite.
  `sync-protos.sh` verifies the `java_package` on every synced file instead and fails the sync
  if one is missing, which is what would silently break the artifact.
- **The `flipcash_` filename prefix was an artifact of module merging.** The iOS app renamed
  `messaging_v1_messaging_service.*` in its `Scripts/run` because both protocols generated into
  one `FlipcashAPI` module and the OCP messaging service collided. With a package per protocol
  the collision cannot happen, so the rename is gone. The Swift type names
  (`Flipcash_Messaging_V1_*` vs `Ocp_Messaging_V1_*`) never differed.
- **`validate/validate.proto` is an include-path dependency only.** It is never generated. The
  iOS build currently generates it and then deletes the resulting
  `validate_validate.pb.swift`; keeping it in `proto_deps/` and off the generation list removes
  the need for that step.
- **`grpc-kotlin` codegen is pinned to 1.4.1**, matching the app. 1.5.0 differs in line wrapping
  only, so the bump is safe but belongs in its own commit.
- **The JVM and Android variants of the protobuf Gradle plugin differ.** The JVM variant
  registers the `java` builtin by default; the Android variant does not, which is why the app
  declares `java` as a plugin and this repo configures the builtin instead.
- **The Swift generators are pinned, and one of the pins is transitive.** `brew install
  protoc-gen-grpc-swift` was enough to reproduce the committed output in August 2026 and is
  not enough now. The gRPC stub text is rendered by grpc-swift-2's `GRPCCodeGen`, which
  grpc-swift-protobuf pulls in with a floating `from:` requirement — so the output moves
  when *that* releases, with the plugin's own version unchanged. 2.2.1 added `Sendable` to
  the metadata enums and 2.3.0 added `type:` to every `MethodDescriptor`: hundreds of
  changed lines, no contract change. `scripts/toolchain.env` pins all four versions and
  `install-swift-toolchain.sh` builds the plugins against them.
- **Coroutines are an explicit dependency.** The generated grpckt stubs reference
  `kotlinx.coroutines.flow.Flow`; the app gets that from elsewhere in its graph, a standalone
  artifact cannot.

## Releasing

`.github/workflows/publish.yml`, run from the Actions tab with a version like `0.1.0`. One
version covers both languages: the Kotlin artifact goes to Maven Central, and the git tag the
workflow pushes *is* the Swift Package release, because SPM resolves source straight from this
repo.

The workflow refuses to publish a version that is already tagged, builds and signs everything
before it uploads anything, and tags last — so a broken POM or a stale `Sources/` fails while
the version number is still spendable. `dry_run` does everything except upload and tag.

Consuming a release:

```kotlin
implementation("com.flipcash:flipcash2-client-protocol:0.1.0")
```

```swift
.package(url: "https://github.com/code-payments/flipcash2-client-protocol", from: "0.1.0")
```

### Secrets this needs

Set in both repos.

| Secret | What it is |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token, not the account login |
| `MAVEN_CENTRAL_PASSWORD` | the matching token password |
| `MAVEN_SIGNING_KEY` | ASCII-armored private key, `gpg --armor --export-secret-keys` |
| `MAVEN_SIGNING_KEY_ID` | last 8 characters of the key id |
| `MAVEN_SIGNING_KEY_PASSWORD` | the key's passphrase |

**The signing key's public half must be on a keyserver Central queries.** Producing valid
`.asc` files is not enough: Central fetches the public key by fingerprint to check them, and
one it cannot find fails the whole deployment with `Could not find a public key by the key
fingerprint` against every signed file. Upload once, per key:

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys <fingerprint>
```

The `com.flipcash` namespace is verified in the Central Portal. That was the other one-time
human step, and the one that needs a DNS TXT record.

## Not done yet

Both consumer PRs are open, not merged.
