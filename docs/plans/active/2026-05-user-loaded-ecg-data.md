# User-loaded ECG data via Storage Access Framework

**Status:** active (Phases 1–4 implemented; ZIP import + sample dataset deferred)
**Owner:** alexandr.beresov@gmail.com
**Started:** 2026-05-06
**Related issues / PRs:** —

## Goal

Replace the current "data lives inside the APK as assets" approach with a
user-controlled data source: the user picks a folder on the device that
contains `Series/` and `Parts/` subfolders, and the app loads ECG data from
there. This decouples the dataset from the app binary so datasets can be
updated, shared, or swapped without rebuilding the APK, and removes the
implicit hardcoded data path that today produces an empty app because the
asset folders aren't even shipped.

## Current state

- `data/EcgRepository.kt` reads `Series/` and `Parts/` from `AssetManager`
  (`app/src/main/java/com/example/cardiosimulator/data/EcgRepository.kt:14-77`).
- `MainActivity.kt:36` constructs `EcgRepository(this.assets)` directly and
  injects it into `AppViewModel`.
- `ui/viewmodels/AppViewModel.kt:51-62` calls `repo.load()` once on init and
  surfaces results via `rhythms / allSeries / allParts / waveforms`
  StateFlows.
- The parser in `domain/EcgData.kt` already supports `parse(String)` and
  `parse(File)`, so it is storage-agnostic — only the repository layer is
  coupled to `AssetManager`.
- `app/src/main/assets/` does not currently contain the `Series` or `Parts`
  folders, so `assets.list()` returns empty and the app starts with no
  data anyway.

## Non-goals

- No editor UI for creating/modifying ECG files inside the app.
- No cloud sync, no remote dataset fetching.
- No migration of an existing on-disk dataset format — the file format
  stays exactly as parsed today.
- No multi-dataset switching at runtime beyond "change folder in
  Settings".

## Plan

### Phase 1 — Decouple repository from AssetManager
- Introduce `data/EcgSource.kt` with interface
  `EcgSource { listSeries(); listParts(); openSeries(name); openPart(name) }`.
- Add `AssetEcgSource(assets)` implementation that preserves current
  behavior (used by `@Preview` and tests).
- Refactor `EcgRepository` to take an `EcgSource` instead of
  `AssetManager`; `readAll(...)` uses the source interface.
- `MainActivity` keeps working by passing `AssetEcgSource(assets)` for now.
- **Shippable:** behavior unchanged.

### Phase 2 — SAF-backed source + persistent URI
- Add `SafEcgSource(context, treeUri)` using
  `androidx.documentfile:documentfile`. Expects two children `Series/` and
  `Parts/`. Reads via `contentResolver.openInputStream`.
- Add `data/DataSourcePrefs.kt` (DataStore Preferences) storing
  `ecg_data_tree_uri: String?`.
- On launch, if a persisted URI exists and is still readable, build
  `SafEcgSource`; otherwise leave the repo unconfigured.
- **Shippable:** if a URI was set previously the app loads from it; if not,
  the app starts empty (same as today).

### Phase 3 — Folder-picker UI
- Add `ui/screens/DataSourceScreen.kt` with a "Select data folder" button
  that launches `ActivityResultContracts.OpenDocumentTree()`.
- On result: `takePersistableUriPermission` (read-only), validate that
  `Series/` and `Parts/` subfolders exist and contain at least one file,
  persist the URI, trigger `repo.load()`.
- Show summary on success (`N series, M parts loaded`), error states for
  missing subdirs / unreadable / zero parsable files.
- `AppViewModel` exposes `dataState: StateFlow<DataState>` —
  `NotConfigured / Loading / Ready(counts) / Error(msg)`.
- `MainScreen` branches on `dataState`: shows `DataSourceScreen` when
  `NotConfigured` or `Error`, otherwise the existing UI.

### Phase 4 — Settings entry & ZIP import (optional)
- Add "Change data folder" row in `SettingsScreen` that re-opens the
  folder picker.
- Add "Import from .zip" button using `OpenDocument()`; unzip into
  `filesDir/ecg/`, then use a `FileEcgSource(File)` pointing there. Persist
  the chosen mode (folder vs internal-from-zip).
- Localize all new strings (`values/`, `values-ru/`, `values-es/`,
  `values-zh/`).

### Phase 5 — Polish
- Empty-asset fallback: ship a tiny sample dataset in `assets/sample/` and
  a "Load sample data" button in `DataSourceScreen` for first-run users.
- Remove unused asset-loading code paths if `AssetEcgSource` ends up only
  used by previews.

## Risks & open questions

- **URI revocation:** the user can revoke the folder permission from
  system settings. Repo must surface a clear "Folder no longer accessible"
  error and route back to `DataSourceScreen`.
- **Performance:** SAF `DocumentFile.listFiles()` on large folders is slow
  on some devices. If lists exceed a few hundred entries, switch to a
  `ContentResolver.query` against `DocumentsContract.buildChildDocumentsUriUsingTree`.
- **Encoding:** current parser reads `ISO_8859_1`. SAF stream reads must
  use the same charset — verified, no change needed.
- **Open: locales** — ru/es/zh translations for new strings need the
  same person who owns `2026-04-localization.md` to produce strings.
- **Open: ZIP import scope** — confirmed in scope (Phase 4) but optional;
  may be deferred if folder-picker proves sufficient.

## Verification

- **Phase 1:** project builds; `MainPreview` still renders;
  `EcgRepository` unit-style smoke (load with `AssetEcgSource` over a
  temp fake) returns parsed entries.
- **Phase 2:** persisted URI from a manual SAF pick is reloaded after app
  restart and produces non-empty `rhythms`.
- **Phase 3:** fresh install → `DataSourceScreen` shown; pick a folder
  with valid `Series/` and `Parts/` → counts displayed → main UI loads
  with rhythms populated. Pick a folder missing `Parts/` → clear error.
- **Phase 4:** "Change data folder" in Settings successfully re-points the
  source; ZIP import unzips and loads. All new strings render in ru/es/zh.
- **Phase 5:** "Load sample data" populates the app with the bundled
  sample without requiring a folder pick.

## PR breakdown

| # | PR title                                              | Phase | Notes |
|---|-------------------------------------------------------|-------|-------|
| 1 | Introduce `EcgSource`, refactor repo off AssetManager | 1     | No behavior change |
| 2 | SAF source + persisted tree URI                       | 2     | Adds documentfile + datastore deps |
| 3 | DataSourceScreen + dataState flow                     | 3     | First user-visible change |
| 4 | Settings entry + ZIP import + localization            | 4     | Optional, may split |
| 5 | Sample dataset + cleanup                              | 5     | Polish |

---

## Outcome

- **Result:** partial — core feature shipped (Phases 1–4 minus ZIP).
- **Deviations from plan:**
  - ZIP import (Phase 4 second half) deferred. Folder-picker covers the
    primary use case and shipping it sooner unblocks data-loading. ZIP can
    follow once we know whether users actually want it.
  - Sample dataset (Phase 5) deferred — requires real ECG content that
    can't be sensibly invented from this worktree.
- **Files added:**
  - `data/EcgSource.kt` (interface + `AssetEcgSource`)
  - `data/SafEcgSource.kt`
  - `data/DataSourcePrefs.kt`
  - `ui/screens/DataSourceScreen.kt`
- **Files modified:**
  - `data/EcgRepository.kt` — accepts `EcgSource`, supports `setSource(...)`.
  - `MainActivity.kt` — passes `applicationContext` + `DataSourcePrefs`
    into `AppViewModel`.
  - `ui/viewmodels/AppViewModel.kt` — adds `DataState`, `setDataFolder(...)`,
    SAF restoration on init.
  - `ui/screens/MainScreen.kt` — gates UI behind `dataState`.
  - `ui/screens/SettingsScreen.kt` — adds "Change data folder" entry.
  - `gradle/libs.versions.toml`, `app/build.gradle.kts` — adds
    `androidx.documentfile` and `androidx.datastore.preferences`.
  - `res/values{,-ru,-es,-zh}/strings.xml` — new picker strings.
- **Follow-ups spawned:**
  - ZIP import (`OpenDocument` + unzip into `filesDir/ecg` + `FileEcgSource`).
  - Sample dataset under `assets/sample/` + "Load sample data" button.
  - Build verification on a real toolchain (the worktree sandbox blocks
    Gradle networking, so compilation was not run here).
