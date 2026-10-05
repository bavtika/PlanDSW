# Plan DSW

An Android app for the class schedule of DSW University (harmonogramy.ideis.pl). Free, made by students for students.

## Install

1. Open the [latest release](https://github.com/bavtika/PlanDSW/releases/latest) and download `PlanDSW-<version>.apk`.
2. Open the file on your phone and allow installing apps from your browser or file manager.
3. From then on the app updates itself: when a new version is out, a "Version … is available" banner appears.

## Features

- Languages: English, Polski, Русский, Українська (chosen on first launch, changeable in Settings).
- Schedule setup: Wydział → Kierunek → Nabór → Tryb studiów, then "My groups" (Cw2S, Lab2AS, ANG2S…).
- Subjects tab: every subject of the semester with progress; tap one to see all its class dates, filterable by class type.
- Schedule changes (moved, cancelled, new classes, room changes) shown as a banner in the app.
- Home screen widget; while it is added, the schedule refreshes in the background every 6 hours.
- Teacher and room schedules: the full teacher list (your teachers first) with an instant filter.
- Works offline, dark theme.
- Grades from USOS in their own tab, with a badge for new grades (sign in with the university account).

## How it works

All data comes straight from the university website, there is no backend (`app/src/main/java/app/plandsw/data/IdeisApi.kt`):

- setup wizard: combo box `itemsInfo` on `/Plany/ZnajdzTok?Ukryj=True`, fields of study via `POST /Plany/ZnajdzTokKierunekCombo`,
  study programme search via `GET /Plany/ZnajdzTok?TrybStudiowId=&WydzialId=&naborId=&kierunekId=&specjalnoscId=`;
- programme schedule: `POST /Plany/PlanyTokowGridCustom/{id}` (`DXCallbackName=gridViewPlanyTokow`,
  `__DXCallbackArgument=c0:KV|2;[];GB|35;14|CUSTOMCALLBACK15|[object Object];`, `parametry=Y-M-D;Y-M-D;5;{id}`);
  the semester is fetched in 4 parallel chunks and the grid HTML is parsed by `GridParser`;
- teacher / room: schedule page → the same callback → iCal export;
- teacher list: `POST /Plany/ZnajdzProwadzacego` with `Nazwisko=%`, cached for a week.

## Grades from USOSweb

Grades are read from the USOSweb "moje oceny" page (`usosweb.ideis.pl/kontroler.php?_action=dla_stud/studia/oceny/index`).
The USOS API (`usosapps.ideis.pl`) is not used: it requires a consumer key and registration is closed.

- Sign-in happens in an embedded WebView (`UsosLoginScreen`): `logowaniecas/index` → CAS `login.wsb.pl` → Microsoft with MFA.
  Sign-in is complete once the WebView is back on `usosweb.ideis.pl` and not on `logowaniecas`.
- Session cookies are passed from `CookieManager` to OkHttp by `WebViewCookieJar`.
- An expired session is renewed silently (`UsosSilentLogin`): an invisible WebView opens
  `login.wsb.pl/cas/clientredirect?client_name=SAML2CASClient&service=…logowaniecas/index` (skipping the
  "Zmiana logowania" page) and returns to USOSweb using the saved Microsoft cookies.
- Without a session the page answers 403 "Wymagane zalogowanie" → `UsosLoginRequired` → "USOS session expired".
- Parsing: `UsosGradesParser`: `usos-frame#oceny` → `usos-frame-section` (term) → subject rows;
  the term code (`cdyd_kod`) is taken from the "szczegóły" dialog URL. A markup sample with made-up data is in
  `app/src/test/resources/usos/oceny.html`.
- New grades: `GradesDiff` between the previous and the new cache (`filesDir/grades.json`), checked on launch
  when the cache is older than an hour, and on the grades tab.
- WebView cookies (`app_webview/`) and `grades.json` are excluded from Android backups
  (`res/xml/data_extraction_rules.xml`, `res/xml/backup_rules.xml`).

## Development

```bash
./gradlew testDebugUnitTest              # unit tests
./gradlew testDebugUnitTest -Plive       # plus tests against the live website
./gradlew installDebug                   # debug build (app.plandsw.debug)
```

## Releasing

```bash
git tag -a v1.6.0 -m "Plan DSW 1.6.0

- what's new (this text is shown under \"What's new\" in the app)"
git push origin v1.6.0
```

GitHub Actions builds a signed APK and publishes the release. The version code is derived from the tag
(`v1.6.0` → `10600`). The signing key lives in the repository secrets `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD`;
keep an offline backup of the keystore, because without it installed apps cannot be updated.
