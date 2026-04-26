File: composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/SettingsScreen.kt

@Composable occurrences: 6

Quick observations
- Contains `SettingsCard` and `SettingsRow` helper functions; good candidate for centralizing settings patterns.

Checklist results (manual review required)
- Verify all switches, icons, and labels use consistent spacing and semantic roles.
- Ensure `testTag` or content descriptions are present where needed.

Recommended fixes
- Move `SettingsRow`/`SettingsCard` to `components/SettingsRow.kt` and document usage.
- Add previews for setting permutations (enabled/disabled).
