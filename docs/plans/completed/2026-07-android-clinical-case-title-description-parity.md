# Plan: Clinical case — rename "Case Title" → "Title" + add a "Description" field (Android parity)

**Created:** 2026-07-02
**Status:** ACTIVE
**Direction:** **Windows → Android** (the usual). Built in the WinUI 3 port first from customer
feedback; Android must catch up. The Windows port is the **reference implementation** — match its
behaviour, adapting to Compose idioms.

**Target (Android) source root:** `E:\VLN_Project\CardioSimulator\app\src\main\java\com\example\cardiosimulator\`
**Reference (Windows) source root:** `E:\VLN_Project\CardioSimulatorWin\src\`

## Goal

Two small deltas to the **Clinical Case Parameters** editor + dashboard (the clinical-case feature
itself already ships on both platforms):

1. **Rename the "Case Title" label to just "Title"** everywhere (edit dialog + dashboard), all 5
   languages. The stored key stays `title` — this is a display-label change only.
2. **Add a new "Description" field**, placed **directly under Title** in both the edit dialog and the
   clinical dashboard. Stored as a new `description=…` pair inside the comma-delimited `clinical_case`
   string.

## Reference (Windows, done)

- **`CardioSimulator.App/Localization/AppStrings.cs`**
  - New accessor `ClinicalLabelDescription => S("clinical_label_description")`.
  - `clinical_label_title` value changed **"Case Title" → "Title"** in every locale:
    en `Title`, ru `Название`, zh `标题`, es `Título`, hi `शीर्षक`.
  - New `clinical_label_description` in every locale:
    en `Description`, ru `Описание`, zh `描述`, es `Descripción`, hi `विवरण`.
- **`CardioSimulator.App/Screens/ConstructorScreen.cs` (`OnClinicalCaseClick`)**
  - Parses a `description` key (with RU/ES/ZH/HI aliases) into a `description` local.
  - New `descriptionBox` `TextBox` (`TextWrapping=Wrap`) inserted into the dialog panel **right after
    `titleBox`**.
  - On save, writes `description={…}` **immediately after** `title={…}`, first **stripping separator
    chars** (`,` `;` `\r` `\n` → space) so the raw, unescaped, comma-delimited `clinical_case` field
    stays parseable.
- **`CardioSimulator.App/Controls/RhythmChoosingPanel.xaml.cs` (`ParseClinicalCase`)**
  - Maps the `description` key (+ aliases) to a canonical `"description"`.
  - Emits a **Description dashboard row directly after the Title row**.

> The `clinical_case` string is stored **raw** (no escaping) — header line `clinical_case:…\n` and
> manifest field `;clinical_case:…`. Commas are the intra-string pair separator. A free-text
> description is near-guaranteed to contain commas, hence the sanitization on save. This mirrors the
> existing fragility Android already has (`PathologyParser.kt` writes `clinical_case` raw at
> `sb.append("clinical_case:").append(file.clinicalCase)` and `;clinical_case:`), while the
> pathology-level `description:` header field IS `\n`-escaped — do **not** confuse the two.

## Current state (Android, to change)

All three touch points already exist for the clinical-case feature:

- **Strings** `app/src/main/res/values*/strings.xml` — `clinical_label_title` present in en/ru/zh/es/hi;
  **no** `clinical_label_description` yet.
  - `values/strings.xml:474` `Case Title` · `values-ru:470` `Название случая` ·
    `values-zh:319` `病例标题` · `values-es:327` `Título del caso` · `values-hi:474` `मामले का शीर्षक`.
- **Edit dialog** `ui/screens/ConstructorScreen.kt` → `ClinicalCaseDialog` (lines ~908–1049):
  - Parses `initialClinicalCase` into `params` (~915). Locals `title,name,age,gender,hr,bp,others`
    (~922–932); `others` excludes `listOf("title","name","age","gender","hr","bp")` (~929).
  - Title `TextField` at ~947–952 (`label = clinical_label_title`, `singleLine = true`).
  - Confirm (~1016–1038) builds `newParams` in order title→name→age→gender→hr→bp→others, then
    `onSave(newParams.map{"${k}=${v}"}.joinToString(","))`.
- **Dashboard** `ui/panels/RhythmSelector.kt` → `ClinicalDashboard` (lines ~235–308):
  - `canonicalKeys = listOf("title","name","age","gender","hr","bp")` (~249) drives row order.
  - `when (key)` label map (~268–276) has no `description` case.
- **Unaffected:** `RhythmSelector.getClinicalTitle()` (~310) keys off `title=` only — leave as is.
  `RhythmViewModel.kt` / `ConstructorViewModel.kt` pass `clinicalCase` through opaquely — no change.

## Non-goals

- Do **not** touch the pathology-level `description:` field (the separate "Pathology Information"
  dialog / `pathology_description_label`). This plan is only the **clinical-case sub-field**.
- Do **not** change the storage format, add escaping, or migrate existing records. The stored `title`
  key is unchanged; `description` is purely additive.
- Do **not** make description multi-line — the raw single-line `clinical_case` field can't hold
  newlines. Keep it `singleLine`.

## Implementation steps

### 1. Strings — rename title, add description (all 5 locales)

Change the `clinical_label_title` **value** (keep the name) and add `clinical_label_description`
right after it:

| file | `clinical_label_title` → | add `clinical_label_description` = |
|---|---|---|
| `values/strings.xml` | `Title` | `Description` |
| `values-ru/strings.xml` | `Название` | `Описание` |
| `values-zh/strings.xml` | `标题` | `描述` |
| `values-es/strings.xml` | `Título` | `Descripción` |
| `values-hi/strings.xml` | `शीर्षक` | `विवरण` |

e.g. `values/strings.xml`:
```xml
<string name="clinical_label_title">Title</string>
<string name="clinical_label_description">Description</string>
```

### 2. Edit dialog — `ClinicalCaseDialog` (`ConstructorScreen.kt`)

**a.** Add a `description` state next to the others (~after line 922), and exclude it from the
`others` bucket (line ~929):
```kotlin
var title by remember { mutableStateOf(params["title"] ?: "") }
var description by remember { mutableStateOf(params["description"] ?: "") }
var name by remember { mutableStateOf(params["name"] ?: "") }
...
var others by remember {
    mutableStateOf(params.filterKeys { it !in listOf("title", "description", "name", "age", "gender", "hr", "bp") }
        .map { "${it.key}=${it.value}" }
        .joinToString(", "))
}
```

**b.** Insert a Description `TextField` **immediately after the Title `TextField`** (after line ~952):
```kotlin
TextField(
    value = title,
    onValueChange = { title = it },
    label = { Text(stringResource(R.string.clinical_label_title)) },
    singleLine = true
)
TextField(
    value = description,
    onValueChange = { description = it },
    label = { Text(stringResource(R.string.clinical_label_description)) },
    singleLine = true
)
```

**c.** In the confirm handler, write `description` **right after `title`**, sanitizing separator
chars (line ~1018):
```kotlin
if (title.isNotBlank()) newParams["title"] = title
if (description.isNotBlank()) {
    // clinical_case is stored raw & comma-delimited — strip separators so it stays parseable.
    newParams["description"] = description.replace(Regex("[,;\r\n]"), " ").trim()
}
if (name.isNotBlank()) newParams["name"] = name
```
Map ordering is insertion order (`mutableMapOf` = `LinkedHashMap`), so `description` serializes right
after `title` — matching Windows.

### 3. Dashboard — `ClinicalDashboard` (`RhythmSelector.kt`)

**a.** Add `description` to `canonicalKeys` **right after `title`** (line ~249) so the row renders
directly under Title:
```kotlin
val canonicalKeys = listOf("title", "description", "name", "age", "gender", "hr", "bp")
```

**b.** Add its label to the `when (key)` map (after line ~269):
```kotlin
"title" -> R.string.clinical_label_title
"description" -> R.string.clinical_label_description
"name" -> R.string.clinical_label_patient_name
```

## Intentional divergences to flag in review

1. **No multi-language key aliases on parse.** Windows' `OnClinicalCaseClick` / `ParseClinicalCase`
   accept localized keys (`описание`, `descripción`, `描述`, `विवरण`, …). Android's dialog + dashboard
   read **canonical English keys** straight from the `params` map (this is how Android already handles
   `title/name/age/…`), so only `description` is needed. Keep it consistent — do **not** port the alias
   switch.
2. **Sanitize-on-save via `Regex("[,;\r\n]")`.** Same intent as the Windows `.Replace(',',' ')…`
   chain; Kotlin idiom differs. Applies to `description` only (the field most likely to contain
   commas) — existing fields keep their current behaviour.
3. **Description shows under Title** in both dialog and dashboard — this ordering is deliberate
   (customer request); don't reorder.

## Verification

- Build: from `E:\VLN_Project\CardioSimulator`, `./gradlew :app:assembleDebug` (0 warnings/errors).
  Optionally `./gradlew :app:testDebugUnitTest` — `PathologyParserTest` round-trips `clinical_case` and
  should stay green (format unchanged).
- Manual (Constructor → pick a pathology → clinical-case / stethoscope edit button):
  - Dialog shows **Title** (renamed) with a **Description** field directly beneath it.
  - Enter a title + a description containing a comma (e.g. `Chest pain, radiating`) → save → reopen:
    the description round-trips with the comma replaced by a space, all other fields intact.
  - Switch the rhythm list to **Clinical mode**, select the case → dashboard lists **Title** then
    **Description** as the first two rows.
  - Language switch (RU/ZH/ES/HI) relabels both fields; stored `title`/`description` keys unchanged.

## Related

- Extends the shipped clinical-case feature — Windows sync doc
  `CardioSimulatorWin/docs/plans/sync/2026-07-android-clinical-case-presentation-mode.md` (which
  originally specced `clinical_label_title` = "Case Title"; this plan supersedes that label).
- Windows reference commit: label rename + description field across `AppStrings.cs`,
  `ConstructorScreen.cs`, `RhythmChoosingPanel.xaml.cs`.
