# CantoTrack Mobile

Your tickets, the clock and your hours, on your phone.

The Android app of the [CantoTrack](https://github.com/szabolevi98/cantotrack)
issue tracker, for the part of the work that happens away from a desk. The web
is where work is planned; the phone is where it is started, stopped and noted
down. The app works through CantoTrack's JSON API: a person signs in with their
own account, starts the clock on a ticket, and stops it later — on the phone, on
the web or from the notification shade, as it is one and the same clock.

![CantoTrack Mobile: signing in, the tickets with the clock running, a ticket, the week's hours](docs/cover.png)

Written in Kotlin with Jetpack Compose and Material 3, in the web app's colours,
light and dark. Runs on Android 8.0 and later.

## What it does

### Tickets

- **Mine, or every open one**: the first tab lists the open tickets assigned to
  you, or with one tap every open ticket in the projects you can see, a page at a
  time, with a search over titles and keys. Each shows its type, priority,
  status, assignee, and the hours logged and left.
- **A ticket**: its facts (assignee, due date, estimate, logged, left), labels,
  epic and sprint, its description and its comments. The link at the top opens
  it on the web.
- **Status**: a tap on the status lists the project's columns. A move the
  project's workflow does not allow, or a change somebody else made in the
  meantime, is refused by the server, and the app says why in the server's own
  words.
- **Comments**: written at the bottom of the ticket, above the keyboard.

### The clock

CantoTrack keeps one running clock per person, on the server. The app shows and
drives that same clock:

- **Start** it on a ticket. One already running on another ticket is stopped and
  logged first, as on the web, and the app says how much it logged.
- **While it runs**, it sits at the top of both tabs, counting, and in the
  notification shade, where the system keeps counting with the app closed. The
  shade has a **Stop** button of its own.
- **Stop** it with a note and it becomes a worklog on the day it started; under
  a minute, nothing is logged. Or throw it away without logging.
- Started or stopped **on the web**? The app reads the clock again each time it
  comes to the front. It counts from how long the server says the clock has run,
  so a phone clock that is minutes off does not show the wrong time.

No background service runs for any of this: the clock is on the server, and the
counter in the shade is the notification's own.

### Hours

- **Log time by hand**: how long (in quarter hours, or 15 minutes to 2 hours with
  one tap), which day (today, yesterday or any earlier day) and a note.
- **The week**: the second tab is your hours a week at a time, day by day with a
  total for each day and the week, and weekdays without hours shown as empty.
  Step back through earlier weeks. A tap on an entry opens its ticket; holding it
  deletes it.
- **Today's total** is in the header of the tickets tab.

### Security

- Everybody signs in with **their own CantoTrack account**: email address and
  password. The server makes a personal access token for the device; the phone
  keeps it encrypted with an Android Keystore key that never leaves the device,
  and the app's data is left out of backups.
- **Two-step sign-in**: a person who has turned it on in CantoTrack is asked for
  the code from their authenticator app after the password, or one of their
  recovery codes. Without it there is no extra step.
- The sign-in shows on the web, on **Profile → Access tokens**, with an *App*
  badge and the device's name, and can be revoked there. A new password, a reset
  one or **Sign out everywhere else** signs the phone out too; so does a
  deactivated account. The app then goes back to the sign-in screen.
- Wrong passwords and codes count towards the same limit as the web's login form.
- The release build talks HTTPS only. Plain HTTP is allowed in the debug build,
  for a local server reached from the emulator (`10.0.2.2`).

### Languages

Hungarian and English, following the phone's language; from Android 13 the
app's language can be chosen on its own in the system settings. The server's
error messages come in the language set on your CantoTrack profile.

## Screens

| Sign-in | Tickets, the clock running | A ticket |
|---|---|---|
| ![Sign-in](docs/screenshots/login.png) | ![Tickets](docs/screenshots/tickets.png) | ![A ticket](docs/screenshots/ticket.png) |

| The week's hours | Dark |
|---|---|
| ![Hours](docs/screenshots/hours.png) | ![Dark](docs/screenshots/dark.png) |

## Installing it

1. Download the latest `cantotrack-mobile-*.apk` from the
   [releases](https://github.com/szabolevi98/cantotrack-mobile/releases) page.
2. Install it on the phone. The first time, Android asks to allow apps from
   unknown sources.
3. On first start, enter the CantoTrack server's address (say
   `tracker.example.com`), your email address and your password — and, with
   two-step sign-in on, the code from your authenticator app.

It needs **Android 8.0 (API 26)** or later, and a CantoTrack server with the
app's endpoints (`/api/v1/auth/login`, `/api/v1/auth/logout`, `/api/v1/timer`) —
any version from 26 September 2026 on, with `php database/migrate.php` run.

## Building it

| Layer | |
|---|---|
| Language, UI | Kotlin 2.4, Jetpack Compose, Material 3 in the web app's colours |
| Network | OkHttp 5 and kotlinx.serialization |
| Storage | DataStore; the token encrypted with an Android Keystore AES-GCM key |
| Build | Gradle 9.7, Android Gradle Plugin 9.4, compileSdk 37, minSdk 26 |

```
./gradlew assembleDebug                 # a debug build for a local server (from the emulator: http://10.0.2.2/cantotrack/web)
./gradlew testDebugUnitTest             # the API client against a mock server, and the formatting
./gradlew connectedDebugAndroidTest     # on a running emulator: signing in, with and without two-step sign-in
./gradlew assembleDebug -PminifyDebug   # a debug build shrunk by R8 exactly like the release
```

A signed release build needs a `keystore.properties` in the project root (never
committed):

```properties
storeFile=C:/path/to/release.jks
storePassword=...
keyAlias=cantotrack-mobile
keyPassword=...
```

Without it, `./gradlew assembleRelease` makes an unsigned APK.

### Layout

```
app/src/main/java/net/levente/cantotrack/mobile/
  data/api/        the CantoTrack API client, its models and errors
  data/session/    sign-in, the encrypted token, expiry
  data/timer/      the running clock, its notification and the notification's Stop
  ui/home/         the tickets tab
  ui/ticket/       a ticket, its status, comments and logging time
  ui/hours/        the week's hours
  ui/login/, ui/settings/
  ui/components/   shared pieces: header, card, the clock's card and dialog
  ui/theme/        the web app's design tokens, light and dark
app/src/test/          unit tests
app/src/androidTest/   tests on a device or emulator
```

The API is described in the CantoTrack repository's README, under
[The API](https://github.com/szabolevi98/cantotrack#the-api).

## License

[GNU AGPLv3](LICENSE), like CantoTrack. © 2026 [szabolevi98](https://github.com/szabolevi98)
