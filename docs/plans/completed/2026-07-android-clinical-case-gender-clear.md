# Clinical-case dialog — empty gender option + "Clear all fields" — Android parity

**Status:** completed
**Owner:** (unassigned)
**Started:** 2026-07-09
**Finished:** 2026-07-09
**Related issues / PRs:** —
**Source of truth:** Windows port (`CardioSimulatorWin`), shipped 2026-07-09.
**Customer request:** Николай, 2026-07-07 — "в конструкторе ЭКГ при редактировании клин
случая, в разделе пол надо добавить пустое место. Иначе он видит пол и делает его клин
случаем, а не патологией. Ещё можно галочку добавить — очистить все поля."

## Goal

In the ECG **Constructor → clinical-case editor**, the **gender (Пол)** dropdown only
offers **Male / Female**. Once a sex is picked there is **no way to un-set it**, and a
pathology is treated as a *clinical case* purely by having a **non-empty `clinical_case`
value** — so that one lingering `gender=Male` keeps flipping a plain pathology into a
clinical case. Fix it the same way Windows did:

1. Add an **empty "not specified" entry** at the top of the gender dropdown; picking it
   clears gender back to `""`.
2. Add a **"Clear all fields" checkbox** that empties every field in the dialog at once,
   so a clinical case can be wiped back to a plain pathology in one tap.

The data structure needs **no change** — `PathologyFile.clinicalCase` is already nullable
and `ConstructorViewModel.setClinicalCase` already normalizes blank → null
(`ConstructorViewModel.kt:665`), so an all-empty save already reverts to a plain
pathology. This is a **dialog-only** change plus two new strings.

## What Windows shipped (the thing we're porting)

`CardioSimulatorWin/.../Screens/ConstructorScreen.cs`, `OnClinicalCaseClick`:
- Gender `ComboBox` gained an **index-0 "— not specified"** item (`GenderUnset`); Male /
  Female moved to indices **1 / 2**; default `SelectedIndex = 0`; parse of an existing
  `gender=` value now maps to 1/2. Save writes gender **only** for index 1 (`Male`) or 2
  (`Female`) — index 0 writes nothing.
- A **"Clear all fields" `CheckBox`** (`ClinicalClearAll`) placed at the **top** of the
  dialog panel; its `Checked` handler blanks title/description/name/age/hr/bp/others and
  resets gender to index 0. An empty dialog → `resultString = null` → `SetClinicalCase(null)`
  → plain pathology.
- Two new `AppStrings` keys in all 5 locales: `clinical_gender_unset`, `clinical_clear_all`.

## Current Android state

`ui/screens/ConstructorScreen.kt` → `ClinicalCaseDialog` (lines **1038–1188**):
- Gender is a plain `String` state `var gender` (`:1054`); **empty string = unset** — so
  the empty state is representable, there's just **no UI affordance to reach it**.
- The dropdown (`:1103–1127`) is an `OutlinedTextField(readOnly)` + `DropdownMenu` built
  from `genderOptions = listOf(gender_male, gender_female)` (`:1064`). Each item sets
  `gender = option` (`:1121`). No "unset" item exists.
- Save (`:1160–1167`): `if (gender.isNotBlank()) { … normalizedGender = if (gender ==
  genderOptions[0]) "Male" else "Female" }`.
- `setClinicalCase` normalization already correct: `ConstructorViewModel.kt:663–670`
  (`isNullOrBlank() → null`, dirty flag). The confirm button already builds the params map
  and an empty map `joinToString(",")` yields `""`, which normalizes to null. ✅

So Android has the **exact same bug and the exact same fix surface** as Windows.

## Changes

### 1. Empty "not specified" gender option

In `ClinicalCaseDialog`:

- Add the unset label alongside the existing options. Keep `genderOptions` as the two real
  choices (so the save-side `genderOptions[0]` comparison stays valid) and introduce a
  separate unset label:
  ```kotlin
  val genderUnset = stringResource(R.string.clinical_gender_unset)
  val genderOptions = listOf(stringResource(R.string.gender_male), stringResource(R.string.gender_female))
  ```
- In the `DropdownMenu` (`:1112–1126`), render the unset item **first**, then the two
  options:
  ```kotlin
  DropdownMenuItem(
      text = { Text(genderUnset) },
      onClick = { gender = ""; genderExpanded = false }
  )
  genderOptions.forEach { option -> /* unchanged */ }
  ```
- **Field display:** the `OutlinedTextField.value = gender` shows empty when unset — fine.
  Optionally show `genderUnset` as a placeholder when `gender.isBlank()` for clarity
  (`placeholder = { Text(genderUnset) }`), but do **not** put `genderUnset` into `gender`
  itself or the `isNotBlank()` save guard would treat it as a real value.
- Save logic at `:1160` is already correct — `gender.isBlank()` (the unset case) skips
  writing `gender=`. No change needed there beyond confirming it.

### 2. "Clear all fields" checkbox

Add at the **top** of the dialog `Column` (before the title `TextField`, mirroring
Windows' placement), a labelled checkbox that clears every state var:
```kotlin
var clearAll by remember { mutableStateOf(false) }
Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(
        checked = clearAll,
        onCheckedChange = {
            clearAll = it
            if (it) {
                title = ""; description = ""; name = ""; age = ""
                gender = ""; hr = ""; bp = ""; others = ""
            }
        }
    )
    Text(stringResource(R.string.clinical_clear_all))
}
```
Checking it empties the fields; pressing OK then saves an empty map → null → plain
pathology. (Matches Windows: check clears immediately, no restore-on-uncheck.)

### 3. Strings — add to all 5 locales

Add next to the existing `gender_*` keys in each `strings.xml`:

| file | `clinical_gender_unset` | `clinical_clear_all` |
|---|---|---|
| `values/strings.xml` (after :540) | `— not specified` | `Clear all fields` |
| `values-ru/strings.xml` (after :537) | `— не указан` | `Очистить все поля` |
| `values-zh/strings.xml` (after :356) | `— 未指定` | `清除所有字段` |
| `values-es/strings.xml` (after :364) | `— sin especificar` | `Borrar todos los campos` |
| `values-hi/strings.xml` (after :510) | `— निर्दिष्ट नहीं` | `सभी फ़ील्ड साफ़ करें` |

The `—` em dash is literal text (no `%`/`{0}` placeholders), so no Android
placeholder-escaping concerns.

## Gotchas / notes

- **Pre-existing gender round-trip quirk (out of scope, but note it).** Android stores the
  **canonical** `"Male"`/`"Female"` on save (`:1165`) but re-loads it verbatim into the
  `gender` state (`:1054`) and compares against the **localized** `genderOptions[0]` at the
  next save (`:1165`). In a non-English locale a reopened "Male" won't equal the localized
  label, so re-saving silently coerces it to `"Female"`. Windows dodges this by mapping the
  stored value through a lowercase alias list to pick the index. If you want to harden while
  you're here, seed `gender` from the canonical value the same way (map `"male"/"муж"/…` →
  `genderOptions[0]`), but that's a **separate** fix — the customer ask is only the empty
  option + clear checkbox.
- **Don't add the unset label to `genderOptions`** — the save branch keys off
  `genderOptions[0] == gender_male`; inserting an item at index 0 would shift that.
- `Checkbox`, `Row`, `Alignment` imports may need adding to `ConstructorScreen.kt`.
- No parser/model/test change (`Pathology.kt`, `PathologyParser.kt`, `PathologyParserTest.kt`
  untouched) — `clinicalCase` semantics are unchanged.

## Acceptance

1. Open Constructor → pick a pathology → open the clinical-case editor.
2. Pick a gender, reopen the dropdown, choose **"— not specified"** → field clears; press
   OK → the item is **not** a clinical case (no gender persisted, `clinicalCase` null if all
   else empty).
3. Fill several fields, tick **"Clear all fields"** → all fields blank; press OK → item
   reverts to a **plain pathology** (`clinicalCase == null`), shows in the rhythm list, not
   the clinical-case list.
4. Existing clinical cases with a saved gender still load and display correctly.
5. All 5 languages show the new labels.
