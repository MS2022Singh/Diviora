# Architecture (as built, v0.1)

```text
:app ─────────────────────────────────────────────────────────────────────────────
  ui/            Compose screens: Home, Library (+Playlists, Import), Now Playing (+video/PiP),
                 Routines, Routine editor, History, Settings/Diagnostics. Navigation Compose.
  playback/      PlaybackService (Media3 MediaSessionService + ExoPlayer), PlayerConnection
                 (MediaController → StateFlow for the UI), RoutineRunner, MediaMapping
  scheduler/     AlarmScheduler (AlarmManager), RoutineAlarmReceiver, RecoveryReceiver,
                 WatchdogWorker (WorkManager), ExecutionLog, RoutineNotifications
  data/db        Room: media, playlist, playlist_entry, history, routine, routine_item,
                 time_slot, scheduled_alarm, routine_execution (schema exported to app/schemas)
  data/repo      LibraryRepository (MediaStore/SAF/share/URL import), RoutineRepository,
                 SettingsStore (DataStore), PlaylistParser
  DivioraApp     AppContainer (hand-written DI, ADR 0004)
:core:model            pure Kotlin enums/rules shared by all layers (RoutineRules, ConflictPolicy…)
:scheduler:recurrence  pure Kotlin RecurrenceEngine + ConflictPlanner (unit-tested, no Android)
```

Data flow for a routine:
`Editor → RoutineRepository.save → AlarmScheduler.scheduleRoutine → AlarmManager.setAlarmClock`
→ (time) `RoutineAlarmReceiver → startForegroundService(PlaybackService, RUN_SLOT)` →
`RoutineRunner` (claim occurrence, check rules/media/network) → `PlaybackService.startRoutinePlayback`
→ `ExecutionLog.finish` → `AlarmScheduler.scheduleNext`.

Provider adapters (Spotify remote, YouTube embed, Jellyfin, cloud…) are not built yet. The `MediaSource`
enum and `LibraryRepository` are where the `providers/*` modules from the blueprint plug in (roadmap Phase 7).
