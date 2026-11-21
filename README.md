# NEXUS Music 0.4.0 — Foundation+

NEXUS Music is an Android local music player built with Kotlin, Jetpack Compose, Room, DataStore and AndroidX Media3.

This release concentrates on a dependable local player. It is **not** yet a multi-platform streaming aggregator. Spotify, YouTube Music, Yandex Music and SoundCloud are not connected in this build; there are no fake source buttons or claims of direct playback from those services.

## Included in 0.4.0

- Add individual audio files through Android's system document picker.
- Add folders through Storage Access Framework; scan nested folders for audio.
- Read title, artist, album and duration from local file metadata.
- Extract embedded album artwork and cache it in app-private storage.
- Persistent library, favourites, playlists, listening history and queue (Room).
- Settings persisted with DataStore, including selected theme and Speed Lab values.
- Media3 playback with play/pause, next/previous, seeking, shuffle, repeat and editable queue.
- Background playback through MediaSessionService, with system media controls/notification.
- Restore the queue and last saved playback position after the app process is recreated.
- Speed Lab: independent playback speed and pitch controls, 0.5×–2.0×, persisted between launches.
- Four selectable palettes: HyperOS (default), LANE, Material 3 dynamic colours, and Liquid Glass palette.
- Search, sort, recently played, track actions, add-to-playlist, rename and delete playlists.
- Database migration from the 0.3.0 schema to 0.4.0, adding artwork metadata without deleting the library.

## Known limitations

- Liquid Glass currently changes the colour palette only. Real-time background blur/translucent glass rendering is not included.
- The Material 3 theme uses Android dynamic colours on Android 12+; older versions use the standard Material palette.
- Equalizer, crossfade, synced lyrics, cloud backup and provider integrations are not included yet.
- Playback support for individual codecs depends on Android's installed platform decoders and the selected file provider.
- Files whose SAF permission is revoked may stop playing; re-add the file or folder to grant access again.
- Queue restoration saves the playback position periodically while playing, so the last few seconds may not be preserved after a sudden force-stop or power loss.

## Build

Requirements: JDK 17, Android SDK Platform 34, Android Build Tools, and Gradle 8.7.

```bash
gradle --no-daemon assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

A GitHub Actions workflow is included at `.github/workflows/build.yml`. Push the project to the `main` or `master` branch (or start it manually from Actions) and the workflow will build the debug APK and publish it as the artifact `NEXUS-Music-0.4.0-debug` for 14 days. Open the successful workflow run in GitHub Actions, download that artifact ZIP, extract it, and install `app-debug.apk`.

## Important verification note

This source archive was statically reviewed and edited in an environment without an Android SDK or Gradle installation and without package-repository network access. XML/YAML parsing and a Kotlin syntax parse passed, but a successful Android compilation and on-device playback test could not be performed here. The included GitHub Actions workflow is configured to perform the real Gradle build. Do not treat this archive as a device-tested release until that build succeeds and the APK has been installed and tested on an Android device. See `BUILD-REPORT.md` for the exact checks and limitations.

## Project structure

```text
app/src/main/java/com/nexus/music/
  data/db/          Room entities, DAOs, database and migration
  data/local/       SAF media scanning and metadata extraction
  data/settings/    DataStore settings
  playback/         Media3 service and player controller
  ui/screens/       Home, search, library, playlists, player, settings
  ui/components/    Reusable track rows, artwork, dialogs and menus
  ui/theme/         Four selectable colour themes
```
