File: composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/StatsShareCard.kt

@Composable occurrences: 6

Quick observations
- Contains multiple card and share-related composables; ensure share UI matches branding.

Checklist results (manual review required)
- Iconography: verify `contentDescription` present for share icons.
- Colors/typography: ensure tokens used rather than inline values.

Recommended fixes
- Consolidate share actions into a single `ShareRow` reusable composable.
- Add unit tests or screenshot tests for share UI states.
