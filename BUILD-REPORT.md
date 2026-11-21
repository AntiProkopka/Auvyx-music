# AUVYX Alpha 1.6 — change and verification report

## Changes made
- Replaced the previous visible branding with AUVYX.
- Updated app label, in-app brand labels, Android theme resource name, Gradle root project name, workflow name, workflow run name, concurrency key, and uploaded artifact name.
- Set `versionName` to `Alpha 1.6` and incremented `versionCode` to 6.
- Preserved `namespace` and `applicationId` as `com.nexus.music` to avoid breaking updates to the already-installed app.
- Kept Material 3 and the existing SoundCloud embedded-player approach; did not claim that SoundCloud has native Media3 integration.

## Not verified
- This environment does not have Gradle or an Android SDK, so no APK build was run.
- No device install or playback test was performed.
- The exact failure cause for the latest GitHub Actions run is not visible in the supplied screenshot. The build workflow was kept on the action/toolchain configuration from the available source archive; this update fixes project naming/version metadata, but cannot honestly be described as a proven fix for the unknown CI error.

## Required verification
1. Push this source to the repository and run **Build AUVYX APK**.
2. If it fails, open the failed run and inspect the first red step's complete log. The first compiler/Gradle error is needed to fix the actual cause.
3. Install `AUVYX-Alpha-1.6-debug` on the phone and test local playback, playlists, queue restoration, Speed Lab, and one public SoundCloud embed URL.
