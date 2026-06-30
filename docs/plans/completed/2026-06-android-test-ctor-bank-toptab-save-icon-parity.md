# Test Constructor: move the "Question Bank" toggle to the app top bar + make Save an icon button (Android)

**Status:** completed
**Owner:** AI Assistant
**Started:** 2026-06-29
**Finished:** 2026-06-29
**Direction:** **Windows → Android.** Two small Test-Constructor UI tweaks were made first in the
WinUI 3 port (`CardioSimulatorWin`). This plan mirrors them on Android so the Test Constructor reads
the same on both platforms.

**Target (Android) source root:** `E:\VLN_Project\CardioSimulator\app\src\main\java\com\example\cardiosimulator\`
**Reference (Windows) source root:** `E:\VLN_Project\CardioSimulatorWin\src\`

---

## The two Windows changes (reference)

### Change A — "Question Bank" toggle moved from the screen into the **app top bar**
The Test Constructor has a two-way view toggle: **Tests** ↔ **Question Bank**. On Windows both
buttons used to sit in the screen's own top toolbar. The **Question Bank** button was moved out of
the screen and into the **app top control panel**, beside the operating-mode selector. The **Tests**
button stays in the screen. Clicking "Question Bank" in the top bar switches the screen to the bank
view; the in-screen "Tests" button switches back. The active toggle is still highlighted in sync.

Windows files:
- `CardioSimulator.App/Screens/TestConstructorScreen.cs` — the bank toggle (`_bankViewBtn`) is still
  created and click-wired in the screen, but **no longer added to the screen toolbar**; only the
  "Tests" button + divider remain there. A new public property exposes the button so the shell can
  host it:
  ```csharp
  // TestConstructorScreen.cs
  public UIElement QuestionBankButton => _bankViewBtn;   // parented by the app top bar
  ```
  The toggle highlighting (`ShowView`) sets the active button's font weight regardless of where it is
  parented, so the split across two parents stays in sync.
- `CardioSimulator.App/Controls/TopControlPanel.xaml.cs` — new method to drop a mode-specific element
  into the top bar's existing per-mode content slot (`SubPanelHost`):
  ```csharp
  public void SetSubPanel(UIElement? content) => SubPanelHost.Content = content;
  ```
- `CardioSimulator.App/Screens/MainScreen.xaml.cs` — in the `TestConstructor` mode branch, after
  building the screen: `Top.SetSubPanel(testCtor.QuestionBankButton);`

### Change B — the test's **Save** button became an **icon button**
In the Test editor toolbar, the "Save" text button is now an icon button (floppy-disk glyph) with a
tooltip carrying the original label.

Windows file — `CardioSimulator.App/Screens/TestConstructorScreen.cs`:
```csharp
// before
_saveBtn = new Button { Content = AppStrings.TestCtorSave, IsEnabled = false };
// after
_saveBtn = new Button { Content = new SymbolIcon(Symbol.Save), IsEnabled = false };
ToolTipService.SetToolTip(_saveBtn, AppStrings.TestCtorSave);
```

---

## ⚠️ Layout divergence — read before porting

The two ports lay the Test Constructor out **differently**, so this is an *intent* port, not a
line-for-line copy:

| | Windows | Android |
|---|---|---|
| View toggle | Two **buttons** ("Tests" / "Bank") in a horizontal **toolbar** across the top of the screen | A **`TabRow`** with two `Tab`s (`ConstructorTab.TEST` / `ConstructorTab.BANK`) at the top of the **right-hand editor column** (`TestConstructorScreen.kt:87-98`) |
| Save / Delete | In the same top toolbar | Two full-width weighted `Button`s in a Row at the **bottom** of `TestEditor` (`TestConstructorScreen.kt:227-239`) |
| App top bar per-mode slot | `TopControlPanel.SubPanelHost` (`ContentControl`) | `TopControlPanel.kt` — the `when (selectedOperatingMode.id) { … }` Box, currently `OperatingMode.TestConstructor -> {}` (empty) — `TopControlPanel.kt:119` |

The goal is the same end-state on Android: the **Банк** toggle lives in the app top bar beside the
mode pill; the **Тест** toggle stays in the screen; **Save** is an icon button.

---

## Android plan

### Change A — move the Банк (Question Bank) toggle into `TopControlPanel`

**A1. Give `TopControlPanel` access to the Test-Constructor tab state.**
`ui/panels/TopControlPanel.kt` — add a nullable parameter (matching the existing
`constructorViewModel` / `courseConstructorViewModel` pattern):
```kotlin
fun TopControlPanel(
    viewModel: AppViewModel,
    monitorViewModel: MonitorViewModel = viewModel(),
    rhythmViewModel: RhythmViewModel? = null,
    constructorViewModel: ConstructorViewModel? = null,
    courseConstructorViewModel: CourseConstructorViewModel? = null,
    courseViewerViewModel: CourseViewerViewModel? = null,
    testConstructorViewModel: TestConstructorViewModel? = null,   // NEW
    modifier: Modifier = Modifier,
    onStartStopClick: (Boolean) -> Unit = {},
)
```
(Add the `import …ui.viewmodels.TestConstructorViewModel`.)

**A2. Fill the empty `TestConstructor` branch with the Банк toggle.**
`TopControlPanel.kt` — the `when` block currently has `OperatingMode.TestConstructor -> {}`
(`:119`). Replace it with a single toggle built from the shared `Tab` composable
(`ui/components/Tab.kt` — it already has `isActive` for the green-pill active state), wired to the
tab StateFlow:
```kotlin
OperatingMode.TestConstructor -> {
    if (testConstructorViewModel != null) {
        val activeTab by testConstructorViewModel.activeTab.collectAsState()
        Tab(
            text = stringResource(R.string.test_ctor_tab_bank),
            isActive = activeTab == ConstructorTab.BANK,
            onClick = { testConstructorViewModel.setTab(ConstructorTab.BANK) },
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}
```
(Import `ConstructorTab` and `R`.) `setTab` / `activeTab` / `ConstructorTab { TEST, BANK }` already
exist on `TestConstructorViewModel` (`TestConstructorViewModel.kt:17,25-26,59`).

**A3. Pass the view model in from `MainScreen`.**
`ui/screens/MainScreen.kt` — the `testConstructorViewModel` is already created (`:197`) and handed to
`TestConstructorScreen` (`:309-314`). Add it to the `TopControlPanel(...)` call (`:257-271`):
```kotlin
TopControlPanel(
    viewModel = appViewModel,
    monitorViewModel = monitorViewModel,
    rhythmViewModel = rhythmViewModel,
    constructorViewModel = constructorViewModel,
    courseConstructorViewModel = courseConstructorViewModel,
    courseViewerViewModel = courseViewerViewModel,
    testConstructorViewModel = testConstructorViewModel,   // NEW
    onStartStopClick = { … }
)
```

**A4. Drop the Банк tab from the screen's `TabRow` (keep only Тест).**
`ui/screens/TestConstructorScreen.kt:87-98` — the two-tab `TabRow` becomes a single "Тест" toggle.
**Do not** keep a two-`Tab` `TabRow` and just delete one `Tab`: `selectedTabIndex = activeTab.ordinal`
(`:87`) would index out of range when `activeTab == BANK` (ordinal 1, but only one tab). Replace it
with a single control that is selected only for TEST, e.g.:
```kotlin
// was: TabRow(selectedTabIndex = activeTab.ordinal) { Tab(TEST){…}; Tab(BANK){…} }
Tab(
    text = stringResource(R.string.test_ctor_tab_test),
    isActive = activeTab == ConstructorTab.TEST,
    onClick = { testConstructorViewModel.setTab(ConstructorTab.TEST) },
    modifier = Modifier.padding(8.dp)
)
```
The body switch is unchanged — `if (activeTab == ConstructorTab.TEST) TestEditor(...) else BankEditor(...)`
(`:100-104`) — the body still flips to `BankEditor` because the **top-bar** Банк toggle calls
`setTab(BANK)`. (If you prefer to keep `TabRow` for styling, guard the index:
`selectedTabIndex = if (activeTab == ConstructorTab.TEST) 0 else 0` with a single child, but the lone
`Tab` above is the faithful match for Windows' lone "Tests" button.)

> Net behaviour: top bar shows **[ Mode ▾ ] [ Банк ]** (Банк highlighted when on the bank view); the
> screen shows the **Тест** toggle (highlighted on the test view). Clicking Банк → bank view; clicking
> Тест → test view. Exactly mirrors Windows.

### Change B — make the test's Save button an icon button
`ui/screens/TestConstructorScreen.kt` — in `TestEditor`, the bottom Save/Delete row (`:227-239`).
Convert **Save** from a text `Button` to an `IconButton` with `Icons.Default.Save` and a
`contentDescription` (the file already imports `androidx.compose.material.icons.filled.*` and uses
this exact pattern for "new test" at `:163-165`):
```kotlin
// was:
//   Button(onClick = { viewModel.saveTest() }, modifier = Modifier.weight(1f)) {
//       Text(stringResource(R.string.test_ctor_save))
//   }
IconButton(onClick = { viewModel.saveTest() }) {
    Icon(Icons.Default.Save, contentDescription = stringResource(R.string.test_ctor_save))
}
```
Since Save no longer needs `weight(1f)`, adjust the Row so the icon isn't stretched: drop Save's
`weight` and let **Delete** keep its weighted text button (or arrange the Row with the icon at the
start and the Delete button taking the remaining width). Keep Delete a text button — only **Save**
becomes an icon, matching the Windows change. `contentDescription` preserves the label for
TalkBack/accessibility (the Android analog of the Windows tooltip).

---

## Verification
Build: `./gradlew :app:assembleDebug`. Then, in the Test Constructor mode:

| Check | Expected |
|---|---|
| Top bar | Shows the mode pill **and** a **Банк** toggle beside it (top bar was previously empty in this mode) |
| Банк toggle | Highlighted (green/active) when the bank view is showing; clicking it switches the right panel to the bank list |
| Тест toggle (in screen) | Highlighted when the test editor is showing; clicking it switches back from the bank view |
| Toggle sync | Switching via either control updates both highlights consistently; no out-of-range `TabRow` crash |
| Save button | Test editor's Save is now a **floppy-disk icon button**; long-press/TalkBack reads the "Save" label; tapping it still saves the test |
| Other modes | Top bar unchanged in Teaching/Constructor/etc. (the new branch only fires for `TestConstructor`) |

## Acceptance checklist
- [x] `TopControlPanel` takes `testConstructorViewModel` and renders the **Банк** toggle in the
      `OperatingMode.TestConstructor` slot, `isActive` bound to `activeTab == BANK`.
- [x] `MainScreen` passes `testConstructorViewModel` into `TopControlPanel`.
- [x] Screen `TabRow` reduced to a single **Тест** toggle (no `selectedTabIndex` out-of-range path);
      body still switches on `activeTab`.
- [x] Test editor **Save** is an `IconButton` (`Icons.Default.Save`) with a `contentDescription`;
      Delete unchanged; row layout no longer stretches the icon.
- [x] No change to `TestConstructorViewModel` logic, repositories, strings, or the bank/import/export
      flows.

## Out of scope / do NOT do
- Don't touch the **Bank** editor's own controls (search, theme tabs, import/export, per-question
  cards) — only the **top-level Tests/Bank toggle** placement changes.
- Don't move Delete, or iconize any button other than the test **Save**.
- Don't add new string resources — reuse `test_ctor_tab_bank` / `test_ctor_tab_test` /
  `test_ctor_save`.

---

> Windows source of this sync (no separate `docs/plans/complete` doc — changes were applied directly):
> `CardioSimulatorWin/src/CardioSimulator.App/Screens/TestConstructorScreen.cs`
> (`QuestionBankButton` property + `_saveBtn` icon), `…/Controls/TopControlPanel.xaml.cs`
> (`SetSubPanel`), `…/Screens/MainScreen.xaml.cs` (`Top.SetSubPanel(testCtor.QuestionBankButton)`).
> Related Android sync plans: `2026-06-android-top-panel-size-height-parity.md`,
> `2026-06-android-testing-system-parity.md`.
