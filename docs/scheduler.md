# AutoPlay Routines: behaviour and Android limits

## Model
- **Routine**: name, enabled, ordered items (any library media: audio, video, stream) and **rules**.
- **Time slots**: a routine can have *many*. Each has a wall-clock time and a repeat (once / every N days /
  chosen weekdays every N weeks / monthly on day D, with short months clamped to their last day), a start date, and
  optionally an end date, a maximum count and excluded dates. A slot uses the device zone unless it is pinned to a zone.
- **Rules**: shuffle, repeat queue, set volume (media or alarm stream), fade-in, stop after N minutes (with a 4 s
  fade-out), conflict policy (replace / append / skip if something is playing), audio channel (media or
  alarm, which plays under Do Not Disturb), late grace (still play if the device was off and it is at most X min late).

## Guarantees
- One alarm per slot: only its next occurrence. It is mirrored in `scheduled_alarm`.
- Each occurrence runs at most once (unique index on slot + occurrence in `routine_execution`).
- Every outcome is logged: PLAYED, SKIPPED (disabled / skipped by user / something else playing), FAILED (no
  items, files missing, offline, background start blocked), MISSED (device off or app stopped).
- Recovery re-registers alarms on boot, app update, clock/time-zone change, exact-alarm permission change,
  app start, and every 12 h (watchdog). Recovery **never** starts playback, because Android 15 forbids
  starting a media foreground service from BOOT_COMPLETED. Occurrences still inside the grace window are re-fired
  through a fresh alarm, which is allowed to start playback.
- Daylight saving: a time inside a spring-forward gap moves forward by the gap. In an autumn overlap the slot fires
  once, at the earlier offset.

## Android limits (shown in Settings → Routine reliability check)
| Condition | Effect | Mitigation in app |
|---|---|---|
| Exact-alarm permission off (default on Android 14+ new installs) | Alarm is inexact (minutes late); background start may be refused | Warning banner, "Open" button, a "Tap to start" notification fallback |
| Notifications denied (Android 13+) | No media controls or alerts (playback still works) | Permission request |
| Battery optimisation / OEM killers | Alarms may be dropped | Settings shortcut; 12 h watchdog |
| Force-stop | Android cancels all alarms until the app is opened | Disclosed in Settings; app start re-registers them |
| Scheduled video with screen off | Video cannot appear without a full-screen intent | Audio starts, "Tap to watch" notification |
| Stream item while offline | Stream skipped; local items still play | Reason recorded in history |
