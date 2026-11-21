# Auralis 0.5.0

Auralis is an Android music app built with Kotlin, Jetpack Compose, Material 3, Room, DataStore and AndroidX Media3.

## What changed in 0.5.0

- Rebranded the visible app name and launcher artwork to **Auralis**.
- Kept the Android application ID `com.nexus.music` so this remains the same app identity for existing installs. Internal Kotlin package names are unchanged intentionally.
- Reworked the app shell around Material 3: centered top app bar, Material 3 navigation bar, tonal mini-player, dynamic colour scheme and consistent screen surfaces.
- Made Material 3 the only visual design system. The old HyperOS/LANE/Liquid palette selector was replaced by System / Light / Dark appearance modes. Existing saved palette names are read as System so an upgrade does not remain stuck on the old palette.
- Updated the home screen, track rows, empty states, search, library actions and settings to use Material 3 cards, list items, chips, typography and tonal surfaces.
- Added a SoundCloud source in Search. Paste a public track or playlist URL and listen through the official SoundCloud embedded player in the app.

## SoundCloud integration: what it does and does not do

The current integration uses SoundCloud's documented embedded HTML5 player / Widget URL. It does **not** extract audio streams, download tracks, or bypass SoundCloud access restrictions. A track may fail to embed if its owner disabled embedding, the link is private/unavailable, or SoundCloud blocks the embedded player in the current WebView environment.

SoundCloud playback is currently separate from the local Media3 player: it is not added to the local queue, system MediaSession, local history or Speed Lab. This is an intentionally narrow first integration, not a claim that the full SoundCloud API adapter is finished. Official API credentials are a separate setup; current SoundCloud registration documentation says app credentials require an Artist Pro subscription, so no fake client ID or embedded secret is included in this APK.

## Existing local-player features retained

- Add audio files or folders through Android's Storage Access Framework.
- Read common metadata and embedded artwork.
- Persistent local library, favourites, playlists and listening history (Room).
- Persistent settings (DataStore).
- Media3 playback with play/pause, next/previous, seeking, shuffle, repeat and queue controls.
- Background playback via MediaSessionService and system media controls.
- Queue and last-position restoration.
- Speed Lab with independent speed and pitch controls for local Media3 playback.

## Still not included

Spotify, YouTube Music and Yandex Music are not connected. There is no SoundCloud native stream resolver, custom integrated SoundCloud queue, crossfade, equalizer, synced lyrics, cloud backup or account sync. Do not treat the SoundCloud embed as a unified native audio source.

## Build

Requirements: JDK 17, Android SDK Platform 34, Android Build Tools 34.0.0 and Gradle 8.7.

```bash
gradle --no-daemon clean assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The GitHub Actions workflow `.github/workflows/build.yml` runs on push to `main`/`master` or manually with **Actions → Build Auralis Music APK → Run workflow**. It publishes the artifact `Auralis-Music-0.5.0-debug` for 14 days.

## Verification status

This source archive was updated in an environment without Android SDK or Gradle. I could inspect and edit the Kotlin/XML/Gradle/manifest files, but I could not run an Android compile or install the app on a device. The GitHub Actions workflow is the next required build check. The embedded SoundCloud widget also requires a real-device test with a public track URL. Do not treat this archive as a fully verified release until both checks pass.
