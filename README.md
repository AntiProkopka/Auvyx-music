# AUVYX · Alpha 2.0

Сборка, установка, перенос данных и результаты проверок описаны в [отчёте Alpha 2.0](ALPHA-2-TEST.md).

## Архив версии 1.6

AUVYX is an Android music player built with Kotlin, Jetpack Compose and Material 3. The current project includes a local music library and a first, limited SoundCloud integration.

## Alpha 1.6

- App branding and launcher label use **AUVYX**.
- Material 3 is the only visual design system. Appearance can follow the system, Light, or Dark mode.
- Local playback remains powered by AndroidX Media3.
- SoundCloud links are played through SoundCloud's official embedded HTML5 player.
- Android `applicationId` remains `com.nexus.music` so this build can update the existing installation instead of appearing as a separate app.

## SoundCloud: current limits

The integration accepts public HTTPS SoundCloud track or playlist links and displays the official embedded player. It does not extract audio streams, download tracks, or bypass access restrictions. Tracks with embedding disabled, private/unavailable links, or WebView/provider restrictions may not play.

SoundCloud playback is separate from the native local Media3 player. It is not currently part of the local queue, Android MediaSession, local listening history, or Speed Lab. This is an embed integration, not a native SoundCloud streaming adapter.

## Local player features

- Add audio files and folders through Android's Storage Access Framework.
- Read common metadata and embedded artwork.
- Persistent local library, favourites, playlists, and listening history with Room.
- Persistent settings with DataStore.
- Media3 playback with play/pause, next/previous, seeking, shuffle, repeat, and queue controls.
- Background playback with MediaSessionService and system media controls.
- Queue and last-position restoration.
- Speed and pitch controls for local Media3 playback.

## Not included

Spotify, YouTube Music and Yandex Music are not connected. There is no native SoundCloud stream resolver, unified cross-provider queue, crossfade, equalizer, synced lyrics, cloud backup, or account sync.

## Build

Requirements: JDK 17, Android SDK Platform 34, Android Build Tools 34.0.0, and Gradle 8.7.

```bash
gradle --no-daemon clean assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions workflow: **Build AUVYX APK**. It runs on pushes to `main`/`master`, pull requests, and manual dispatch. The artifact is **AUVYX-Alpha-1.6-debug**.

## Verification status

This source was adapted from the available Alpha 1.5 project archive. Android SDK and Gradle are not installed in this editing environment, so `assembleDebug` could not be run here. The GitHub Actions build and an install/playback test on the target phone are still required. A green Actions run will verify compilation, not SoundCloud availability or all runtime behaviour.
