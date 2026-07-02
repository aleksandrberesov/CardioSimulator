# Android parity — adaptive monitor DisplayScale by lead count ("small trace in a sea of cells" fix)

**Status:** completed
**Owner:** a.beresov
**Started:** 2026-07-01
**Related issues / PRs:** Win→Android sync of the WinUI change (CardioSimulatorWin, 2026-07-01)

## Goal

On the live monitor, when the user shows **fewer than the full 12 leads**, each lead cell
(`cellH = containerHeight / rows`) grows tall while the grid squares and the trace stay at the
fixed physical scale (`pxPerMm`, driven by the constant `displayScale = 0.4`). The result is a
small waveform floating in a big field of grid squares — "a small graphic in a sea of cells."

Fix: scale `displayScale` **up as the lead count drops**, so a sparse layout renders as a
proportionally larger version of the dense 12-lead view (grid *and* trace scale together, keeping
the mm-based relationships intact). Because `displayScale` is the single `pxPerMm` anchor, the
grid squares, trace amplitude, calibration pulse, left margin, and the caliper/ruler math all
scale in lock-step. This ports the WinUI change (already shipped on the Windows port) to the
Android master.

The multiplier is a **hand-tuned lookup by number of leads** (not a formula off row count) — this
was an explicit product decision on the Windows side.

## Current state

**Windows (done, reference implementation)** — `CardioSimulatorWin`:

- `src/CardioSimulator.App/Rendering/EcgRenderer.cs`:
  - New `DisplayScaleFactor(int leadCount)` lookup (current values):

    | leads | ×factor |
    |------:|:--------|
    | ≤ 1   | 6.0     |
    | 2     | 4.4     |
    | 3     | 3.2     |
    | 4     | 3.2     |
    | 5     | 2.4     |
    | 6+    | 2.0     |

  - New `EffectivePxPerMm(MonitorModeModel mode) => PxPerMm(mode.DisplayScale * DisplayScaleFactor(mode.Count))`.
  - `Render(...)` now builds its `PixelScale` from `EffectivePxPerMm(mode)` instead of
    `PxPerMm(mode.DisplayScale)`.
- `src/CardioSimulator.App/Controls/EcgMonitorControl.cs`: the ruler's Δt/Δmv/bpm math now uses
  `EcgRenderer.EffectivePxPerMm(_mode)` too, so measurements stay correct at the boosted scale.
- **Deliberately left on the base scale (no factor):** the single-lead editor and preview surfaces
  — `EcgRenderer.RenderEditableLead`, `EditableLeadControl`, `PreviewPaneControl`,
  `ConstructorScreen` — still call `PxPerMm(mode.DisplayScale)` directly. They were never the
  "sea of cells" case.
- Keyed on `mode.Count` (lead/pane count), independent of the column scheme. Compare mode benefits
  too, since panes = `Count`. Nothing is persisted — the factor is derived per-render.
- Built clean x64 (0 warnings / 0 errors).

**Android (target)** — `CardioSimulator`:

- **Single injection point** for the live monitor:
  `app/src/main/java/com/example/cardiosimulator/ui/display/Monitor.kt:118`
  ```kotlin
  val pxPerMm = density.density * (160f / 25.4f) * mode.displayScale
  ```
  `pxPerMm` (:118) → `pixelScale` (:119–127) → provided via `LocalPixelScale` (:199) and read by
  every child (`ChartCanvas`, `CalibrationPulse`, `TraceOverlay`, `Lead`, grid draw). The
  **caliper/ruler** at `Monitor.kt:277,280` also reads the same `pixelScale.pxPerSec` /
  `pixelScale.pxPerMv`. So multiplying `mode.displayScale` by the per-count factor **here fixes
  both the draw and the ruler in one edit** (cleaner than Windows, which needed two sites).
- `rows`/`columns` are already computed at `Monitor.kt:111–112` from `mode.count` and
  `mode.seriesScheme` (`maxColumns` at :104–109 — note Android has a 4th scheme `ThreeByFour`
  that Windows lacks). We keep the multiplier **count-based** for faithfulness, so scheme
  differences don't matter.
- `MonitorModeModel` (`app/src/main/java/com/example/cardiosimulator/domain/MonitorModeModel.kt`):
  `count` and `displayScale: Float = 0.4f` (:67). No new state — the factor is purely derived.
- `data/PixelScale.kt`: the anchor/derivation data class (`pxPerMm` → `pxPerMv/pxPerSec/…`,
  `smallGridStepPx = pxPerMm`, `largeGridStepPx = pxPerMm * 5`). Good home for a testable
  top-level `displayScaleFactor(leadCount)` helper.
- **Leave on the base scale (parity with Windows):** `ConstructorScreen.kt:715` computes its own
  `pxPerMm = density.density * (160f/25.4f) * monitorMode.displayScale` for the **single-lead
  editor** — do **not** apply the factor there. Fixed-`6.3f` preview scales (`ChartCanvas.kt:99`,
  `PreviewPane.kt:165`, `Lead.kt:193`) are unrelated and untouched.
- Tests live in `app/src/test/java/com/example/cardiosimulator/data/` (JUnit) — see e.g.
  `EcgSvgRendererTest.kt` for style.

## Non-goals

- No new UI control and no persisted setting. `setDisplayScale`/`displayScale` stays a
  fixed-`0.4` base on both platforms; the factor is applied only at render time.
- Do **not** touch the single-lead editor/preview/constructor scale (matches the Windows scope).
- No change to `SeriesScheme`, `LeadsGrid` layout, zoom/pan, or the compare-mode pane model.
- Not a formula off `rows` — keep the hand-tuned per-count table (product decision).

## Plan

### Phase 1 — helper + wire-up (one PR)
1. Add a top-level function in `data/PixelScale.kt`:
   ```kotlin
   /**
    * Per-lead-count multiplier applied to MonitorModeModel.displayScale on the live monitor.
    * With fewer leads each cell is much taller (cellH = height / rows), leaving the fixed-scale
    * trace as a small graphic in a sea of grid squares; scaling the whole cell (grid + trace) up
    * for sparse layouts makes them read as densely as the full 12-lead view. Hand-tuned by number
    * of leads (not a formula). Only ever scales up.
    */
   fun displayScaleFactor(leadCount: Int): Float = when {
       leadCount <= 1 -> 6.0f
       leadCount == 2 -> 4.4f
       leadCount == 3 -> 3.2f
       leadCount == 4 -> 3.2f
       leadCount == 5 -> 2.4f
       else -> 2.0f
   }
   ```
   (Mirrors the Windows values exactly — 6+ leads use the base ×2.)
2. `Monitor.kt:118` — apply the factor:
   ```kotlin
   val pxPerMm = density.density * (160f / 25.4f) * mode.displayScale * displayScaleFactor(mode.count)
   ```
   Add `mode.count` to the `remember(...)` keys on :119 so `pixelScale` recomputes when the lead
   count changes (currently keyed on `pxPerMm, mode.speed, mode.calibration, scale` — since
   `pxPerMm` now embeds the factor it already changes with count, but add `mode.count` explicitly
   for clarity/safety).
3. Leave `ConstructorScreen.kt:715` unchanged.

### Phase 2 — test
4. Add `app/src/test/java/com/example/cardiosimulator/data/PixelScaleTest.kt` (or extend an
   existing data test): assert `displayScaleFactor` returns 6.0/4.4/3.2/3.2/2.4 for 1..5,
   2.0 for 6/7/12, and that `leadCount <= 0` maps to 6.0 (same bucket as 1, matching the
   Windows `<= 1` arm).

## Risks & open questions

- ~~**6 vs 7+ discontinuity (mirror or reconcile?).**~~ **Resolved 2026-07-01:** the transient
  Windows table briefly had 7+ → ×1.0 (a jump at 6→7). The final decision is **6+ → ×2.0** (all
  counts scale up ≥ ×2); both platforms and the doc-comment now agree. First Android impl landed
  the old 7+ → ×1.0 and was corrected in the same session (`PixelScale.kt` + `PixelScaleTest.kt`).
- **Perceived amplitude change.** Boosting `displayScale` up to ×6 makes a 1-lead trace 6× taller;
  the trace can clip the cell for very tall waveforms. Windows accepts this (the grid grows with
  it, so it reads as a zoom, not distortion). Verify the tallest pathologies at 1–2 leads.
- **Caliper correctness.** Confirm the on-screen Δt/Δmv/bpm still matches a known interval after
  the boost (it should — the caliper divides by the same `pixelScale`).
- **`ThreeByFour` scheme (Android-only).** Count-based factor is scheme-agnostic, so no special
  handling; just sanity-check the 3×4 / 12-lead layout (12 leads → ×2.0, same as any 6+ layout).

## Verification

- `./gradlew :app:assembleDebug` builds clean; `./gradlew :app:testDebugUnitTest` passes incl. the
  new `displayScaleFactor` test.
- Manual, on the live monitor (Teaching/monitor screen): switch lead count 1 → 2 → 3 → 6 → 12 and
  confirm the grid+trace scale up for sparse counts and the 12-lead view is visually identical to
  before. Repeat one case in compare mode (panes = count).
- Ruler: drop a caliper across one large square at 1 lead and at 12 leads; the mm/ms readout must
  be consistent (the physical scale is the same *per cell*, only the pixel size differs).

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Adaptive monitor displayScale by lead count | 1 | `PixelScale.kt` helper + `Monitor.kt:118` wire-up; leave editor/preview untouched |
| 2 | Unit test for displayScaleFactor | 2 | JUnit in `app/src/test/.../data/` |

---

## Outcome

- **Result:** shipped (Android working tree, 2026-07-01).
- **Changes:** `data/PixelScale.kt` — `displayScaleFactor(leadCount)` helper; `ui/display/Monitor.kt:119`
  applies it (`… * mode.displayScale * displayScaleFactor(mode.count)`), feeding both the draw and
  the caliper. `app/src/test/java/com/example/cardiosimulator/data/PixelScaleTest.kt` added.
  `ConstructorScreen.kt` single-lead editor left on base scale, as planned.
- **Deviations from plan:** the first pass mirrored the transient Windows table (7+ → ×1.0); this
  was corrected to the final **6+ → ×2.0** in the same session (Windows, Android impl, and test all
  updated together).
- **Follow-ups spawned:** none.
