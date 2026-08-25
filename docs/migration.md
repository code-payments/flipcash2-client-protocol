# Migration from vendored protos

`code-android-app` and `code-ios-app` each used to vendor their own copy of the Flipcash protos
and generate independently — two copies of the contract, two generator toolchains, and nothing
that made them agree. This package replaced both.

`0.1.0` shipped in August 2026. Android migrated in
[code-android-app#1325](https://github.com/code-payments/code-android-app/pull/1325) and iOS in
[code-ios-app#645](https://github.com/code-payments/code-ios-app/pull/645), which together
deleted the vendored copies; Android's remaining `:definitions:*` modules and its
`scripts/fetch-protos.sh` went in
[code-android-app#1326](https://github.com/code-payments/code-android-app/pull/1326).

## The parity gate, and why it is gone

Before the apps migrated, `scripts/verify-parity.sh` proved this repo was a drop-in replacement
for what they generated: 526 Kotlin/Java files against
`:definitions:flipcash:models:generateDebugProto` and 50 Swift files against
`FlipcashAPI/.../Core/Generated`, both identical, with the Kotlin split matching per generator
(17 grpc, 17 grpckt, 33 java, 257 kotlin, 202 validate-kt). Two Swift filenames differed —
`messaging_v1_messaging_service.{pb,grpc}.swift` against the app's `flipcash_`-prefixed copies,
byte-identical contents. That prefix is explained in [Code generation](generation.md#filenames).

That gate retired with the copies it compared against — an app that no longer generates has no
second output to disagree with.

## What replaced it

Contract drift between the platforms was possible because each vendored its own copy. With one
generation point it is possible only if the two apps sit on different package versions, which is
a visible version number rather than a silent content diff.

What still needs watching is generator drift, which parity never guarded well anyway: the Swift
output moves on a transitive grpc-swift-2 release with no contract change at all.
`scripts/toolchain.env` is the guarantee now — see [Code generation](generation.md).
