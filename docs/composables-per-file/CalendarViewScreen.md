File: composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/CalendarViewScreen.kt

@Composable occurrences: 7

Quick observations
- Calendar UIs are sensitive to spacing and typography; check day-cell sizing and selection states.

Checklist results (manual review required)
- Consistent sizing: ensure day cells use consistent dimensions and no hard-coded numbers.
- Accessibility: check semantics for selected days and range selections.

Recommended fixes
- Extract day cell composable if not already extracted.
- Add preview for month and week views.
