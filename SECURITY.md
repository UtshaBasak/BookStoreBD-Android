# Security Policy

BookStoreBD for Android holds people's sessions, delivery addresses and phone
numbers, so security reports are treated as a priority. Thank you for
reporting responsibly.

## Supported versions

Security fixes go into the latest release on the
[Releases](https://github.com/UtshaBasak/BookStoreBD-Android/releases) page and
the current `main` branch. The app offers each new release from inside the app,
so updating is one tap. Older releases and forks are not maintained.

The website and the API the app uses live in
[UtshaBasak/BookStoreBD](https://github.com/UtshaBasak/BookStoreBD), which has
its own [security policy](https://github.com/UtshaBasak/BookStoreBD/security/policy).
A problem in the server is best reported there; if you are not sure which side
it belongs to, either repository is fine.

## Reporting a vulnerability

**Please do not open a public issue, discussion or pull request for a security
problem.** Report it privately, in either of these ways:

1. **GitHub (preferred):** go to the
   [Security tab](https://github.com/UtshaBasak/BookStoreBD-Android/security) and
   choose **Report a vulnerability**. This opens a private advisory that only
   you and the maintainer can see.
2. **E-mail:** <support.utsha@gmail.com>, with "Security (Android)" in the
   subject.

Include what you can of:

- what the problem is, and what an attacker could do with it;
- the steps to show it, and the app version (Account → bottom of the screen);
- the Android version and phone, if it matters;
- whether you think it has been used against anyone.

## What to expect

- An acknowledgement once the report has been read.
- An assessment of how serious it is, and what will be done about it.
- A fix with the priority its severity calls for, a new release, then a
  published advisory crediting you, unless you would rather not be named.

The project has a single maintainer, so no fixed response time is promised.
Every private report is read, and a follow-up is welcome if a reply seems
overdue.

## In scope

- The app's code in this repository, and the APKs published on its Releases
  page
- Ways to read another app's or person's data through the app, or to read the
  app's session, cache or settings from outside it
- Weaknesses in how the app stores or renews its session (the Keystore-backed
  refresh token and the device-signed renewal)
- Ways to make the app install an update that did not come from this
  repository's releases
- The CI and release workflows in `.github/workflows`

## Out of scope

- Denial of service and load testing of the live server. It runs on a free
  hosting plan; please do not attempt to overwhelm it.
- Attacks that need a rooted phone, a debugger attached, or physical access to
  an unlocked phone
- Social engineering, phishing, or physical attacks on people
- Reports from automated scanners without a demonstrated impact
- Problems in Android itself, Google Play services, or other third-party
  services rather than in how this app uses them

## Verifying an APK

Every release APK is signed with the same certificate. Its SHA-256 fingerprint
is:

```text
11:39:B7:92:CA:48:CF:12:88:0D:10:64:F3:40:19:DC:7F:8B:F3:94:34:B0:1A:45:DA:0C:07:06:81:FA:A5:B9
```

You can check a downloaded APK with `apksigner verify --print-certs` from the
Android SDK build tools, and its file against the SHA-256 checksum published
with each release.

## Safe harbour

Good-faith research that follows this policy will not be treated as an attack.
Please:

- test only with your own accounts, or with the demo accounts of a local copy
  of the [website and API](https://github.com/UtshaBasak/BookStoreBD#running-with-docker);
- not access, keep or change other people's data beyond the minimum needed to
  show the problem;
- give a reasonable time to fix it before telling anyone else.
