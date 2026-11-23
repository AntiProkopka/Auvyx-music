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

## Сборки обновления Alpha 2.0

Текущая сборка использует пакет `com.nexus.music` и `versionCode=16`.
Для сборки нужны JDK 17 и Android SDK 37.0 с Build Tools 36.0.0. Gradle Wrapper использует версию 9.3.1.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions проверяет ветку `alpha-2.0-bugfix-cloud` при публикации коммитов.
Результаты тестов и APK доступны в артефактах запуска.

Для подписанного релиза добавьте четыре секрета GitHub Actions:
`AUVYX_SIGNING_KEYSTORE_BASE64`, `AUVYX_SIGNING_STORE_PASSWORD`,
`AUVYX_SIGNING_KEY_ALIAS` и `AUVYX_SIGNING_KEY_PASSWORD`.
Используйте ключ уже установленной основной версии, если он доступен.
При полном наборе секретов CI проверит подпись и опубликует
`auvyx-Alpha-2.0-release`. Для следующих сборок сохраняйте тот же ключ,
пакет и увеличивайте `versionCode`.

APK с отладочным ключом предназначен для тестирования. Если старая установка
имеет суффикс `.preview`, `.librarytest` или `.providerreview`, установите
новую основную версию рядом, экспортируйте JSON из старой и импортируйте его
в новой. Если пакет старой установки уже `com.nexus.music`, а её ключ
недоступен, сначала сохраните и проверьте резервную копию. После этого Android
потребует удалить старую установку перед установкой APK с новым постоянным
ключом. Резервная копия не переносит аудиофайлы и разрешения доступа к папкам.

## SoundCloud в Alpha 2.0

Офлайн-копии в AUVYX доступны для локальных аудиофайлов из системного выбора документов. [Условия API SoundCloud](https://developers.soundcloud.com/docs/api/terms-of-use#caching) запрещают сторонним приложениям сохранять аудио для офлайн-доступа.

Кроссфейд поддерживает перематываемые потоки SoundCloud по запросу, если следующий трек успел загрузиться. При недоступности потока используется обычный переход очереди. SoundCloud не предоставляет этому клиенту надёжный список треков альбома. Страницы альбомов собираются из метаданных локальных файлов.

Автоматический поиск текстов обращается к LRCLIB по названию, исполнителю и длительности, когда она известна. Наличие текста зависит от каталога и совпадения метаданных.
