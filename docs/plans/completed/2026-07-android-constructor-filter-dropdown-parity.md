# Constructor display-Filters dropdown — Android parity

**Status:** active
**Owner:** (unassigned)
**Started:** 2026-07-07
**Related issues / PRs:** —
**Source of truth:** Windows port (`CardioSimulatorWin`), shipped 2026-07-07.

## Goal

The Windows port added a **display Filters dropdown to the ECG Constructor's bottom
control panel**, matching the Teaching monitor's Filters control (None / Low-pass /
High-pass / Band-pass). The selected band is applied to the Constructor's **looping
preview pane** and its **read-only all-leads overview**, while the **editable canvas
stays raw** (you author true ADC samples there). Port that to Android.

This is a small, additive UX change: Android already has the exact same Filters
dropdown on the Teaching monitor and already applies the filter inside the `Lead`
composable — so the Constructor just needs to surface the same control and route the
active `filterType` into the two preview paths that don't currently receive it.

## Current state

### What Windows shipped (the thing we're porting)
- `CardioSimulatorWin/.../Controls/ConstructorControlPanel.cs` — a chevron `FiltersTab`
  with a flyout (bold header + info tooltip + check-marked single-select rows) wired to
  `MonitorViewModel.SetFilterType`, placed between the smoothing-algorithm and speed
  controls.
- `CardioSimulatorWin/.../Controls/EcgDisplayFilter.cs` — shared Butterworth + `filtfilt`
  helper (extracted from the Teaching `MonitorView`) so the Constructor filters with the
  *same* processing as Teaching.
- `ConstructorScreen.cs` — the active filter is applied to the looping preview and the
  all-leads overview (both refresh live on filter change); the editable canvas is left raw.
- ⚠️ **Cutoff divergence — do NOT copy Windows' numbers.** Windows uses 40 Hz LP /
  0.5 Hz HP. **Android uses 25 Hz LP / 3 Hz HP / 3–25 Hz BP** (`EcgFilters.kt:9–14`) and
  the `monitor_filters_info` string already documents 25/3 Hz. Keep Android's cutoffs and
  strings unchanged — this plan is about *surfacing* the existing filter in the
  Constructor, not re-tuning it.

### What Android already has
- **Teaching Filters dropdown** — `ui/panels/MonitorControlPanel.kt:323–366`: a
  `Tab(text = monitor_filters, showChevron = true)` opening a `DropdownMenu` with
  `MenuInfoHeader(...)`, an `SqiBadge(...)`, a `HorizontalDivider()`, then one
  `DropdownMenuItem` per `EcgFilterType.entries` (leading `Icons.Default.Check` on the
  active one), calling `viewModel.setFilterType(...)`. **This is the pattern to reuse.**
- **Filter application lives inside `Lead`** — `ui/display/Lead.kt:70` (param
  `filterType: EcgFilterType = NONE`) and `:78–102` (`processedPoints` is a
  `remember(points, artifacts, filterType, calibration)` that runs
  `EcgFilters.apply(signal, filterType, samplingRate)` when `filterType != NONE` and
  `points.values.size >= 50`). So **any caller that passes `filterType` gets filtering for
  free**, and the keyed `remember` re-filters when the band changes.
- **Filter math** — `signals/biosppy/EcgFilters.kt:6–20` (Butterworth order-4;
  25/3/3–25 Hz), on top of `signals/biosppy/Filter.kt`.
- **Strings already exist** — `res/values/strings.xml:116–121`: `monitor_filters`
  ("Filters"), `monitor_filters_info` (25/3 Hz explanation), `monitor_filter_none`
  ("No filters"), `monitor_filter_lowpass`/`_highpass`/`_bandpass`. **No new strings.**
- **View-model** — `MonitorViewModel.setFilterType(...)` and `MonitorModeModel.filterType`
  are the same shared instance the Constructor bottom panel already binds
  (`ConstructorControlPanel.kt:40` reads `monitorViewModel.monitorMode`).

### The three Constructor touch-points
1. **Bottom panel** — `ui/panels/ConstructorControlPanel.kt:271–387`. The `Row` holds:
   Point-selection group (`:285`), divider, Point-adjustment group (down / ADC / algo / up,
   `:312–339`), divider, Speed group (`:344–368`), divider, Library `Tab` (`:372`),
   divider, Start/Stop `Tab` (`:381`). **No filter control today.**
2. **Looping preview** — `ui/screens/ConstructorScreen.kt:846–867`. `points` is
   `remember(stream, baseline) { Points(stream.samples.map { (it - baseline).toFloat() }) }`
   and handed to `PreviewPane(points = points, …)`. **`PreviewPane` draws the raw path
   itself (no `Lead`), so it never sees the filter.** This is the one place that needs the
   `EcgFilters.apply` call inlined (mirroring `Lead.kt`).
3. **All-leads overview** — `ui/screens/ConstructorScreen.kt:1191` (`AllLeadsPreviewOverlay`),
   `LeadView(...)` call at `:1265–1277`. It renders through `Lead` but **does not pass
   `filterType`**, so every cell is unfiltered. One-line fix: pass
   `filterType = monitorMode.filterType`.

The Constructor's **editable trace** (`EditableLead` at `ConstructorScreen.kt:803`) stays
raw — see Non-goals.

### Blocker to reuse
`MenuInfoHeader` is `private fun` at `MonitorControlPanel.kt:65`, so `ConstructorControlPanel`
(same package `ui.panels`, different file) can't call it. Relax it to package-internal
(drop `private`, or `internal fun`) so both panels share the one header composable.

## Non-goals

- **Do not filter the editable canvas** (`EditableLead`). Editing writes raw ADC and the
  bottom-panel ADC/time readouts read raw samples; filtering the canvas would misrepresent
  what you're editing. The filter is a *presentation* aid (preview + all-leads only) —
  matches the Windows decision.
- **Do not change the cutoffs or the biosppy filter.** Keep 25/3/3–25 Hz.
- **No SQI badge** in the Constructor dropdown — the Constructor has no live monitor
  computing signal quality (Teaching's `SqiBadge` is fed by `viewModel.signalQuality`).
  Header + filter rows only.
- **No new strings, no new resources.**
- Filter state remains the shared `MonitorViewModel` value, so it carries to/from Teaching
  — **intended**, consistent with how the Constructor already shares `speed`.

## Plan

### Phase 1 — Filters dropdown in the Constructor panel
- `MonitorControlPanel.kt:65` — change `private fun MenuInfoHeader` → `internal fun
  MenuInfoHeader` (or drop `private`).
- `ConstructorControlPanel.kt` — insert a filter control between the Point-adjustment
  group and the Speed group (i.e. after the divider at `:341`, before the Speed `Row` at
  `:344`), mirroring Windows' placement. Wrap it so the `DropdownMenu` anchors to the tab:
  ```kotlin
  ControlPanelDivider()
  Box(modifier = Modifier.weight(1.2f)) {
      var filtersMenuExpanded by remember { mutableStateOf(false) }
      Tab(
          text = stringResource(R.string.monitor_filters),
          showChevron = true,
          onClick = { filtersMenuExpanded = true },
          modifier = Modifier.fillMaxWidth()
      )
      DropdownMenu(
          expanded = filtersMenuExpanded,
          onDismissRequest = { filtersMenuExpanded = false }
      ) {
          MenuInfoHeader(
              title = stringResource(R.string.monitor_filters),
              explanation = stringResource(R.string.monitor_filters_info),
          )
          HorizontalDivider()
          EcgFilterType.entries.forEach { filterType ->
              DropdownMenuItem(
                  text = { Text(filterLabel(filterType)) },   // when(...) over the 4 monitor_filter_* strings
                  leadingIcon = {
                      if (filterType == monitorMode.filterType)
                          Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                  },
                  onClick = {
                      monitorViewModel.setFilterType(filterType)
                      filtersMenuExpanded = false
                  }
              )
          }
      }
  }
  ```
  Copy the `when (filterType) -> stringResource(...)` mapping verbatim from
  `MonitorControlPanel.kt:347–352` (extract to a local helper if you like). Add imports:
  `DropdownMenu`, `DropdownMenuItem`, `HorizontalDivider`, `Icon`, `Icons.Default.Check`,
  `androidx.compose.foundation.layout.Box`, `size`, and
  `com.example.cardiosimulator.domain.EcgFilterType`.
- **Tab label:** keep it the static `monitor_filters` ("Filters"), matching the Teaching
  tab. (Windows shows the active band on the tab via *short* labels; Android only has the
  long menu labels and its Teaching tab is static, so static "Filters" is the consistent
  choice. The active band is still discoverable via the ✓ in the menu.) See open question.

### Phase 2 — Filter the looping preview
- `ConstructorScreen.kt:846` — extend the `points` `remember` to key on the band +
  calibration and apply the filter, mirroring `Lead.kt`'s guard exactly:
  ```kotlin
  val points = remember(stream, baseline, monitorMode.filterType, monitorMode.calibration) {
      val zeroed = stream.samples.map { (it - baseline).toFloat() }
      if (monitorMode.filterType == EcgFilterType.NONE || zeroed.size < 50) {
          Points(zeroed)
      } else {
          val filtered = EcgFilters.apply(
              zeroed.map { it.toDouble() }.toDoubleArray(),
              monitorMode.filterType,
              monitorMode.calibration.sampleRateHz.toDouble()
          )
          Points(filtered.map { it.toFloat() })
      }
  }
  ```
  Use the Android guard `size < 50` (not Windows' 15). Imports: `EcgFilterType`,
  `com.example.cardiosimulator.signals.biosppy.EcgFilters`.

### Phase 3 — Filter the all-leads overview
- `ConstructorScreen.kt:1265` — add one argument to the `LeadView(...)` call:
  `filterType = monitorMode.filterType`. `Lead`'s keyed `remember` does the rest and
  refreshes live when the band changes (the overlay already re-derives `map` on
  `targetFile`). No other change.

### Phase 4 — Polish & verify
- Build; manual smoke test (see Verification).
- Confirm the dropdown, preview, and overlay all reflect the same shared band, and that
  switching back to **No filters** restores the raw trace everywhere.

## Risks & open questions

- **Tab label: static "Filters" vs. reflect the selection.** Chosen: static, to match the
  Teaching tab and avoid inventing short strings. If the customer wants the Windows
  behavior (tab shows the active band), set `text = filterLabel(monitorMode.filterType)`
  using the existing long labels — no new strings, just a longer tab caption. *Decide with
  the customer; static is the safe default.*
- **Shared filter state across Teaching ↔ Constructor.** By design (same `MonitorViewModel`,
  same as `speed`). If per-screen isolation is ever wanted, that's a separate change to the
  view-model — out of scope here.
- **Relaxing `MenuInfoHeader` visibility** is trivially safe (same module, additive).
- **Re-filtering cost.** The preview `remember` is keyed on `filterType`/`calibration`, so
  it only re-runs on band/calibration change, not per frame — good. All-leads filtering is
  inside `Lead`'s keyed `remember`, likewise cached.

## Outcome

- **Result:** Completed.
- **PRs:** N/A (implemented directly).
- **Deviations from plan:** None.
- **Follow-ups spawned:** None.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Constructor: Filters dropdown + preview/all-leads filtering | 1–4 | One PR, 3 commits: (a) dropdown + `MenuInfoHeader` visibility, (b) preview filter, (c) all-leads `filterType`. No new strings. |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:** —
- **PRs:** —
- **Deviations from plan:** —
- **Follow-ups spawned:** —
