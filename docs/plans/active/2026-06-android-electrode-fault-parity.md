# Wire the Electrodes window's state buttons to a real ECG hookup fault (Android parity)

**Status:** active
**Owner:** AI Assistant
**Started:** 2026-06-28

**Direction:** **Windows → Android.** The Windows port (`CardioSimulatorWin`) shipped this on
2026-06-28; Android must catch up. The Windows port is the reference for behaviour/visuals — match it,
adapting idioms to Kotlin/Compose.

---

## Goal

Make the three state buttons in the **Электроды** window (`Все ок` / `Перепутаны` / `Смещение` =
All OK / Swapped / Displacement) actually *do* something instead of being inert. They become a
mutually-exclusive segmented control that applies a real **electrode-hookup fault** to the live monitor
trace:

- **Все ок** — correct hookup; trace unchanged.
- **Перепутаны** — the classic **RA/LA limb-electrode reversal**: lead **I inverts**, **II↔III** swap,
  **aVR↔aVL** swap; **aVF and every precordial lead are unchanged** (Wilson's central terminal — the
  average of RA+LA+LL — is unaffected by an RA/LA exchange). Medically correct, and the single most-taught
  electrode error.
- **Смещение** — precordial electrodes off their landmarks: **V1–V6 amplitude ×0.55** (poor R-wave
  progression); limb leads untouched.

The window also reflects the selection visually (active button filled blue; RA/LA legend dots swap colour
for Swapped; the V-lead group dims for Displacement; a caption explains the effect). The fault **stays
applied after the window closes** so a student can study the distorted trace.

### Why now

The Windows port implemented this on 2026-06-28 (the Android-parity plan
`docs/plans/sync/2026-06-android-monitor-panel-parity.md` Phase 6 previously listed the state buttons as a
scaffold — that note is now stale). This is a small, self-contained feature with a unit-tested pure
transform on the Windows side, so the port is low-risk.

## Current state (Android)

The Электроды window already exists as a **scaffold** — the three buttons are plain `DialogBlueButton`s
with no action:

- **Dialog** — `ui/dialogs/ElectrodesDialog.kt`. `ElectrodesDialog(onDismiss: () -> Unit)` (`:27`).
  The state buttons are at `:99-103`:
  ```kotlin
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      DialogBlueButton(stringResource(R.string.electrodes_state_ok), Modifier.weight(1f))
      DialogBlueButton(stringResource(R.string.electrodes_state_swapped), Modifier.weight(1f))
      DialogBlueButton(stringResource(R.string.electrodes_state_displacement), Modifier.weight(1f))
  }
  ```
  The RA/LA legend rows are `:86-87` (`LegendRow(Color(0xFFE53935), …RA)`, `LegendRow(Color(0xFFFDD835), …LA)`);
  the six V rows are `:91-96`. `LegendRow` is the private composable at `:150`.
- **Invoked from** — `ui/screens/TeachingScreen.kt:260-262`:
  ```kotlin
  if (mode.showElectrodes) {
      ElectrodesDialog(onDismiss = { monitorViewModel.setShowElectrodes(false) })
  }
  ```
- **Waveform pipeline (the key difference from Windows):** Android applies artifacts/filter **per-lead,
  inside `ui/display/Lead.kt`** — each `LeadView(...)` cell receives `artifacts = mode.artifacts` and
  processes its own samples. There is **no central `UpdateWaveforms`** like Windows
  (`MonitorView.UpdateWaveforms`). The raw lead map is resolved in the screen:
  - `TeachingScreen.kt:238` `val waveforms by rhythmViewModel.waveforms.collectAsState()` → `Map<Lead, Points>`.
  - Per-lead lookup at `:438` `val points = lead?.let { waveforms[it] }…` then `LeadView(points = …, artifacts = mode.artifacts, …)` (`:444-453`).
  - SQI reads `waveforms[firstLeadForSqi]` at `:465`.
  - Compare-mode panes use `comparisonWaveforms[index]` (`:428`), a **separate** map.
  > **Consequence:** the RA/LA swap is a *cross-lead* transform (new II = old III, etc.), so it **cannot**
  > live inside `Lead.kt` (which only sees one lead's samples). It must be applied to the **whole
  > `Map<Lead, Points>`** before the per-lead lookup at `:438`. The per-lead artifacts then run *after* the
  > remap — which is exactly the right order (a wiring fault is at the source, noise is downstream).
- **Domain** — `domain/MonitorModeModel.kt`. The `MonitorModeModel` data class (`:58-80`) already carries
  `artifacts`, `showElectrodes`, etc. (`:69`, `:71`). `enum class EcgArtifact` is at `:22-29`.
- **ViewModel** — `ui/viewmodels/MonitorViewModel.kt`. Setters that `_monitorMode.update { it.copy(...) }`,
  e.g. `setArtifacts` (`:210-212`), `setShowElectrodes` (`:218-220`). Several flags (`setShowImpulseLabels`,
  `setShowElectrodes`, …) are **not persisted** — same as the electrode fault should be.
- **Lead algebra reference** — `domain/DerivedLeads.kt` is the existing Einthoven/Goldberger combiner
  (`combineIII_aVR_aVL_aVF`, `:29`), a good style template for the new pure transform.
- **Types** — `enum class Lead { I, II, III, aVR, aVL, aVF, V1..V6 }` in `domain/Pathology.kt:17-18`
  (identical order to Windows). `data class Points(val values: List<Float>)` in `data/Points.kt`.
- **Samples are baseline-zeroed.** On Windows the trace `Points.values` are deviations centred on 0
  (verified by `PathologyRepositoryTests.LeadWaveform_DirectLead_ZeroesBaseline`). Android shares the
  pipeline — `Lead.kt` plots `points.values` directly against a mid-cell baseline — so negation and
  amplitude scaling around 0 are correct. **Verify** by glancing at the Android `RhythmViewModel`/repository
  that builds `waveforms` (it should subtract the manifest `baseline` like Windows `LeadWaveform`).
- **Strings** — the electrodes block lives in all four locales: `values/strings.xml:105-120`,
  `values-ru/strings.xml:99-114`, `values-es/strings.xml:94-109`, `values-zh/strings.xml:93-108`. There is
  **no** caption string yet.

### Windows reference files (read these first)

| Concern | Windows file |
|---|---|
| `ElectrodeState` enum + `MonitorModeModel.ElectrodeState` field | `src/CardioSimulator.Core/Domain/MonitorMode.cs` |
| **Pure lead transform** (the algebra to port 1:1) | `src/CardioSimulator.Core/Domain/ElectrodeFault.cs` |
| Unit tests for the transform (mirror these) | `tests/CardioSimulator.Core.Tests/ElectrodeFaultTests.cs` |
| View-model setter `SetElectrodeState` (not persisted) | `src/CardioSimulator.App/ViewModels/MonitorViewModel.cs` |
| Injection point (apply before artifacts/filter; not on compare panes) | `src/CardioSimulator.App/Controls/MonitorView.cs` (`UpdateWaveforms`) |
| Dialog: segmented control + visual feedback + caption | `src/CardioSimulator.App/Controls/ElectrodesDialog.cs` |
| Strings (4 locales): `electrodes_state_caption_*` | `src/CardioSimulator.App/Localization/AppStrings.cs` |

## Non-goals

- Don't change the artifact/filter pipeline, the SQI card, ruler/caliper, zoom/pan, or the
  significant-point overlay.
- Don't apply the fault to **compare-mode** panes (independent reference rhythms — a hookup fault is
  meaningless there).
- Don't persist the electrode state (a fresh session must start correctly wired — matches Windows).
- Don't touch the 3D / EOS / Tips windows.
- Don't restyle `DialogBlueButton` (shared with `Heart3DDialog`) — add a local selectable cell instead.

## Plan

### Phase 1 — Domain: `ElectrodeState` enum + model field

In `domain/MonitorModeModel.kt`:
```kotlin
enum class ElectrodeState { Ok, Swapped, Displacement }
```
and add to `MonitorModeModel` (next to `showElectrodes`, `:71`):
```kotlin
val electrodeState: ElectrodeState = ElectrodeState.Ok,
```
Default `Ok`. **Not** added to any prefs read/write (transient teaching demo).

### Phase 2 — Pure transform: `ElectrodeFault` (mirror Windows + its tests)

New file `domain/ElectrodeFault.kt`. Pure algebra on baseline-zeroed samples; copies the map, never
mutates input, skips missing leads (so a 6-limb-only recording still works):
```kotlin
package com.example.cardiosimulator.domain

import com.example.cardiosimulator.data.Points

object ElectrodeFault {
    private const val DISPLACEMENT_GAIN = 0.55f
    private val PRECORDIAL = listOf(Lead.V1, Lead.V2, Lead.V3, Lead.V4, Lead.V5, Lead.V6)

    /** Returns [waveforms] transformed for [state]; for [ElectrodeState.Ok] (or empty) returns it as-is. */
    fun apply(waveforms: Map<Lead, Points>, state: ElectrodeState): Map<Lead, Points> {
        if (state == ElectrodeState.Ok || waveforms.isEmpty()) return waveforms
        val result = waveforms.toMutableMap()
        when (state) {
            ElectrodeState.Swapped -> {
                waveforms[Lead.I]?.let { result[Lead.I] = it.scale(-1f) }   // lead I inverts
                swap(result, waveforms, Lead.II, Lead.III)
                swap(result, waveforms, Lead.aVR, Lead.aVL)
                // aVF and V1..V6 are unchanged by an RA/LA exchange.
            }
            ElectrodeState.Displacement ->
                PRECORDIAL.forEach { v -> waveforms[v]?.let { result[v] = it.scale(DISPLACEMENT_GAIN) } }
            ElectrodeState.Ok -> {}
        }
        return result
    }

    private fun swap(result: MutableMap<Lead, Points>, src: Map<Lead, Points>, a: Lead, b: Lead) {
        val pa = src[a]; val pb = src[b]
        if (pa != null) result[b] = pa
        if (pb != null) result[a] = pb
    }

    private fun Points.scale(factor: Float) = Points(values.map { it * factor })
}
```
Add unit tests `ElectrodeFaultTests` (port `tests/CardioSimulator.Core.Tests/ElectrodeFaultTests.cs`):
Ok returns same instance; Swapped inverts I / swaps II↔III / aVR↔aVL and leaves aVF + V unchanged and
does not mutate input; Displacement attenuates V1–V6 ×0.55 and leaves limb leads untouched; the
6-limb-only map doesn't throw and skips missing leads.

### Phase 3 — ViewModel setter

In `MonitorViewModel.kt`, beside `setArtifacts` (`:210`):
```kotlin
fun setElectrodeState(state: com.example.cardiosimulator.domain.ElectrodeState) {
    _monitorMode.update { it.copy(electrodeState = state) }
}
```
No persistence.

### Phase 4 — Injection point in `TeachingScreen.kt` (apply to the whole map, before per-lead artifacts)

After collecting `waveforms` (`:238`), derive the display map and use it everywhere the **live** (non-compare)
leads are read:
```kotlin
val displayWaveforms = remember(waveforms, mode.electrodeState) {
    ElectrodeFault.apply(waveforms, mode.electrodeState)
}
```
- Replace the per-lead lookup at `:438`: `val points = lead?.let { displayWaveforms[it] }…`.
- Replace the SQI read at `:465`: `val sqiSignal = displayWaveforms[firstLeadForSqi]`.
- **Leave the compare-mode branch (`comparisonWaveforms[index]`, `:428`) untouched.**
The per-lead `artifacts`/`filterType` inside `LeadView` then run *after* the remap — correct order.
(Import `com.example.cardiosimulator.domain.ElectrodeFault`.)

### Phase 5 — Dialog: segmented control + visual feedback + caption

Change the signature so the screen owns the state (mirrors the existing `onDismiss`-only pattern):
```kotlin
@Composable
fun ElectrodesDialog(
    electrodeState: ElectrodeState,
    onSelectState: (ElectrodeState) -> Unit,
    onDismiss: () -> Unit
)
```
- Replace the three `DialogBlueButton`s (`:99-103`) with a **local** selectable cell, e.g.
  `StateButton(text, selected = electrodeState == s, onClick = { onSelectState(s) })`:
  active = filled `WindowsBlue` + white text; inactive = white fill + blue border + blue text (don't reuse
  `DialogBlueButton` — it's shared with Heart3DDialog).
- **RA/LA dots:** make the RA/LA `LegendRow` colours depend on state — Swapped shows RA=yellow, LA=red
  (the physical red/yellow electrodes exchanged); otherwise RA=red (`0xFFE53935`), LA=yellow (`0xFFFDD835`).
- **V-lead group:** wrap the six V `LegendRow`s (`:91-96`) in a `Column` and set
  `Modifier.alpha(if (electrodeState == Displacement) 0.45f else 1f)`.
- **Caption:** a `Text` below the buttons bound to `electrodes_state_caption_{ok,swapped,displacement}`.
- Wire the call site (`TeachingScreen.kt:260-262`):
  ```kotlin
  if (mode.showElectrodes) {
      ElectrodesDialog(
          electrodeState = mode.electrodeState,
          onSelectState = { monitorViewModel.setElectrodeState(it) },
          onDismiss = { monitorViewModel.setShowElectrodes(false) }
      )
  }
  ```
  The fault stays applied after dismiss because it lives on `mode.electrodeState`, not on `showElectrodes`.

### Phase 6 — Strings (all four locales)

Add three keys after `electrodes_caption_cross` in each `strings.xml`. **Exact values from Windows
`AppStrings.cs`:**

| key | en | ru | zh | es |
|---|---|---|---|---|
| `electrodes_state_caption_ok` | Electrodes connected correctly. | Электроды подключены правильно. | 电极连接正确。 | Electrodos conectados correctamente. |
| `electrodes_state_caption_swapped` | RA/LA electrodes swapped: lead I is inverted, II and III are interchanged (as are aVR and aVL). | Перепутаны электроды RA/LA: отведение I инвертировано, II и III меняются местами (как и aVR с aVL). | RA/LA 电极接反：I 导联倒置，II 与 III 互换（aVR 与 aVL 也互换）。 | Electrodos RA/LA intercambiados: la derivación I se invierte, II y III se intercambian (igual que aVR y aVL). |
| `electrodes_state_caption_displacement` | Chest electrodes off their landmarks: reduced amplitude in leads V1–V6. | Грудные электроды смещены: снижена амплитуда отведений V1–V6. | 胸前电极偏离标准位置：V1–V6 导联振幅降低。 | Electrodos torácicos fuera de su posición: amplitud reducida en las derivaciones V1–V6. |

## Risks & open questions

- **Baseline-zeroed assumption:** the negation/attenuation only reads correctly if `waveforms` Points are
  centred on 0. Windows verified this; confirm the Android `RhythmViewModel`/repository subtracts the
  manifest `baseline` when building `waveforms` (it should — `Lead.kt` plots `values` against a mid-cell
  baseline). If for some reason a lead were stored as absolute ADC counts, attenuation would pull it toward
  0 instead of shrinking the deflection — but that's not how the pipeline works.
- **Cross-lead placement:** the swap **must** be applied to the map before the `:438` lookup, not inside
  `Lead.kt`. Don't be tempted to push it down per-lead.
- **Compare mode:** double-check the fault is not applied to `comparisonWaveforms`.
- **Other monitor screens** (Testing/Examination/OSKE) don't open the Электроды window and each has its own
  `MonitorViewModel`, so `electrodeState` stays `Ok` there — no change needed. (Only port the Phase 4
  injection into Teaching unless a second screen later grows an Electrodes entry point.)
- **`remember` keys:** key `displayWaveforms` on both `waveforms` and `mode.electrodeState` so it recomputes
  when either changes (a new rhythm or a button press).

## Verification

- Open the Электроды window on the Teaching monitor; the three buttons act as a single-choice segmented
  control (active one filled blue).
- **Перепутаны:** lead **I flips polarity**, **II and III swap**, **aVR and aVL swap**, aVF and V1–V6
  unchanged. RA/LA legend dots swap colour; caption shows the swapped text.
- **Смещение:** V1–V6 visibly lower-amplitude, limb leads unchanged; V-lead legend group dims; caption shows
  the displacement text.
- **Все ок** restores the normal trace.
- Closing the window **keeps** the fault applied; reopening reflects the current selection.
- Compare mode is unaffected.
- App builds; `ElectrodeFaultTests` pass; all three captions resolve in en/ru/zh/es (no fallbacks).

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Domain: `ElectrodeState` enum + model field | 1 | Pure addition, default Ok |
| 2 | `ElectrodeFault` transform + unit tests | 2 | Port of Windows `ElectrodeFault.cs` + tests |
| 3 | ViewModel `setElectrodeState` (not persisted) | 3 | One setter |
| 4 | Apply fault to live waveforms in `TeachingScreen` | 4 | Before per-lead artifacts; not on compare |
| 5 | Electrodes dialog: segmented control + feedback + caption | 5 | Signature change + wiring |
| 6 | Strings (4 locales): `electrodes_state_caption_*` | 6 | Values from `AppStrings.cs` |

---

## Cross-reference

Windows session 2026-06-28 (CardioSimulatorWin): `ElectrodeState` enum + `ElectrodeFault.cs` (unit-tested),
`MonitorViewModel.SetElectrodeState`, `MonitorView.UpdateWaveforms` injection, interactive
`ElectrodesDialog.cs`, and `electrodes_state_caption_*` strings. Supersedes the "state-button behaviour is a
scaffold" note in `docs/plans/sync/2026-06-android-monitor-panel-parity.md` Phase 6.
