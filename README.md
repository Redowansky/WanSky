# WanSky
The All In One Media Solutions

Redowan's own everything-app: movies, series, live TV, sports and YouTube —
modeled on [Hamyra](https://hamyra.xyz)'s functions, built with Kotlin +
Jetpack Compose and Media3 (ExoPlayer).

## What it plays

- **Movies + TV series** — from his own library catalog
  (`https://redowansky.bond/api/movies`). Main-bucket files play through the
  signed `/stream/` URLs (token minted at play time, same flow as the
  Library app); other files play direct. Lulustream embeds play in a WebView.
- **Live TV** — M3U playlists, fetched on-device:
  - 103 BDIX — `http://103.229.254.25:7001/playlist.m3u8`
  - Bdix — `https://raw.githubusercontent.com/Sopnnonil775/Bdix3/refs/heads/main/Bdix.m3u`
  - iptv-org — `https://iptv-org.github.io/iptv/index.m3u`
- **Sports** — sports channels filtered out of the same playlists.
- **YouTube** — in-app search + play (v0.2; needs a YouTube Data API key).
- **AI Picks** — pick a mood, get suggestions from his own catalog.

## Build

Gradle doesn't run on Pepper's machine, so the app builds in the cloud:
every push to `main` runs the **Android** workflow (JDK 17 + Gradle 8.7)
and uploads the debug APK as the `wansky-debug` artifact. Download it from
the Actions tab — no APKs are attached in chat.

## Notes

- Package `app.pepper.wansky`, minSdk 26. The app sends its own
  User-Agent (`WanskyApp/x.y.z`), so the Library app's server-side version
  gate (which only matches the `RedowanskyLibraryApp` UA) does not apply.
- `usesCleartextTraffic` is on: live TV includes plain-http streams
  (the 103 BDIX playlist and some iptv-org entries).
- The iptv-org list contains a few malformed `#EXTINF` lines; the M3U
  parser in `data/Playlists.kt` is deliberately tolerant of them.
