# Comparison-mode defaults + entry wiring (Android parity)

**Status:** completed
**Owner:** AI Assistant
**Started:** 2026-06-29
**Finished:** 2026-06-29

**Direction:** **Windows → Android.** The Windows port (`CardioSimulatorWin`) refined comparison mode on
2026-06-29; Android must catch up. The Windows port is the reference for behaviour — match it, adapting
idioms to Kotlin/Compose.

---

## Goal

Three small refinements to the monitor **comparison mode** (Teaching). On Windows all three were
addressed; on Android **two of the three are already done**, so the real work is the third plus a
prerequisite wiring fix that is currently blocking compare from being entered at all.

The three Windows refinements were:

1. **Compare button highlights while compare mode is active** — the bottom-panel Compare tab fills with
   the accent (toggled-pill state) while `isCompareMode` is true, clearing on exit.
2. **Default comparison layout = 4 panes, 1 column, with only the first pane pre-filled** — entering
   compare with no saved presets seeds a *single* filled pane (the **currently selected rhythm**, lead
   **II**) and shows **4 single-column panes** (`OneColumn`); the other three are tappable placeholders.
   (Previously it seeded two panes — leads I and II — in a 2-column layout.)
3. **Lead picker in the target dialog laid out as a fixed 2-column × 6-row grid** — so all 12 leads are
   visible without overflow.

## Current state (Android) — what's already done vs. what's missing

| # | Refinement | Android status |
|---|------------|----------------|
| 1 | Compare button highlight | **Already done.** `ui/panels/MonitorControlPanel.kt:339-345` already passes `isActive = monitorMode.isCompareMode` on the Compare `Tab`. No change needed. |
| 3 | 2-column lead grid | **Already done.** `ui/dialogs/ComparisonTargetDialog.kt:135-161` already uses `LazyVerticalGrid(columns = GridCells.Fixed(2))`, which gives 2 columns × 6 rows for the 12 leads. No change needed. |
| 2 | Default 4/OneColumn + single pre-filled pane (lead II) | **Not done** — seeds two panes (I + II), no forced layout. **See changes below.** |
| 0 | (prerequisite) Compare button is wired | **Not wired** — `onCompareClick` is never supplied, so the button is a no-op and compare mode is currently unreachable. **Must fix for #2 to be observable.** |

> Because #1 and #3 are already present, **do not re-implement them** — just verify they still match
> after this change. The substantive work is the prerequisite wiring (#0) plus the seeding/layout (#2).

---

## Change 0 (prerequisite) — wire the Compare button

`ui/panels/MonitorControlPanel.kt:50-56` declares `onCompareClick: () -> Unit = {}` and uses it at
`:342` (`onClick = onCompareClick`). But the only call site,
`ui/screens/MainScreen.kt:354-363`, **does not pass `onCompareClick`**, so it defaults to a no-op:

```kotlin
// MainScreen.kt — Teaching branch, current
MonitorControlPanel(
    viewModel = monitorViewModel,
    onStartStopClick = { isRunning -> /* … */ }
)
```

`selectedRhythm` is already in scope here (it is used two lines down in `onStartStopClick`,
`MainScreen.kt:358`). Wire the button to toggle compare mode, passing the selected rhythm's id so the
default seed can pre-fill pane 0:

```kotlin
// MainScreen.kt — Teaching branch, after
MonitorControlPanel(
    viewModel = monitorViewModel,
    onCompareClick = { monitorViewModel.toggleCompareMode(selectedRhythm?.id) },
    onStartStopClick = { isRunning -> /* … unchanged … */ }
)
```

All enter/exit/layout logic lives in `toggleCompareMode` (Change 2), so the call site stays a one-liner.
Entering when saved presets exist still works: `TeachingScreen.kt:274-278`'s
`LaunchedEffect(mode.isCompareMode)` already pops the presets dialog when `isCompareMode` flips true and
presets are non-empty.

## Change 2 — default layout + single pre-filled pane (lead II)

`ui/viewmodels/MonitorViewModel.kt:242-254`, current:

```kotlin
fun toggleCompareMode(defaultPathologyId: String? = null) {
    _monitorMode.update { prev ->
        val nextCompareMode = !prev.isCompareMode
        var nextTargets = prev.comparisonTargets
        if (nextCompareMode && nextTargets.isEmpty() && defaultPathologyId != null && prev.comparisonPresets.isEmpty()) {
            nextTargets = mapOf(
                0 to ComparisonTarget(defaultPathologyId, Lead.I),
                1 to ComparisonTarget(defaultPathologyId, Lead.II)
            )
        }
        prev.copy(isCompareMode = nextCompareMode, comparisonTargets = nextTargets)
    }
}
```

After — seed a single pane (`Lead.II`), force the 4/`OneColumn` default on entry, and restore the normal
12-lead grid on exit (mirrors the Windows `EnterCompareWithDefaults` / `ExitCompare` pair):

```kotlin
fun toggleCompareMode(defaultPathologyId: String? = null) {
    _monitorMode.update { prev ->
        val nextCompareMode = !prev.isCompareMode
        if (nextCompareMode) {
            // Entering. With no saved presets, seed the default comparison: a single filled pane
            // (selected rhythm, lead II) in a four-pane single column; the other three panes are
            // tappable placeholders. With presets, leave it to the presets dialog (TeachingScreen).
            val seedDefaults = prev.comparisonTargets.isEmpty() && prev.comparisonPresets.isEmpty()
            val nextTargets = if (seedDefaults && defaultPathologyId != null) {
                mapOf(0 to ComparisonTarget(defaultPathologyId, Lead.II))
            } else {
                prev.comparisonTargets
            }
            prev.copy(
                isCompareMode = true,
                comparisonTargets = nextTargets,
                count = if (seedDefaults) 4 else prev.count,
                seriesScheme = if (seedDefaults) SeriesScheme.OneColumn else prev.seriesScheme
            )
        } else {
            // Exiting. Drop the per-pane targets and restore the standard 12-lead grid.
            prev.copy(
                isCompareMode = false,
                comparisonTargets = emptyMap(),
                count = 12,
                seriesScheme = SeriesScheme.Grid
            )
        }
    }
}
```

Notes:
- `SeriesScheme` and `Lead` are already imported in this file (`SeriesScheme.entries` at `:32`,
  `Lead.valueOf`/`Lead.I` already used). `MonitorModeModel` already has `count` and `seriesScheme`
  fields (`setSeriesCount`/`setSeriesScheme` at `:135`/`:144`).
- **Deliberate deviation from Windows:** the compare layout (`count`/`seriesScheme`) is folded straight
  into the `MonitorModeModel` copy and **not persisted to prefs**, since it is transient compare-mode UI
  state. (Windows routes through `SetSeriesCount`/`SetSeriesScheme`, which persist, then resets on exit —
  same net effect, but on Android we avoid writing temporary values into `DataSourcePrefs`.) If you'd
  rather mirror Windows exactly, call `setSeriesCount(4)` / `setSeriesScheme(SeriesScheme.OneColumn)`
  (and `setSeriesCount(12)` / `setSeriesScheme(SeriesScheme.Grid)` on exit) after the `update {}` instead.
- Clearing `comparisonTargets` on exit also drops the loaded comparison waveforms: they are loaded
  reactively from `comparisonTargets` (`TeachingScreen.kt:268-272`,
  `rhythmViewModel.loadComparisonWaveform`), so an empty map leaves nothing rendered. Verify the
  `rhythmViewModel` side has no stale per-pane waveforms after exit; clear them too if needed (Windows
  calls `ClearComparisonWaveforms`).
- `availableSeriesCounts` already widens to `listOf(1, 2, 3, 4, 5, 6, 12)` in compare mode
  (`MonitorViewModel.kt:30-31`), so a count of 4 is a valid menu option and the user can change it.

## Out of scope / already-correct (do not touch)

- **Compare button highlight (#1)** — already correct (`MonitorControlPanel.kt:344`).
- **2-column lead grid (#3)** — already correct (`ComparisonTargetDialog.kt:136`).
- The presets/save flow (`ComparisonPresetsDialog`, `SaveComparisonPresetDialog`, `applyPreset`) — the
  preset path intentionally keeps whatever targets the preset defines and is unaffected by the new
  default-seed branch (it only runs when `comparisonPresets.isEmpty()`).

## Acceptance criteria

1. In Teaching, tapping **Compare** (bottom panel) now enters compare mode (it was previously inert).
2. On entry with no saved presets: the monitor shows **4 panes in a single column**; **pane 0** is
   pre-filled with the **currently selected rhythm on lead II**; panes 1–3 are placeholders that open the
   target dialog when tapped.
3. The **Compare** tab is visibly highlighted (accent fill) while compare mode is active, and clears when
   it is exited.
4. The target dialog's lead picker shows all 12 leads as a **2-column × 6-row** grid (already true —
   just confirm it didn't regress).
5. Exiting compare mode restores the standard **12-lead grid** and clears the per-pane targets.
6. Entering compare mode when saved presets exist still opens the presets dialog (unchanged).

## Files

- `app/src/main/java/com/example/cardiosimulator/ui/screens/MainScreen.kt` — wire `onCompareClick`
  (`:354`).
- `app/src/main/java/com/example/cardiosimulator/ui/viewmodels/MonitorViewModel.kt` — rewrite
  `toggleCompareMode` (`:242`).
- *(verify only, no edits)* `ui/panels/MonitorControlPanel.kt:344`,
  `ui/dialogs/ComparisonTargetDialog.kt:136`.

## Windows reference (already shipped 2026-06-29)

- `src/CardioSimulator.App/Controls/MonitorControlPanel.xaml.cs` — `UpdateTexts()` sets
  `CompareTab.IsActive = mode.IsCompareMode` (the #1 analog Android already has).
- `src/CardioSimulator.App/ViewModels/MonitorViewModel.cs` — `ToggleCompareMode` seeds
  `[0] = ComparisonTarget(id, Lead.II)` only.
- `src/CardioSimulator.App/Screens/MainScreen.xaml.cs` — `EnterCompareWithDefaults` sets
  `SetSeriesCount(4)` + `SetSeriesScheme(OneColumn)`; `ExitCompare` resets to 12 / `Grid` and clears
  targets + comparison waveforms.
- `src/CardioSimulator.App/Controls/ComparisonTargetDialog.cs` — lead picker is a fixed 2-column ×
  6-row grid of toggle buttons (the #3 analog Android already has via `LazyVerticalGrid`).
