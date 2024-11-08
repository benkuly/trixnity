# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)

## [Unreleased]

### Added

- Allow to drop elements from `Timeline` to support infinite timelines.

### Changed

- Close HttpClient on stop().
- Allow to configure HttpClientEngine. This allows to reuse it, which spares a lot of resources when spawning many
  clients.
- More flexible module definitions.

### Deprecated

### Removed

### Fixed

### Security

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
