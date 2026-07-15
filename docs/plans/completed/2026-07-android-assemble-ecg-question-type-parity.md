# «Собери ЭКГ» (assemble-the-ECG) test question type — Android parity

**Status:** completed
**Owner:** (unassigned)
**Started:** 2026-07-07 · **Reworked:** 2026-07-07 (simplified to split-and-reorder — see history note)
**Completed:** 2026-07-08
**Related issues / PRs:** Windows implementation (source of truth) in `CardioSimulatorWin`.

## Goal

Port the net-new **«Собери ЭКГ»** question type from the Windows build to Android, so the Testing screen and the Test Constructor gain it at parity.

The learner is shown an assignment and an empty ECG **tape** (a gray dashed isoline) divided into **N ordered slots**, plus a palette of the trace's parts in **shuffled** order. A single rhythm's lead was cut into N equal contiguous parts; the learner drags (or taps) the parts back into their **original order** along the tape to rebuild one continuous strip. A correct ordering reads as one smooth line; a wrong one shows visible steps between parts. Graded **all-or-nothing** (every slot i must hold the part whose original index == i).

Why this shape: an earlier design sliced the beat into semantic **P/QRS/T** blocks via fiducial detection, but many rhythms have no cleanly detectable P/QRS/T, so authoring failed on them. The reworked design just splits the trace into equal parts and asks the student to reorder — **no wave detection, so it works for every rhythm**. Port this reworked design; do **not** port the old P/QRS/T + distractor model.

Why now: the feature is built, tested (245 Core tests green) and shipping on Windows; Android's Testing screen otherwise stays behind.

## Current state

### Windows source (faithful reference — read these)
- Domain model: `src/CardioSimulator.Core/Domain/EcgAssembly.cs` — `EcgAssemblyPart(Samples: IReadOnlyList<int>)` (baseline-zeroed, 0 = isoline); `EcgAssembly(SampleRateHz, Parts, SourcePathologyId?, SliceLead = Lead.II)` with `PartCount` / `IsComplete` (≥2 parts). No `EcgBlock`, no distractors.
- Slicer (pure): `src/CardioSimulator.Core/Domain/EcgAssemblySlicer.cs` — `Split(samples, baseline, partCount, windowSamples=0)`: slices the leading window (≤0 = whole lead) into `partCount` **equal** contiguous parts, each value − baseline; trailing remainder dropped so all parts share one length; `MinParts=2`, `MaxParts=8`; returns null if too short.
- Runtime attempt (pure): `src/CardioSimulator.Core/Domain/AssemblyAttempt.cs` — `AssemblyPaletteItem(CorrectIndex, Samples, Key)`; `AssemblyAttempt(spec, seed)` builds a **seeded-shuffled** palette (Fisher–Yates on `Random(seed)`) and N empty slots; `Place(slot, item)` (moves the item out of any old slot; bumps any current occupant back to the pool), `Clear(slot)`, `Available`, `PlacedAt(slot)`, `SlotOf(item)`, `ItemByKey(key)`, `IsComplete`, `AllCorrect` (slot i holds `CorrectIndex == i`).
- Authoring slicer: `src/CardioSimulator.App/Data/EcgAssemblyBuilder.cs` — `Build(repository, sourceId, lead, partCount, fs)`: reads the lead's raw samples (or the baseline-subtracted `LeadWaveform` for a derived lead), caps the window to **5 s**, and calls `EcgAssemblySlicer.Split`. **No BioSPPy.** Returns null when the rhythm is missing or too short.
- Question model: `src/CardioSimulator.Core/Domain/Test.cs` — `enum QuestionKind {SingleChoice, AssembleEcg}`; `TestQuestion` has `EcgAssembly? Assemble = null`, `IsAssembly`, `Kind`.
- Testing runtime VM: `src/CardioSimulator.App/ViewModels/TestViewModel.cs` — `AssemblyAttempt? Assembly` (rebuilt per question, `seed = Index + 1`); `AssemblyComplete`; `AnswerCorrect` branches on `IsAssembly` (→ `Assembly.AllCorrect`); `NotifyAssemblyChanged()`; `SubmitAssembly()` (reveal + all-or-nothing score).
- Workspace control: `src/CardioSimulator.App/Controls/EcgAssemblyControl.cs` — one continuous tape (single dashed isoline, faint dividers) of N ordered slots + a shuffled palette of the still-unplaced parts; **any part drags into any slot** (payload = part `Key`) with a **tap-to-place** fallback; all parts share one amplitude scale and are drawn **full slot width (no pad)** so a correct order joins seamlessly; long parts strided down; on reveal each slot tints green/red and a wrong slot shows the correct part faintly.
- Testing panel: `src/CardioSimulator.App/Controls/TestQuestionPanel.cs` — for assembly shows the assignment + a **Check** button (enabled when `AssemblyComplete`) → `SubmitAssembly()`, then a verdict comment.
- Testing screen: `src/CardioSimulator.App/Screens/TestingScreen.cs` — hosts the workspace in the left pane; `PlacementChanged → NotifyAssemblyChanged`; shows it (hiding monitor/image) when `IsAssembly`.
- Constructor VM: `src/CardioSimulator.App/ViewModels/TestConstructorViewModel.cs` — `EditQuestion` has `IsAssembly`, `AssembleSourceId`, `AssembleLead = II`, `AssemblePartCount` (default `DefaultPartCount = 4`), built `Assembly`; `From(...)` hydrates them; `Compile(...)` → assembly question (empty options, `Assemble` set) when `IsAssembly`.
- Constructor screen: `src/CardioSimulator.App/Screens/TestConstructorScreen.cs` — 4th mode "Собери ЭКГ"; `BuildAssembleEditor(...)` = **source rhythm** picker + **lead** picker + **parts** selector (3–6) + a status line; `RebuildAssembly(q)` re-slices immediately on any change; `EnsureAssembliesBuiltAsync(...)` re-slices unbuilt questions before every save (test / bank / to-bank) and blocks the save if a rhythm can't be sliced. No distractor UI, no Build button.
- Strings: `src/CardioSimulator.App/Localization/AppStrings.cs` — `assemble_*` (runtime) + `assemble_ctor_*` (authoring). EN + RU authored; ZH/ES/HI fall back to EN.
- Tests: `tests/CardioSimulator.Core.Tests/EcgAssemblyTests.cs` — Split (equal parts, remainder drop, window, concatenation), AssemblyAttempt (reorder grading, move/bump/clear, shuffle determinism), JSON round-trip.

### ⚠️ Performance note (still applies)
This user's `pathologies/manifest.txt` holds **~45,000 entries**. The Windows constructor's rhythm pickers use the app's shared **grouped/searchable `RhythmPickerButton`** (virtualized), not an eager list. On Android the source-rhythm picker in the assembly editor must likewise use the shared **`RhythmSelector`** (lazy `LazyColumn` + filter), never a non-lazy list or a per-row catalog scan. (This is far less pressure than the old design — one picker, not three.)

### Android target
- `app/.../domain/Test.kt` — `TestQuestion`/`Test`/`QuestionStimulus` (kotlinx.serialization). Add the assembly model + `assemble` field here (or a new `EcgAssembly.kt`).
- `app/.../data/PathologyRepository.kt` — `readPathology(id)`, `leadWaveform(id, lead)`, `manifest()?.baseline`, `PathologyFile.leads[lead].samples`. Reuse.
- `app/.../data/TestData.kt` — `TestJson` (kotlinx `Json`). Serialization is automatic once the model is `@Serializable`.
- `app/.../ui/viewmodels/TestViewModel.kt`, `TestConstructorViewModel.kt`; `app/.../ui/screens/TestingScreen.kt`, `TestComponents.kt`, `TestConstructorScreen.kt`; `app/.../ui/panels/RhythmSelector*` (shared picker).
- `app/src/main/res/values{,-ru,-es,-hi,-zh}/strings.xml`.
- **Not needed:** `signals/biosppy/Landmarks.kt` / `QrsSegmenters.kt` and `domain/SignificantPoint.kt` — the reworked design uses no detection.

## Non-goals
- No change to single-choice questions, the bank, exam/OSCE, or the monitor.
- No wave/fiducial detection, no distractor rhythms, no P/QRS/T semantics (all removed in the rework).
- Not redesigning the 45k-catalog pipeline; only keep the source picker lazy + filterable (reuse `RhythmSelector`).
- Cross-platform *shuffle order* need not match Windows (`.NET Random` ≠ `kotlin.random.Random`); only per-attempt stability matters. The stored **parts/JSON** must round-trip identically.

## Plan

### Phase 1 — Domain model + JSON (pure, testable)
- Add `EcgAssemblyPart` + `EcgAssembly` as `@Serializable` Kotlin (new `domain/EcgAssembly.kt`). **Match Windows JSON names exactly** so files round-trip: `samples: List<Int>`, `sampleRateHz`, `parts`, `sourcePathologyId`, `sliceLead` (a `Lead`, serialized by name e.g. `"II"`). Add `assemble: EcgAssembly? = null` to `TestQuestion` (keep default-null so it's omitted when absent) + `isAssembly` / `kind`.
- Port `EcgAssemblySlicer.split(...)` and `AssemblyAttempt` (+ `AssemblyPaletteItem`) as pure Kotlin. Shuffle with `kotlin.random.Random(seed)`.
- Unit tests mirroring `EcgAssemblyTests.kt`: equal-parts + remainder-drop + window + concatenation; reorder all-or-nothing; move/bump/clear; JSON round-trip of a `TestQuestion` with `assemble` (parse a Windows-written sample to lock cross-platform parity).

### Phase 2 — Authoring slicer
- Port `EcgAssemblyBuilder.build(repository, sourceId, lead, partCount, fs)` (`data/EcgAssemblyBuilder.kt`): read `leads[lead].samples` (raw, baseline from `manifest().baseline`) or `leadWaveform` (baseline 0) for a derived lead, cap the window to 5 s, `split`, wrap into `EcgAssemblyPart`s. Trivial — no signal processing. `fs` from the monitor calibration `sampleRateHz`.

### Phase 3 — Testing runtime (Compose)
- Extend `TestViewModel`: hold the current `AssemblyAttempt` (rebuilt per question, `seed = index + 1`), expose `assemblyComplete`, branch `answerCorrect` on `isAssembly`, add `submitAssembly()` (reveal + score iff `allCorrect`) and a `notifyAssemblyChanged()` equivalent.
- Build the workspace composable (port of `EcgAssemblyControl`), **centered**: a **labels-free single tape** — a paper `Box`/`Card` with **one dashed isoline drawn across its full width** (`Canvas`) and faint dividers marking N drop **slots** (equal `Row` weights); below it, a `Row`/wrap of the **shuffled unplaced parts**. Draw each part with `Canvas`/`Path` from baseline-zeroed samples, **all parts sharing one amplitude scale** and drawn **full width (no pad)** so a correct order joins into one continuous line; stride long parts. Reveal: tint each slot green/red and faintly overlay the part that belongs there where wrong. Windows sizing for reference: slot width ≤132 (`min(132, 640/N)`), tape height 148, tile height 88.
  - **Interaction:** Compose has no WinUI-style drag payload. Primary = **tap part → tap slot** (tap a filled slot to return its part to the pool) — mirrors the Win fallback and is robust in a lazy row. Optionally add real drag via Compose `dragAndDropSource`/`dragAndDropTarget` (Compose 1.6+) carrying the part `key`; tap-to-place must work regardless.
- Extend `TestComponents`/`TestingScreen`: when `isAssembly`, render the workspace in place of the monitor/image and show a **Check** button (enabled at `assemblyComplete`) → `submitAssembly()`, then a verdict. Reuse the countdown / Next-Finish flow.

### Phase 4 — Test Constructor authoring (Compose)
- Extend the edit-question state: `isAssembly`, `assembleSourceId`, `assembleLead = II`, `assemblePartCount` (default 4), built `assembly`; hydrate on load; compile to a `TestQuestion` with empty options + `assemble` when `isAssembly`.
- Add a 4th question-type option "Собери ЭКГ". When selected: hide options; show a **source rhythm** picker (reuse the shared **`RhythmSelector`** — lazy + filterable), a **lead** picker (default II), and a **parts** selector (3–6). Re-slice via `EcgAssemblyBuilder.build(...)` immediately on any change; show a status ("Ready · N parts" / "too short"). Ensure the build runs (and blocks the save on failure) before persisting — port `EnsureAssembliesBuiltAsync`.

### Phase 5 — Strings + polish
- Add the `assemble_*` + `assemble_ctor_*` keys to `values/strings.xml` (EN) and `values-ru/strings.xml` (RU). ES/HI/ZH may fall back to `values/` or be translated. **Placeholder gotcha:** `assemble_ctor_built_format` = "Ready · {0} parts" → Android `%1$d`. Key set (final): `assemble_title, assemble_hint, assemble_reveal_hint, assemble_pieces, assemble_panel_hint, assemble_check, assemble_verdict_correct, assemble_verdict_wrong, test_ctor_stimulus_assemble, assemble_ctor_hint, assemble_ctor_source, assemble_ctor_lead, assemble_ctor_parts, assemble_ctor_built_format, assemble_ctor_build_failed, assemble_ctor_none`.
- Manual smoke test end-to-end; run unit tests.

## Risks & open questions
- **Compose drag-and-drop maturity.** Ship tap-to-place first; add drag if the BOM supports it. Customer wording is "перемещение/dragging" — tap-to-place still moves a part onto the tape.
- **Continuity rendering.** Parts must be drawn edge-to-edge with a shared amplitude scale, or a correct order won't look continuous — the whole point of the puzzle. Verify on a real rhythm.
- **45k-catalog source picker.** Reuse the lazy/filterable `RhythmSelector`. *Open:* is the 45k dataset intentional? (Win `.bak` manifest had 61.) Flag to the user; not blocking.
- **JSON parity.** Field/enum names must match Windows exactly. Covered by the Phase 1 round-trip test.

## Verification
- Phase 1/2: unit tests green (split, reorder grading, move/bump/clear, JSON round-trip incl. a Windows-authored sample).
- Phase 3: author a test with one assembly question; take it — parts render shuffled, tap-to-place fills slots, a correct order looks continuous, **Check** enables only when full, correct → ✓ + score, wrong → ✗ with the right parts shown faintly.
- Phase 4: open the assembly editor against the full catalog — **no jank**; source picker filters; changing parts/lead re-slices; save + reload preserves the question.
- Phase 5: labels correct in RU and EN; `%1$d` renders.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Assembly model + slicer + attempt + JSON | 1 | Pure Kotlin + tests; no BioSPPy |
| 2 | EcgAssemblyBuilder (equal-slice) | 2 | Window cap 5 s |
| 3 | Testing runtime: VM + workspace composable | 3 | Continuous tape; tap-to-place; Check/grade |
| 4 | Test Constructor authoring (source/lead/parts) | 4 | Reuse RhythmSelector; ensure-on-save |
| 5 | Strings (EN/RU) + smoke test | 5 | `%1$d` placeholder |

---

## History

- **2026-07-07 (rework):** original plan targeted a P/QRS/T block model (BioSPPy fiducial slicing + author-chosen distractor rhythms). Dropped because rhythms without detectable P/QRS/T couldn't be authored. Replaced with this simpler **split-into-N-parts / reorder** design — no detection, no distractors, works for any rhythm.

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:**
- **PRs:**
- **Deviations from plan:**
- **Follow-ups spawned:**
