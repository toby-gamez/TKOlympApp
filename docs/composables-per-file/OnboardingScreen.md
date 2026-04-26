File: composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/OnboardingScreen.kt

@Composable occurrences: 17

Quick observations
- Multiple step composables and mock preview parameter; file is large and likely mixes layout and content.
- Confirm consistent use of typography and spacing across steps.

Checklist results (manual review required)
- Theme usage: confirm central `Theme` tokens used for colors and typography.
- Accessibility: ensure focus order and skip links between steps.
- Reusability: extract repeated step layout into a single `OnboardingStep` component.

Recommended fixes
- Move repeated step scaffold to `components/OnboardingStep.kt`.
- Add previews showing multiple steps and dark-mode variants.
