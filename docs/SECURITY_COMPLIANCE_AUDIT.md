# Security & Compliance Audit — TKOlympApp (client apps)

**Date:** 2026-11-05 · **Scope:** `androidApp`, `appRes`, `composeApp`, `shared`, `iosApp`, CI, git history.
**Out of scope:** the club GraphQL API (`tkolymp.cz/graphql`), which we cannot change. Items that only the API
or the club as an organisation can address are listed separately at the end.

> This is an engineering review, not legal advice or an ISO certification. ISO/IEC 27001 certifies an
> organisation's information security management system (ISMS), not an app; what follows maps the app's
> technical controls onto the relevant Annex A controls and OWASP MASVS so that gaps are visible.

## Summary

The app's technical security baseline is good: encrypted token/data storage, certificate pinning, backups
disabled, an allowlist HTML sanitizer plus CSP in the WebView, validated deep links, immutable
`PendingIntent`s, R8 shrinking, signing secrets kept out of the repo, and a logout that wipes cached personal
data. No secrets were found in the code or git history.

The main gaps were on the **privacy/legal** side: third-party SDK data collection that started before (and
regardless of) the consent screen, push subscriptions that leaked one user's group messages to the next user
of a shared device, and a consent flow whose wording and legal basis don't line up with GDPR.

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| H1 | Firebase Analytics + Crashlytics collected data before/without consent | High | **Fixed** |
| H2 | FCM cohort topics stayed subscribed after logout | High | **Fixed** |
| H3 | Error-reporting choice can't be changed/withdrawn after onboarding | High | **Fixed** |
| M1 | Privacy policy accepted as "consent" and used as a gate | Medium | **Fixed** (app); policy text: club |
| M2 | Error reports described as "anonymous", toggle pre-enabled | Medium | **Fixed** (option 1) |
| M3 | Third-party recipients (tobiso.com, GitHub, Google, image hosts) | Medium | **Partly fixed** (in-app notice); policy + DPA: club |
| M4 | iOS logged in release builds (NSLog ignored `isDebug`) | Medium | **Fixed** |
| M5 | No iOS privacy manifest (`PrivacyInfo.xcprivacy`) | Medium | **Fixed** |
| M6 | Children's data — age of digital consent (15 in CZ) | Medium | Mitigated (no consent-based processing left); policy: club |
| L1 | iOS WebView has no CSP (Android has one) | Low | **Fixed** |
| L2 | Remote images in HTML loaded from any HTTPS host (IP leak / tracking pixels) | Low | **Fixed** |
| L3 | iOS offline cache files lack explicit `NSFileProtectionComplete` | Low | **Fixed** |
| L4 | Camp-schedule photos in cache dir survive logout | Low | **Fixed** |
| L5 | Firebase API key in `google-services.json` — restrict in Google Cloud | Low | Open (Google Cloud console, not code) |
| L6 | CI has no dependency/secret scanning, lint, or tests; no `permissions:` block | Low | **Fixed** (secret scanning: enable in repo settings) |
| L7 | Stale/misleading WebView JS-bridge comment | Info | **Fixed** |

---

## Resolution notes (follow-up commit)

- **H3:** Settings now has a *Privacy* section with the error-report switch (`SettingsViewModel.setErrorReportingEnabled`,
  which updates storage and `ErrorReporter`) and a link to the privacy policy.
- **M1:** the checkbox now reads *"Beru na vědomí Zásady ochrany osobních údajů"* (an acknowledgement) in all eight
  languages. The club still needs to make sure the policy names the legal bases, recipients, retention periods
  and the right to complain to ÚOOÚ.
- **M2:** we chose option 1 (legitimate interest, on by default, opt-out at any time). "Anonymous" has been
  removed, and the text now says what is sent (device model, OS and app version, no name or e-mail) and that it
  can be turned off in Settings.
- **M3:** the feedback form now says that name, e-mail and message go to the app developer (tobiso.com). Still
  for the club to do: list all recipients in the privacy policy and sign a processing agreement (DPA) with the
  tobiso.com operator.
- **M5:** `iosApp/iosApp/PrivacyInfo.xcprivacy` added. It declares no tracking, the data types collected and the
  `UserDefaults` reason `CA92.1`. Xcode includes it automatically because the folder is file-system-synchronised.
  Check it against Xcode's *Generate Privacy Report* before submitting, because bundled SDKs ship their own
  manifests.
- **M6:** nothing in the app relies on consent any more, so the age-15 rule doesn't gate anything. The
  child-friendly summary of the policy is still for the club to write.
- **L1:** both platforms now use `HtmlSanitizer.CONTENT_SECURITY_POLICY` (`script-src 'none'`). On iOS the
  bank-account script moved from an inline `<script>` into a `WKUserScript`, which page CSP doesn't block.
- **L2:** images load only from `HtmlSanitizer.trustedImageHosts` (`tkolymp.cz` and its subdomains, HTTPS only).
  Images from any other host become a tappable link, so nothing is fetched unless the member chooses to open it.
  The CSP `img-src` mirrors this. To trust another host (for example a CDN), add it to the set.
- **L3:** the iOS offline directory and files are written with `NSFileProtectionComplete`, and existing files
  are upgraded once at startup.
- **L4:** logout calls `ServiceLocator.localFilesCleaner`. On Android it deletes `cacheDir/camp_schedule` and
  `cacheDir/share`. On iOS it deletes the share PNGs and the notification-settings export in Documents.
- **L6:** CI now has `permissions: contents: read`, Gradle wrapper validation, shared unit tests and lint, plus
  Dependabot for Gradle and GitHub Actions. Lint is report-only until existing findings are triaged; remove its
  `continue-on-error` line after that. `.github/*` has been removed from `.gitignore`. Secret scanning and push
  protection are switched on in the repository settings (Settings → Code security), not in code.

## Findings

### H1 — Firebase Analytics and Crashlytics ran before consent — **fixed**
`composeApp` depends on `firebase-analytics` and `firebase-crashlytics` (`composeApp/build.gradle.kts:47-48`),
both of which collect automatically from the first launch (app-instance ID, device data, sessions, crash
data) unless disabled. Nothing in the code disables or gates them, and the app never calls `logEvent`;
crash reporting that respects the consent screen is done separately by `ErrorReporter`. So data went to Google
before the privacy screen was shown and even when the user turned error reporting off.

- **Law:** GDPR art. 6 (no legal basis) and art. 5(1)(c) (data minimisation); **Czech Act No. 127/2005 Coll.,
  § 89(3)** (storing or reading information on a user's device needs prior consent unless strictly necessary);
  transfer to Google LLC also needs to be disclosed (art. 13(1)(e)/(f)).
- **ISO 27001:2022:** A.5.34 (privacy and PII protection), A.5.19–A.5.21 (supplier relationships).
- **Fix:** `composeApp/src/androidMain/AndroidManifest.xml` now sets `firebase_analytics_collection_deactivated`,
  `firebase_crashlytics_collection_enabled=false` and disables ad-ID collection/ad personalisation signals.
- **Follow-up:** either remove both dependencies (and the Crashlytics Gradle plugin) since they're unused, or,
  if you want them, call `setCrashlyticsCollectionEnabled(true)` / `setAnalyticsCollectionEnabled(true)` only
  after a separate consent that is opt-in (not pre-checked) and can be withdrawn.

### H2 — Push topics leaked to the next user on a shared device — **fixed**
`NotificationsSettingsViewModel` subscribes the device to one FCM topic per cohort (group). FCM topics belong
to the device, not the account, and `ProfileViewModel.logout()` didn't unsubscribe them. A parent/child or
club-shared phone would keep receiving the previous user's group/coach messages after they logged out.

- **Law:** GDPR art. 5(1)(f) (confidentiality), art. 32 (security of processing).
- **ISO 27001:2022:** A.5.15/A.8.3 (access control / information access restriction).
- **Fix:** `logout()` now reads the cached cohort IDs and unsubscribes them before wiping user data, with a
  5 s timeout so an offline logout doesn't hang. The broadcast topic `all` stays.

### H3 — Error-reporting choice can't be withdrawn later
The error-reporting switch exists only on the one-time `PrivacyConsentScreen`; `setErrorReportingEnabled` is
never called elsewhere. Users who accepted it have no way to opt out later.

- **Law:** GDPR art. 7(3) (withdrawing consent must be as easy as giving it) or, if you rely on legitimate
  interest, art. 21 (right to object).
- **Recommendation:** add a "Send error reports" switch to Settings/Privacy that calls
  `onboardingStorage.setErrorReportingEnabled()`, `ErrorReporter.setEnabled()` and
  `ErrorReporter.setDeviceInfoConsented()`, plus a link to the privacy policy. Strings need to be added
  to all eight translation files.

### M1 — The privacy policy is "consented to" and used as a gate
The checkbox reads *"Přečetl/a jsem si a **souhlasím** se Zásadami ochrany osobních údajů"* ("I have read and
**agree** to the Privacy Policy"), and the app can't be used without ticking it. Processing of member data
(name, contact, payments, training schedule) is based on the **membership contract** (art. 6(1)(b)),
**legal obligations** such as accounting (art. 6(1)(c)), and **legitimate interest** (art. 6(1)(f)), not on
consent. Presenting it as consent and requiring it to use the app conflicts with art. 7(4) (consent must be
freely given), and if the club ever treated it as the legal basis, a withdrawal would leave it with none.

- **Recommendation:** change the wording to an acknowledgement: *"Beru na vědomí Zásady ochrany osobních
  údajů"* ("I acknowledge the Privacy Policy"). Make sure the policy itself (art. 13) names the controller
  (the club, its ID number IČO and contact details), the legal basis for each purpose, recipients
  (see M3), transfers outside the EU, retention periods, data-subject rights, and the right to complain to
  **ÚOOÚ** (Czech Office for Personal Data Protection).

### M2 — "Anonymous" error reports, pre-enabled
The screen says reports are *"anonymní … bez osobních údajů"* ("anonymous … without personal data"). They're
scrubbed (`ErrorReporter.scrub`), but they still carry a timestamp, device model, OS/app version and stack
traces, and the HTTP request exposes the device's IP address to the receiving server. That makes them
**pseudonymous**, which is still personal data (GDPR recital 26). The switch also defaults to **on**.

- **Law:** GDPR art. 5(1)(a)/art. 12 (transparent information). If the club relies on consent for this, a
  pre-ticked box isn't valid consent (CJEU C-673/17 *Planet49*).
- **Recommendation:** pick one of these:
  1. Keep it on by default, base it on **legitimate interest** (keeping the app stable), say so in the policy,
     and change the text to "technical error reports (no name or e-mail)", with H3's opt-out, or
  2. default the switch to **off** and treat it as consent.

### M3 — Third-party recipients must be disclosed and contracted
Personal data leaves the device to these parties besides the club's API:

| Recipient | What | Where in code |
|-----------|------|---------------|
| `www.tobiso.com` (Tobiso.Web) | Feedback form: **name, e-mail, free text**; automatic error reports | `PlatformNetwork*.kt` `FEEDBACK_BASE_URL`, `FeedbackService` |
| Google (Firebase Cloud Messaging) | Push token, topic subscriptions (cohort IDs) | `MainActivity`, `AndroidTopicManager` |
| GitHub (`api.github.com`) | IP address, app user-agent (changelog, update check) | `ChangelogService`, `UpdateChecker` |
| Any image host linked in announcements | IP address, user-agent | `HtmlText` (see L2) |

- **Law:** GDPR art. 13(1)(e) (recipients), art. 28 (a **data processing agreement** with whoever runs
  tobiso.com, which receives member names and e-mails on the club's behalf), and art. 44+ (Google/GitHub are
  US companies, so the transfers rely on the EU–US Data Privacy Framework).
- **ISO 27001:2022:** A.5.19–A.5.23 (suppliers, cloud services).
- **Recommendation:** list them in the privacy policy, sign a DPA with the tobiso.com operator, and show a
  one-line notice in `FeedbackDialog` ("Your name and e-mail are sent to … to respond to you").

### M4 — iOS logged in release builds — **fixed**
`shared/src/iosMain/.../Logger.kt` called `NSLog` unconditionally, and `Logger.isDebug` was never set on iOS.
Logged content includes GraphQL error bodies (`AuthService`: `"Login failed: $errors"`,
`"refreshJwt returned no token: $resp"`) and offline-cache keys. NSLog output goes to the device's unified log,
readable from a Mac and included in sysdiagnose bundles.

- **ISO 27001:2022:** A.8.15 (logging, protecting log information); OWASP MASVS-STORAGE-2.
- **Fix:** the iOS `Logger` now logs only when `isDebug` is set, and `initNetworking()` sets it from
  `Platform.isDebugBinary`, matching Android's `BuildConfig.DEBUG`.

### M5 — Missing iOS privacy manifest
There is no `PrivacyInfo.xcprivacy` in `iosApp/`. Since May 2024 the App Store requires one for apps that use
"required-reason" APIs (the app uses `NSUserDefaults` for preferences and file APIs for the offline cache), and
it also feeds the App Store privacy label.

- **Recommendation:** add `iosApp/iosApp/PrivacyInfo.xcprivacy` with `NSPrivacyTracking = false`, the
  collected data types (name, e-mail address, user ID, crash data, other diagnostic data), and the
  required-reason entries (`NSPrivacyAccessedAPICategoryUserDefaults` → `CA92.1`,
  `NSPrivacyAccessedAPICategoryFileTimestamp` → `C617.1` if file attributes are read). Fill in the Play
  Console *Data safety* form to match.

### M6 — Children
The club trains minors. In the Czech Republic the age for a child's own consent to information-society
services is **15** (**§ 7 of Act No. 110/2019 Coll.**, using the option in GDPR art. 8). Core processing is
contractual, so this only matters for consent-based processing (error reporting if you choose M2 option 2,
and any future analytics). Information for children must also be in age-appropriate language (art. 12(1)).

- **Recommendation:** rely on legitimate interest for diagnostics (M2 option 1), or have a parent or guardian
  consent for under-15 accounts. Add a child-friendly summary to the privacy policy. The birthdays widget and
  people list show other members' dates of birth. That data comes from the API, but the club should confirm
  that showing it to all members is covered by its stated purposes.

### L1 — iOS WebView has no Content-Security-Policy
Android's `HtmlText` includes `script-src 'none'` and related CSP rules; the iOS version
(`HtmlText.ios.kt`) has none. Both sanitise with `HtmlSanitizer`, so this is defence-in-depth only. iOS needs
an inline script for the bank-account bridge, so use a per-render nonce
(`script-src 'nonce-<random>'; default-src 'none'; img-src https: data:; style-src 'unsafe-inline'`), or move
the script into a `WKUserScript` and use `script-src 'none'`.

### L2 — Remote images from arbitrary hosts
Sanitised HTML may contain `<img src="https://any-host/…">`. Loading it reveals the member's IP address and
the time they read the announcement to that host (a tracking pixel). Consider allowing only `tkolymp.cz` (and
known CDNs) in `HtmlSanitizer`'s `imageSchemes` check or in the CSP `img-src`. Club staff write the content,
so the risk is low.

### L3 — iOS offline cache file protection
`OfflineDataStorageIos` writes cached member/payment JSON to `Application Support/offline` with the default
protection class (`CompleteUntilFirstUserAuthentication`). Since iOS has no background widgets that need the
data while the phone is locked, write with `NSDataWritingFileProtectionComplete`, or set
`NSFileProtectionComplete` on the directory. Android uses `ksafe`, which encrypts with Keystore-backed keys.

### L4 — Camp-schedule photos survive logout
`cacheDir/camp_schedule/capture.jpg` and `view_photo.jpg` (Android), plus `cacheDir/share/`, are not removed by
`logout()`. Delete them on logout. They're app-private and the OS may evict them, so the risk is low.

### L5 — Firebase configuration
`androidApp/google-services.json` (project `tkolymp-3aced`) is committed. Firebase API keys are public
identifiers by design, but in Google Cloud Console → Credentials the key should be **restricted** to the
Android app (package name + signing SHA-1) and to the APIs actually used (FCM, Installations). Consider
Firebase App Check. `local.properties` was briefly committed in the past; it contained only the SDK path,
API URL and tenant ID, so no secrets.

### L6 — CI / supply chain
`.github/workflows/android.yml` only compiles. ISO 27001 A.8.8 (technical vulnerability management) and
A.8.25/A.8.29 (secure development, security testing) call for: running `:shared:testAndroidHostTest` and
`:androidApp:lintDebug` in CI, enabling **Dependabot** (Gradle + GitHub Actions) and GitHub secret scanning or
push protection, pinning actions to commit SHAs, and adding `permissions: contents: read` to the workflow.
Note that `.gitignore` contains `.github/*`, so new workflow files won't be picked up by `git add` unless
forced. Remove that line.

### L7 — Misleading WebView comment — **fixed**
`HtmlText.kt` said "no addJavascriptInterface used" directly above code that registers one. The comment is
corrected. The bridge itself is acceptable: `minSdk` is 31, page scripts are blocked by the sanitizer and the
CSP, and both bridge methods validate their input or only copy text.

---

## What's already in good shape

| Control | Evidence | ISO 27001:2022 / MASVS |
|---|---|---|
| Token storage encrypted (Android Keystore via `ksafe`; iOS Keychain) | `TokenStorageAndroid.kt`, `TokenStorage.ios.kt` | A.8.24 · MASVS-STORAGE-1 |
| Offline member/payment cache encrypted on Android, legacy plaintext purged | `OfflineDataStorage.android.kt` | A.8.24 · STORAGE-1 |
| Backups and device transfer disabled | `allowBackup=false`, `data_extraction_rules.xml` | A.8.13 · STORAGE-2 |
| TLS certificate pinning (root + backup pins) on both platforms; no redirects for token-bearing requests | `PlatformNetwork*.kt` | A.8.20/A.8.21 · MASVS-NETWORK-2 |
| HTML allowlist sanitizer + CSP; WebView file/content access off; link schemes restricted, user gesture required | `HtmlSanitizer.kt`, `HtmlText.kt` | A.8.28 · MASVS-PLATFORM-2 |
| Exported activity validates deep-link route against a strict regex; replay from history blocked | `MainActivity.kt` | A.8.28 · PLATFORM-1 |
| `PendingIntent`s are immutable; receivers/services/providers not exported | manifest, `NotificationReceiver.kt` | PLATFORM-1 |
| Logout wipes token, user data, cache, offline data and notifications | `ProfileViewModel.logout()` | A.8.10 (information deletion) |
| Error reports scrub JWTs, e-mails and long numbers; rate-limited; gated on consent; release-only | `ErrorReporter.kt` | A.5.34 · MASVS-PRIVACY |
| Android logs disabled in release | `shared/src/androidMain/.../Logger.kt` | A.8.15 |
| R8 minify + resource shrinking; signing credentials outside the repo; `*.jks` ignored | `androidApp/build.gradle.kts`, `.gitignore` | A.8.4 · MASVS-RESILIENCE |
| `minSdk 31` / `targetSdk 36` | `libs.versions.toml` | A.8.8 |

## Czech / EU legal framework — applicability

| Regulation | Applies? | Notes |
|---|---|---|
| **GDPR** (EU 2016/679) | Yes | Club = controller. See H1–H3, M1–M3, M6. Keep records of processing (art. 30) and report breaches to ÚOOÚ within 72 h (art. 33). |
| **Act No. 110/2019 Coll.** on personal data processing | Yes | § 7: age of digital consent is 15 (M6). ÚOOÚ is the supervisory authority. |
| **Act No. 127/2005 Coll.** on electronic communications, § 89(3) | Yes | Device storage/access needs consent unless strictly necessary. Firebase Analytics is the main issue (H1, fixed). Token, preferences and offline cache are strictly necessary. |
| **Act No. 480/2004 Coll.** on information-society services | Only for marketing | Push notifications about trainings and club news aren't commercial communications. Promotional pushes for third parties would need opt-in consent. |
| **Act No. 264/2025 Coll.** on cybersecurity (NIS2) | Likely no | A sports club is normally below the thresholds and outside the regulated sectors. Re-check if the club or tenant operator grows into a regulated entity. |
| **Act No. 424/2023 Coll.** on accessibility requirements (EAA) | Likely no | Covers specific consumer services (e-commerce, banking, transport, etc.) and exempts micro-enterprises. Accessibility work is still advisable (touch targets and `contentDescription` are already conventions in this repo). |
| **EU Cyber Resilience Act** (2024/2847) | Likely no | Covers products made available "in the course of a commercial activity". A free, non-monetised club app is generally outside it. Re-assess if the app is sold or licensed to other clubs (multi-tenant `TENANT_ID` suggests that's possible). Reporting obligations start 11 Sep 2026; full application is Dec 2027. |
| **App Store / Google Play policy** | Yes | M5 (privacy manifest), Play *Data safety* form must match M3. Account deletion requirement applies only if accounts can be created in-app (they can't: accounts come from the club). Still link to how to request deletion. |

## ISO/IEC 27001:2022 / 27701 — what the app can and can't cover

The app contributes technical controls (table above). Certification or alignment additionally requires
organisational work by the club or operator, which no code change provides:

- ISMS scope, information-security policy, risk assessment and treatment plan (clauses 4–6), Statement of
  Applicability.
- Supplier register and agreements (A.5.19–A.5.23): Google/Firebase, tobiso.com host, GitHub, the GraphQL
  API host.
- Incident management and breach-notification procedure (A.5.24–A.5.28) tied to the GDPR 72-hour rule.
- Secure development policy (A.8.25): this document, `CLAUDE.md` conventions, PR checklist, plus the CI
  gates in L6.
- Records of processing, DPIA screening (a full DPIA is probably not required, but document why), retention
  schedule (ISO 27701 / GDPR art. 30, 35).

## Items for the API / club side (not changed here)

These depend on the server and can't be fixed in the client. They're listed so the club can follow up with
the API operator:

- JWT lifetime and refresh-token revocation on logout. The client only discards its copy; it does not revoke
  the token on the server.
- Server-side authorisation of what each member can see (other members' birthdays, contacts, payments).
- Login rate limiting, account lockout and password policy.
- Retention and deletion of member data, and processing of data-subject requests (access, erasure,
  portability).
- The site serving `/f/*` with the JWT as a `rozpisovnik` cookie value (the client sends it for authenticated
  images).
