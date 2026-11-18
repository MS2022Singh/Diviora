# Project state log
Each milestone records commit, tag, verification and next step. Newest first.

| Date | Milestone / tag | Commit | Build | Unit tests | Restore verify | Notes / next |
|---|---|---|---|---|---|---|
| 2026-11-18 | v0.1.0-mvp | (see tag) | assembleDebug + assembleRelease (R8) PASS | 15/15 PASS, lint 0 errors | see below | Emulator smoke test (Android 14): alarm fired 14:20:00.878 exactly as scheduled → RoutineRunner PLAYED 2 items → media session PLAYING. Next: connect GitHub, physical-device matrix, Phase 5–7 |
| 2026-11-18 | v0.0.1-bootstrap | cd35c31 | n/a | n/a | PASS | Repo skeleton, docs, CI |

## Current status (v0.1.0-mvp)
- Built: local library and import, playback (audio/video/streams, background, notification, PiP), playlists and favourites,
  AutoPlay Routines (multi-item × multi-slot, rules, exact alarms, recovery, watchdog, history, diagnostics).
- Not built yet (roadmap): provider adapters (Jellyfin/Subsonic/cloud/Spotify remote/YouTube embed/handoff),
  radio directory and podcasts, lyrics, EQ and crossfade, widgets and QS tile, Android Auto and Cast, backup/export of routines,
  "resume from last position" routine rule, Hilt and feature-module split, instrumented UI tests.
- Verification gaps: no physical-device run yet. The emulator here has no hardware acceleration, so the OS itself
  ANRs and only the routine-fires-and-plays path was verified on it. GitHub not connected (ADR 0005).
- Restore: `git switch -c recovery/mvp v0.1.0-mvp` · verify: `scripts/verify-restore.sh v0.1.0-mvp`
