# Tri-state highlight on the Electrodes tab (Android parity)

**Status:** active
**Owner:** AI Assistant
**Started:** 2026-06-29

**Direction:** **Windows → Android.** The Windows port (`CardioSimulatorWin`) shipped this on
2026-06-29; Android must catch up. The Windows port is the reference for behaviour/visuals — match it,
adapting idioms to Kotlin/Compose.

---

## Goal

Make the **Электроды** (Electrodes) tab in the monitor control panel signal the electrode-hookup
state at a glance, so a student (or instructor) can see a fault is active without opening the window:

- **Never used** → the tab keeps its **original neutral** look (inactive).
- **"Все ок" (Ok) chosen** → tab fills **green** (the app accent) — hookup confirmed correct.
- **"Перепутаны" (Swapped) or "Смещение" (Displacement) active** → tab fills **red** — a fault is on
  the live trace.

This builds directly on the electrode-fault feature already ported in
`docs/plans/completed/2026-06-android-electrode-fault-parity.md` (the `ElectrodeState` enum, the
`ElectrodeFault` transform, `setElectrodeState`, and the segmented-control dialog all exist). This plan
is **only the button highlight** on top of that.

### Why now

The Windows port added this on 2026-06-29 — a small, self-contained visual increment on a feature that
already has full Android parity. Low-risk, and it closes a real usability gap (a wiring fault is
currently invisible once the window is closed).

## Current state (Android)

- **Panel button** — `ui/panels/MonitorControlPanel.kt:194-199`. The Electrodes `Tab` currently
  highlights green **while the dialog overlay is open**:
  ```kotlin
  Tab(
      text = stringResource(R.string.monitor_electrodes),
      onClick = { viewModel.setShowElectrodes(!monitorMode.showElectrodes) },
      isActive = monitorMode.showElectrodes,          // ← open/closed highlight (to be replaced)
      modifier = Modifier.weight(1f)
  )
  ```
- **`Tab` composable** — `ui/components/Tab.kt`. `resolvedBgColor` (`:51-56`) maps `isActive -> AccentGreen`
  (the only active colour), and `resolvedContentColor` (`:58-63`) maps `isActive -> OnAccent` (white).
  There is **no** active-colour override parameter — every active tab is green.
- **Domain** — `domain/MonitorModeModel.kt`. `enum class ElectrodeState { Ok, Swapped, Displacement }`
  (`:15`); `MonitorModeModel` carries `electrodeState: ElectrodeState = ElectrodeState.Ok` (`:74`) and
  `showElectrodes: Boolean = false` (`:75`). **No** "user has chosen" flag — so the default `Ok` is
  indistinguishable from an explicitly-selected `Ok` (exactly the gap that needs a flag).
- **ViewModel** — `ui/viewmodels/MonitorViewModel.kt:218-220`:
  ```kotlin
  fun setElectrodeState(state: ElectrodeState) {
      _monitorMode.update { it.copy(electrodeState = state) }
  }
  ```
- **Dialog wiring** — `ui/screens/TeachingScreen.kt:270` `onSelectState = { monitorViewModel.setElectrodeState(it) }`.
  This is the **single** entry point that changes `electrodeState`, so it's the right place to mark "user set".
- **Colour tokens** — `ui/theme/Color.kt`: `AccentGreen = Color(0xFF33A06A)` (`:6`),
  `OnAccent = Color(0xFFFFFFFF)` (`:8`). Same accent as Windows. No fault-red token yet.

### Windows reference files (read these first)

| Concern | Windows file / member |
|---|---|
| `Tab` active-colour override (`ActiveBrush` DP, used in `ApplyVisualState`) | `src/CardioSimulator.App/Controls/Tab.xaml.cs` |
| "User has chosen" flag (`ElectrodeStateUserSet`) + `SetElectrodeState` sets it | `src/CardioSimulator.App/ViewModels/MonitorViewModel.cs` |
| Tri-state logic + fault-red `#D33A2F` (`ApplyElectrodesVisual`) | `src/CardioSimulator.App/Controls/MonitorControlPanel.xaml.cs` |

On Windows the highlight is computed in `ApplyElectrodesVisual()`:

```csharp
if (!_viewModel.ElectrodeStateUserSet) { ElectrodesTab.IsActive = false; return; }  // original/neutral
var ok = _viewModel.MonitorMode.ElectrodeState == ElectrodeState.Ok;
ElectrodesTab.ActiveBrush = ok ? AppTheme.Accent : ElectrodeFaultFill;              // green vs red
ElectrodesTab.IsActive = true;
```

## Non-goals

- Don't touch the electrode-fault transform, the dialog's segmented control, the RA/LA dot recolour, the
  V-group dim, or the caption — all already shipped.
- Don't add a fault highlight to any **other** tab; `activeColor` defaults to `AccentGreen`, so every
  existing active tab (Artifacts, pQRSt, EOS, Tips, Compare, …) is byte-for-byte unchanged.
- Don't persist the new flag (a fresh session starts neutral — matches the un-persisted `electrodeState`).
- Don't change the 3D / EOS / Tips buttons or the Testing/Examination/OSKE panels.

## Plan

### Phase 1 — Fault-red colour token

In `ui/theme/Color.kt`, beside `AccentGreen` (`:6`):
```kotlin
val ElectrodeFaultRed = Color(0xFFD33A2F)   // Windows ElectrodeFaultFill
```

### Phase 2 — `Tab`: active-colour override

In `ui/components/Tab.kt`, add a parameter (default keeps every other tab green):
```kotlin
activeColor: Color = AccentGreen,
```
and use it in `resolvedBgColor` (`:51-56`):
```kotlin
val resolvedBgColor = when {
    !enabled    -> Color.LightGray.copy(alpha = 0.3f)
    showChevron -> PanelBackground
    isActive    -> activeColor          // ← was AccentGreen
    else        -> backgroundColor
}
```
Leave `resolvedContentColor` (`:58-63`) as-is — `OnAccent` (white) reads on both green and red.

### Phase 3 — Domain: "user has chosen" flag

In `domain/MonitorModeModel.kt`, add to `MonitorModeModel` (next to `electrodeState`, `:74`):
```kotlin
val electrodeStateUserSet: Boolean = false,
```
Default `false`; **not** read/written in any prefs (transient, like `electrodeState`).

> **Idiom note / divergence from Windows:** Windows tracks this as a `[ObservableProperty]` on the
> view-model. The Compose-idiomatic place is the `MonitorModeModel` StateFlow the panel already collects
> (`collectAsState`), so the button recomposes for free. Functionally identical.

### Phase 4 — ViewModel: set the flag

In `MonitorViewModel.kt:218-220`:
```kotlin
fun setElectrodeState(state: ElectrodeState) {
    _monitorMode.update { it.copy(electrodeState = state, electrodeStateUserSet = true) }
}
```
This is the only mutator of `electrodeState`, so the flag flips exactly when the student picks a state in
the dialog (Ok / Swapped / Displacement).

### Phase 5 — Panel: tri-state highlight on the Electrodes tab

In `ui/panels/MonitorControlPanel.kt:194-199`, replace the open/closed `isActive` with the hookup tri-state:
```kotlin
val electrodeFault = monitorMode.electrodeState != ElectrodeState.Ok
Tab(
    text = stringResource(R.string.monitor_electrodes),
    onClick = { viewModel.setShowElectrodes(!monitorMode.showElectrodes) },
    isActive = monitorMode.electrodeStateUserSet,                              // neutral until used
    activeColor = if (electrodeFault) ElectrodeFaultRed else AccentGreen,      // red fault / green ok
    modifier = Modifier.weight(1f)
)
```
Imports: `com.example.cardiosimulator.domain.ElectrodeState`, and the two colour tokens
(`ElectrodeFaultRed`, `AccentGreen`) from `ui.theme`.

> **Behaviour divergence (intentional):** this **drops the "dialog is open" green highlight**
> (`isActive = showElectrodes`) in favour of the hookup tri-state, matching Windows — where the
> Electrodes window is a modal `ContentDialog` with no open-state highlight. The window still opens/closes
> on tap via `setShowElectrodes`; only the tab's colour semantics change. Don't try to combine both into
> one `isActive` (an open-but-not-yet-chosen window would wrongly show green, breaking "neutral until
> chosen").

## Risks & open questions

- **Recomposition:** `monitorMode` is already `collectAsState()` in the panel (`:57`), so adding fields to
  the model and reading them in the `Tab` call recomposes correctly — no extra `remember`/keys needed.
- **`copy()` safety:** `MonitorModeModel` is a data class with all-default fields; adding `electrodeStateUserSet`
  with a default is source-compatible with every existing `copy(...)` and the no-arg constructor.
- **Other screens:** Testing/Examination/OSKE each build their own `MonitorViewModel` and never open the
  Электроды window, so `electrodeStateUserSet` stays `false` there — tab neutral, no change. (Those panels
  may not even show an Electrodes tab; nothing to do regardless.)
- **Open question (defer, match Windows = no):** should reopening the window and re-confirming "Все ок"
  ever return the tab to neutral? Windows keeps it green once chosen (the flag never clears within a
  session). Mirror that — do not add a "reset to neutral" path.

## Verification

- Fresh Teaching monitor: the **Электроды** tab is **neutral** (no fill) before the window is opened.
- Open the window, tap **Все ок** → tab fills **green**; close the window → stays green.
- Tap **Перепутаны** or **Смещение** → tab fills **red**; the live trace shows the fault (already wired);
  close the window → stays red.
- Back to **Все ок** → tab returns to green.
- Every **other** active tab (Artifacts `(n)`, pQRSt, EOS, Tips, Compare) is still **green** when active —
  unchanged.
- App builds; no new strings (label `monitor_electrodes` already exists in all four locales).

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Theme: `ElectrodeFaultRed` token | 1 | One colour, `#D33A2F` |
| 2 | `Tab`: `activeColor` override (defaults to AccentGreen) | 2 | No behaviour change for other tabs |
| 3 | Model + ViewModel: `electrodeStateUserSet` flag | 3–4 | Set in `setElectrodeState`, not persisted |
| 4 | Panel: tri-state highlight on the Electrodes tab | 5 | Replaces the open/closed highlight |

*(Phases are tiny; 1–4 can ship as a single PR if preferred.)*

---

## Cross-reference

Windows session 2026-06-29 (CardioSimulatorWin): added `Tab.ActiveBrush` (DP, used in
`ApplyVisualState`), `MonitorViewModel.ElectrodeStateUserSet` (observable, set in `SetElectrodeState`),
and `MonitorControlPanel.ApplyElectrodesVisual()` with the fault-red fill `#D33A2F`. Builds on the
already-completed `2026-06-android-electrode-fault-parity.md`.
