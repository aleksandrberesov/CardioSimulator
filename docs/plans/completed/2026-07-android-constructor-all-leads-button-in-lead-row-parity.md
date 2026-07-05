# Plan — Constructor "All leads" button in the lead-button row (Android parity)

**Status:** active
**Owner:** a.beresov
**Started:** 2026-07-04
**Related:** Windows→Android UI parity. Source of truth = Windows port
`CardioSimulatorWin/src/CardioSimulator.App/Screens/ConstructorScreen.cs`.

## Goal

On the Windows port the Constructor's **"All leads"** (show-all-12-leads) button was
moved out of the top toolbar and into the **row of lead buttons**, so it now sits at the
end of the lead strip: `[I][II][III] … [V6]  [All leads]`. Do the same on Android so the
two Constructors match. This is a pure **placement** change — the overlay it opens, the
icon, the tooltip/string, and the gating (only visible when a pathology is loaded) are all
unchanged.

## Current state (Android)

`app/src/main/java/com/example/cardiosimulator/ui/screens/ConstructorScreen.kt`:

- **State** — `var showAllLeads by remember { mutableStateOf(false) }` at
  `ConstructorScreen.kt:148`. Unchanged by this plan.
- **Button (to move)** — the view-all `IconButton` lives in the top toolbar, inside the
  `if (targetFile != null) { … }` cluster (that block opens at `ConstructorScreen.kt:599`):

  ```kotlin
  // ConstructorScreen.kt:641-646
  IconButton(onClick = { showAllLeads = true }) {
      Icon(
          Icons.Default.GridView,
          contentDescription = stringResource(R.string.constructor_view_all_leads)
      )
  }
  ```

  It is the last button before the toolbar `targetFile` block closes at line 647.
- **Lead-button row (target location)** — the lead `TabRow` at
  `ConstructorScreen.kt:663-681`:

  ```kotlin
  // Lead Tabs
  TabRow(
      selectedTabIndex = Lead.entries.indexOf(focusedLead),
      containerColor = MaterialTheme.colorScheme.surface
  ) {
      Lead.entries.forEach { lead ->
          Tab( … lead.name … )
      }
  }
  ```

- **Overlay** — `AllLeadsPreviewOverlay` at `ConstructorScreen.kt:873-879` / defined at
  `:1089`, shown when `showAllLeads == true`. Unchanged.
- **String** — `R.string.constructor_view_all_leads` already exists (used by the button
  today). No new strings, no new imports (`GridView`, `IconButton`, `Row` are already in
  use in this file).

### What Windows did (reference)

`ConstructorScreen.cs`:
- `BuildLayout()` — **removed** `toolbar.Children.Add(_viewAllButton)`; kept the tooltip +
  `Click` wiring; added a small left margin `_viewAllButton.Margin = new Thickness(8, 0, 0, 0)`
  to separate it from the last lead tab.
- `RefreshTabs()` — after appending the `I…V6` lead buttons to the `_tabs` strip, appended
  the same button: `_tabs.Children.Add(_viewAllButton)`. `_tabs.Children.Clear()` at the
  top of the method re-parents it cleanly on every refresh.
- Visibility is still driven by `UpdateToolbar()`
  (`_viewAllButton.Visibility = hasTarget ? Visible : Collapsed`) — the button shows only
  when a pathology is loaded.

## Non-goals

- No change to the `AllLeadsPreviewOverlay` content, rendering, or the `showAllLeads` state.
- No icon / string / tooltip change.
- Do **not** move any other toolbar button (rename, duplicate, delete, calc-derived, etc.).
- Do not make the lead `TabRow` scrollable or otherwise restyle the lead tabs.

## Plan (one PR)

### Phase 1 — Remove the button from the toolbar
- Delete the view-all `IconButton` block at `ConstructorScreen.kt:641-646` from inside the
  `if (targetFile != null)` toolbar cluster. Leave the surrounding rename/duplicate/delete/
  calc-derived buttons untouched.

### Phase 2 — Append it to the lead-button row
Compose's `TabRow` distributes its tabs evenly across the full width, so it cannot host a
trailing child directly. Wrap the existing `TabRow` in a `Row` and place the button after
it, letting the `TabRow` take the remaining width via `weight(1f)`:

```kotlin
// Lead Tabs  (+ trailing "All leads" button)
Row(verticalAlignment = Alignment.CenterVertically) {
    TabRow(
        modifier = Modifier.weight(1f),
        selectedTabIndex = Lead.entries.indexOf(focusedLead),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Lead.entries.forEach { lead ->
            Tab(
                selected = focusedLead == lead,
                onClick = { constructorViewModel.selectLead(lead) },
                text = {
                    Text(
                        text = lead.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        color = if (dirtyLeads.contains(lead)) Color.Red else Color.Unspecified
                    )
                }
            )
        }
    }

    // Show-all-12-leads overview — only when a pathology is loaded (matches Windows'
    // hasTarget gating; the button used to live in the `targetFile != null` toolbar block).
    if (targetFile != null) {
        IconButton(onClick = { showAllLeads = true }) {
            Icon(
                Icons.Default.GridView,
                contentDescription = stringResource(R.string.constructor_view_all_leads)
            )
        }
    }
}
```

Notes:
- Gate the button on `targetFile != null` so it appears only when editing a pathology —
  the same condition it had inside the toolbar block, and the same intent as Windows'
  `hasTarget` visibility. When there is no target, the `TabRow` still fills the width on
  its own.
- Keep the button trailing (right of `V6`) to mirror the Windows `[I]…[V6] [All leads]`
  order. The `TabRow`'s `Modifier.weight(1f)` gives the button its intrinsic width and the
  tabs the rest.

## Risks & open questions

- **TabRow indicator width** — because the `TabRow` now measures at `weight(1f)` (full width
  minus the button) instead of full screen, the selected-tab indicator/tab widths shrink
  very slightly. This is expected and matches the Windows layout intent; verify it still
  reads cleanly on a phone-width screen.
- **Vertical alignment** — the lead tabs are short; `verticalAlignment = CenterVertically`
  on the wrapping `Row` keeps the `IconButton` centered against the tab strip. Confirm the
  `Row` doesn't add unwanted height (an `IconButton` is 48.dp; the tab row is comparable).
- If a phone is too narrow for all 12 tabs + the button, consider `ScrollableTabRow` in a
  follow-up — **out of scope here** (Windows uses a horizontally scrollable strip, but the
  Android `TabRow` was already non-scrollable before this change, so behavior is unchanged).

## Verification

- Build: `./gradlew :app:assembleDebug` succeeds.
- Open **Constructor** with a pathology loaded:
  - The **grid/"All leads"** icon no longer appears in the top toolbar.
  - It appears at the **right end of the lead-tab row**, after `V6`.
  - Tapping it opens the existing all-12-leads preview overlay; closing returns to the editor.
- With **no pathology** loaded (fresh Constructor, `targetFile == null`): the button is
  absent and the lead-tab row fills the width normally.
- Switching focused leads still highlights the correct tab; dirty leads still render red.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Constructor: move "All leads" button into the lead-tab row | 1–2 | Single-file change in `ConstructorScreen.kt`; no strings/imports added |

---

## Outcome

- **Result:** shipped
- **PRs:** N/A (applied directly)
- **Deviations from plan:** None.
- **Follow-ups spawned:** None.
