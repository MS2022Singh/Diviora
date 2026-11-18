#!/usr/bin/env bash
# End-to-end smoke test on a connected device/emulator (command line only):
# install → grant → add media → launch → schedule a 1-minute test routine → verify it fired and played.
# Usage: scripts/smoke-test.sh <dir-with-audio-files>
set -euo pipefail
MEDIA_DIR="${1:?directory with audio files}"
ADB="${ADB:-adb}"
PKG=com.diviora.music.debug
APK=app/build/outputs/apk/debug/app-debug.apk
OUT="${OUT:-build/smoke}"; mkdir -p "$OUT"
step(){ echo ">> $*"; }

if [ -z "${SKIP_INSTALL:-}" ]; then
  step "install"; $ADB install -r -g "$APK" >/dev/null
  # Slow (unaccelerated) emulators: AOT-compile so service start stays under Android's ANR limit.
  [ -n "${AOT_COMPILE:-}" ] && $ADB shell cmd package compile -m speed -f $PKG >/dev/null
fi
$ADB shell appops set $PKG SCHEDULE_EXACT_ALARM allow || true
$ADB shell dumpsys deviceidle whitelist +$PKG >/dev/null || true
step "push media"; for i in $(seq 1 60); do $ADB shell mkdir -p /sdcard/Music/diviora 2>/dev/null && break; sleep 5; done
for f in "$MEDIA_DIR"/*; do for t in 1 2 3 4; do $ADB push "$f" /sdcard/Music/diviora/ >/dev/null 2>&1 && break; sleep 5; $ADB wait-for-device; done; done
$ADB shell content call --method scan_volume --uri content://media --arg external_primary >/dev/null 2>&1 || true
$ADB logcat -c
step "launch"; $ADB shell am start -W -n $PKG/com.diviora.music.MainActivity >/dev/null
for i in $(seq 1 60); do $ADB logcat -d | grep -q "rescheduleAll: app start" && break; sleep 2; done
sleep 15
$ADB shell am start -n $PKG/com.diviora.music.MainActivity --es open settings >/dev/null; sleep 20
step "create a test routine (debug hook)"
$ADB shell am broadcast -n $PKG/com.diviora.music.debug.DebugRoutineReceiver >/dev/null
for i in $(seq 1 90); do line=$($ADB logcat -d -s DebugRoutine:I DebugRoutine:E | grep -E "created|failed" | tail -1 || true); [ -n "$line" ] && break; sleep 2; done
echo "$line"
step "registered alarm"; $ADB shell dumpsys alarm | grep -c "FIRE_ROUTINE" || true
step "wait for the routine to fire (up to 8 min)"
for i in $(seq 1 240); do
  line=$($ADB logcat -d -s RoutineRunner:I | grep "result=" | tail -1 || true)
  [ -n "$line" ] && break; sleep 2
done
echo "$line"
sleep 5
$ADB shell dumpsys media_session | grep -E "state=PlaybackState|description=" | head -4 | tee "$OUT/session.txt"
$ADB shell am start -n $PKG/com.diviora.music.MainActivity --es open player >/dev/null; sleep 6
$ADB exec-out screencap -p > "$OUT/player.png"
echo "$line" | grep -q "result=PLAYED" && grep -qE "state=(3|PLAYING)" "$OUT/session.txt" && echo "SMOKE PASS" || { echo "SMOKE FAIL"; exit 1; }
