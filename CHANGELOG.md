# Changelog

Запись изменений **PrimeGramFork** (не апстрим Telegram и не весь PrimeGram).  
Формат близок к [Keep a Changelog](https://keepachangelog.com/). Даты — день коммита в `dev`.

Правило для агентов и людей: **каждая пользовательская правка сразу дописывается сюда** (см. `.cursor/rules/changelog.mdc`). История до появления этого файла — в `git log`.

## Unreleased

### VPN / VLESS-ключи (экран «Серверы»)

Список своих узлов (`PrimeVpnServersActivity` + `PrimeVpnServerStore` + `VpnSDK`) приведён к сценарию Happ / v2rayNG, а не к одному однострочному полю.

**Добавление ключей**

- Многострочное поле: пачка `vless://` / `vmess://` / `trojan://` / `ss://` / `socks://` из заметок или буфера. Лишний текст вокруг ссылок отбрасывается.
- Имя сервера: из `#remark`, у vmess — из поля `ps`; если пусто — `PROTOCOL host`.
- Дубликаты (одна ссылка без `#фрагмента`) не добавляются повторно.
- Кнопка «Вставить из буфера», если в клипборде уже есть ключи или URL подписки.
- В диалоге кнопка «Буфер» подставляет клипборд, не закрывая окно.
- URL подписки (`http://` / `https://` без share-ссылок в самой строке): GET тела, при необходимости decode base64, разбор ключей из ответа (лимит тела 2 МБ).
- Долгий тап по серверу: копировать ключ, проверить один узел, удалить.

**Проверка задержки (пинг)**

Три режима (выбор радиокнопками, запоминается в настройках):

| Режим | Что измеряет | Влияние на текущий туннель |
|---|---|---|
| TCP | `connect()` к `host:port` из ссылки | нет, параллельно |
| TLS | TLS handshake на том же порту (сертификат не проверяется — это только RTT) | нет, параллельно |
| HTTP GET | GET `https://www.gstatic.com/generate_204` (fallback на HTTP) **через** SOCKS поднятого xray с этим ключом | да: пока идёт прогон, текущий xray снимается и после восстанавливается |

- «Проверить все» и проверка одного узла пишут `lastPingMs` в список (персистентно).
- HTTP-пробы сериализованы замком с `setCustomVlessConfig`, watchdog xray на время прогона останавливается, прежний конфиг поднимается обратно.
- Схемы ссылок нормализуются без учёта регистра (`VLESS://` = `vless://`).

**Файлы:** `PrimeVpnServersActivity.java`, `PrimeVpnServerStore.java`, `vpn/sdk/.../VpnSDK.kt`.

### Сборка на реальный телефон без лагов debug

- Gradle-задача `:TMessagesProj_AppStandalone:runDeviceStandalone` — `installAfatStandalone` + запуск лаунчера на подключённом устройстве.
- Shared run-конфиг `.run/Phone afatStandalone.run.xml` (тип Gradle) на эту задачу.

Почему не `afatRelease`: в модуле приложения `release` оставлен `debuggable true` (обход предупреждений Play Protect на sideload). ART на debuggable-процессе не включает нормальный JIT — отсюда лаги. `standalone` — minify + `debuggable false` + в библиотеке `DEBUG_VERSION=false`. Локально ABI только `arm64-v8a`.

### Документация и правила агента

- Этот файл.
- `.cursor/rules/changelog.mdc` — всегда обновлять changelog вместе с правкой.
- В `project.mdc` — ссылка на changelog.
