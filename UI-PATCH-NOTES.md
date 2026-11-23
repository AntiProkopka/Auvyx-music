# AUVYX Alpha 2.0 — UI patch (source only)

This patch was prepared from the user's **AUVYX-Alpha-2.0-Source.zip** (Library Test, versionCode 16). The original APK is unmodified.

## Changes

1. UI content colour defaults to the active Material 3 `onBackground` colour. This fixes dark icons and text on dark backgrounds when there is no enclosing Material Surface.
2. SoundCloud tracks no longer save the provider name as a fake album. Existing saved SoundCloud records with the old sentinel album are cleaned on ViewModel initialization, without changing IDs or favourites. Player metadata also filters out duplicate labels.
3. The artist's name under the artwork in the full-screen player is clickable and opens the existing artist overlay without stopping the player.
4. Artist profile redesigned within Material 3: large artwork header, actual provider bio, play button and artwork-bearing song rows. SoundCloud still looks up the profile by ID. No invented albums or popularity ranks.
5. As an optional automatic lyrics provider, AUVYX now checks LRCLIB by track name, artist and duration. Prefers time-synced LRC where available, uses plain lyrics otherwise, and retains manual TXT/LRC as fallback. Existing saved lyrics take priority. This is an independent lyrics catalogue, not lyrics served by SoundCloud.
6. Player artwork gets a softly blurred accent layer based on a cached representative artwork colour, updated only when the artwork changes.
7. User-selectable launcher icon files keep their three options but now use adaptive icon XML layers with enlarged foregrounds. The third icon is the default. Actual HyperOS launcher rendering still requires a phone test.

Spotify and YouTube Music were not modified. No wholesale design overhaul was made.

## Status

- Checked the source structure and XML resources; **not** compiled or installed.
- No Android SDK / Gradle is installed in this sandbox. The delivered archive is updated source, **not a working APK**.
- Build with JDK 17, Android SDK 34 and the project's Gradle wrapper:
  `./gradlew -Pauvyx.libraryTest :app:testDebugUnitTest :app:assembleDebug`
- Do not assume a newly generated debug APK can update the existing test app: Android requires the **same package name AND signing certificate**. Back up your data before installing a differently signed build.
- Auto lyrics depend on external LRCLIB availability and metadata matches; some songs have no lyrics or no synchronized lyrics.
- Effects need testing on a physical device, especially the launcher icon, layout contrast and artwork glow.
