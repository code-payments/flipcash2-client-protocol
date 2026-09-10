# Changelog

Contract changes from a consumer's point of view: what appeared, what changed shape, and what breaks
if you upgrade. Field and enum renumbering matters more than its diff size suggests, so it gets
called out explicitly even when nothing else did.

`publish.yml` reads the section matching the version it is publishing and uses it as the GitHub
release notes, so a version with no entry here does not release. Write the entry in the same PR that
syncs the contract, while the diff is still in front of you.

## 0.5.0

Synced to [`flipcash2-protobuf-api@797052dd`](https://github.com/code-payments/flipcash2-protobuf-api/commit/797052dd1070662f97407427665fd48967abfac6),
picking up three upstream changes: [#90](https://github.com/code-payments/flipcash2-protobuf-api/pull/90),
[#91](https://github.com/code-payments/flipcash2-protobuf-api/pull/91) and
[#92](https://github.com/code-payments/flipcash2-protobuf-api/pull/92).

### Added

- `message` on `push.v1.ChatMetadata`, field 3, typed `messaging.v1.Message`. A chat push now carries
  the message it is about, so a client can render or store it without a follow-up fetch.

  It is optional in practice as well as in type — the field comment says "if the push is for a
  message", and a `Message` whose `TextContent` approaches its 4096-character limit will not fit a
  4 KB push payload on either transport. Check `hasMessage` in Swift or `hasMessage()` in Kotlin and
  keep the fetch path as the fallback.

  How to merge one is already specified, on `Message.event_sequence` rather than here: ignore a copy
  whose `event_sequence` is at or below the version already held, otherwise insert or replace. That
  makes a pushed message, a `SendMessage` echo, and an event-stream delivery of the same message
  interchangeable, which is what lets a push be written straight into local storage.

- `action` on `intent.v1.ChatMetadata.PaymentMetadata`, field 2, with a new nested `Action` enum —
  `DEFAULT = 0`, `SEND = 1`, `TIP = 2`. `DEFAULT` means infer from `location`, so a sender that
  leaves it unset keeps the behaviour it has today.

- `event.v1.ChatEvent` and `event.v1.ChatEventBatch`. A `ChatEvent` addresses an `Event` to a chat id
  with up to 1024 `exclude_user_ids`, and a batch holds up to 1024 of them. Forwarding an event to a
  chat's membership no longer requires the caller to expand it to user ids first.

### Changed

- `event.v1.ForwardEventsRequest.user_events` moved into a required `type` oneof alongside the new
  `chat_events`. **This is source-breaking for code that constructs or reads that request.** In Swift
  `userEvents` survives as a computed property but `hasUserEvents` is gone, `type` is a
  `OneOf_Type?`, and an exhaustive switch needs the new `.chatEvents` case; Kotlin gains
  `getTypeCase()`. Neither app calls this RPC — the only matches in either repo are inside a stale
  worktree's vendored generated code — so the break is real but currently unreachable.

- `push.v1.Payload` is now `@unchecked Sendable` backed by a copy-on-write `_StorageClass` in Swift,
  where it was a plain `Sendable` struct with stored properties. Reaching `messaging.v1.Message`
  through `ChatMetadata` put the message over SwiftProtobuf's threshold for indirect storage. Every
  property keeps its name and type, so consuming code compiles unchanged; what changes is that
  `Payload` heap-allocates and that its `Sendable` conformance is asserted rather than checked.

### Deprecated

- `sending_user_id` on `push.v1.ChatMetadata`. Read the sender from `message.sender_id` instead. It
  is deprecated by comment only, with no `[deprecated = true]` option, so neither language emits a
  warning and the server still populates it.

### Unchanged

Nothing was renumbered, and no result enum gained or reordered a case. `push.v1.ChatMetadata` keeps
fields 1 and 2 and appends 3; `PaymentMetadata` keeps `location` on 1 and appends 2; and
`user_events` keeps field number 1 inside its new oneof, so `ForwardEventsRequest` is wire-compatible
and only its generated API moved. No service, RPC or message was removed.

## 0.4.1

No contract change. `flipcash2.lock` points at the same upstream commit as `0.4.0`, and the Swift
sources are untouched — protovalidate-kt generates Kotlin only, so Swift consumers have nothing to
gain from this release.

### Fixed

- `blob.v1.AccessContext` can be validated at all. Its `scope` oneof marks each arm `required`, and
  protovalidate-kt 0.1.1 emitted every arm's required check unguarded:

  ```kotlin
  Validators.checkRequired(scopeCase == ScopeCase.CHAT, "chat")?.let { violations += it }
  Validators.checkRequired(scopeCase == ScopeCase.PROFILE, "profile")?.let { violations += it }
  ```

  Setting `chat` then failed as `profile: value is required`, setting `profile` failed as
  `chat: value is required`, and setting neither failed as both. No `AccessContext` could pass
  client-side validation, which made `GetBlobsRequest.context` unusable: a caller that needed a
  scope had to validate the request before attaching one. 0.1.2 guards each arm's check on that arm
  being the selected one, and reports an unset oneof once, as
  `scope: exactly one field is required in oneof`.

  `AccessContextValidator.kt` is the only one of the 203 generated validators whose output moves —
  no other message in this contract has a `required` oneof arm.

## 0.4.0

Synced to [`flipcash2-protobuf-api@0b56e3cd`](https://github.com/code-payments/flipcash2-protobuf-api/commit/0b56e3cd9a9f380f86a09664f09695a2254d7b0a).

### Added

- `message_edit_window` and `message_delete_window` on `account.v1.UserFlags`, both
  `google.protobuf.Duration`, on fields 17 and 18. Each is the window after a message is created
  during which that message can still be edited or deleted.

  They are message-typed, so they carry explicit presence and an unset value is not a zero duration.
  Check `hasMessageEditWindow` / `hasMessageDeleteWindow` in Swift, or `hasMessageEditWindow()` in
  Kotlin, before reading either one. A server that has not set them leaves them absent, and reading
  the field directly would report a zero-length window rather than no configured window.

### Unchanged

Nothing existing changed. No services, RPCs, messages, or fields were removed or reshaped, no result
enum gained or reordered a case, and nothing was renumbered: `username_min_balance` keeps field 16,
and every field before it keeps its number. The upgrade from `0.3.0` is free for both languages.

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
