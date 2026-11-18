# Supplying your own content to all users: step by step

You (the developer) publish a list of your audio/video files on the internet. Every copy of DIVIORA reads that list
and shows your content in **Library → Online** and on the **Home** screen. Users can play it, favourite it and schedule it.
When you change the list, users see the change within 6 hours (or immediately via **Settings → Content sources → Refresh**),
**without installing an update**.

## The easy way: free hosting on GitHub Pages (about 15 minutes, no server needed)
1. **Create a public content repository.** On github.com click **New repository**, name it `diviora-content`, choose
   **Public**, tick **Add a README**, then click **Create**. (Public is needed so phones can download the files.)
2. **Upload your media.** In that repo click **Add file → Upload files**, drag in your files (MP3, M4A, MP4…), then
   click **Commit changes**. Each file can be up to 100 MB. For bigger or many files, see "Larger libraries" below.
3. **Turn on GitHub Pages.** In that repo go to **Settings → Pages → Branch: main → / (root) → Save**. After 1–2 minutes your site
   is at `https://ms2022singh.github.io/diviora-content/`. A file `song1.mp3` is then at
   `https://ms2022singh.github.io/diviora-content/song1.mp3`.
4. **Create `catalog.json`.** Click **Add file → Create new file**, name it `catalog.json`, paste the template below and change
   the titles and URLs to your files. Click **Commit changes**.
   ```json
   {
     "version": 1,
     "name": "My DIVIORA Channel",
     "items": [
       { "id": "song1", "type": "audio", "title": "My first song", "artist": "Me",
         "url": "https://ms2022singh.github.io/diviora-content/song1.mp3" },
       { "id": "video1", "type": "video", "title": "My first video",
         "url": "https://ms2022singh.github.io/diviora-content/video1.mp4" }
     ],
     "playlists": [ { "id": "best", "title": "Best of", "itemIds": ["song1"] } ]
   }
   ```
   Every item needs a different `id`. The `type` is `audio`, `video` or `stream` (live radio).
5. **Check it.** Open `https://ms2022singh.github.io/diviora-content/catalog.json` in a browser; you should see your text.
   Optional: `python3 scripts/validate-catalog.py catalog.json`.
6. **Test it on your own device (developer only).** Open **Settings → Developer options** (debug build, or unlocked
   release build: see [DEVELOPER_GUIDE.md §5](DEVELOPER_GUIDE.md#5-developer-options-content-sources-developer-only)), paste the catalog
   URL and press **Add source**. Your items appear in **Library → Online**. Normal users never see this option.
7. **Publish it to every user.** On GitHub open the Diviora repo → **Settings → Secrets and variables → Actions →
   Variables → New repository variable**, name `DIVIORA_CATALOG_URL`, value
   `https://ms2022singh.github.io/diviora-content/catalog.json`. Then go to **Actions → Build installers → Run workflow**
   (or push to `main`). Everyone who installs the new version gets your content automatically, with nothing to type.
8. **Add more content later.** Upload new files and add entries to `catalog.json`. Users receive them automatically.

## Larger libraries or your own server
Any HTTPS host works: your own server (nginx/Apache), Amazon S3, Cloudflare R2, Google Cloud Storage, Firebase Hosting,
Bunny CDN, etc. Put `catalog.json` and your media there and use the catalog's https:// address in step 6/7. The
`content-server/` folder in this repo is a ready template to copy.

---

# Reference

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
4. Only the developer can add sources (Developer options). Release builds load only the built-in catalog.

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
