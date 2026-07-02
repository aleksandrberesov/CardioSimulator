# Android parity — Test Constructor "Manage Themes" dialog: add themes from Course Constructor courses

**Status:** active
**Owner:** a.beresov
**Started:** 2026-07-01
**Related issues / PRs:** Win→Android sync of the WinUI change (CardioSimulatorWin, 2026-07-01)

## Goal

In the Test Constructor's **Bank → Manage Themes** dialog, let the author pull the
**courses created in the Course Constructor** into the question-bank theme catalog with one
tap, instead of only being able to type a new theme by hand. Each course title is effectively
a subject/theme, so offering them as ready-made themes removes duplicate typing and keeps test
themes aligned with course names. This ports the WinUI change (already shipped on the Windows
port) to the Android master.

## Current state

**Windows (done, reference implementation):**
- `src/CardioSimulator.App/Screens/TestConstructorScreen.cs` → `OnManageThemesAsync()`:
  added a **"From courses"** row below the free-text add row — a `ComboBox` of course display
  names + an **Add** button. The combo is populated from `_appVm.CourseRepository.Courses`,
  **excludes courses already in the catalog** (case-insensitive), and is rebuilt on every
  add/delete (so deleting a course-derived theme makes the course reappear). The whole row is
  shown only when `CourseRepository.Courses.Count > 0`. Add uses `TestThemeStore.Add` (dedups
  case-insensitively).
- Helper `CourseThemeName(CourseEntry)` → `SelectedLanguage == RU ? (NameRu ?? TitleEn) : TitleEn`.
- New strings `theme_from_course` = "From courses" / `theme_from_course_hint` = "Select a
  course" in all 5 locales (`Localization/AppStrings.cs`) + the two accessor properties.
- Built clean x64 (0 errors).

**Android (target):**
- Dialog composable: `app/src/main/java/com/example/cardiosimulator/ui/screens/TestConstructorScreen.kt:584`
  `ThemeManagerDialog(themes, onAdd, onDelete, onDismiss)` — currently only a delete-list + a
  free-text add row + Close.
- Invoked from `BankEditor` at `TestConstructorScreen.kt:348` with
  `onAdd = { viewModel.addTheme(it) }`, `onDelete = { viewModel.deleteTheme(it) }`.
- `TestConstructorViewModel` (`ui/viewmodels/TestConstructorViewModel.kt`):
  `themes: StateFlow<List<String>>` (:46), `addTheme(theme)` dedups via `.distinct()` and
  persists to `TestThemeStore` (:195), `deleteTheme(theme)` (:201).
- Courses are available app-wide: `AppViewModel.courses: StateFlow<List<CourseEntry>>`
  (`ui/viewmodels/AppViewModel.kt:142`), **sorted by English title, with an "All Rhythms"
  sentinel prepended** (`id = ALL_RHYTHMS_ID`, `AppViewModel.kt:664` → `"all_rhythms"`). Existing
  code excludes it via `filterNot { it.id == AppViewModel.ALL_RHYTHMS_ID }`
  (`ui/panels/CourseConstructorTopPanel.kt:26`).
- Current language: `AppViewModel.selectedLanguage: StateFlow<Language>` (`AppViewModel.kt:100`).
- `CourseEntry` fields (`domain/Course.kt:29`): `id, titleEn, nameRu: String?, lecturesCount,
  pathologies`. Same localization convention as Windows: `if (lang == Language.RU) nameRu ?: titleEn
  else titleEn`.
- `BankEditor(appViewModel, monitorViewModel, rhythmViewModel, viewModel)` already receives
  `appViewModel`, so `courses`/`selectedLanguage` are reachable without new plumbing.

## Non-goals

- No auto-sync: courses only enter the catalog when the user explicitly taps Add (matches
  Windows — the picker never floods the theme list).
- Not touching the Course Constructor, the per-question theme dropdown, or the theme
  filter tabs — they already read `themes`, so newly added course-themes appear automatically.
- Not back-filling the pre-existing zh/es translation gap for `test_ctor_manage_themes` /
  `test_ctor_theme_new_hint` (see Risks) beyond adding the two *new* strings to all 5 locales.

## Plan

### Phase 1 — Dialog gains a "From courses" picker
In `ui/screens/TestConstructorScreen.kt`:
- Add a parameter to `ThemeManagerDialog`:
  `courses: List<String>` — already-localized, already-filtered course names (empty ⇒ hide row).
- Render, below the existing free-text add `Row`, a **"From courses"** section shown only when
  `courses.isNotEmpty()`:
  - a label `stringResource(R.string.test_ctor_theme_from_course)`,
  - an `ExposedDropdownMenuBox` (read-only `OutlinedTextField` + `ExposedDropdownMenu`) whose
    items are `courses`, with placeholder `test_ctor_theme_from_course_hint`, holding a local
    `selectedCourse` state,
  - an Add `IconButton(Icons.Default.Add)` → `if (selectedCourse.isNotBlank()) { onAdd(selectedCourse); selectedCourse = "" }`.
  - Keep it inside the same `Column` so it scrolls with the rest.

### Phase 2 — Feed courses into the dialog from `BankEditor`
In `BankEditor` (`TestConstructorScreen.kt:233`):
- Collect state:
  `val courseEntries by appViewModel.courses.collectAsState()`
  `val lang by appViewModel.selectedLanguage.collectAsState()`
- Compute the filtered display list (exclude the All-Rhythms sentinel + anything already a theme):
  ```kotlin
  val courseThemeNames = courseEntries
      .filterNot { it.id == AppViewModel.ALL_RHYTHMS_ID }
      .map { if (lang == Language.RU) (it.nameRu ?: it.titleEn) else it.titleEn }
      .filter { name -> name.isNotBlank() && themes.none { it.equals(name, ignoreCase = true) } }
  ```
- Pass `courses = courseThemeNames` into the `ThemeManagerDialog(...)` call at `:349`.
- Add imports: `com.example.cardiosimulator.domain.Language` and (if not already resolvable)
  reference `AppViewModel.ALL_RHYTHMS_ID`.
- Because `themes` is recomposed reactively after `addTheme`/`deleteTheme`, `courseThemeNames`
  recomputes automatically → a just-added course drops out of the picker; a deleted theme's
  course reappears. No manual rebuild needed (Compose analogue of the Windows `Rebuild()`).

### Phase 3 — Strings
Add two strings to **all five** locale files (`values/`, `values-ru/`, `values-zh/`,
`values-es/`, `values-hi/`), right after `test_ctor_theme_new_hint` (`values/strings.xml:237`):
- `test_ctor_theme_from_course`
- `test_ctor_theme_from_course_hint`

Suggested values (mirror the Windows copy):
| key | en | ru | zh | es | hi |
|---|---|---|---|---|---|
| `test_ctor_theme_from_course` | From courses | Из курсов | 来自课程 | De cursos | पाठ्यक्रमों से |
| `test_ctor_theme_from_course_hint` | Select a course | Выберите курс | 选择课程 | Seleccionar un curso | एक पाठ्यक्रम चुनें |

## Risks & open questions

- **Case-insensitive dedup divergence:** Windows `TestThemeStore.Add` + the picker filter use
  `CurrentCultureIgnoreCase`. Android `addTheme` dedups with Kotlin `.distinct()` (case-*sensitive*).
  The Phase-2 filter already uses `equals(..., ignoreCase = true)` so the picker won't *offer* a
  case-dup, which covers the realistic path. Optionally harden `addTheme` to
  `distinctBy { it.lowercase() }` — but that's a behavior change beyond this sync; leave unless the
  customer wants it.
- **Pre-existing zh/es string gap (found during research):** `test_ctor_manage_themes` and
  `test_ctor_theme_new_hint` exist only in `values/`, `values-ru/`, `values-hi/` — they fall back to
  English in zh/es. Don't "fix" the old keys here (out of scope), but **do** add the two new keys to
  all 5 so we don't widen the gap.
- **`ExposedDropdownMenuBox` API:** already used elsewhere in this file (the per-question theme
  dropdown, `TestConstructorScreen.kt:521`) — copy that pattern for consistency (`@OptIn
  (ExperimentalMaterial3Api::class)` already present on the enclosing composables).

## Verification

- Build: `./gradlew :app:assembleDebug` (or Android Studio build) — 0 errors.
- Manual (EN + RU):
  1. Create ≥1 course in the Course Constructor.
  2. Test Constructor → Bank → gear/Manage Themes: the **From courses** picker lists course
     titles in the active language; the already-present ones are absent.
  3. Pick a course → Add → it appears in the theme list and disappears from the picker; the new
     theme shows up in the bank filter tabs and the per-question theme dropdown.
  4. Delete that theme → the course reappears in the picker.
  5. With **no** courses loaded, the "From courses" row is hidden; the free-text add still works.
- Switch language RU⇄EN with the dialog reopened → course names re-localize (`nameRu` vs `titleEn`).

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Test Ctor: add "From courses" theme picker | 1–3 | Single screen file + 5 strings.xml; small, ship together |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:** —
- **PRs:** —
- **Deviations from plan:** —
- **Follow-ups spawned:** —

> **⚠ Reactivated 2026-07-01.** A prior local pass already applied these edits to the working tree
> (`app/.../ui/screens/TestConstructorScreen.kt` + `test_ctor_theme_from_course`/`_hint` in all 5
> `values*/strings.xml`). This plan was re-opened for review/processing in Android Studio — **check the
> existing working-tree changes first** (`git status` / `git diff`) before re-applying, so you don't
> duplicate the picker or the strings.
