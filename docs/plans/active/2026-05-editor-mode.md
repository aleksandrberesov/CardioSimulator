# Editor mode — parity with the desktop editor

**Status:** in-progress (initial implementation landed; on-device QA pending)
**Owner:** a.beresov
**Started:** 2026-05-11
**Related issues / PRs:** —

> **Update 2026-05-17 — interpolation removed.** The `EasingCurve` enum,
> cubic-Bezier handling, and the per-anchor curve dropdown described in
> Phase 1 / PR 5 have been removed. Anchors are now joined by straight
> line segments only, and waveforms render as discrete dots rather than a
> polyline. Treat the `curve` / `EasingCurve` / Bezier references below as
> historical.

## Goal

CardioSimulator's Editor mode is currently a read-only multi-lead viewer with part-selection highlighting. The legacy desktop editor is a full waveform editor: point-level segment editing, block-level series composition, derived-lead generation, and DB persistence. This plan closes the functional gap on Android using the existing ZIP-backed data flow, so CardioSimulator becomes a usable replacement and not just a viewer.

## Current state

- Display side of the data model is already ported: `WaveformPart` / `EcgSeries` / `SourceSpec` / `AnchorPoint` / `EasingCurve` / `SeriesPartRef` all parse correctly in [`EcgData.kt`](../../../app/src/main/java/com/example/cardiosimulator/domain/EcgData.kt).
- [`EditableLead`](../../../app/src/main/java/com/example/cardiosimulator/ui/display/EditableLead.kt) renders parts from baked `samples: List<Int>` and accepts an `onPartPointsChange` callback that is wired up but unused — see the explicit `// TODO` at [EditorScreen.kt:149](../../../app/src/main/java/com/example/cardiosimulator/ui/screens/EditorScreen.kt:149).
- Data is read from a user-picked ZIP, unzipped to `filesDir/unzipped_ecg/` and served via [`FileEcgSource`](../../../app/src/main/java/com/example/cardiosimulator/data/EcgSource.kt:59). No write path exists.
- TCP client uploads the originally-picked ZIP URI on connect ([AppViewModel.kt:235](../../../app/src/main/java/com/example/cardiosimulator/ui/viewmodels/AppViewModel.kt:235)).
- Block flags (`FREQUENTLY`, `WITHSOUND`, `DURATIONFIXED`, `BROKEN`, `BEAT`, `SKIPQRS`) used heavily by the Blocks tab are **not** represented anywhere in the Android domain model.

A more complete inventory of missing features (Segments tab, Blocks tab, Test tab, derived-lead math, persistence) was produced in chat on 2026-05-11; phases below reflect that inventory.

## Non-goals

- Porting the database layer. Storage stays ZIP/folder-based.
- Porting the `words`-table per-record localization (`LocalizationTag`). Static Android resource i18n is sufficient; the `localizationTag` field parses but stays unused.
- Window/splitter bounds persistence — not meaningful on Android.
- A built-in logging panel — use Logcat.
- 1-px keyboard-arrow nudging as primary input. Touch is primary; numeric +/− nudges substitute for arrow keys.

## Plan

Phases are sequenced so each leaves the editor in a usable state and so the smaller/higher-value pieces land before the heaviest UI work. Suggested merge order: **0 → 0a → 1 → 3 → 2 → 4**. Phase 0a (calibration parity) is required before anchor-driven editing in Phase 1 because the grid coordinate system must agree with the source-file coordinate system. Phase 3 jumps ahead of 2 because it is small, demos well, and stress-tests the Phase 0 serializer on real edits before the timeline UI is built on top of it.

### Phase 0 — Round-trip persistence

Edits must survive an app restart before any editor UX work is worth doing.

- Add a serializer (`EcgFileFormat.writeKeyValues` / `writeSource`) symmetric to the parser at [EcgData.kt:174](../../../app/src/main/java/com/example/cardiosimulator/domain/EcgData.kt:174). Emit `key:value;` with parenthesized `(x,y,curve)` tuples. Round-trip-test against the existing files in `unzipped_ecg/` first.
- Extend `EcgSource` with `writeSeries(name, text)` / `writePart(name, text)`. Implement on `FileEcgSource` only; `AssetEcgSource` stays read-only.
- Introduce editable in-memory models (`EditablePart`, `EditableSeries`) on `AppViewModel`, separate from the immutable parse types. Per-record dirty flag.
- Save-confirm dialog (Yes / No / Cancel) on screen-exit and operating-mode change.
- Undo stack per record.
- **"Export ZIP" / "Download" action** in `SettingsScreen` near the existing "Change folder" button at [SettingsScreen.kt:293](../../../app/src/main/java/com/example/cardiosimulator/ui/screens/SettingsScreen.kt:293). Uses SAF `CreateDocument("application/zip")` to let the user pick the destination. Re-pack happens on demand only, not on every save.
- **TCP upload of edited data:** keep manual. The current auto-upload at [AppViewModel.kt:235](../../../app/src/main/java/com/example/cardiosimulator/ui/viewmodels/AppViewModel.kt:235) re-sends the originally-picked ZIP URI, which would silently transmit stale data after edits. Either re-zip to a cache file before upload, or require the user to press an explicit Upload button — the latter is safer for a clinical dataset and is the default.

**Deliverable:** programmatic mutation of an `EditablePart` survives an app restart, and the user can export a fresh ZIP.

### Phase 0a — Calibration parity

Prerequisite to anchor-driven editing. Today CardioSimulator ignores three fields it already parses — `SourceSpec.max`, `SourceSpec.value` ([EcgData.kt:42-43](../../../app/src/main/java/com/example/cardiosimulator/domain/EcgData.kt:42)) and `WaveformPart.duration` ([EcgData.kt:79](../../../app/src/main/java/com/example/cardiosimulator/domain/EcgData.kt:79)) — and renders every record through global constants `adcCountsPerMv = 256` and `sampleRateHz = 500` ([EcgCalibration.kt:8](../../../app/src/main/java/com/example/cardiosimulator/data/EcgCalibration.kt:8)). The legacy editor instead uses **per-record `AMax / AValue`** for both the grid (`AMax/AValue/10` px per mm) and the px↔mV/ms conversion (`P.Y / AMax * AValue`, `CalcDuration(AMax, AValue, X)`). Records whose `value`, `max`, or true sample rate diverge from CS's hard-coded defaults will misrender — wrong gain and/or wrong duration. Comparison memo: produced 2026-05-12 in chat.

- Promote `max` and `value` from `SourceSpec` onto `WaveformPart` (fall back to 200/2 when absent — matches legacy defaults). Same for `EcgSeries` so series-level scale can read from its parts.
- Replace global `EcgCalibration.adcCountsPerMv` with a **per-part `samplesPerMv`** derived at load time. Stop using a single global gain constant in the renderer.
- Read `duration` (ms) per part. Derive **per-part effective sample rate** `samples.size / (duration / 1000f)` when `duration > 0`, else fall back to 500 Hz. Stash on `WaveformPart` so `pxPerSample` becomes per-part.
- Update [`ChartCanvas`](../../../app/src/main/java/com/example/cardiosimulator/ui/components/ChartCanvas.kt) and [`EditableLead`](../../../app/src/main/java/com/example/cardiosimulator/ui/display/EditableLead.kt) to use the per-part calibration in place of `LocalPixelScale.pxPerAdcCount` / `pxPerSample`. `LocalPixelScale` keeps owning `pxPerMm` and paper-mm geometry; the data-side scaling is what becomes per-part.
- **Editor-mode grid**: switch `Monitor`'s `pxPerMm` for Editor mode only to a *source-anchored* value `AMax/AValue/10` of the currently-edited record (one grid square = one source mm, so dragging an anchor moves an exact integer source-unit). Viewer modes (Teaching / Testing / Examination / OSKE) keep the physical-mm grid — they're displaying, not editing.
- **Series-view shrink**: when rendering a series (block timeline in Phase 2) apply the hard-coded `ARatio = 2` halving against the first part's `AMax/AValue`. Implement the scaffolding here; the timeline consumer arrives in Phase 2.
- Calibration pulse: drive `CalibrationPulse` from the same per-part `samplesPerMv` so the 1 mV box matches the record's own scale instead of the global constant.

**Verification:** load a fixture with non-default `max:400 value:5`, confirm the rendered amplitude matches legacy screen captures of the same record to within 1 px. Load a fixture with `duration` implying 250 Hz, confirm the rendered duration matches the source. Round-trip a record through save → reload → render and assert visual identity.

**Risk:** the per-part `amplitude` field used by [`EcgRepository.assembleWaveform`](../../../app/src/main/java/com/example/cardiosimulator/data/EcgRepository.kt:63) was baked against the record's own `AMax/AValue` at export time, so mixing the new per-part scaling with the existing `amplitude` multiplication will double-scale unless `amplitude` is renormalized or treated as a pre-applied factor and removed from the renderer math. Decide which is authoritative (`amplitude` vs `max/value`) before merging — likely *re-derive* `amplitude` on save so it stays a function of `max/value` and the sample range.

### Phase 1 — Segment / point editor

- Re-implement [`EditableLead`](../../../app/src/main/java/com/example/cardiosimulator/ui/display/EditableLead.kt) to render from `anchors` (with Bezier handling for cubic triples) rather than baked `samples`. Cache rendered samples for speed; invalidate on edit.
- Draggable anchor handles (16–24 dp hit targets). Selected anchor opens a side panel:
  - X / Y numeric fields with +/− nudge buttons
  - Interpolation dropdown — `EasingCurve` enum at [EcgData.kt:17](../../../app/src/main/java/com/example/cardiosimulator/domain/EcgData.kt:17) is already complete
  - Delete / Insert-before / Insert-after / Center-Y buttons
- Move-all-anchors when dragging the first (origin) anchor.
- Property inspector (Title / Identity / Lead / Pathology / Max / Value) bound to `EditablePart`.
- Auto-fill helpers `makeTitle()` / `makeIdenty()` from Pathology + Lead.
- Segment list with text filter + Add / Copy / Delete buttons.
- In-app clipboard for anchor lists (copy / paste).
- Pinch-to-zoom on the canvas — substitutes for missing pixel precision.

**Risk:** Bezier-pair handling is subtle — the cubic flag usually implies a 3-point group consumed together. Mirror exactly or the renderer will desync.

### Phase 3 — Derived-lead generation

Pure math. Sequenced ahead of Phase 2 because it's small and validates the Phase 0 serializer.

- `combineIII_aVR_aVL_aVF(leadI, leadII, target: Lead)` — Einthoven / Goldberger linear combinations.
- `combineV1_V3_V4_V5(leadV2, leadV6, target: Lead)` — cos/sin angular projection.
- "Generate derived leads" button on both the Segment editor (lead-level) and the Series editor (series-level).

### Phase 2 — Series / block timeline editor

- Add block-flag fields to the domain. `SeriesPartRef` at [EcgData.kt:52](../../../app/src/main/java/com/example/cardiosimulator/domain/EcgData.kt:52) has no flag byte today — extend it and update the parser/serializer to encode the six flags (`FREQUENTLY | WITHSOUND | DURATIONFIXED | BROKEN | BEAT | SKIPQRS`).
- Horizontal timeline composable: each block draws its part's thumbnail (reuse `ChartCanvas`); width = duration, height = amplitude.
- Drag a block left/right to reorder; drag edge handles to resize. Snap to neighbours.
- Per-block icon overlay (Beat, Lock, Broken, Sound, Skip) drawn over the thumbnail.
- Long-press → context menu with the six flag toggles.
- Toolbar: Insert-before / Insert-after / Delete / Scale-to-source.
- Series inspector (Title / Lead / Pathology / Params).
- Group series by Pathology+Params in the rhythm list, with expandable headers — extend [`RhythmChoosingPanel`](../../../app/src/main/java/com/example/cardiosimulator/ui/panels/RhythmChoosingPanel.kt).
- Pathology-dropdown filter alongside the existing text search.
- Tap-block-to-jump: open the underlying part in the segment editor (Phase 1).

### Phase 4 — Preview & visual polish

- HR=60 footer preview pane re-rendering the current segment/series in a loop.
- Reference-image overlay (load image → draw at adjustable opacity behind the canvas). Persist bitmap position via `DataSourcePrefs`.
- "Test" screen equivalent: pick a part, render both the source curve and the raw 1024-baseline samples side-by-side.
- Paper-grid legend ("10 mm/mV  50 mm/s") on the canvas — grid already painted by `ekgGrid()`, just add the text.

## Risks & open questions

- **Bezier-group invariants.** Cubic curves usually come in triples. The Kotlin parser at [EcgData.kt:209](../../../app/src/main/java/com/example/cardiosimulator/domain/EcgData.kt:209) doesn't enforce this. Decide whether to validate on load or only on render. *(Open.)*
- **Touch precision vs. 1-px arrow nudging.** Compensating with pinch-zoom + numeric nudge panel. Verify on-device before Phase 1 lands. *(Open.)*
- **Existing TCP auto-upload would re-send the originally-picked ZIP after edits, silently transmitting stale data.** Resolved 2026-05-11: keep TCP upload manual; do not auto-zip on connect.
- **ZIP re-pack performance** for large archives during Export. Probably fine, but worth a measurement on a real dataset before claiming Phase 0 done.
- **Block-flag wire format.** The Android parser drops them today — need to decide the serialized representation (extra column inside the `(x,y,identy,offset)` tuple, or a separate `flags:` field). *(Open — resolve in Phase 2.)*

## Verification

- **Phase 0:** edit a part programmatically → restart app → file on disk reflects the edit; "Export ZIP" produces a valid archive that loads back into the app.
- **Phase 0a:** load fixtures with non-default `max`/`value` (e.g. 400/5) and non-500 Hz `duration`; visually compare against legacy screen captures of the same record; assert pixel-accurate match for the 1 mV calibration pulse and full-scale amplitude.
- **Phase 1:** open a known segment, drag an anchor, verify the rendered waveform updates; save; reopen; anchor position persists. Round-trip a sample file through parse → serialize → parse and assert structural equality.
- **Phase 3:** generate III from I+II on a known sample, compare numerically against legacy output for the same input.
- **Phase 2:** insert a block, toggle each of the six flags, save, reopen, verify flags persisted and icons render.
- **Phase 4:** visual / manual.
- Build green and existing unit tests pass after each phase. Run on a tablet (the editor is not realistic on a phone-class screen).

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Editor: serialize SourceSpec back to text + FileEcgSource write path | 0 | Round-trip test fixture |
| 2 | Editor: EditablePart/EditableSeries with dirty tracking + save-confirm | 0 | UI affordance only on Editor screen |
| 3 | Editor: Export ZIP action in Settings | 0 | SAF CreateDocument |
| 3a | Calibration: per-part max/value/duration plumbed into renderer | 0a | Removes global adcCountsPerMv / sampleRateHz |
| 3b | Editor mode: source-anchored grid; series-view ARatio shrink | 0a | Editor-only; viewer modes unchanged |
| 4 | Editor: anchor-driven rendering + drag handles in EditableLead | 1 | Replaces samples-based render |
| 5 | Editor: anchor side-panel (X/Y/curve/insert/delete) + inspector | 1 | |
| 6 | Editor: segment list with Add/Copy/Delete + filter | 1 | |
| 7 | Editor: derived-lead generation (I/II → III/aVR/aVL/aVF, V2/V6 → V1/V3/V4/V5) | 3 | Pure math + button |
| 8 | Editor: extend SeriesPartRef with flags; parse/serialize | 2 | Foundation for timeline |
| 9 | Editor: block timeline composable with drag/resize | 2 | |
| 10 | Editor: block flags context menu + icon overlay | 2 | |
| 11 | Editor: series inspector + grouped rhythm list + pathology filter | 2 | |
| 12 | Editor: HR=60 footer preview | 4 | |
| 13 | Editor: reference-image overlay + Test preview screen | 4 | |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:**
- **PRs:**
- **Deviations from plan:**
- **Follow-ups spawned:**
