# Trace a real ECG photo into editable samples

**Status:** proposed (awaiting sign-off; flip to `active` on approval)
**Owner:** alexandr.beresov@gmail.com
**Started:** 2026-05-29
**Related issues / PRs:** —

## Goal

Turn the existing reference-image *underlay* in the Constructor into a real
**digitize-the-photo** workflow: the user loads a photo of a real ECG (a single
lead / rhythm strip), positions it by eye over the grid, then reconstructs the
digital waveform from it — first by **freehand tracing**, later assisted by
**automatic detection** of the printed trace. The output is ordinary editable
ADC samples in the focused lead, so all existing per-sample tools (nudge,
absolute-set, smoothing kernels, significant points, derived-lead calculation,
save/export) keep working unchanged.

## Current state

- **Underlay today** is display-only: `ConstructorViewModel.referenceImageUri`
  (`ui/viewmodels/ConstructorViewModel.kt:73-78`), picked via a `GetContent()`
  launcher (`ui/screens/ConstructorScreen.kt:176-180`, button at `:309`), and
  rendered in `Monitor.backgroundContent` as
  `AsyncImage(ContentScale.Fit, alpha = 0.5f)` (`ConstructorScreen.kt:378-388`).
  The grid's paper fill is suppressed when an image is present
  (`showGridBackground = referenceImageUri == null`, `:377`); grid *lines* still
  draw, because `ekgGrid(showBackground=false)` only drops the fill
  (`ui/display/Modifers.kt:58-106`). **There is no pan/zoom/rotate/opacity
  control and no relationship between photo pixels and the grid.**
- **Monitor gesture stack:** `Monitor` wraps everything in a `transformable`
  that pinch/pans the *entire* monitor (grid + image + trace together) and
  persists scale via `monitorViewModel.setScale` (`ui/display/Monitor.kt:124-152`).
  Layer order is `backgroundContent()` → grid `Column` → `content` lambda
  (`:153-166`).
- **Editing model** is per-sample, anchor-driven: select an index, then
  `moveSelectedUp/Down` → `adjustSample`, or `setSample` for an absolute value.
  Both apply a weighted kernel over a configurable radius (1..1000) with 5
  algorithms, accumulating sub-integer contributions in `floatBuffers`
  (`ConstructorViewModel.kt:95-281`). ADC range is `0..2048`, baseline ~1024.
- **Trace + handles** live inside `EditableLead`'s waveform `Box` (the same box
  holds `ChartCanvas`, `SignificantPointOverlay`, `SampleHandleOverlay`)
  (`ui/display/EditableLead.kt:94-115`). Crucially, `EditableLead` renders at a
  **fit-to-view vertical scale** (`EDITOR_ADC_RANGE = 800`), centered, offset by
  a 48dp calibration-pulse column and a 132dp bottom spacer
  (`EditableLead.kt:70-99`) — i.e. a *different* coordinate frame than the
  grid-space monitor background where the photo currently sits.
- **px ↔ sample mapping** already exists in `SampleHandleOverlay`:
  `index = x / stepX`, `y = baselineY - (sample - baseline) * stepY` with
  `baselineY = height/2`, `stepY = scale.pxPerAdcCount`
  (`ui/components/SampleHandleOverlay.kt:51,63-70`). Today it only *selects* an
  index on drag — tracing means also *writing* the value under the pointer.
- **Data / persistence:** `PathologyFile { leads: Map<Lead, LeadStream>, ... }`,
  `LeadStream { lead, samples: IntArray }`; edits persist via
  `PathologyRepository` to `filesDir/pathologies` (see [[project_editor_persistence]]).

## Decisions locked in

- **Trace tools:** freehand sweep **and** auto-detect (no control-point/spline tool).
- **Calibration:** *eyeball only* — pan/zoom/rotate the photo until it looks
  right; no numeric mm calibration. This means v1 does **not** depend on the
  RP5 calibration-parity work ([[project_calibration_parity]]); traced amplitude
  is whatever the user visually aligns to.
- **Photo scope:** one lead / rhythm strip per photo (not a full 12-lead page).
- **Coordinate unification (agreed):** move the reference image out of
  `Monitor.backgroundContent` and **into the `EditableLead` waveform `Box`**, so
  the photo, the trace, and the pointer all share one coordinate frame and
  "the line I drag along on the photo" equals "the sample I write" by
  construction. Grid stays behind, in the monitor.
- **Tool mode (agreed):** an explicit `Position / Trace / Select` mode gates
  pointer routing, because image-positioning drags, trace drags, and the
  monitor's own `transformable` would otherwise fight over the same gestures.

## Non-goals

- Numeric mm / mV calibration of the photo (eyeball alignment only for now).
- Full 12-lead-page tracing, cropping, or region-to-lead mapping (single strip only).
- Control-point / spline tracing tool.
- Persisting the photo or its transform into the saved `PathologyFile` — the
  photo is a transient tracing aid, kept in ViewModel state only (open question
  below if we later want it remembered).
- Heavy CV dependencies (e.g. OpenCV); Phase C aims for lightweight pure-Kotlin
  pixel processing (revisit if accuracy demands it).

## Plan

### Phase A — Image transform tools (the "position the photo" step)
Make the underlay positionable and move it into the trace's coordinate frame.
- **State (`ConstructorViewModel`):** add `imageOffset: Offset`,
  `imageScale: Float` (uniform; consider independent `scaleX/scaleY` only if
  needed), `imageRotationDeg: Float`, `imageAlpha: Float` (default 0.5, replaces
  the hardcoded literal), `imageLocked: Boolean`, and
  `toolMode: ToolMode { Position, Trace, Select }` — with setters. Reset on
  `selectPathology` / image change.
- **Render move:** stop passing the image to `Monitor.backgroundContent`; add an
  optional bottom layer inside the `EditableLead` waveform `Box` that draws the
  `AsyncImage` via `graphicsLayer(translationX/Y, scaleX/Y, rotationZ, alpha)`
  from the new state. Z-order becomes: grid (monitor) → photo → `ChartCanvas` →
  overlays.
- **Gestures:** in `Position` mode, a `transformable` + drag on the image layer
  drives offset/scale/rotation; in other modes the image ignores input. Gate
  the **monitor's** `transformable` so single-finger drags don't pan the canvas
  while tracing — add an `interactive`/`gesturesEnabled` flag to `Monitor`.
- **UI:** tool-mode control in the Constructor toolbar; a compact panel for
  `Position` mode (opacity slider, lock toggle, fine nudge/scale/rotation
  controls as a keyboard-free fallback to gestures).
- **Files:** `ConstructorViewModel.kt`, `EditableLead.kt`, `ConstructorScreen.kt`,
  `Monitor.kt`, `res/values*/strings.xml`.
- **Outcome:** user can load a strip, position/scale/rotate/fade it to sit over
  the grid exactly where they want to trace; nothing writes samples yet.

### Phase B — Freehand sweep trace
- **Trace input:** in `Trace` mode, a drag over the waveform box writes every
  x-column it crosses — `index = x / stepX`,
  `value = baseline + (baselineY - y) / stepY` (inverse of the handle mapping),
  clamped to `0..2048`; linearly interpolate indices skipped on fast moves so a
  quick sweep yields a continuous trace. Reuse `LocalPixelScale.current` (the
  same fit-scale the handles use) so input and render agree.
- **VM:** add a batch writer (e.g. `traceSamples(lead, startIndex, endIndex,
  values)` or `setSampleRange`) that writes directly (no kernel), keeps
  `floatBuffers` in sync, marks the lead dirty, and respects `isLeadEditable`.
- **Undo/redo:** snapshot the lead's `IntArray` before each stroke into a
  per-lead undo stack; expose `undo()/redo()` + toolbar buttons. (Scope: at
  least trace strokes; nudges can join later.)
- **Optional finisher:** a "Smooth traced range" action reusing `computeWeight`
  / a moving average to clean jitter.
- **Files:** new `TraceOverlay` (or extend `SampleHandleOverlay`),
  `ConstructorViewModel.kt`, `EditableLead.kt`, `ConstructorScreen.kt`.
- **Outcome:** user can redraw a full beat/strip by dragging along the photo.

### Phase C — Auto-detect the printed trace
- **Core (pure Kotlin):** decode the bitmap; using the Phase-A transform, map
  each sample index → image column, scan the vertical strip, filter out grid
  color (real ECG grids are pink/orange/grey — discriminate by low saturation /
  hue + keep dark/near-black ink), take the centroid of the darkest run →
  image-y → ADC (inverse transform). Produce a candidate `IntArray`, with
  interpolation across columns where ink is missing/faint.
- **UX:** run on demand → show a "ghost" candidate trace with Apply / Cancel;
  Apply writes via the Phase-B batch path; user cleans up with freehand + the
  per-sample tools. Provide a detection threshold and a column/region limiter.
- **Files:** new `data`/util (e.g. `TraceExtractor.kt`),
  `ConstructorViewModel.kt`, `ConstructorScreen.kt`.
- **Outcome:** one-tap rough digitization of a clean strip, refined manually.

## Risks & open questions

- **Gesture routing** is the central integration risk: `Monitor.transformable`,
  the image transform, the trace drag, and the select drag all want pointers in
  the same area. The `ToolMode` switch + a `Monitor` `interactive` flag must
  cleanly hand off; needs careful manual testing on a touch device.
- **Coordinate alignment:** the photo layer must fill the *same* waveform `Box`
  as `SampleHandleOverlay` (which starts at x=0 *after* the 48dp cal-pulse
  column). Verify the photo is placed in that inner box, not the full
  `EditableLead`, or finger→sample x will be offset.
- **Fit-to-view vs medical scale:** with eyeball-only calibration, amplitude is
  whatever the user aligns to under the 800-ADC fit scale. If we later adopt
  true mm scaling (RP5 parity, [[project_calibration_parity]]), previously
  traced amplitudes will *re-read* differently. Acceptable for v1; flag it.
- **Performance:** strips can be thousands of samples; batch each stroke /
  auto-detect result into a single state write to avoid recompose storms.
- **Auto-detect accuracy:** strong grids, low contrast, overlapping neighbor
  traces, and baseline wander degrade detection. Keep it "rough draft + manual
  cleanup," not "trust the output."
- **Undo memory:** per-stroke `IntArray` snapshots are cheap individually but
  cap the stack depth.
- **Open — persistence:** should the photo URI + transform survive app restart /
  be stored with the pathology so a half-finished trace can resume? Default:
  session-only. Decide before Phase A lands if we want resume.
- **Open — image source longevity:** `GetContent()` gives a non-persistable URI;
  for a multi-session trace we'd need `OpenDocument()` +
  `takePersistableUriPermission` or a copy into `filesDir`.

## Verification

- **Phase A:** load a strip → `Position` mode → pan/pinch/rotate moves only the
  photo (grid/trace fixed); opacity slider and lock work; photo renders between
  grid and trace, aligned within the waveform box; switching to `Select`/`Trace`
  stops the photo from moving and stops the canvas from panning.
- **Phase B:** in `Trace` mode, dragging along the photo's printed line writes
  samples that visually track the pointer; a fast sweep interpolates (no gaps);
  derived/locked leads reject writes (`isLeadEditable`); undo restores the
  pre-stroke shape; `save()` persists; the bottom `PreviewPane` loops the result.
- **Phase C:** pick a clean single-lead strip → auto-detect → ghost matches the
  printed trace acceptably → Apply → manual cleanup with Phase B leaves a usable
  waveform. Degraded photo → output is rough but recoverable, app doesn't hang.

## PR breakdown

| # | PR title                                                | Phase | Notes |
|---|---------------------------------------------------------|-------|-------|
| 1 | Positionable photo underlay + tool modes                | A     | Moves image into EditableLead; adds transform state + `Monitor` interactive flag |
| 2 | Freehand sweep tracing + undo                           | B     | Batch sample writer; per-lead undo stack |
| 3 | Auto-detect trace from image (core + apply/cancel UX)   | C     | May split detector core from UI if large |

## Outcome

(To be filled in as work completes.)
