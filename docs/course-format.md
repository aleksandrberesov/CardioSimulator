# Course Bundle Format

Specification of the `Courses.zip` bundle consumed by the Course
Constructor (see [plan](plans/active/2026-05-course-constructor.md)).
Pattern mirrors [`data-structure.md`](data-structure.md): UTF-8 text,
LF line endings, key-value headers for metadata, Markdown for rich
content, custom fenced blocks for the things plain Markdown can't
express.

## 1. Overview

```
Courses.zip
├── manifest.txt                 ← dataset header + course index
├── <course-id>/
│   ├── course.txt               ← course metadata + lecture index
│   ├── lectures/
│   │   ├── <lecture-id>.<lang>.md
│   │   └── …
│   └── assets/
│       └── <image-or-svg>
└── …
```

Multilingual content lives in **one file per language**
(`<lecture-id>.en.md`, `<lecture-id>.ru.md`), mirroring the
`values-ru/`, `values-zh/` Android resource pattern. At read time the
viewer picks the file matching the active `Language.tag`, falling back
to `.en.md`.

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
or more `<lecture-id>.<lang>.md` files in `lectures/`.

The optional `pathologies` header key is a comma-separated list of
`PathologyEntry.id`s — the **authoritative** set of pathologies the
course covers, typically the union of the ids embedded across its
lectures (§5.1). It is surfaced to the UI as a course → pathologies map
so a viewer (e.g. `ui/panels/RhythmSelector.kt`) can restrict the
selectable rhythm list to the active course. The generator keeps the
manifest row's `pathologies` field (§2) in sync with this list.

## 4. `<lecture-id>.<lang>.md`

YAML front matter + Markdown body. The front matter is delimited by
`---` lines and parsed as `key: value` (one entry per line).
The body is standard CommonMark + GFM tables + KaTeX math + the custom
fenced blocks defined in §5.

```markdown
---
id: 02-axis
order: 2
title: Electrical Axis
schemaVersion: 1
---

# Cardiac Axis

The mean QRS axis lies between **-30°** and **+90°**.

Inline math: $\theta = \arctan(aVF / I)$.

Block math:

$$
\theta = \arctan\!\left(\frac{aVF}{I}\right)
$$

![Hexaxial reference](assets/ecg-axis.svg)
```

**Front-matter keys recognised by the parser:**

| Key | Type | Notes |
|---|---|---|
| `id` | string | Should match the filename's lecture id. |
| `order` | int | Display order within the course (fallback when not set in `course.txt`). |
| `title` | string | Display title in this language. Falls back to the title in `course.txt`. |
| `schemaVersion` | int | Defaults to `1`. Loader skips unknown fenced-block types when a higher schema is observed. |

Unknown keys are preserved verbatim and re-emitted on save.

## 5. Custom fenced blocks

The two things Markdown can't express natively are an embedded ECG
reference and an editable quiz table. Both are introduced as fenced
code blocks with custom info-strings, so editors that don't understand
them still render the source intact rather than mangle it.

### 5.1 `ecg` — embedded ECG reference

```
\```ecg
pathology: lad
lead: II
caption: Left axis deviation, lead II
\```
```

| Body key | Required | Notes |
|---|---|---|
| `pathology` | yes | `PathologyEntry.id` resolvable through `PathologyRepository`. |
| `lead` | no | `Lead.fromToken` value (`I`, `II`, …, `V6`). Omit to display all 12 leads. |
| `caption` | no | Free text rendered below the trace. |

Rendering reuses `ui/display/Lead.kt` plus
`PathologyRepository.leadWaveform` — no duplicate pipeline.

### 5.2 `table` — quiz / reference table

```
\```table
id: axis-quadrants
editable: true
---
| Axis   | I | aVF | Range       |
|--------|---|-----|-------------|
| Normal | + | +   | -30°…+90°   |
| LAD    | + | -   | -30°…-90°   |
\```
```

| Header key | Required | Notes |
|---|---|---|
| `id` | yes | Stable identifier — used as the key for `.answers.json` cell state. |
| `editable` | no | Default `false`. When `true`, the runtime mounts each cell as a `TextField`. |

The `---` line separates the key-value header from the GFM table source.
Cell-grid parsing lives in the renderer (Phase 2 read-only, Phase 3
write-back).

## 6. Editable-table answers (`<lecture-id>.<lang>.answers.json`)

Sibling file to a `.md` lecture. Only created when the user actually
edits a cell. Keeps the `.md` author-pristine for diffs and grading.

```json
{
  "axis-quadrants": {
    "1,3": "-30°…+90°",
    "2,3": "-30°…-90°"
  }
}
```

Keys are `"<rowIndex>,<colIndex>"` (0-based, counting only data rows —
the header row is excluded).

## 7. Versioning

- `manifest.version` is bundle-wide.
- `LectureFrontMatter.schemaVersion` is per-lecture, allowing individual
  lectures to evolve fenced-block syntax without re-versioning the
  bundle. Loaders skip unknown fence types with a warning rather than
  failing.

## 8. Encoding & line endings

- All text files: **UTF-8**, no BOM.
- Line endings: **LF**.
- Assets folder is opaque to the parser; binary integrity is the zip's
  responsibility.
