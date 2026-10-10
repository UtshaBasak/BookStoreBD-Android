# BookStoreBD for Android: working notes

## What this is

A native Android client for BookStoreBD, an online marketplace for new and
second-hand books in Bangladesh. The backend already exists and is live; this
app talks to it over HTTPS like the website does.

- Live site and API: https://bookstorebd-loum.onrender.com (API under `/api`)
- Backend and website source: `A:\MernBookstore` (GitHub: UtshaBasak/BookStoreBD)
- The API contract: `A:\MernBookstore\server\shared\api.d.ts` (every request and
  response shape) and the "API reference" section of `A:\MernBookstore\README.md`
- Design tokens to match: `A:\MernBookstore\client\src\index.css` (violet
  `#6d28d9`, sunset orange `#ff5c35`, yellow `#facc15`, Plus Jakarta Sans;
  dark theme values under `:root[data-theme='dark']`)

## Stack (industry standard, no shortcuts)

- Kotlin, Jetpack Compose, Material 3, single activity, edge-to-edge
- MVVM with unidirectional data flow; Coroutines and Flow
- Hilt for dependency injection
- Retrofit + OkHttp + kotlinx.serialization; Coil for images
- Room for the offline cache; DataStore for preferences
- Tokens encrypted with the Android Keystore (no plain-text secrets on disk)
- Navigation Compose with type-safe routes; Paging 3 for long lists
- WorkManager for background work
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`)
- R8 minification and resource shrinking for release; baseline profile
- ktlint/detekt, JUnit, MockK, Turbine; Compose UI tests via Robolectric
- GitHub Actions: lint, test and build the APK on every push

## How the app is built and shipped

- No Android Studio: everything is built from the command line with the Gradle
  wrapper, a JDK and the Android SDK command-line tools.
- Distributed as a signed release APK on GitHub Releases. Play Store comes later.
- The release keystore and its passwords never enter the repository
  (`keystore.properties` and `*.jks` are git-ignored).

## Quality bar (same as the website)

- Accessibility: TalkBack labels, 48dp touch targets, font scaling, contrast
- Light and dark themes; English and Bangla
- Graceful offline and slow-network states; no blank screens
- Every feature covered by tests; CI must stay green
- Privacy: no tracking; the same rules as the website's privacy policy

## Conventions

- Commits carry only the author's name: no co-author or AI attribution lines.
- Commits are SSH-signed automatically (global git config); never disable it.
- Never use `git stash` or `git checkout <path>`: both have silently lost work.
- Never print or commit secrets (keystore passwords, API keys, `.env` values).
- Kill only processes this work started, by PID; never by image name.
- Plain, positive, professional wording in everything published.
