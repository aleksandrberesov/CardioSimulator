# Rhythm picker consolidation — Android parity

**Status:** completed
**Owner:** (unassigned)
**Started:** 2026-07-08 · **Widened:** 2026-07-09 (was "Test Constructor ECG grouped picker")
**Related:** Windows implementation (source of truth) in `CardioSimulatorWin` — the new
`Controls/RhythmPickerButton.cs` + every rhythm selector routed through `RhythmChoosingPanel`.

## Goal

Unify **every** rhythm/pathology selector in the Android app onto the one grouped-and-searchable
`RhythmSelector` (category groups, subgroups, search, clinical / group-vs-A–Z / expand / collapse
buttons) — mirroring the Windows consolidation that replaced its assortment of flat combos / menus /
lists. No screen should present a flat, ungrouped rhythm list anymore.

## Windows source (what shipped there)

All Windows rhythm pickers now use the grouped `RhythmChoosingPanel` via two hosts:
- **Drawer / dialog:** embed the panel (Teaching drawer, Constructor drawer, DataSource dialog,
  ComparisonTargetDialog, HtmlBlockEditor `<ecg>` picker).
- **Inline field:** new `Controls/RhythmPickerButton.cs` — a button (localized name) that opens a
  flyout hosting the panel; `ShowClearButton` (✕ → None), `Filter` (exclude items), commit on the
  panel's new `RhythmInvoked` (explicit tap only). Enablers added to `RhythmChoosingPanel`:
  `ShowPinButton` (hide pin outside the drawer) + `RhythmInvoked`.
- Sites converted on Windows: Test Ctor ECG stimulus, Test Ctor assemble target + distractor, OSKE
  constructor ECG box, OSKE exam start-dialog ECG box, ComparisonTargetDialog, HtmlBlockEditor
  `<ecg>` picker, Teaching top-bar rhythm flyout.

## Android current state (the important part)

Android is **already half-consolidated** — it has one shared `RhythmSelector`
(`ui/panels/RhythmSelector.kt`) and several sites already use it. So this is mostly converting the
*remaining* flat lists, **not** building new infrastructure.

- **`RhythmSelector`** reads its grouped/clinical/collapsed/pin state from `AppViewModel` and fires
  `onRhythmSelect(entry)`. Precedent for embedding in a dialog: `DataSourceScreen.kt:153-168`.
- **No `RhythmPickerButton` class is needed on Android.** The Windows control is just "a button that
  opens a flyout hosting the panel"; in Compose that idiom is already a `Tab`/`OutlinedCard` +
  `Dialog { RhythmSelector(...) }`. Reuse it — do not port a new widget class.

Inventory of sites and current state:

| Site | Android file:line | Now | Action |
|---|---|---|---|
| Teaching top-bar rhythm tab | `TeachingControlPanel.kt:110-133` | **already `RhythmSelector`** in a `Dialog` | verify + add `showPinButton=false` |
| Compare-target dialog | `ComparisonTargetDialog.kt:100-124` | flat `LazyColumn` of `titleEn`/`nameRu` | → `RhythmSelector` |
| Course `<ecg>` embed | `HtmlBlockEditor.kt:286-298` | **uses `ComparisonTargetDialog`** | covered by the compare-dialog fix |
| Test Ctor ECG stimulus | `TestConstructorScreen.kt:447-478` | flat `ExposedDropdownMenu` | → dialog + `RhythmSelector` |
| OSKE constructor ECG | `OskeConstructorScreen.kt:74-91` | flat `rhythms.forEach{ RadioButton }` | → `RhythmSelector` |
| OSKE exam start dialog ECG | `OSKEScreen.kt:241-253` (`OskeStartDialog`) | flat `filteredRhythms.forEach{ RadioButton }` | → `RhythmSelector` (subset) |
| Test Ctor **assemble** target/distractor | — | **does not exist on Android** | N/A (see non-goals) |
| Data-source details dialog | `DataSourceScreen.kt:157` | already `RhythmSelector` | no change |

## Key divergences (read before coding)

1. **Shared toggle state.** `RhythmSelector`'s grouped/clinical/collapsed/pin state lives in
   `AppViewModel` (global), so every embedding shares it with the Teaching drawer. Accept it — it's
   the existing behaviour and the DataSource dialog already shares it.
2. **`showPinButton`, not `RhythmInvoked`.** Port only the `showPinButton` toggle (Phase 1). The
   Windows `RhythmInvoked` event exists purely so a *flyout* doesn't close on filter-auto-select;
   Android embeds `RhythmSelector` in dialogs/columns and commits on `onRhythmSelect`, so there is
   nothing to spuriously close in the embedded (non-auto-closing) cases. Do **not** add an
   invoked-vs-selected split.
   - *Caveat for the auto-closing cases* (Teaching tab, and the Test-Ctor dialog if it auto-closes):
     `RhythmSelector.kt:189-193` fires `onRhythmSelect` on the clinical-mode auto-select, which would
     commit+close. The Teaching tab already lives with this; keep parity (don't fix here) or, if
     desired, don't auto-close on select and add a Done button (the compare/OSKE dialogs already use
     an explicit Done/Start button, so they're immune).
3. **Nested-scroll gotcha.** `RhythmSelector` lays its list out with `fillMaxHeight().weight(1f)`, so
   it needs a **bounded height** and must **not** sit inside a parent `Column(verticalScroll(...))`.
   Both OSKE screens currently wrap their ECG list in a scrolling Column — give the embedded
   `RhythmSelector` a fixed height (e.g. `Modifier.height(320.dp)`) and keep it out of the outer
   scroll (or drop the outer scroll for that pane). The compare dialog and Teaching dialog already
   give it bounded height (`fillMaxHeight`, `fillMaxHeight(0.8f)`).
4. **No new widget.** Don't create an Android analog of `RhythmPickerButton`. Inline fields open a
   `Dialog`/`AlertDialog` hosting `RhythmSelector` (Test Ctor, OSKE exam) or embed it in a pane
   (OSKE ctor, compare dialog).

## Non-goals

- **Test Ctor «Собери ЭКГ» assemble** target/distractor pickers — the assemble question type is not
  on Android yet. When it lands (see the assemble-ECG Android sync), its rhythm pickers should also
  open a `Dialog { RhythmSelector(...) }` (target with clear, distractor with an exclude filter via
  `rhythms.filter { … }` passed into `RhythmSelector`).
- Non-rhythm dropdowns: OSKE **specialty**, Examination **count/theme/test**, Course **topic**.
- Any change to `RhythmSelector`'s Teaching-drawer behaviour or its selection semantics.

## Plan

### Phase 1 — `RhythmSelector.showPinButton`
`ui/panels/RhythmSelector.kt`:
- Add `showPinButton: Boolean = true` to the signature (`:67`).
- Wrap the fix-drawer `IconButton` (`:260-269`) in `if (showPinButton) { … }`.
- Set `showPinButton = false` at the existing Teaching-tab embed (`TeachingControlPanel.kt:122`) and
  every new embed below. Teaching drawer / DataSource keep the default `true`.

### Phase 2 — Test Constructor ECG (from the original plan)
`ui/screens/TestConstructorScreen.kt`:
- Add `appViewModel: AppViewModel` to `QuestionEditorCard` (`:370`); pass it at both call sites
  (~`:178`, ~`:301`).
- Replace the `ExposedDropdownMenuBox` (`:447-478`) with a read-only field that opens a dialog:

```kotlin
if (stimulus == QuestionStimulus.Ecg) {
    Spacer(modifier = Modifier.height(8.dp))
    var showEcgPicker by remember { mutableStateOf(false) }
    val selected = rhythms.find { it.id == question.pathologyId }
    val currentLanguage by appViewModel.selectedLanguage.collectAsState()
    val label = selected?.let {
        if (currentLanguage == Language.RU) it.nameRu ?: it.titleEn else it.titleEn
    } ?: stringResource(R.string.test_ctor_ecg_none)

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = label, onValueChange = {}, readOnly = true,
            label = { Text(stringResource(R.string.test_ctor_ecg)) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.weight(1f).clickable { showEcgPicker = true }
        )
        if (question.pathologyId != null) {
            IconButton(onClick = { onUpdate { it.copy(pathologyId = null) }; onPreview(null) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.test_ctor_ecg_none))
            }
        }
    }
    if (showEcgPicker) {
        AlertDialog(
            onDismissRequest = { showEcgPicker = false },
            title = { Text(stringResource(R.string.test_ctor_ecg)) },
            text = {
                RhythmSelector(
                    appViewModel = appViewModel,
                    modifier = Modifier.fillMaxHeight(0.7f),
                    rhythms = rhythms,
                    selectedId = question.pathologyId,
                    showPinButton = false,
                    onRhythmSelect = { entry -> onUpdate { it.copy(pathologyId = entry.id) }; onPreview(entry.id) },
                )
            },
            confirmButton = { TextButton(onClick = { showEcgPicker = false }) { Text(stringResource(R.string.data_source_close)) } },
        )
    }
}
```

### Phase 3 — Compare-target dialog (also fixes the `<ecg>` embed)
`ui/dialogs/ComparisonTargetDialog.kt`: replace the left `LazyColumn` (`:100-124`) — and drop the
now-redundant `rhythm_selector_title` header (`:95-99`, the panel has its own) — with:

```kotlin
RhythmSelector(
    appViewModel = appViewModel,
    modifier = Modifier.fillMaxHeight(),
    rhythms = rhythms,
    selectedId = selectedPathology?.id,
    showPinButton = false,
    onRhythmSelect = { selectedPathology = it },
)
```

Keep the right-hand lead grid and the Confirm/Cancel buttons. Because `HtmlBlockEditor.kt:286-298`
opens this same dialog for the `<ecg>` embed, that picker is fixed by this change with no edit there.
The `listState`/`LaunchedEffect` scroll-to-selection (`:67-74`) can be removed (the panel scrolls to
its own selection).

### Phase 4 — OSKE constructor ECG list
`ui/screens/OskeConstructorScreen.kt`: replace the `Text("ECG") + rhythms.forEach { RadioButton }`
block (`:73-91`) with a fixed-height `RhythmSelector` (see nested-scroll gotcha):

```kotlin
Text("ECG")
RhythmSelector(
    appViewModel = appViewModel,          // thread appViewModel into this screen if absent
    modifier = Modifier.fillMaxWidth().height(320.dp),
    rhythms = rhythms,
    selectedId = selectedEcgId,
    showPinButton = false,
    onRhythmSelect = { oskeViewModel.setConstructorSelection(specialty, it.id) },
)
```

Keep it **out of** the settings Column's `verticalScroll` (give it the fixed height instead). Confirm
`appViewModel` is available on this screen; thread it from the call site if not.

### Phase 5 — OSKE exam start dialog ECG (filtered subset)
`ui/screens/OSKEScreen.kt` `OskeStartDialog` (`:178-253`):
- Add `appViewModel: AppViewModel` to `OskeStartDialog` and pass it from the call site (~`:93-100`,
  where `appViewModel` is in scope).
- Replace the `filteredRhythms.forEach { RadioButton }` block (`:240-253`) with a fixed-height
  `RhythmSelector` fed the **subset**:

```kotlin
RhythmSelector(
    appViewModel = appViewModel,
    modifier = Modifier.fillMaxWidth().height(300.dp),
    rhythms = filteredRhythms,            // already computed at :191
    selectedId = selectedEcgId,
    showPinButton = false,
    onRhythmSelect = { selectedEcgId = it.id },
)
```

Keep the empty-subset warning (`:231-238`) and the Start button's `selectedEcgId != null` guard.
Because the outer body is a `Column(verticalScroll)` (`:197`), give the selector a fixed height and
place it outside that scroll (or split the dialog body) per the nested-scroll gotcha.

## Risks & open questions

- **Nested scroll (biggest one).** Verify the two OSKE screens don't crash/measure-loop by embedding
  a `weight(1f)` list inside a scrolling Column — the fixed-height wrapper is mandatory there.
- **Shared toggle state** across all embeds — confirm acceptable (recommended: yes, matches existing).
- **Auto-close on clinical auto-select** for the Teaching tab / Test-Ctor dialog — keep parity or add
  a Done button; the compare/OSKE dialogs already use explicit buttons so they're safe.

## Verification

1. Each site (compare dialog, OSKE ctor, OSKE exam, Test Ctor ECG, `<ecg>` embed, Teaching tab) opens
   the **grouped** rhythm list with search + group/clinical/expand/collapse buttons and **no pin**.
2. Search filters; selecting a rhythm updates the target and (where wired) the monitor preview.
3. OSKE exam shows only ECGs with keys for the chosen specialty; empty-specialty warning still shows;
   Start enabled only with a pick.
4. OSKE constructor + exam dialogs scroll smoothly (no nested-scroll jank); selector has bounded height.
5. Compare-dialog change also makes the course `<ecg>` embed picker grouped (same dialog).
6. Teaching drawer + DataSource dialog still show the pin button (default `true` preserved).

---

## Outcome

- **Result:** shipped
- **Deviations from plan:** none
- **Follow-ups spawned:** none
