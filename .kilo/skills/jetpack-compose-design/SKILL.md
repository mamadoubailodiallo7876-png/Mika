---
name: jetpack-compose-design
description: Use when building or reviewing Mika Compose screens, Material 3 components, navigation, responsive layouts, animation, state management or accessibility.
---

# Jetpack Compose and Material 3 design

## Start with the existing system

Mika already has a design system: `MikaTheme`, `MikaColors`, `MikaType`, `MikaSpace`, `MikaSize`,
`MikaShapes` and `MikaIcons`, plus components such as `SubPage`, `MikaCard`, `GroupCard`, `ListRow`,
`MikaButton`, `SegmentedControl`, `StateCard`, `MikaDialog` and `InputRow`. Reuse them before adding
a new component. A new component needs at least two real call sites or a clear semantic role.

## Screen recipe

1. Choose the page shape: `SubPage` for readable, scrollable secondary pages; a `LazyColumn` when
   content is long or unbounded.
2. Define one screen-level state holder or view model. Hoist events; keep composables stateless where
   practical.
3. Build sections in order: status/call to action, primary content, empty state, secondary actions.
4. Add stable test tags only where an interaction test needs them.
5. Add previews for independent components when the project supports them.

## Visual rules

- Use theme tokens, never raw hex colors in a screen.
- Use one accent for the primary action. Status colors are for status, not decoration.
- Keep spacing on the 4/8/12/16/20/24 scale. Page padding is 16-20dp.
- Corner radii come from `MikaShapes`; do not mix five different radii on one screen.
- Prefer borders and one soft shadow over stacked elevations.
- Typography hierarchy: one page title, one section label, body text, caption. Avoid all-bold cards.
- Do not use gradients as decoration. A single purposeful hero treatment is enough.
- Empty states explain the next action and include a button when the action is available.
- Loading states are skeletons or inline indicators; never a blank screen.
- Errors are inline, specific and recoverable where possible.

## State and recomposition

- Use `remember` for cheap derived values, `rememberSaveable` for user input and selected tabs.
- Do not call a service or read a file directly in composition without `remember`/`LaunchedEffect`.
- Avoid passing a whole large data class when a stable id and a small view model suffice.
- Use `key` in lists. Never use list index as a key for mutable rows.
- Do not launch effects from `onClick` without a scope; use `rememberCoroutineScope`.

## Responsive layout

- Test at 320dp, 360dp, 411dp and tablet widths. Avoid fixed widths that clip translated text.
- Use `FlowRow` for chips and actions; use weights for equal columns.
- Keep touch targets at least 48dp. Icon-only buttons need a content description.
- Respect font scaling: use `sp`, allow wrapping, avoid `maxLines = 1` on essential labels.
- Provide landscape/tablet layouts only when the content genuinely benefits; do not duplicate screens.

## Accessibility

- Every icon that conveys meaning has a content description; decorative icons use `null`.
- Do not rely on color alone for status; include text or a shape.
- Support screen readers with logical traversal order and meaningful labels.
- Test with TalkBack on the main flow and with large font/display size.
- Dialogs must be dismissible with Back and must return focus sensibly.

## Navigation

- Add a route to `Route` and handle it in `MainActivity`; preserve the chat root.
- Back behavior goes through `NavStack.back()` so the drawer and system back agree.
- A new top-level drawer entry needs a clear label, icon and return path.
- Do not remove existing entries users depend on.

## Verification

- Compile with the project's Android build; a Compose preview alone is not verification.
- Run relevant UI tests when they exist. If you cannot run a device test, say so.
- Check dark and light themes, large text and a narrow screen before calling a screen finished.
