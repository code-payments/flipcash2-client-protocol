# flipcash2-client-protocol

Kotlin and Swift client SDKs for the Flipcash services, generated from the contract in
[`flipcash2-protobuf-api`](https://github.com/code-payments/flipcash2-protobuf-api) and
published so the apps consume a versioned dependency instead of vendoring `.proto` files and
running protoc themselves.

Today `code-android-app` and `code-ios-app` each vendor their own copy of these protos and
generate independently. That is two copies of the contract, two generator toolchains, and no
mechanism that makes them agree. This repo is the single generation point.

It is the sibling of [`ocp-client-protocol`](https://github.com/code-payments/ocp-client-protocol).
The two are separate packages because the contracts are independent — flipcash2 does not import
ocp — and because they belong to different orgs once the split lands.

## Status

Pilot. It has never been published to a real registry, and neither app depends on it on a
branch. What is verified is that the generated code is a drop-in replacement for what the apps
produce now:

| Output | Files | Compared against | Result |
|---|---|---|---|
| Kotlin/Java | 526 | `:definitions:flipcash:models:generateDebugProto` | identical |
| Swift | 50 | `FlipcashAPI/.../Core/Generated` | identical, two files renamed |

The Kotlin split matches per generator too: 17 grpc, 17 grpckt, 33 java, 257 kotlin, 202
validate-kt. `scripts/verify-parity.sh` is that check, and it should stay green until both apps
migrate.

The two renamed Swift files are `messaging_v1_messaging_service.{pb,grpc}.swift`; their contents
are byte-identical to the app's `flipcash_`-prefixed copies. See below.

Both artifacts also build: `swift build` compiles the SPM target, and `./gradlew build
publishToMavenLocal` produces a 2574-class JAR under `com.codeinc.flipcash.gen.*`.

The consumer side is proven too. Pointing `:services:flipcash` at the mavenLocal artifact and
dropping `:definitions:flipcash:models` from its classpath builds the app and passes the module's
220 unit tests. That was done with `:services:opencode` on its own artifact at the same time, so
the combined end state builds, not just one half of it.

Compare against a checkout whose vendored protos are current. An app checkout that predates the
username protos generates 521 files, and the five-file gap is staleness, not drift.

## Layout

```
proto/                            contract, synced from upstream at the SHA in flipcash2.lock
proto_deps/validate/              include-path dependency, never generated
Sources/Flipcash2ClientProtocol/  generated Swift, committed (SPM ships source)
build.gradle.kts                  Kotlin generation + publishing
scripts/
  sync-protos.sh                  pull upstream at a pinned SHA, verify namespacing
  generate-swift.sh               regenerate Sources/
  verify-parity.sh                the phase-1 gate
```

Generated Kotlin is not committed. It is a build input to a published JAR, so the
reviewable-diff argument that applies to Swift does not apply here.

## Updating the contract

```bash
scripts/sync-protos.sh <upstream-sha>   # re-pins flipcash2.lock
scripts/generate-swift.sh               # refresh committed Swift
./gradlew build                         # Kotlin regenerates as part of the build
```

## Things worth knowing

- **Upstream already owns the namespace here.** flipcash2 protos declare
  `com.codeinc.flipcash.gen.*` themselves, so unlike the OCP repo there is nothing to rewrite.
  `sync-protos.sh` verifies the `java_package` on every synced file instead and fails the sync
  if one is missing, which is what would silently break the artifact.
- **The `flipcash_` filename prefix is an artifact of module merging.** The iOS app renames
  `messaging_v1_messaging_service.*` in its `Scripts/run` because both protocols generate into
  one `FlipcashAPI` module and the OCP messaging service collides. With a package per protocol
  the collision cannot happen, so the rename is dropped. `verify-parity.sh` replays it to keep
  the comparison honest until the app migrates.
- **`validate/validate.proto` is an include-path dependency only.** It is never generated. The
  iOS build currently generates it and then deletes the resulting
  `validate_validate.pb.swift`; keeping it in `proto_deps/` and off the generation list removes
  the need for that step.
- **`grpc-kotlin` codegen is pinned to 1.4.1**, matching the app. 1.5.0 differs in line wrapping
  only, so the bump is safe but belongs in its own commit.
- **The JVM and Android variants of the protobuf Gradle plugin differ.** The JVM variant
  registers the `java` builtin by default; the Android variant does not, which is why the app
  declares `java` as a plugin and this repo configures the builtin instead.
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

None of these exist yet — the first publish is blocked until someone with org access adds them.

| Secret | What it is |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token, not the account login |
| `MAVEN_CENTRAL_PASSWORD` | the matching token password |
| `MAVEN_SIGNING_KEY` | ASCII-armored private key, `gpg --armor --export-secret-keys` |
| `MAVEN_SIGNING_KEY_ID` | last 8 characters of the key id |
| `MAVEN_SIGNING_KEY_PASSWORD` | the key's passphrase |

The `com.flipcash` namespace also has to be verified in the Central Portal before the first
upload is accepted, which means a DNS TXT record on the matching domain. That is a one-time
human step and it gates everything else here.

The signing path itself is verified: a throwaway key produces `.asc` signatures for all five
artifacts Central requires (jar, sources, javadoc, pom, module), and all five verify.

## Not done yet

A real released version and a committed dependency in either app. Publishing CI exists but has
never run: the Central namespace is unverified and the signing secrets are unset.
