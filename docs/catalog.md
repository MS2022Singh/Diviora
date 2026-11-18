# Developer content server ("content sources")

DIVIORA can load audio and video that **you, the developer or content provider,** publish on your own server. Every
user of your build gets that content in **Library → Online** and on the Home screen. They can play it, favourite it,
add it to playlists and schedule it in AutoPlay Routines.

## How it works
1. Host a catalog file (JSON, format below) and your media files on any HTTPS server: your own backend, S3/R2/GCS,
   a CDN, GitHub Pages, Firebase Hosting, etc. Media must be direct files or streams the player can open
   (MP3, AAC/M4A, OGG/Opus, FLAC, WAV, MP4, WebM, MKV, HLS `.m3u8`, DASH `.mpd`, Icecast/Shoutcast).
2. Build the app with your catalog URL:
   ```bash
   ./gradlew assembleRelease -Pdiviora.catalogUrl=https://content.example.com/diviora/catalog.json
   ```
   (or set `diviora.catalogUrl` in `gradle.properties`). If it is empty, the bundled demo catalog is used.
3. The app syncs the catalog at start-up and every 6 h when online. When you edit the JSON on your server, users get
   the new items **without an app update**. Items you remove disappear from their library.
4. Users can also add more sources in **Settings → Content sources** (HTTPS only).

## Format (version 1)
```json
{
  "version": 1,
  "name": "My Channel",
  "description": "optional",
  "items": [
    { "id": "ep-001", "type": "audio", "title": "Episode 1", "artist": "Me", "album": "Season 1",
      "url": "https://content.example.com/media/ep-001.mp3",
      "artwork": "https://content.example.com/art/ep-001.jpg", "durationMs": 1800000, "license": "All rights reserved" },
    { "id": "live", "type": "stream", "title": "Live radio", "url": "https://stream.example.com/live" },
    { "id": "intro", "type": "video", "title": "Intro video", "url": "https://content.example.com/hls/intro/master.m3u8" }
  ],
  "playlists": [ { "id": "s1", "title": "Season 1", "itemIds": ["ep-001"] } ]
}
```
Rules: `id` must be unique within the catalog, `url` must be http(s), and `type` is `audio`, `video` or `stream`. The file can be
at most 2 MB and hold at most 5000 items. Invalid items are skipped, not fatal. Catalog playlists appear as `"<name> · <playlist>"`.

Validate before publishing:
```bash
python3 scripts/validate-catalog.py catalog.json
```

## What cannot go in a catalog
YouTube, Spotify, Amazon Music, Gaana, JioSaavn, Apple Music and similar **page links are not media files**. Their audio
is protected, and playing it in another app breaks their terms. The validator rejects them, and the app opens such links in
the platform's own app instead. Publish only content you own or are licensed to distribute.

## Server notes
- Serve the JSON with `Content-Type: application/json`. Allow HTTP range requests on media files so seeking works.
- Use CDN caching for media. Keep the catalog small or short-cached so updates reach users.
- Private/paid content needs authentication (signed URLs or tokens). Signed, expiring URLs in the catalog work today.
  Per-user login is a planned feature.
