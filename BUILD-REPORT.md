# Auralis 0.5.0 — build and verification report

## Source checks completed
- Updated Gradle app version and GitHub Actions artifact naming.
- Added the Android INTERNET permission required by the SoundCloud embed.
- Updated app label, launcher foreground artwork and platform theme resource.
- Replaced the previous palette implementation with a Material 3 dynamic-colour theme (Android 12+) and a Material 3 fallback palette on older Android versions.
- Changed the default appearance to System and mapped legacy saved values (HyperOS/LANE/Liquid) to System mode.
- Reworked app navigation shell, home screen, search screen, settings screen, local track rows and empty states.
- Added a SoundCloud WebView embed based on SoundCloud's official HTML5 Widget/embedded-player URL. Input is restricted to HTTPS SoundCloud hosts.
- Kept the existing local Room / DataStore / Media3 code and app ID intact.

## Not verified here
- No Android SDK or Gradle installation was available in this environment, so a full `assembleDebug` was not run.
- No APK was generated in this environment.
- No physical-device visual test was possible.
- SoundCloud embedded playback requires testing with a real public link; some tracks disallow embedding and some links may be unavailable.

## Required next checks
1. Run GitHub Actions workflow **Build Auralis Music APK**.
2. Install the downloaded APK on the target Android device.
3. Confirm Material 3 screens render correctly in both system light and dark mode.
4. Paste a public SoundCloud track URL in Search → SoundCloud and verify that the official widget loads and plays after tapping its Play button.
5. Confirm local playback, queue, playlists, library persistence and Speed Lab still work as before.

A green GitHub build only proves compilation, not device playback or provider availability.
- A Kotlin parser pass was attempted without an Android/Compose dependency classpath. No Kotlin parse/`expecting` diagnostics were found, but this is not a substitute for Gradle compilation.
