# DIVIORA Music: user guide

## 1. Screens (Android)
| Tab | What it is for |
|---|---|
| **Home** | Next routine, quick buttons (Import · Add link · New routine), online content, recently played, favourites, recently added |
| **Library** | Online · Songs · Videos · Albums · Artists · Streams · Playlists · Favourites, plus search and **+** to add |
| **Routines** | Your AutoPlay Routines, **New routine**, and the ⟲ history |
| **Settings** | Reliability check, rescan, theme, about |

On desktop the same sections are in the left bar, and the player is at the bottom.

## 2. Adding music and videos
- **Automatic (Android):** DIVIORA finds the music and videos already on the phone. Use **Settings → Rescan device** after
  copying new files.
- **Files or folders:** **Library → + → Files** or **Folder** (desktop: **Library → Files / Folder**).
- **From another app:** **Share → DIVIORA Music**, or **Open with → DIVIORA Music**.
- **A link:** **Library → +**, paste the link, then tap **Add & play**:
  - **YouTube, Spotify, SoundCloud:** play *inside DIVIORA* with the service's official player. Their rules allow
    on-screen playback only, so playback stops if you leave that screen or lock the phone, and Spotify plays 30-second
    previews unless you are signed in inside the player. Tap **↗ Open in …** for the full app.
  - **Gaana, Amazon Music, JioSaavn, Apple Music, etc.:** these services have no embeddable player, so the link opens in their app.
  - **Direct links** (…`.mp3`, `.m4a`, `.mp4`, `.webm`, `.m3u8`, `.mpd`, internet radio): play in DIVIORA's own player,
    including in the background and in routines.

## 3. Playing
Tap any item to play it. The **mini player** at the bottom has play/pause, next and **✕** (stop and close). Tap it for **Now playing**:
seek bar, previous/next, shuffle, repeat, **⟲10 / 10⟳**, ♥ favourite, add to playlist, ⏰ schedule, speed, 🌙 sleep timer,
⏹ stop, and ☰ queue (tap to jump, ✕ to remove). Videos play at the top.

**Picture-in-picture (video):** tap the **PiP** button at the top of Now playing, or press Home or switch apps while a video
plays. The video keeps playing in a small window you can drag anywhere, with ⏮ ⏯ ⏭ buttons. Tap it to make it bigger,
or tap ⤢ to return to DIVIORA. On a computer, click **Picture-in-picture** under the video (or in the player bar) for a small
always-on-top window. Close it to bring the video back.

## 4. AutoPlay Routines (you choose what plays and when)
1. Press **⏰** next to any song or video, in **Now playing**, on the **Schedule** button above a list, or use
   **Routines → New routine**.
2. **What to play:** add songs, videos, streams or a whole playlist, and reorder them with ↑ ↓.
3. **When to play → Add time:** choose the time, then **Once** (pick the date), **Daily** (every N days), **Weekdays**,
   **Weekly** (pick days, every N weeks) or **Monthly** (day of month), and optionally **Stop after N runs**.
   Add as many times as you like, for example 07:00 on weekdays and 09:30 on Saturday.
4. **Playback rules:** shuffle, repeat, volume, fade-in, stop after N minutes, what happens if something is already
   playing (replace / add to queue / skip), **Alarm volume** (plays even in Do Not Disturb), and "still play if late by …".
5. Tap **Save**. The routine card shows **Next: …**. Its ⋮ menu has **Run now**, **Skip next run**, **Duplicate** and **Delete**.
6. **Routines → ⟲ History** shows every run: played, skipped, failed (with the reason) or missed.

YouTube/Spotify items in a routine can only play on screen, so at the routine's time you get a **"tap to play"** notification.

### Routines did not play?
Open **Settings → Routine reliability check** and make every line green:
- **Exact alarms → Open → Allow** (Android 12+).
- **Notifications → Allow**.
- **Battery optimisation off → Open → All apps → DIVIORA Music → Don't optimise**.
- Phone-brand battery savers (they stop apps from waking up):
  - **Vivo / iQOO:** Settings → Battery → Background power consumption management → DIVIORA → **Allow**. Also i Manager → App manager → Autostart → DIVIORA **on**.
  - **Xiaomi / Redmi / POCO:** Settings → Apps → DIVIORA → **Autostart on**, then Battery saver → **No restrictions**.
  - **Oppo / Realme / OnePlus:** Settings → Battery → DIVIORA → **Allow background activity** and **Allow auto launch**.
  - **Samsung:** Settings → Battery → Background usage limits → remove DIVIORA from *Sleeping/Deep sleeping apps*.
- Do not **force-stop** DIVIORA: Android then cancels all routines until you open the app again.
- Use **Settings → Quick test** to schedule a routine about 2 minutes ahead using music files on your phone, then check
  **Routines → History**.

On a computer, DIVIORA must be running (it stays in the tray when the window is closed) and the computer awake.

## 5. Online content and updates
- **Library → Online** and the Home screen show audio and video published by the DIVIORA developer. New content appears
  by itself (when the app starts and every hour while online). Tap **⟳ Refresh online content** to check now.
- **Offline downloads:** if the developer allows it, an online item's **⋮** menu has **Download for offline**. Saved
  items show "⬇ Offline", play without internet and can be removed with **⋮ → Remove download**. If the developer turns
  downloads off, saved copies are removed automatically.
- **App updates:** DIVIORA checks for a new version every day. On Android tap the **Update available** banner →
  **Download** → **Install**. On a computer click **Download update** and run the installer. Settings → **App updates** → **Check now**.

## 6. Playback errors
| Message | Meaning / fix |
|---|---|
| `ERROR_CODE_IO_BAD_HTTP_STATUS` | The server refused the link (deleted, private or blocked). DIVIORA skips to the next item. Remove or replace the link. |
| `…NETWORK_CONNECTION_FAILED` | No internet. Local files still play. |
| `…DECODING_FORMAT_UNSUPPORTED` | The device cannot play this format. Try MP3/M4A/MP4. |
| "Files are missing or access was revoked" (history) | The file was moved or deleted. Add it again. |
