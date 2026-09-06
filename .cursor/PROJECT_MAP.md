# Карта проекта PrimeGramFork

Форк [Primeevokak/PrimeGram](https://github.com/Primeevokak/PrimeGram) → [VibeLandos/PrimeGramFork](https://github.com/VibeLandos/PrimeGramFork).
Официальный клиент Telegram сюда не путать: ядро то же (`org.telegram.*`), пакет приложения другой.

| | |
|---|---|
| Ветка | `dev` |
| Версия | `12.9.0.11` (`APP_VERSION_CODE` 6977) |
| applicationId | `org.telegram.messenger.prime` |
| Лицензия | GPLv2 |
| AGP / Kotlin / NDK | 8.13.2 / 1.9.20 / 27.2.12479018 |
| minSdk / targetSdk / compileSdk | 24 / 35 / 36 |

Сборка: `./gradlew :TMessagesProj_AppStandalone:assembleAfatRelease`  
На телефон без лагов debug: `./gradlew :TMessagesProj_AppStandalone:runDeviceStandalone` (вариант `afatStandalone`, не `afatRelease`). Run-конфиг в IDE: **Phone · afatStandalone**.  
Локально ABI только `arm64-v8a`. На CI (`CI=true`) — все четыре.

Ключи: env `PRIME_APP_ID` / `PRIME_APP_HASH`, иначе `local.properties`, иначе публичная пара в `gradle.properties` (Telegram Web `2496`).

---

## Модули Gradle

Корень `settings.gradle`:

- `:TMessagesProj` — Android **library**: весь клиент, JNI, Python (Chaquopy). Namespace `org.telegram.messenger`.
- `:TMessagesProj_AppStandalone` — Android **application**: тонкая оболочка, `ApplicationLoaderImpl`, flavor `afat`. Namespace `org.telegram.messenger.web`.
- `includeBuild('vpn')` — composite build, зависимость `vpn:sdk`.

`TMessagesProj` зависит от `vpn:sdk`. Приложение зависит от `:TMessagesProj`.

Build types (библиотека и приложение): `debug`, `standalone`, `release` (+ HockeyApp-варианты `HA_*` в библиотеке, для этого форка обычно не нужны).

Рабочий вариант в Android Studio: **`TMessagesProj_AppStandalone`**, flavor **`afat`**, build type **`debug`** или **`release`**.

---

## Дерево корня

```
TMessagesProj/                 # библиотека клиента
TMessagesProj_AppStandalone/   # APK-модуль
vpn/                           # соединение / туннель / прокси (Kotlin)
buildSrc/                      # генерация TL-схемы (Kotlin)
docs/                          # сайт документации (плагины)
plugins-sdk-reference/         # .pyi-заглушки SDK для авторов плагинов
Tools/                         # HTML-утилиты (emoji, binary)
.github/workflows/             # build-release.yml → assembleAfatRelease
gradle.properties              # версия, пакет, публичные APP_ID/HASH, signing debug
Handshake_new.cpp              # справочный патч хендшейка (не модуль сборки)
```

Секреты и мусор: `local.properties` (gitignore), `google-services.json` в репо чужой.

Один готовый бинарник: `TMessagesProj/libs/libgsaverification-client.aar`.
FFmpeg / BoringSSL / tde2e — prebuilt `.a` под ABI в `TMessagesProj/jni/`.

---

## Java: где что лежит

Корень исходников: `TMessagesProj/src/main/java/`

### Ядро Telegram — `org.telegram`

| Пакет | Роль |
|---|---|
| `messenger` | Приложение, аккаунты, файлы, уведомления, БД, контроллеры. Сюда же почти все классы **Prime\*** |
| `messenger/plugins` | Движок Python-плагинов PrimeGram |
| `messenger/voip` | Звонки |
| `messenger/video` | Кодирование/плеер |
| `messenger/browser` | Встроенный браузер (логика) |
| `messenger/blocks` | PrimeGram Blocks |
| `ui` | Экраны (`*Activity`), `LaunchActivity` — точка входа UI |
| `ui/Components` | Виджеты; много **Prime\*** UI |
| `ui/Cells` | Ячейки списков |
| `ui/ActionBar` | Навигация Telegram (`BaseFragment`, темы) |
| `ui/web` | WebView-браузер, история, закладки |
| `ui/Stories` | Истории |
| `ui/iv` | Rich/Instant View редактор |
| `ui/community` | Community UI |
| `tgnet` | MTProto с Java-стороны. `ConnectionsManager` — сеть. `TLRPC.java` — огромная схема |
| `SQLite` | Обёртка над нативным sqlite |
| `PhoneFormat` | Формат номеров |

Точки входа:

- `ApplicationLoader` — `Application`
- `TMessagesProj_AppStandalone/.../ApplicationLoaderImpl.java` — standalone-реализация (обновления, plugin menu)
- `LaunchActivity` — главный Activity
- `ConnectionsManager` — RPC к DC

### Совместимость / чужие пакеты в том же дереве

| Пакет | Роль |
|---|---|
| `com.exteragram.messenger.plugins` | Тонкие алиасы под импорты плагинов exteraGram |
| `org.webrtc` | WebRTC Java API (звонки) |
| `androidx.recyclerview` | Форк RecyclerView внутри клиента |
| `com.google.zxing` | QR |
| `me.vkryl.*` | Утилиты |

---

## Код именно PrimeGram

Префикс **`Prime`** / **`GreyZone`** / **`TgWs`**. Сначала искать их, а не править ядро Telegram.

Настройки форка: `PrimeGramSettingsActivity` (хаб) ← `SettingsActivity`.

### UI (`org.telegram.ui`)

| Класс | Фича |
|---|---|
| `PrimeGramSettingsActivity` | Настройки PrimeGram |
| `GreyZoneActivity` | «Серая зона» (opt-in) |
| `PrimePluginsActivity` / `PrimePluginSettingsActivity` / `PrimePluginInstallDialog` | Плагины |
| `PrimeTgWsActivity` / `PrimeTgWsLogActivity` | Туннель TgWs |
| `PrimeVpnServersActivity` | Список VLESS/VMess/Trojan/SS |
| `PrimeBrowserActivity` | Браузер |
| `PrimeFeedActivity` | Лента каналов |
| `SearchPlusActivity` | Поиск по id / телефону / t.me |
| `PrimePinGateActivity` | PIN-замок |
| `PrimeIconPacksActivity` | Паки иконок |
| `PrimeBlocksActivity` / `PrimeBlockEditorActivity` | Blocks |
| `PrimeArchiveFolderCreateActivity` | Папки архива |

Компоненты: `ui/Components/Prime*.java`, ячейки: `ui/Cells/Prime*.java`.

### Логика (`org.telegram.messenger`)

| Группа классов | Фича |
|---|---|
| `TgWsProxyService` | Локальный прокси/туннель до DC |
| `PrimeCfWorkers` | Cloudflare Workers как маршрут |
| `PrimeVpnGuard` / `PrimeVpnServerStore` / `PrimeBackgroundProxy` | VPN/прокси поверх `vpn:sdk` |
| `PrimeDirectHttps` | Прямой HTTPS-путь |
| `PrimeBigFile*` | Файлы до 8 ГБ (нарезка/сборка) |
| `PrimeWhisper` / `PrimeTranscription` | Локальная расшифровка голосовых |
| `GreyZone` | Ghost, save deleted, обход ограничений чата — **всё выкл. по умолчанию** |
| `PrimeGramPrivacy` | Маскировка телефона, скрытие текста уведомлений |
| `PrimeDecoy*` | Декой-сессия |
| `PrimePin*` | PIN, Keystore, lockout, wipe |
| `PrimeFeedController` / `PrimeFeedReadState` | Лента |
| `PrimeIdentitySearch` | Поиск+ |
| `PrimeTranslator` / `PrimeSendTranslate` | Переводчики |
| `PrimeUpdater` / `PrimeWhatsNew` | Обновления с GitHub, «что нового» |
| `PrimeTweaks` | Оптимизации |
| `PrimeStartupTrace` / `PrimePerfMonitor` | Трассировка холодного старта |
| `PrimeCache` / `PrimeDownloadBoost` | Кэш и загрузки |
| `PrimeFakeEmojiStatus` | Локальные эмодзи-статусы |
| `PrimeSidebarZone` / `PrimeToolbarSettings` | Боковая панель, панель форматирования |
| `PrimeAutoDelete` / `PrimeTempSub*` (UI) | Автоудаление, временные подписки |
| `PrimeEmergencyWipe` / `PrimeSecretWiper` | Аварийная очистка |

Плагины — `org.telegram.messenger.plugins`:

- `PrimePluginsController` — реестр
- `PrimePythonEngine` — Chaquopy
- `PrimePluginHooks` / `PrimePluginXposed` — хуки (AliuHook / LSPlant, API как у Xposed)
- `PrimePluginStore` / `PluginManifest` — файлы `.plugin`

Python SDK (в APK через Chaquopy): `TMessagesProj/src/main/python/`

- `base_plugin.py` — базовый класс плагина
- `client_utils.py`, `android_utils.py`, `hook_utils.py`, `file_utils.py`
- `plugins_manager.py` — совместимость с exteraGram
- `_prime_pip.py` — докачка чистых пакетов с PyPI
- `_prime_loader.py`, `_sdk_version.py` (`HOST = "PrimeGram"`)

Документация плагинов: `docs/plugins/`. Справочник типов: `plugins-sdk-reference/`.

---

## Native (`TMessagesProj/jni`)

CMake: `TMessagesProj/jni/CMakeLists.txt`. Общая `.so` собирается из исходников + prebuilt `.a`.

| Каталог / файл | Роль |
|---|---|
| `tgnet/` | MTProto C++ |
| `voip/` | Звонки |
| `ffmpeg/{abi}/` | Prebuilt ffmpeg/dav1d/vpx |
| `boringssl/` | TLS |
| `tde2e/` | E2E |
| `whisper/` | whisper.cpp + JNI `prime_whisper_jni.cpp` |
| `rlottie/` `opus/` `sqlite/` `mozjpeg/` `exoplayer/` | Медиа и БД |
| `TgNetWrapper.cpp` `jni.c` `NativeLoader.cpp` | JNI-мост |

Модели Whisper в git **нет** — качаются на устройство.

---

## Модуль `vpn/` (composite)

Отдельный Gradle (`vpn/settings.gradle.kts`), имя `vpn`:

| Модуль | Роль |
|---|---|
| `:sdk` | Фасад `VpnSDK` — то, что видит клиент |
| `:tunnel` | Туннель, `AwgBackendAdapter` (AmneziaWG) |
| `:proxy` | `XrayProxy` |
| `:network` | HTTP API конфигов/регистрации/апдейтов |
| `:base` | UI-стейт/навигация VPN-слоя |
| `:utils` | Общие утилиты |

Экраны списка серверов и TgWs живут в Java-клиенте (`PrimeVpnServersActivity`, `PrimeTgWsActivity`), не в `vpn/base`.

---

## Ресурсы и манифесты

- `TMessagesProj/src/main/res/` — layout, drawable, strings ядра
- `TMessagesProj/src/main/AndroidManifest.xml` — манифест библиотеки
- `TMessagesProj/config/release/AndroidManifest_standalone.xml` — манифест standalone APK
- `TMessagesProj/config/release.keystore` — debug/release подпись из `gradle.properties` (не Play-ключ)

---

## CI и скрипты сборки

- `.github/workflows/build-release.yml` — JDK 17, Python 3.11, NDK 27.2, секреты `PRIME_APP_ID`/`PRIME_APP_HASH`
- `build_primegram.bat`, `build-release.ps1`
- `Dockerfile`

`buildSrc` генерирует Java-классы из TL JSON-схемы Telegram. Не путать с рантаймом `tgnet`.

---

## Как искать правку

1. Фича PrimeGram → класс `Prime*` / `GreyZone*` / `TgWs*` (таблица выше).
2. Экран настроек → `PrimeGramSettingsActivity`, не `SettingsActivity` (там только вход).
3. Сеть до DC → `ConnectionsManager` + native `tgnet` + `TgWsProxyService` / `vpn`.
4. Плагин → `messenger/plugins` + `src/main/python`.
5. UI чата/списка как в официальном Telegram → `org.telegram.ui.*` без префикса Prime; менять осторожно, это апстрим.

Комментарии с префиксом `// PrimeGram:` помечают расхождения с Telegram — при мерже апстрима смотреть их в первую очередь.
