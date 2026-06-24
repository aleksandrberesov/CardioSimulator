# Plan: Further development (constructor + courses) — from the 11–13 Jun 2026 discussion

**Created:** 2026-06-14
**Status:** Decisions made (2026-06-14) — D1=Option C (hybrid), D2=fix-first, D3=live 12-lead.
Executing WS1 next.
**Source:** WhatsApp discussion between Николай (clinical/product) and Aleksandr (dev),
11–13 Jun 2026, plus a Python ECG-generation script and a DeepSeek dialog Николай shared.

This plan turns that free-form discussion into concrete, code-grounded workstreams. It is written
against the current WinUI 3 port (`src/CardioSimulator.App` + `src/CardioSimulator.Core`) and the
Android source of truth at `../CardioSimulator/app/src/main/java/com/example/cardiosimulator/`.

---

## What the discussion actually asked for

Sorted by urgency / how concrete the ask was:

1. **(13 Jun, Николай)** Course constructor — add an **"All in one"** insert mode: a plain field to
   paste a *complete* HTML page (copy-paste), so AI-reworked ECG textbooks drop straight into the
   simulator. *"У тебя так до этого было."*
2. **(12 Jun, Николай)** Two editor bugs/questions blocking him from authoring ECGs:
   - **New ECG names don't save.**
   - **How do you change points manually?**
3. **(12 Jun, Aleksandr → Николай agreed)** Re-think the ECG constructor around **a library of ECG
   artifacts/elements** (P, QRS, T, ST, …) inserted with one button each, then resized by
   **width/height** controls — *"логика врача, а не инженера-конструктора."* Origin: Николай's
   "create new" idea (generate 500 flat points/lead, then drag) + the Python `one_cycle` generator.
4. **(12 Jun, Николай)** A **full 12-lead ECG screen** — "12 отведений, с нижней панелью",
   open question whether it's all-leads-at-once and/or start/stop.
5. **(12 Jun, Николай)** A batch of **small course edits**, sent by email with screenshots
   (content not in the chat — blocked on that email).

---

## Current state (verified in code)

- **Pathology save (bug #2a).** `ConstructorViewModel.Rename` sets `TargetFile.TitleEn/NameRu` +
  `IsMetadataDirty`; `SaveAsync` → `PathologyRepository.WritePathology` → `FilePathologySource.WritePathology`.
  That method **writes only `<id>.dat`** (which carries `title:`/`name:` in its header) and never
  touches `manifest.txt`. The rhythm lists render from `manifest.Entries` (`Pathologies()`), so after
  `LoadManifest()` re-reads the *stale* manifest the rename is lost. **Android's `writePathology`
  updates the manifest entry (title/name, or appends a new row) before rewriting `manifest.txt`** —
  the Windows port dropped that half. New-pathology *creation* already updates the manifest
  (`CreatePathology` appends an entry), which is why creation "works" but rename doesn't.
  → File: `src/CardioSimulator.Core/Data/FilePathologySource.cs:73`.
- **Manual point editing (bug #2b — actually a discoverability gap).** It already exists: the
  constructor bottom bar (`ConstructorControlPanel`, wired at `MainScreen.xaml.cs:138`) has
  ◀ time ▶ (select sample / type ms), ▼ value ▲ (nudge ADC through the smoothing kernel), a value
  cell that opens an exact-ADC dialog (`ConstructorViewModel.SetSample`), a smoothing algo/width
  dialog, speed, and start/stop. Plus canvas tap-to-select and the **Trace** tool mode (freehand).
  So the feature is there; Николай didn't find it.
- **Course constructor.** `CourseConstructorScreen` has two edit modes today: **Visual** (block editor,
  `HtmlBlockEditor`) and **Source** (`_htmlEditor`, a raw-HTML textbox that edits the lecture *body*).
  Body HTML is wrapped by `LectureWebView.BuildDocument` into a fixed document (KaTeX CSS/JS, theme
  CSS, `<base href>`, `<ecg>`→SVG substitution, quiz `<input>` bridge). Lectures are stored as
  `<id>.<lang>.html` = `---` front-matter + verbatim body (`Lecture.RawHtml`). There is **no**
  full-page paste mode; pasting a whole `<!DOCTYPE html>…` into Source today nests html/head inside
  the wrapper `<body>` (fragile). Android has no such mode either — this is net-new.
- **ECG model.** One `<id>.dat` per pathology, 12 leads (derived leads read-only), raw ADC samples
  baseline-centered on 1024, optional `markers:` (significant points). Editing is per-sample through a
  weighted kernel (`AdjustSample`/`SetSample`/`TraceSamples`), per-lead undo/redo. No notion of
  "elements/segments" — the array is flat. `CreatePathology(…, 500, baseline)` already makes a flat
  500-sample/lead blank (Николай's "500 points" idea is half-built).

---

## Workstream 1 — Fix "new ECG names don't save"  (P0) — ✅ DONE 2026-06-14

**Implemented:** `FilePathologySource.WritePathology` now updates `manifest.txt` (rewrites the matching
entry's title/name, or appends a new entry) after writing `<id>.dat`, porting Android. Verified by 2
new Core tests (`WritePathology_RenamedTitle_UpdatesManifestEntry`,
`WritePathology_NewId_AppendsManifestEntry`); Core suite 61/61 green.



**Goal:** Renames (and any metadata change) persist to `manifest.txt` so the rhythm lists update.

**Approach (faithful port of Android `FilePathologySource.writePathology`):**
- In `FilePathologySource.WritePathology`, after writing `<id>.dat`, read the manifest; find the entry
  by `Id`. If found and `TitleEn`/`NameRu` differ → rewrite that entry; if absent → append a new
  entry (`leadsCount = file.Leads.Count`, `FileName = "<id>.dat"`). Persist via the existing atomic
  `WriteManifest`. Only rewrite when something changed.
- Keep `PathologyRepository.WritePathology`'s `LoadManifest()` call — it now has fresh content to load,
  which already raises `ManifestChanged` → `RhythmViewModel.LoadManifestAsync` → lists refresh.
- The in-memory patch `ConstructorScreen.RefreshRhythmListNames` can stay (it shows the rename
  pre-save); after this fix the post-save reload will agree with it instead of reverting.

**Tests:** extend `tests/CardioSimulator.Core.Tests/FilePathologySourceTests.cs` — write a renamed
file, re-read manifest, assert the entry's title/name updated; assert writing a brand-new id appends
an entry. (Round-trips with the existing `PathologyRepositoryTests`.)

**Risk:** low. Pure Core change with an obvious oracle (Android).

---

## Workstream 2 — Course "All in one" full-HTML paste  (✅ DONE 2026-06-14, Option C hybrid)

**Implemented:** `Lecture.IsStandalone` (front-matter extra `layout: standalone`, round-trips via
`CourseParser`); `CourseConstructorViewModel.ImportFullPage` (detects `<!doctype`/`<html>` → verbatim
+ flag, else fragment fallback); an **"All in one"** toolbar button + paste dialog in
`CourseConstructorScreen`; `LectureWebView.BuildStandaloneDocument` serves the page as-is while
injecting KaTeX, `<ecg>`→SVG, a course `<base>` (only if absent), and the quiz bridge. Core 63/63
(+2 standalone tests); App build clean (0/0). GUI feel unverified (no headless WinUI capture).
Follow-up (minor): switching a standalone lecture into the **Visual** block editor will try to parse
a whole document into blocks — fine to leave; All-in-one is a Source-oriented flow.

### Original design notes (for reference)

**Goal:** Let Николай paste a complete AI-generated HTML page and have it become a lecture with
minimal fuss.

**The fork (needs a decision — see §Decisions):**

- **Option A — Ingest body (integrates with the course pipeline).** A paste box runs the page through
  AngleSharp (already a Core dependency): take `<body>` inner-HTML as `Lecture.RawHtml`, hoist
  `<head><style>` into a leading `<style>`, drop `<script>`/metadata. Renders through the existing
  KaTeX + `<ecg>` + theme + quiz pipeline → consistent with the rest of the course, math keeps
  working. *Cost:* the page's own `<head>` CSS/JS is stripped (except hoisted styles); very custom
  pages may look slightly different from the source.
- **Option B — Standalone verbatim.** Store the full document verbatim, mark the lecture
  `layout: standalone` (a `LectureFrontMatter.Extras` key — no schema break). `LectureWebView` serves
  it as-is (no wrapper). *Cost:* loses `<ecg>` substitution, course theme, quiz bridge, and KaTeX
  unless the pasted page ships its own; assets must be self-contained; arbitrary scripts run.
- **Option C — Hybrid (recommended).** Detect a full document (`<!DOCTYPE`/`<html>` at the top) →
  store verbatim + `layout: standalone`, but still **inject KaTeX auto-render, `<ecg>`→SVG, and the
  quiz bridge** on top of the served page so our features keep working inside pasted textbooks. A
  fragment paste falls back to the current Source behavior. Best "paste and it just works."

**UI (shared by all options):**
- Add a third mode to the `_modeToggle` cycle (Visual → Source → **All-in-one**), or a dedicated
  "Paste full page…" button that opens a large monospace paste dialog with an **Import** action.
- On import: set the lecture body/standalone HTML, mark the lecture dirty, refresh the preview.
- New-lecture flow already exists; "All in one" should also be usable to *replace* the current
  lecture's content wholesale.

**Files:** `CourseConstructorScreen` (mode/dialog + wiring), `CourseConstructorViewModel`
(an `ImportFullPage(string)` that sets RawHtml [+ standalone flag]), and for Option B/C
`LectureWebView.RenderAsync`/`BuildDocument` (+ a `Lecture`/front-matter `standalone` signal) and
`CourseParser` serialize/parse of the `layout` extra.

**Risk:** medium. Mostly contained; the data-model touch (standalone flag) and the verbatim render
path (CSP/scripts/assets) are the sharp edges. Recommend Option C but it's the most code.

---

## Workstream 3 — Manual point editing: document + small UX  (P1, cheap)

The capability exists (see Current state). Two cheap wins:
- **Document the workflow** for Николай (select lead tab → tap a point on the canvas or use ◀▶ →
  ▼▲ to nudge or tap the value to type an exact ADC). Add a short "Editing points" section to the
  course/editor help or a one-pager.
- **Discoverability polish (optional):** tooltips on the bottom-bar cells; make the selected sample
  more visually prominent on the canvas; confirm tap-to-select hit-test feels right. No model change.

---

## Workstream 4 — ECG element/artifact library  (P2, the big one; R&D + phased)

This is the substantive new direction (Aleksandr's "doctor, not engineer"). It supersedes raw
point-dragging as the *primary* authoring flow without removing it (power users keep the kernel
editor). Proposed phasing so value lands early:

- **Phase 4.0 — Generators (closest to the Python script, smallest leap).**
  Port the `one_cycle` idea into Core as parametric element generators: `P(width,height)`,
  `QRS(q,r,s widths/heights)`, `T(width,height)`, `ST(level,slope)`, baseline. Each emits an ADC
  segment at the dataset's sample rate/baseline. Add a constructor action **"Insert element"** that
  writes a generated segment into the focused lead at the selected index (reusing `SetSampleRange` +
  undo). This already gives "build an ECG out of pieces," editable afterward with the existing tools.
  *Unit-testable in Core with zero UI.*
- **Phase 4.1 — Width/height handles per element.** After insert, expose +/- width and +/- height
  controls that re-generate/scale just that segment (Николай & Aleksandr both asked for this). Needs
  lightweight element bookkeeping (segment ranges) on top of the flat array — design TBD: either a
  parallel "elements" annotation layer persisted alongside the samples, or treat each insert as an
  immediately-baked segment with a re-edit affordance.
- **Phase 4.2 — Library/palette UI.** A panel of element thumbnails (normal P, biphasic P, tall R,
  pathological Q, ST-elevation, inverted T, …) → click to drop. This is where it becomes "врач, а
  не инженер." Build on 4.0/4.1.

**Open design questions (must resolve before 4.1):** do elements persist as structured metadata
(richer, but a `.dat`-format/Android-parity change) or bake into samples (simple, lossy for
re-editing)? This needs its own mini-ADR — recommend `engineering:architecture` once we commit.

**Risk:** high / open-ended. Keep 4.0 small and shippable; gate 4.1+ on Николай trying 4.0.

---

## Workstream 5 — Full 12-lead ECG screen  (P2, needs clarification)

Николай: "12 отведений, с нижней панелью"; Aleksandr asked all-at-once vs start/stop. The monitor
already renders multi-series with schemes; a 12-lead "printed page" layout + a bottom control panel
(start/stop, speed) is plausibly a new monitor scheme + screen. **Blocked on product detail:** static
12-lead snapshot vs live sweep, layout (3×4 + rhythm strip?), and where it lives (Teaching? new mode?).
Defer until §Decisions clarifies.

---

## Workstream 6 — Course Constructor layout edits  (screenshot #1 ✅ DONE 2026-06-14)

**Implemented** in `CourseConstructorScreen.cs`: removed the left-nav Courses list (Lectures-only
now), added a top-toolbar **course selector** (ComboBox → `SelectCourse`, keeps an unsaved new course
selectable) and a **"New Course"** button (`CourseConstructorViewModel.CreateCourse`). App build clean
(0/0, `-r win-x64`). GUI feel unverified (no headless WinUI capture here) — needs an in-app check.
More screenshots can still be folded in.



Николай's annotated screenshot of the Course Constructor (red ✗ over the Courses drawer, red circle
on the "Лекции" handle, arrows to the top bar). Interpreted asks:

1. **Remove the Courses side-drawer/list** from the constructor. The course is identified/selected in
   the top "Курс обучения" bar instead. (Same simplification already done for the Course *Viewer*,
   commit c263de3.)
2. **Keep the Lectures drawer/list** on the side — that's the in-course navigation.
3. **Course selection + course-level actions belong in the top bar** (course selector; "Новый курс"
   lives up there with New lecture / Rename / Delete).

**Windows port specifics:** the Win `CourseConstructorScreen` differs from Android — it uses a left
*nav column* with stacked **Courses** (`_courseList`) + **Lectures** (`_lectureList`) ListViews, has
**no top course selector**, and has **no "New Course"** button at all (Android has "Новый курс").
So applying this edit means:
- Drop `_courseList` from the left nav; leave only Lectures there (or as a slim drawer).
- Add a **course selector to the top toolbar** (a ComboBox bound to `vm.Repository.Courses`, driving
  `vm.SelectCourse`), matching the Android "Курс обучения" field.
- Add a **"New Course"** toolbar button (`CourseConstructorViewModel.CreateCourse` already exists;
  it's just unsurfaced) for parity.

**Open:** there may be more screenshots in Николай's email — fold them in before/with this.
**Note:** Android is the source of truth and still has the courses drawer too; if we want true parity
the same change should land in `CardioSimulator/.../CourseConstructorScreen.kt` (separate task/repo).

---

## Recommended sequencing

1. **WS1** (names-not-saving) — do now; unblocks Николай's authoring, tiny + safe.
2. **WS3** (document manual editing) — same session; it's mostly a written answer.
3. **WS2** (All in one) — next, once the rendering option is chosen.
4. **WS6** (email edits) — as soon as the email arrives.
5. **WS4.0** (element generators) — start the big direction with a small, testable Core slice.
6. **WS5 / WS4.1+** — after product clarification / after 4.0 lands.

## Decisions (resolved 2026-06-14)
- **D1 (WS2):** All-in-one rendering → **Option C (hybrid)**. Detect a full document → store verbatim
  + `layout: standalone`, but still inject KaTeX, `<ecg>`→SVG, and the quiz bridge on top; fragments
  fall back to the current Source behavior.
- **D2:** Sequencing → **fix-first**. WS1 + WS3 now, then WS2; defer WS4/WS5.
- **D3 (WS5):** Full 12-lead screen → **live mode** (live sweep, not a static snapshot). Hosting
  mode + exact layout still TBD with Николай, but build toward a live 12-lead monitor scheme.
