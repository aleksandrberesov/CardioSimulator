# Android parity — Constructor: move the significant-points list out of the drawer into the Points panel

**Type:** Win → Android UI parity port
**Source:** CardioSimulatorWin (WinUI3), 2026-07-07
**Status:** active

## What changed on Windows (and why)

Customer feedback: the standalone floating "significant points" side drawer was clutter. On
Windows we:

1. **Deleted** the floating collapsible drawer that listed the pathology's significant points
   (`SignificantPointsDrawer`), and **folded that same list into the Points tool-mode panel**
   (`SignificantPointPanel`) as a new **"Marked points"** section.
2. The list is unchanged in behavior: every marked P/QRS/T significant point, **sorted by sample
   index**, each row showing the **point label + time (ms)**, the row **highlighted when it is the
   currently-selected sample**, and **clicking a row selects/jumps to that sample**.
3. Added a new caption string `editor_marked_points` (Win key) in all 5 languages.
4. The tool-mode icon strip + mode panel stay on the **right** of the canvas. (An interim Windows
   experiment that moved that whole cluster to the LEFT was reverted as "looks not good" — **do NOT
   port a left move**; Android already has this cluster on the right, leave it.)

Net effect to port: **eliminate the standalone significant-points drawer on Android and surface the
exact same list inside the Points / "significant points" panel.** Everything else in that panel
(Auto-Detect, P/QRS/T toggle chips, R-R interval list) is preserved.

## Android is structurally identical — the port is a relocation, not a rewrite

| Windows | Android |
|---|---|
| `SignificantPointsDrawer` (deleted) | `pointsDrawer` = `SideDrawer` + `SignificantPointSelector` in `ConstructorScreen.kt` |
| `SignificantPointPanel` (list folded in) | `SignificantPointPanel.kt` |
| list rows (inlined into panel) | `SignificantPointSelector.kt` (reusable composable) |
| `editor_marked_points` string | `constructor_marked_points` string (Android uses the `constructor_` prefix) |

Because Android already separates the drawer handle (`SideDrawer`) from the list content
(`SignificantPointSelector`), we can **reuse `SignificantPointSelector` inside the panel** instead of
deleting-and-inlining like Windows did. Same outcome, less churn — this is an intentional, idiomatic
divergence from the Windows implementation.

## Changes

### 1. `ui/panels/SignificantPointsSelector.kt` — make it embeddable

Add two params so it can render inside the panel without its own title (the panel supplies the
"Marked points" caption):

- `showHeader: Boolean = true` — when false, skip the `constructor_significant_points` title +
  the top `HorizontalDivider()`.
- `modifier: Modifier = Modifier` — apply to the root `Column` instead of the hard-coded
  `fillMaxSize()`, so the caller can bound its height (see the nested-scroll gotcha below).

Keep the existing empty-state hint and the `LazyColumn` of clickable rows exactly as-is.

### 2. `ui/panels/SignificantPointPanel.kt` — add the "Marked points" section

- Add a param: `onPointSelect: (SignificantPoint) -> Unit = {}`.
- After the P/QRS/T chips-or-hint block and **before** the R-R (`rPeaks`) block — mirroring the
  Windows insertion point — add, only when `significantPoints.isNotEmpty()`:
  - a `HorizontalDivider()`,
  - a `Text(stringResource(R.string.constructor_marked_points), style = labelMedium, color = onSurfaceVariant)` caption,
  - `SignificantPointSelector(points = significantPoints.sortedBy { it.index }, selectedIndex = selectedIndex ?: -1, sampleRateHz = sampleRate, onPointSelect = onPointSelect, showHeader = false, modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp))`.
- Add the `SignificantPointSelector` + `SignificantPoint` imports to this file.

### 3. `ui/screens/ConstructorScreen.kt` — remove the drawer, wire the panel

- **Wire the panel** (the `ToolMode.Points -> SignificantPointPanel(...)` call, ~line 956): add
  `onPointSelect = { constructorViewModel.selectIndex(it.index) }` (same VM call the drawer used —
  `selectIndex(index: Int)` is at `ConstructorViewModel.kt:460`).
- **Delete the points drawer**:
  - remove `var isPointsDrawerExpanded by remember { mutableStateOf(false) }` (~line 519),
  - remove the whole `val pointsDrawer = @Composable { SideDrawer(... SignificantPointSelector ...) }`
    block (~lines 565–593),
  - remove the `if (!showAllLeads) { pointsDrawer() }` render (~lines 1032–1034). The `showAllLeads`
    guard existed only to hide this drawer during the all-leads preview (the Windows analog —
    toggling `_pointsDrawer.Visibility` — was also removed); nothing else needs it here.
  - remove the now-unused `import ...ui.panels.SignificantPointSelector` (~line 62).
- **Keep** the `rhythmDrawer` / `SideDrawer` for rhythms untouched.

### 4. Strings — add `constructor_marked_points`, drop the dead drawer title

Add after `constructor_significant_points` in each locale:

| file | anchor line | add |
|---|---|---|
| `values/strings.xml` | 426 | `<string name="constructor_marked_points">Marked points</string>` |
| `values-ru/strings.xml` | 424 | `<string name="constructor_marked_points">Отмеченные точки</string>` |
| `values-zh/strings.xml` | 287 | `<string name="constructor_marked_points">已标记的点</string>` |
| `values-es/strings.xml` | 295 | `<string name="constructor_marked_points">Puntos marcados</string>` |
| `values-hi/strings.xml` | 396 | `<string name="constructor_marked_points">चिह्नित बिंदु</string>` |

Remove the now-unused `points_drawer_title` (present only in `values` L221, `values-ru` L220,
`values-hi` L191 — ZH/ES never had it).

## Gotchas

- **String key prefix:** Windows uses `editor_*`; Android uses `constructor_*`. The new key is
  `constructor_marked_points`, **not** `editor_marked_points`.
- **Don't port the reverted left-move.** The Windows left-side experiment was undone. Android's
  tool/mode cluster is already on the right — leave the layout side alone.
- **Nested-scroll trap:** the panel already has a `weight(1f)` `verticalScroll` chips column *and*
  the selector's `LazyColumn`. Do **not** wrap the whole panel `Column` in `verticalScroll`
  (LazyColumn inside an infinite-height scroll crashes/misbehaves). Bound the embedded selector with
  `heightIn(max = 200.dp)` so it coexists with the weighted chips column instead of competing for
  weight.
- **`selectedIndex` nullability:** the panel's `selectedIndex` is `Int?`; the selector wants a
  non-null `Int`. Pass `selectedIndex ?: -1` (no row matches -1, so nothing highlights when nothing
  is selected — correct).
- Reusing the composable (vs. Windows delete+inline) is deliberate; don't "restore" a separate
  points drawer as a parity regression.

## Verify

- `./gradlew :app:compileDebugKotlin` (or `assembleDebug`) is green.
- Constructor → Points tool: the panel shows Auto-Detect + P/QRS/T chips + **Marked points** list +
  R-R list. Clicking a Marked-points row jumps the selection to that sample and highlights the row.
- The old floating points-drawer handle is gone; the rhythm drawer still works.
- Caption reads correctly in EN/RU/ZH/ES/HI.
