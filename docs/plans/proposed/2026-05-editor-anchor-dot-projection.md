# Editor: unify anchor-handle and waveform-dot projection

**Status:** proposed
**Owner:** —
**Started:** 2026-05-17
**Related issues / PRs:** —
**Follows:** `docs/plans/proposed/2026-05-editor-rendering-parity.md`

## Goal

In the editor canvas the waveform dots (`ChartCanvas`) and the draggable anchor
handles (`AnchorHandleOverlay`) are drawn by two **independent** coordinate
calculations. They agree only by coincidence, and drift apart after editing —
handles no longer sit on the dots they represent.

The goal is to make `AnchorHandleOverlay` a **decorator** over the waveform: a
single projection produces one `List<Offset>` of dot positions, and the handles
are placed *on* those exact offsets instead of being re-projected. After this
change, handle-on-dot alignment is structural — impossible to break — and the
three duplicate projection implementations collapse toward one.

This finishes the job started by `2026-05-editor-rendering-parity.md`: that plan
unified the *render path* (`Lead → ChartCanvas`), but the editor still carries a
second coordinate system (`AnchorSpace`) for the handles.

---

## Background — why handles drift off the dots

The two components locate a point in fundamentally different ways:

| Component | How it places a point |
|-----------|-----------------------|
| `ChartCanvas` | by **array index**: dot `i` at screen X `i * stepX` ([ChartCanvas.kt:60](../../../app/src/main/java/com/example/cardiosimulator/ui/components/ChartCanvas.kt)) |
| `AnchorHandleOverlay` | by **absolute source coordinate**: anchor at `a.x * pxPerSourceX` ([AnchorCanvas.kt:38](../../../app/src/main/java/com/example/cardiosimulator/ui/components/AnchorCanvas.kt)) |

`bakeAnchorsToSamples` emits one sample per integer X starting at `minX`, so the
two agree **only when every anchor has an integer X and `minX == 0`**. The
single-anchor drag path enforces that (`roundToInt()` + `coerceAtLeast(0f)`);
every other edit path breaks it:

- **Origin-handle drag** (`onAllAnchorsMoved`) adds an unsnapped float delta to
  every anchor's X → `minX != 0` → the whole waveform detaches horizontally.
- **Insert before/after** creates fractional-X anchors (midpoints) → that
  handle lands ~`0.5 * stepX` off its dot.
- **Inspector X edit** writes a raw value with no snap.
- **Y truncation**: `bakedSamples()` does `(y + 1024f).toInt()`, so any
  fractional anchor Y shifts its dot slightly below the handle.

Root cause: **index-based vs coordinate-based addressing**, plus three separate
implementations of "sample value → screen `Offset`":

1. `ChartCanvas.drawWithCache` ([ChartCanvas.kt:52-62](../../../app/src/main/java/com/example/cardiosimulator/ui/components/ChartCanvas.kt))
2. `PreviewPane.drawWithCache` ([PreviewPane.kt:62-78](../../../app/src/main/java/com/example/cardiosimulator/ui/components/PreviewPane.kt))
3. `AnchorSpace` / `computeSpace` / `toScreen` ([AnchorCanvas.kt:31-56](../../../app/src/main/java/com/example/cardiosimulator/ui/components/AnchorCanvas.kt))

---

## Design

One projection, computed once, consumed by both layers. Neither the waveform
nor the handles compute screen positions independently.

### Target structure

```
                drawDots(dots)                ← shared DrawScope routine
                  ▲              ▲
                  │              │
       ChartCanvas(points)    AnchorChart(baked, anchors, …)      ← decorator
         projectDots(…)         BoxWithConstraints:
         → drawDots             projectDots(…) ONCE → dots
                                  ├── Canvas { drawDots(dots) }        (waveform)
                                  └── AnchorHandleOverlay(             (handles)
                                        handlePositions = anchorSampleIndex
                                                            .map { dots[it] } )
```

### New / changed types

- **`BakedWaveform`** — `bakeAnchorsToSamples` returns this instead of a bare
  list:
  ```kotlin
  data class BakedWaveform(
      val samples: List<Float>,        // baseline-relative source units
      val originX: Int,                // source-X of samples[0]  (== minX)
      val anchorSampleIndex: List<Int>,// original-order anchor k -> index into samples
  )
  ```
  `anchorSampleIndex` is built by the same `x.toInt()` truncation that places
  the vertex, so a handle and its dot can never disagree on a rule.

- **`projectDots`** — the one projection routine, in absolute source-X:
  ```kotlin
  fun projectDots(values: List<Float>, originX: Int,
                  stepX: Float, stepY: Float, baselineY: Float): List<Offset> =
      values.mapIndexed { i, v -> Offset((originX + i) * stepX, baselineY - v * stepY) }
  ```

- **`DrawScope.drawDots(dots, color)`** — the shared `drawPoints` call.

- **`AnchorChart`** — the decorator: `BoxWithConstraints` so the size is known
  in composition, projects once, stacks the dot `Canvas` + `AnchorHandleOverlay`.

- **`AnchorHandleOverlay`** — slimmed: receives `handlePositions: List<Offset>`
  (placement + hit-test) + `anchors` + `stepX`/`stepY` (pixel→source delta for
  the move callbacks only). `AnchorSpace` / `computeSpace` / `toScreen` deleted.

### Coordinate note

Projecting in **absolute** source-X (`(originX + i) * stepX`) — rather than the
current index-based `i * stepX` — also makes the origin-handle "move all" drag
translate the on-screen trace correctly, instead of leaving it pinned to the
left edge. This is a small behaviour change; see open question Q1.

---

## Current state

- `2026-05-editor-rendering-parity.md` Phases 1–4 appear fully implemented:
  `AnchorHandleOverlay` exists, `bakedPoints` is pre-baked in `EditorScreen`,
  the editor uses `CalibrationPulse + ChartCanvas + AnchorHandleOverlay`, and
  `AnchorEditableCanvas` is gone. That doc is still marked **proposed** — its
  status should be moved to completed independently of this plan.
- `bakeAnchorsToSamples` ([AnchorBaking.kt](../../../app/src/main/java/com/example/cardiosimulator/domain/AnchorBaking.kt)) returns `List<Float>` and discards `minX` and the per-anchor index.
- `EditorScreen` renders `ChartCanvas` and `AnchorHandleOverlay` as two siblings
  in a `Box` ([EditorScreen.kt:248-280](../../../app/src/main/java/com/example/cardiosimulator/ui/screens/EditorScreen.kt)), each with its own coordinate math.

---

## Non-goals

- Changing drag / tap / select / move-all **semantics** — only how positions
  are *projected*. The X-snap (`roundToInt`) on single-anchor drag is preserved.
- Snapping anchor data to integer X at the model level. After this change a
  handle sits on its dot even when the anchor's X is fractional, so the visual
  bug is gone; cleaning fractional X out of the *data* (from inserts / inspector)
  is a separate follow-up.
- Touching Teaching / Testing / Examination / OSKE rendering.
- Changing calibration plumbing (`max` / `value` / `duration`) — that is
  `2026-05-editor-mode.md` Phase 0a, already resolved.
- Pinch-zoom, reference overlay, or any new editor feature.

---

## Plan

Each phase leaves the build green and the editor usable.

### Phase 1 — Baker emits the anchor↔sample link

**Goal:** `bakeAnchorsToSamples` returns enough to map each anchor to its baked
sample, so no downstream code recomputes "which sample is this anchor".

**Files touched:** `domain/AnchorBaking.kt`, `domain/Editable.kt`, baker unit tests.

Steps:
- Add the `BakedWaveform` data class (above). `samples` are the baseline-relative
  floats `bakeAnchorsToSamples` already computes — *before* the `+1024` shift.
- Rewrite `bakeAnchorsToSamples(anchors): BakedWaveform`. Keep the defensive
  sort-by-X, but also record, for each **original** anchor index, its position
  `(x.toInt() - minX).coerceIn(samples.indices)`. Handle the empty / single-anchor
  cases (`originX = first anchor x`, `anchorSampleIndex = [0]`).
- `EditablePart.bakedSamples(): List<Int>` keeps its current persistence
  contract: `bakeAnchorsToSamples(anchors).samples.map { (it + 1024f).toInt() }`,
  cached as today (`samples` field, invalidated by `mutatePart`).
- `EditorScreen` is **not** touched in this phase. It keeps deriving
  `bakedPoints` from `bakedSamples()`, so the editor renders byte-identically.
  Moving `EditorScreen` onto the `BakedWaveform` is deferred to Phase 3, where
  the renderer is rewired anyway — this also avoids a fresh-load regression for
  parts that carry file `samples` but an empty `anchors` list.

**Deliverable:** build green; editor renders identically (still the old sibling
pair); `BakedWaveform` is produced and unit-tested, including out-of-order
anchors and a non-zero `originX`.

---

### Phase 2 — Extract shared `projectDots` / `drawDots`

**Goal:** one projection routine and one draw routine; remove `ChartCanvas`'s
inline copy without changing its public API.

**Files touched:** `ui/components/ChartCanvas.kt` (or a new `ChartProjection.kt`).

Steps:
- Add `projectDots(...)` and `DrawScope.drawDots(dots, color)` (signatures above).
- `ChartCanvas` keeps its `points: Points` signature. Internally, inside
  `drawWithCache`, it calls `projectDots(points.values, originX = 0, stepX,
  stepY, size.height / 2f)` then `onDrawBehind { drawDots(dots, color) }`.
  No `BoxWithConstraints` — `ChartCanvas` stays on `drawWithCache`, so the
  Teaching-mode 12-lead path takes no subcomposition cost.
- Verify `Lead`, `EditableLead`, `BlockTimeline`, and the `@Preview` are
  unaffected (signature unchanged).

**Deliverable:** build green; Teaching / 12-lead visually identical; the
projection lives in exactly one function.

---

### Phase 3 — `AnchorChart` decorator; handles share the dots; rewire `EditorScreen`

**Goal:** the editor draws the waveform and the handles from one shared `dots`
list. `AnchorHandleOverlay` stops projecting.

This phase lands as a single PR: `AnchorHandleOverlay`'s signature changes, so
its only caller (`EditorScreen`) must be rewired in the same change.

**Files touched:** `ui/components/AnchorCanvas.kt`, `ui/screens/EditorScreen.kt`.

Steps:
- Add `AnchorChart` in `AnchorCanvas.kt`:
  ```kotlin
  @Composable
  fun AnchorChart(
      baked: BakedWaveform,
      anchors: List<AnchorPoint>,
      sampleRateHz: Float,
      samplesPerMv: Float,
      selectedIndex: Int?,
      onAnchorSelected: (Int?) -> Unit,
      onAnchorMoved: (Int, Float, Float) -> Unit,
      onAllAnchorsMoved: (Float, Float) -> Unit,
      modifier: Modifier = Modifier,
  ) {
      BoxWithConstraints(modifier) {
          val scale = LocalPixelScale.current
          val stepX = scale.pxPerSampleFor(sampleRateHz)
          val stepY = scale.pxPerAdcCountFor(samplesPerMv)
          val baselineY = with(LocalDensity.current) { maxHeight.toPx() } / 2f
          val dots = remember(baked, stepX, stepY, baselineY) {
              projectDots(baked.samples, baked.originX, stepX, stepY, baselineY)
          }
          val handlePositions = remember(dots, baked.anchorSampleIndex) {
              baked.anchorSampleIndex.map { dots.getOrElse(it) { Offset.Zero } }
          }
          if (dots.size >= 2) {
              Canvas(Modifier.matchParentSize()) { drawDots(dots, Color.Black) }
          }
          if (handlePositions.isNotEmpty()) {
              AnchorHandleOverlay(
                  handlePositions = handlePositions,
                  anchors = anchors,
                  stepX = stepX, stepY = stepY,
                  selectedIndex = selectedIndex,
                  onAnchorSelected = onAnchorSelected,
                  onAnchorMoved = onAnchorMoved,
                  onAllAnchorsMoved = onAllAnchorsMoved,
                  modifier = Modifier.matchParentSize(),
              )
          }
      }
  }
  ```
- Rewrite `AnchorHandleOverlay`: replace `anchors` + `sampleRateHz` +
  `samplesPerMv` with `handlePositions: List<Offset>` + `anchors:
  List<AnchorPoint>` + `stepX` + `stepY`. Placement and hit-test use
  `handlePositions[k]`; drag converts the pixel delta with `stepX`/`stepY`;
  `onAnchorMoved` / `onAllAnchorsMoved` math is unchanged (it reads
  `anchors[idx]` for current source coords, keeps the `roundToInt` X-snap).
  Delete `AnchorSpace`, `computeSpace`, `toScreen`; `drawHandles` takes
  `handlePositions`. The overlay no longer reads `LocalPixelScale`.
- `EditorScreen`: replace the `ChartCanvas` + `AnchorHandleOverlay` sibling pair
  ([EditorScreen.kt:248-280](../../../app/src/main/java/com/example/cardiosimulator/ui/screens/EditorScreen.kt)) with a single `AnchorChart`, passing the
  `BakedWaveform` from Phase 1 and the existing `anchors` list and callback
  lambdas. The `size >= 2` / `isNotEmpty` guards move inside `AnchorChart`.

**Deliverable:** handles sit exactly on their dots in every state — single drag,
move-all, insert before/after, inspector X/Y edit. The overlay contains no
projection logic.

---

### Phase 4 (optional) — Converge `PreviewPane` onto the shared routine

**Goal:** remove the last duplicate projection.

**Files touched:** `ui/components/PreviewPane.kt`.

Steps:
- Replace `PreviewPane`'s inline `drawWithCache` projection with `projectDots` +
  `drawDots`. The scroll offset is preserved by rotating the value list (or
  shifting `originX`) before projecting.

**Deliverable:** one projection routine across the whole app.

---

## Risks & open questions

| # | Risk / question | Mitigation / answer |
|---|-----------------|---------------------|
| R1 | `AnchorHandleOverlay` signature change breaks the `EditorScreen` call site | Land the overlay rewrite + `AnchorChart` + `EditorScreen` rewire in one PR (Phase 3) |
| R2 | `BoxWithConstraints` adds a subcomposition | Only one instance (the editor canvas). `ChartCanvas` stays on `drawWithCache`, so Teaching / 12-lead is untouched |
| R3 | `anchorSampleIndex` must be in original anchor order, not the baker's internal sort order | Baker maps sorted positions back to original indices; covered by a unit test |
| R4 | Empty / single-anchor parts: an `anchorSampleIndex` entry may not index a ≥2-element `dots` list | `getOrElse` guard; `AnchorChart` skips the dot `Canvas` when `dots.size < 2` |
| R5 | `PreviewPane` keeps a third projection copy if Phase 4 is skipped | Acceptable short-term; Phase 4 closes it |
| Q1 | **Open:** absolute-X projection makes the origin-drag translate the trace — the trace can now scroll off-canvas. Index-based projection keeps it pinned but leaves move-all visually dead | Recommended default: **absolute-X** (correct behaviour). Revisit with on-device QA; clamping `originX` is a cheap follow-up if scrolling-off is unwanted |

---

## Verification

### Phase 1
- Unit test: an out-of-order anchor list bakes to the correct `anchorSampleIndex`
  (each entry points at the sample equal to that anchor's Y).
- Unit test: a list whose first anchor has `x > 0` produces `originX == x`.
- Editor renders identically to before (manual check — still old sibling pair).

### Phase 2
- Teaching mode and the 12-lead grid are pixel-identical before/after.
- `BlockTimeline` thumbnails and `EditableLead` unchanged.

### Phase 3
- Drag a single handle: it stays exactly on the waveform dot throughout.
- Drag the origin handle (move-all): handles and trace move together.
- Insert before/after, then inspect: the new handle sits on a dot (no ~½-step
  gap).
- Inspector X/Y edit: handle and dot stay coincident.
- Tap selects; `AnchorInspector` still receives selection and mutates correctly.
- Build green; existing unit tests pass.

### Phase 4
- `PreviewPane` scrolls identically to before; no remaining `drawWithCache`
  projection outside `projectDots`.

---

## PR breakdown

| # | PR title | Phase |
|---|----------|-------|
| 1 | Editor: bake anchors to `BakedWaveform` (samples + originX + anchor index map) | 1 |
| 2 | Editor: extract shared `projectDots` / `drawDots`; route `ChartCanvas` through them | 2 |
| 3 | Editor: `AnchorChart` decorator — anchor handles share the waveform's dots | 3 |
| 4 | Editor: route `PreviewPane` through the shared projection | 4 (optional) |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:**
- **PRs:**
- **Deviations from plan:**
- **Follow-ups spawned:**
