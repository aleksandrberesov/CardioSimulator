# Localization

**Status:** active
**Started:** 2026-04-29
**Branch:** `claude/charming-hertz-d394c5`

## Goal

Make the Settings → Language switcher actually change the UI language, and
extract user-visible strings into resources so we can ship Russian, Chinese,
and Spanish.

## Current state (at planning time)

- Compose-only Android app, single `app` module.
- Only `res/values/strings.xml` existed (1 string: `app_name`).
- A `Language` enum (`EN/RU/CH/ES`) and `FilterChip`-based switcher were
  added in commit `3351bb5`, but the chip selection only updated in-memory
  `StateFlow` — no system locale was applied.
- ~13+ user-visible strings hardcoded in Compose across `SettingsScreen`,
  `BaseScreen`, `MonitorControlPanel`, `RhythmChoosingPanel`,
  `TestingControlPanel`.
- `MainScreen` and `TopControlPanel` switched on the operating mode's
  *localized* title — would break the moment the title got translated.
- `minSdk = 24`, so per-app language API needed the AppCompat back-port.

## Non-goals

- RTL languages (no Arabic/Hebrew planned yet).
- Locale-aware number/date formatting (single-language data so far).
- Translating the cardiology rhythm/pathology corpus shipped in `assets/`.

## Plan

### Phase 1 — Wire the switcher
- Add `androidx.appcompat` dependency.
- Create `res/xml/locales_config.xml` listing en/ru/zh/es.
- Manifest: `android:localeConfig` + `AppLocalesMetadataHolderService` with
  `autoStoreLocales="true"`.
- Switch `MainActivity` from `ComponentActivity` to `AppCompatActivity`;
  retheme to `Theme.AppCompat.Light.NoActionBar`.
- `Language` enum gains `tag` (BCP-47) and `displayName` (in-language); rename
  `CH` → `ZH`.
- `AppViewModel.updateLanguage()` calls
  `AppCompatDelegate.setApplicationLocales(...)`; init reads back current
  locale.

### Phase 2 — Extract hardcoded strings + stable IDs
- Introduce `OperatingMode` enum with `@StringRes titleRes`. Switch
  `OperatingModeModel` to `id: OperatingMode`. Replace string-based `when`
  in `MainScreen` and `TopControlPanel`.
- Add `@StringRes` to `GridScheme`.
- Move every user-visible literal into `res/values/strings.xml` with format
  strings for units (`%1$d mm/s`, etc.).
- Drop the `app_modes` string-array.
- Add `iconContentDescription` to the `Tab` component; set on icon-only
  tabs.

### Phase 3 — Translations
- Create `values-ru/`, `values-zh/`, `values-es/` mirroring the English
  file. Machine-assisted; cardiology terms flagged for clinician review.

### Phase 4 — a11y polish
- Fill missing `contentDescription` on icon buttons (Settings/Pause/Ruler,
  search, nav).

## Risks & open questions

- ~~Per-app language pre-API-33 needs AppCompatActivity to apply.~~ Resolved
  by switching MainActivity + theme.
- Cardiology terminology (ECG axis, EMD/PEA, OSCE) is rough in non-EN
  locales — needs a clinician review before release.
- Activity recreation on language change: handled by AppCompat — verified
  in plan, not yet manually QA'd on-device.

## Verification

- [ ] `./gradlew assembleDebug` succeeds (could not run in sandbox — verify
      locally).
- [ ] Tapping a language chip immediately recreates the activity with
      translated UI.
- [ ] App restart preserves the chosen language.
- [ ] No `Language.CH`, `R.array.app_modes`, or `selectedMode.title ==
      "..."` references remain (greps are clean).

## PR breakdown

Originally pitched as 4 PRs; combined into a single PR for simplicity.

| # | Title | Phase | Status |
|---|-------|-------|--------|
| 1 | Localization (wire switcher, extract strings, ru/zh/es, a11y) | 1–4 | open on `claude/charming-hertz-d394c5`, not yet merged |

## Progress log

- **2026-04-29** — Code for all four phases written on
  `claude/charming-hertz-d394c5`. Local build could not be run in the agent
  sandbox (Gradle loopback restriction).

## What's left to call this done

- [ ] `./gradlew assembleDebug` succeeds locally / in Android Studio.
- [ ] On-device manual QA: switching the Settings chip recreates the
      activity into the chosen language (verify all four: en/ru/zh/es).
- [ ] App restart preserves the chosen language.
- [ ] Clinician review of cardiology terminology in ru/zh/es (ECG axis
      name, EMD/PEA, OSCE, lead/rhythm labels).
- [ ] PR reviewed and merged.

## Deviations & decisions made so far

- Combined all four phases into one PR rather than staging.
- Renamed `Language.CH` → `Language.ZH` while we were touching the enum
  (not in original plan but obvious cleanup — `CH` is Switzerland).
- Added `displayName` to `Language` so chip labels stay in their native
  script regardless of currently-loaded locale (matches Android system
  settings UX).
- Switched `MainActivity` from `ComponentActivity` to `AppCompatActivity`
  + retheme to `Theme.AppCompat.Light.NoActionBar` — required for the
  per-app language back-port to apply pre-API-33.

## Deferred (not part of this plan)

- Locale-aware `NumberFormat`/`DateFormat` for monitor readouts.
- RTL support.
- Translating the cardiology asset corpus (rhythm/pathology data files).
