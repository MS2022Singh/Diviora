# DIVIORA Music

A native, local-first Android audio and video player with **AutoPlay Routines**: schedule any number of
tracks, playlists, videos or streams to play automatically, at any number of times, once or on a repeating schedule.

- Specification: [`docs/blueprint.md`](docs/blueprint.md) · Agent build prompt: [`docs/master-prompt.md`](docs/master-prompt.md)
- Developer content server (your own audio/video for all users): [`docs/catalog.md`](docs/catalog.md)
- Decisions: [`docs/adr/`](docs/adr) · Progress and restore points: [`docs/STATE.md`](docs/STATE.md)

## Build from the command line

Requirements: JDK 17 or newer, and an Android SDK with platform 36 (`sdk.dir` in `local.properties`, or `ANDROID_HOME`).

```bash
./gradlew check assembleDebug          # unit tests + lint + debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows (cmd / PowerShell):

```bat
gradlew.bat check assembleDebug
```

## Restore a previous state

```bash
git fetch --all --tags
git tag -l                                          # list checkpoints and milestones
git switch -c recovery/<name> <tag>                 # safe restore onto a new branch
scripts/verify-restore.sh <tag>                     # prove a tag builds from a fresh clone
```

Licence: proprietary, all rights reserved. See `LICENSE` and `THIRD_PARTY_NOTICES.md`.
