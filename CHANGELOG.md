# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)

## [Unreleased]

### Added

- Allow message sending to be cancelled while the message is sent

### Changed

### Deprecated

### Removed

### Fixed

- if already created a recovery olm session recently, skip creating a new one
- fixed typo in url of GetHierarchy

### Security

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