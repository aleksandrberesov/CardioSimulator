# Migration to flat-pathology architecture & unified rendering pipeline

**Status:** proposed
**Owner:** a.beresov
**Started:** 2026-05-21
**Related docs:** [`docs/architecture.md`](../../../VLN_Project/CardioSimulator/docs/architecture.md), [`docs/ecg-rendering-pipeline.md`](../../../VLN_Project/CardioSimulator/docs/ecg-rendering-pipeline.md), [`docs/data-structure.md`](../../../VLN_Project/CardioSimulator/docs/data-structure.md)
**Supersedes:** `docs/plans/active/2026-05-editor-mode.md` (Phases 0/0a/1/2/3/4 — anchor-based design is dropped)
**Target on-repo path after approval:** `docs/plans/active/2026-05-architecture-and-rendering-migration.md`

---

## Context

`docs/architecture.md` and `docs/ecg-rendering-pipeline.md` were redesigned on 2026-05-21 (commit `cf38e31` "Redesign docs around new pathology .dat format") to target a **flat dataset format**: one `.dat` per pathology, all 12 leads inside, raw ADC samples, no anchors, no per-record calibration, no Parts/Series indirection. The current code still implements the Parts/Series + anchor-baking design with per-part calibration and a divergent editor pipeline. This plan migrates the codebase to the new target.

**One clarification on top of the new docs** (decided with user 2026-05-21): the new architecture text says "no editor in the target." That is wrong as stated. The editor stays — but it must render through the **same** `Points → ChartCanvas` pipeline as the viewer. A "point" is now `(sample_index, ADC_value)`, not a geometric anchor. In editor mode, every drawn sample gets a vertical-drag handle that mutates `samples[i]` directly. `AnchorPoint` / `bakeAnchorsToSamples` / per-part calibration are still removed; the editor just edits the raw `IntArray`.

**Dataset** lives outside this repo (built by `Data/build_pathologies.py` in a sibling project). The app consumes `Pathologies.zip` only — no in-app builder.

**Shape:** phased directly on `master`. Each phase = one PR, each leaves the app shippable.

---

## Current state

Source tree: `app/src/main/java/com/example/cardiosimulator/`

**To be added / heavily rewritten:**
- `domain/Pathology.kt` (new) — `Lead`, `PathologyEntry`, `PathologyManifest`, `LeadStream`, `PathologyFile`
- `domain/PathologyParser.kt` (new) — manifest + `.dat` text parsers
- `data/EcgSource.kt` — replace contract: `readManifest()`, `readPathology(id)`, `listPathologies()`
- `data/EcgRepository.kt` — replace `assembleWaveform` / `assembleWaveformParts` with `leadWaveform(id, lead)`; cache manifest; lazy `readPathology`
- `data/AssetEcgSource.kt` (new) — reads `assets/Pathologies/`
- `data/FileEcgSource.kt` (new) — reads `filesDir/pathologies/`
- `data/ZipExtractor.kt` (new) — read-only SAF→filesDir unzip
- `ui/viewmodels/RhythmViewModel.kt` — new shape: `rhythms`, `selectedRhythm`, `waveforms: Map<Lead, Points>`
- `ui/viewmodels/AppViewModel.kt` — `dataState`, `isDataConfirmed`, `setDataZip` flow
- `ui/display/Monitor.kt` — drop `sourceAnchoredCalibration` branch (currently lines 74–93)
- `ui/components/ChartCanvas.kt` — drop `sampleRateHz` / `samplesPerMv` params; take only `Points` + `LocalPixelScale` (currently lines 67–94)
- `data/PixelScale.kt` — drop `sourceAnchored()` factory (lines 61–83) and per-part overrides `pxPerSampleFor` / `pxPerAdcCountFor`
- `ui/screens/EditorScreen.kt` — rebuild on unified `Lead` + per-sample drag overlay
- `ui/viewmodels/EditorViewModel.kt` — edit target becomes `Map<Lead, IntArray>` of one `PathologyFile`

**To be deleted (legacy anchor era):**
- `domain/EcgData.kt` (whole file: `WaveformPart`, `EcgSeries`, `SeriesPartRef`, `SourceSpec`, `EcgFileFormat`, `BlockFlags`, `PathologyGroup`)
- `domain/Editable.kt` (`EditablePart`, `EditableSeries`, `UndoStack`)
- `domain/AnchorBaking.kt` (`bakeAnchorsToSamples`, `BakedWaveform`)
- `domain/AnchorClipboard.kt`
- `ui/components/AnchorCanvas.kt` and `AnchorHandleOverlay`
- `ui/components/PreviewPane.kt`
- `ui/components/BlockTimeline.kt`
- `ui/components/ReferenceOverlay.kt`
- `ui/display/EditableLead.kt`
- `ui/panels/AnchorInspector.kt`
- `ui/panels/SeriesInspector.kt`
- `ui/panels/EditorLeftPanel.kt` (or trimmed)
- `ui/panels/EditorRightPanel.kt` (or trimmed)
- `data/ZipCompressor.kt` (replaced by direct `.dat` write)
- `data/ZipDecompressor.kt` (replaced by `ZipExtractor`)

**Unchanged (already aligned):**
- `domain/DerivedLeads.kt` — already implements Einthoven/Goldberger + V2/V6 projection (matches §3.2 of pipeline doc)
- `data/EcgCalibration.kt` — already has `gainMmPerMv=10`, `sampleRateHz=500`, `adcCountsPerMv=256`
- `data/Points.kt` — already a `List<Float>` wrapper
- `network/*` — TCP layer is orthogonal
- `domain/AppStateModel.kt`, `OperatingModeModel.kt`, `AppBuilder.kt`, `MonitorModeModel.kt`
- All four viewer screens (`Teaching/Testing/Examination/OSKE`) only need their `Lead → ChartCanvas` plumbing rewired

---

## Non-goals

- Building the `.dat` dataset producer (`build_pathologies.py`) — external repo.
- Adding new features (recording, multi-pathology compare, charts beyond 12-lead).
- Editor undo/redo, anchor clipboard, block timeline — features of the dropped anchor era, not rebuilt.
- TCP protocol changes.
- Per-app language changes (handled by separate active plan `2026-04-localization.md`).
- Migration of `.claude/worktrees/*` — those are throwaway agent sandboxes.

---

## Plan

Each phase is one PR straight to `master`. Each leaves the app installable and the viewer screens functional. Editor stays broken during Phase 2/3 only if absolutely necessary; otherwise legacy editor remains running on legacy data until Phase 4 swaps it.

### Phase 1 — New domain & data layer (additive only)

**PR-1.** Add new types and parsers. No existing code changes behaviour.

- Add `domain/Pathology.kt`: `PathologyEntry(id, titleEn, nameRu?, leadsCount, fileName)`, `PathologyManifest(version, baseline, leadOrder, entries)`, `LeadStream(lead, samples: IntArray)`, `PathologyFile(id, titleEn, nameRu?, leads: Map<Lead, LeadStream>)`. `Lead` enum is already in `domain/EcgData.kt` — move it into `Pathology.kt` and re-export with a typealias to keep legacy callers compiling for now.
- Add `domain/PathologyParser.kt`: pure-Kotlin `parseManifest(text): PathologyManifest` and `parsePathology(text): PathologyFile`. Mandatory `version` check.
- Add `data/PathologyEcgSource.kt` interface (separate from legacy `EcgSource`) with `readManifest()`, `readPathology(id)`, `listPathologies()`. Two impls: `AssetEcgSource(assets)`, `FileEcgSource(dir: File)`.
- Add `data/ZipExtractor.kt` (UTF-8, flat, no charset detection — per `ecg-rendering-pipeline.md:60-64`).
- Add a new `EcgRepository.leadWaveform(id: String, lead: Lead): Points?` method **next to** existing `assembleWaveform`. Implementation: `readPathology(id)` → `leads[lead]?.samples ?: DerivedLeads.synthesize(lead, leads)` → `Points(FloatArray { (s[i] - 1024f) })`.
- Unit tests for parsers (round-trip a small fixture: 1 manifest, 2 `.dat` files with full + partial leadset).

**Exit criteria:** legacy app behaves identically; new code paths exist but no caller. `./gradlew assembleDebug test` green.

### Phase 2 — Viewer screens consume the new pipeline

**PR-2.** Swap the four viewer screens onto `leadWaveform`.

- Reshape `RhythmViewModel`:
  - Inputs: `EcgRepository`.
  - State: `rhythms: StateFlow<List<PathologyEntry>>`, `selectedRhythm: StateFlow<PathologyEntry?>`, `waveforms: StateFlow<Map<Lead, Points>>`.
  - Actions: `loadManifest()`, `selectRhythm(id)`, `refresh()`.
- Update `MainScreen.kt`:
  - Construct `RhythmViewModel(ecgRepository)` keyed by `"${mode.id.name}_rhythm"`.
  - Add `dataState` guard: route to `DataSourceScreen` when `NotConfigured | Loading | Error`.
- Update `AppViewModel.kt`:
  - `setDataZip(uri)` → `ZipExtractor.extract` → `FileEcgSource(targetDir)` → `EcgRepository.setSource` → `loadManifest`.
  - `dataState: StateFlow<DataState>` sealed: `NotConfigured | Loading | Ready(count) | Error(msg)`.
  - `isDataConfirmed: StateFlow<Boolean>`.
- Update `TeachingScreen / TestingScreen / ExaminationScreen / OSKEScreen`:
  - Read `rhythmViewModel.waveforms` and pass `waveforms[lead]` into `LeadsGrid → Lead`.
- `ChartCanvas` signature: temporarily keep `sampleRateHz` / `samplesPerMv` params with default values (`EcgCalibration.sampleRateHz` / `adcCountsPerMv`) so the legacy editor still compiles.
- `Monitor.kt`: keep both branches for now; `sourceAnchoredCalibration` parameter still passed by editor.

**Exit criteria:** viewer screens render correctly off `Pathologies.zip`. Legacy editor still loads via the legacy `EcgRepository.assembleWaveform` paths. Both coexist this one PR.

### Phase 3 — Unified `ChartCanvas` & `PixelScale`

**PR-3.** Drop the calibration-divergence between viewer and editor.

- `ChartCanvas.kt`: remove `sampleRateHz` / `samplesPerMv` parameters entirely. Signature becomes `ChartCanvas(points: Points, modifier: Modifier)`. Internally reads `LocalPixelScale.current` only. Project samples via `i * pxPerSample` × `baselineY - sample[i] * pxPerAdcCount` (pipeline doc §5a).
- `Monitor.kt`: drop the `sourceAnchoredCalibration` branch (`Monitor.kt:74-93`). Only the viewer-mode `PixelScale(pxPerMm, paperSpeed, gainZoom, cal)` remains.
- `PixelScale.kt`: delete `sourceAnchored()` factory and `pxPerSampleFor(hz)` / `pxPerAdcCountFor(spmv)` methods.
- `Lead.kt`: update to the new `ChartCanvas(points)` signature (drop the extra args it forwards).
- Legacy editor will compile-fail; **bring it along in this PR** with a temporary local helper inside `EditorScreen.kt` that re-derives per-part scaling. The editor will still render but without source-anchored grid. Phase 4 rewrites it properly.

**Exit criteria:** all viewer modes render via the unified pipeline. Editor still loads, may look mildly off, but does not crash.

### Phase 4 — Rebuild editor on the unified pipeline

**PR-4.** Editor edits raw ADC samples; handles per visible point.

- Reshape `EditorViewModel`:
  - Edit target: a `MutableState<PathologyFile>` (full file in memory) plus a dirty flag per lead.
  - Actions: `selectPathology(id)`, `selectLead(lead)`, `setSample(lead, index, adcValue)`, `revertLead(lead)`, `save()`.
- New `ui/components/SampleHandleOverlay.kt`: an overlay Composable that places invisible vertical-drag hit-targets at each `(x[i], y[i])` of the rendered points (projection identical to `ChartCanvas`). On drag, translates Δy → Δ(ADC counts) via `LocalPixelScale.pxPerAdcCount` and calls `onSampleChanged(i, newValue)`. Sub-sampling: when `pxPerSample < 4 dp` show a handle every `ceil(4 dp / pxPerSample)` samples (handles ride on the rendered line; intermediate samples still draggable via region snap-to-nearest).
- `ui/display/EditableLead.kt` (rewritten thin): composes a `Lead` (`CalibrationPulse` + `ChartCanvas`) and overlays `SampleHandleOverlay` over the chart area. Same `Points` array as viewer; no anchor model.
- `EditorScreen.kt`: replace the current Parts/Series UI with a Lead-tabs + EditableLead view. Drop `EditorLeftPanel`, `EditorRightPanel`, `AnchorInspector`, `SeriesInspector`. Add a minimal toolbar: `Save`, `Revert`, `Lead selector`.
- Writable source extension: add `PathologyEcgSource.writePathology(file: PathologyFile)` on `FileEcgSource` (asset source is read-only). Serialize via `domain/PathologyParser.kt::serialize(file)`. `AppViewModel.saveEditor()` calls it.
- Delete legacy editor files now unused:
  - `domain/AnchorBaking.kt`
  - `domain/AnchorClipboard.kt`
  - `domain/Editable.kt`
  - `ui/components/AnchorCanvas.kt`
  - `ui/components/PreviewPane.kt`
  - `ui/components/BlockTimeline.kt`
  - `ui/components/ReferenceOverlay.kt`
  - `ui/panels/AnchorInspector.kt`
  - `ui/panels/SeriesInspector.kt`
  - `ui/panels/EditorLeftPanel.kt`
  - `ui/panels/EditorRightPanel.kt`

**Exit criteria:** editor loads a pathology, every visible point is draggable, save persists to `filesDir/pathologies/<id>.dat`, reload restores edits. Save uses the same `.dat` text format the parser reads (round-trip).

### Phase 5 — Strip legacy code & docs reconciliation

**PR-5.** Final cleanup.

- Delete `domain/EcgData.kt` legacy types (Parts/Series, `EcgFileFormat`, `SourceSpec`, `BlockFlags`, `PathologyGroup`). Confirm no remaining imports.
- Delete `data/ZipDecompressor.kt`, `data/ZipCompressor.kt`.
- Delete legacy `EcgRepository.assembleWaveform` / `assembleWaveformParts` and `loadFromSeries` paths.
- Delete the `EcgSource` legacy interface (the new `PathologyEcgSource` should be renamed to `EcgSource` at the very end of this PR for naming cleanliness; do this rename in a single commit at the tail).
- Edit `docs/architecture.md` §10 to correct the "no editor in target architecture" claim — replace with the actual decision (editor stays, shares `Points → ChartCanvas`, per-sample drag).
- Edit `docs/ecg-rendering-pipeline.md` "What is no longer in the pipeline" to remove the bullet that says "there is no editor; samples are stored directly" — replace with "anchor baking is gone; the editor edits the raw IntArray directly through the same `Points → ChartCanvas` projection."
- Move `docs/plans/active/2026-05-editor-mode.md` → `docs/plans/completed/` with an *Outcome: dropped* note (one sentence: superseded by `2026-05-architecture-and-rendering-migration.md`).
- Update `docs/plans/README.md` Index accordingly.
- Update memory entries (these become stale on completion of Phase 4):
  - `memory/project_calibration_parity.md` — remove or rewrite: per-record calibration no longer exists; renderer uses fixed `EcgCalibration` only.
  - `memory/project_editor_persistence.md` — rewrite: editor edits raw IntArray of a single `.dat` file; save writes `<id>.dat` in `filesDir/pathologies/`; no ZIP re-pack, no anchors.

**Exit criteria:** `git grep -i 'WaveformPart\|EcgSeries\|AnchorPoint\|bakeAnchorsToSamples\|sourceAnchored\|pxPerSampleFor\|pxPerAdcCountFor'` in `app/src/main/` returns nothing. App is a single coherent codebase against the new architecture.

---

## Risks & open questions

- **`.dat` write format**: docs describe the reader but the writer is implicit ("symmetric"). Need to confirm UTF-8, LF line endings, exact field order on save. Resolution: lock this in Phase 4 by writing a `serialize()` that round-trips the parser tests.
- **Sample-handle density on long leads**: at 500 Hz × ~10 s recording = 5000 samples per lead, drawing 5000 hit-targets is wasteful. Phase 4 must subsample handles (one per ≥4 dp visually) and snap to the nearest sample on drag. Confirm UX: discrete handles vs region-drag.
- **Manifest version handling**: pipeline doc §2.1 says "MUST be validated; older or newer formats are rejected." We need a version constant in `PathologyParser.kt` and a clear error path through `dataState = Error(...)`.
- **Asset source vs file source ordering**: `MainActivity.onCreate` boots with `AssetEcgSource(assets)`; SAF pick swaps to `FileEcgSource`. If the bundled assets don't yet exist (this app may ship empty), `loadManifest` will fail at boot. Decision: bundle a small placeholder manifest in assets OR initialize `dataState = NotConfigured` until user picks a ZIP. Lean toward NotConfigured.
- **Memory entries stale during migration**: `project_calibration_parity.md` and `project_editor_persistence.md` describe the OLD direction. Don't update until Phase 4 lands; their content is still factually true of the current code.
- **TCP send path**: `AppViewModel.sendStartCommand(pathologyId)` currently takes a series/part id. After migration it takes a pathology id. No protocol break expected, but confirm in Phase 2.
- **EditorMode branch**: there's an in-flight `EditorMode` local branch with anchor-era work. It will be obsolete after Phase 4. Decision deferred — likely just delete the branch on completion.

---

## Verification

**Per-phase smoke checklist** (each PR runs this before merge):

| Check | Tool |
|---|---|
| `./gradlew assembleDebug` | CLI |
| `./gradlew test` | CLI |
| `./gradlew lint` clean for changed files | CLI |
| App launches | adb / emulator |
| Mode switch Teaching → Testing → Examination → OSKE no crash | manual |
| If editor exists at this phase: open editor, no crash | manual |

**End-to-end (run at end of Phase 4 and again after Phase 5):**

1. Fresh install. App boots → `DataSourceScreen` (NotConfigured).
2. SAF-pick a known `Pathologies.zip`. `dataState → Loading → Ready(N)`.
3. Mode = Teaching. `RhythmChoosingPanel` lists all pathologies. Select "Atrial tachycardia" (`tachpm`). 12-lead grid renders. Eyeball: grid scale matches paper (`5 mm = 0.2 s` at default speed).
4. Mode = Examination, Testing, OSKE — same pathology, same waveform shapes.
5. Mode = Editor. Pick lead `II`. Every visible peak has a vertical drag handle. Drag one R-wave peak down 0.5 mV. Save.
6. Switch to Teaching, reselect `tachpm`. Edit is visible. Force-quit, relaunch, reselect. Edit persists (read from `filesDir/pathologies/tachpm.dat`).
7. Pick a pathology with partial leads (`emd`, 6 leads only). V1–V6 are synthesized via `DerivedLeads`; spot-check that V6 looks like a known waveform.
8. Calibration pulse: open Teaching, confirm `CalibrationPulse` renders 10 mm tall × 5 mm wide (1 mV × 200 ms) at default settings.

---

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Add Pathology/manifest/.dat domain + parsers + new EcgSource impls (no callers) | 1 | Purely additive. New tests. |
| 2 | Switch viewer screens (Teaching/Testing/Examination/OSKE) to leadWaveform pipeline | 2 | `RhythmViewModel` reshape, `AppViewModel.dataState`, `setDataZip` flow. ChartCanvas still has default-arg compat. |
| 3 | Unify ChartCanvas/PixelScale: drop sourceAnchored & per-part overrides | 3 | Editor compiles via temporary local helper; cosmetic regression OK. |
| 4 | Rebuild Editor on Points + per-sample drag handles | 4 | New `SampleHandleOverlay`, new `EditableLead`, new `EditorViewModel`, `.dat` writeback. Delete anchor-era files. |
| 5 | Strip legacy types/files; reconcile docs; archive editor-mode plan | 5 | Final cleanup + memory refresh. |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:**
- **PRs:**
- **Deviations from plan:**
- **Follow-ups spawned:**