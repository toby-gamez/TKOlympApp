# Composables UI Consistency Audit

Overview
- Scanned codebase for `@Composable` occurrences: 106 matches found.
- This document lists discovered composable files, a consistency checklist, issues to review, and recommended next steps.

Files with `@Composable` occurrences (non-exhaustive list)
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/PaymentsScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/OtherScreen.kt
- composeApp/src/androidMain/kotlin/com/tkolymp/tkolympapp/App.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/BottomBar.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/components/OfflineBanner.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/components/BarChart.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/components/QuantityInput.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/SwipeToReload.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/PersonalEventEditScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/PersonalEventsScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/SettingsScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/BoardScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/PeopleScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/TrainersLocationsScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/LanguageScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/PrivacyPolicyScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/LoginScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/GroupsScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/StatsShareCard.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/OnboardingScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/StatsScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/util/CalendarUiUtils.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/NotificationsSettingsScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/ProfileScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/ProfileDialogs.kt
- composeApp/src/commonMain/kotlin/ui/theme/Theme.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/platform/ShareUtils.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/PersonScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/platform/NotificationFileButtons.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/platform/HtmlText.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/CalendarViewScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/platform/AppLogo.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/AboutScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/LeaderboardScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/ui/Branding.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/CalendarScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/EventScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/NoticeScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/RegistrationScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/EventsScreen.kt
- composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/OverviewScreen.kt
- composeApp/src/androidMain/kotlin/com/tkolymp/tkolympapp/platform/ShareUtils.android.kt
- composeApp/src/androidMain/kotlin/com/tkolymp/tkolympapp/platform/NotificationFileButtons.kt
- composeApp/src/androidMain/kotlin/com/tkolymp/tkolympapp/platform/HtmlText.kt
- composeApp/src/androidMain/kotlin/com/tkolymp/tkolympapp/platform/AppLogo.kt
- composeApp/src/androidMain/kotlin/com/tkolymp/tkolympapp/MainActivity.kt

Consistency Checklist (apply to each composable)
- Theme & tokens: Uses the app `Theme` and design tokens (colors, typography, shapes) rather than hard-coded values.
- Colors: Uses `Material`/theme color roles; verifies sufficient contrast in light and dark modes.
- Typography: Uses consistent typography scale (no ad-hoc font sizes) and correct semantic styles (`bodyLarge`, `labelSmall`, etc.).
- Spacing: Uses consistent spacing system (dimension tokens) and `Modifier` usage for paddings/margins.
- Modifier ordering: `Modifier` parameters follow consistent ordering and are passed in (avoid internal fixed modifiers that block composition).
- Reusability: Small, single-responsibility composables; UI primitives extracted to `components/` for reuse.
- Iconography: Consistent icon sizes, paddings, and use of `contentDescription` for accessibility.
- Accessibility: Text sizes, contrast, semantics (roles, testTags where useful), and focus/navigation ordering are considered.
- State & side effects: State is lifted where appropriate; composables are stateless where possible and accept state/events via parameters.
- Previews & testing: Parameterized `@Preview` or platform previews exist for key composables; unit tests or screenshot tests where feasible.
- Naming: Composable names follow a clear pattern and file/component naming is consistent.
- No magic numbers: Avoid inline numeric constants for sizes, spacing, or colors.

Initial Findings & Recommendations
- Theme usage: Check `Theme.kt` and confirm most screens use it — ensure colors/typography are only defined centrally.
- Multiple large screens (e.g., `StatsScreen.kt`, `OnboardingScreen.kt`) contain many composables in one file — consider splitting into smaller components in `components/`.
- Platform-specific composables exist (`platform/*.android.kt`) — verify API parity and consistent look across platforms.
- Accessibility: Search for missing `contentDescription` on icons and images, and run contrast checks on primary UI screens.
- Modifier ordering and repeated paddings: Create a small lint or PR checklist to enforce `Modifier` parameter and `padding` consistency.
- Previews: Add parameterized previews for the most-used components (cards, lists, inputs) to speed visual reviews.

Next Steps (recommended)
1. Perform per-file manual review against the Checklist for the top-priority screens: `StatsScreen.kt`, `OnboardingScreen.kt`, `SettingsScreen.kt`, `ProfileScreen.kt`.
2. Create a small set of design tokens (if missing) for spacing and typography and refactor a representative component to use them.
3. Add a PR checklist entry and a short `docs/compose-guidelines.md` with the Consistency Checklist.
4. Optionally: Add Compose lint rules or editorconfig checks for `Modifier` ordering and magic numbers.

If you want, I can:
- Produce a per-file findings report (one file per composable listing issues), or
- Open the top 5 large composable files and annotate specific lines to change.

-- Audit generated: automated scan for `@Composable` occurrences; manual review required for visual and accessibility checks.
