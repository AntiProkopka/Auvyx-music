# auvyx Alpha 2.0 — patch 01

This archive contains the files to add or replace in the repository. Keep the folder structure and overwrite the matching files.

## Included in this patch

- Custom six-band PCM equalizer in the Media3 audio pipeline: 60 Hz, 150 Hz, 400 Hz, 1 kHz, 2.4 kHz, 15 kHz.
- Bass Boost is the first-launch preset. The 400 Hz and 2.4 kHz bands are at 0 dB in that preset.
- Presets: Bass Boost, Default, Treble Boost, Vocal, and Custom. Gain range: -12 dB to +12 dB.
- Audio output picker for the built-in speaker and connected Bluetooth, wired, USB, HDMI and other supported Android output devices. The built-in speaker label uses the saved nickname as `[nickname]'s phone`.
- First-run nickname dialog, saved locally in DataStore.
- App display name is lowercase `auvyx`; launcher icon 3 is enabled by default; all three icon choices remain available in Settings.
- SoundCloud artwork loader now tries original and high-resolution image variants before falling back.
- SoundCloud search rows now have a three-dot menu with working queue actions; the temporary button that opened YouTube Music outside the app has been removed.
- Home, Settings, launcher labels and the Android app name use lowercase `auvyx`.
- Alpha version and GitHub Actions artifact naming are updated to 2.0.
- Optional stable CI signing through GitHub Actions repository secrets.

## Stable signing setup

A signing key cannot safely be committed to this public repository. To use the same signature between Actions builds, the repository owner must add these four Actions secrets:

- `AUVYX_SIGNING_KEYSTORE_BASE64` — Base64-encoded JKS keystore.
- `AUVYX_SIGNING_STORE_PASSWORD` — keystore password.
- `AUVYX_SIGNING_KEY_ALIAS` — key alias.
- `AUVYX_SIGNING_KEY_PASSWORD` — key password.

Without these secrets, the workflow still builds an APK, but it uses the runner's normal debug key and does **not** guarantee that it can update an APK signed by a previous runner. Do not publish a signing key in the repository.

If the currently installed Alpha 1.9 was signed with an already-lost debug key, no Gradle setting can recreate that signature. Export the app's data before replacing it, or keep the old signing key if it is still available.

## Important scope note

This is the first implementation patch for the audio and app-foundation parts of Alpha 2.0. It does not yet add Spotify or YouTube Music providers, universal cross-platform playlists/history, artist pages, or database backup/restore. Those require additional catalogue and persistence work; this archive does not pretend those features are already working.

The EQ is applied to decoded PCM audio. Formats routed through encoded passthrough/offload cannot be equalized by a PCM processor; Media3's processing path uses PCM where available.
