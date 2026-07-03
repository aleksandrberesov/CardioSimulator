# Plan: "Все ок" (All OK) becomes a toggle in the Electrodes dialog (Android parity)

**Created:** 2026-07-02
**Status:** ACTIVE
**Direction:** **Windows → Android** (the usual). Built in the WinUI 3 port first from customer
feedback; Android must catch up. The Windows port is the **reference implementation** — match its
behaviour, adapting to Compose idioms.

**Target (Android) source root:** `E:\VLN_Project\CardioSimulator\app\src\main\java\com\example\cardiosimulator\`
**Reference (Windows) source root:** `E:\VLN_Project\CardioSimulatorWin\src\`

## Goal

Customer feedback (verbatim): *"При повторном нажатии на «все ок» – она перестаёт гореть синим, а сама
кнопка «электроды» – перестаёт гореть зелёным."*

Make the **"Все ок" (All OK)** button in the Электроды dialog a **toggle**: tapping it while it is
already the confirmed choice must **deselect it** (blue highlight off) and return the monitor's
**Электроды** tab to **neutral** (green off) — i.e. clear the hookup back to the neutral/unset state.
Today the three state buttons are a one-way, always-one-selected picker, so once a student makes any
choice there is no way back to the pristine unset state.

Two consequences that come with this (both intentional, both already shipped on Windows):
1. **When the hookup is unset, NO button is highlighted** and the caption is blank — matching the
   neutral tab. (Previously the "Все ок" button showed selected on open because `electrodeState`
   defaults to `Ok`.)
2. Only **"Все ок" toggles-off.** Swapped/Displacement stay select-only (the feedback named only
   "все ок"). Selecting "Все ок" while a fault is active still clears the fault → tab green; a further
   "Все ок" tap then clears to neutral.

## Reference (Windows, done)

- `CardioSimulator.App/ViewModels/MonitorViewModel.cs` — new `ClearElectrodeState()` resets to the
  neutral/unset state (`ElectrodeStateUserSet = false`, then `ElectrodeState = Ok`).
- `CardioSimulator.App/Controls/ElectrodesDialog.cs` — the dialog tracks a local `userSet` mirroring
  the VM flag; `IsActive(state) => userSet && selected == state` gates every visual (button fill,
  RA/LA dot swap, precordial dim, caption). `Toggle(state)`: if `state == Ok && IsActive(Ok)` →
  `ClearElectrodeState()`; else `SetElectrodeState(state)`.
- The monitor's Электроды tab tri-state highlight is unchanged — it already reads the "user-set" flag,
  so clearing the flag makes it go neutral automatically.

## Current state (Android, to change)

- **Dialog** `ui/dialogs/ElectrodesDialog.kt` — fully **state-hoisted / stateless**. Signature:
  `ElectrodesDialog(electrodeState: ElectrodeState, onSelectState: (ElectrodeState) -> Unit,
  onDismiss: () -> Unit)`. All visuals are derived directly from `electrodeState`:
  - `StateButton(..., selected = electrodeState == ElectrodeState.Ok/Swapped/Displacement, ...)`
    (lines ~120–137).
  - RA/LA dot colours from `electrodeState == Swapped` (lines ~95–96).
  - Precordial `Column` `Modifier.alpha(if (electrodeState == Displacement) 0.45f else 1f)` (line ~107).
  - `captionRes` `when (electrodeState) { Ok/Swapped/Displacement }` (lines ~140–150).
- **VM** `ui/viewmodels/MonitorViewModel.kt:224` —
  `fun setElectrodeState(state) { _monitorMode.update { it.copy(electrodeState = state, electrodeStateUserSet = true) } }`.
- **Model** `domain/MonitorModeModel.kt:74–75` — `electrodeState: ElectrodeState = Ok`,
  `electrodeStateUserSet: Boolean = false`.
- **Call site** `ui/screens/TeachingScreen.kt:253–259` —
  `ElectrodesDialog(electrodeState = mode.electrodeState, onSelectState = { monitorViewModel.setElectrodeState(it) }, onDismiss = { monitorViewModel.setShowElectrodes(false) })`.
- **Tab highlight** `ui/panels/MonitorControlPanel.kt:~222–228` — already
  `isActive = monitorMode.electrodeStateUserSet`, `activeColor = if (fault) ElectrodeFaultRed else AccentGreen`.
  **No change needed here** — resetting the flag returns the tab to neutral for free.

## Non-goals

- No new strings, drawables, or colours.
- Do **not** make Swapped/Displacement toggle-off.
- Do **not** touch the monitor tab highlight logic in `MonitorControlPanel.kt` — it already keys off
  `electrodeStateUserSet`.
- Do **not** persist electrode state (it already isn't; a fresh session starts unset/correctly-wired).

## Implementation steps

### 1. VM: add `clearElectrodeState()` (`MonitorViewModel.kt`, right after `setElectrodeState`, ~line 226)

```kotlin
/** Clears the electrode hookup back to the neutral/unset state (default OK wiring, no fault), as if
 *  the Электроды window had never been used. Backs the "Все ок" toggle: a second tap turns the blue
 *  highlight off and returns the Electrodes tab to neutral. */
fun clearElectrodeState() {
    _monitorMode.update { it.copy(electrodeState = ElectrodeState.Ok, electrodeStateUserSet = false) }
}
```

> Import note: reuse the same reference style already in the file (`setElectrodeState` uses the
> fully-qualified `com.example.cardiosimulator.domain.ElectrodeState`). Either fully-qualify here too
> or add the import — match whatever the file already does.

### 2. Dialog: gate visuals on a new `userSet` flag + add an `onClearState` callback (`ElectrodesDialog.kt`)

Change the signature:

```kotlin
@Composable
fun ElectrodesDialog(
    electrodeState: ElectrodeState,
    userSet: Boolean,
    onSelectState: (ElectrodeState) -> Unit,
    onClearState: () -> Unit,
    onDismiss: () -> Unit
) {
```

Then replace every `electrodeState == ElectrodeState.X` visual predicate with one gated by `userSet`.
Add a local helper at the top of the content for clarity:

```kotlin
fun isActive(state: ElectrodeState) = userSet && electrodeState == state
```

- RA/LA dot colours (lines ~95–96):
  ```kotlin
  val raColor = if (isActive(ElectrodeState.Swapped)) Color(0xFFFDD835) else Color(0xFFE53935)
  val laColor = if (isActive(ElectrodeState.Swapped)) Color(0xFFE53935) else Color(0xFFFDD835)
  ```
- Precordial dim (line ~107):
  ```kotlin
  .alpha(if (isActive(ElectrodeState.Displacement)) 0.45f else 1f)
  ```
- The three `StateButton` calls — set `selected = isActive(...)`, and make **only the Ok button** toggle:
  ```kotlin
  StateButton(
      stringResource(R.string.electrodes_state_ok),
      selected = isActive(ElectrodeState.Ok),
      onClick = { if (isActive(ElectrodeState.Ok)) onClearState() else onSelectState(ElectrodeState.Ok) },
      modifier = Modifier.weight(1f)
  )
  StateButton(
      stringResource(R.string.electrodes_state_swapped),
      selected = isActive(ElectrodeState.Swapped),
      onClick = { onSelectState(ElectrodeState.Swapped) },
      modifier = Modifier.weight(1f)
  )
  StateButton(
      stringResource(R.string.electrodes_state_displacement),
      selected = isActive(ElectrodeState.Displacement),
      onClick = { onSelectState(ElectrodeState.Displacement) },
      modifier = Modifier.weight(1f)
  )
  ```
- Caption (lines ~140–150): show nothing when unset. Keep `minLines = 2` so the layout height doesn't
  jump:
  ```kotlin
  val captionText = if (!userSet) "" else when (electrodeState) {
      ElectrodeState.Ok -> stringResource(R.string.electrodes_state_caption_ok)
      ElectrodeState.Swapped -> stringResource(R.string.electrodes_state_caption_swapped)
      ElectrodeState.Displacement -> stringResource(R.string.electrodes_state_caption_displacement)
  }
  Text(text = captionText, style = MaterialTheme.typography.bodySmall, color = TextSecondary, minLines = 2)
  ```
  (`stringResource` must be read outside the `if`/`when` composable-conditionally? No — it's fine
  inside `when` branches here because each branch is evaluated as a normal `@Composable` call.
  Alternatively compute a `captionRes: Int?` and read the string once — either is acceptable.)

### 3. Call site: pass the flag + clear callback (`TeachingScreen.kt:253–259`)

```kotlin
if (mode.showElectrodes) {
    ElectrodesDialog(
        electrodeState = mode.electrodeState,
        userSet = mode.electrodeStateUserSet,
        onSelectState = { monitorViewModel.setElectrodeState(it) },
        onClearState = { monitorViewModel.clearElectrodeState() },
        onDismiss = { monitorViewModel.setShowElectrodes(false) }
    )
}
```

> Search the repo for any **other** `ElectrodesDialog(` call sites before finishing — at time of writing
> `TeachingScreen.kt` is the only one, but the OSCE/Testing/Examination screens also host the monitor,
> so grep `ElectrodesDialog(` to be sure every caller is updated (a missed one won't compile).

## Intentional divergences to flag in review

1. **No local mirror-state / imperative `Render()`.** Windows' `ElectrodesDialog.cs` keeps a mutable
   local `selected`/`userSet` and re-applies visuals imperatively (WinUI). The Android dialog stays
   **fully state-hoisted** — Compose recomposes from `mode.electrodeState` + `mode.electrodeStateUserSet`
   automatically. Functionally identical, idiomatic per platform.
2. **No "flag-first" ordering.** Windows' `ClearElectrodeState()` deliberately sets the flag before the
   model to avoid a two-write green-flash on the tab. Android's `clearElectrodeState()` sets **both
   fields in one atomic `_monitorMode.update { it.copy(...) }`**, so there is no intermediate state and
   no flash — the Windows ordering comment does **not** apply.
3. **Fresh-open shows no highlighted button** (was: "Все ок" selected on open). Intentional, matches the
   neutral tab — do **not** "restore" it as a regression.

## Verification

- Build: from `E:\VLN_Project\CardioSimulator`, `./gradlew :app:assembleDebug` (0 warnings/errors).
- Manual (Teaching-mode monitor → Электроды):
  - Fresh open → no button highlighted, caption blank, tab neutral.
  - Tap **Все ок** → button blue + tab **green** + OK caption.
  - Tap **Все ок** again → button blue **off** + tab **neutral** + caption blank. ✅ (the fix)
  - **Swapped** → button blue + RA/LA dots swap + tab **red**; **Все ок** → clears fault, tab green;
    **Все ок** again → neutral.
  - **Displacement** → precordial rows dim + tab red; same clear path.

## Related

- Builds on `docs/plans/active/2026-06-android-electrode-button-highlight-parity.md` (the tri-state tab
  highlight this toggle drives). Windows memory: `electrodes-allok-toggle-2026-07`.
