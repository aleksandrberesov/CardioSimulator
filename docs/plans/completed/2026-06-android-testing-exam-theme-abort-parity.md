# Plan / Prompt: Testing & Examination — theme the question panels, add an "Abort test" button, drop the red ✗

**Created:** 2026-06-30
**Status:** NOT STARTED
**Direction:** **Windows → Android** (reverse of the usual). These three tweaks were made in the WinUI 3
port; the Android app must catch up. The Windows port is the **reference implementation** — match the
behaviour/colours, adapting idioms to Kotlin/Jetpack Compose.

**Target (Android) source root:** `E:\VLN_Project\CardioSimulator\app\src\main\java\com\example\cardiosimulator\`
**Reference (Windows) source root:** `E:\VLN_Project\CardioSimulatorWin\src\`

> Scope: a **restyle + two small UX additions** on the *self-assessment Testing* and *Examination*
> screens only. No data/domain/viewmodel-logic changes (one trivial defensive line in `TestViewModel`).

---

## Use this as the prompt

> In the Android CardioSimulator, bring the **Testing** and **Examination** question panels in line with
> the WinUI port: (1) route their colours through the existing green-theme tokens — the panels still use a
> stray **blue** (`#1976D2`) for the title/question and a hard **red** counter, and the grading views use
> bright web greens/reds; make the **question body readable** (primary text, not coloured), reserve
> **green** for headings + the correct answer, **slate** for the progress counter, and a single refined
> **red** for the countdown + wrong answers — adding `Positive`/`Negative` semantic tokens. (2) Add an
> **"Abort test"** button to the Testing question panel (red text, next to / before the Next button) that
> confirms, then drops back to the test picker. (3) **Remove the red ✗** the Testing Next-button shows on a
> wrong answer — keep the green ✓ on a correct one. Read the Windows reference files first and copy the
> exact hex values + strings.

---

## Three changes (each maps 1:1 to a Windows edit this session)

| # | Change | Windows files (reference) | Android target files |
|---|---|---|---|
| **A** | Theme the Testing/Exam question panels + the Exam grading/results views (blue/red/bright-web → green-theme tokens; readable body text; add `Positive`/`Negative`) | `Theming/AppTheme.cs`, `App.xaml`, `Controls/TestQuestionPanel.cs`, `Controls/ExamQuestionPanel.cs`, `Screens/ExaminationScreen.cs` | `ui/theme/Color.kt`, `ui/screens/TestComponents.kt`, `ui/screens/ExaminationScreen.kt` |
| **B** | "Abort test" button (with confirm) in Testing → back to picker | `Controls/TestQuestionPanel.cs` (footer), `ViewModels/TestViewModel.cs` (`Close()`), `Localization/AppStrings.cs` | `ui/screens/TestComponents.kt`, `ui/screens/TestingScreen.kt`, `ui/viewmodels/TestViewModel.kt`, `res/values*/strings.xml` |
| **C** | Drop the red ✗ on the Testing Next button (keep green ✓) | `Controls/TestQuestionPanel.cs` (verdict glyph) | `ui/screens/TestComponents.kt` |

---

## A. New semantic tokens — `ui/theme/Color.kt`

Add two tokens (Windows added them to `App.xaml` + `AppTheme.cs`). They replace the bright
`Color.Green`/`Color.Red`/`0xFF4CAF50`/`0xFFF44336`/`0xFF2E7D32`/`0xFFC62828` literals so pass/fail
matches the muted green theme:

```kotlin
val Positive = Color(0xFF2E9E5B)   // correct answer / pass
val Negative = Color(0xFFCC3A3A)   // wrong answer / fail / countdown urgency
```

(Existing `AccentGreen #33A06A`, `AccentGreenTint #DCF1E6`, `TextPrimary #1B2430`, `TextSecondary #5A6B82`,
`ControlFill #EFF1F7`, `ControlBorder #E0E4EC` are already defined — reuse them.)

## A. Colour mapping — `ui/screens/TestComponents.kt`

Apply to **both** `TestQuestionPanel` and `ExamQuestionPanel` (they're near-duplicates):

| Element | Current (Android) | New token |
|---|---|---|
| Counter `"N из M"` | `Color.Red` | `TextSecondary` |
| Countdown timer | `Color.Red` | `Negative` |
| Title `"N вопрос"` | `Color(0xFF1976D2)` blue | `AccentGreen` |
| **Question text** | `Color(0xFF1976D2)` blue | **`TextPrimary`** (readable — this is the key fix; Windows un-greened the body) |
| Option text — revealed & correct | `0xFF2E7D32` | `Positive` |
| Option text — revealed & selected & wrong | `0xFFC62828` | `Negative` |
| Option text — revealed & other | (falls to `Color.Black`) | `TextSecondary` (add this branch) |
| Option text — not revealed | `Color.Black` | `TextPrimary` |
| Option border — selected & not revealed | `Color.Blue` | `AccentGreen` |
| Option border — correct | `Color.Green` | `Positive` |
| Option border — selected & wrong | `Color.Red` | `Negative` |
| Option border — neutral | `Color.Gray` | `ControlBorder` |
| Option bg — correct | `0xFFE8F5E9` | `AccentGreenTint` |
| Option bg — selected & wrong | `0xFFFFEBEE` | `Negative.copy(alpha = 0.12f)` |
| Comment block bg | `0xFFF5F5F5` | `ControlFill` |
| Comment title + correct-answer line | default | `AccentGreen` |
| Comment body | default | `TextPrimary` |

`ExamQuestionPanel` options: selected text → `AccentGreen` + `FontWeight.Bold`, unselected → `TextPrimary`;
border `if (isSelected) AccentGreen else ControlBorder`.

## A. Colour mapping — `ui/screens/ExaminationScreen.kt` (grading + results)

Replace the bright literals with tokens (mirrors the Windows `LimeGreen`/`Tomato` → `Positive`/`Negative`
sweep): every `Color(0xFF2E7D32)` and `Color(0xFF4CAF50)` → **`Positive`**; every `Color(0xFFC62828)` and
`Color(0xFFF44336)` → **`Negative`**; `Color.Gray` (in-progress / unanswered) → **`TextSecondary`**.
(Lines ~359, 400, 425, 433, 484 at time of writing.) If a pass/fail banner uses a translucent green/red
fill, use `Positive.copy(alpha=…)` / `Negative.copy(alpha=…)`.

> **Relationship to `2026-06-android-green-theme-parity.md`:** that (NOT STARTED) plan's blue→green sweep
> lists these same two files. This plan supersedes the testing/exam slice of it with the **refined**
> mapping (readable body text + `Positive`/`Negative`, not a flat blue→green). When the theme plan runs,
> treat `TestComponents.kt` / `ExaminationScreen.kt` as already done — don't re-introduce blue.

---

## B. "Abort test" button (Testing only)

Windows added a footer button left of Next that calls `TestViewModel.Close()` (drops to the picker), with
a confirm dialog because aborting discards the attempt.

**`ui/screens/TestComponents.kt` — `TestQuestionPanel`:**
- Add a param `onAbort: () -> Unit`.
- A `var showAbortConfirm by remember { mutableStateOf(false) }`.
- Render an **"Abort test"** `TextButton` whose content colour is `Negative`. It must be visible **whether
  or not `revealed`** (the student can quit before answering) — Android currently only renders the Next
  button inside `if (revealed)`. So put a persistent footer: when `revealed`, a `Row` with `[Abort]` on the
  left and the existing `[Next]` on the right; when not revealed, `[Abort]` alone.
- On click → `showAbortConfirm = true`. Render an `AlertDialog` when true:
  - `title` = `stringResource(R.string.test_abort)`
  - `text`  = `stringResource(R.string.test_abort_confirm)`
  - confirm button = `R.string.test_abort` → `{ showAbortConfirm = false; onAbort() }`
  - dismiss button = `R.string.cd_cancel` (already localized in all 5 `values*`) → `{ showAbortConfirm = false }`

**`ui/screens/TestingScreen.kt` — `TestActiveView`:** pass `onAbort = { viewModel.close() }` into
`TestQuestionPanel(...)`.

**`ui/viewmodels/TestViewModel.kt`:** `close()` already nulls `_activeTest` + cancels the timer, so the
screen falls back to `TestPicker` (it shows when `activeTest == null && !finished`). Defensively also set
`_revealed.value = false`, `_selectedOptionId.value = null`, `_finished.value = false` inside `close()` so a
later picker→start is clean. (No other logic change.)

## B. Strings — add to **all five** `res/values*/strings.xml` (after `test_finish`, ~line 195)

Reuse existing `cd_cancel` ("Cancel") for the dialog's dismiss — only two new keys. **No `%…$` placeholders**,
so no .NET-`{0}`→Android positional gotcha here.

| key | values/ (en) | values-ru/ | values-zh/ | values-es/ | values-hi/ |
|---|---|---|---|---|---|
| `test_abort` | Abort test | Прервать тест | 中止测试 | Cancelar prueba | परीक्षण रद्द करें |
| `test_abort_confirm` | Abort the test? Your progress will be lost. | Прервать тест? Прогресс будет потерян. | 确定中止测试吗？您的进度将丢失。 | ¿Cancelar la prueba? Se perderá tu progreso. | परीक्षण रद्द करें? आपकी प्रगति खो जाएगी। |

---

## C. Remove the red ✗ on the Testing Next button

Today (`TestComponents.kt`, `TestQuestionPanel`, the `if (revealed)` Next `Button`): the button is coloured
green when the pick was correct and **red** when wrong, with `Icons.Default.Check` vs **`Icons.Default.Close`**
(the red ✗) inside it. Windows now shows a green ✓ only on a correct answer and **nothing** on a wrong one
(wrong/right is already conveyed by the coloured option text + the comment block). Mirror that:

- Correct (`isCorrectSelection == true`) → keep the green button (`containerColor = Positive`) with the
  `Icons.Default.Check` ✓ icon.
- Wrong → **neutral button** (default `ButtonDefaults.buttonColors()` — i.e. the theme's green primary) with
  **no icon** (drop the `Icons.Default.Close` branch). No red, no ✗.

So the `Icon(...)` is rendered only `if (isCorrectSelection)`, and the `containerColor` is
`if (isCorrectSelection) Positive else <default>`. `Icons.Default.Close` import can be removed if now unused.
`ExamQuestionPanel`'s Next button has no verdict icon already — leave it.

---

## Phases

1. **Tokens** — add `Positive`/`Negative` to `Color.kt`.
2. **A — recolour** `TestComponents.kt` (both panels) + `ExaminationScreen.kt` per the tables.
3. **C — verdict** — green ✓ only / drop red ✗ in the Testing Next button.
4. **B — abort** — strings (×5 locales), `onAbort` + confirm dialog in `TestQuestionPanel`, wire
   `viewModel.close()` in `TestActiveView`, defensive resets in `close()`.
5. Build (`./gradlew :app:assembleDebug`) + spot-check all five languages.

## Verification

- **Testing**: question body reads in dark primary text (no blue, no green); title + correct option +
  comment header are green; counter is slate; countdown is red. Answer **wrong** → no red ✗ on the Next
  button (neutral button), wrong option text is red; answer **right** → green ✓ + green button. An **Abort
  test** button shows before and after answering; tapping it confirms, then returns to the test picker with
  no lingering state.
- **Examination**: same panel palette; the **Результаты**/grading breakdown uses the muted `Positive`/
  `Negative` (no bright lime/tomato); in-progress/unanswered is slate.
- All five locales (`en/ru/zh/es/hi`) render the new abort strings; everything else unchanged.

## Out of scope / notes

- **No** Examination abort button (Windows added abort to Testing only; flagged as a possible follow-up).
- Android keeps its **bordered/filled option rows** (Windows options are plain coloured text) — that's an
  existing platform idiom; just route its colours through tokens, don't restructure.
- No domain/data/JSON/grading changes. The confirm-before-abort guard matches Windows; if the customer wants
  instant abort, drop the `AlertDialog` and call `onAbort()` directly.

> Companion Windows work is recorded in the port's memory (`theme-system-2026-06`,
> `testing-screen-quiz-2026-06`). Sibling plans: `2026-06-android-testing-system-parity.md` (built the
> screens), `2026-06-android-green-theme-parity.md` (the broader blue→green sweep this refines).
