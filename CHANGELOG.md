# Changelog

Contract changes from a consumer's point of view: what appeared, what changed shape, and what breaks
if you upgrade. Field and enum renumbering matters more than its diff size suggests, so it gets
called out explicitly even when nothing else did.

`publish.yml` reads the section matching the version it is publishing and uses it as the GitHub
release notes, so a version with no entry here does not release. Write the entry in the same PR that
syncs the contract, while the diff is still in front of you.

## 0.18.0

Synced to [`flipcash2-protobuf-api@9d44350b`](https://github.com/code-payments/flipcash2-protobuf-api/commit/9d44350b8d4d8eda9d63cff405238bd0e2920409),
picking up [#135](https://github.com/code-payments/flipcash2-protobuf-api/pull/135),
[#136](https://github.com/code-payments/flipcash2-protobuf-api/pull/136),
[#137](https://github.com/code-payments/flipcash2-protobuf-api/pull/137) and
[#138](https://github.com/code-payments/flipcash2-protobuf-api/pull/138). Only `chat.v1` moved;
`blob.v1` changed only in comments.

### Renumbered

- `EditChatResponse.Result`: `COVER_PICTURE_BLOB_NOT_ACCEPTED` is inserted at 5, and
  `DESCRIPTION_MODERATED` moves from 5 to 6. 0.17.0 shipped `DESCRIPTION_MODERATED = 5`, so a
  0.17.0 client reads a cover-picture rejection from a current server as a moderated description,
  and does not recognize 6 at all. Code that maps this enum by raw value, such as iOS's
  `Error*(rawValue:)`, has to be re-checked case by case, not only extended.

No other field or enum moved. `StartChatResponse.Result` gains its new case at the end.

### Renamed

The group picture becomes the profile picture, to tell it apart from the new cover picture. Field
numbers are unchanged, so this is wire-compatible but breaks source in both languages.

- `Metadata.picture` → `profile_picture` (= 9).
- `picture` → `profile_picture` (= 2) on `PublicGroupChatParameters` and
  `PrivateGroupChatParameters`.
- `EditChatRequest.picture` → `profile_picture` (= 3), and its type `Picture` → `ProfilePicture`.
- `MetadataUpdate.picture_changed` → `profile_picture_changed` (= 5), and `PictureChanged` →
  `ProfilePictureChanged` with `new_picture` → `new_profile_picture`.
- `PICTURE_BLOB_NOT_ACCEPTED` → `PROFILE_PICTURE_BLOB_NOT_ACCEPTED` on `StartChatResponse.Result`
  (= 3) and `EditChatResponse.Result` (= 4).

### Added

- Group cover pictures, the banner behind a chat's profile view.
  - `Metadata.cover_picture` (= 17). The feed RPCs may leave it unset even when one is set, so a
    feed result must not clear a cover picture the client already holds; `GetChat` returns it.
  - Set at creation through `cover_picture` on `PublicGroupChatParameters` (= 5) and
    `PrivateGroupChatParameters` (= 4), and changed through `EditChatRequest.cover_picture`
    (= 5, a `CoverPicture { blob_id }` wrapper).
  - Announced as `MetadataUpdate.CoverPictureChanged` (= 7, `new_cover_picture`).
  - `COVER_PICTURE_BLOB_NOT_ACCEPTED` on `StartChatResponse.Result` (= 7) and
    `EditChatResponse.Result` (= 5, see above).
- `RosterUpdate.MembershipChanged` (= 3), an empty case for a roster change the recipient is not
  shown. Apply its `roster_summary` by version and leave the cached member list alone.
- `Chat.SampleChatters`, a public sample of up to 100 members of a public group: its creator, then
  the most recent senders. Auth is optional. Returns `SampledChatter` (`user_profile`,
  `last_sent_at`, `is_creator`) and `has_more`.
- `Chat.SetFeaturedGroups` and `Chat.GetFeaturedGroups`, an ordered list of up to 10 public groups a
  user shows on their profile. `GetFeaturedGroups` looks the user up by `username`, takes optional
  auth, and returns each group as a list view shows it, without `cover_picture` or any viewer or
  messaging state.

### Changed behavior

- `GetRoster` now requires membership. A non-member whom a group's listener rules let read its
  messages used to get the roster and now gets `DENIED`. `SampleChatters` is the public
  alternative.

### Upgrading

Expect compile errors from the renames. Beyond those, an exhaustive `MetadataUpdate` switch needs
`CoverPictureChanged`, an exhaustive `RosterUpdate` switch needs `MembershipChanged`, and anything
that calls `GetRoster` for a group the user has not joined needs to stop.

## 0.17.0

Synced to [`flipcash2-protobuf-api@3bb442d3`](https://github.com/code-payments/flipcash2-protobuf-api/commit/3bb442d359dd8452b4e3eca5f5c1ca463e7f5a4b),
picking up [#134](https://github.com/code-payments/flipcash2-protobuf-api/pull/134). Only `chat.v1`
moved.

### Added

- Group descriptions, up to 160 characters and moderated like the title.
  - `chat.v1.Metadata.description` (= 16).
  - Set at creation through `description` on `PublicGroupChatParameters` (= 4) and
    `PrivateGroupChatParameters` (= 3). Optional; empty sets none.
  - Changed through `EditChatRequest.description` (= 4), a `Description { value }` wrapper: unset
    leaves the description alone, and a set wrapper with an empty `value` clears it.
  - Announced as `MetadataUpdate.DescriptionChanged` (= 6, `new_description`).
- `DESCRIPTION_MODERATED` on `StartChatResponse.Result` (= 6) and `EditChatResponse.Result` (= 5).
  The response's `flagged_category` is set for it, as it is for `TITLE_MODERATED`.

### Upgrading

Nothing existing changed: no renames, and no field or enum renumbering. Both new result cases come
after the existing ones. A client that ignores `description` keeps working, but one with an
exhaustive `MetadataUpdate` switch needs a `DescriptionChanged` case.

## 0.16.0

Synced to [`flipcash2-protobuf-api@ec66e6b1`](https://github.com/code-payments/flipcash2-protobuf-api/commit/ec66e6b12c59d7eae3281341e74fe8853c90a306),
picking up [#132](https://github.com/code-payments/flipcash2-protobuf-api/pull/132) and
[#133](https://github.com/code-payments/flipcash2-protobuf-api/pull/133). `chat.v1`, `event.v1`,
`messaging.v1` and `profile.v1` moved; `blob.v1` changed only in comments.

### Changed

- `StartChatRequest`'s `group` oneof case is now `public_group`, and its message
  `GroupChatParameters` is now `PublicGroupChatParameters`. The field number is still 1, so this is
  wire-compatible, but every call site that sets or reads it stops compiling on upgrade
  (`setGroup`/`group` in Kotlin, `.group` in Swift).

### Added

- Private groups. `StartChatRequest` gains a `private_group` case (= 2) with
  `PrivateGroupChatParameters` (`title`, 1–64 characters; optional `picture`). A private group is
  visible once created, but nothing can happen in it until its creator stores the group's key.
- Seven `Chat` RPCs for the lobby and keys: `EnterLobby`, `LeaveLobby`, `GetLobbyMembers`,
  `AdmitLobbyMember`, `DenyLobbyMember`, `SetKeyEnvelope` and `GetKeyEnvelope`, each with its own
  `Result` enum. A user waits in the lobby until the creator admits or denies them; admitting is
  the only way to hand another user a key envelope, and the admission arrives as an ordinary
  `RosterUpdate.MemberJoined`. A denial is not announced to the denied user.
- `chat.v1.KeyEnvelope` (`scheme`, 24-byte `nonce`, 48-byte `ciphertext`): the group's 32-byte chat
  key, wrapped per member. The first envelope a caller stores for themself stands; a different one
  afterwards returns `ALREADY_SET`. There is no forward secrecy, and a member who leaves still knows
  the chat key.
- `chat.v1.Lobby`, `LobbyMember`, `LobbyUpdate` and `LobbyUpdateBatch`, and
  `event.v1.ChatUpdate.lobby_updates` (= 9) carrying members entering and leaving a lobby.
- `chat.v1.Metadata.is_private` (= 14) and `in_lobby` (= 15). `in_lobby` is per-viewer: there is no
  RPC that lists the lobbies a caller is waiting in, so a client reads it from `GetChat`.
- `messaging.v1.EncryptedContent.Scheme.CHAT_KEY_XCHACHA20POLY1305` (= 2), the scheme private groups
  use. Clients render a scheme they don't recognize as unsupported.
- `ENCRYPTION_REQUIRED` on `SendMessageResponse.Result` (= 3) and `EditMessageResponse.Result`
  (= 6): the chat is a private group and the content is not `EncryptedContent`.
- Profile bios and cover pictures: `UserProfile.bio` (= 12, up to 160 characters) and
  `cover_picture` (= 13), set through the new `Profile.SetBio` and `Profile.SetCoverPicture` RPCs.
  `SetBio` can come back `FAILED_MODERATED` with a `flagged_category`; `SetCoverPicture` reports blob
  state the same way `SetProfilePicture` does.

### Upgrading

The `public_group` rename is the one source break. No field or enum value was renumbered: every new
enum case is appended after the existing ones, so `Error*(rawValue:)` mappings keep their meaning.

## 0.15.0

Synced to [`flipcash2-protobuf-api@993127b5`](https://github.com/code-payments/flipcash2-protobuf-api/commit/993127b50e42046ee2f290f122b05625774a0661),
picking up [#130](https://github.com/code-payments/flipcash2-protobuf-api/pull/130) and
[#131](https://github.com/code-payments/flipcash2-protobuf-api/pull/131). Only `chat.v1` moved.

### Added

- `Chat.GetMentionSuggestions`, a new RPC: a ranked pool of people to suggest after an @ in a group
  chat, most relevant first. Today that's the group's most recent senders, including people who
  have since left. It is not paged and not complete; the server returns up to 200, and the client
  filters the pool locally as the user types. A DM is `DENIED`.
- `GetMentionSuggestionsRequest` (`chat_id`, `auth`) and `GetMentionSuggestionsResponse`
  (`result`: `OK`, `DENIED`, `NOT_FOUND`; repeated `suggestions`).
- `chat.v1.MentionSuggestion`: a `UserProfile` with `user_id` and `username` always set, and
  `last_sent_at`, unset for someone suggested for another reason. It is not a `Member`.

### Upgrading

Nothing existing changed: no renames, and no field or enum renumbering. The server already drops
the caller, users the caller blocked, and users without a username from the pool, so a client
doesn't need its own username filter on these results.

## 0.14.1

Synced to [`flipcash2-protobuf-api@d082b0a3`](https://github.com/code-payments/flipcash2-protobuf-api/commit/d082b0a3dd268f884f75402ccd355b6548612221),
picking up [#129](https://github.com/code-payments/flipcash2-protobuf-api/pull/129). Only `chat.v1`
moved.

### Added

- `chat.v1.CreatorRequirement`, a new empty message and a new `SpeakerRules` rule case
  (`creator = 4`): only the chat's creator, as given by `Metadata.creator`, may speak.

### Upgrading

Nothing existing changed: no renames, and no field or enum renumbering. A client that switches
exhaustively over the `SpeakerRules` rule needs a case for `creator`. Until it has one, a chat with
this rule reaches that client as an unset or unknown rule, so decide what an unrecognized speaker
rule does before the server starts sending it.

## 0.14.0

Synced to [`flipcash2-protobuf-api@df1cb04e`](https://github.com/code-payments/flipcash2-protobuf-api/commit/df1cb04e71fef7ce77a6a537e0a106fde6452a8f),
picking up [#127](https://github.com/code-payments/flipcash2-protobuf-api/pull/127) and
[#128](https://github.com/code-payments/flipcash2-protobuf-api/pull/128). `account.v1` and
`profile.v1` moved; `messaging.v1` changed only in a comment.

One RPC is renamed, which changes its gRPC method path, so this release is not wire-compatible with a
server that still serves the old name. The other renames break source only. No field or enum number
changed.

### Added

- `profile.v1.UserProfile.is_username_auto_assigned` (field 11, `bool`): true when the server
  assigned the current username from the display name, rather than the user choosing it with
  `SetUsername`. Choosing a different username clears it. It is private: set only on the caller's
  own profile, and false for anyone else's and when there is no username.

### Changed

Renamed, with numbers unchanged:

| Before | After | Swift | Kotlin |
|---|---|---|---|
| `profile.v1.TipCardCustomization` | `FlipcardCustomization` | `Flipcash_Profile_V1_TipCardCustomization` → `…FlipcardCustomization` | `TipCardCustomization` → `FlipcardCustomization` |
| `UserProfile.tip_card_customization = 7` | `flipcard_customization = 7` | `tipCardCustomization` → `flipcardCustomization` | `getTipCardCustomization()` → `getFlipcardCustomization()` |
| `account.v1.TipPresets` | `SendPresets` | `Flipcash_Account_V1_TipPresets` → `…SendPresets` | `TipPresets` → `SendPresets` |
| `UserFlags.tip_presets = 15` | `send_presets = 15` | `tipPresets` → `sendPresets` | `getTipPresetsList()` → `getSendPresetsList()` |
| rpc `Profile.UpdateTipCard` | `UpdateFlipcard` | `updateTipCard` → `updateFlipcard` | `updateTipCard` → `updateFlipcard` |
| `UpdateTipCardRequest` / `UpdateTipCardResponse` | `UpdateFlipcardRequest` / `UpdateFlipcardResponse` | same rename | same rename |

`SendPresets.minimum` is now documented as the least a payment that opens a DM may be when the
recipient has set no `min_dm_chat_init_fee`, and the least such a fee may be. `low`, `medium` and
`high` are one-tap amounts. The fields did not change.

### Upgrading

**`UpdateFlipcard` is a wire change.** gRPC calls a method by name, so the path moves from
`/flipcash.profile.v1.Profile/UpdateTipCard` to `/flipcash.profile.v1.Profile/UpdateFlipcard`. A
client on 0.14.0 fails against a server that only serves `UpdateTipCard`, and an older client fails
against a server that only serves `UpdateFlipcard`. The request and response shapes, and the
`Result` enum, are unchanged.

**The other renames are compile errors, not wire changes.** Code that names any of the message or
field symbols above has to be updated before it builds. Proto JSON encodes field names, so anything
serializing `UserProfile` or `UserFlags` as JSON sees `flipcardCustomization` and `sendPresets`.

**`is_username_auto_assigned` is free to ignore.** It defaults to false, so a client that does not
read it behaves as before.

## 0.13.0

Synced to [`flipcash2-protobuf-api@3d6de707`](https://github.com/code-payments/flipcash2-protobuf-api/commit/3d6de707e8b013435b802ea3c37ddf6fff21ba6d),
picking up [#123](https://github.com/code-payments/flipcash2-protobuf-api/pull/123) through
[#126](https://github.com/code-payments/flipcash2-protobuf-api/pull/126). `chat.v1`, `intent.v1`,
`messaging.v1` and `profile.v1` moved.

The binary wire format is unchanged, but tip DMs are now plain DMs, and that rename breaks source in
both languages. Every other change is additive.

### Added

- `messaging.v1.WidgetContent`, a new `Content.type` case (`widget = 8`), for messages that clients
  render as a native widget. Its required `type` oneof has one case, `share_profile = 1`, holding
  the new `ShareProfileWidget` with a required `username` (field 1, `common.v1.Username`). A client
  that does not recognize the widget variant renders the message as unsupported.
- `chat.v1.Never`, a new empty message and a new `SpeakerRules` rule case (`never = 3`): nobody may
  speak.
- `profile.v1.SetDisplayNameResponse.username` (field 3, `common.v1.Username`). The server may now
  auto-assign a username derived from the display name, so this carries the caller's current
  username. It is set only when `result == OK`, and unset if the caller has no username.

### Changed

Renamed, with numbers unchanged:

| Before | After | Swift | Kotlin |
|---|---|---|---|
| `chat.v1.ChatType.TIP_DM = 2` | `DM = 2` | `.tipDm` → `.dm` | `TIP_DM` → `DM` |
| `intent.v1.ChatMetadata.TipDmPayment` | `DmPayment` | `.TipDmPayment` → `.DmPayment` | `TipDmPayment` → `DmPayment` |
| `ChatMetadata.tip_dm_payment = 3` | `dm_payment = 3` | `tipDmPayment` / `.tipDmPayment` → `dmPayment` / `.dmPayment` | `getTipDmPayment()` / `TIP_DM_PAYMENT` → `getDmPayment()` / `DM_PAYMENT` |
| `DmPayment.Location.TIPCARD = 0` | `FLIPCARD = 0` | `.tipcard` → `.flipcard` | `TIPCARD` → `FLIPCARD` |

`DmPayment.Action.DEFAULT` is now documented as meaning `SEND`: the payment shows as sent, not
tipped. The value did not change.

### Upgrading

**The renames are compile errors, not wire changes.** Binary protobuf carries field and enum
numbers, so an old client and a new one still read each other's messages. Code that names any of
the four symbols above has to be updated before it builds. Proto JSON encodes enums by name, so
anything that serializes these enums as JSON sees `DM` and `FLIPCARD` in place of the old names.

**Read `username` back after `SetDisplayName`.** Setting a display name can now change the
caller's username. A client that caches the username should replace it with the response's value
on `OK`.

**`WidgetContent` needs a fallback before the server sends it.** A client that switches
exhaustively over `Content.type` needs a case for `widget`, and an unknown widget variant renders
as unsupported.

### Unchanged

Nothing was renumbered. `Content.widget` and `SpeakerRules.never` are appended to their oneofs, and
`SetDisplayNameResponse.username` takes a free number. No service, RPC or enum case was removed.
Everything else in the diff is comments.

## 0.12.0

Synced to [`flipcash2-protobuf-api@9ebf55fe`](https://github.com/code-payments/flipcash2-protobuf-api/commit/9ebf55fef834c1a47ae995ae22cad28d79080f2c),
picking up [#117](https://github.com/code-payments/flipcash2-protobuf-api/pull/117) through
[#122](https://github.com/code-payments/flipcash2-protobuf-api/pull/122). `blob.v1`, `chat.v1`,
`messaging.v1` and `push.v1` moved.

Most of this release is end-to-end encryption for DMs: an encrypted content type, encrypted blob
uploads for DM media, and a per-chat flag for migrating DMs over. It is additive on the wire, but
one existing Swift accessor is gone and pushes can now arrive without the message, so the upgrade is
not free on iOS. Both are under Upgrading.

### Added

End-to-end encrypted DMs, in `messaging.v1`:

- `EncryptedContent`, a new `Content.type` case (`encrypted = 7`), carrying `scheme` (field 1),
  a 24-byte `nonce` (field 2) and `ciphertext` (field 3, up to 17408 bytes including the 16-byte
  tag). The plaintext is a serialized `Content` holding a `TextContent`, a `MediaContent`, or a
  `ReplyContent` of either. `EncryptedContent.Scheme` has `UNKNOWN = 0` and
  `X25519_XCHACHA20POLY1305 = 1`. The proto comment on `EncryptedContent` specifies the key
  derivation, AAD and blob format in full; follow it rather than this summary.
- `SendMessageResponse.Result.ENCRYPTION_NOT_ALLOWED = 2` and
  `EditMessageResponse.Result.ENCRYPTION_NOT_ALLOWED = 5`, returned when `EncryptedContent` is sent
  to a chat that is not a DM.

Encrypted blobs, in `blob.v1`:

- `InitiateExternalUploadRequest.end_to_end_encrypted_for`, a oneof whose only case is
  `chat = 4` (`common.v1.ChatId`). The caller must be a member of that DM. `mime_type` must be
  `application/octet-stream`, and the server checks only the size.
- `EncryptedBlobMetadata`, a new empty message and a new `BlobMetadata.kind` case
  (`encrypted = 5`), marking a blob uploaded that way.
- `UploadPolicy.encrypted` (field 4) and the new `EncryptedConstraints`, with `max_size_bytes`
  (field 1, the only enforced limit) and advisory `image` bounds (field 2). Unset when the caller
  may not upload encrypted blobs.

Chat metadata, in `chat.v1.Metadata`:

- `creator` (field 13, `common.v1.UserId`), set for group chats only.
- `use_e2ee` (field 100, `bool`), true when clients should send new content in this DM as
  `EncryptedContent`. Always false for group chats. It is transitional and will be deprecated once
  E2EE launches. The Swift accessor is `useE2Ee`.

Push, in `push.v1.ChatMetadata`:

- `message_id` (field 5, `messaging.v1.MessageId`), sent in place of the full message when the
  message would push the payload over the 4KB FCM/APNs limit.

### Changed

- `ChatMetadata.message` (field 3) now sits inside a new `message_ref` oneof alongside
  `message_id`. The field number and type are unchanged, so this is wire-compatible. In Swift the
  generated `hasMessage` and `clearMessage()` are gone; read `messageRef` instead. Kotlin keeps
  `hasMessage()` and gains `getMessageRefCase()`.
- `ChatMetadata.sending_user_id` is no longer deprecated. It is set whether the push carries the
  message or only its ID.
- `Chat.GetChat` documents an unauthenticated read: with `auth` unset, the caller gets the chat's
  public view, and `view_mode` must be `REDACTED`. Any other mode, or a DM, is `DENIED`. No field
  changed.

### Upgrading

**A chat push may no longer carry the message.** This applies to every client, including one that
never upgrades: the server now omits the message from a push for a long message and sends
`message_id` instead, which an older client sees as `message` simply unset. The push's title and
body are still set, so the notification can be shown either way. A client that needs the message,
to decrypt it or to store it for a muted chat, fetches it with `Messaging.GetMessage` using
`message_id` and the chat ID from `Payload.navigation`.

**`EncryptedContent` needs handling before a DM turns on `use_e2ee`.** The server cannot read,
moderate or preview it. A client that cannot decrypt a message, or decrypts a type outside the
allowed three, renders it as unsupported rather than failing.

### Unchanged

Nothing was renumbered. Both new `Result` cases are appended after the last existing case (`DENIED`
and `CONFLICT` respectively), so no positional `rawValue` mapping shifts. `Content.encrypted` and
`BlobMetadata.encrypted` are appended to their oneofs, and every new field takes a free number. No
service, RPC, message or enum case was removed. Everything else in the diff is comments.

## 0.11.0

Synced to [`flipcash2-protobuf-api@4ccbbe43`](https://github.com/code-payments/flipcash2-protobuf-api/commit/4ccbbe43197ec6cc2d7a3f0681a73f799cdcfb46),
picking up [#114](https://github.com/code-payments/flipcash2-protobuf-api/pull/114) through
[#116](https://github.com/code-payments/flipcash2-protobuf-api/pull/116). Only `chat.v1` moved.

Additive throughout: a chat's roster becomes readable page by page, a group chat becomes editable,
and the server starts telling each viewer what it will let them do. Nothing existing changed number,
type or meaning, so the upgrade itself is free. Two of the additions carry client obligations that
are not visible in the generated code; those are under Upgrading.

### Added

Roster paging, on `chat.v1.Chat`:

- `GetRoster(GetRosterRequest) returns (GetRosterResponse)` pages a chat's roster, most recently
  joined first. Page size comes from `common.v1.QueryOptions` and is capped at 100; ordering is
  fixed and not client-selectable. The `common.v1.PagingToken` is opaque, server-generated and bound
  to `chat_id` — it is refused with another chat. `GetRosterResponse.Result` is `OK`, `DENIED`,
  `NOT_FOUND`. A member may read the roster, as may a non-member the group's listener rules admit; a
  viewer who may only preview the chat is `DENIED`.
- Every page carries the chat's `RosterSummary`, so `member_count` and `version` arrive with the
  members rather than needing a separate read.
- Pointers are hydrated for a DM's participants only. A group's members carry none, because group
  pointer advances are never broadcast and a page of them would be stale as soon as it was served.
- `Member.joined_at` (field 4) and `Member.version` (field 5). `joined_at` is when the member most
  recently joined, so a member who left and rejoined carries the rejoin time. `version` is the
  roster version that placed them — equal to `roster_summary.version` on the
  `RosterUpdate.MemberJoined` that announced it, and zero for a member present at the chat's
  creation and for every DM participant. Both are set on a `GetRoster` page and on `MemberJoined`,
  and unset on `Metadata.members`, which carries only the viewer's own entry.

Group chat editing, on `chat.v1.Chat`:

- `EditChat(EditChatRequest) returns (EditChatResponse)` changes a group chat's `title`, `picture`,
  or both. Every field is optional and only the ones set are changed. The edit is atomic, so a
  refusal of any part applies none of it, and a request that sets nothing returns `OK`.
- `EditChatResponse.Result` is `OK`, `DENIED`, `NOT_FOUND`, `TITLE_MODERATED`,
  `PICTURE_BLOB_NOT_ACCEPTED`. `TITLE_MODERATED` carries the tripped `moderation.v1.FlaggedCategory`
  in `flagged_category`; every other result leaves it `NONE`. `OK` carries the post-edit `Metadata`
  in `chat`, including the renditions the server derived.
- A new picture is a blob the caller has already uploaded through BlobStorage: the client uploads
  only the ORIGINAL and passes its `BlobId` once the blob is `READY`.
- `MetadataUpdate.TitleChanged` (oneof case 4) and `MetadataUpdate.PictureChanged` (oneof case 5),
  one per field actually changed, delivered to the chat's members including the editor's other
  devices.

Per-viewer permissions:

- `ViewerState.permissions` (field 2) and the nested `ViewerState.Permissions`, whose only flag so
  far is `can_edit` — whether the viewer may call `EditChat` on this chat. Each flag is named for
  the RPC it gates and defaults to false, so an unset flag always means the action is not permitted.
- `ViewerState` is now documented as always present for a member. It remains absent when the chat
  holds nothing about the viewer.

### Upgrading

Nothing forces a change. Both obligations below arrive only with the features that carry them.

**A `GetRoster` page is not authoritative on its own.** A DM's roster and a small group's are read
whole, at exactly `roster_summary.version`. A large group's is paged from an index that trails
membership writes, so a page may lag its own summary: a member who just joined may be absent, one
who just left may be present. The client cannot see which case it is in, so it must follow the
weaker contract — merge each page against what the event stream has already delivered, per member,
by `Member.version`, the greater winning. A member absent from a fully read roster is gone unless
the client holds their join above that page's `roster_summary.version`, and a join that lands during
the walk arrives only as a `RosterUpdate`.

**`can_edit` is not derivable.** It is computed from the viewer's standing in the chat and the
chat's rules, neither of which the client holds in full. Show the edit affordance if and only if the
flag is set, rather than inferring it from membership or chat kind, and expect it to move: a change
advances `ViewerState.version` and arrives as `MetadataUpdate.ViewerStateChanged`.

### Unchanged

Nothing was renumbered, and nothing existing changed type or meaning. `Member.joined_at` and
`Member.version` are appended after `pointers` (field 3); the two `MetadataUpdate` cases are
appended after `viewer_state_changed` (case 3); `ViewerState.permissions` takes the free field 2
between `settings` (1) and `version` (10), which does not move. Both new `Result` enums belong to
new messages, so no positional `rawValue` mapping over an existing enum shifts. No service, RPC,
message or enum case was removed. Everything else in the diff is comments.

## 0.10.0

Synced to [`flipcash2-protobuf-api@dd5e92db`](https://github.com/code-payments/flipcash2-protobuf-api/commit/dd5e92db6f76700ab6b565f4eff1f9fb25140c9a),
picking up [#103](https://github.com/code-payments/flipcash2-protobuf-api/pull/103) through
[#113](https://github.com/code-payments/flipcash2-protobuf-api/pull/113) — eleven upstream commits,
the largest sync so far.

One field changes type in place, so this upgrade is not free. Everything else is additive: chat
muting, a Reporting service, and a redaction model that lets a non-member preview a group without
being shown what it says.

### Breaking

`messaging.v1.EmojiReaction.reacted_by_self` (`bool`, field 3) is replaced by `self_reactor`
(`Reactor`, field 3). Same field number, different wire type — varint to length-delimited — so this
is not a field that can be read either way. A 0.9.0 client parsing a 0.10.0 response does not see a
missing field, it sees a malformed one.

The replacement carries more than the bit it replaces. `self_reactor` is set exactly when the viewer
currently reacts with that emoji, and it is the same `Reactor` entry the reactor list carries, so a
client can place itself in a partially loaded reactor list by its `version` instead of paging
`GetReactors` to find its own row. Its presence answers "did I react". Its version is not the
watermark for that toggle: an `EmojiReaction` is a snapshot at `version`, every transition of the
viewer's at or below it is already reflected, and live `ReactionUpdate`s for the viewer are applied
against `EmojiReaction.version` like any other actor's.

### Added

Chat muting, on `chat.v1.Chat`:

- `MuteChat(MuteChatRequest) returns (MuteChatResponse)` and
  `UnmuteChat(UnmuteChatRequest) returns (UnmuteChatResponse)`. Both results are `OK`, `DENIED`
  (not a member) and `NOT_FOUND`. Both responses carry `viewer_state` when `OK`, so the caller can
  apply the new state at its version without a refetch.
- `MuteState`, a required `oneof duration` of `google.protobuf.Timestamp until` or an empty
  `Forever`. A timed mute must end in the future or the server rejects it.
- `ViewerState`, holding `Settings.mute` and a `version` that advances by one on every real change.
  It is compared like `RosterSummary.version` — apply the greater, drop the rest — and it is
  private to the viewer, so it never moves the roster's version.
- `Metadata.viewer_state` (field 12), absent when the chat holds nothing about the viewer.
- `MetadataUpdate.ViewerStateChanged` (oneof case 3), which is how a mute made on another device
  arrives.
- `push.v1.ChatMetadata.muted` (field 4).

A Reporting service, in the new `flipcash.reporting.v1` package:

- `Report(ReportRequest) returns (ReportResponse)` over a required target `oneof` of `user_id`,
  `chat_id`, `message` or `blob_id`, plus an optional free-form `description` capped at 8192 bytes.
  A reported message travels with its chat, because `MessageId` is a per-chat sequence number.
- Reports are advisory. Nothing about the client's view changes, no outcome is reported back, and
  reporting the same target twice is a no-op that returns `OK`.

Redacted reads, so a viewer can be shown that a chat exists without being shown what it says:

- `messaging.v1.ViewMode`, with `FULL = 0`, `FULL_OR_REDACTED = 1` and `REDACTED = 2`. It is set on
  every read that returns a `Message` — `GetMessage`, `GetMessages`, `GetDelta`, `GetChat` for
  `last_message`, and a preview stream. `FULL = 0` is the contract that predates redaction, so a
  client that does not set the field behaves exactly as before.
- `messaging.v1.Message.redacted` (field 9), set on a copy that was redacted for the viewer. The
  content keeps its kind and structure but holds placeholders: text of the same script, length and
  line structure; media with its dimensions and blurhash; a reply with a placeholder body. Cash,
  system and deleted content are never redacted.
- `event.v1.StreamEventsRequest.Params.chat_preview`, a stream targeted at one group chat under a
  `ViewMode` for a server-fixed window, ending in `STREAM_EXPIRED`. Group chats only; a DM is
  `DENIED` whatever the viewer's standing.
- `StreamEventsResponse.Error.Code` gains `NOT_FOUND = 2` and `STREAM_EXPIRED = 3`.
- `Reactor.version` (field 3) and `GetReactorsResponse.version` (field 5).

### Changed

`blob.v1.BlobMetadata.download_url` (field 3) is no longer `required`. A redacted message's
renditions carry their intrinsic metadata — for an image, dimensions and blurhash — with no URL.
The blob is not readable by that viewer, so `GetBlobs` would not return one either.

Reactor lists are now ordered by `Reactor.version` descending, newest first. `reacted_ts` is display
only and no longer an ordering key; `GetReactorsRequest.options.order` is ignored and `page_size`
above 100 is clamped to 100.

### Upgrading

Three things need attention beyond the rename.

**Keep an emoji's version watermark after its count reaches 0.** The server retains the version
across an emoji emptying and being re-added, so a re-add always arrives above the removal. A client
that drops the `(message, emoji)` version when it stops rendering the emoji has nothing to reject a
delayed, lower-versioned `ADDED` with, and resurrects an emoji the server has already emptied. Hide
the entry, keep its version. A summary omits emptied emoji entirely, so once the watermark is
forgotten a refresh cannot restore it.

**Muted pushes are still delivered.** The server sends them so the client can store the message, and
flags them with `ChatMetadata.muted`; suppressing the notification is the client's job. Nothing is
sent when a timed mute lapses either, so the client owns that countdown. A mute is cleared
server-side when the caller leaves the chat.

**Redacted and unredacted copies of a chat are not one history.** A redacted copy is not a version of
the message — `event_sequence` still describes the underlying message — so a client that later reads
it unredacted replaces the placeholder because the read was unredacted, not because of a higher
sequence. Key the cache by the `ViewMode` the read was made under and keep the two apart. A client
catching up a redacted view must call `GetDelta` under the same mode it read the history under, and
must not offer to copy, quote or download a redacted message.

### Unchanged

`reacted_by_self` is the only field that changed number or type, and nothing was renumbered. The two
new `StreamEventsResponse.Error.Code` cases are appended after `DENIED` and `INVALID_TIMESTAMP`, so
a positional `rawValue` mapping over that enum does not shift. No service, RPC, message or enum case
was removed.

## 0.9.0

No contract change. Still synced to
[`flipcash2-protobuf-api@e1f4116c`](https://github.com/code-payments/flipcash2-protobuf-api/commit/e1f4116c499718401d002cf79ce64229149ee297).

### Added

- `Flipcash2ContractInfo`, in both Kotlin and Swift, carrying `VERSION` / `version` and
  `PROTO_COMMIT` / `protoCommit` for the upstream commit this package was generated from,
  plus `shortProtoCommit` and `isLocal`. A package built from `sync-protos.sh --local`
  reports `LOCAL` as its commit, so a consumer can tell a local contract build from a
  pinned one at runtime.

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
