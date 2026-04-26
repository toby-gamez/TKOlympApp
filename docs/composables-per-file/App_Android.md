File: composeApp/src/androidMain/kotlin/com/tkolymp/tkolympapp/App.kt

@Composable occurrences: 3

Quick observations
- Platform-specific `App.kt` hosts top-level navigation and theming; ensure theme wrapper usage is consistent with common `Theme`.

Checklist results (manual review required)
- Verify `Theme` usage and window insets handling are consistent with common screens.

Recommended fixes
- Ensure navigation transitions and top-level scaffolds follow tokenized paddings and sizes.
