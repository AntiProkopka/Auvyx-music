# Changelog

## 0.4.0 — Foundation+

- Hardened local folder scanning so a single inaccessible folder/file does not abort a complete scan.
- Added extraction and private caching of embedded album artwork.
- Added artwork thumbnails in track rows and the player.
- Added Room migration from database schema 1 to 2 without deleting existing library data.
- Improved MediaController startup by queuing player actions until the session connection is ready.
- Added visible playback error feedback.
- Improved persistence and restoration of the playback queue, shuffle/repeat state and last playback position.
- Added independent speed and pitch controls to Speed Lab, including persistent values and presets.
- Added four selectable colour palettes: HyperOS, LANE, Material 3 and Liquid Glass palette.
- Corrected list sizing in library/search/playlist views.
- Added a GitHub Actions workflow that builds and uploads a debug APK.

### Not included in this version

Spotify, SoundCloud, YouTube Music and Yandex Music integrations; equalizer; crossfade; synchronized lyrics; cloud backup; real glass blur/translucency.

### Verification status

Source-level static checks only. Android Gradle compilation and device testing were not possible in the current environment. See `BUILD-REPORT.md`.
