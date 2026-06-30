# Plan: Add Hindi (hi) language support — Android parity

**Created:** 2026-06-29
**Status:** NOT STARTED
**Direction:** **Windows → Android.** Hindi was added to the WinUI 3 port first (for Indian
users). The Android app must reach parity. The Windows port is the **reference implementation**
for the translation copy — match it, adapting to Android string-resource idioms.

**Target (Android) source root:** `E:\VLN_Project\CardioSimulator\app\src\main\`
**Reference (Windows) source root:** `E:\VLN_Project\CardioSimulatorWin\src\`

## Goal

Add Hindi as a fully selectable in-app UI language, exactly like the existing EN/RU/ZH/ES:
it appears in the Settings language picker as **"हिन्दी"**, switches the whole UI live, and
persists across launches. No behavioral/logic changes — this is enum + a locale config entry +
a new translated `strings.xml`.

## Why this is small on Android (the system is already data-driven)

Android's language stack mirrors Windows and needs no plumbing changes beyond the enum:
- `SettingsDialog.kt:226` iterates `Language.entries` → a new enum value shows up automatically.
- `AppViewModel.updateLanguage()` (`:294`) calls
  `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))`,
  so the actual strings come from `res/values-<tag>/strings.xml`. Nothing to change there.
- `Language.fromTag()` (`AppStateModel.kt:10`) iterates `entries`, so persistence round-trips "hi".
- `currentSystemLanguage()` / startup restore (`AppViewModel.kt:193`, `:304`) work unchanged.

So the work is: **(1)** one enum line, **(2)** one `locales_config.xml` entry, **(3)** a new
`res/values-hi/strings.xml`.

---

## Step 1 — `Language` enum

File: `app/src/main/java/com/example/cardiosimulator/domain/AppStateModel.kt`

Add `HI` after `ES` (mind the semicolon currently terminating the `ES` entry):

```kotlin
enum class Language(val tag: String, val displayName: String) {
    EN("en", "English"),
    RU("ru", "Русский"),
    ZH("zh", "中文"),
    ES("es", "Español"),
    HI("hi", "हिन्दी");
    ...
}
```

Windows reference: `CardioSimulator.Core/Domain/AppState.cs` — `Language.HI`, `Tag()` → `"hi"`,
`DisplayName()` → `"हिन्दी"`.

## Step 2 — Declare the locale for per-app language

File: `app/src/main/res/xml/locales_config.xml`

Add the Hindi locale so Android 13+ per-app-language and `AppCompatDelegate` recognize it:

```xml
<locale android:name="hi" />
```

(The manifest's `<application android:localeConfig="@xml/locales_config">` already points here.)

## Step 3 — `res/values-hi/strings.xml` (the bulk of the work)

Create `app/src/main/res/values-hi/strings.xml` translating the string keys.

**Canonical key list = `app/src/main/res/values/strings.xml` (409 strings).** This is the English
baseline and the authoritative set of resource names. ZH/ES are partial (257 / 265 of 409) and
fall back to the default for missing keys, so Hindi at minimum should match that coverage; full
coverage is preferred for a first-class Indian-user experience.

**Translation source of truth:** the Windows `Hi` dictionary just added to
`CardioSimulator.App/Localization/AppStrings.cs` (~350 keys). The Android resource names are
identical snake_case to the Windows `AppStrings` keys (Windows was ported *from* this strings.xml),
so for every key present in the Windows `Hi` dict, **copy that Hindi string**. For the ~60
Android-only keys not in the Windows dict (e.g. `course_data_source_loaded_format`, content
descriptions, any RU/EN-only extras), translate fresh from the English baseline.

### ⚠️ Critical gotcha — placeholder format differs from Windows

The Windows `Hi` strings use .NET `string.Format` placeholders (`{0}`, `{1}`, `{0:0.000}`).
Android uses **positional printf** (`%1$d`, `%2$d`, `%1$.3f`, `%1$s`). When copying a Windows
Hindi string into the Android XML, **do not paste `{0}` — convert to the Android spec.**

The safe rule: **keep each key's format specifiers exactly as they appear in the English
`values/strings.xml`; only translate the surrounding words (and reorder the `%n$` indices if the
Hindi word order differs).** Examples from the baseline:

| key | EN (`values`) | Hindi (`values-hi`) |
|---|---|---|
| `test_counter_format` | `%1$d of %2$d` | `%2$d में से %1$d`  ← **reordered** (Hindi "Y of X") |
| `test_result_score_format` | `Result: %1$d of %2$d` | `परिणाम: %2$d में से %1$d` |
| `exam_score_format` | `%1$d of %2$d` | `%2$d में से %1$d` |
| `oske_score_format` | `%1$d of %2$d blocks correct` | `%2$d में से %1$d ब्लॉक सही` |
| `test_question_title_format` | `Question %1$d` | `प्रश्न %1$d` |
| `data_source_loaded_format` | `Loaded %1$d pathologies` | `%1$d विकृतियाँ लोड हुईं` |
| `monitor_count_format` | `%1$d×` | `%1$d×` (keep the `×`) |
| `ecg_rr_value_format` | `R-R: %1$.3f s` | `R-R: %1$.3f s` |
| `exam_roster_count_format` | (`%1$d` registered · `%2$d` finished) | keep both indices in order |

Cross-check every `*_format` / `*_count` / `*_score` key for index order. The Windows `Hi` dict is
the wording reference; the Android `%n$` spec is authoritative for the placeholder syntax.

### Android XML escaping

- Apostrophes must be escaped: `'` → `\'` (the Hindi copy uses few/none, but check).
- `&` → `&amp;`, `<` → `&lt;`. A leading `@` or `?` must be escaped.
- `…` (ellipsis), `—` (em dash), `×` and Devanagari are fine as literal UTF-8; file is UTF-8.
- Wrap the whole file in `<resources>…</resources>`; one `<string name="…">…</string>` per key.

### Keys whose value is intentionally non-Hindi (leave as-is)

Pure symbols/abbreviations stay identical to the baseline: `ecg_interval_p/qrs/t/pr/st/qt/rr`,
`monitor_3d` ("3D"), `editor_adc_label`/`editor_adc_format` ("ADC"), `editor_time_unit` ("ms"),
`monitor_speed_unit` ("mm/s"), `test_ctor_id_format` ("id: %1$s"), electrode tags `RA/LA/RL/LL/V1…V6`.
(See the Windows `Hi` dict — these were deliberately kept in Latin there too.)

---

## While you're here (optional, pre-existing bug — flagged on Windows too)

`grid_scheme_pink` is the Russian leftover **"Пленка ЭКГ"** in **all four** Android locales
(`values`, `values-ru`, `values-zh`, `values-es`) — same bug exists on Windows and is tracked
separately there. For Hindi, use the correct translation **`ईसीजी फिल्म`** (matches the Windows
`Hi` dict). Fixing the other three locales (`ECG film` / `心电图纸` / `Película ECG`) is out of
scope for this plan but trivial if you want to clean it up while in the file.

## Acceptance criteria

- Settings → Language shows **हिन्दी** as a fifth chip; selecting it switches the entire UI to
  Hindi live and the choice survives an app restart (DataStore `languageTag` = "hi").
- `res/values-hi/strings.xml` exists, is valid XML, and covers at least the ZH/ES key set
  (ideally all 409); untranslated keys fall back to English with no crash.
- All `*_format` strings render correctly with no `IllegalFormatException` and correct number
  order in Hindi (especially `test_counter_format` / `*_score_format`).
- `locales_config.xml` lists `hi`; the OS per-app-language screen offers Hindi.
- Parity check: behavior matches the Windows port — same picker, same live switch, same persistence.

## File checklist

- `app/src/main/java/com/example/cardiosimulator/domain/AppStateModel.kt` — add `HI` enum entry.
- `app/src/main/res/xml/locales_config.xml` — add `<locale android:name="hi" />`.
- `app/src/main/res/values-hi/strings.xml` — **new**, Hindi translations.
- (No changes needed in `AppViewModel.kt` or `SettingsDialog.kt` — they're data-driven.)
