# Plan: Testing, Examination & Test Constructor — Android port

**Created:** 2026-06-20
**Status:** Not started. These three features were built first in the **Windows port**
(`E:\VLN_Project\CardioSimulatorWin`); this doc is the implementation prompt to bring them to the
Android app (`E:\VLN_Project\CardioSimulator`, package `com.example.cardiosimulator`), matching
behavior and the on-disk test JSON schema.

Both repos are on the same machine. Read the Windows files as the behavioral source of truth and the
Android **OSKE** feature as the structural template (same layered MVVM; `org.json` for JSON; `filesDir`
for storage; StateFlow ViewModels; Compose UI; modes routed in `MainScreen.kt`).

---

## Reference files

**Windows (replicate behavior + data schema):**
- Domain: `src/CardioSimulator.Core/Domain/{Test.cs, TestSeed.cs, Exam.cs, ExamGrader.cs}`
- Data: `src/CardioSimulator.Core/Data/{TestJson.cs, FileTestSource.cs, TestRepository.cs, ExamResultStore.cs}`
- App: `src/CardioSimulator.App/ViewModels/{TestViewModel.cs, ExaminationViewModel.cs, TestConstructorViewModel.cs}`,
  `Controls/{TestQuestionPanel.cs, ExamQuestionPanel.cs}`,
  `Screens/{TestingScreen.cs, ExaminationScreen.cs, TestConstructorScreen.cs}`

**Android (patterns to follow):**
- OSKE: `domain/Oske.kt`, `data/OskeData.kt` (`OskeJson` + `FileOskeSource` + `OskeRepository` +
  `OskeResultStore`), `ui/viewmodels/OskeViewModel.kt`,
  `ui/screens/{OSKEScreen.kt, OskeComponents.kt, OskeConstructorScreen.kt}`, `data/SampleOskeSeeder.kt`
- `domain/OperatingModeModel.kt`, `ui/screens/MainScreen.kt`,
  `ui/viewmodels/{RhythmViewModel.kt, MonitorViewModel.kt, AppViewModel.kt}`
- Strings: `app/src/main/res/values/strings.xml` (+ `values-ru`, `values-zh`, `values-es`)

---

## What the three features are

1. **Testing** (`Тестирование`) — self-assessment quiz with **immediate feedback**. Monitor (left)
   shows the ECG bound to the current question; the question panel (right) shows a counter, a
   per-question countdown, the question, numbered single-choice options, and — **after answering** — a
   comment block (correct answer + explanation) and a Next button with a ✓/✗ verdict.
2. **Examination** (`Экзамен`) — the **same question bank**, but graded with **no comments/feedback**;
   the whole attempt is graded and **saved**, then viewable — store/show results exactly like OSKE.
3. **Test Constructor** — a new top-level mode (like `CourseConstructor`/`OSKEConstructor`) to author
   tests "по типу конструктора курсов".

Both Testing and Examination reuse one shared `TestRepository` + the demo test. Examination collects
ФИО + группа before starting (like OSKE).

---

## Data contract (must match Windows so `tests/*.json` is cross-platform)

`domain/Test.kt`:

```kotlin
data class TestOption(val id: String, val text: String)

data class TestQuestion(
    val id: String,
    val number: Int,
    val text: String,
    val options: List<TestOption>,
    val correctOptionId: String,
    val comment: String,
    val pathologyId: String? = null,    // ECG shown on the monitor; null = leave monitor as-is
    val leads: List<Lead> = emptyList(),
    val scheme: SeriesScheme = SeriesScheme.Grid,
) {
    fun correctOptionNumber(): Int =
        options.indexOfFirst { it.id == correctOptionId }.let { if (it < 0) 0 else it + 1 }
}

data class Test(
    val testId: String,
    val title: String,
    val questions: List<TestQuestion>,
    val questionTimeSeconds: Int = 0,   // per-question countdown; 0 = untimed
)
```

JSON (`data/TestData.kt`, `org.json`, mirroring `OskeJson`): **camelCase** keys, enums as strings,
omit `pathologyId`/`leads` when null/empty, `scheme` defaults to `"Grid"`. Files at
`File(filesDir, "tests")/<testId>.json`. Provide:
- `object TestJson { fun parse(json): Test; fun serialize(test): String }`
- `interface ITestSource { readTests(); readTest(id) }` + `class FileTestSource(root: File)` with
  `writeTest`, `deleteTest`, atomic temp+rename (copy `FileOskeSource`)
- `class TestRepository(source)` — cached `tests`, `test(id)`, `writeTest`, `deleteTest`, `reload`

`domain/Exam.kt` + `ExamGrader` + result store (model on `OskeResult`/`OskeGrader`/`OskeResultStore`):

```kotlin
data class ExamStudentInfo(val fullName: String, val group: String)
data class ExamQuestionResult(val questionId: String, val selected: String?, val correct: String, val isCorrect: Boolean)
data class ExamResult(
    val student: ExamStudentInfo, val testId: String, val testTitle: String,
    val timestamp: Long, val questions: List<ExamQuestionResult>,
    val correctCount: Int, val totalCount: Int, val passed: Boolean,
)
object ExamGrader { const val PASS_FRACTION = 0.6; fun grade(test, selections: Map<String,String>, student): ExamResult }
```

`ExamResultStore(root = File(filesDir, "tests/results"))` with `save(result)` (file
`"${timestamp}_${name}.json"`) + `list()` newest-first — copy `OskeResultStore`. Results are
local-only, so `timestamp` as Long (Android convention) is fine; only `tests/*.json` content must be
portable. **The test reader must not recurse into `tests/results/`** (list `*.json` in the root only).

`domain/TestSeed.kt` — `fun sample(pathologyIds: List<String>): Test`, `testId="sample"`,
`questionTimeSeconds=300`, prototype questions. Q1 verbatim (Russian):
- Text: «Найти депрессию (если она имеется), проверить, является ли она вторичной; если нет — отнести
  её в разряд «патологической, требующей дифференциальной диагностики (ДД)».»
- Options: 1 «Депрессия присутствует» (correct), 2 «Отсутствует, так как…», 3 «Да, вторичная», 4 «ПДД»
- Comment: «На графике видно, что в сегменте AVL и V5, V7 чётко видны подъёмы сегмента ST.»
- Plus 2 more rhythm/HR questions (see `TestSeed.cs`). Bind questions to the first 3 pathology ids.

---

## ViewModels (StateFlow, like the existing ones)

- **`TestViewModel`** (self-assessment): `Test?`, current index, `revealed`, `selectedOptionId`,
  `correctCount`, `finished`, `remainingSeconds`. `start(test)`, `select(optionId)`
  (records + grades + reveals comment), `next()` (advance / finish), `restart()`, `close()`, `tick()`
  (decrement; auto-reveal-unanswered at 0).
- **`ExaminationViewModel`** (constructed with `ExamResultStore`): `start(test, student)`,
  `select(optionId)` (records only, **no reveal**, changeable), `next()` (advance; on last →
  `submit()`), `submit()` → `ExamGrader.grade` + `resultStore.save` + expose `result`, `reset()`,
  `tick()` (expiry → next). Plus `results` + `refreshResults()` like `OskeViewModel`.
- **`TestConstructorViewModel`**: mutable edit model (title, questionTimeSeconds, editable questions
  with options/correctOptionId/comment/pathologyId). `newTest()`, `load(testId)`,
  `addQuestion/removeQuestion`, `addOption/removeOption`, `save()` (compile → `writeTest`), `delete()`.
  Generate ids with a short random string.

---

## UI (Compose)

**Drive the monitor from the question** (Testing & Examination), as `TestingScreen.cs`/
`ExaminationScreen.cs` do: when the current question changes and has a `pathologyId`, call
`rhythmViewModel.selectRhythm(id, persist = false)` and apply `leads`/`scheme` via the monitor VM
(`setSeriesCount`/`setSeriesScheme`; add a lead-selection setter if missing), and start the monitor as
the other screens do. Do this **once per question** (track last-loaded id), not on every tick/answer.

1. **`TestingScreen`** (rewrite stub): Row — monitor on the left (**no `MonitorControlPanel`**) at ~3f,
   question panel at ~2f. Remove the placeholder right-pane `Box{}` and the hardcoded "Question 13" in
   `TestingControlPanel`.
2. **`TestQuestionPanel`** — match the prototype top→bottom: header «N из M» (left, bold red) + «M:SS»
   (right, bold red, only if timed); centered «N вопрос» title (bold, blue accent); question text
   (bold, blue); numbered single-choice options (click grades immediately); **after answering** a
   «Комментарий» block = bold title + «Правильный ответ: N» (`correctOptionNumber()`) + explanation,
   chosen option colored green/red and the correct one green; a «Следующий вопрос»/«Завершить» button
   (enabled after answering) with ✓ (green)/✗ (red). Before start: test picker (dropdown of
   `repository.tests` + Start); at end: score summary with Restart / pick-another.
3. **`ExaminationScreen`** (rewrite stub) — model on `OSKEScreen.kt`: sub-tab row (`Экзамен` /
   `Результаты`); start area (intro + Start → dialog with ФИО + группа + test); exam area = monitor
   (left) + an **`ExamQuestionPanel`** (right) = the Testing panel **without comment/verdict** (options
   only highlight the current selection, changeable); on Finish → grade + save + banner
   (passed/failed + score) + per-question ✓/✗ breakdown (reuse OSKE's graded-block rendering with the
   test's option text); Results tab = list of saved `ExamResult`s + detail breakdown (look up the test
   by id for option text; fall back to ids if missing).
4. **`TestConstructorScreen`** — model on `OskeConstructorScreen.kt`: tests dropdown + New/Delete,
   title field + per-question-seconds field, scrollable list of question editor cards (question text,
   ECG/pathology picker that previews on the monitor, options with "correct" radio + text + remove,
   add-option, comment field, remove-question), add-question button, Save. Disable
   spell-check/auto-correct on text fields (Cyrillic input).

---

## Wiring

- `OperatingModeModel.kt`: add `TestConstructor(R.string.mode_test_constructor)`.
- `MainScreen.kt`: add ViewModel factories (keyed by mode) for `TestViewModel`, `ExaminationViewModel`,
  `TestConstructorViewModel`; route `Testing`/`Examination`/`TestConstructor` in the
  `when(selectedMode.id)` block; Testing/Examination show no bottom-panel content. If a compare/zoom
  ("+") button is shown in the bottom bar for Testing/Examination, **hide it** (the gear/Settings
  button stays).
- `AppViewModel.kt`: add nullable `testRepository: TestRepository?` and `examResultStore:
  ExamResultStore?` (like `oskeRepository`/`oskeResultStore`); construct/swap their
  `FileTestSource(File(filesDir,"tests"))` / `ExamResultStore(File(filesDir,"tests/results"))` on data
  load; **seed the demo test once pathologies are loaded** if no tests exist (mirror `SampleOskeSeeder`;
  build from the first 3 pathology ids via `TestSeed.sample`).

---

## Strings (`values/` + `values-ru/zh/es`)

Add `mode_test_constructor` plus the `test_*` / `exam_*` keys, copying the exact set and translations
from the Windows `AppStrings.cs`:
`test_select_title, test_start, test_empty, test_counter_format, test_question_title_format,
test_comment_title, test_correct_answer_format, test_next, test_finish, test_result_title,
test_result_score_format, test_restart, test_ctor_*`, and `exam_tab_exam, exam_tab_results, exam_start,
exam_start_title, exam_intro, exam_field_full_name, exam_field_group, exam_field_test, exam_no_tests,
exam_finish, exam_finish_confirm(_title), exam_passed, exam_failed, exam_score_format,
exam_new_attempt, exam_unanswered, exam_results_empty`. RU is the primary user language.

---

## Behavior decisions (match Windows; flag if you'd diverge)

- Per-question countdown; on expiry: Testing auto-reveals unanswered, Examination auto-advances.
- Forward-only navigation (no going back). Examination answers changeable until Next.
- Exam pass threshold = 60%. Testing monitor has **no** control panel.

---

## Acceptance

- `./gradlew :app:assembleDebug` builds clean; `./gradlew :app:testDebugUnitTest` passes.
- Unit tests mirroring Windows: `TestJson` round-trip, `FileTestSource` read/write/delete, `ExamGrader`
  (all-correct passes, unanswered fails, wrong recorded), `ExamResultStore` round-trip newest-first.
- Manually: Testing shows the prototype flow with feedback; Examination has no comments, saves a
  result, and the Results tab lists it with a ✓/✗ breakdown; the Test Constructor authors a test that
  appears in both pickers; a `tests/sample.json` written by Android is schema-compatible with the
  Windows app.
