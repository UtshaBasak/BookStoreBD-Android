# Contributing to BookStoreBD for Android

Thank you for contributing. This guide covers what you need to get a change
merged.

## Table of contents

- [Getting set up](#getting-set-up)
- [Branching](#branching)
- [Commit messages](#commit-messages)
- [Before you open a pull request](#before-you-open-a-pull-request)
- [Code style](#code-style)
- [Project conventions](#project-conventions)
- [Working with the API](#working-with-the-api)
- [Reporting bugs](#reporting-bugs)
- [Sharing ideas](#sharing-ideas)

---

## Getting set up

You need JDK 21 and the Android SDK command-line tools with platform 37, with
`ANDROID_HOME` set. Android Studio is optional.

```bash
git clone https://github.com/UtshaBasak/BookStoreBD-Android.git
cd BookStoreBD-Android
./gradlew assembleGithubDebug
adb install -r app/build/outputs/apk/github/debug/app-github-debug.apk
```

Debug builds use the live API. To work against a local copy of the
[website and API](https://github.com/UtshaBasak/BookStoreBD), see
[Using a local server](README.md#using-a-local-server).

---

## Branching

Branch off `main` and use a short, descriptive name prefixed with the kind of
change:

| Prefix | Use for | Example |
| --- | --- | --- |
| `feat/` | A new capability | `feat/order-tracking` |
| `fix/` | A bug fix | `fix/cart-badge-count` |
| `refactor/` | Restructuring without behaviour change | `refactor/session-manager` |
| `docs/` | Documentation only | `docs/wiki-install-guide` |
| `chore/` | Tooling, dependencies, CI | `chore/bump-compose-bom` |

---

## Commit messages

Write messages in the imperative mood, with a subject line under 72 characters:

```text
fix: keep the cart badge in step after checkout

The badge read the cart before the server cleared it, so it showed the
old count until the next refresh. It now follows the cart's own flow.
```

Prefixes that map onto the branch table above (`feat:`, `fix:`, `refactor:`,
`docs:`, `chore:`, plus `build:`, `ci:` and `test:`) keep the history readable.

---

## Before you open a pull request

Run these checks locally. CI runs the same ones and blocks the merge if any
fails:

```bash
./gradlew ktlintCheck detekt                         # code style and static analysis
./gradlew lintGithubDebug lintPlayDebug lintDebug    # Android Lint (warnings are errors)
./gradlew test                                       # unit and Compose UI tests
./gradlew assembleGithubDebug assembleGithubRelease assemblePlayRelease
```

`./gradlew ktlintFormat` fixes most style findings automatically.

Then confirm:

- New behaviour has a test, and a fixed bug has a regression test
- Every new user-facing string is in `values/strings.xml` **and**
  `values-bn/strings.xml`
- New screens work with TalkBack, at large font sizes, and in both themes
- No keystore, `keystore.properties`, `local.properties` or other secret is
  staged
- A changed request or response matches the website's contract in
  [`server/shared/api.d.ts`](https://github.com/UtshaBasak/BookStoreBD/blob/master/server/shared/api.d.ts)

Open the PR against `main` and fill in the template.

---

## Code style

- **Kotlin:** official style, enforced by ktlint through
  [`.editorconfig`](.editorconfig). detekt's rules are in
  [`config/detekt/detekt.yml`](config/detekt/detekt.yml).
- **Warnings are errors:** Kotlin compiler warnings and Android Lint warnings
  fail the build. Fix the cause rather than suppressing it; where a suppression
  is genuinely needed, add a comment explaining why.
- **Compose:** stateless screen composables that take UI state and callbacks;
  state lives in the ViewModel. Previews and tests use the same composables.
- **Tests:** JUnit 4 with Robolectric for Android classes and Compose UI tests,
  Turbine for flows, MockK for collaborators, and MockWebServer for the network
  layer. Shared helpers are in `core/testing`.
- **Formatting:** 4-space indent, LF line endings, a final newline.
  [`.gitattributes`](.gitattributes) keeps line endings consistent on every
  platform.

---

## Project conventions

### Modules

- Feature code goes in `feature/<name>`, applying the
  `bookstore.android.feature` convention plugin. Features depend on `core`
  modules, never on each other; navigation between them goes through the routes
  in `core/ui/navigation/Routes.kt`.
- Shared Gradle configuration belongs in `build-logic`, and every version in
  [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
- Resource names start with the module's prefix (`feature_cart_…`), which
  Android Lint checks.

### Data and state

- Repositories in `core/data` are the only way to the network and the cache.
  They return `Outcome` values, never throw for expected failures, and serve the
  saved copy first where one exists.
- ViewModels expose one `StateFlow` of UI state, plus one-off events where a
  screen needs them (a snackbar, a navigation).
- Account data in the cache is stored under the account's id, so it is removed
  on sign-out.

### Security and privacy

- Tokens never touch disk in plain text: the refresh token goes through
  `SecretCipher`, and the access token stays in memory.
- No analytics, advertising or tracking libraries, and nothing sent to a third
  party that the website does not already use.
- Plain HTTP is allowed in debug builds only, and only to a local server.

---

## Working with the API

The app uses the website's API under `/api/v1`. Request and response shapes are
defined in the website repository's
[`server/shared/api.d.ts`](https://github.com/UtshaBasak/BookStoreBD/blob/master/server/shared/api.d.ts)
and its README's API reference, and versioning rules are in its
[`docs/API-VERSIONING.md`](https://github.com/UtshaBasak/BookStoreBD/blob/master/docs/API-VERSIONING.md).

If a feature needs a change on the server, open an issue or pull request in the
[website repository](https://github.com/UtshaBasak/BookStoreBD) first, so both
clients keep working.

---

## Reporting bugs

Open an issue using the [bug report template](.github/ISSUE_TEMPLATE/bug_report.md)
and include the app version (Account → bottom of the screen), your Android
version and phone, the steps, and what you expected. For anything
security-sensitive, do not file a public issue: report it privately as
[SECURITY.md](SECURITY.md) describes.

---

## Sharing ideas

New features, upgrades and better ways of doing things are welcome in
[Ideas](https://github.com/UtshaBasak/BookStoreBD-Android/discussions/categories/ideas).
A short form asks what the idea is, who it helps and why it matters, and
everyone can discuss and upvote it. The most wanted ideas join the
[roadmap](README.md#coming-soon).

---

## Code of conduct

Everyone taking part is expected to follow the
[Code of Conduct](CODE_OF_CONDUCT.md). Report unacceptable behaviour to
<support.utsha@gmail.com>.
