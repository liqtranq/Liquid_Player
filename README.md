<div align="center">

# Liquid Player

**Музыкальный плеер для Android с расширенными возможностями.**

Локальная музыка · Редактор тегов · Обложки · Конвертация аудио

[![Release](https://img.shields.io/github/v/release/liqtranq/Liquid_Player?style=flat-square&color=E05A32)](https://github.com/liqtranq/Liquid_Player/releases)
![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?style=flat-square)
[![License](https://img.shields.io/badge/License-GPL--3.0--or--later-blue?style=flat-square)](LICENSE)

[Скачать APK](https://github.com/liqtranq/Liquid_Player/releases) · [Сообщить об ошибке](https://github.com/liqtranq/Liquid_Player/issues)

</div>

## О плеере

Liquid Player — музыкальный плеер для Android с расширенными возможностями. Слушайте музыку с устройства, наводите порядок в библиотеке, редактируйте теги и подбирайте обложки в одном приложении.

В оформлении — тёмные поверхности, тёплые оранжевые акценты и элементы, вдохновлённые аудиотехникой. Доступны светлая тема и настройки внешнего вида.

## Возможности

| | Что умеет плеер |
| :--- | :--- |
| **Музыка** | Локальное воспроизведение, очередь, плейлисты, избранное, плавные переходы и таймер сна |
| **Библиотека** | Альбомы, исполнители, жанры, папки, поиск и статистика прослушивания |
| **Теги и обложки** | Редактирование метаданных, заполнение из имени файла, очистка лишнего текста, поиск в Deezer и выбор изображения с устройства |
| **Конвертация** | Экспорт в M4A / AAC или WAV / PCM 16-bit; возможности кодирования зависят от устройства |
| **Тексты песен** | Встроенные тексты, локальные LRC-файлы и дополнительный поиск через LRCLIB |
| **Своя медиатека в сети** | Подключение к Navidrome / Subsonic и Jellyfin |
| **Настройки** | Темы, виджеты, резервное копирование и восстановление |

**Telegram Audio Deck пока экспериментальный:** экран показывает демонстрационные аудио и поддерживает их воспроизведение и загрузку. Подключение личного аккаунта, чтение каналов и «Избранного» ещё не реализованы в интерфейсе.

## Установка

Нужен **Android 11 или новее**. Откройте [релизы](https://github.com/liqtranq/Liquid_Player/releases), выберите версию и скачайте APK. Файлы с `debug` в названии — тестовые сборки; предварительные версии отмечены как *Pre-release*.

Сборки Liquid Player публикуются в этом репозитории. Ссылка на F-Droid исходного проекта не является страницей Liquid Player.

## Архитектура и благодарности

Liquid Player основан на [PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS), который происходит от PixelPlayer. Значительная часть архитектуры и базового кода унаследована от этих проектов: Jetpack Compose и Material 3 для интерфейса, Media3 для воспроизведения, Room для библиотеки, Hilt для зависимостей и DataStore для настроек.

В Liquid Player развиваются собственное оформление и дополнительные инструменты для работы с музыкой. Спасибо **Theo Vilardo (@theovilardo)**, **@lostf1sh** и всем участникам исходных проектов за основу.

Исходное пространство имён `com.lostf1sh.pixelplayeross` сохранено для совместимости; это техническое имя пакета, а не имя автора Liquid Player.

## Сборка из исходников

Требуются **JDK 21** и **Android SDK 37**. Укажите путь к SDK в `local.properties` или через `ANDROID_HOME`.

```sh
git clone https://github.com/liqtranq/Liquid_Player.git
cd Liquid_Player
./gradlew :app:assembleDebug -Ppixelplayer.enableAbiSplits=false
```

На Windows используйте `./gradlew.bat`. APK появится в `app/build/outputs/apk/debug/`.

Проверки перед изменениями и публикацией описаны в [CONTRIBUTING.md](CONTRIBUTING.md) и [docs/RELEASE.md](docs/RELEASE.md).

## Лицензия

[GPL-3.0-or-later](LICENSE). Исходные уведомления об авторских правах сохранены: PixelPlayerOSS, Copyright (C) 2026 Theo Vilardo.

[Компоненты и благодарности](THIRD_PARTY_NOTICES.md) · [Конфиденциальность](PRIVACY.md) · [Безопасность](SECURITY.md)
