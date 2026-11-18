# Changelog
All notable changes to this project are documented here (Keep a Changelog, SemVer).

## [Unreleased]

## [0.1.0-mvp] - 2026-11-18
### Added
- Gradle 9.9 / AGP 9.5 / Kotlin 2.4 multi-module build (`:app`, `:core:model`, `:scheduler:recurrence`), compileSdk 37.2, targetSdk 36, minSdk 26.
- Library: MediaStore scan (audio + video), file and folder import with persistable access, "Share to" / "Open with" import
  (copied into app storage), stream URLs (HTTP/HLS/DASH/Icecast), M3U/PLS import, search, albums, artists, favourites, playlists, history.
- Playback: Media3 MediaSessionService with background playback, media notification, lock-screen, headset and Bluetooth controls,
  audio focus, queue editing, shuffle/repeat, speed, sleep timer, video with picture-in-picture.
- AutoPlay Routines: many items × many time slots (once / every N days / weekdays / chosen days every N weeks / monthly; end date,
  count, exclusions), rules (shuffle, repeat, volume, fade-in, stop-after with fade-out, conflict policy, alarm stream, late grace),
  exact alarms with inexact fallback, recovery receivers, 12 h watchdog, de-duplicated execution history, conflict warnings,
  run now / skip next / duplicate, test routine, reliability diagnostics.
- Docs: architecture, scheduler behaviour and limits, testing, restore guide, ADRs 0001–0005.

## [0.0.1-bootstrap] - 2026-11-18
### Added
- Repository skeleton, licence (proprietary), notices, docs, ADRs, CI workflow, restore-verification scripts.
