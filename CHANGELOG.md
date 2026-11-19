# Changelog
All notable changes to this project are documented here (Keep a Changelog, SemVer).

## [Unreleased]

## [0.5.0] - 2026-11-19
### Changed
- Now Playing: one balanced row (⟲10 · ♥ · add to playlist · ⏰ · speed · sleep · 10⟳), stop moved to the top bar, queue position centred.
- Home quick buttons: "Add link" (was "Add stream"), and the row scrolls on narrow screens.
- YouTube/Spotify/SoundCloud items get their real title, channel and thumbnail (also fixes items saved as "YouTube video").
### Fixed
- Developer unlock: a clear message when the build has no passphrase. Spaces are trimmed and autocorrect is off. The build passes secrets safely.
- Developer documentation moved out of the public repository.

## [0.4.0] - 2026-11-18
### Added
- YouTube, Spotify and SoundCloud links play inside DIVIORA with the official embedded players (on screen only). Routines
  with them show a tap-to-play notification. Other platform links still open their app.
- "Add & play": adding a link plays it immediately in the player.
- Mini player ✕ close. Now Playing: ±10 s, favourite, add to playlist, stop, "n of N" queue position.
- Content sources are developer-only and hidden in release builds.
- Docs: README landing page, INSTALL, USER_GUIDE (incl. phone-brand battery fixes).
### Fixed
- Broken demo links (Big Buck Bunny/Sintel/HLS) replaced with verified ones.
- A broken item no longer stops the queue (auto-skip). Routines are reported PLAYED only after audio actually starts.
- Quick test used online demo items; it now uses local files only.
- "LIVE" was shown for failed items; desktop player bar has ✕ close.

## [0.3.0-desktop] - 2026-11-18
### Added
- Desktop app (Windows .msi, macOS .dmg, Linux .deb): library (files, folders, links, M3U/PLS), player with video,
  AutoPlay Routines on the same recurrence engine as Android (user-chosen date/time, multiple times, repeat rules,
  shuffle/repeat/volume/fade/stop-after/conflict), history, content sources, system tray, notifications, `--selftest`.
- "Schedule…" on every song/video (Android menu, list "Schedule" button, Now Playing ⏰): opens the editor pre-filled,
  straight to choosing your own time.
- `Build installers` GitHub workflow: builds every installer on each push to main and publishes them at Releases → latest.
- Stable sideload signing key so APK updates install over earlier versions. Shared `:core:catalog` module.
- docs/INSTALL.md (non-technical install guide).

## [0.2.0-catalog] - 2026-11-18
### Added
- Developer content server: catalog JSON (v1) over HTTPS, built in via `diviora.catalogUrl`. Synced at start-up and
  every 6 h; items and playlists appear in Library → Online and on Home, and can be scheduled in routines.
- Content sources management and a bundled demo catalog.
- YouTube/Spotify/Amazon Music/Gaana/JioSaavn/Apple Music/SoundCloud… links are detected and handed off to their own
  app instead of failing as broken streams.
- Remote artwork loading (Coil network). Cleartext http allowed for user-added radio streams.
- Database v2 with migration from v1.

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
- `scripts/smoke-test.sh` end-to-end device test and a debug-only `DebugRoutineReceiver` hook (adb shell only).
### Fixed
- Test routine could land in the past on slow devices (now the next whole minute at least 60 s ahead).
- Saving a routine whose times are all in the past is now refused with a clear message.
- Startup work (notification channel, watchdog, alarm re-registration) moved off the main thread.
- Docs: architecture, scheduler behaviour and limits, testing, restore guide, ADRs 0001–0005.

## [0.0.1-bootstrap] - 2026-11-18
### Added
- Repository skeleton, licence (proprietary), notices, docs, ADRs, CI workflow, restore-verification scripts.
