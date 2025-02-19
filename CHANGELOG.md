# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)

## [Unreleased]

### Added

### Changed

- `AccessTokenAuthenticationFunction` now allows servers to set `soft_logout`.

### Deprecated

### Removed

### Fixed

- Ignore status code for onLogout callback check.
- Logout and LogoutAll should send empty request body.

### Security

## 4.12.3

### Added

- Possibility to configure a MatrixClientServerApiClient via MatrixClientConfiguration.

### Fixed

- Send access token to endpoints when server wants to.
- Redacted TimelineEvent should not be replaced by edits.
- Fix error enumerating account registration flows.

## 4.12.2

### Fixed

- Fix ConcurrentModificationException on cache removal.

## 4.12.1

### Fixed

- Fixed use authentication with `GetMediaConfigLegacy`.
- Fixed use authentication with `GetPublicRooms`.

## 4.12.0

### Added

- Reset unread marker on sending own message.
- Expose member counts for Room.
- Allow to init Timeline without explicitly cancel load.
- Allow to listen to state changes in Timeline.
- Rollback of cache on failed transactions additionally to database rollback.
- Support for refresh tokens.

### Changed

- Upgrade dependencies.
- Parameter `from` in `RoomsApiClient::getHierarchy` is nullable.
- Parameter `from` in `RoomsApiClient::getRelations*` is nullable.
- Performance improvements in cache: Skip cache, when an entry is not subscribed by anyone.
- Performance improvements in cache: Don't start coroutine for each cache entry and instead invalidate cache in loop.

### Removed

- Removed Realm repository implementation as it is currently not actively maintained and does not work with current
  Kotlin versions.

### Fixed

- Fixed Androidx Room repository implementation.
- Fixed edge-case, where a one time key is published twice and blocks sync processing.
- Fixed race condition in cache, when cache is skipped.
- Timeline::init returns removed elements in TimelineStateChange.

## 4.11.2

### Fixed

- Fix message replacements not redacted.

## 4.11.1

### Fixed

- Fixed bug where own device keys are removed when leaving all rooms.
- Don't fail when setting read marker fails in outbox.
- Fix outbox may filter elements from cache, that are initial null.

## 4.11.0

### Added

- Allow to create temporary files from media.

### Changed

- Use Blob in media-indexeddb.

### Fixed

- Fixed OPFS streams not closed.
- Catch more MediaStore exceptions.

## 4.10.0

### Added

- Log rate limits.
- Allow to drop elements from `Timeline` to support infinite timelines.

### Changed

- Close HttpClient on stop().
- Allow to configure HttpClientEngine. This allows to reuse it, which spares a lot of resources when spawning many
  clients.
- Upgrade to Ktor 3
- Upgrade to Kotlin 2.0.21
- More flexible module definitions.

## 4.9.2

### Added

- Log cache statistics to find possibly memory leaks

### Fixed

- Fixed memory leak in cache due not cancelled coroutines

## 4.9.1

### Fixed

- Fix media config fetching when Matrix server does not support 1.11

## 4.9.0

### Added

- Support Matrix 1.12
- Generic return type for downloads in MediaApiClient

### Changed

- internal: Upgrade dependencies

### Fixed

- Don't send content type when there is no body.
- Fixed blocking outbox.

## 4.8.1

### Fixed

- Deprecated modules never loaded

## 4.8.0

### Added

- Add utils for converting between Input-/OutputStream and ByteArrayFlow
- Add extensions for creating ByteArrayFlow from ByteBuffer
- Check server media config before uploading any media.

### Changed

- Support files larger than 2.1GB
- Outbox API returns a sorted list
- Keep transaction id for redacted messages
- internal: new Docker images
- updated openssl and libolm (prepare Android 15 support)

### Deprecated

- `modules` replaced by `modulesFactory`

### Fixed

- Delete outbox on room forget
- Missing schema version in Realm prevented automatic migrations

## 4.7.3

### Added

- Add `via` parameter on join and knock requests (MSC4156)

### Changed

- Don't decrypt events when searching for one via `getTimelineEvent`
- Reaction aggregation now exposes the entire timeline event for each reaction, not just the user id
- internal: upgrade Dockerfile (use Adoptium Temurin JDK) and install binutils, fakeroot, flatpak and rpm for compose
  multiplatform

### Deprecated

- Deprecate `server_name` query parameter on join and knock requests in favour of `via` (MSC4156)

### Fixed

- Incorrect handling of reaction redactions and reactions

## 4.7.2

### Changed

- internal: Simplify RoomListHandler

### Fixed

- Corrected an issue deserializing message relationships for annotations
- Deadlock in Timeline loading

## 4.7.1

### Fixed

- Remove own userId from heroes in room name
- Fix server versions not received leading to stuck media downloads

## 4.7.0

### Added

- Add AuthenticationApiClient.getSsoUrl
- Support for Matrix 1.11

### Changed

- Upgraded Kotlin to 2.0.10
- Simplify room name calculation
- internal: precompiled gradle plugins
- Update ErrorResponse to contain non-nullable `error` field

### Deprecated

- AbortSendMessage -> cancelSendMessage

### Removed

- Not usable SSO endpoints in AuthenticationApiClient

### Fixed

- Wrong room name calculation when homeserver does not send complete room summary

## 4.6.2

### Added

- Allow message sending to be cancelled while the message is sent

### Fixed

- if already created a recovery olm session recently, skip creating a new one
- fixed typo in url of GetHierarchy

## 4.6.1

### Changed

- smaller transaction scope on gap filling
- optimized cache to update cache value before persisting into database

### Fixed

- don't encode mimeType null

## 4.6.0

### Changed

- cleaned up `MatrixRegex`
- upgrade gradle to 8.9
- close Okio streams by default
- Make `refresh_token` required
  on [/_matrix/client/v3/refresh](https://spec.matrix.org/v1.11/client-server-api/#post_matrixclientv3refresh).
- [/_matrix/client/v3/register/available](https://spec.matrix.org/v1.11/client-server-api/#get_matrixclientv3registeravailable)
  returns the correct json body on 200.
- Allow to have individual timeouts when calling `getTimelineEvent` in parallel. Previously the first one calling
  defined the timeouts.

### Fixed

- parsing and scanning of mentions
- fixed wrong calculation of canLoadBefore and canLoadAfter in Timeline, when room with upgrades is used
- close JavaScript streams correctly

## 4.5.1

### Changed

- move gradle locks to CI

### Fixed

- fix wrong AES counter increase in Browser

## 4.5.0

### Added

- added Kotlin Multiplatform support to client-repositories-room
  (JVM based targets for now, Native will be enabled, when more stable)

### Changed

- revert: don't filter in load members
- update dependencies (including Kotlin 2.0.0)
- updated AndroidX Room (to an alpha version!)

### Deprecated

### Removed

- Removed Realm repository implementation as it is currently not actively maintained and does not work with current
  Kotlin versions.

### Fixed

### Security

## 4.4.0

### Added

- Streaming support of Media in Kotlin Browser target. This means, that for AES-CTR-256 and SHA-256 media is not loaded
  completely into memory anymore and instead processed sequentially.
- added `trixnity-client-media-opfs`, which implements a store with Origin private file system.

### Changed

- don't filter in load members

### Fixed

- fix download on missing file
- never remove own keys from key tracking, when server says to
- fix typo in encrypted file content

## 4.3.12

### Changed

- only decrypt when decryptionTimeout is larger than ZERO
