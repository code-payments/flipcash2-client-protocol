# Changelog

Contract changes from a consumer's point of view: what appeared, what changed shape, and what breaks
if you upgrade. Field and enum renumbering matters more than its diff size suggests, so it gets
called out explicitly even when nothing else did.

`publish.yml` reads the section matching the version it is publishing and uses it as the GitHub
release notes, so a version with no entry here does not release. Write the entry in the same PR that
syncs the contract, while the diff is still in front of you.

## 0.3.0

No contract change. `flipcash2.lock` points at the same upstream commit as `0.2.0`, and the generated
Kotlin and Swift are unchanged. Swift consumers have nothing to gain from this release.

### Added

- The Kotlin artifact now ships R8 keep rules, at `META-INF/proguard/flipcash2-client-protocol.pro`:

  ```proguard
  -keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
      <fields>;
  }
  ```

  protobuf-javalite ships no keep rules of its own, so until now every Android consumer had to
  write one, and the obvious `-keep class * extends GeneratedMessageLite { *; }` is far wider
  than javalite needs. javalite resolves *fields* reflectively — the schema built from
  `newMessageInfo` looks up `java.lang.reflect.Field` by the generated `<name>_` field — while
  builders and message methods are reached from ordinary call sites, so R8 traces those without
  help. `-keepclassmembers` also does not keep the class, so a message type nothing references is
  still removed entirely.

  On upgrading, an Android consumer can delete its own protobuf keep rules. Dropping the wide
  pair from `code-android-app` cut 25,542 live methods and 568 live classes, and moved its R8
  optimization score from 89.3% to 96.3%.

  The rule is deliberately not scoped to `com.codeinc.flipcash.gen.**`. The well-known types (`Any`, `Timestamp`,
  `Duration`, `Struct`) come from protobuf-javalite itself, and other dependencies ship generated
  messages with no rules of their own, so a package-scoped rule would leave those broken under R8
  full mode. Both contract packages ship identical rule text; R8 collapses them into one entry.

## 0.2.0

Synced to [`flipcash2-protobuf-api@0300d252`](https://github.com/code-payments/flipcash2-protobuf-api/commit/0300d252f35f3524e067aeef22382012c754902a).

### Added

- `SetMinDmChatInitFee` on the `profile.v1.Profile` service, setting the minimum fee another user
  must pay to initialize a DM chat with the caller. `SetMinDmChatInitFeeRequest` takes the new fee as
  a required `common.v1.FiatPaymentAmount` on field 1 and the usual required `common.v1.Auth` on
  field 10. It replaces whatever fee was already set rather than merging into it.

  `SetMinDmChatInitFeeResponse.Result` is a new enum: `OK`, `DENIED`, and `INVALID_AMOUNT`, the last
  covering an unsupported currency or an amount outside the allowed range.

- `min_dm_chat_init_fee` on `profile.v1.UserProfile`, a `common.v1.FiatPaymentAmount` on field 10.
  It is public, so it comes back for any user rather than only the caller, and it is unset when the
  user has not set a fee, in which case the server default applies.

Nothing existing changed. No field number or enum value moved, and `SetMinDmChatInitFeeResponse.Result`
is a new enum rather than a case added to an existing one, so upgrading from `0.1.0` needs no consumer
changes.

## 0.1.0

First release. The Flipcash contract is now generated once here and published for both platforms,
replacing the copies each app vendored and generated for itself.

- Kotlin, on Maven Central as `com.flipcash:flipcash2-client-protocol`, under
  `com.codeinc.flipcash.gen.*`.
- Swift, as the `Flipcash2ClientProtocol` module. The git tag is the SPM release.
