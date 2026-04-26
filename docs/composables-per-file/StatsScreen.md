File: composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/StatsScreen.kt

@Composable occurrences: 21

Quick observations
- Many small composables defined in one large file (cards, containers, helpers).
- Look for hard-coded colors, font sizes, and spacings inside the file.
- Check `contentDescription` for any image/icon usages.
- Verify state is lifted and passed via parameters for cards.

Checklist results (manual review required)
- Theme usage: likely present but confirm no hard-coded color values.
- Typography: check for direct numeric font sizes.
- Spacing: identify repeated paddings that should use tokens.
- Reusability: suggest extracting `StatsCard` and repeated card patterns to `components/`.

Recommended fixes
- Extract reusable card and header components to `composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/components/`.
- Replace magic numbers with tokens in `ui/theme/`.
- Add `@Preview` variants for main cards.
