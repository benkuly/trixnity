# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)

## [Unreleased]

### Added

### Changed

- upgrade gradle to 8.9
- close Okio streams by default

### Deprecated

### Removed

### Fixed

- fixed wrong calculation of canLoadBefore and canLoadAfter in Timeline, when room with upgrades is used

### Security

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