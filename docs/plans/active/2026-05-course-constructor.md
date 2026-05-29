# Course Constructor — Lecture authoring + bundle pipeline

**Status:** proposed
**Owner:** alexandr.beresov@gmail.com
**Started:** 2026-05-29
**Related issues / PRs:** —
**Related docs:** [2026-05-new-requirements.md](2026-05-new-requirements.md) Phase 5, format spec in chat thread 2026-05-29

## Goal

Add a Course Constructor that lets an author build educational courses
(course → lectures → rich content) inside the app, persist them as a
versioned `Courses.zip` bundle, and re-load them later. Content must
support formatted Markdown text, KaTeX formulas, images, editable tables
for quiz-style exercises, and embedded ECG references that reuse the
existing rendering pipeline. The data pipeline mirrors the established
pathology flow: SAF zip → extract to `filesDir/courses/` → mutate
in-memory → atomic write back → re-zip on export / TCP upload.

## Current state

- No course / lecture domain exists. `TeachingControlPanel.kt:31-33`
  hard-codes a `listOf("Program 1", … "Program 6")` placeholder that the
  dropdown doesn't wire anywhere.
- `TeachingScreen.kt` currently renders pathology rhythms only.
- `OperatingMode` enum (`domain/OperatingModeModel.kt:6-12`) has five
  values, none for course editing.
- The pathology pipeline this plan mirrors lives at
  `data/PathologyRepository.kt`, `data/PathologyZipExtractor.kt`,
  `data/ZipCompressor.kt`, `data/FilePathologySource.kt` (atomic
  `.tmp` + rename writes), and `ui/viewmodels/ConstructorViewModel.kt`.
  Reuse these as templates verbatim.
- The bundle format (Markdown + KaTeX + custom fenced blocks for
  `ecg` and editable `table`) is specified in chat 2026-05-29 and will
  be promoted to `docs/course-format.md` in Phase 1.

## Non-goals

- No cloud sync, no course marketplace, no per-user progress tracking.
- No WYSIWYG Markdown editor. The constructor edits raw `.md` text with
  a live-preview pane; cell-level UI is added only for the editable
  `table` fenced block.
- No multi-author merge / conflict resolution. Courses are single-writer
  on-device.
- No legacy/migration story — this is greenfield content.
- No TCP `upload`-message changes beyond reusing the existing flow to
  push the new zip; protocol stays as documented in `docs/tcp-protocol.md`.

## Plan

### Phase 1 — Domain + read-only pipeline

- Promote the format spec from chat to `docs/course-format.md`
  (bundle layout, `manifest.txt`, front-matter, fenced-block syntax).
- Add `domain/Course.kt` with:
  - `Course`, `CourseEntry`, `Lecture`, `LectureFrontMatter`.
  - `sealed class CourseBlock`: `Markdown(text)`, `Formula(latex, isBlock)`,
    `Image(src, alt, width?)`, `EcgEmbed(pathologyId, lead, caption?)`,
    `EditableTable(id, columns, rows)`.
  - `CourseManifest` (`version`, `entries`); `SUPPORTED_VERSION = "1.0"`.
- Add `domain/CourseParser.kt` — manifest parser (key:value, mirrors
  `PathologyParser`), YAML-lite front-matter parser, fenced-block
  extractor that yields a list of `CourseBlock` for the body. Parser
  returns raw Markdown spans untouched; rendering happens in Phase 2.
- Add `data/CourseSource.kt` interface + `AssetCourseSource` +
  `FileCourseSource` (read-only at this phase). Filesystem layout:
  `filesDir/courses/<course-id>/{course.md, lectures/<id>.<lang>.md,
  assets/*}`.
- Add `data/CourseRepository.kt` (mirrors `PathologyRepository`):
  `manifestFlow`, `courses()`, `readLecture(courseId, lectureId, lang)`,
  `setSource()`.
- Add `data/CourseZipExtractor.kt` — flatten-extract a SAF zip into
  `filesDir/courses/`. UTF-8 only, no charset detection.
- Extend `data/DataSourcePrefs.kt` with `coursesTreeUri` +
  `lastCourseId` (+ per-mode `lastLectureId`, mirroring the rhythm-id
  pattern in `DataSourcePrefs.kt:47-53`).
- Extend `AppViewModel` with `courseRepository`, parallel
  `courseDataState: StateFlow<CourseDataState>`, and
  `setCourseDataFolder(context, uri)` that re-uses `CourseZipExtractor`.
- **Shippable:** no UI change; manifest loads, lectures parse, tests
  pass. Existing pathology flow untouched.

### Phase 2 — Teaching viewer (read-only consumer)

- Add `ui/components/MarkdownRenderer.kt`. Render strategy:
  - Body via a Compose Markdown library (e.g. `compose-markdown`).
  - Each `CourseBlock.Formula` → `KatexFormulaView` Composable wrapping
    a single `WebView` loaded from `file:///android_asset/katex/...`.
  - Each `CourseBlock.Image` → Coil `AsyncImage` reading from
    `filesDir/courses/<course-id>/assets/...`.
  - Each `CourseBlock.EcgEmbed` → reuse `ui/display/Lead.kt` +
    `PathologyRepository.leadWaveform`. No duplicate pipeline.
  - Each `CourseBlock.EditableTable` → read-only Compose table (Phase 3
    adds cell editing).
- Bundle KaTeX assets under `app/src/main/assets/katex/`
  (`katex.min.js`, `katex.min.css`, fonts). Document the version in
  `assets/katex/VERSION`.
- Rewire `TeachingScreen.kt`: replace the `educationPrograms`
  placeholder with a `CourseSelector` drawer (mirrors `RhythmSelector`),
  plus a `LectureSelector` inside the chosen course. Right pane hosts
  `MarkdownRenderer(lecture)`.
- Add `ui/viewmodels/CourseViewerViewModel.kt` (per-mode keyed):
  `selectedCourse`, `selectedLecture`, `lectureContent: StateFlow<Lecture?>`.
  Restores last selection from `DataSourcePrefs`.
- Coil + SVG dependency added if not present
  (`gradle/libs.versions.toml`).
- **Shippable:** Teaching mode renders an authored course bundle
  end-to-end. Constructor work doesn't start yet.

### Phase 3 — Course Constructor mode

- Add `OperatingMode.CourseConstructor` to
  `domain/OperatingModeModel.kt` (+ `R.string.mode_course_constructor`).
  Wire into `MainScreen.kt` `when` block.
- Add `ui/viewmodels/CourseConstructorViewModel.kt`:
  - State: `selectedCourse`, `selectedLecture`,
    `targetLecture: State<Lecture?>`, `dirtyLectures: Set<String>`,
    `isMetadataDirty`, `isSaving` (mirrors `ConstructorViewModel`).
  - Actions: `selectCourse / selectLecture / setMarkdown(text) /
    setFrontMatter(...) / setTableCell(tableId, row, col, value) /
    revertLecture / save / createLecture / deleteLecture / createCourse`.
  - Editable-table answers persist as a sibling `<lecture-id>.<lang>.answers.json`
    so the `.md` stays author-pristine; `FileCourseSource.writeLecture`
    writes both atomically (`.tmp` + rename, same idiom as
    `FilePathologySource`).
- Add `ui/screens/CourseConstructorScreen.kt`:
  - Side drawers: `CourseSelector` + `LectureSelector` (left).
  - Main pane: split view — raw Markdown `TextField` left,
    `MarkdownRenderer` live preview right.
  - Toolbar: Save / Revert / Delete / New lecture / Rename.
- Add `ui/panels/CourseConstructorControlPanel.kt` for the bottom slot
  (mirrors `ConstructorControlPanel`): course/lecture metadata fields,
  insert-block helpers (formula / image / ECG / table).
- Add `ui/components/EditableTable.kt` (Phase 2's table promoted to
  read+write when the embedding context is the constructor; gate via a
  composable param).
- Add `R.string.*` entries in `values/`, `values-ru/`, `values-es/`,
  `values-zh/`.
- **Shippable:** authors can pick a course, edit a lecture's Markdown,
  see live KaTeX/ECG/table preview, and save back to
  `filesDir/courses/`.

### Phase 4 — Export + TCP push

- Add `ZipCompressor.zipCourses(context, sourceDir, destUri)` — copy of
  the existing `zip()` with a different root, or generalize the current
  one to take a configurable root path. Prefer generalization to avoid
  code duplication.
- Add "Export Courses ZIP" button in `SettingsContent`
  (mirrors the existing "Export ZIP" at `SettingsScreen.kt:382-390`).
- Extend `AppViewModel.sendUploadArchive` to optionally upload a second
  bundle (`Courses.zip`) when courses exist. Same `UploadMessage` /
  `AckMessage` flow — no protocol change required.
- **Shippable:** authors can ship their bundle off-device.

### Phase 5 — Polish

- Schema-version negotiation: lecture front matter carries
  `schemaVersion: 1`; loader skips unknown fenced-block types with a
  warning rather than erroring.
- "New course from template" — bundles a starter course in
  `assets/sample-course/` so first-run authors aren't staring at an
  empty list.
- Verify TeachingControlPanel's old `educationPrograms` placeholder is
  fully removed; delete dead code.
- Re-run `update-config` / lint passes; add KaTeX font files to APK
  ignore-on-compress list to keep download size sane.

## Risks & open questions

- **KaTeX rendering perf** — one `WebView` per formula doesn't scale
  past ~10 formulas/page. Plan-of-record: single WebView per lecture
  that renders the whole HTML, with native Compose overlays for ECG /
  editable tables positioned via `evaluateJavascript` bounding-box
  queries. This is the trickiest part; budget a one-day spike at the
  start of Phase 2 before committing.
- **Markdown library choice** — `compose-markdown` vs `Markwon` via
  AndroidView. Decide after Phase 1; both work, `compose-markdown` is
  more idiomatic but younger. Open.
- **YAML front-matter parser** — pulling in `snakeyaml` (≈300 KB)
  for ~5 known keys is overkill. Recommend a hand-rolled mini parser
  in `CourseParser.kt` accepting only `key: value` and `key: [a, b]`.
  Open if more complex YAML proves necessary.
- **Editable-table state on import** — when a `Courses.zip` is
  re-imported, prior `.answers.json` siblings are merged or overwritten?
  Default: overwrite; document clearly in `course-format.md`. Open.
- **TCP upload size** — `Courses.zip` can include large images. The
  existing `UploadMessage.size` is a `Long`, so wire-level OK, but the
  receiver may rate-limit. Open: confirm with the desktop side before
  shipping Phase 4.
- **Mode keying churn** — adding `OperatingMode.CourseConstructor`
  invalidates the per-mode preference keys
  (`${mode}_monitor_speed`, etc.). No migration needed since defaults
  re-populate, but call it out so QA isn't surprised on first launch.

## Verification

- **Phase 1:** unit-style parser tests on a hand-crafted minimal
  `Courses.zip` — manifest parses, one lecture loads, front-matter and
  fenced blocks deserialize to the expected `CourseBlock` shape.
- **Phase 2:** SAF-pick a sample `Courses.zip` → Teaching mode shows
  course → pick lecture → KaTeX renders, image displays, ECG embed
  shows a real waveform from the loaded pathology dataset, table
  renders read-only.
- **Phase 3:** edit Markdown → live preview updates within ~200 ms →
  Save → kill app → re-open → edit persists. Cell-edit a table → save
  → `.answers.json` written → re-open → values restored.
- **Phase 4:** Export Courses ZIP → import on a fresh device → all
  lectures present. TCP-connect with a course bundle loaded → desktop
  receives both `Pathologies.zip` and `Courses.zip` `UploadMessage`s.
- **Phase 5:** "New course from template" creates a working course
  with one sample lecture covering every block type.

## PR breakdown

| # | PR title                                                     | Phase | Notes |
|---|--------------------------------------------------------------|-------|-------|
| 1 | Course domain + parser + read-only source + repository       | 1     | No UI; format spec lands too |
| 2 | KaTeX-WebView spike + Markdown renderer skeleton             | 2a    | Standalone spike PR to de-risk |
| 3 | Teaching viewer wired to courses; CourseSelector drawer       | 2b    | First user-visible change |
| 4 | `OperatingMode.CourseConstructor` + ConstructorViewModel     | 3a    | No editing UI yet |
| 5 | CourseConstructorScreen + control panel + editable tables    | 3b    | Editing end-to-end |
| 6 | Export Courses ZIP + TCP upload integration                  | 4     | Settings button + connect flow |
| 7 | Sample course template + localizations + cleanup             | 5     | Polish |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:** —
- **PRs:** —
- **Deviations from plan:** —
- **Follow-ups spawned:** —
