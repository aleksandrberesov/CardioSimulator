# Course Bundle Format

Specification of the `Courses.zip` bundle consumed by the Course
Constructor (see [plan](plans/active/2026-05-course-constructor.md)).
Pattern mirrors [`data-structure.md`](data-structure.md): UTF-8 text,
LF line endings, key-value headers for metadata, and **HTML** for rich
lecture content (rendered in a single per-lecture `WebView`), with a
custom `<ecg>` element for embedded ECG references.

## 1. Overview

```
Courses.zip
├── manifest.txt                 ← dataset header + course index
├── <course-id>/
│   ├── course.txt               ← course metadata + lecture index
│   ├── lectures/
│   │   ├── <lecture-id>.<lang>.html
│   │   └── …
│   └── assets/
│       └── <image-or-svg>
└── …
```

Multilingual content lives in **one file per language**
(`<lecture-id>.en.html`, `<lecture-id>.ru.html`), mirroring the
`values-ru/`, `values-zh/` Android resource pattern. At read time the
viewer picks the file matching the active `Language.tag`, falling back
to `.en.html`.

`<course-id>` and `<lecture-id>` are ASCII alphanumeric + `-` / `_`.

## 2. `manifest.txt`

Top-level index — the first file a consumer should read.

```
version:1.0
created:2026-05-29T12:00:00
encoding:utf-8
line_endings:lf

course:cardio-101;lectures:8;title:Cardio Basics;name:Основы кардиологии;pathologies:sin,1abblock,fib
course:arrhythmia-adv;lectures:12;title:Arrhythmias;name:Аритмии;pathologies:fib,atrflu,tach
```

| Header key | Required | Notes |
|---|---|---|
| `version` | yes | Validated against `CourseManifest.SUPPORTED_VERSION`. Mismatch → `FormatException`. |
| `created` | no | ISO-8601 local time of generation. |
| `encoding` | no | Always `utf-8`. |
| `line_endings` | no | Always `lf`. |

Below the blank-line-terminated header, one semicolon-delimited row per
course: `course:<id>;lectures:<n>;title:<en>;name:<ru>;pathologies:<csv>`.

| Row field | Required | Notes |
|---|---|---|
| `course` | yes | `<course-id>`. |
| `lectures` | no | Lecture count (display hint). |
| `title` / `name` | no | English / Russian display names. |
| `pathologies` | no | Comma-separated `PathologyEntry.id`s the course covers. Mirrors the authoritative list in `course.txt` (§3) so a consumer can scope the rhythm list to a course straight from the manifest. |

## 3. `<course-id>/course.txt`

Per-course metadata + ordered lecture index. Same key-value syntax.

```
course:cardio-101
title:Cardio Basics
name:Основы кардиологии
authors:A. Beresov
language:ru,en
pathologies:sin,1abblock,fib

lecture:01-intro;title:Introduction;name:Введение
lecture:02-axis;title:Electrical Axis;name:Электрическая ось
```

The lecture rows define the **display order**. Each row references one
or more `<lecture-id>.<lang>.html` files in `lectures/`.

The optional `pathologies` header key is a comma-separated list of
`PathologyEntry.id`s — the **authoritative** set of pathologies the
course covers, typically the union of the ids embedded across its
lectures (§5.1). It is surfaced to the UI as a course → pathologies map
so a viewer (e.g. `ui/panels/RhythmSelector.kt`) can restrict the
selectable rhythm list to the active course. The generator keeps the
manifest row's `pathologies` field (§2) in sync with this list.

## 4. `<lecture-id>.<lang>.html`

YAML-lite front matter + HTML body. The front matter is delimited by
`---` lines and parsed as `key: value` (one entry per line). The body is
HTML rendered in a single `WebView`: standard tags for text, KaTeX
`$…$` / `$$…$$` math (auto-rendered in one DOM pass), `<img>` for images,
plus the custom constructs in §5.

```html
---
id: 02-axis
order: 2
title: Electrical Axis
schemaVersion: 1
---
<h1>Cardiac Axis</h1>

<p>The mean QRS axis lies between <strong>−30°</strong> and
<strong>+90°</strong>.</p>

<p>Inline math: $\theta = \arctan(aVF / I)$.</p>

$$ \theta = \arctan\!\left(\frac{aVF}{I}\right) $$

<img src="assets/ecg-axis.svg" alt="Hexaxial reference">
```

**Front-matter keys recognised by the parser:**

| Key | Type | Notes |
|---|---|---|
| `id` | string | Should match the filename's lecture id. |
| `order` | int | Display order within the course (fallback when not set in `course.txt`). |
| `title` | string | Display title in this language. Falls back to the title in `course.txt`. |
| `schemaVersion` | int | Defaults to `1`. The renderer ignores unknown custom elements when a higher schema is observed. |

Unknown keys are preserved verbatim and re-emitted on save.

The body after the closing `---` is stored verbatim as `Lecture.rawHtml`
and handed to the `WebView` unchanged except for the `<ecg>` → inline-SVG
rewrite (§5.1); it is **not** decomposed into structured blocks.

## 5. Custom constructs

The two things plain HTML can't express on its own are an embedded ECG
reference and a write-back-able quiz table. Both are authored as HTML — so
any browser renders the source intact — and the app gives them extra
behaviour at runtime.

### 5.1 `<ecg>` — embedded ECG reference

```html
<ecg pathology="lad" lead="II" caption="Left axis deviation, lead II"></ecg>
```

| Attribute | Required | Notes |
|---|---|---|
| `pathology` | yes | `PathologyEntry.id` resolvable through `PathologyRepository`. |
| `lead` | no | `Lead.fromToken` value (`I`, `II`, …, `V6`). Omit to display all 12 leads. |
| `caption` | no | Free text rendered below the trace. |

At load time the renderer rewrites each `<ecg>` element into a `<figure>`
holding an inline `<svg>` produced by `EcgSvgRenderer`, which reuses
`PathologyRepository.leadWaveform` and the same projection math as the
on-screen monitor (`ui/components/ChartCanvas.kt`, `projectPath`). The
figure is **static** — no scroll/sweep animation. `<ecg>` is an inert
custom element, so a missed rewrite degrades gracefully instead of
breaking the page.

### 5.2 `<table>` — quiz / reference table

A standard HTML table. Quiz behaviour is opt-in via `data-` attributes:

```html
<table data-quiz-id="axis-quadrants" data-editable="true">
  <tr><th>Axis</th><th>I</th><th>aVF</th><th>Range</th></tr>
  <tr><td>Normal</td><td>+</td><td>+</td><td><input></td></tr>
  <tr><td>LAD</td><td>+</td><td>−</td><td><input></td></tr>
</table>
```

| Attribute | Required | Notes |
|---|---|---|
| `data-quiz-id` | yes (when editable) | Stable identifier — the key for `.answers.json` cell state. |
| `data-editable` | no | Default absent / `false`. When `"true"`, `<input>` cells are writable and their edits are persisted (§6). |

Cells the learner fills in are authored as `<input>` elements. In the
constructor, edits flow back through a `@JavascriptInterface` bridge keyed
by `data-quiz-id` + the cell's `(row, col)`; in the read-only viewer the
saved values are injected into the inputs and left non-editable.

## 6. Editable-table answers (`<lecture-id>.<lang>.answers.json`)

Sibling file to a `.html` lecture. Only created when the user actually
edits a cell. Keeps the `.html` author-pristine for diffs and grading.

```json
{
  "axis-quadrants": {
    "1,3": "-30°…+90°",
    "2,3": "-30°…-90°"
  }
}
```

Keys are `"<rowIndex>,<colIndex>"` (0-based, counting only data `<tr>`
rows — the header row is excluded).

## 7. Versioning

- `manifest.version` is bundle-wide.
- `LectureFrontMatter.schemaVersion` is per-lecture, letting individual
  lectures introduce new custom elements without re-versioning the
  bundle. The renderer leaves unknown custom elements inert in the DOM
  rather than failing.

## 8. Encoding & line endings

- All text files: **UTF-8**, no BOM.
- Line endings: **LF**.
- Assets folder is opaque to the parser; binary integrity is the zip's
  responsibility.
