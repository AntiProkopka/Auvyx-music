# AUVYX Alpha 1.9 patch

## Included
- Search defaults to **All** and searches local library + the currently integrated SoundCloud catalogue in parallel.
- Local and SoundCloud filters remain optional.
- A YouTube Music action opens the official YouTube Music search page/app for the same query. This is an external hand-off, not native playback.
- SoundCloud search, favorites and history rows display the supplied SoundCloud provider logo as a small overlay on the artwork.
- Settings include three launcher-icon choices. The three aliases currently point to the existing launcher icon so the project builds before the custom artwork is added.
- Version and GitHub Actions artifact labels updated to Alpha 1.9.

## Not included: platform audio downloads
SoundCloud's Developer API Terms prohibit apps from persistently saving/caching User Content or providing offline access. YouTube's Developer Policies prohibit downloading audiovisual content, separating audio tracks, or offering offline playback outside approved YouTube Premium features. Therefore this patch does not add a stream-ripping/downloader implementation.

Android private app storage is technically available for files that AUVYX is entitled to store: `context.filesDir` is app-private and other ordinary apps cannot access it. That storage mechanism does not grant permission to save SoundCloud or YouTube streams. A future download feature would require a source that explicitly grants third-party download/offline rights or user-provided audio files that the user is entitled to store.

## YouTube Music limitation
Google publishes the YouTube Data API, but not a public YouTube Music API/SDK for third-party native music streaming. The YouTube API policies also prohibit extracting audio from videos and background/offline playback through the API. This patch therefore opens the official YouTube Music search externally; it does not pretend to add YouTube Music tracks to AUVYX's Media3 queue.

## Where to add platform logos
Add provider images in:
`app/src/main/res/drawable-nodpi/`

Included file: `platform_soundcloud.png` (the supplied transparent logo). The code discovers this resource at runtime. Do not use a `platform-` hyphen; Android resource names use lowercase letters and underscores.

## Where to add the three app icons
Included in this patch in `app/src/main/res/mipmap-xxxhdpi/`:
- `auvyx_icon_1.png`
- `auvyx_icon_2.png`
- `auvyx_icon_3.png`

The supplied images are used without redesign or cropping. The first three source uploads were JPEG-encoded images despite their `.png` upload names; they were converted to PNG resource files while preserving their decoded pixels. The three manifest aliases already point to the corresponding resources, and Settings shows their previews.

## Apply
Merge the archive's `app/` and `.github/` folders over the repository root. Keep all other repository files. Build the APK with GitHub Actions.

## Validation
This patch was assembled against the current `main` branch sources. A local Android Gradle build was not run in this environment, so GitHub Actions is the build verification step. Please check the workflow result before installing the APK.


## Build fix

Fixed Kotlin compilation in `SoundCloudTrackRow.kt` and `SoundCloudHistoryRow.kt`: replaced the unavailable `matchParentSize` import/call with `fillMaxSize` for the artwork child inside the fixed-size Box.
