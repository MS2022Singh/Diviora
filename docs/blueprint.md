# DIVIORA Music — Consolidated Product Blueprint (v3, merged and upgraded)

Android audio and video player · Architecture · Open-source research · Scheduler engine · Build, Git and GitHub plan

> This merges Answer 1 and Answer 2 into one document with the duplicates removed, checks it against the stated requirement, and fills the gaps. Section 0 lists what changed. The master build prompt is in a separate file: `DIVIORA_Master_Prompt.md`.

---

## 0. Requirement coverage check (what was missing, and what this version adds)

| # | Requirement (as stated) | Coverage in Ans 1 + Ans 2 | Gap found | Added in v3 |
|---|---|---|---|---|
| R1 | Android audio **and video** player like YouTube Music / Amazon Music / Gaana / Spotify | Covered | Video and Android Auto/Cast were thin | Video module, PiP, subtitles, Cast, Android Auto (MediaLibraryService), widgets |
| R2 | **Upload any music file from the system** | Partly (MediaStore + file picker) | No "Share to / Open with", no PC transfer, no format list, no folder watch | Share-target and Open-with intents, persistent folder grants, auto-rescan, optional Wi-Fi transfer from PC, a list of supported formats |
| R3 | **Access content from any streaming platform** to play or stream | Feasibility was explained, but the user was left with "not possible" | No usable legal routes were listed | 8 legal integration routes (§3): direct URL/HLS/DASH, radio, podcasts, personal servers, cloud, licensed catalogues, official SDK/embedded players, deep-link handoff plus a system media-control bridge |
| R4 | **Schedule** a certain track at a particular time | Covered | Android 12–15 background limits were not addressed | Exact-alarm strategy, FGS-from-alarm exemption, Android 15 boot restriction, full-screen-intent limit, DND/alarm-stream option |
| R5 | Schedule **multiple files for one or multiple times** automatically | Covered in general terms | The data model allowed only one time per schedule | A "Routine → TimeSlots → Queue" model: many items × many times × recurrence (RRULE), plus a conflict planner and calendar preview |
| R6 | "All other features same as other apps" | Mostly | Lyrics, crossfade, ReplayGain, EQ, widgets, backup, Auto, Cast, smart playlists, offline downloads were scattered or missing | One feature matrix (§6) with an MVP/later split |
| R7 | Build + design + architect + "what will be required" | Covered | No concrete versions, permissions matrix, CI or signing | Permissions matrix (§5.6), toolchain (§10), CI/CD + restore (§9) |
| R8 | Search GitHub open-source repos **and use them** | Covered, but licences were partly unverified | `skaftab-in/android-music-player` has **no licence file** (so all rights are reserved), and the GPL impact was not explained | All 26 repos were checked as reachable and their licences read (Nov 2026). A licence strategy was added so GPL code does not force-relicense the app by accident (§7) |
| R9 | (Prompt) CLI-only build + GitHub kept in sync + restorable | In Ans 2 prompt | No CI-from-GitHub verification, no state log, no checkpoint tags, no OS-specific commands | Covered in the master prompt |

Removed from the merge: placeholder third-party images, `utm_source` tracking links, and the duplicated tables and sections that Ans 1 and Ans 2 repeated.

---

## 1. Product vision

**DIVIORA Music** is a native, local-first Android media player. It brings these into one experience:

- Local audio and video playback, with a full library (songs, albums, artists, genres, folders, playlists, favourites, history).
- Import of any media file from the phone, an SD card, USB-OTG, cloud storage, a share sheet, or a PC.
- Streaming from every source that can legally be reached: network streams, radio, podcasts, personal media servers, licensed catalogues, and official provider SDKs or embedded players.
- **AutoPlay Routines.** This is the signature feature. Any number of items (tracks, playlists, albums, videos, streams) can be scheduled at any number of times, once or on a recurring basis, and they play automatically.
- A polished, original Material 3 UI that uses familiar patterns from Spotify, YouTube Music, Amazon Music and Gaana. It copies none of their code, branding or assets.

Design principle: **the local player and scheduler must work 100% offline and with zero provider accounts.** Providers are optional plug-ins.

---

## 2. Feasibility boundary (read before promising features)

"Local playback", "authorised provider playback" and "arbitrary playback from any platform" are three different capabilities, technically and legally.

| Source | What DIVIORA can do | Plays inside DIVIORA's own player? | Can be scheduled? |
|---|---|---|---|
| Phone storage, SD card, USB-OTG, Downloads | Import, index, play | Yes | Yes |
| User-shared or picked files (any supported format) | Play, optionally copy into the app | Yes | Yes |
| Direct stream URLs (MP3/AAC/OGG/FLAC over HTTP(S), HLS, DASH, Icecast/Shoutcast) | Play | Yes | Yes (needs network) |
| Internet radio (Radio Browser directory) | Browse and play | Yes | Yes |
| Podcasts (RSS) | Subscribe, stream, download | Yes | Yes |
| Personal servers: Jellyfin, Subsonic/OpenSubsonic (Navidrome), Plex, DLNA/UPnP, SMB, WebDAV | Browse and stream | Yes | Yes |
| Cloud drives (Google Drive, OneDrive, Dropbox) via their official APIs | Browse, stream, cache | Yes | Yes |
| Licensed or free catalogues (Jamendo, Internet Archive, Free Music Archive) | Search and play within their licence terms | Yes, per licence | Yes, per licence |
| Spotify | Official Android SDK (App Remote): playback happens **inside the Spotify app** and DIVIORA controls it. Web API for metadata. | No (the Spotify app plays it) | Only if the current Spotify Developer Policy allows it. Verify before building. Web API access for new apps is limited to a small allowlisted user count unless Spotify approves extended access |
| YouTube / YouTube Music | Official IFrame player (for example the `android-youtube-player` library) plus the Data API for search | Embedded and visible only. Background or audio-only play is **not** permitted by YouTube's terms | Only as a visible, screen-on embedded player |
| Amazon Music, Gaana, JioSaavn, Apple Music, etc. | Deep-link handoff that opens the item in the provider's app. Official APIs only if access is granted (Amazon's Web API has been a closed beta; Gaana has no public developer API) | No | "Open in provider app" action. Whether it auto-plays depends on the provider |
| Any app that is currently playing | **System media-control bridge.** With the user's notification-access grant, `MediaSessionManager` can play, pause or skip another app's active session | No (that app plays it) | "Resume / pause what is playing" at a time. Best effort |

**Never:** scrape protected services, bypass DRM or authentication, extract subscription or YouTube streams into ExoPlayer, or redistribute copyrighted content. Each provider is an independent adapter with a declared capability matrix, so any one of them can be disabled without affecting the rest of the app.

---

## 3. Legal integration routes for "any streaming platform"

Together these cover the requirement as far as the law and platform terms allow:

1. **Direct URL / playlist import.** Paste or share a URL, or import an `.m3u`, `.m3u8` or `.pls` file. Media3 handles progressive, HLS and DASH streams.
2. **Internet radio** through the Radio Browser API.
3. **Podcasts** through RSS, with an optional PodcastIndex API search.
4. **Personal media servers** (Jellyfin, OpenSubsonic, Plex, DLNA, SMB, WebDAV). The user's own legally owned library can be streamed from anywhere.
5. **Cloud drives** through their official SDKs and OAuth.
6. **Licensed catalogues** (Jamendo and others), with licence checks per track.
7. **Official provider SDKs or embedded players** (Spotify App Remote, YouTube IFrame player).
8. **Deep-link handoff plus the system media-control bridge.** Users of any other service can still schedule "open X in app Y" or "press play on the active session at 07:00".

Unified search fans out to every enabled provider in parallel and groups the results by source. Each result shows a **capability badge** ("Plays here", "Opens in Spotify", "Visible player only") so nothing unsupported is presented as available.

---

## 4. System architecture

### 4.1 Layers

```text
┌──────────────────────── Compose UI (Material 3) ────────────────────────┐
│ Home · Search · Library · Now Playing · Video · Routines · Settings     │
└───────────────▲─────────────────────────────────────────▲───────────────┘
        ViewModels (StateFlow, UDF)                Widgets / QS tile / Auto
┌───────────────┴──────────── Domain (use cases, pure Kotlin) ────────────┐
│ Library · Playback · Playlists · Routines/Recurrence · Providers · Sync │
└──────▲─────────────────▲─────────────────▲───────────────────▲──────────┘
  Playback engine     Scheduler engine    Media library        Provider adapters
  Media3 ExoPlayer    AlarmManager        MediaStore / SAF     provider-api + impls
  MediaLibrarySvc     Receivers, Planner  Tag reader, Room     OkHttp/Retrofit, OAuth
└──────────────── Persistence & platform: Room · DataStore · Keystore · WorkManager · Notifications ┘
```

### 4.2 Technology stack (versions are pinned in `gradle/libs.versions.toml`; use the latest stable at build start)

| Layer | Technology | Purpose |
|---|---|---|
| Language / async | Kotlin, Coroutines, Flow | App and domain logic |
| UI | Jetpack Compose, Material 3, Navigation Compose, Coil (artwork), Glance (widgets) | Interface |
| DI | Hilt | Dependency injection |
| Playback | AndroidX Media3: ExoPlayer, `MediaLibraryService` (a superset of MediaSessionService that adds Android Auto/Wear browsing), media3-ui, media3-cast, HLS/DASH modules | Audio, video, sessions, casting |
| Audio extras | `android.media.audiofx` (Equalizer, BassBoost, LoudnessEnhancer), ReplayGain from tags, Media3 audio processors for crossfade and fades | Effects |
| Local media | MediaStore, Storage Access Framework (persistable URI grants), a tag reader (a TagLib-based or JAudioTagger library, chosen for licence fit) | Discovery and metadata |
| Persistence | Room (with Paging 3 for large libraries), Proto/Preferences DataStore | Library, routines, history, settings |
| Scheduling | AlarmManager (`setAlarmClock`, `setExactAndAllowWhileIdle`), BroadcastReceivers, RRULE engine (for example `dmfs/lib-recur`, Apache-2.0; verify), WorkManager for non-exact jobs | AutoPlay Routines |
| Networking | OkHttp, Retrofit, kotlinx.serialization, AppAuth (OAuth PKCE) | Providers |
| Security | Android Keystore, encrypted token storage, network security config | Tokens and secrets |
| Quality | JUnit5/4, Turbine, MockK, Robolectric, Compose UI tests, Media3 test utils, Macrobenchmark, ktlint/detekt, Android Lint | Testing and static analysis |
| Build / CI | Gradle wrapper, version catalog, convention plugins, GitHub Actions, gitleaks, licence report plugin, Dependabot/Renovate | Reproducible builds |

Target platform: `minSdk 26` (Android 8.0). `targetSdk` and `compileSdk` = the level Google Play currently requires (API 36 at the time of writing; verify at project start). JDK 17 (or whatever the chosen AGP requires).

### 4.3 Module structure (merged from both answers, based on the `nowinandroid` convention-plugin pattern)

```text
app/                          # entry, navigation host, DI graph
build-logic/convention/       # shared Gradle convention plugins
core/model  core/common  core/designsystem  core/ui
core/database  core/datastore  core/network  core/security  core/testing
feature/home  feature/search  feature/library  feature/player  feature/video
feature/playlists  feature/routines  feature/settings  feature/providers  feature/diagnostics
playback/service              # MediaLibraryService, notification, audio focus, wake mode
playback/queue                # queue model, shuffle/repeat, crossfade/fade processors
providers/api                 # MediaProvider contract + capability model
providers/local  providers/url  providers/radio  providers/podcast
providers/jellyfin  providers/subsonic  providers/cloud
providers/spotify-remote  providers/youtube-embed  providers/handoff  providers/session-bridge
scheduler/recurrence          # RRULE expansion, next-occurrence, DST-safe math (pure Kotlin)
scheduler/planner             # conflict detection, upcoming-event calendar
scheduler/alarm               # AlarmManager registration, receivers, boot/time-change recovery
scheduler/execution           # run routine → playback hand-off, execution log, retries
docs/  scripts/  .github/
```

### 4.4 Provider contract

```kotlin
interface MediaProvider {
    val id: ProviderId
    val capabilities: ProviderCapabilities   // SEARCH, METADATA, DIRECT_PLAY, REMOTE_CONTROL,
                                             // EMBED_VISIBLE_ONLY, HANDOFF, OFFLINE, PLAYLISTS,
                                             // LYRICS, AUTH_REQUIRED, SCHEDULABLE
    suspend fun authenticate(): AuthResult
    suspend fun search(query: String, page: Int = 0): List<MediaRef>
    suspend fun metadata(ref: MediaRef): MediaMeta?
    suspend fun resolve(ref: MediaRef): PlaybackRoute? // DirectSource | RemoteControl | Embed | Handoff
}
```

`PlaybackRoute` is the key abstraction. The scheduler and player treat a local file, a Jellyfin stream, a Spotify App Remote command and a deep link uniformly, and the UI shows exactly what will happen.

---

## 5. AutoPlay Routines — scheduler engine

### 5.1 Concepts

- **Routine**: a named container (for example "Morning Bhajans"). It can be enabled or paused.
- **TimeSlot**: *one routine may have many slots.* Each slot has a start time, a recurrence (one-time, daily, weekdays, weekly on selected days, monthly, or a custom RRULE), an optional end date or count, and an optional skip list (holidays).
- **Queue**: ordered items (tracks, files, playlist, album, video, stream URL, provider item). It can be set per routine or overridden per slot (for example 07:00 → playlist A, 21:00 → ambient video).
- **Rules**: start position (beginning or resume), shuffle, repeat; stop condition (end of queue, after N minutes, at a clock time); fade-in/out duration; target volume; output (any, speaker only, or "only if headphones connected"); audio stream (Media, or **Alarm** so it plays even under Do Not Disturb or when media volume is muted); conflict policy (replace, append, interrupt then restore the previous queue, skip); failure policy (retry N times for network, skip unavailable items, fall back to a local backup item); pre-notification ("Starts in 5 min — Skip / Snooze").

This gives the required **many items × many times × recurrence**. It also allows "the same item in many routines".

### 5.2 Data model (Room)

| Entity | Key fields |
|---|---|
| `MediaItem` | id, uri/providerRef, providerId, type (audio/video/stream), title, artist, album, genre, duration, artworkUri, hash, availability, addedAt |
| `Playlist` / `PlaylistEntry` | id, name, artwork, smart-rule (nullable), created/modified · playlistId, mediaId, position |
| `Routine` | id, name, enabled, color/icon, defaultQueueId, rules (embedded JSON), createdAt |
| `TimeSlot` | id, routineId, localTime, zoneId (or "follow device"), rrule, startDate, endDate/count, exclusions, queueOverrideId |
| `RoutineQueueEntry` | queueId, mediaRef, position |
| `ScheduledAlarm` | slotId, occurrenceUtc, requestCode, registeredAt (a mirror of what AlarmManager holds, used for recovery and diagnostics) |
| `RoutineExecution` | id, slotId, occurrenceUtc, firedAt, startedAt, endedAt, result (PLAYED/SKIPPED/FAILED/MISSED), reason, itemsPlayed |
| `PlaybackHistory` | mediaId, lastPosition, lastPlayedAt, playCount, skipCount |
| `ProviderAccount` | providerId, state, capabilities, tokenRef (Keystore alias, never the raw token) |

### 5.3 Lifecycle

1. Save or edit a routine → the **planner** expands occurrences for the next N days and shows them in the calendar preview, flagging any overlap with other routines.
2. For each slot, compute the next occurrence in `java.time` with the slot's `ZoneId`. Daylight-saving gaps and overlaps are resolved explicitly; the default is "next valid instant".
3. Register **one alarm per slot** (the next occurrence only). Prefer `setAlarmClock()`: it shows in the status bar, is exempt from Doze, and is the most reliable option. If exact-alarm permission is denied, fall back to `setExactAndAllowWhileIdle()`, or to inexact timing with a clear warning.
4. `RoutineAlarmReceiver` fires. Because the trigger is an exact alarm, the app is allowed to start a foreground service. It calls `startForegroundService` on the playback service with the slot ID and acquires a short wake lock.
5. The service re-reads the DB (was the routine disabled? already executed? de-duplicated by `slotId+occurrence`), checks media availability and permissions, applies the conflict policy, builds the queue, applies the volume/fade/stream rules, and plays.
6. A stop timer is set for the stop condition. Network items use `WAKE_MODE_NETWORK`.
7. The `RoutineExecution` result is written, the next occurrence is computed, and the next alarm is registered.
8. **Recovery receivers** call `rescheduleAll()` without starting playback. They listen for `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `LOCALE_CHANGED` and `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. Occurrences missed while the phone was off are logged as `MISSED`. An optional per-routine setting plays them if they are less than X minutes late.
9. A daily WorkManager "watchdog" compares `ScheduledAlarm` with the expected next occurrences and repairs any drift.

### 5.4 Android platform rules the scheduler must respect

- **Exact alarms**: `SCHEDULE_EXACT_ALARM` is denied by default on Android 14+ for new installs. Guide the user to the system screen, and re-check on every app open. `USE_EXACT_ALARM` is restricted on Google Play to alarm and calendar apps. Use it only if the Play policy review accepts DIVIORA's scheduling as core functionality.
- **Foreground service**: `foregroundServiceType="mediaPlayback"`, with `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` declared.
- **Android 15+**: a `BOOT_COMPLETED` receiver may not start a `mediaPlayback` foreground service. Boot recovery only re-registers alarms.
- **Video at a scheduled time with the screen off**: `USE_FULL_SCREEN_INTENT` is restricted on Android 14+ and Play. Default behaviour: start the audio track and post a high-priority "Tap to watch" notification. Full-screen launch is used only if the permission is granted.
- **Notifications**: the `POST_NOTIFICATIONS` runtime permission is needed on Android 13+.
- **Battery**: detect OEM battery killers and the standby bucket, and link to the "Don't optimise" settings. Disclose that a force-stop cancels all alarms until the app is opened again.
- **DND**: the "Alarm stream" option uses `AudioAttributes.USAGE_ALARM`. It is clearly labelled and opt-in.

### 5.5 Routine UI

- A routines list with the next run time, an enable switch, and "Run now / Skip next / Duplicate".
- An editor: content picker (multi-select from library, playlists, URLs and providers) → time slots (add many) → recurrence chips with an advanced RRULE builder → rules → preview of the next 10 occurrences → conflict warnings.
- An upcoming calendar (day, week or agenda view), the execution history with reasons, and an export/import of routines as JSON.
- A **Diagnostics** screen: exact-alarm permission, notifications, battery optimisation, standby bucket, last 20 executions, and a "test fire in 1 minute" button.

### 5.6 Permissions matrix

| Permission | Why | When requested |
|---|---|---|
| `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO` (API 33+); `READ_EXTERNAL_STORAGE` (≤ 32) | Library scan | First-run library setup. SAF folder pick works without it |
| `POST_NOTIFICATIONS` (33+) | Playback and routine notifications | First playback or routine creation |
| `SCHEDULE_EXACT_ALARM` | Exact routine start | First routine save |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` | Background playback | Install-time |
| `RECEIVE_BOOT_COMPLETED` | Recovery after reboot | Install-time |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Streams and providers | Install-time |
| Notification-listener access (optional) | System media-control bridge | Only when the user enables that feature |
| `USE_FULL_SCREEN_INTENT` (optional) | Full-screen scheduled video | Only if the user enables it and Play allows it |

---

## 6. Feature matrix (MVP vs later)

| Area | MVP (v1.0) | Later |
|---|---|---|
| Import / "upload" | MediaStore scan, SAF file/folder pick, "Share to DIVIORA" and "Open with" intents, `.m3u/.pls` import, auto-rescan via a content observer, copy-into-app option | Wi-Fi transfer from a PC (local web page), USB-OTG browser, duplicate merge |
| Formats | Whatever Media3 and the device decoders support: MP3, AAC/M4A, OGG/Opus, FLAC, WAV, ALAC (device-dependent), MP4, MKV, WebM, 3GP; HLS/DASH streams | FFmpeg decoder extension for exotic codecs (check licence and size) |
| Library | Songs, albums, artists, genres, folders, favourites, history, search, sort/filter, artwork, tag display | Tag editor, MusicBrainz matching, TheAudioDB artwork, smart playlists |
| Player | Background, notification and lock-screen controls, headset/Bluetooth, queue editing, shuffle/repeat, resume, gapless, speed/pitch, sleep timer, audio focus | Crossfade, ReplayGain, 10-band EQ presets, visualiser, A-B repeat |
| Video | Full-screen, gestures (seek, volume, brightness), PiP, subtitle and audio-track selection, audio-only mode, resume | External subtitle files, casting video |
| Lyrics | Embedded and `.lrc` sidecar lyrics | LRCLIB online synced lyrics (MIT, open API) |
| Routines | Everything in §5 | Location or Bluetooth-connect triggers, Assistant shortcuts |
| Streaming | URL streams, internet radio, podcasts | Jellyfin/Subsonic/Plex/DLNA/SMB/WebDAV, cloud drives, Jamendo, Spotify remote, YouTube embed, deep-link handoff, session bridge |
| Surfaces | Home-screen widget, Quick Settings tile (play/pause, next routine) | Android Auto, Wear OS, Chromecast |
| Offline | Local everything | Authorised offline downloads per provider licence |
| Personalisation | Dark/light/dynamic colour, accent, home sections | Listening stats ("Wrapped"-style, local only) |
| Data | Backup/restore of playlists, routines and settings as JSON; Android Auto Backup rules | Optional account sync (self-hosted or Firebase/Supabase), opt-in |
| Accessibility | TalkBack labels, contrast, large fonts, touch targets | — |

---

## 7. Open-source repositories (all reachable on GitHub; licences read from each repo in Nov 2026; re-verify before reuse)

### Licence strategy (decide in Phase 0)

- If DIVIORA's own code is **Apache-2.0 or MIT** (proprietary or Play-friendly), **do not copy GPL code**. Study the GPL projects for design only.
- If DIVIORA is **GPL-3.0**, GPL code can be reused with attribution, but the whole app must then be distributed under GPL-3.0.
- A repo with **no licence file means all rights are reserved**. Do not copy from it.
- Record every reused file's source, commit hash and licence in `THIRD_PARTY_NOTICES.md`.

| Repository | Licence | Use it for | Caution |
|---|---|---|---|
| [androidx/media](https://github.com/androidx/media) | Apache-2.0 | **Core dependency**: ExoPlayer, MediaLibraryService, UI, Cast, session demos | Pin a stable version |
| [android/uamp](https://github.com/android/uamp) | Apache-2.0 | Official sample of a music service with browse tree and Android Auto | Older architecture; take the patterns |
| [android/nowinandroid](https://github.com/android/nowinandroid) | Apache-2.0 | **Project template**: modularisation, convention plugins, Hilt, Compose, testing, CI | Not a media app |
| [Doarf/app-music](https://github.com/Doarf/app-music) | MIT | Compose + MediaStore + SAF local player | Reusable with attribution |
| [skaftab-in/android-music-player](https://github.com/skaftab-in/android-music-player) | **No licence file** | Reading only: Room, playlists, Media3 service | **Do not copy code** |
| [akilaisadev/meduza](https://github.com/akilaisadev/meduza) | Apache-2.0 (stated in README) | Compose, Room, search, queue | Check its streaming parts separately |
| [OxygenCobalt/Auxio](https://github.com/OxygenCobalt/Auxio) | GPL-3.0 | Excellent local library, tag parsing, Media3 | GPL |
| [AkaneTan/Gramophone](https://github.com/AkaneTan/Gramophone) | GPL-3.0 | Modern Media3 local player, lyrics | GPL |
| [RetroMusicPlayer/RetroMusicPlayer](https://github.com/RetroMusicPlayer/RetroMusicPlayer) | GPL-3.0 | Full-featured UI ideas, EQ, widgets | GPL |
| [FossifyOrg/Music-Player](https://github.com/FossifyOrg/Music-Player) | GPL-3.0 | Simple local player | GPL |
| [anilbeesetti/nextplayer](https://github.com/anilbeesetti/nextplayer) | GPL-3.0 | Video player UX, gestures, subtitles | GPL |
| [Wavora-dev/Wavora](https://github.com/Wavora-dev/Wavora) | GPL-3.0 | Shared-Kotlin architecture, crossfade, lyrics | GPL; unofficial streaming |
| [maxrave-dev/SimpMusic](https://github.com/maxrave-dev/SimpMusic) | GPL-3.0 | Library, search, session UI | GPL; unofficial YouTube Music access |
| [z-huang/InnerTune](https://github.com/z-huang/InnerTune) | GPL-3.0 | Search, lyrics, caching, dynamic themes | GPL; unofficial access |
| [TeamNewPipe/NewPipe](https://github.com/TeamNewPipe/NewPipe) | GPL-3.0 | Service abstraction, queue design | GPL; its maintainers object to Play redistribution of forks; no extraction code in production |
| [AntennaPod/AntennaPod](https://github.com/AntennaPod/AntennaPod) | GPL-3.0 | Podcast feeds, downloads, sleep timer | GPL |
| [jellyfin/jellyfin-android](https://github.com/jellyfin/jellyfin-android) | GPL-2.0 | Personal-server client patterns (use the Jellyfin API docs or SDK for the actual integration) | GPL |
| [yuriykulikov/AlarmClock](https://github.com/yuriykulikov/AlarmClock) | Apache-2.0 | **Scheduler reference**: alarm registration, reboot handling, OEM quirks | Adapt rather than copy wholesale |
| [FossifyOrg/Clock](https://github.com/FossifyOrg/Clock) | GPL-3.0 | Alarm UX, exact-alarm permission flows | GPL |
| [PierfrancescoSoffritti/android-youtube-player](https://github.com/PierfrancescoSoffritti/android-youtube-player) | MIT | **Official YouTube IFrame player** for Android: a compliant embed | Visible playback only, per YouTube's terms |
| [spotify/android-sdk](https://github.com/spotify/android-sdk) | Apache-2.0 | Spotify auth + App Remote | Playback is inside the Spotify app; check the current policy |
| [tranxuanthang/lrclib](https://github.com/tranxuanthang/lrclib) | MIT | Synced lyrics API (LRCLIB) | Respect its rate limits |
| [segler-alex/radiobrowser-api-rust](https://github.com/segler-alex/radiobrowser-api-rust) | — (see repo) | Radio Browser API server/docs | Check each station's terms |
| [metabrainz/musicbrainz-server](https://github.com/metabrainz/musicbrainz-server) | see repo | Metadata matching (use the hosted MusicBrainz API) | Metadata only; 1 req/s and a User-Agent are required |
| [public-apis/public-apis](https://github.com/public-apis/public-apis) | MIT | A directory for discovering further music APIs | Verify each API |
| [cyberboysumanjay/GaanaAPI](https://github.com/cyberboysumanjay/GaanaAPI) | MIT | **Research only**: an unofficial scraper | Not for production; conflicts with Gaana's terms |

Official APIs: [Media3 guide](https://developer.android.com/media/implement/playback-app) · [Spotify Web API](https://developer.spotify.com/documentation/web-api) · [Spotify Developer Policy](https://developer.spotify.com/policy) · [YouTube Data API](https://developers.google.com/youtube/v3) · [YouTube IFrame API](https://developers.google.com/youtube/iframe_api_reference) · [MusicBrainz API](https://musicbrainz.org/doc/MusicBrainz_API) · [Jamendo API](https://developer.jamendo.com/) · [Radio Browser](https://www.radio-browser.info/) · [TheAudioDB](https://www.theaudiodb.com/api_guide.php) · [LRCLIB](https://lrclib.net/docs)

**How they are used:** Media3 is the engine. `nowinandroid` provides the project skeleton. `Doarf/app-music`, `meduza` and `uamp` are permissive sources whose patterns can be reused. `AlarmClock` is the scheduler reference. `android-youtube-player` is the compliant YouTube route. The GPL apps are design study only (unless the app is licensed GPL). **The multi-slot Routine scheduler is new code**, because none of the repos implements it.

---

## 8. UI and navigation

- Bottom navigation: **Home · Search · Library · Routines · Settings**, plus a persistent mini-player (artwork, title, play/pause, progress; tap to expand) and a "Next routine" chip on Home.
- Screens: Home dashboard (recent, favourites, recently added, continue watching, next routines, connected sources) · Search (all sources, capability badges) · Library tabs · Album/Artist/Genre/Folder pages · Playlist editor · Now Playing (art, lyrics, queue, speed, EQ, sleep timer, audio↔video switch) · Video player · Routines list/editor/calendar/history · Sources & Accounts · Permissions & Diagnostics · Settings · Onboarding (permissions explained one at a time).
- Design: original Material 3 design system (`core/designsystem`) in dark, light and dynamic colour; accessible contrast; adaptive layouts for phones, foldables and tablets. **No third-party logos, screenshots or brand assets.**
- States: empty, loading and error states on every screen, with actionable messages such as "File moved — Locate / Remove" or "Exact alarms off — Fix".

---

## 9. Engineering workflow: CLI-only, GitHub-synced, restorable

- **Every action is a command** (create, edit, build, test, install, commit, push, tag, release, restore). Windows uses PowerShell, or `cmd` for simple steps; Linux/macOS use Bash. Android Studio is optional and is never required for any step.
- **GitHub from minute one**: `gh repo create` → `main` (protected) + `develop` → feature branches `feat/*` → PR → squash-merge. Push after every validated commit.
- **Checkpoints**: tag `cp-YYYYMMDD-HHMM` before risky work, tag milestones `v0.N.0-<name>`, and never delete a tag.
- **CI (GitHub Actions)** on every push/PR: wrapper validation → lint/detekt/ktlint → unit tests → `assembleDebug` → upload the APK artifact → gitleaks secret scan → licence report. On a `v*` tag, a release workflow builds a signed AAB/APK (the keystore comes from GitHub Secrets) and attaches it to the GitHub Release.
- **Restore verification**: `scripts/verify-restore.(sh|ps1) <tag>` makes a fresh clone from GitHub into a temporary folder, checks out the tag, and builds and tests it. This proves the state on GitHub is complete and restorable.
- **State log**: `docs/STATE.md` records each milestone's commit hash, tag, CI run, artifact name, open issues and the next step. It lets any agent session resume exactly where the last one stopped.

---

## 10. Development roadmap (each phase ends with green CI + tag + restore check)

| Phase | Scope | Exit / deliverable | Tag |
|---|---|---|---|
| 0 Setup & compliance | Toolchain via CLI, `gh` auth, repo + branches + protection, licence decision, README/LICENSE/NOTICE/THIRD_PARTY_NOTICES/CONTRIBUTING/SECURITY/CHANGELOG, issue/PR templates, provider capability matrix, ADR log | Empty repo with docs and CI skeleton | `v0.0.1-bootstrap` |
| 1 Foundation | Gradle wrapper, version catalog, convention plugins, modules, Hilt, Room, DataStore, theme, navigation, placeholder screens | Installable debug APK; CI green | `v0.1.0-foundation` |
| 2 Local library & import | Permissions, MediaStore scan, SAF folders, share/open-with intents, tags/artwork, search, favourites, history, playlists | Library of 5k+ songs is smooth | `v0.2.0-library` |
| 3 Playback | MediaLibraryService, queue, notification/lock screen, focus, headset/BT, resume, gapless, speed, sleep timer | Plays for 1 h+ with the screen off | `v0.3.0-playback` |
| 4 Routines | Recurrence engine (unit-tested incl. DST), planner, alarms, execution service, recovery receivers, watchdog, diagnostics, UI | Routine test matrix passes on a device | `v0.4.0-routines` |
| 5 Video | Video player, PiP, subtitles/tracks, audio-only switch, scheduled video behaviour | Video tests pass | `v0.5.0-video` |
| 6 Experience | Home, lyrics, EQ, crossfade, widgets, QS tile, backup/restore, accessibility, onboarding | UX review done | `v0.6.0-experience` |
| 7 Streaming (one adapter per PR) | URL/radio/podcast → servers → cloud → Spotify remote → YouTube embed → handoff/session bridge | Each adapter's capability matrix verified | `v0.7.x-<provider>` |
| 8 Hardening & release | Device matrix, Doze/battery/OEM tests, large libraries, malformed media, security/privacy/licence audit, macrobenchmarks, signed AAB, privacy policy, store listing | Release candidate | `v1.0.0` |

---

## 11. What is required

- **Computer**: Windows 10/11, Linux or macOS with 16 GB RAM or more and SSD space. Install JDK 17, Git, GitHub CLI (`gh`), Android SDK command-line tools (`sdkmanager`: platform-tools, a platform, build-tools, optionally an emulator image), and the Gradle wrapper (inside the repo).
- **Devices**: at least one physical Android phone (ideally Android 14 or 15, plus one older device on Android 9–11 and one aggressive-OEM device such as Xiaomi, Oppo or Vivo) with USB debugging. Emulators are not enough for alarms, Doze and Bluetooth.
- **Accounts**: a GitHub account. Optionally a Google Play Console account (one-time fee), a Spotify developer app, a Google Cloud project (YouTube Data API key) and cloud-drive OAuth clients.
- **Secrets**: a release keystore with an offline backup, stored as GitHub Actions secrets (never committed).
- **Content**: test media you have the rights to use (Creative Commons/public domain), including malformed and edge-case files.
- **Docs to publish**: privacy policy (library scanning, tokens, optional sync), third-party notices, provider limitations.

---

## 12. Quality gates (definition of done for every feature)

1. Builds from a clean checkout, both locally and in GitHub Actions.
2. Unit, integration, Compose and relevant instrumented tests pass. The scheduler has a dedicated test matrix: one-time, daily, weekdays, multi-slot, multi-item, conflict, edit/cancel, disabled, reboot, time-zone and DST change, Doze, permission revoked, missing file, offline stream, expired token, force-stop disclosure.
3. Verified on a physical device with `adb` (for example `adb shell dumpsys alarm`, `adb shell dumpsys deviceidle force-idle`, `adb reboot`).
4. Permission and failure states are handled and shown in the UI.
5. No secrets are committed (gitleaks passes), and licences are recorded.
6. Docs are updated, along with `CHANGELOG.md` and `docs/STATE.md`.
7. Committed, pushed, CI green, tagged where it is a milestone, and the restore command has been recorded and tested.

## Final technical decision

Build DIVIORA Music as a **native, local-first Kotlin + Compose + Media3 + Room + AlarmManager** app on the `nowinandroid` modular template. Ship the local player and the multi-slot Routine scheduler first, so they are fully offline-capable. Then add streaming sources one compliant adapter at a time. Do not fork an unofficial streaming client as the foundation.

*Status: architecture, feature specification, repo research and roadmap are complete. No code, screens or APK have been built yet. The build starts when the master prompt is run.*
