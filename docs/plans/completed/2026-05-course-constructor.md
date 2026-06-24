# Course Constructor — Lecture authoring + bundle pipeline

**Status:** in progress — Phases 1–4 implemented (worktree, uncommitted); Phase 5 + on-device verification pending
**Owner:** alexandr.beresov@gmail.com
**Started:** 2026-05-29
**Revised:** 2026-05-30 — content format switched from Markdown to HTML, rendered in a single per-lecture `WebView` (was: Compose-Markdown + WebView-per-formula + native overlays). See Risks & open questions.
**Related issues / PRs:** —
**Related docs:** [2026-05-new-requirements.md](2026-05-new-requirements.md) Phase 5, [course-format.md](../../course-format.md)

## Goal

Add a Course Constructor that lets an author build educational courses
(course → lectures → rich content) inside the app, persist them as a
versioned `Courses.zip` bundle, and re-load them later. Lecture content is
authored as **HTML** and rendered in a single per-lecture `WebView`, so it
supports formatted text, KaTeX formulas, images, editable tables for
quiz-style exercises, and embedded ECG references. ECG embeds reuse the
existing waveform pipeline by re-emitting it as inline SVG (same data +
projection math, rendered as a static figure). The data pipeline mirrors
the established pathology flow: SAF zip → extract to `filesDir/courses/` →
mutate in-memory → atomic write back → re-zip on export / TCP upload.

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
- The bundle format is specified in [`docs/course-format.md`](../../course-format.md):
  per-lecture HTML body + `---` front matter, KaTeX `$…$` math, `<ecg>`
  custom elements, and HTML `<table>` quiz blocks.
- **Implementation status (2026-05-30):** Phase 1 (domain, parser, source,
  repository, zip extractor, format doc) is already built but
  Markdown-shaped; this revision retargets it to HTML. The renderer
  (Phase 2) and constructor (Phase 3) are not built —
  `CourseConstructorScreen.kt` is still a stub — so the pivot lands before
  any rendering code is written.

## Non-goals

- No cloud sync, no course marketplace, no per-user progress tracking.
- No WYSIWYG editor. The constructor edits raw `.html` text with a live
  `WebView` preview pane plus insert-block toolbar buttons; there is no
  rich-text / `contentEditable` surface. Editable quiz tables are plain
  HTML `<table>`s with `<input>` cells — no custom cell-editing UI.
- No multi-author merge / conflict resolution. Courses are single-writer
  on-device.
- No legacy/migration story — this is greenfield content.
- No TCP `upload`-message changes beyond reusing the existing flow to
  push the new zip; protocol stays as documented in `docs/tcp-protocol.md`.

## Plan

### Phase 1 — Domain + read-only pipeline

> **Status: largely built (Markdown-shaped); this phase retargets it to HTML.**

- Update the format spec in `docs/course-format.md` to the HTML format
  (bundle layout, `manifest.txt`, front matter, `<ecg>` custom element,
  HTML `<table>` quiz blocks). **Done.**
- `domain/Course.kt`:
  - Keep `Course`, `CourseEntry`, `Lecture`, `LectureEntry`,
    `LectureFrontMatter`, `CourseManifest` (`SUPPORTED_VERSION = "1.0"`).
  - Rename `Lecture.rawMarkdown` → `rawHtml`.
  - **Remove** the `CourseBlock` sealed class (`Markdown` / `EcgEmbed` /
    `EditableTable`). With one WebView rendering the whole document, the
    body is not decomposed into blocks; ECG/table handling moves into the
    renderer (Phase 2), not the data model.
- `domain/CourseParser.kt`:
  - Keep the `manifest.txt` and `course.txt` parsers/serializers verbatim
    (key:value header + semicolon rows — unchanged).
  - `parseLecture` shrinks to: split the `---` front matter from the HTML
    body and return `rawHtml` verbatim. **Delete** the fenced-block
    machinery (`extractBlocks`, `parseEcgFence`, `parseTableFence`,
    `FENCE_LINE`).
- `data/CourseSource.kt` + `FileCourseSource`: change the lecture file
  extension `.md` → `.html`; everything else (atomic `.tmp` + rename
  writes, `.answers.json` siblings) is unchanged.
- `data/CourseRepository.kt`, `data/CourseZipExtractor.kt`,
  `data/DataSourcePrefs.kt`, and the `AppViewModel` wiring
  (`courseRepository`, `courseDataState`, `setCourseDataFolder`,
  `coursesTreeUri` / `lastCourseId`): unchanged from what is already built.
- **Shippable:** no UI change; manifest loads, lectures parse to
  front-matter + `rawHtml`, tests pass. Existing pathology flow untouched.

### Phase 2 — Teaching viewer (single-WebView renderer)

- Add `ui/components/LectureWebView.kt` — an `AndroidView` wrapping one
  `WebView` that renders the whole lecture as a single HTML document:
  - Serve all content from one virtual origin via `WebViewAssetLoader`
    (androidx.webkit): `/assets/katex/*` → APK assets,
    `/course/*` → `filesDir/courses/<course-id>/`. One origin lets KaTeX
    fonts and `<img src="assets/…">` load without `file://` CORS issues.
  - Assemble the document at load: `<head>` links `katex.min.css` + an
    injected `:root{}` CSS block carrying the Compose theme
    colors/typography/density (so dark mode + sizing match the app);
    `<body>` = the lecture HTML with `<ecg>` elements already rewritten to
    inline `<svg>`; a trailing `<script>` runs `renderMathInElement` once
    over the whole body and (constructor only) wires `<input>` listeners.
  - KaTeX `$…$` (inline) / `$$…$$` (block) auto-rendered in a single DOM
    pass — no per-formula WebView.
  - Restrict to local content only (no remote loads); JS enabled.
- Add `data/EcgSvgRenderer.kt` — reuses `PathologyRepository.leadWaveform`
  and ports the `projectPath` math from `ui/components/ChartCanvas.kt`
  (`x = i·pxPerSample`, `y = baselineY − sample·pxPerAdcCount`) to an SVG
  `<path>` + a pink `<pattern>` grid. Static figure at a fixed `pxPerMm`.
  `<ecg pathology lead caption>` → `<figure><svg/>…</figure>`. No native
  overlay, no second pipeline.
- Bundle KaTeX assets under `app/src/main/assets/katex/`
  (`katex.min.js`, `katex.min.css`, `auto-render.min.js`, fonts).
  Document the version in `assets/katex/VERSION`.
- Images: native HTML `<img>`, resolved through the asset loader (Coil is
  not needed for in-WebView images).
- Editable `<table>`s render read-only in the viewer (Phase 3 wires the
  write-back bridge).
- Rewire `TeachingScreen.kt`: replace the `educationPrograms`
  placeholder with a `CourseSelector` drawer (mirrors `RhythmSelector`),
  plus a `LectureSelector` inside the chosen course. Right pane hosts
  `LectureWebView(lecture)`.
- Add `ui/viewmodels/CourseViewerViewModel.kt` (per-mode keyed):
  `selectedCourse`, `selectedLecture`, `lectureContent: StateFlow<Lecture?>`.
  Restores last selection from `DataSourcePrefs`.
- Add `androidx.webkit` to `gradle/libs.versions.toml`.
- **Shippable:** Teaching mode renders an authored HTML course bundle
  end-to-end — text, KaTeX, images, inline-SVG ECG, read-only tables.

### Phase 3 — Course Constructor mode

- Add `OperatingMode.CourseConstructor` to
  `domain/OperatingModeModel.kt` (+ `R.string.mode_course_constructor`).
  Wire into `MainScreen.kt` `when` block.
- Flesh out `ui/viewmodels/CourseConstructorViewModel.kt` (currently a
  `selectCourse`-only stub):
  - State: `selectedCourse`, `selectedLecture`,
    `targetLecture: State<Lecture?>`, `dirtyLectures: Set<String>`,
    `isMetadataDirty`, `isSaving` (mirrors `ConstructorViewModel`).
  - Actions: `selectCourse / selectLecture / setHtml(text) /
    setFrontMatter(...) / setTableCell(quizId, row, col, value) /
    revertLecture / save / createLecture / deleteLecture / createCourse`.
  - Editable-table answers persist as a sibling
    `<lecture-id>.<lang>.answers.json` (keys `"<row>,<col>"`) so the
    `.html` stays author-pristine; `FileCourseSource` already writes both
    atomically (`.tmp` + rename, same idiom as `FilePathologySource`).
  - Optionally scan the body for `<ecg pathology="…">` occurrences to sync
    the course's `pathologies` list (the role the removed
    `CourseBlock.EcgEmbed` would have served).
- Replace the `ui/screens/CourseConstructorScreen.kt` stub:
  - Side drawers: `CourseSelector` + `LectureSelector` (left).
  - Main pane: split view — raw HTML `TextField` left, `LectureWebView`
    live preview right, reloaded on a ~200 ms debounce.
  - Toolbar: Save / Revert / Delete / New lecture / Rename.
- Add `ui/panels/CourseConstructorControlPanel.kt` for the bottom slot
  (mirrors `ConstructorControlPanel`): course/lecture metadata fields +
  insert-block helpers that inject HTML snippets (heading / formula
  `$$ $$` / `<img>` / `<ecg>` / `<table data-editable>`).
- Editable-table write-back: the preview `WebView` exposes a
  `@JavascriptInterface onCell(quizId, row, col, value)` that posts to a
  flow on the main thread → `CourseConstructorViewModel.setTableCell` →
  `.answers.json`. On load, saved answers are injected back into the
  `<input>`s. No Compose table component.
- Add `R.string.*` entries in `values/`, `values-ru/`, `values-es/`,
  `values-zh/`.
- **Shippable:** authors can pick a course, edit a lecture's HTML, see
  live KaTeX/ECG/table preview, and save back to `filesDir/courses/`.

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
  `schemaVersion`; the renderer ignores unknown custom elements (e.g. a
  future `<quiz>`), leaving them inert in the DOM rather than erroring.
- **Done.** "New course from template" — a starter course is bundled in
  `assets/sample-course/`, seeded into `filesDir/courses/` by
  `SampleCourseSeeder` via a "Use sample course" button on the course-data
  screen (`AppViewModel.loadSampleCourses`); persists across restarts.
- Verify TeachingControlPanel's old `educationPrograms` placeholder is
  fully removed; delete dead code.
- Re-run `update-config` / lint passes; add KaTeX font files to APK
  ignore-on-compress list to keep download size sane.

## Risks & open questions

- **HTML authoring ergonomics** — authors hand-write HTML (`<strong>`
  not `**`). Decision (2026-05-30): accept raw HTML + live preview,
  softened by insert-block toolbar buttons that inject snippets. A future
  MD→HTML authoring layer can sit on top without changing the stored
  format. Resolved.
- **Single-WebView KaTeX perf** — rendering the whole lecture in one
  WebView with one `renderMathInElement` pass is the standard, well-scaled
  KaTeX usage (this replaces the old "one WebView per formula" risk
  entirely). Low risk, but can't be measured in-session (Gradle can't run
  here) — confirm on-device on a formula-heavy lecture during Phase 2.
- **WebView ↔ Compose theming** — match Material colors, dark mode, and
  density by injecting a `:root{}` CSS-variable block from the Compose
  theme. Real but contained; verify dark mode early.
- **JS bridge threading/security** — `@JavascriptInterface` runs on a
  binder thread, so cell edits must hop to the main thread (flow / `post`).
  Restrict the WebView to local content (asset-loader origin only); never
  enable remote loads. Standard hardening.
- **`androidx.webkit` dependency** — needed for `WebViewAssetLoader` (one
  virtual origin for KaTeX fonts + course images). Small, official.
- **ECG is a static SVG** — no scroll/sweep animation inside a lecture.
  Correct for a textbook figure; a "live" beating ECG inside a lecture
  would need the animation ported to JS/canvas. Open only if requested.
- **YAML front-matter parser** — unchanged from the built version: a
  hand-rolled `key: value` / `key: [a, b]` mini-parser in `CourseParser`,
  not `snakeyaml`. Open if richer YAML proves necessary.
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
  `Courses.zip` — manifest parses, one lecture loads, front matter splits
  cleanly from the HTML body (`rawHtml` round-trips byte-for-byte).
- **Phase 2:** SAF-pick a sample `Courses.zip` → Teaching mode shows
  course → pick lecture → KaTeX renders, image displays, the `<ecg>`
  element shows a real inline-SVG waveform from the loaded pathology
  dataset, table renders read-only. Spot-check a formula-heavy lecture
  for render latency.
- **Phase 3:** edit HTML → live WebView preview updates within ~200 ms →
  Save → kill app → re-open → edit persists. Cell-edit a table → save
  → `.answers.json` written → re-open → values restored in the `<input>`s.
- **Phase 4:** Export Courses ZIP → import on a fresh device → all
  lectures present. TCP-connect with a course bundle loaded → desktop
  receives both `Pathologies.zip` and `Courses.zip` `UploadMessage`s.
- **Phase 5:** "New course from template" creates a working course
  with one sample lecture covering every block type.

## PR breakdown

| # | PR title                                                     | Phase | Notes |
|---|--------------------------------------------------------------|-------|-------|
| 1 | Retarget course domain + parser + source to HTML             | 1     | `rawHtml`; drop `CourseBlock` + fenced parser; format-doc rewrite |
| 2 | `LectureWebView` + `EcgSvgRenderer` + KaTeX assets           | 2a    | The real renderer (no throwaway spike); `androidx.webkit` dep |
| 3 | Teaching viewer wired to courses; CourseSelector drawer       | 2b    | First user-visible change |
| 4 | `OperatingMode.CourseConstructor` + ConstructorViewModel     | 3a    | No editing UI yet |
| 5 | CourseConstructorScreen: HTML editor + live preview + table bridge | 3b | Editing end-to-end; JS-bridge cell write-back |
| 6 | Export Courses ZIP (TCP upload intentionally omitted)        | 4     | Settings export button; courses TCP push dropped per owner |
| 7 | Sample course template + localizations + cleanup             | 5     | Polish |

---

## Outcome

*In progress — Phases 1–4 implemented in worktree `agitated-boyd-7221d1` (uncommitted); Phase 5 and on-device verification pending. Updated 2026-05-30.*

- **Result:**
  - **Phase 1 (retarget):** the pre-existing Markdown-shaped domain/parser/source
    were retargeted to HTML — `Lecture.rawHtml`, `CourseBlock` + the fenced-block
    parser removed, `FileCourseSource` lecture files `.md` → `.html`, dead
    `parseKeyValueLines` dropped. `course-format.md` rewritten to the HTML spec.
  - **Phase 2 (renderer + viewer):** `data/EcgSvgRenderer.kt` (pure, unit-tested)
    re-emits the monitor's waveform projection as static inline SVG;
    `ui/components/LectureWebView.kt` renders a whole lecture in one WebView
    (single-origin `WebViewAssetLoader`, theme→CSS vars, one-pass KaTeX,
    `<ecg>`→SVG off the main thread). `androidx.webkit` added. The viewer is an
    additive overlay in Teaching mode (a corner button opens a course/lecture rail
    + `LectureWebView`); `CourseViewerViewModel` restores the last selection.
  - **Phase 3 (constructor):** `CourseConstructorViewModel` + a rebuilt
    `CourseConstructorScreen` give edit → debounced live preview → save/revert over
    the raw lecture source; `CourseConstructorControlPanel` adds create/rename/delete
    lecture, new course, and insert-block helpers. Editable-table cells round-trip
    to `.answers.json` (JS bridge in, `evaluateJavascript` injection out).
  - **Phase 4 (export):** the Settings "Export Courses ZIP" button
    (`exportCoursesZip`) ships the bundle off-device. Courses TCP push was
    **intentionally omitted** per the owner; the manual upload button was removed.
  - KaTeX binaries are scaffolded (`assets/katex/` + README) but **not bundled** —
    they must be dropped in before math renders (loader degrades gracefully).
- **PRs:** none merged; all work is uncommitted in the session worktree. Maps to
  plan PRs 1–6 (PR 7 / Phase 5 not started).
- **Deviations from plan:**
  - **Viewer placement** — rather than rewriting `TeachingScreen` (which *is* the
    live monitor) or adding a `CourseViewer` operating mode, the viewer is a
    non-destructive overlay inside Teaching. Chosen to preserve the monitor and to
    avoid exhaustive-`when` churn from a new `OperatingMode` (couldn't compile-check
    here). The AskUserQuestion to confirm this failed (tool error), so the safest
    option was taken.
  - **Phase 4** — export only; no courses TCP push (owner decision).
  - **Table answers** — injected post-load via `evaluateJavascript` (not embedded in
    the HTML) so editing a cell never triggers a reload.
  - **Insert helpers** append snippets to the end of the draft (String-bound editor,
    not cursor-aware).
  - The single-WebView design replaced the planned WebView-per-formula + native
    overlays (the original top risk); `compose-markdown` was never needed.
- **Follow-ups spawned:** none (no tasks spun off). Outstanding work to track:
  - Bundle the real KaTeX assets; run the on-device verification checklist
    (dark-mode CSS, formula-heavy latency, real `<ecg>` render, save round-trip).
  - Phase 5 (sample-course template **done**): schema-version negotiation, lint /
    APK no-compress pass; delete dead `uploadCourses()` + `course_data_source_upload`;
    remove the old `educationPrograms` placeholder.
  - Editor polish: cursor-aware insert; rename/delete across sibling-language files;
    `createCourse` when no manifest exists yet.
