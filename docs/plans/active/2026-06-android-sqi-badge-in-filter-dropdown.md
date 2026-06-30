# Move the SQI "Quality" badge off the monitor into the Filters dropdown (Android parity)

**Status:** active
**Owner:** AI Assistant
**Started:** 2026-06-29

**Direction:** **Windows → Android.** The Windows port (`CardioSimulatorWin`) shipped this on
2026-06-29; Android must catch up. The Windows port is the reference for behaviour/visuals — match it,
adapting idioms to Kotlin/Compose.

> **Supersedes** `docs/plans/completed/…sqi-badge-bottom-right` (the badge no longer floats on the
> monitor at all, so its corner position is moot once this lands). Don't "restore" the `BottomEnd`
> overlay as a regression.

---

## Goal

Stop overlaying the signal-quality (SQI) "Quality" card on top of the live ECG trace, and instead show
the quality readout **at the top of the monitor's Filters dropdown**. The readout reflects the quality
of the displayed (filtered) trace, so it belongs next to the filter chooser — and it stops occluding the
waveform. This mirrors the Windows change exactly.

### Why now

The Windows port made this move on 2026-06-29. It's a small, self-contained UX increment on a feature
that already has Android parity (`SqiCard` exists and works). Low-risk, and it removes a card that
currently covers the bottom-right of the trace.

## Current state (Android)

- **The badge composable** — `ui/components/SqiCard.kt`. Self-contained: takes `signal: DoubleArray`,
  `samplingRate: Double`, `modifier`. Computes SQI in a `LaunchedEffect` on `Dispatchers.Default`
  (`:38-57`, guard `signal.size < 500 → null`), then renders a **floating dark Card**
  (`Color.Black.copy(alpha = 0.4f)`, 1dp white-20% border, 8dp corner) with a coloured dot + quality +
  details (`:59-97`). Quality→colour map (`:70-74`): `"Excellent" → Green`, `"Barely acceptable" → Yellow`,
  else `Red`. The data holder is `data class SqiInfo(quality, sSqi, kSqi, pSqi)` (`:23-28`).
  > Note: it does **not** carry the lead, and it only matches `"Barely acceptable"` (Windows also matches
  > the compound `"Barely acceptable/Acceptable"` label).
- **Its only call site** — `ui/screens/TeachingScreen.kt:478-491`. Builds
  `displayWaveformsForSqi = ElectrodeFault.apply(waveforms, mode.electrodeState)` (`:479-481` — electrode
  fault only, **not filtered/artifacts**), takes `firstLeadForSqi`, and when `sqiSignal.values.size > 100`
  renders `SqiCard(...)` aligned `Alignment.BottomEnd`, `padding(bottom = 16.dp, end = 16.dp)`.
- **The Filters dropdown** — `ui/panels/MonitorControlPanel.kt:269-300`. A `Tab` (chevron) opens a
  `DropdownMenu` that iterates `EcgFilterType.entries` into `DropdownMenuItem`s calling
  `viewModel.setFilterType(filterType)`. No checkmark on the active filter today.
- **Panel signature** — `MonitorControlPanel(viewModel: MonitorViewModel, modifier, onTipsClick,
  onCompareClick, onStartStopClick)` (`:50-56`); already reads `val monitorMode by
  viewModel.monitorMode.collectAsState()` (`:57`). It has the **same** `MonitorViewModel` instance the
  screen uses — `MainScreen.kt:355-357` passes `viewModel = monitorViewModel`, the same object handed to
  `TeachingScreen`. So a new StateFlow on that VM flows straight from producer to dropdown.
- **ViewModel** — `ui/viewmodels/MonitorViewModel.kt`. `_monitorMode: MutableStateFlow<MonitorModeModel>`
  (`:27-28`); setters do `_monitorMode.update { it.copy(...) }`; `setFilterType` at `:214-216`. **No**
  `signalQuality` yet.
- **Where the filter is actually applied** — `ui/display/Lead.kt:46-74`. Artifacts + filter are applied
  **per-lead at render time** inside `remember(points, artifacts, filterType, calibration)`; the filter
  `when (filterType)` block is `:69-74` (calls the biosppy filters). **There is no central "processed"
  signal** — each `LeadView` filters its own copy. This is why the current SQI signal ignores the filter.
- **Strings** — `res/values/strings.xml:92-93`: `monitor_filters` = "Filters", `monitor_filter_none` =
  "No filters". RU/ZH/ES variants exist for the existing monitor strings. **No** `monitor_signal_quality`.

### Windows reference files (read these first)

| Concern | Windows file / member |
|---|---|
| Removed the on-monitor `_sqiCard` overlay; `UpdateSqi` now pushes a result instead of drawing | `src/CardioSimulator.App/Controls/MonitorView.cs` (`UpdateSqi` → `_monitorVm.SetSignalQuality(...)`, null when map empty / `< 100` samples) |
| Observable readout + setter + data record | `src/CardioSimulator.App/ViewModels/MonitorViewModel.cs` — `[ObservableProperty] SignalQualityInfo? _signalQuality`, `SetSignalQuality(...)`, `record SignalQualityInfo(Quality, SSqi, KSqi, PSqi, PrimaryLead)` |
| Filters menu rebuilt to host the badge | `src/CardioSimulator.App/Controls/MonitorControlPanel.xaml.cs` — `OnFiltersClick` now a custom `Flyout`: `BuildSqiBadge()` (dot + `Quality: <q> (<lead>)` + `sSQI/kSQI/pSQI`; muted "unavailable" placeholder when null) + divider + `AddFilterRow` (check glyph on the active filter, applies + hides). `QualityBrush(quality)` maps Excellent→LimeGreen, "Barely acceptable[/Acceptable]"→Gold, else→Crimson |
| Strings | `src/CardioSimulator.App/Localization/AppStrings.cs` — `monitor_signal_quality` ("Quality"/"Качество"), `monitor_signal_quality_unavailable` (EN/RU) |

**Windows data flow (the shape to mirror):** `MonitorView.UpdateSqi(processed)` computes the indices on
the **filtered** trace and calls `viewModel.SetSignalQuality(info)`. The Filters flyout reads
`viewModel.SignalQuality` at open time and renders the badge above the filter rows.

## Non-goals

- Don't change the SQI maths (`Sqi.zz2018` / `ssqi` / `ksqi` / `psqi`, the Hamilton/SSF detectors) — only
  *where* the result is computed-from and *where* it's shown.
- Don't move SQI off a background dispatcher — keep the `Dispatchers.Default` computation.
- Don't touch compare-mode panes, the EOS/Tips overlays, or any other dropdown.
- Don't localise the raw quality label itself ("Excellent", …) — Windows shows the raw ZZ2018 English
  string too; only the "Quality:" prefix and the "unavailable" placeholder are localised.

## Plan

### Phase 1 — Lift `SqiInfo` out of the UI layer + add the lead

The view-model will hold `SqiInfo`, so it shouldn't live in `ui.components` (a VM → UI dependency).
Move `data class SqiInfo` into a neutral spot — e.g. `signals/biosppy/SqiInfo.kt` — and add the lead so
the badge can show it like Windows (`Quality: <q> (<lead>)`):
```kotlin
data class SqiInfo(
    val quality: String,
    val sSqi: Double,
    val kSqi: Double,
    val pSqi: Double,
    val lead: Lead? = null,
)
```
Update the `import` in `SqiCard.kt` (and anywhere else) to the new location.

### Phase 2 — Extract the computation into a reusable suspend helper

Pull the `LaunchedEffect` body out of `SqiCard.kt` into a plain function so both the producer and (future)
callers share one implementation, e.g. `signals/biosppy/SqiCompute.kt`:
```kotlin
suspend fun computeSqi(signal: DoubleArray, samplingRate: Double): SqiInfo? =
    withContext(Dispatchers.Default) {
        if (signal.size < 500) return@withContext null
        try {
            val det1 = QrsSegmenters.hamiltonSegmenter(signal, samplingRate)
            val det2 = QrsSegmenters.ssfSegmenter(signal, samplingRate)
            SqiInfo(
                quality = Sqi.zz2018(signal, det1, det2, samplingRate, mode = "fuzzy"),
                sSqi = Sqi.ssqi(signal), kSqi = Sqi.ksqi(signal), pSqi = Sqi.psqi(signal),
            )
        } catch (e: Exception) { null }
    }
```
> Keep the existing `< 500` guard (Android's current threshold) — don't silently change it to Windows'
> `< 100`. Call it out in Risks.

### Phase 3 — ViewModel: expose the readout (mirror `SignalQuality`)

In `MonitorViewModel.kt`, beside `_monitorMode`:
```kotlin
private val _signalQuality = MutableStateFlow<SqiInfo?>(null)
val signalQuality: StateFlow<SqiInfo?> = _signalQuality.asStateFlow()
fun setSignalQuality(info: SqiInfo?) { _signalQuality.value = info }
```
This is the Android analogue of Windows' `[ObservableProperty] SignalQuality` + `SetSignalQuality`.

### Phase 4 — Presentational badge composable (for the dropdown surface)

Replace the floating-Card body in `SqiCard.kt` with a presentational `SqiBadge(info: SqiInfo?, modifier)`
that renders inside a **light dropdown** (not a dark floating card): a `Row` with the coloured dot + a
`Column(quality line, details line)`. Show a muted placeholder when `info == null`:
- title: `"${stringResource(R.string.monitor_signal_quality)}: ${info.quality}" + lead?.let { " (${it.name})" }`,
  or `"… : —"` when null.
- details: `"sSQI (skew): %.2f | kSQI (kurt): %.2f | pSQI (flat): %.1f%%".format(sSqi, kSqi, pSqi*100)`,
  or `stringResource(R.string.monitor_signal_quality_unavailable)` when null.
- dot colour: `"Excellent" → Green`, `"Barely acceptable", "Barely acceptable/Acceptable" → amber/Gold`,
  else `Red`; muted grey when null. (Add the compound-label case to match Windows.)

Drop the dark `Card` container / black background — use on-surface text colours so it reads in the menu.

### Phase 5 — TeachingScreen: compute + push, delete the overlay

Remove the `SqiCard(...)` overlay at `TeachingScreen.kt:478-491`. In its place (still inside the monitor
`Box`, but with **no visible UI**), compute and publish:
```kotlin
val firstLeadForSqi = mode.leadOrder?.firstOrNull() ?: LEAD_ORDER.first()
val displayWaveformsForSqi = remember(waveforms, mode.electrodeState) {
    ElectrodeFault.apply(waveforms, mode.electrodeState)
}
val sqiSignal = displayWaveformsForSqi[firstLeadForSqi]
LaunchedEffect(sqiSignal, mode.calibration.sampleRateHz, mode.filterType, mode.isCompareMode) {
    val src = sqiSignal
    if (mode.isCompareMode || src == null || src.values.size <= 100) {
        monitorViewModel.setSignalQuality(null)
    } else {
        val signal = /* see Phase 5b */ src.values.map { it.toDouble() }.toDoubleArray()
        monitorViewModel.setSignalQuality(
            computeSqi(signal, mode.calibration.sampleRateHz.toDouble())?.copy(lead = firstLeadForSqi)
        )
    }
}
```

#### Phase 5b — (Recommended, parity) reflect the active filter

On Windows the badge reflects the **filtered** trace; on Android the SQI signal currently ignores the
filter (it's electrode-fault only — see Current state). Since the badge now lives *in the filter menu*,
it should change with the filter. Apply `mode.filterType` to `src` before `computeSqi`, reusing the
filter logic from `Lead.kt:69-74`. Cleanest: **extract** that `when (filterType)` block into a shared
`EcgFilters.apply(points, filterType, calibration): Points` and call it from both `Lead.kt` and here.
> If you defer this, the move still works but the badge won't track the filter — leave a `// TODO parity`
> and note the divergence. Do **not** leave Android computing on the unfiltered signal silently if the
> goal is full parity.

### Phase 6 — Panel: render the badge in the Filters dropdown

In `MonitorControlPanel.kt`, inside the Filters `DropdownMenu` (`:277-298`), **before** the
`EcgFilterType.entries.forEach`:
```kotlin
val sqi by viewModel.signalQuality.collectAsState()
SqiBadge(sqi, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
HorizontalDivider()
```
Optional parity polish: give each `DropdownMenuItem` a leading `Icon(Icons.Default.Check, …)` shown only
when `filterType == monitorMode.filterType` (Windows now marks the active filter with a check).

### Phase 7 — Strings

Add to `res/values/strings.xml` and `res/values-ru/strings.xml` (others fall back to default):
```xml
<string name="monitor_signal_quality">Quality</string>            <!-- ru: Качество -->
<string name="monitor_signal_quality_unavailable">Signal quality unavailable</string>
                                                                  <!-- ru: Качество сигнала недоступно -->
```

## Risks & open questions

- **Layering:** moving `SqiInfo` out of `ui.components` avoids `viewmodels → ui` coupling. Verify no other
  file imported `com.example.cardiosimulator.ui.components.SqiInfo`.
- **Min-length threshold mismatch:** the helper keeps `< 500` (Android today); TeachingScreen's outer
  guard is `> 100`; Windows uses `< 100`. With both guards the effective floor is 500 samples — same as
  today, so no behaviour change. Don't "fix" one without the other.
- **Recompute cadence:** the old card recomputed via a `LaunchedEffect` keyed on `signal`; the new
  `LaunchedEffect` keys on `sqiSignal` (+ `filterType`, `isCompareMode`). Same off-main-thread cost,
  just published to a StateFlow instead of drawn. The dropdown reads the latest value when opened.
- **`DropdownMenu` content:** Compose `DropdownMenu` accepts arbitrary composables, so a badge + divider
  above the items is fine; it'll size to the badge's width (Windows uses ~230dp — match if it looks tight).
- **Compare mode:** push `null` (Phase 5) so the badge shows the "unavailable" placeholder, matching the
  Windows behaviour where compare mode produces no SQI.
- **Open question (defer, match Windows = no live refresh):** the badge is read at dropdown-open time and
  doesn't live-update while open. Windows behaves the same (a transient flyout). Don't add a live
  observer inside the menu.

## Verification

- `./gradlew :app:assembleDebug` passes.
- Teaching → monitor: **no floating quality card** over the bottom-right of the trace anymore.
- Open the **Filters** dropdown → a quality badge sits at the top: coloured dot + `Quality: <label>
  (<lead>)` + `sSQI / kSQI / pSQI`, then a divider, then the filter list.
- Selecting a filter applies it (trace changes) and closes the menu; reopening shows the (Phase 5b)
  filter-reflecting quality.
- Compare mode (or a too-short signal): the badge shows "Signal quality unavailable".
- RU locale shows "Качество" / "Качество сигнала недоступно"; the quality word ("Excellent", …) stays
  English (parity with Windows).

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Move `SqiInfo` to `signals/biosppy`, add `lead` | 1 | Pure move + one field (default) |
| 2 | Extract `computeSqi` suspend helper | 2 | From `SqiCard`'s `LaunchedEffect` |
| 3 | `MonitorViewModel.signalQuality` + `setSignalQuality` | 3 | Mirrors Windows observable |
| 4 | `SqiBadge` presentational composable (light surface) | 4 | Replaces the dark floating Card |
| 5 | TeachingScreen: compute+push, delete overlay (+ filter-reflecting) | 5 (+5b) | 5b reuses `Lead.kt` filter logic |
| 6 | Filters dropdown hosts the badge (+ active-filter check) | 6 | `DropdownMenu` content |
| 7 | Strings (EN/RU) | 7 | `monitor_signal_quality[_unavailable]` |

*(Phases 1–4 are mechanical and can ship together; 5–7 are the user-visible change.)*

---

## Cross-reference

Windows session 2026-06-29 (CardioSimulatorWin): removed the `_sqiCard` overlay from `MonitorView.cs`;
`UpdateSqi` now calls `MonitorViewModel.SetSignalQuality(SignalQualityInfo)`; the Filters `MenuFlyout`
became a custom `Flyout` (`MonitorControlPanel.xaml.cs`) showing `BuildSqiBadge()` + divider +
`AddFilterRow` (check on active filter); added `monitor_signal_quality[_unavailable]` (EN/RU). The SQI is
computed on the **processed/filtered** trace there — Phase 5b brings Android to the same.
