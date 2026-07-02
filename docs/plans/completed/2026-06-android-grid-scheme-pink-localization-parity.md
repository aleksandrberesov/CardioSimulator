# Android parity — localize `grid_scheme_pink` ("ECG film") in non-Russian locales

**Status:** DONE (2026-06-29)
**Owner:** Android port
**Started:** 2026-06-29
**Related issues / PRs:** Windows fix in `CardioSimulatorWin/src/CardioSimulator.App/Localization/AppStrings.cs` (En/Zh/Es entries for `grid_scheme_pink`)

## Goal

The pink "ECG film" grid colour-scheme label (`grid_scheme_pink`) is currently the
literal Russian string **`Пленка ЭКГ`** in **every** Android locale. English, Chinese,
and Spanish users see Cyrillic text in the monitor grid-scheme picker (Settings).
This is a localization bug. Port the Windows fix: translate `grid_scheme_pink` in the
non-Russian locales, keeping Russian as-is. Tiny, self-contained, do it now while the
matching Windows change is fresh.

## Current state

`grid_scheme_pink` exists in all four Android locales, all holding `Пленка ЭКГ`:

| locale | file | line | current value |
|---|---|---|---|
| default (en) | `app/src/main/res/values/strings.xml` | 40 | `Пленка ЭКГ` ← **bug** (default locale is English: cf. "Yellow", "Blue/Gray", "Bedside monitor" on lines 38–41) |
| ru | `app/src/main/res/values-ru/strings.xml` | 40 | `Пленка ЭКГ` ← **correct, leave** |
| zh | `app/src/main/res/values-zh/strings.xml` | 37 | `Пленка ЭКГ` ← **bug** |
| es | `app/src/main/res/values-es/strings.xml` | 37 | `Пленка ЭКГ` ← **bug** |

The key is consumed by the enum at
`app/src/main/java/com/example/cardiosimulator/domain/MonitorModeModel.kt:11`:
`Pink(R.string.grid_scheme_pink)`. **The resource key name does not change**, so no
Kotlin / enum / picker code is touched — this is a pure string-resource edit.

> **⚠️ This deliberately reverses an earlier customer decision.** The completed plan
> `docs/plans/completed/2026-06-android-grid-color-schemes-parity.md:186-193` records that
> the customer chose to keep `Пленка ЭКГ` **untranslated in all four locales**. That choice
> has since been overruled: on Windows it's now treated as a localization bug and translated
> per-locale. Do **not** flag this as a parity regression and do **not** "restore" the
> untranslated label — translating it is the intended new behaviour. (The Windows `AppStrings`
> Russian and Hindi entries stay/were already correct; Android has no Hindi locale, so Hindi
> is N/A here.)

## Non-goals

- No change to `values-ru/` — Russian keeps `Пленка ЭКГ`.
- No enum, picker, renderer, or `EcgSvgRenderer.kt` changes — the colour scheme itself,
  its key name, and picker ordering are unaffected. Labels only.
- No new locale (Android ships en/ru/zh/es only; the Windows Hindi entry has no Android
  counterpart).

## Plan

### Phase 1 — Translate the three non-Russian labels

Replace the `grid_scheme_pink` value in each non-Russian `strings.xml`. These are the
exact translations applied on Windows:

| locale | file | new value |
|---|---|---|
| default (en) | `app/src/main/res/values/strings.xml` | `ECG film` |
| zh | `app/src/main/res/values-zh/strings.xml` | `心电图纸` |
| es | `app/src/main/res/values-es/strings.xml` | `Película ECG` |

Leave `values-ru/strings.xml` untouched (`Пленка ЭКГ`).

That's the whole change — one line edited in each of three files.

## Risks & open questions

- **Translation wording.** `心电图纸` = "ECG paper"; `Película ECG` = "ECG film". Both match
  the Windows choices and the intent (pink ECG-paper grid). If a clinician reviewer prefers
  alternate Chinese/Spanish phrasing, swap the value — the key and everything else stay put.
  *(Resolved 2026-06-29: use the Windows values verbatim for cross-platform consistency.)*

## Verification

- `grep -rn 'grid_scheme_pink' app/src/main/res/values*/strings.xml` shows Cyrillic only in
  `values-ru/`; en/zh/es show their translations.
- `./gradlew assembleDebug` (or `lint`) passes — string edits shouldn't affect the build, but
  confirm no malformed XML / unescaped entity.
- Manual: launch, open Settings → monitor grid-scheme picker, switch app language. The pink
  scheme reads **"ECG film" / "心电图纸" / "Película ECG"** in en/zh/es and **"Пленка ЭКГ"** in ru.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Localize `grid_scheme_pink` (ECG film) in en/zh/es | 1 | 3 one-line string edits; reverses the prior "untranslated in all locales" choice — see callout |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:** shipped / dropped / partial
- **PRs:** #…
- **Deviations from plan:** …
- **Follow-ups spawned:** …
