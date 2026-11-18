# Testing

## Automated (runs in CI and locally)
```bash
./gradlew check            # unit tests (recurrence engine, playlist parser) + Android Lint
./gradlew assembleDebug    # debug APK → app/build/outputs/apk/debug/app-debug.apk
```

## Device / emulator checks (adb, command line only)
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.diviora.music.debug/com.diviora.music.MainActivity
adb push sample.mp3 /sdcard/Music/ && adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/sample.mp3
adb shell appops set com.diviora.music.debug SCHEDULE_EXACT_ALARM allow
adb shell dumpsys alarm | grep -A3 com.diviora.music.debug          # registered routine alarms
adb shell dumpsys deviceidle force-idle                                # Doze: alarms must still fire
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.diviora.music.debug   # recovery path (works on emulators/rooted devices)
adb reboot                                                             # real reboot recovery
adb shell settings put global auto_time_zone 0 && adb shell setprop persist.sys.timezone America/New_York   # zone change
adb logcat -s AlarmScheduler RoutineAlarmReceiver RoutineRunner PlaybackService
```

## Routine test matrix (manual, on a physical device)
One-time · daily · weekdays · multi-slot · multi-item (audio + video + stream) · each conflict policy ·
edit/disable/delete · skip next · reboot · time-zone change · Doze · exact alarms revoked · file deleted ·
offline stream · force-stop then reopen (expect a MISSED entry) · screen off for 1 h of playback.
