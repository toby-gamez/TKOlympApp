- PR Checklist: Composable UI Changes

When opening a PR that touches UI composables, ensure:
- All new UI uses centralized tokens in `ui/theme/`.
- No magic numeric sizes or colors are introduced.
- Reusable components accept `modifier: Modifier = Modifier` and do not hardcode layout modifiers internally.
- Icons/images include `contentDescription` or are explicitly marked decorative.
- Screens/components have at least one `@Preview` (or platform preview) demonstrating default and dark themes.
- Accessibility basics covered: contrast, touch target sizes, and semantics.
- Add a short screenshot or GIF demonstrating visual changes for reviewers.
