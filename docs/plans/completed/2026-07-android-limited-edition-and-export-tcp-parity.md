# Limited (student) edition + the export/TCP egress policy

**Status:** active
**Owner:** unassigned
**Started:** 2026-07-16
**Direction:** **Windows → Android**
**Reference (Windows) source:** `E:\VLN_Project\CardioSimulatorWin\src\CardioSimulator.App\`
(`AppEdition.cs`, `ViewModels\AppViewModel.cs`, `Screens\SettingsContent.cs`),
`src\CardioSimulator.Core\Data\ZipCompressor.cs`, `Directory.Build.props`, `build-limited.ps1`
**Target (Android) source root:** `E:\VLN_Project\CardioSimulator\app\src\main\java\com\example\cardiosimulator\`

**Companion plan (read first):** `sync/2026-07-android-content-pack-csp2-parity.md` — the encrypted
`*.pak` pipeline, the writable overlay, and the pack-only cutover. That plan **explicitly excludes**
the Limited edition split ("Not porting the Windows `Limited` edition split", §5). **This plan is that
missing half**, plus the egress policy neither plan otherwise owns. Deliberately **no overlap**: nothing
here re-specifies the container, the sources, or the overlay.

---

## Goal

Two things Windows shipped alongside the packs, without which the packs are decoration:

1. **A Limited (student) edition** — hides the four constructor/authoring modes and the Settings data
   import/export, so the build handed to students has no authoring surface at all.
2. **An egress policy** — in pack mode, **stop the TCP dataset upload** and make **export** produce an
   encrypted `.pak` instead of a plaintext ZIP.

**Why now:** the companion plan turns the dataset into a protected pack. But **protection is only as
strong as its weakest egress**, and Android's is wide open: `AppViewModel.kt:434` uploads the *entire*
dataset over TCP on **every connect**, the TCP target is editable by anyone from Settings
(`SettingsDialog.kt:248-349`) and **auto-applies after a 1 s debounce with no button press**
(`:100-105`). A student can point the app at a listener on their own laptop and receive the whole
dataset — the exact hole Windows found and closed. Shipping CSP2 without this is spending the effort
and leaving the door open.

**Threat model:** casual copying. Android is inherently weaker than Windows here — an APK unzips
trivially and `classes.dex` decompiles well — so do not oversell the result.

## Current state (Android — verified, with line numbers)

### There is no edition concept at all
- `app/build.gradle.kts` — **no `productFlavors`, no `flavorDimensions`**; only a `release` buildType.
  `:23` **`isMinifyEnabled = false`**. `:28` release is signed with the **debug** signing config.
  `buildFeatures` (`:35-37`) enables **only `compose = true`** — **`buildConfig` is NOT enabled, so
  under AGP 9 no `BuildConfig` class is generated at all.**
- Repo-wide grep for `BuildConfig|buildConfigField|productFlavors|flavorDimensions` across `app/src`
  and the build file: **zero hits**. No `AppEdition`, no `IS_LIMITED`, no flavor source sets
  (`app/src` has only `androidTest`, `main`, `test`).

### Modes — one clean choke point
- `domain/OperatingModeModel.kt:6-15` — 8-value `OperatingMode` enum, each with a `@StringRes` title:
  Teaching, Testing, Examination, OSKE, **OSKEConstructor, Constructor, CourseConstructor,
  TestConstructor** (the last four are the authoring set; `Constructor` uses string `mode_editor`).
- **Built exactly once:** `MainActivity.kt:42-44`
  `OperatingMode.entries.forEach { appBuilder.addMode(OperatingModeModel(it)) }`, then
  `appBuilder.build(initialMode = OperatingMode.Teaching)` at `:56`. `AppBuilder.kt:11-18` stores the
  list into `AppStateModel.operatingModes`; `AppViewModel.kt:112` is a plain pass-through `val`.
- **Rendered exactly once:** `TopControlPanel.kt:59` reads `viewModel.operatingModes`, `:86` shows the
  current mode, `:92-104` renders the dropdown → `viewModel.updateOperatingMode(item)`
  (`AppViewModel.kt:333-337`).
- ⇒ The Android equivalent of Windows' single `OperatingModes` choke point is **`MainActivity.kt:42-44`**
  (the builder feed) — *not* the ViewModel, which only re-exposes an already-built immutable list.
- **Duplicated add-all loops for `@Preview`:** `MainActivity.kt:104-106`, `MainScreen.kt:416-417`.
- **Exhaustive `when(selectedMode.id)` with no `else`:** `MainScreen.kt:291-348` (mode → screen),
  `TopControlPanel.kt:109-162` (mode → top bar). `MainScreen.kt:357-395` does have an `else`.
- `AppBuilder.kt:12` — `check(modes.isNotEmpty())`: an over-filter throws at startup.
- `TeachingScreen.kt:583-584` / `:593-594` — `operatingModes.find { it.id == … }!!` for
  Testing/Examination.
- `AppViewModel.kt:335` notes the mode is intentionally **not persisted** (always launches on Teaching,
  `MainActivity.kt:56`) ⇒ no stale saved mode can resurrect a hidden constructor.

### Settings import/export + the egress paths
- `ui/dialogs/SettingsDialog.kt:353-431` — the whole data import/export block, SAF-based and
  contiguous: pick pathologies ZIP `:375` (`OpenDocument`, zip mime filter `:357-369`,
  `takePersistableUriPermission` READ-only), export `:389` (`CreateDocument("application/zip")`,
  default `ecg_export.zip`), pick courses ZIP `:420`, export courses `:426`.
- `ui/screens/DataSourceScreen.kt:67-82` — a **second, independent import surface**, the first-run gate
  (`MainScreen.kt:241-249` when `!isDataConfirmed`).
- `AppViewModel.kt:598-610` — `exportZip` / `exportCoursesZip` re-zip the **internal** dirs
  (`filesDir/pathologies`, `filesDir/courses`) to a SAF Uri.
- `data/ZipCompressor.kt:23` — `openOutputStream(destUri, "w")`. **`"w"` does not truncate** on all
  providers: overwriting a larger existing file leaves trailing bytes.

### The TCP hole
- `AppViewModel.kt:421-454` `connectTcp` → **`sendUploadArchive()` at `:434`** on every connect, then a
  drain loop; auto-reconnects every 5 s (`:450`).
- `AppViewModel.kt:505-532` `sendUploadArchive` — **zips `filesDir/pathologies` to `cacheDir/upload.zip`
  first** (`:511`, `ZipCompressor.zipToCache`), writes a one-line JSON header
  (`{"type":"upload","filename":"Pathologies.zip","size":…}`, `:514-521`), streams the raw bytes
  (`:522-524`), deletes the temp (`:528`). No encryption, no ack, no prompt; exceptions **silently
  swallowed** (`:526`); fire-and-forget on `viewModelScope`.
- `SettingsDialog.kt:248-349` — IP/port free-text + connect button, **not gated by anything**;
  `:100-105` a `LaunchedEffect` **auto-applies** a valid ip/port after a 1 s debounce; ip/port persist
  (`AppViewModel.kt:225-233`). `MainScreen.kt:251-257` shows the dialog with no role/edition gate.
- **Courses are excluded from TCP only structurally** — `:508` hardcodes `PATHOLOGIES_DIR` and
  `filesDir/courses` is a *sibling*, never a child, so `walkTopDown` never reaches it. There is **no**
  `uploadCourses` method (the `android-exclude-courses-tcp-sync-2026-06` memory note claiming a dead
  method is **stale** — it is already gone; only a verify/test step remains).

## Non-goals

- **The pack pipeline, the sources, and the writable overlay** — the companion CSP2 plan owns all of it.
- **`CSD1` decoding** — `active/2026-07-android-delta-binary-dat-format-parity.md`.
- **The unsaved-changes mode-switch guard** — separate active plan; do not conflate. (Android has only
  the bare `updateOperatingMode`, `AppViewModel.kt:333-337`.)
- **Removing the constructors from the codebase.** They stay compiled; only the *edition* hides them.
- Machine/licence binding, server-delivered content, obfuscation hardening beyond enabling R8.

## Plan

### Phase 1 — Edition scaffolding *(no behaviour change yet)*

**Use a flavor source set, not just a `buildConfigField`.** With `isMinifyEnabled = false`
(`build.gradle.kts:23`), a `BuildConfig` boolean is a *runtime* value: every constructor screen,
ViewModel and string still ships inside the "limited" APK and only the picker hides them — materially
weaker than Windows, where `#if LIMITED` removes the code outright. A **`const val`** in a flavor
source set folds at compile time even with minify off.

- `build.gradle.kts:35-37` — add **`buildConfig = true`** (required under AGP 9; without it
  `buildConfigField` silently yields nothing and `BuildConfig` won't resolve).
- `build.gradle.kts:21` — `flavorDimensions += "edition"` and
  `productFlavors { create("full") { dimension = "edition" }; create("limited") { dimension = "edition";
  applicationIdSuffix = ".limited"; versionNameSuffix = "-limited" } }` *(suffix = a product decision,
  see Risks)*.
- `app/src/full/java/com/example/cardiosimulator/domain/AppEdition.kt` →
  `object AppEdition { const val IS_LIMITED = false }`; the `limited` source set → `true`.
  Mirrors Windows `AppEdition.IsLimited`. Turn `isMinifyEnabled = true` on for `limited` as
  belt-and-braces.
- `domain/OperatingModeModel.kt:6-15` — add the single source of truth:
  `val OperatingMode.isAuthoring get() = this == Constructor || this == CourseConstructor ||
  this == TestConstructor || this == OSKEConstructor`.

### Phase 2 — Hide the constructor modes

- `MainActivity.kt:42-44` — filter the builder feed:
  `OperatingMode.entries.filter { !AppEdition.IS_LIMITED || !it.isAuthoring }.forEach { … }`.
  Nothing else is needed for the picker: `TopControlPanel.kt:92-104` renders whatever the list holds.
- **Do not delete enum entries** — `MainScreen.kt:291-348` and `TopControlPanel.kt:109-162` are
  exhaustive `when`s with no `else` and will stop compiling.
- Centralise the filter (e.g. `AppBuilder.addAvailableModes()` or an `OperatingMode.availableForEdition()`
  extension) and apply it at **all three** call sites, or the `@Preview`s diverge from the shipped list:
  `MainActivity.kt:42-44`, `MainActivity.kt:104-106`, `MainScreen.kt:416-417`.
- Harden `TeachingScreen.kt:583-584` / `:593-594`: `find { … }!!` → `?.let { … }`. Testing/Examination
  survive the filter today, so this is not yet a crash — but it is a landmine for any future edition.

### Phase 3 — Hide Settings data import/export

- `SettingsDialog.kt:353-431` — wrap the four controls in `if (!AppEdition.IS_LIMITED) { … }`. It is one
  contiguous region; there is no other Settings surface.
- **Decide `DataSourceScreen.kt:67-82`** (the first-run gate, `MainScreen.kt:241-249`). Hiding both
  import surfaces without shipping/seeding a dataset **locks a Limited build out of the app entirely**.
  Options: (a) Limited keeps the first-run pick and only loses the Settings re-pick/export;
  (b) Limited ships a bundled pack and skips the gate. **Resolve jointly with the companion plan's
  bundled-pack decision** — the two are the same question.

### Phase 4 — Close the TCP egress *(the exfiltration fix)*

- `AppViewModel.kt:434` — guard `sendUploadArchive()` so **nothing is uploaded when a pack is loaded**
  (use the companion plan's pack-mode signal; if Phase 4 lands first, gate on `AppEdition.IS_LIMITED`
  as an interim and tighten later). Keep the socket + command channel (start/stop/points) live —
  the drain loop at `:437-440` and the reader/writer split must stay intact.
- `AppViewModel.kt:505-532` — **delete the `cacheDir/upload.zip` copy** (`:511`) and stream the archive
  from memory. Today a 1.7 GB dataset is copied in full, in plaintext, into the cache with no progress,
  no cancel and no free-space check.
- Consider gating the TCP block itself (`SettingsDialog.kt:248-349`) and/or removing the 1 s auto-apply
  (`:100-105`) so connecting is at least deliberate. **Open question** — see Risks.
- Add a regression test asserting `courses` never enters the upload: the exclusion is only structural
  (`:508`), asserted by nothing, and a refactor to a shared parent dir would silently start shipping
  courses.

### Phase 5 — Export policy

- `AppViewModel.kt:598-610` — in pack mode, export an **encrypted `.pak`** of the merged view via the
  companion plan's CSP2 **writer** (its Phase 3 overlay work), not a plaintext ZIP. Default the SAF
  `CreateDocument` name to `*.pak`. File/author mode keeps exporting a ZIP.
  **Interlock:** this phase cannot land before the companion plan's writer exists.
- `ZipCompressor.kt:23` — `"w"` → `"wt"` so an overwrite truncates.

## Risks & open questions

1. **[RISK] Minify-off makes a runtime flag hollow.** `isMinifyEnabled = false`
   (`build.gradle.kts:23`) ⇒ a `BuildConfig.IS_LIMITED` boolean still ships every constructor screen,
   ViewModel and string in the limited APK. **Mitigation:** flavor source set + `const val` (folds at
   compile time), and enable R8 for `limited`. Without this, "Limited" is UI hiding, not capability
   removal — say so plainly to the customer rather than implying Windows-grade parity.
2. **[OPEN — product] `applicationIdSuffix`.** With the suffix, Full and Limited install **side by side**
   as separate apps; without it they **conflict** and cannot coexist on one device. Windows has no
   analogue (one app, two installers). Needs a product decision, not an engineering one.
3. **[RISK] Signing.** `build.gradle.kts:28` signs release with the **debug** key. Neither edition is
   Play-publishable as-is; a student edition intended for distribution needs a real `signingConfig`.
   Windows never forced this question.
4. **[RISK] Build/CI breakage.** Adding any flavor **deletes `assembleDebug`/`assembleRelease`** and
   renames every variant task (`assembleLimitedRelease`, …); `app/build/outputs/apk/` gains a flavor
   subdirectory. Any script/doc/CI referencing the old task or path breaks. Windows' analogue is
   `build-limited.ps1`; Android has no build-script wrapper at all — document the new commands.
5. **[OPEN] Does Limited keep the TCP UI at all?** The link's only payload today is the dataset upload;
   with the upload gated, TCP still drives a paired display via start/stop. If the display is not a
   student feature, gating `SettingsDialog.kt:248-349` in Limited is simpler and strictly safer.
6. **[OPEN] `DataSourceScreen` lockout** — see Phase 3; joined to the companion plan's bundled-pack
   decision.
7. **[NOTE]** `lint { disable += "MissingTranslation" }` (`:38-40`) means unused/untranslated mode
   strings are not flagged either way; leaving the 8 mode titles in both editions is harmless.

## Verification

- **Phase 1–2:** `./gradlew assembleLimitedRelease` → the mode dropdown offers exactly
  **Teaching / Testing / Examination / OSKE**; `assembleFullRelease` still offers all 8. Both
  `@Preview`s match the shipped list. App starts (no `AppBuilder.kt:12` `check` throw).
- **Phase 3:** Limited Settings shows no import/export controls; Full is unchanged; a Limited build can
  still reach a dataset (whichever Phase-3 option is chosen).
- **Phase 4 (the one that matters):** with a pack loaded, point the TCP target at a local listener
  (`nc -l`) → **zero bytes of dataset are sent**; start/stop still arrive; `cacheDir` never gains
  `upload.zip`. Repeat in **both** editions — the hole is not edition-specific. Automated test: courses
  never appear in the upload archive.
- **Phase 5:** an export from a pack build produces a file that **fails to open as a ZIP** and **does
  open in the Windows app**; an overwrite of a larger file leaves no trailing bytes.
- **Decompile check (honesty):** `apktool`/`jadx` the limited APK and confirm the constructor screens
  are actually absent (they will **not** be if you relied on a runtime `BuildConfig` flag — risk 1).

## PR breakdown

| # | PR title                                                              | Phase | Notes |
|---|-----------------------------------------------------------------------|-------|-------|
| 1 | Edition scaffolding: `buildConfig`, flavors, source-set `AppEdition`   | 1     | Renames assemble tasks (risk 4) |
| 2 | Hide authoring modes (`isAuthoring` + filter the builder feed)         | 2     | 3 call sites; harden `find{}!!` |
| 3 | Gate Settings data import/export                                       | 3     | `SettingsDialog.kt:353-431` + DataSourceScreen call |
| 4 | TCP: no dataset upload in pack mode; stream from memory                | 4     | The exfiltration fix + courses test |
| 5 | Export `.pak` in pack mode; `ZipCompressor` `"wt"`                     | 5     | **Blocked on the CSP2 plan's writer** |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:** —
- **PRs:** —
- **Deviations from plan:** —
- **Follow-ups spawned:** —
