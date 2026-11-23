# AUVYX Alpha 2.0 — аудит Spotify и YouTube Music

Проверены два скриншота. Поиск Spotify требовал регистрации приложения у конечного пользователя. YouTube открывал отдельное модальное окно поверх поиска. В этой сборке оба интерфейса удалены.

## Изученные проекты

| Проект и ревизия | Проверенные файлы | Наблюдение и применимость |
| --- | --- | --- |
| [RX Playa](https://github.com/rentalx-wifi/rx-playa/tree/3c874b8), GPL-3.0 | `auth/SpotifyLoginScreen.kt`, `data/spotify/SpotifyLibrary.kt` в jvmSharedMain, `data/sources/YouTubeSource.kt`, `SourceResolver.kt`, `playback/QueueCoordinator.kt`, `LICENSE` | Spotify-вход использует сессию сайта. Каталог использует запросы веб-клиента и резервный REST для некоторых операций. YouTube-источник разделяет поиск и поток. Resolver различает источник записи в очереди и источник потока, учитывает доступность и резервные варианты. QueueCoordinator управляет пользовательской очередью и контекстом воспроизведения раздельно. Применима сама граница между каталогом, очередью и проигрывателем; код и авторизация RX не перенесены. |
| [Mimose](https://github.com/ZhoskiProger/mimose/tree/75570a2), MIT | `src/core/dataService/search.ts`, `fetchers.ts`, `src/pages/AlbumPage.tsx`, `ArtistPage.tsx`, `src/hooks/useStreamLoader.ts`, `src/core/playerStore.ts`, `LICENSE` | Поиск, страницы и получение потока делегируются серверу Mimose. Публичный клиент показывает запросы по провайдерам и типам объектов, но по нему нельзя установить, откуда сервер получает Spotify OAuth или каждый поток. Загрузчик отменяет устаревшие запросы, отдельно возвращает тип и источник потока; Zustand хранит очередь и позицию. Применима модель независимых операций поиска и воспроизведения. |
| [Dotify](https://dotify.fun/en/faq), лицензия исходников не установлена | Официальный сайт, FAQ и список официальных ресурсов | Заявлены поиск, импорт, несколько платформ и сохранение треков. Публичного репозитория приложения или сервера не найдено. Авторизацию, соответствие метаданных потоку и логику очереди по этим страницам установить нельзя. |
| [Lane](https://sklane.com/ru), лицензия исходников не установлена | Официальный сайт, условия и [Lane Devs](https://t.me/s/lane_devs) | Видны заявленные возможности медиатеки, плейлистов и управления звуком. Публичного репозитория не найдено. Реализация Spotify, YouTube Music и серверная часть не подтверждены. |
| [Spotifeur](https://github.com/jean-voila/Spotifeur/tree/964cf25), GPL-3.0 | `data/SpotifyAccount.kt`, `SpotifySearch.kt`, `SpotifyCatalog.kt`, `data/youtube/YouTubeMusic.kt`, `YouTubePages.kt`, `playback/SpotifeurMediaSourceFactory.kt`, `QueuePreloader.kt`, `LICENSE` | Авторизация и каталог используют собственный слой сессии и закрытые протоколы Spotify. Для YouTube есть отдельные поиск, страницы и получение потока. Media3 выбирает фабрику по типу источника, а предзагрузка обрабатывает очередь и ошибки отдельно. Применим принцип явной маршрутизации; закрытые протоколы и нативный движок не переносятся. |
| [Fluidify](https://github.com/Casual76/Fluidify/tree/c6162ad), GPL-3.0 | `auth/SpotifyOAuth.kt`, `backend/SpotifyBackend.kt`, `data/SpotifyApi.kt`, `core/.../playback/PlayQueue.kt`, `LICENSE` | Использует PKCE, но в коде по умолчанию ID librespot. Backend отдельно возвращает результаты и ошибки доступа; каталог имеет методы артистов и альбомов. Очередь моделируется отдельно от проигрывателя, звук связан с librespot. Применимы PKCE с собственным ID и типизированные ошибки. Подход к стороннему Client ID и движку не перенесён. |

## Архитектура этой сборки

- Spotify использует один публичный Client ID AUVYX из конфигурации Gradle. Пользователь видит только вход. Код использует OAuth PKCE в браузере и проверяет state; токен хранится в памяти до завершения сессии. Просроченная сессия требует повторного входа.
- Каталог Spotify вызывает официальный Web API с пагинацией. Управление Spotify Connect идёт через документированные команды Premium. AUVYX показывает состояние, запускает выбранный трек, добавляет в очередь устройства, ставит на паузу, переключает и перематывает. Звук играет на активном устройстве Spotify. Speed Lab, кэш и общая очередь Media3 этот звук не обрабатывают.
- YouTube Music сейчас показывает результаты своего каталога через неофициальный поиск. Кнопка явно открывает внешний YouTube Music. Встроенный диалог и извлечение прямого аудио для старой очереди убраны. Старые закладки открывают оригинальные страницы. Поддерживаемого фонового аудио YouTube внутри AUVYX нет.
- SoundCloud и Speed Lab остались на существующем пути Media3. Проверка воспроизведения на телефоне всё ещё нужна.

## Однократная настройка разработчиком

1. Для разрешённой Spotify-сборки зарегистрировать приложение AUVYX в Spotify Developer Dashboard. Добавить Redirect URI `http://127.0.0.1/callback`.
2. Задать публичный Client ID в переменной `AUVYX_SPOTIFY_CLIENT_ID` среды сборки или в Gradle-свойстве `auvyx.spotifyClientId`. Client Secret не требуется.
3. Для Development Mode у владельца приложения должен быть активный Premium. Добавить тестовые Spotify-аккаунты в allowlist. Лимит новых приложений — пять пользователей. Для удалённого управления нужен Spotify Premium у управляющего аккаунта и активное устройство Spotify.
4. Перед публичной публикацией подтвердить у Spotify разрешённую архитектуру и доступ. Developer Policy отдельно ограничивает объединение Spotify с контентом других сервисов. Раздельные экраны сами по себе не подтверждают соответствие. При отсутствии разрешения публичный мультипровайдерный APK остаётся без Spotify, а официальную интеграцию можно проверять в отдельном Spotify-клиенте в пределах выданных прав.

[Spotify Redirect URIs](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri) · [Режимы доступа](https://developer.spotify.com/documentation/web-api/concepts/quota-modes) · [Spotify Connect API](https://developer.spotify.com/documentation/web-api/reference/start-a-users-playback) · [Политика Spotify](https://developer.spotify.com/policy) · [Политика YouTube](https://developers.google.com/youtube/terms/developer-policies-guide)

## Содержимое архива

В архиве изменённые файлы относительно текущей ветки main. При наложении на предыдущий ZIP Catalog Providers удали оставшиеся старые файлы `app/src/main/java/com/nexus/music/ui/screens/YouTubePlayerDialog.kt` и `app/src/main/java/com/nexus/music/data/catalog/SpotifyCatalog.kt`. Они больше не используются.

Тестовый APK собран с `-Pauvyx.providerReview` и отдельным package ID `com.nexus.music.providerreview`, чтобы установить его рядом с предыдущим APK другой подписи. Данные предыдущей установки остаются в её пакете.
