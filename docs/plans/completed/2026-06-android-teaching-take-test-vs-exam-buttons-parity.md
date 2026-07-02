# Android parity — Teaching lecture bar: fix "Take the test" → Testing, add "Take the exam" → Examination

**Status:** active
**Owner:** Android port
**Started:** 2026-06-30
**Related issues / PRs:** Windows fix in
`CardioSimulatorWin/src/CardioSimulator.App/Controls/CourseViewerPanel.cs` (button wiring +
`SwitchToMode` helper) and `.../Localization/AppStrings.cs` (new `teaching_take_exam` string in
all 5 locales).

## Goal

In the Teaching screen's lecture view, the lecture top-bar has a **"Take the test"** button
(`R.string.teaching_take_test`) whose label means *Testing* (ru "Пройти тестирование", zh "进行测试"…)
— but it is wired to open **`OperatingMode.Examination`**, the graded exam flow. The label and the
destination disagree.

Port the Windows fix:

1. **Re-wire "Take the test" → `OperatingMode.Testing`** (the self-assessment flow it's named for).
2. **Add a second button, "Take the exam" → `OperatingMode.Examination`**, so the graded exam still
   has an entry point from the lecture.
3. **Add a new string `teaching_take_exam`** in every locale.

Small, self-contained UI + string change. Do it now while the matching Windows change is fresh.

## Current state

`app/src/main/java/com/example/cardiosimulator/ui/screens/TeachingScreen.kt`, the lecture top bar
`Row` (≈ lines 581–602):

```kotlin
TextButton(onClick = {
    appViewModel.updateOperatingMode(
        appViewModel.operatingModes.find { it.id == OperatingMode.Examination }!!   // ← wrong: label says "test"
    )
}) {
    Icon(Icons.Default.Quiz, contentDescription = null)
    Spacer(modifier = Modifier.width(8.dp))
    Text(stringResource(R.string.teaching_take_test))
}
Spacer(modifier = Modifier.width(8.dp))
IconButton(onClick = onMonitorClick) { Icon(imageVector = Icons.Default.MonitorHeart, …) }
```

- `OperatingMode.Testing` and `OperatingMode.Examination` both exist
  (`domain/OperatingModeModel.kt:8-9`) and both are registered in `appViewModel.operatingModes`,
  so `.find { it.id == … }!!` resolves for either — no VM/enum change needed.
- The `Row` uses `horizontalArrangement = Arrangement.End`, so children pack to the **right**.
  Adding the exam button as a sibling before the monitor `IconButton` keeps everything right-aligned;
  insert a `Spacer(Modifier.width(8.dp))` between the two text buttons.
- `Icons.Default.School` is **already imported** (`TeachingScreen.kt:26`) — reuse it for the exam
  button so no new icon import is required. (`Icons.Default.Quiz` stays on the test button.)

### String resources

`teaching_take_test` is **not present in every locale** — a pre-existing gap:

| locale | file | line | `teaching_take_test` |
|---|---|---|---|
| default (en) | `app/src/main/res/values/strings.xml` | 237 | `Take a Test` |
| ru | `app/src/main/res/values-ru/strings.xml` | 235 | `Пройти тестирование` |
| hi | `app/src/main/res/values-hi/strings.xml` | 237 | `टेस्ट दें` |
| zh | `app/src/main/res/values-zh/strings.xml` | — | **missing** (falls back to en "Take a Test") |
| es | `app/src/main/res/values-es/strings.xml` | — | **missing** (falls back to en "Take a Test") |

`mode_testing` / `mode_examination` exist in all five locales (lines 6–7 of each file).

## Non-goals

- **Do not change the existing `teaching_take_test` wording.** Android's en value is `Take a Test`
  (Windows says "Take the test"); leave Android's as-is — only the *destination* of the button is wrong,
  not the label.
- No `OperatingMode` enum, `AppViewModel`, or navigation-plumbing changes — both modes already exist
  and are registered.
- No monitor-button, lecture-web-view, or rhythm-dropdown changes.

## Plan

### Phase 1 — Re-wire + add the exam button (`TeachingScreen.kt`)

1. In the existing `TextButton`, change the lookup from `OperatingMode.Examination` to
   **`OperatingMode.Testing`**. Keep `Icons.Default.Quiz` + `R.string.teaching_take_test`.
2. Immediately after that button, add `Spacer(Modifier.width(8.dp))` then a second `TextButton`:

   ```kotlin
   Spacer(modifier = Modifier.width(8.dp))
   TextButton(onClick = {
       appViewModel.updateOperatingMode(
           appViewModel.operatingModes.find { it.id == OperatingMode.Examination }!!
       )
   }) {
       Icon(Icons.Default.School, contentDescription = null)
       Spacer(modifier = Modifier.width(8.dp))
       Text(stringResource(R.string.teaching_take_exam))
   }
   ```

   Place it **before** the existing `Spacer` + monitor `IconButton` so order reads
   *Take the test · Take the exam · [monitor]*, all right-packed.

### Phase 2 — Add the `teaching_take_exam` string in all 5 locales

Add next to each `teaching_take_test` entry (and create one in zh/es even though `teaching_take_test`
is absent there — see Phase 3 note):

| locale | file | new value |
|---|---|---|
| default (en) | `app/src/main/res/values/strings.xml` | `Take the exam` |
| ru | `app/src/main/res/values-ru/strings.xml` | `Сдать экзамен` |
| zh | `app/src/main/res/values-zh/strings.xml` | `参加考试` |
| es | `app/src/main/res/values-es/strings.xml` | `Hacer el examen` |
| hi | `app/src/main/res/values-hi/strings.xml` | `परीक्षा दें` |

These match the Windows `AppStrings` values verbatim.

### Phase 3 — (Optional) Backfill the missing `teaching_take_test` in zh/es

Pre-existing bug surfaced while doing this: zh/es lack `teaching_take_test`, so those users see the
English "Take a Test". Cheap to fix in the same PR — add:

| locale | file | value |
|---|---|---|
| zh | `app/src/main/res/values-zh/strings.xml` | `进行测试` |
| es | `app/src/main/res/values-es/strings.xml` | `Hacer la prueba` |

(Windows zh/es values for `teaching_take_test`.) If the reviewer wants to keep the PR strictly scoped
to the exam button, drop Phase 3 — it does not affect the new behaviour, only the existing button's
localization.

## Risks & open questions

- **Icon choice.** `Icons.Default.School` (already imported) reads as a graded/exam action and avoids a
  new import. Swap to `Icons.Default.Grading`/`Assignment` if a reviewer prefers — would add one import.
- **Translation wording.** Exam-button values are the Windows choices verbatim; adjust if a clinician
  reviewer prefers alternate zh/es/hi phrasing (key name stays `teaching_take_exam`).

## Verification

- Build: `./gradlew assembleDebug` (string + Compose edits; confirm no malformed XML).
- `grep -rn 'teaching_take_exam' app/src/main/res/values*/strings.xml` → present in all 5 locales.
- Manual: open Teaching → select a lecture. The lecture bar shows **two** text buttons, "Take the test"
  and "Take the exam", plus the monitor icon. "Take the test" opens the **Testing** screen; "Take the
  exam" opens the **Examination** screen. Switch app language and confirm both labels localize.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Teaching lecture bar: Take-the-test → Testing, add Take-the-exam → Examination | 1–2 | one-line re-wire + new TextButton + `teaching_take_exam` ×5 |
| 1 | (same PR) backfill `teaching_take_test` for zh/es | 3 | optional; drop if keeping PR scoped |

---

## Outcome

- **Result:** shipped
- **PRs:** N/A (applied directly)
- **Deviations from plan:** none
- **Follow-ups spawned:** none
