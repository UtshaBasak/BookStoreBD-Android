# BookStoreBD for Android

[![CI](https://github.com/UtshaBasak/BookStoreBD-Android/actions/workflows/ci.yml/badge.svg)](https://github.com/UtshaBasak/BookStoreBD-Android/actions/workflows/ci.yml)

The native Android app for [BookStoreBD](https://bookstorebd-loum.onrender.com), a marketplace for new and second-hand books in Bangladesh. It is built in Kotlin with Jetpack Compose and uses the same API as the website ([UtshaBasak/BookStoreBD](https://github.com/UtshaBasak/BookStoreBD)).

> Work in progress. Signed APK files will be published on this repository's [Releases](https://github.com/UtshaBasak/BookStoreBD-Android/releases) page.

## What it will do

The app follows the website, in phases:

1. **Shop:** browse, Bangla–English search, book details, wishlist, cart and checkout, with sign-in.
2. **Account and orders:** profile, order tracking, cancellations and returns, reviews.
3. **Notifications and chat:** live updates, push notifications and buyer–seller chat.
4. **Selling:** listing books, managing stock, prices and orders, payouts.
5. **Native extras:** ISBN barcode scanning, app links, shortcuts and larger-screen layouts.

Light and dark themes, English and Bangla, TalkBack support and offline browsing are part of every phase.

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Kotlin 2.4 |
| UI | Jetpack Compose, Material 3, Navigation 3 |
| Architecture | MVVM with unidirectional data flow, coroutines and Flow |
| Dependency injection | Hilt |
| Network | Retrofit, OkHttp, kotlinx.serialization |
| Storage | Room (offline cache), DataStore (settings), Android Keystore (session) |
| Background work | WorkManager |
| Tests | JUnit, MockK, Turbine, Robolectric, Roborazzi |
| Code quality | ktlint, detekt, Android Lint |
| Build | Gradle 9 with Kotlin DSL, a version catalog and convention plugins |

## Project structure

```text
app/                  The application: activity, navigation, flavours
build-logic/          Gradle convention plugins shared by every module
core/common           Coroutine dispatchers and shared helpers
core/model            Plain Kotlin models
core/designsystem     Theme (the website's colours and Plus Jakarta Sans), components
core/testing          Test rules and helpers
config/               detekt and Compose compiler settings
keystore/             The shared debug signing key (public by design)
```

Feature modules (`feature/*`) and the network, database and data layers (`core/*`) are added phase by phase.

## Building

Requirements: JDK 21 and the Android SDK (platform 37), with `ANDROID_HOME` set. Android Studio is not needed.

```bash
./gradlew assembleGithubDebug        # debug APK: app/build/outputs/apk/github/debug/
./gradlew test                       # unit and UI tests
./gradlew ktlintCheck detekt lint    # code style, code smells and Android Lint
./gradlew assembleGithubRelease      # minified release APK (unsigned without a keystore)
```

There are two distribution flavours: `github`, the APK published on GitHub Releases with its own update check, and `play`, prepared for the Play Store.

## Releases

Releases are built and signed by GitHub Actions when a version tag such as `v1.0.0` is pushed. The workflow checks that the tag matches the version in `app/build.gradle.kts`, verifies the APK's signing certificate, and publishes the APK with its SHA-256 checksum.

The release signing certificate's SHA-256 fingerprint is:

```text
11:39:B7:92:CA:48:CF:12:88:0D:10:64:F3:40:19:DC:7F:8B:F3:94:34:B0:1A:45:DA:0C:07:06:81:FA:A5:B9
```

## Privacy

The app contains no analytics or tracking. It follows the website's [privacy policy](https://bookstorebd-loum.onrender.com/privacy).

## License

[MIT](LICENSE). Plus Jakarta Sans is used under the SIL Open Font License.
