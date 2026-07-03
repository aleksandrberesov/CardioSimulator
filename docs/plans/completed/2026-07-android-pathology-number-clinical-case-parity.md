# Android parity: pathology `number` field + clinical-case numbering

**Status:** active
**Owner:** (unassigned)
**Started:** 2026-07-02
**Related issues / PRs:** Windows→Android sync. Windows source of truth:
`CardioSimulatorWin/src/CardioSimulator.Core/Domain/{Pathology,PathologyParser}.cs`,
`Data/FilePathologySource.cs`, `App/Controls/RhythmChoosingPanel.xaml.cs`,
`App/ViewModels/ConstructorViewModel.cs`. Builds on completed
`2026-07-android-clinical-case-presentation-mode.md` and
`2026-07-android-clinical-case-title-description-parity.md`.

## Goal

Give every pathology an optional 1-based `number` and surface it in **clinical case
mode**, matching the shipped Windows behavior:

- **every** rhythm row is prefixed with its number — **`{N} <title>`** — in both rhythm
  mode (title = pathology name) and clinical mode (title = case title), for any pathology
  that has a number;
- the clinical dashboard header reads **`Clinical case №N`** (currently just
  "Clinical case").

`number` is a new field in the `.dat` header and manifest, parsed/serialized like the
existing `group` / `clinical_case` fields. Why now: the shared dataset is being
**enumerated and renamed to `ecg{number}.dat`** by the new Windows data tools
(`CardioSimulatorWin/tools/pathology-enumerate/`), so both apps will ingest datasets
carrying `number:` and the `ecg{N}` ids. Android must read the field to display it and
must not choke on it.

## Current state

Android already has the full clinical-case feature; this only adds one field and two
display touch-ups. Relevant files (verified 2026-07-02):

- **`domain/Pathology.kt`** — `PathologyEntry` (`:39`) and `PathologyFile` (`:70`) both
  carry `group`, `description`, `clinicalCase`. No `number`.
- **`domain/PathologyParser.kt`** — manifest read (`:35`) / write (`:64`) and `.dat`
  header read (`:91`) / write (`:122`) all handle `group`/`description`/`clinical_case`.
  Field reads use `header[...]` / `fields[...]`; ints use `?.trim()?.toIntOrNull()`
  (see `baseline` at `:25`).
- **`data/FilePathologySource.kt`** — `writePathology` syncs the manifest entry: change
  test at `:65`, `it.copy(... clinicalCase = file.clinicalCase)` at `:68`, and the
  new-entry `PathologyEntry(...)` at `:78`.
- **`ui/viewmodels/ConstructorViewModel.kt`** — `selectPathology` (`:368`) seeds
  `group` from the manifest entry when the `.dat` lacks it (`:371`). (Note: Android does
  **not** currently seed `clinicalCase` here — out of scope, but seed `number` next to
  `group` so a save can't wipe a manifest-only number.)
- **`ui/panels/RhythmSelector.kt`** — `RhythmItem` builds the row title at `:214`
  (clinical mode uses `getClinicalTitle()` at `:319`). `ClinicalDashboard(clinicalCase,
  language)` renders the header from `R.string.clinical_dashboard_title` at `:268`, and
  is invoked at `:154` with `selectedRhythm.clinicalCase`.
- **Tests:** `app/src/test/java/com/example/cardiosimulator/data/PathologyParserTest.kt`.
- **Strings:** `clinical_dashboard_title` exists in all five locales
  (`values*/strings.xml`). **No new strings needed** — `№N` is appended in code and the
  list prefix is a bare number.

Like `clinicalCase`, `number` for the list/dashboard is read from the **manifest
entries** (`PathologyEntry`), not enriched from `.dat` at load — keep that sourcing.

## Non-goals

- **No port of the Python data tools** (`enumerate_pathologies.py`,
  `update_courses.py`). They are offline, platform-agnostic dev tools run once against
  the shared zips; their output (`ecg{N}.dat`, updated `Courses.zip`) is consumed by
  both apps unchanged. Android needs no code for the *rename* itself — ids stay coupled
  to filenames (`fileName = "$id.dat"`, `PathologyParser.kt:40`) exactly as on Windows.
- No `clinicalCase` seeding fix in the constructor (pre-existing Android divergence).
- No number **editor** UI in the Pathology Constructor (Windows has none either; numbers
  come from the enumerate tool or a manifest edit).
- No change to grouping, search, or sort — the number is display-only; search/sort still
  key off the plain title.

## Plan

### Phase 1 — Model + parser + source (data layer)
- `Pathology.kt`: add `val number: Int? = null` to `PathologyEntry` (`:39`) and
  `PathologyFile` (`:70`).
- `PathologyParser.kt`:
  - manifest parse (`:35` block): `number = fields["number"]?.toIntOrNull()`.
  - manifest serialize (after the `title`/`name`/`group`… appends, `:64` block):
    `if (e.number != null) sb.append(";number:").append(e.number)`.
  - `.dat` parse (`:91` block): `val number = header["number"]?.trim()?.toIntOrNull()`;
    pass into the `PathologyFile(...)` at `:119`.
  - `.dat` serialize (`:122`): after the `title:` line (`:125`), emit
    `if (file.number != null) sb.append("number:").append(file.number).append('\n')`.
- `FilePathologySource.kt` `writePathology`:
  - change test (`:65`): add `|| existing.number != file.number`.
  - `it.copy(...)` (`:68`): add `number = file.number`.
  - new-entry `PathologyEntry(...)` (`:78`): add `number = file.number`.
- Leaves the app shippable: field is optional, absent = null, ignored everywhere it
  isn't yet consumed. Older builds ignore an unknown `number:` header key (block maps
  are tolerant), so datasets stay cross-compatible.

### Phase 2 — Constructor seeding
- `ConstructorViewModel.selectPathology` (`:371`): mirror the `group` seed —
  when `file.number == null`, set `file = file.copy(number = manifestEntry.number)` so a
  round-trip save doesn't drop a manifest-only number.

### Phase 3 — Number display (Compose)
- `RhythmSelector.kt` `RhythmItem` (`:214`): after computing `title` (either branch —
  rhythm mode uses the pathology name, clinical mode uses the case title), prefix the
  number when present, **regardless of mode**:
  `val display = if (rhythm.number != null) "${rhythm.number} $title" else title`
  and render `display`. Keep the filter/sort keying off the plain title (upstream at
  `:95`/`:109`/`:120` — do **not** prefix there).
- `ClinicalDashboard` (`:242`): add a `number: Int?` param; header text becomes
  `stringResource(R.string.clinical_dashboard_title) + (if (number != null) " №$number" else "")`.
  Update the call site (`:154`) to pass `number = selectedRhythm.number`.

### Phase 4 — Tests + docs
- `PathologyParserTest.kt`: add (mirroring the Windows xUnit cases)
  - `.dat` header with `number:7` parses to `number == 7`; absent → `null`;
  - serialize→parse round-trips `number` (assert the `number:` line is emitted);
  - manifest line `...;number:3` parses to `entry.number == 3` and re-serializes with
    `;number:3`.
- Update `docs/data-structure.md` (if it enumerates header/manifest keys) to list
  `number`.

## Risks & open questions

- **`№` glyph.** Appended verbatim in code (matches Windows). Renders fine in Compose;
  no per-locale string. If a locale ever needs a different label, promote to a formatted
  string then — not now.
- **Number source.** Reading from `PathologyEntry` (manifest) means an un-regenerated
  manifest shows no number even if the `.dat` carries one. This matches `clinicalCase`
  and is intended; the enumerate tool writes `number` into **both** the manifest and the
  `.dat`, and `writePathology` keeps them in sync. *Resolved 2026-07-02: manifest-sourced,
  no `.dat` enrichment.*
- **Renamed ids.** `ecg{N}` ids are transparent to Android (id == filename stem). Any
  Course/Test data referencing old ids is repaired by the Windows `update_courses.py`
  against the shared zips — no Android code involved.

## Verification

- `./gradlew :app:testDebugUnitTest` — new parser tests green, existing green.
- `./gradlew :app:assembleDebug` — builds clean.
- Manual: load a dataset produced by `enumerate_pathologies.py` (or hand-add
  `number:` to a `.dat` + `;number:` to its manifest line).
  - **Rhythm mode:** rows read `1 <name>`, `2 <name>`, … (numbered pathologies only).
  - **Clinical mode:** rows read `1 <case title>`, …; selecting a case shows the
    dashboard header `Clinical case №N`.
  - A pathology with no number shows a plain title (no prefix) in either mode.
  - Save the pathology in the Constructor → reopen → number preserved.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Add `number` to pathology model/parser/source | 1–2 | Data layer + constructor seed; no UI yet |
| 2 | Show clinical-case number in list + dashboard | 3–4 | Compose touch-ups + parser tests + doc |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:** —
- **PRs:** —
- **Deviations from plan:** —
- **Follow-ups spawned:** —
