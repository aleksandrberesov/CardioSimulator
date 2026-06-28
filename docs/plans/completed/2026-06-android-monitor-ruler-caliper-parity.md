# Android monitor ruler / caliper parity

**Status:** completed
**Owner:** (unassigned)
**Started:** 2026-06-28
**Finished:** 2026-06-28
**Related issues / PRs:** —
**Source of truth:** Windows implementation in `E:\VLN_Project\CardioSimulatorWin` (commit on `main`, the monitor-ruler change). Read those files first — this plan ports that behaviour 1:1.
**Windows-side sync brief (cross-reference):** `E:\VLN_Project\CardioSimulatorWin\docs\plans\sync\2026-06-android-monitor-ruler-caliper-parity.md` ← keep these two in sync.

## Goal

The monitor's **Ruler** button in Teaching mode currently does nothing on Android: the `Straighten` tab calls `onRulerClick`, which defaults to `{}` and is never supplied by `MainScreen`, so tapping it is a no-op. Implement a working ECG **caliper** matching the Windows app: toggle ruler mode from the button, drag on the monitor to measure a time interval (**ms**), the implied heart **rate (bpm)** and **amplitude (mV)**, drawn as an overlay on the trace. This closes a visible "dead button" gap and reaches parity with the Windows port, where the feature was just built.

## Current state

### Android (what exists today — all paths under `app/src/main/java/com/example/cardiosimulator/`)
- `ui/panels/MonitorControlPanel.kt`
  - `onRulerClick: () -> Unit = {}` parameter (line ~52) — **defaults to no-op**.
  - Ruler tab (lines ~321-327): `Tab(icon = Icons.Default.Straighten, iconModifier = Modifier.rotate(-45f), iconContentDescription = stringResource(R.string.cd_ruler), onClick = onRulerClick, ...)`. **The icon is already a ruler rotated -45° — do NOT change it.**
  - Sibling toggles to mirror (lines ~268-302): pQRSt / EOS / Tips / 3D are all `Tab(..., onClick = { viewModel.setShowX(!monitorMode.showX) }, isActive = monitorMode.showX)`.
- `ui/screens/MainScreen.kt` (line ~353): calls `MonitorControlPanel(viewModel = monitorViewModel, onStartStopClick = …)` — **does not pass `onRulerClick`**, hence the dead button.
- `ui/display/Monitor.kt` — the monitor composable and the place the caliper must live:
  - `scale` (zoom, clamped `1f..5f`) + `offset` (pan) held as local state (lines ~67-68, 139, 143-160); `mode.scale` syncs the scale dropdown (lines ~70-75); persisted via `LaunchedEffect(scale){ delay(500); monitorViewModel.setScale(scale) }` (lines ~163-166).
  - Zoom/pan applied in `drawWithContent { withTransform({ scale(scale,scale,pivot=center); translate(offset) }) { drawContent() } }` (lines ~173-182). **The trace is transformed inside the draw; pointer-input coordinates on the Box are in the untransformed (screen) space** — same model the Windows overlay uses.
  - Gestures via `rememberTransformableState { _, zoomChange, offsetChange, _ -> … }` + `.transformable(state)` (lines ~136-172).
  - `PixelScale` provided through `LocalPixelScale` (line ~168); `pixelScale.pxPerSec` and `pixelScale.pxPerMv` are available.
- `data/PixelScale.kt`: `pxPerSec = paperSpeedMmPerSec * pxPerMm` and `pxPerMv = gainMmPerMv * pxPerMm` — **neither includes zoom**; zoom is applied separately by the draw transform. So **on-screen px-per-unit = pixelScale.pxPerSec * scale** (and `pxPerMv * scale`).
- `ui/components/Tab.kt`: supports `isActive` → `AccentGreen` background / `OnAccent` foreground (lines ~36, 54, 60). Use it for the lit state, exactly like pQRSt.
- `res/values/strings.xml`: `cd_ruler = "Ruler"` already exists.

### Windows reference (read these for exact behaviour & math — under `…\CardioSimulatorWin\src\CardioSimulator.App\`)
- `Controls/EcgMonitorControl.cs` — `SetRulerActive` / `SetCalipers`, and `DrawRuler(...)` which draws the band + legs + connector + endpoint dots + readout box, and computes the readout. **Note:** it resets the draw transform to identity before drawing the overlay because `EcgRenderer.Render` leaves the zoom matrix applied — the Compose equivalent is simply drawing the overlay *outside* `withTransform`.
- `Controls/MonitorView.cs` — `RulerActive` property; pointer handlers route a left-drag to caliper editing instead of pan, and **freeze wheel-zoom while ruler is active**; a bare click (no drag) clears the measurement.
- `Controls/MonitorViewerOverlay.cs`, `Screens/TeachingScreen.cs` — passthrough + clear-on-close.
- `Controls/MonitorControlPanel.xaml(.cs)` — toggle button, `RulerToggled` event, `ResetRuler`, active highlight, tooltip. (On Windows the icon had to be hand-drawn as a vector ruler because Segoe Fluent Icons has no ruler glyph — **irrelevant on Android, which already has the `Straighten` icon.**)
- `Screens/MainScreen.xaml.cs` — wires the toggle and resets the button when the monitor is dismissed.
- `Localization/AppStrings.cs` — `monitor_ruler` description string in EN/RU/ZH/ES (optional on Android).

### The measurement math (identical on both platforms)
With `scale` = current zoom and caliper points A, B in screen px:
```
dtSec = abs(B.x - A.x) / (pixelScale.pxPerSec * scale)
ms    = dtSec * 1000
bpm   = if (dtSec > 0) 60.0 / dtSec else null        // show "— bpm" when null
mV    = abs(B.y - A.y) / (pixelScale.pxPerMv * scale)
```
Sanity check: measuring the 1 mV / 0.2 s calibration pulse should read ≈ **1.00 mV** and ≈ **200 ms**.

## Non-goals
- **No icon change** — Android already uses `Straighten` rotated -45°.
- Ruler only in the Teaching monitor; not in Constructor / Testing / Examination / OSKE.
- Disabled in **compare mode** (`mode.isCompareMode`), same as Windows.
- No persistence of in-progress caliper points across recomposition beyond what `remember` gives; only the on/off toggle is part of monitor state.

## Plan

### Phase 1 — Ruler on/off state
- Add a `showRuler: Boolean` field to the monitor-mode model and a `setShowRuler(Boolean)` on `MonitorViewModel`, **mirroring `showTips` / `showImpulseLabels` end-to-end** (default `false`; match their persistence behaviour — if `showTips` is not persisted across sessions, `showRuler` shouldn't be either).
- Verify the model's `copy(...)`/flow plumbing compiles and `monitorMode.showRuler` is observable.

### Phase 2 — Wire the button
- In `MonitorControlPanel.kt`, change the ruler tab to the sibling-toggle pattern:
  ```kotlin
  Tab(
      icon = Icons.Default.Straighten,
      iconModifier = Modifier.rotate(-45f),
      iconContentDescription = stringResource(R.string.cd_ruler),
      onClick = { viewModel.setShowRuler(!monitorMode.showRuler) },
      isActive = monitorMode.showRuler,
      modifier = Modifier.weight(1f)
  )
  ```
- Remove the now-unused `onRulerClick` parameter (and its call site, if any). The button now lights green (`isActive`) when active, exactly like pQRSt.

### Phase 3 — Caliper interaction + overlay (`Monitor.kt`)
- Collect `mode.showRuler`. Hold caliper state: `var caliperA by remember { mutableStateOf<Offset?>(null) }`, `var caliperB by remember { … }`.
- `LaunchedEffect(mode.showRuler) { if (!mode.showRuler) { caliperA = null; caliperB = null } }`.
- **Freeze zoom & pan while ruler is active:** in the `rememberTransformableState` lambda early-return when `mode.showRuler` (so a measurement's px/ms relationship stays stable). Add a `Modifier.pointerInput(mode.showRuler) { if (showRuler) detectDragGestures(onDragStart = { caliperA = it; caliperB = it }, onDrag = { change, _ -> caliperB = change.position }, onDragEnd = { /* clear if A≈B (a tap) */ }) }` on the same Box. Pointer positions are already in untransformed screen px.
- **Draw the overlay as a sibling, NOT inside `withTransform`** (so it is screen-anchored over the frozen, already-zoomed trace). Add an overlay `Canvas(Modifier.fillMaxSize())` after the transformed Box drawing, when `caliperA != null && caliperB != null`:
  - translucent vertical band between the two x's (`Color(0x1E88E5)` @ ~15% alpha),
  - two full-height vertical legs at A.x and B.x, a connector A→B, and filled dots at A and B (`Color(0xFF1E88E5)`, ~1.2.dp strokes),
  - a small rounded readout box near the span's top-centre showing two lines: `"Δt {ms} ms   {bpm} bpm"` and `"Δ {mV} mV"`. For text inside a Compose `Canvas`, use a `TextMeasurer` (`drawText(textMeasurer, …)`) or overlay a `Surface`+`Text` positioned with `Modifier.offset { … }`.
- Use `pixelScale.pxPerSec * scale` and `pixelScale.pxPerMv * scale` for the readout (see math above). Guard `dtSec == 0` → show `"— bpm"`.

### Phase 4 — Polish
- Ensure ruler is unavailable / cleared in compare mode (`mode.isCompareMode`): either hide the tab or no-op the toggle, and never draw the overlay.
- Reset `showRuler` to `false` when leaving the monitor view (course selected in Teaching), mirroring how Windows clears it on dismiss — match whatever `showTips`/`showEos` do on the same transition.
- Optional: enrich `cd_ruler` or add a `monitor_ruler` tooltip string in `values/` + `values-ru/` + `values-zh/` + `values-es/` (EN/RU/ZH/ES), parity with the Windows `monitor_ruler` string.

## Risks & open questions
- **Coordinate space (most important):** confirm `pointerInput` positions and the overlay `Canvas` both use the same untransformed px space as the draw pivot — they should, since the transform is applied only inside `drawWithContent`. If a future refactor moves zoom to a `graphicsLayer`, revisit (pointer coords would then be pre-layer too, still fine, but verify).
- **Text in Canvas:** Compose has no direct `drawText(String)`; use `rememberTextMeasurer()` + `drawText(...)`, or float a `Text` composable. Pick one and keep it simple.
- **Persistence of `showRuler`:** open — mirror `showTips`. If `showTips` persists via prefs, do the same; otherwise keep it session-local. (Resolve by reading the model.)
- **Running trace:** like Windows, calipers are screen-anchored, so the intended workflow is to Stop and then measure. Acceptable; document in a code comment.

## Verification
- `./gradlew :app:assembleDebug` (or `compileDebugKotlin`) passes.
- Manual (Teaching → monitor / "All rhythms"):
  1. Tap Ruler → button turns green (`isActive`); tap again → off.
  2. With it on, drag across two R-peaks → overlay shows band + legs + a readout with ms / bpm / mV.
  3. Drag the 1 mV / 0.2 s calibration pulse → reads ≈ 1.00 mV / ≈ 200 ms.
  4. While ruler is on, pinch does nothing (zoom frozen); turning ruler off clears the measurement.
  5. Compare mode: ruler unavailable / no overlay.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Add `showRuler` monitor state + wire ruler tab | 1-2 | Button toggles & lights up; no overlay yet |
| 2 | Caliper drag + measurement overlay in Monitor | 3 | The actual feature |
| 3 | Compare-mode guard, reset-on-leave, strings | 4 | Polish & parity |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:** —
- **PRs:** —
- **Deviations from plan:** —
- **Follow-ups spawned:** —
