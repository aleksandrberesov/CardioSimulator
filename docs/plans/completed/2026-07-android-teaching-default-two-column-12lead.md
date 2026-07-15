# Teaching monitor default = 12-lead, 2-column — Android parity

**Status:** active
**Owner:** (unassigned)
**Started:** 2026-07-07
**Related:** Windows implementation (source of truth) in `CardioSimulatorWin` — `MainScreen.BuildForMode` Teaching case.

## Goal

When the user enters **Teaching** mode, the monitor ("All rhythms" view) should open as a
**12-lead, 2-column** layout by default — instead of the 4-column grid. Each lead trace is then
wider and easier to read. The user can still change the layout at runtime from the monitor control
panel; this only sets the **default that Teaching opens with**.

Scope is **Teaching only**. Testing / Examination / OSKE are untouched.

## Windows source (faithful reference)

Single change in `src/CardioSimulator.App/Screens/MainScreen.xaml.cs`, `BuildForMode()`, the
`OperatingMode.Teaching` case:

```csharp
case OperatingMode.Teaching:
    // Customer default: Teaching opens the monitor as a 12-lead, 2-column layout
    // (not the 4-column Grid) so each lead trace is wider and easier to read.
    _monitorViewModel.SetSeriesCount(12);
    _monitorViewModel.SetSeriesScheme(SeriesScheme.TwoColumn);   // was SeriesScheme.Grid
    _rhythmViewModel.SetCourseFilter(appVm.SelectedCoursePathologies);
```

Only the scheme token changed: `SeriesScheme.Grid` → `SeriesScheme.TwoColumn`. `SetSeriesCount(12)`
was already there. On Windows, `BuildForMode` runs on entering the mode and re-creates a fresh
`MonitorViewModel`, so this forces the layout **every time Teaching is entered** (guarded against
same-mode rebuilds like a language change by `_lastBuiltMode`).

`SeriesScheme.TwoColumn` maps to 2 columns on both platforms
(`MonitorMode.cs` `MaxColumns()` = 2; Android `Monitor.kt:107` `TwoColumn -> 2`).

### What did NOT change on Windows (keep parity — do not touch these on Android)
- **Compare-mode exit** still restores the **4-column Grid**, not TwoColumn
  (`MainScreen.xaml.cs` `ExitCompare()` → `SetSeriesScheme(SeriesScheme.Grid)`).
  ⇒ Leave Android's `MonitorViewModel.toggleCompareMode` exit branch
  (`MonitorViewModel.kt:288-294`, `count = 12, seriesScheme = SeriesScheme.Grid`) **as-is**.
- Testing / Examination / OSKE defaults are unchanged.

## Key divergence — Android has no per-mode default forcing today

Unlike Windows (which force-sets count+scheme per mode in `BuildForMode`), Android's monitor default
comes straight from the data-class default and saved prefs — there is **no code that forces Teaching
to 12/Grid**. So this is **not a one-token swap** on Android; you must *add* the Teaching default.

- `domain/MonitorModeModel.kt` — data-class defaults are `count = 1`, `seriesScheme = OneColumn`.
- `ui/viewmodels/MonitorViewModel.kt` `init {}` — only reads persisted prefs
  (`prefs.monitorSeriesCount` / `monitorSeriesScheme`, both `null` when unset → no override) and
  applies them with `persist = false`. The MonitorViewModel is created per mode
  (`MainScreen.kt:41-52`, `viewModel(key = selectedMode.id.name, …)`).
- The `count = 12 … SeriesScheme.Grid` and `TwoColumn` occurrences in `Monitor.kt:340-450` are
  **`@Preview` composables only** (Android Studio design-time) — not runtime defaults.
- `TeachingScreen.kt:112-117` already has the Windows analog of the Teaching-entry reset: a
  `LaunchedEffect(Unit)` guarded by a `rememberSaveable lastBuiltMode` that runs once per Teaching
  entry and calls `appViewModel.selectCourse(ALL_RHYTHMS_ID)` — the exact counterpart of Windows'
  `appVm.SelectCourse(null)` in the same `BuildForMode` Teaching case.

## Plan

Add the layout seed to the **existing Teaching-entry block** — the tightest analog to Windows'
`BuildForMode` Teaching case (same trigger: entering Teaching; same guard: not on same-mode rebuild).

`ui/screens/TeachingScreen.kt`, in the `LaunchedEffect(Unit)` at ~line 112:

```kotlin
LaunchedEffect(Unit) {
    if (lastBuiltMode != OperatingMode.Teaching) {
        appViewModel.selectCourse(AppViewModel.ALL_RHYTHMS_ID)
        // Customer default: Teaching opens the monitor as a 12-lead, 2-column layout
        // (not the 4-column grid) so each lead trace is wider and easier to read.
        monitorViewModel.setSeriesCount(12)
        monitorViewModel.setSeriesScheme(SeriesScheme.TwoColumn)
        lastBuiltMode = OperatingMode.Teaching
    }
}
```

Add the import `import com.example.cardiosimulator.domain.SeriesScheme` if not already present.

That's the whole change — no model, prefs, or panel edits.

## Notes / gotchas

- **Async-init race is benign.** `MonitorViewModel.init` reads prefs in a `viewModelScope.launch`
  where the DataStore `.first()` suspends on IO, so this synchronous `LaunchedEffect` body runs and
  completes first. On a fresh install the pref is `null` (no override); after the first entry it is
  `TwoColumn` (see persist note) — either way it converges to TwoColumn. No first-frame flip in
  practice.
- **persist flag — either is fine; recommend the default `persist = true`** to mirror Windows
  (`SetSeriesScheme` writes the pref). Because the `lastBuiltMode` guard re-forces on every Teaching
  entry, the persisted value can't drift the default away from TwoColumn. (If you prefer not to write
  prefs, `persist = false` also yields "always TwoColumn on entry" — the guarded seed wins regardless.)
- **`<ecg>` embed / MonitorOverlay path is unaffected.** The seed runs once on Teaching entry, before
  any lecture-embed interaction; embeds that open the monitor with handpicked leads/scheme apply
  their own values later, so nothing is clobbered.
- **Do not change** `MonitorModeModel.kt` defaults or the `Monitor.kt` previews — that would leak into
  every mode. Keep the seed in the Teaching-entry effect.

## Outcome

- **Result:** Completed.
- **PRs:** N/A (implemented directly).
- **Deviations from plan:** None.
- **Follow-ups spawned:** None.
```
