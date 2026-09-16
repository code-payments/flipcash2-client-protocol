# Changelog

Contract changes from a consumer's point of view: what appeared, what changed shape, and what breaks
if you upgrade. Field and enum renumbering matters more than its diff size suggests, so it gets
called out explicitly even when nothing else did.

`publish.yml` reads the section matching the version it is publishing and uses it as the GitHub
release notes, so a version with no entry here does not release. Write the entry in the same PR that
syncs the contract, while the diff is still in front of you.

## 0.8.0

Synced to [`flipcash2-protobuf-api@e1f4116c`](https://github.com/code-payments/flipcash2-protobuf-api/commit/e1f4116c499718401d002cf79ce64229149ee297),
picking up [#102](https://github.com/code-payments/flipcash2-protobuf-api/pull/102).

`StartChat` gains a required idempotency key, carried by a new `chat.v1.IdempotencyKey` message.
Twenty added lines across two files, nothing removed and nothing renumbered — but this is not a free
upgrade, because the new field is required and 0.7.0 has no way to set it.

Group chat is still in flux. `StartChat` itself only arrived one version ago, in 0.7.0, and the
surface is still being iterated on, so expect it to keep moving. The bump to 0.8.0 tracks the pinned
contract advancing, not the feature settling — if you are integrating group chat, plan on taking
further versions rather than pinning this one and walking away.

### Added

- `chat.v1.IdempotencyKey`, a single `bytes value` (field 1) constrained to exactly 16 bytes:
  `min_len` and `max_len` are both 16, so a shorter or longer value fails validation rather than
  being padded or truncated. The key is minted and owned by the client, typically a random UUID, and
  is never exposed as the created chat's identity.

- `idempotency_key` on `chat.v1.StartChatRequest`, field 9,
  `[(validate.rules).message.required = true]`. It sits between the `parameters` oneof (field 1) and
  `auth` (field 10), both of which keep their numbers.

### Upgrading

Every `StartChat` call site has to set the key, and where it is minted decides whether the field does
anything. The server derives the created chat's identity from the caller and the key, so a retry
carrying the same key returns the chat the first attempt created, with result `OK`, instead of
creating a second one. The parameters are not part of that identity: a retry with a different title,
picture or rules still returns the original chat, unchanged. So the key is only worth something if it
survives a retry — mint it where the user's intent to create a chat begins and carry it down through
the retry path. A key generated inside the call, fresh on each attempt, passes validation and buys
nothing: the duplicate chat it was meant to prevent is exactly what you get.

### Unchanged

Nothing was renumbered. `StartChatResponse.Result` keeps all six of its cases in place
(`OK`, `DENIED`, `TITLE_MODERATED`, `PICTURE_BLOB_NOT_ACCEPTED`, `INVALID_RULES`,
`RULES_NOT_SATISFIED`), so a positional `rawValue` mapping over it does not shift. No existing field
changed number or type, and no service, RPC, message or enum case was removed.

## 0.7.0

Synced to [`flipcash2-protobuf-api@89b444bc`](https://github.com/code-payments/flipcash2-protobuf-api/commit/89b444bc4ae0d099dbaa90cc8b1b301398c94990),
picking up [#99](https://github.com/code-payments/flipcash2-protobuf-api/pull/99) and
[#100](https://github.com/code-payments/flipcash2-protobuf-api/pull/100).

One field is gone: `event.v1.ChatUpdate.new_messages`, field 2, now `reserved 2`. It has carried
`[deprecated = true]` since 0.1.0, and `events` has sat beside it as the sequenced replacement for
just as long, so a client already reading `events` is unaffected and one still falling back to
`new_messages` will not compile. Everything else is additive: four RPCs that let a client create a
group chat and move in and out of it, and roster changes delivered on the event stream.

### Removed

- `new_messages` on `event.v1.ChatUpdate`, field 2, previously `messaging.v1.MessageBatch`. The
  number is now `reserved`, so it cannot be reused and the wire format stays unambiguous for old
  clients. **Source-breaking for anyone still reading it.** Read new messages from `events`
  (field 6), which is contiguous, ordered, gap-detectable and catchable up through
  `Messaging.GetDelta` — none of which `new_messages` offered. Delete the fallback rather than
  porting it; there is nothing left for it to fall back to.

### Added

- Four RPCs on `service Chat`: `GetGroupChatFeed`, `StartChat`, `JoinChat` and `LeaveChat`. This is
  what makes `chat/v1/chat_service.proto` import `blob/v1/model.proto` and
  `moderation/v1/model.proto` for the first time, so anything compiling the chat service now needs
  both alongside it.

- `GetGroupChatFeed` reads the caller's group chats, ordered by last activity with the most recent
  first. `GetGroupChatFeedRequest` carries `query_options` (field 1) and required `auth` (field 10);
  `GetGroupChatFeedResponse` carries `result` (field 1, `OK`/`DENIED`/`NOT_FOUND`), up to 100 `chats`
  (field 2, `chat.v1.Metadata`), `paging_token` (field 3) and `has_more` (field 4).

  It has the same read contract as `GetDmChatFeed`, and the same reason for it: chats are ordered by
  a mutable key, so pagination alone cannot give a complete read. Open the event stream and start
  buffering `ChatUpdate` *before* the first call, page until `has_more` is false while echoing back
  the previous response's `paging_token`, then merge the buffered and ongoing updates onto the
  paginated set. `query_options` controls `page_size` only — ordering is not client-selectable, and
  the token is opaque and server-minted, so leave it unset on the first request and never construct
  one.

  One thing the DM feed does not have: a group's membership can change mid-read. Every page is served
  only for groups the caller is still a member of at the time of that page, so a group left between
  pages is simply absent and its removal arrives on the stream. A client treating the paginated set
  as authoritative without reconciling will show a chat it is no longer in.

  Group feed tokens are considerably larger than DM feed tokens, because the server carries the
  snapshot's remaining order inside the token rather than recomputing it per page. See the
  `PagingToken` change below.

- `StartChat` creates a chat. `StartChatRequest` has a required `parameters` oneof whose only arm
  today is `group` (field 1), plus required `auth` (field 10). `GroupChatParameters` carries `title`
  (field 1, 1–64 characters), optional `picture` (field 2, `blob.v1.BlobId` — the original the caller
  uploaded, which must be caller-owned and `READY`) and optional `rules` (field 3, the
  `chat.v1.Rules` added in 0.6.0; unset means no participation requirements, and the caller must
  satisfy whatever it does set).

  `StartChatResponse.result` (field 1) is `OK`, `DENIED`, `TITLE_MODERATED`,
  `PICTURE_BLOB_NOT_ACCEPTED`, `INVALID_RULES` or `RULES_NOT_SATISFIED`. `chat` (field 2) is set only
  on `OK` and carries the server-generated `chat_id` along with any picture renditions the server
  derived, so there is nothing to refetch. `flagged_category` (field 3,
  `moderation.v1.FlaggedCategory`) is the best-fit category that tripped moderation, set only on
  `TITLE_MODERATED` and `NONE` otherwise; it mirrors the Moderation service's vocabulary, so a client
  already rendering moderation categories can reuse that mapping.

- `JoinChat` and `LeaveChat` move the caller in and out of a chat's roster. Both requests take
  required `chat_id` (field 1) and `auth` (field 10). `JoinChatResponse.result` is
  `OK`/`DENIED`/`NOT_FOUND`/`RULES_NOT_SATISFIED`, with `chat` (field 2) set only on `OK`, giving the
  joined chat's metadata as the caller now sees it. `LeaveChatResponse.result` is
  `OK`/`DENIED`/`NOT_FOUND` and carries nothing else.

- `chat.v1.RosterUpdate` and `chat.v1.RosterUpdateBatch`. A `RosterUpdate` is one member joining or
  leaving — a required `kind` oneof of `member_joined` (field 1) or `member_left` (field 2), plus
  required `roster_summary` (field 10) holding the roster after that change. `RosterUpdateBatch`
  wraps 1–100 of them. Updates go to the chat's members, including the affected user's other
  devices, and are best-effort.

  They are convergent, not sequenced: they ride the event stream *outside* the gap-detected event
  log, and are applied by version as described on `RosterSummary` — a greater version than the one
  held means apply, lesser or equal means drop, so delivery order does not matter. A miss is not
  caught up via `GetDelta`; refetch the roster on a version that cannot be reconciled.

  `MemberJoined.member` (field 1) arrives with the profile hydrated, so a cached member list updates
  without a refetch. `MemberJoined.metadata` (field 2) is set **only when the joining member is the
  recipient** — that is the signal to insert the chat into your own list, from that snapshot. Version
  by the enclosing `RosterUpdate.roster_summary`, which is authoritative;
  `metadata.roster_summary` is not compared separately. `MemberLeft.user_id` (field 1) naming the
  recipient means the recipient is out and the chat should come off their list.

- `roster_updates` on `event.v1.ChatUpdate`, field 8, typed `chat.v1.RosterUpdateBatch`. Same
  convergent-overlay handling as `reaction_updates`, and the delivery path for everything above: a
  `MemberLeft` naming the recipient is how a group dropped between pages of a feed read gets
  reconciled.

### Changed

- `common.v1.PagingToken.value` raises its validation `max_len` from 128 to 16384. Same field, same
  number, bytes on the wire either way, and no generated API change — this is a server-side
  validation ceiling only. It is raised because `GetGroupChatFeed` packs the snapshot's remaining
  order into the token. Treat the token as an opaque blob and size any storage holding it for the new
  ceiling rather than the old one.

### Unchanged

Nothing was renumbered, and no existing result enum gained, lost or reordered a case — all four
`Result` enums in this release are on brand-new messages, so nothing positional shifted underneath
anyone. No existing field changed type, and no service, RPC or message other than `new_messages` was
removed. Apart from dropping that one fallback, the upgrade is additive.

## 0.6.0

Synced to [`flipcash2-protobuf-api@35f99814`](https://github.com/code-payments/flipcash2-protobuf-api/commit/35f9981400947921bbe2872be63b0bb77a569e34),
picking up six upstream changes: [#93](https://github.com/code-payments/flipcash2-protobuf-api/pull/93)
through [#98](https://github.com/code-payments/flipcash2-protobuf-api/pull/98).

Three fields were renamed in place. Nothing was renumbered, so the wire format is unchanged and an
old client keeps decoding new messages — but the generated accessors move, and upgrading will not
compile until you rename with them. Details under Changed.

### Changed

- `messaging.v1.EmojiReaction.sequence` and `messaging.v1.ReactionUpdate.sequence` are both now
  `version`. **Source-breaking.** Field numbers (5 and 6), types and semantics are untouched; the
  name was wrong for what the value does. It is a per-aggregate version advanced by one on every
  change, not a position in a sequence, and it was already documented as opaque and ordering-only.
  Swift `.sequence` becomes `.version`; Kotlin `getSequence()`/`setSequence` become
  `getVersion()`/`setVersion`.

  Keep applying reaction updates last-writer-wins by this value per (message, emoji), and per actor
  for `reacted_by_self`. It is still not the chat event sequence and still not gapless.

- `blob.v1.AccessContext.profile` is now `user_profile`, and the `scope` oneof gains a third arm.
  **Source-breaking twice over.** The rename keeps field number 2 and type `common.v1.UserId`, so
  Swift's oneof case `.profile` becomes `.userProfile` and Kotlin's `ScopeCase.PROFILE` becomes
  `ScopeCase.USER_PROFILE`. Separately, an exhaustive `switch` or `when` over `scope` needs the new
  `chat_profile` arm below. Code that only sets one arm sees the rename; code that reads the oneof
  exhaustively sees both.

### Added

- `chat_profile` on `blob.v1.AccessContext`, field 3, typed `common.v1.ChatId` — the third `scope`
  arm. It authorizes reading a chat's public profile picture, and only that: the blob must be a
  rendition of the chat's *current* picture, so renditions of a superseded picture stop resolving
  through it. That expiry is the difference from the `chat` scope, which does not narrow to one blob.

- `picture` on `chat.v1.Metadata`, field 9, typed `blob.v1.Media`. Group chats only, and optional.
  This is what makes `chat/v1/model.proto` import `blob/v1/model.proto` for the first time, so a
  build that compiles the chat package now needs the blob package alongside it.

- `roster_summary` on `chat.v1.Metadata`, field 10, typed `chat.v1.RosterSummary` and marked
  required. `RosterSummary` describes a chat's member list without containing it: `member_count`
  (field 1) is the roster's true size, where `Metadata.members` is only a subset for large group
  chats, and `version` (field 2) advances by one on every membership-record change — a join, a
  leave, and in future anything the chat records about a member, such as a role.

  `version` is opaque. Compare it against the last value you held: different means your cached member
  list may be stale and should be refetched. On a stream, apply the greater value and drop the rest,
  so delivery order stops mattering. There is no delta to fetch against it, only a refetch. It never
  moves for a profile change — profiles are hydrated fresh onto every response carrying a member.

- `rules` on `chat.v1.Metadata`, field 11, typed `chat.v1.Rules`. Group chats only, and unset means
  no participation requirements, so existing chats are unaffected.

  `Rules` splits into two independently optional classes: `listener` (field 1, up to 32) gates
  reading and joining, `speaker` (field 2, up to 32) gates sending. Empty `listener` means anyone can
  read and join; empty `speaker` means any member can send. All rules within a class must be
  satisfied, and speaker rules apply on top of listener rules — a user has to be able to listen
  before they can speak.

  `ListenerRules` and `SpeakerRules` are separate messages with identical `kind` oneofs, each
  requiring one of `minimum_balance` (field 1) or `staff` (field 2). `StaffRequirement` is empty and
  means `UserFlags.is_staff`. `MinimumBalanceRequirement` carries a required fiat `amount` and a
  `mints` list that is currently capped at one entry — empty applies the requirement across all
  mints, and the field is repeated only so more can be allowed later.

### Unchanged

No field or enum was renumbered, and no result enum gained, lost or reordered a case — the three
renames all keep their field numbers, and the new `chat_profile` arm appends at 3. No service, RPC or
message was removed, and no existing message changed the type of an existing field. Every break in
this release is a name your compiler will point at, not a value that silently means something else.

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

- `action` on `intent.v1.ChatMetadata.TipDmPayment`, field 2, with a new nested `Action` enum —
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
fields 1 and 2 and appends 3; `TipDmPayment` keeps `location` on 1 and appends 2; and
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
