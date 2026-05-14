# Editor rendering parity with Teaching mode

**Status:** proposed
**Owner:** —
**Started:** 2026-05-13
**Related issues / PRs:** —

## Goal

Teaching and Editor modes currently use two completely different rendering pipelines to draw
ECG waveforms. Teaching goes through the shared `Lead → ChartCanvas` stack that handles calibration,
grid alignment, and polyline drawing consistently. Editor bypasses all of that and uses a
monolithic `AnchorEditableCanvas` that bakes anchor points inside its own draw phase, draws the
polyline itself, and overlays drag handles — all in one place with no `CalibrationPulse` column and
with a redundant outer `CompositionLocalProvider` that is silently shadowed by the inner
`Monitor`-provided scale.

The goal is to rebuild the Editor's rendering layer so it goes through the **same
`Lead → ChartCanvas` path as Teaching**, with editor-specific behaviour (draggable handles,
anchor baking) implemented as a thin overlay on top rather than as a replacement for the shared
stack. Every existing Editor screen feature must continue to work after the refactor.

Why now: the `2026-05-editor-mode.md` plan (Phases 1–4) is about to add more drawing to the
editor canvas (block timeline thumbnails, derived-lead previews). Building those on the current
divergent path widens the gap further; aligning the renderer first makes all subsequent phases
cheaper.

---

## Current state

### Teaching pipeline

```
RhythmViewModel
  → waveforms: Map<Lead, Points>       (from EcgRepository.assembleWaveform)
  ↓
TeachingScreen.kt
  ↓
Monitor(sourceAnchoredCalibration = null)    ← physical-mm PixelScale via LocalPixelScale
  ↓
LeadsGrid(rows, columns)
  ↓
Lead(points, title)                          ← ui/display/Lead.kt
  ├── CalibrationPulse(48 dp wide)           ← ui/components/CalibrationPulse.kt
  └── ChartCanvas(points)                    ← ui/components/ChartCanvas.kt
        drawPath(polyline from Points, via LocalPixelScale.pxPerSample / pxPerAdcCount)
```

Data baking happens **before the composable tree** — `assembleWaveform` turns raw samples into
baseline-zeroed floats, which become `Points` in `RhythmViewModel.waveforms`.

### Editor pipeline (current)

```
AppViewModel
  → EditablePart (anchors: List<AnchorPoint>)
  ↓
EditorScreen.kt
  ↓
CompositionLocalProvider(LocalPixelScale = editorPixelScale)     ← OUTER [DEAD CODE — shadowed
  ↓                                                                  by Monitor's inner provision]
Monitor(sourceAnchoredCalibration = aMaxGlobal to aValueGlobal)  ← source-anchored PixelScale
  ↓
  CompositionLocalProvider(LocalPixelScale = sourceAnchored PixelScale)   ← INNER [wins]
    ↓
    Box + ekgGrid()
      ├── PaperGridLegend
      └── AnchorEditableCanvas(anchors, sampleRateHz, samplesPerMv)   ← ui/components/AnchorCanvas.kt
              [bakes anchors → polyline samples inside the draw phase via bakeAnchors()]
              [draws polyline + handles in a single Canvas]
              [no CalibrationPulse column]
  ↓
PreviewPane(points = focusedPart.samples)    ← STALE: uses raw ZIP samples, not current anchors
```

### Identified gaps

| # | Symptom | Location | Impact |
|---|---------|----------|--------|
| G1 | Waveform drawn by `AnchorEditableCanvas.drawWaveform()`, not by `ChartCanvas` | `AnchorCanvas.kt:143` | Divergent rendering: parameter parity with viewer must be maintained in two places |
| G2 | Calibration pulse column absent in editor canvas | `EditorScreen.kt:208` | Editor waveform not aligned with the 1 mV reference visible in Teaching/Examination |
| G3 | Anchor baking happens inside the Canvas draw phase | `AnchorCanvas.kt:149` | Baked samples are thrown away every recomposition; no caching; not available to siblings |
| G4 | Outer `CompositionLocalProvider(LocalPixelScale)` in `EditorScreen` is dead code | `EditorScreen.kt:157` | `Monitor` always overwrites it; the duplicate misleads readers |
| G5 | `PreviewPane` uses `focusedPart.samples` from the ZIP | `EditorScreen.kt:322` | Preview does not reflect in-session anchor edits until the part is saved and reloaded |
| G6 | `AnchorEditableCanvas` mixes rendering and interaction logic | `AnchorCanvas.kt:57–124` | Can't reuse the rendering without the interaction; can't change interaction without touching the renderer |

---

## Non-goals

- Changing **how** calibration values (`aMax`, `aValue`, `duration`) flow through the pipeline — that is `2026-05-editor-mode.md` Phase 0a, already resolved.
- Changing the source-anchored grid in Editor mode — it stays source-anchored.
- Rewriting `MonitorViewModel`, `RhythmViewModel`, or the data-load path.
- Changing any of Teaching / Testing / Examination / OSKE rendering — they must be untouched.
- Adding new editor features (block timeline, derived-lead buttons) — those belong to the parent plan phases.

---

## Plan

### Phase 1 — Extract AnchorHandleOverlay

**Goal:** separate handle drawing + gesture handling from waveform rendering so the two concerns can be composed independently.

**Files touched:** `ui/components/AnchorCanvas.kt` (new composable added; existing `AnchorEditableCanvas` kept unchanged until Phase 3)

Steps:
- Add `AnchorHandleOverlay` composable in `AnchorCanvas.kt` (or a new `AnchorHandleOverlay.kt`):
  - Parameters: `anchors`, `sampleRateHz`, `samplesPerMv`, `selectedIndex`,
    `onAnchorSelected`, `onAnchorMoved`, `onAllAnchorsMoved`, `modifier`
  - Uses the same `AnchorSpace.computeSpace()` helper already in the file (make it `internal`
    rather than `private` so both composables share it)
  - `detectTapGestures` — same hit-test logic as `AnchorEditableCanvas`
  - `detectDragGestures` — same drag + move-all logic
  - Draws only the circles (`drawHandles`); **no polyline**
  - Backed by a transparent `Canvas` (no background)
- Expose `computeSpace` and `drawHandles` as `internal` helpers so both `AnchorEditableCanvas`
  and `AnchorHandleOverlay` share them without duplication
- Unit-test: tap at a known anchor screen coordinate → `onAnchorSelected` fires with the correct index

**Deliverable:** `AnchorHandleOverlay` compiles and passes tests. `AnchorEditableCanvas` still works unchanged on the Editor screen.

---

### Phase 2 — Pre-bake anchors to Points in EditorScreen

**Goal:** produce `Points` from the current anchor list at the composable level (same as Teaching produces `Points` in `RhythmViewModel`), fix the stale `PreviewPane`, and remove the redundant outer scale wrapper.

**Files touched:** `ui/screens/EditorScreen.kt`

Steps:

- Derive `bakedPoints: Points` from `focusedEditable`:
  ```kotlin
  val bakedPoints = remember(focusedEditable?.identy, focusedEditable?.anchors?.toList()) {
      val raw = focusedEditable?.bakedSamples() ?: emptyList()
      Points(raw.map { it - 1024f })
  }
  ```
  `EditablePart.bakedSamples()` already caches the result and invalidates on anchor change
  ([Editable.kt:33](../../../app/src/main/java/com/example/cardiosimulator/domain/Editable.kt:33)).
  The `remember` key on `anchors.toList()` ensures recomposition whenever the list changes.

- Replace the `PreviewPane` call at `EditorScreen.kt:322`:
  ```kotlin
  // BEFORE
  val previewSamples = remember(focusedPart.identy, focusedPart.samples) {
      Points(focusedPart.samples.map { (it - 1024f) })
  }
  // AFTER
  val previewSamples = bakedPoints   // already derived above
  ```
  This fixes gap G5: the preview now reflects the live anchor state.

- Remove the redundant outer `CompositionLocalProvider(LocalPixelScale provides editorPixelScale)`
  at `EditorScreen.kt:157`. The `editorPixelScale` local variable and `remember` block
  (`EditorScreen.kt:112–128`) can also be deleted — `Monitor` already computes and provides the
  identical source-anchored scale from `sourceAnchoredCalibration`.  
  This fixes gap G4.

**Deliverable:** build green; `PreviewPane` updates in real time as anchors are dragged; no outer `LocalPixelScale` wrapper; `editorPixelScale` local is gone.

---

### Phase 3 — Replace AnchorEditableCanvas with Lead + ChartCanvas + AnchorHandleOverlay

**Goal:** the Editor's main canvas uses the same `Lead → ChartCanvas` rendering path as Teaching, with `AnchorHandleOverlay` stacked on top inside the waveform column.

**Files touched:** `ui/screens/EditorScreen.kt`, `ui/display/Lead.kt` (or a new `EditableLeadCanvas.kt`)

Steps:

- In `EditorScreen`, inside the Monitor content block (currently at `EditorScreen.kt:208`), replace:
  ```kotlin
  AnchorEditableCanvas(
      anchors = anchors,
      sampleRateHz = focusedPart.effectiveSampleRateHz,
      samplesPerMv = focusedEditable.samplesPerMv,
      ...
  )
  ```
  with a `Row` that mirrors `Lead`'s layout:
  ```kotlin
  Row(modifier = Modifier.leadArea(), verticalAlignment = Alignment.CenterVertically) {
      // Left column: calibration pulse + lead name (identical to Lead.kt:39–66)
      Box(modifier = Modifier.width(48.dp).fillMaxHeight()) {
          CalibrationPulse(
              modifier = Modifier.fillMaxSize(),
              samplesPerMv = focusedEditable.samplesPerMv,
          )
          Text(
              text = focusedLead?.name ?: "",
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Serif,
              fontSize = 16.sp,
              color = Color.Black,
              modifier = Modifier.align(Alignment.Center).padding(top = 45.dp, start = 8.dp),
          )
      }
      // Right column: waveform + handle overlay
      Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          if (bakedPoints.values.size >= 2) {
              ChartCanvas(
                  points = bakedPoints,
                  modifier = Modifier.fillMaxSize().chartArea(),
                  sampleRateHz = focusedPart.effectiveSampleRateHz,
                  samplesPerMv = focusedEditable.samplesPerMv,
              )
          }
          if (anchors.isNotEmpty()) {
              AnchorHandleOverlay(
                  anchors = anchors,
                  sampleRateHz = focusedPart.effectiveSampleRateHz,
                  samplesPerMv = focusedEditable.samplesPerMv,
                  selectedIndex = selectedAnchorIndex,
                  onAnchorSelected = { selectedAnchorIndex = it },
                  onAnchorMoved = { idx, nx, ny -> ... },    // same lambda as before
                  onAllAnchorsMoved = { dx, dy -> ... },     // same lambda as before
                  modifier = Modifier.fillMaxSize().chartArea(),
              )
          }
      }
  }
  ```
  This fixes gaps G1, G2, G3, G6.

- The "empty state" message (`"Pick a rhythm and a part to start editing."`) stays as the `else`
  branch, unchanged.

- Keep `Modifier.fillMaxSize().ekgGrid()` on the outer `Box` (grid is still drawn by the Monitor
  via `ekgGrid(mode.gridScheme)` on the Column — verify this still fires after removing the inline
  `Box.ekgGrid()` wrapper, or keep it explicitly).

- `leadArea()` modifier: confirm it is accessible from `EditorScreen` (`ui/display/Modifiers.kt`
  — already `internal` or move to `ui/components` if needed). If scoping prevents direct use,
  inline its equivalent (`fillMaxWidth().height(leadHeight)`).

**Deliverable:** Editor main canvas shows `CalibrationPulse` + waveform rendered by `ChartCanvas` + draggable handles from `AnchorHandleOverlay`. All existing anchor drag, select, move-all interactions work identically to before.

---

### Phase 4 — Delete AnchorEditableCanvas

**Goal:** remove the now-unused monolithic canvas to eliminate the divergent rendering path permanently.

**Files touched:** `ui/components/AnchorCanvas.kt`

Steps:
- Confirm `AnchorEditableCanvas` has no remaining callers (`grep -r "AnchorEditableCanvas" app/src`).
- Delete the `AnchorEditableCanvas` composable and its private `drawWaveform` helper.
- Keep `AnchorHandleOverlay`, `AnchorSpace`, `computeSpace`, `drawHandles` in `AnchorCanvas.kt`.
- Update the architecture doc `docs/architecture.md` — remove `AnchorCanvas (incl. AnchorEditableCanvas)`, replace with `AnchorCanvas (AnchorHandleOverlay, AnchorSpace)`.

**Deliverable:** build green; no references to `AnchorEditableCanvas` remain.

---

## Risks & open questions

| # | Risk | Mitigation |
|---|------|-----------|
| R1 | `leadArea()` modifier is `internal` to `ui.display` — `EditorScreen` can't call it | Move to `ui.components` or duplicate the explicit size expression |
| R2 | `chartArea()` modifier must be applied to both `ChartCanvas` and `AnchorHandleOverlay` at the same size so hit regions align | Apply to the outer `Box`, or apply identical `Modifier.fillMaxSize().chartArea()` to both children |
| R3 | `bakedPoints` `remember` key on `anchors.toList()` creates a new list on every recomposition — cheap but not free | Acceptable: list is small (< 100 anchors); if profiling shows cost, switch to a `derivedStateOf` over a snapshot-backed state |
| R4 | `Move-all` gesture on origin anchor (index 0) currently dispatches `onAllAnchorsMoved` — must be preserved in `AnchorHandleOverlay` | Directly ported; no logic change |
| R5 | `PaperGridLegend` is currently placed inside the Monitor Box at `EditorScreen.kt:209` — after restructuring it must stay in the Box above the lead row | Keep `PaperGridLegend` as a sibling of the new lead `Row`, aligned `TopStart` |

---

## Verification

### Phase 1
- `AnchorHandleOverlay` renders circles at the expected screen positions for a known anchor list (screenshot test or manual check with a fixture).
- `AnchorEditableCanvas` still works: open any part in Editor, drag an anchor, confirm it moves.

### Phase 2
- Edit an anchor → `PreviewPane` scrolls the updated waveform in real time (no save/reload needed).
- No compile error from the removed outer `CompositionLocalProvider`.
- Grid scale is visually unchanged (source-anchored 1 small square = 1 source unit).

### Phase 3
- Calibration pulse appears on the left of the editor canvas.
- Waveform position/gain matches what the same record shows in Teaching mode (same `pxPerAdcCount`, same baseline centre).
- Anchor handles appear at the correct positions over the waveform.
- Tap a handle → selects it; drag → moves it; drag origin → moves all.
- `AnchorInspector` still receives selection events and mutates correctly.
- Build green; existing unit tests pass.

### Phase 4
- `grep -r "AnchorEditableCanvas" app/src` returns zero results.
- Build green.

---

## PR breakdown

| # | PR title | Phase |
|---|----------|-------|
| 1 | Editor: extract AnchorHandleOverlay from AnchorEditableCanvas | 1 |
| 2 | Editor: pre-bake anchors to Points; fix PreviewPane; remove redundant PixelScale wrapper | 2 |
| 3 | Editor: render waveform via Lead + ChartCanvas + AnchorHandleOverlay | 3 |
| 4 | Editor: delete AnchorEditableCanvas; update architecture doc | 4 |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:**
- **PRs:**
- **Deviations from plan:**
- **Follow-ups spawned:**
