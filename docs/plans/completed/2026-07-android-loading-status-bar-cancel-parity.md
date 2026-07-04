# Loading status bar + Cancel button for the pathologies dataset (Android parity)

**Status:** COMPLETED
**Owner:** AI Assistant
**Started:** 2026-07-03
**Finished:** 2026-07-04

**Direction:** **Windows → Android.** The Windows port (`CardioSimulatorWin`) shipped this on
2026-07-03; Android must catch up. Windows is the reference for *behaviour/intent* — match the
user-visible result (an informative progress bar with phase text + a Cancel button while the ECG
dataset extracts), adapting the mechanism to Kotlin/Compose coroutines.

---

## Goal

While the user's `Pathologies.zip` is being extracted, replace the bare
`CircularProgressIndicator` + "Loading data…" with an **informative status bar**:

```
        Extracting ECG records          ← phase heading
   ▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░              ← determinate LinearProgressIndicator
        243 / 1057 records · 23%        ← count + live percentage
            js00243.dat                 ← record currently being unpacked
         [ Cancel ]                     ← abort the extraction
```

and add a **Cancel button** that aborts the extraction, deletes the partial output, and returns to
the "pick a ZIP" screen. Applies to the **pathology** section of `DataSourceScreen` (the courses
section can keep the simple spinner — out of scope, matching the Windows change which only touched
pathologies).

### Why now

The Windows port made this change on 2026-07-03 after the customer asked for "a status bar showing
info about the loading process" plus a way to abort. Extracting a real dataset (the reference zip is
~1057 records / 54 MB) takes several seconds — long enough that a featureless spinner is poor UX and
there was no way to cancel a mistaken/huge pick.

---

## ⚠️ Two Windows-only changes — do NOT port (read first)

The Windows session made two fixes that are **specific to WinUI and have no Android analogue**. Porting
them would be wrong.

1. **White-on-white text fix — Windows-only.** On Windows the shell forces `RequestedTheme = Dark`
   globally while the design tokens are light-oriented, so the `DataSourceScreen` text was rendering
   *white on a white background* (invisible) until explicit `Background` + `Foreground` brushes were
   added. **Android does not have this bug**: `DataSourceScreen.kt` already sets `PageBackground`,
   `TextPrimary`, `TextSecondary` explicitly and Compose theming resolves correctly. Text is already
   visible. Just reuse the same `TextPrimary`/`TextSecondary` colors for the new status lines — nothing
   to "fix".

2. **"Make the manifest load async so it paints" — Windows-only.** On Windows the returning-user
   startup did a *synchronous* manifest read on the UI thread, so the loading screen never painted;
   the fix moved it to a background thread (`ReloadAsync`). **Android is already fully async**:
   `AppViewModel.loadFromSaf()`/`reload()` run in `viewModelScope` with
   `withContext(Dispatchers.IO) { … }`, and Compose recomposes reactively from the `dataState`
   `StateFlow`. There is no UI-thread freeze and nothing to restructure. **Only add progress + cancel.**

A third, smaller divergence (real work, not a skip) — **total entry count**: Windows gets it free from
`ZipArchive.Entries.Count`; Android's `ZipInputStream` is a forward-only stream with no upfront count.
See Phase 1 for the count strategy.

---

## Current state (Android)

- **Extractor** — `data/PathologyZipExtractor.kt`. `fun extract(context, zipUri, targetDir): Boolean`
  wrapped in `runCatching`. Deletes `targetDir` first, then streams entries via
  `ZipInputStream(input, UTF_8)`, flattening names (`substringAfterLast('/')`). **No progress, no
  cancellation, no total count.**
- **ViewModel** — `ui/viewmodels/AppViewModel.kt`:
  - `DataState` (`:61-68`): `NotConfigured | Loading | Ready(count) | Error(reason)` — same as Windows.
  - `_dataState: MutableStateFlow<DataState>` (`:129`), exposed as `dataState` (`:130`).
  - `setDataFolder(context, uri)` (`:481`) → `viewModelScope.launch { setTreeUri; loadFromSaf(…, forceUnzip=true) }`.
  - `loadFromSaf()` (`:554`): sets `_dataState = Loading`, then
    `withContext(Dispatchers.IO) { PathologyZipExtractor.extract(context, uri, targetDir) }`, then
    `reload(repo)`. **Already off the main thread; UI recomposes from the flow.**
  - `reload()` (`:579`): `loadManifest()` on IO → `Ready(count)` / `Error(...)`.
  - `confirmData()` (`:531`).
- **Screen** — `ui/screens/DataSourceScreen.kt`. `DataSourceSection(...)` `when (state)`:
  - `DataState.Loading -> CircularProgressIndicator(); Spacer; Text(R.string.data_source_loading)`
    (`:212-216`). **This is the block to enrich (pathology section only).**
  - Uses explicit `TextPrimary` / `TextSecondary` / `PageBackground` already (`:87,102,199,207,215,233`).
  - The `state` is passed in from the caller (`MainActivity`/host collects `dataState`); the section is
    a stateless `@Composable` fed `state: DataState`. To surface progress, feed it an extra
    `loadingInfo` (see Phase 3) alongside `state`, or collect it inside the section from `appViewModel`.
- **Strings** — `res/values/strings.xml`: `data_source_loading` (`:330`), `data_source_loaded_format`
  = `Loaded %1$d pathologies` (`:331`). Cancel labels already exist elsewhere: `cd_cancel`,
  `constructor_save_cancel`, `constructor_rename_cancel` (all "Cancel"). Locales: `values-{ru,zh,es,hi}`.
- **Test idiom** — `app/src/test/java/com/example/cardiosimulator/data/ZipCompressorTest.kt` is a
  **pure-JVM** unit test (no Robolectric). Mirror it for the extractor by adding an
  `InputStream`-based overload (Phase 1) so the test needs no `Context`.

### Windows reference (what shipped 2026-07-03, the intent to match)

| Concern | Windows file / member |
|---|---|
| Progress + cancellation in the extractor | `src/CardioSimulator.Core/Data/ZipExtractor.cs` — added `ZipProgress(Done, Total, CurrentEntry)` record; `Extract(…, IProgress<ZipProgress>?, CancellationToken)`; throttles to ~100 reports (`step = max(1, total/100)`); `token.ThrowIfCancellationRequested()` each entry; on cancel deletes the partial `targetDir` then rethrows |
| Loading state + cancel plumbing | `src/CardioSimulator.App/ViewModels/AppViewModel.cs` — `LoadingTitle` / `LoadingStatus` / `LoadingDetail` / `LoadingProgress` / `LoadingIsIndeterminate` / `CanCancelLoading`; `BeginLoading()` (arms a `CancellationTokenSource`, shows Cancel); `OnExtractionProgress` (marshals to UI thread, computes %); `CancelLoading()`; `OnLoadingCancelled()` (revert to `NotConfigured`). Wired into `SetDataFolderAsync` + the bundled-seed + startup paths |
| Status-bar UI + Cancel button | `src/CardioSimulator.App/Screens/DataSourceScreen.xaml(.cs)` — phase heading + determinate `ProgressBar` + count/percent line + monospace record line + Cancel button (visible only while `CanCancelLoading`) |
| Strings | `src/CardioSimulator.App/Localization/AppStrings.cs` — `data_source_extracting_title` = "Extracting ECG records", `data_source_records_format` = `"{0} / {1} records · {2}%"`; reused the existing cancel string; EN/RU/ZH/ES/HI |
| Tests | `tests/CardioSimulator.Core.Tests/ZipExtractorTests.cs` — 3 tests: all-entries + final progress; pre-cancelled token throws + cleans up; mid-extract cancel throws + removes partial output |

**Windows mechanism vs. Android:** Windows marshals progress from a background `Task.Run` onto the UI
thread via `DispatcherQueue.TryEnqueue`, and cancels via `CancellationToken`. **Android is simpler**:
`MutableStateFlow.value = …` is safe to set from the IO dispatcher (Compose collects on main — no
`TryEnqueue` analogue needed), and cancellation is cooperative coroutine cancellation (cancel the
stored extraction `Job`; the extractor calls `ensureActive()`). Same user-visible result.

## Non-goals

- Don't add progress/cancel to the **courses** extractor/section (`CourseZipExtractor`,
  `loadCoursesFromSaf`) — Windows only did pathologies.
- Don't touch colors/theme of `DataSourceScreen` (see divergence #1 — Android text is already visible).
- Don't restructure `loadFromSaf`/`reload` threading (see divergence #2 — already async).
- Don't change the flat-extraction semantics, the SAF pick flow, or `confirmData()` gating.

## Plan

### Phase 1 — `PathologyZipExtractor`: progress + cancellation + testable overload

Two problems to solve: (a) `ZipInputStream` gives no total up front; (b) cancellation.

**(a) Total count** — do a cheap first pass that *counts* entries by reopening the SAF stream, then a
second pass that extracts and reports `done/total`. (Counting reads only the central-directory-less
stream headers; it's fast relative to writing files. Alternative: copy the SAF stream to a cache
`File` and use `java.util.zip.ZipFile`, which exposes `.size()` and random iteration — pick this if a
double SAF open proves flaky.)

**(b) Cancellation** — make `extract` a `suspend fun` and call `currentCoroutineContext().ensureActive()`
each entry. Cancelling the ViewModel's stored `Job` (Phase 2) throws `CancellationException`; delete the
partial `targetDir` in a `catch` and rethrow so the caller can revert.

Add an `InputStream`-based core so the unit test needs no `Context`:

```kotlin
data class ZipProgress(val done: Int, val total: Int, val currentEntry: String?)

object PathologyZipExtractor {

    /** SAF entry point. Counts entries (first pass) then extracts with progress (second pass). */
    suspend fun extract(
        context: Context,
        zipUri: Uri,
        targetDir: File,
        onProgress: ((ZipProgress) -> Unit)? = null,
    ): Boolean {
        val total = countEntries(context, zipUri)          // reopen stream, count, close
        val input = context.contentResolver.openInputStream(zipUri) ?: return false
        return input.use { extractStream(it, targetDir, total, onProgress) }
    }

    private fun countEntries(context: Context, zipUri: Uri): Int =
        context.contentResolver.openInputStream(zipUri)?.use { inp ->
            ZipInputStream(inp, Charsets.UTF_8).use { z ->
                var n = 0; while (z.nextEntry != null) { n++; z.closeEntry() }; n
            }
        } ?: 0

    /** Testable core: extracts [input] into [targetDir], flattening, reporting throttled progress. */
    suspend fun extractStream(
        input: InputStream,
        targetDir: File,
        total: Int,
        onProgress: ((ZipProgress) -> Unit)? = null,
    ): Boolean {
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()
        val step = maxOf(1, total / 100)                    // ≤ ~100 callbacks
        var done = 0
        try {
            ZipInputStream(input, Charsets.UTF_8).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    currentCoroutineContext().ensureActive()     // cooperative cancel
                    if (!entry.isDirectory) {
                        val name = entry.name.substringAfterLast('/')
                        FileOutputStream(File(targetDir, name)).use { out -> zin.copyTo(out) }
                    }
                    done++
                    if (onProgress != null && (done == total || done % step == 0))
                        onProgress(ZipProgress(done, total, entry.name.substringAfterLast('/')))
                    zin.closeEntry()
                    entry = zin.nextEntry
                }
            }
            return true
        } catch (ce: CancellationException) {
            runCatching { if (targetDir.exists()) targetDir.deleteRecursively() }  // no half dataset
            throw ce
        } catch (t: Throwable) {
            return false
        }
    }
}
```

> `extractStream` is `suspend` only to reach `currentCoroutineContext()`; it does no real suspension.
> Keep `CancellationException` rethrown (never swallow it into `false`) so structured concurrency and
> the caller's revert both work.

### Phase 2 — `AppViewModel`: loading progress state + cancel

Expose a single immutable snapshot as a `StateFlow`, keep the extraction `Job`, add `cancelLoading()`:

```kotlin
data class LoadingInfo(
    val title: String = "",
    val statusLine: String = "",     // "243 / 1057 records · 23%"
    val detail: String = "",         // current record file
    val percent: Int = 0,
    val indeterminate: Boolean = true,
    val canCancel: Boolean = false,
)

private val _loadingInfo = MutableStateFlow(LoadingInfo())
val loadingInfo: StateFlow<LoadingInfo> = _loadingInfo.asStateFlow()

private var extractionJob: Job? = null

fun cancelLoading() { extractionJob?.cancel() }

fun setDataFolder(context: Context, uri: Uri) {
    val p = prefs ?: return
    _isDataConfirmed.value = false
    extractionJob = viewModelScope.launch {
        p.setTreeUri(uri)
        try {
            loadFromSaf(context, uri, forceUnzip = true)
        } catch (ce: CancellationException) {
            _loadingInfo.value = LoadingInfo()
            _dataState.value = DataState.NotConfigured        // partial dir already deleted by extractor
            throw ce                                          // preserve cancellation
        }
    }
}
```

In `loadFromSaf`, set the initial "preparing" info + `canCancel = true`, pass the progress callback, and
clear `canCancel` before the (non-cancellable) manifest read:

```kotlin
_dataState.value = DataState.Loading
_loadingInfo.value = LoadingInfo(
    title = context.getString(R.string.data_source_preparing),
    indeterminate = true, canCancel = true,
)
val ok = PathologyZipExtractor.extract(context, uri, targetDir) { p ->
    val pct = if (p.total > 0) p.done * 100 / p.total else 0
    _loadingInfo.value = LoadingInfo(
        title = context.getString(R.string.data_source_extracting_title),
        statusLine = context.getString(R.string.data_source_records_format, p.done, p.total, pct),
        detail = p.currentEntry ?: "",
        percent = pct, indeterminate = false, canCancel = true,
    )
}
_loadingInfo.value = _loadingInfo.value.copy(
    title = context.getString(R.string.data_source_loading_manifest),
    indeterminate = true, canCancel = false, detail = "", statusLine = "",
)
```

> Note: `PathologyZipExtractor.extract` is now `suspend` and drives IO itself, so the outer
> `withContext(Dispatchers.IO) { … }` wrapper around it in `loadFromSaf` is no longer required (move
> the `Dispatchers.IO` inside the extractor, or make `extract` switch context internally). The
> `_loadingInfo.value = …` writes are safe from any thread. Keep `reload()` as-is (divergence #2).

### Phase 3 — `DataSourceScreen.kt`: the status bar + Cancel

Thread `loadingInfo` into the pathology section (add a param, collected by the caller) and rewrite only
the `DataState.Loading` branch. Courses section keeps the plain spinner.

```kotlin
is DataState.Loading -> {
    Text(info.title.ifEmpty { stringResource(R.string.data_source_loading) },
         style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    Spacer(Modifier.height(8.dp))
    if (info.indeterminate)
        LinearProgressIndicator(Modifier.fillMaxWidth(0.8f))
    else
        LinearProgressIndicator({ info.percent / 100f }, Modifier.fillMaxWidth(0.8f))
    if (info.statusLine.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(info.statusLine, color = TextPrimary)
    }
    if (info.detail.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(info.detail, style = MaterialTheme.typography.bodySmall,
             fontFamily = FontFamily.Monospace, color = TextSecondary)
    }
    if (info.canCancel) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { appViewModel.cancelLoading() }) {
            Text(stringResource(R.string.data_source_cancel))
        }
    }
}
```

Collect once in `DataSourceScreen(...)`: `val info by appViewModel.loadingInfo.collectAsState()` and
pass `info` to the **pathology** `DataSourceSection` (give courses a default `LoadingInfo()`).

### Phase 4 — Strings (EN/RU/ZH/ES/HI)

Add to `res/values/strings.xml` and each locale. **Gotchas:** Android uses positional args `%1$d`
etc., and a **literal percent sign must be escaped as `%%`**.

| key | en (values) | ru | zh | es | hi |
|---|---|---|---|---|---|
| `data_source_preparing` | `Preparing…` | `Подготовка…` | `正在准备…` | `Preparando…` | `तैयारी हो रही है…` |
| `data_source_extracting_title` | `Extracting ECG records` | `Извлечение записей ЭКГ` | `正在解压心电图记录` | `Extrayendo registros ECG` | `ईसीजी रिकॉर्ड निकाले जा रहे हैं` |
| `data_source_loading_manifest` | `Loading pathology list…` | `Загрузка списка патологий…` | `正在加载病理列表…` | `Cargando lista de patologías…` | `विकृति सूची लोड हो रही है…` |
| `data_source_records_format` | `%1$d / %2$d records · %3$d%%` | `%1$d / %2$d записей · %3$d%%` | `%1$d / %2$d 条记录 · %3$d%%` | `%1$d / %2$d registros · %3$d%%` | `%1$d / %2$d रिकॉर्ड · %3$d%%` |
| `data_source_cancel` | `Cancel` | `Отмена` | `取消` | `Cancelar` | `रद्द करें` |

(Reusing the existing `cd_cancel` is acceptable, but a dedicated `data_source_cancel` keeps the screen
self-contained and matches how Windows scoped it.)

### Phase 5 — Test (pure JVM, mirror `ZipCompressorTest`)

`app/src/test/.../data/PathologyZipExtractorTest.kt` using `extractStream(InputStream, …)`:
build an in-memory zip with N entries via `ZipOutputStream`, then assert with `runTest`/`runBlocking`:

- **All entries + final progress:** extracts all files; last `ZipProgress.done == total`.
- **Pre-cancelled:** run inside a job cancelled before start (or throw from the first `onProgress`);
  expect `CancellationException` and `targetDir` removed.
- **Mid-extract cancel:** cancel once the first progress tick arrives; expect `CancellationException`
  and no partial `targetDir`.

## Risks & open questions

- **Double SAF open for the count pass:** most `content://` providers allow reopening the stream; if a
  provider is single-shot, fall back to the *temp-file + `java.util.zip.ZipFile`* strategy (copy once,
  then `ZipFile.size()` + iterate). Decide per testing; note which you shipped.
- **Cancel wipes existing data:** `extractStream` deletes `targetDir` **first** (pre-existing
  behaviour), so cancelling a *re-pick* leaves the user with no dataset → revert to `NotConfigured`
  (pick screen). This matches Windows. Acceptable; call out if the customer wants "keep old data on
  cancel" (would require extracting to a temp dir then swapping).
- **`CancellationException` must propagate:** never catch-and-swallow it into `false` in the extractor
  or the ViewModel — that breaks structured concurrency and the revert. Catch it explicitly, clean up,
  rethrow.
- **Throttle:** keep `done % step` (`step = max(1, total/100)`) so the `StateFlow` isn't updated
  thousands of times; Compose conflates but the churn is wasteful.
- **Percent string escaping:** forgetting `%%` yields a build-time/format crash
  (`UnknownFormatConversionException`) — double-check every locale.

## Verification

- `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` pass (new extractor test green).
- First run / re-pick via **Change ZIP** with a large dataset: the pathology section shows the phase
  heading, a determinate bar, `N / M records · P%`, and the current record file name updating live.
- **Cancel** mid-extraction returns to the "Select ZIP archive" screen; no half-extracted dataset is
  left in `filesDir/pathologies` (verify the dir is gone/empty).
- Small/instant datasets still land on `Ready` → Continue as before.
- RU/ZH/ES/HI show translated phase text and a correctly-formatted `%` line.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | `PathologyZipExtractor`: progress + cancellation + `extractStream` core | 1 | `suspend`; count pass; throttle; delete-on-cancel |
| 2 | `AppViewModel`: `LoadingInfo` StateFlow + `cancelLoading()` | 2 | keep extraction `Job`; feed progress; revert on cancel |
| 3 | `DataSourceScreen`: status bar + Cancel (pathology section) | 3 | LinearProgressIndicator + 3 text lines + OutlinedButton |
| 4 | Strings EN/RU/ZH/ES/HI | 4 | mind `%%` + positional args |
| 5 | `PathologyZipExtractorTest` (pure JVM) | 5 | mirrors `ZipCompressorTest` |

*(PRs 1–3 are coupled; 4 can land with 3; 5 with 1.)*

---

## Cross-reference

Windows session 2026-07-03 (CardioSimulatorWin): added a live loading status bar + Cancel to the
first-run/data-source screen. `ZipExtractor.cs` gained `ZipProgress` + `IProgress` + `CancellationToken`
(throttled, deletes partial on cancel); `AppViewModel.cs` gained
`LoadingTitle/Status/Detail/Progress/IsIndeterminate/CanCancelLoading`, `BeginLoading` (CTS),
`OnExtractionProgress` (dispatcher-marshaled), `CancelLoading`, `OnLoadingCancelled`, plus an async
`ReloadAsync`; `DataSourceScreen.xaml(.cs)` got the status bar UI + Cancel button **and a
white-on-white text fix** (explicit `PageBackgroundBrush` + `TextPrimaryBrush`/`TextSecondaryBrush`);
strings `data_source_extracting_title` + `data_source_records_format` (+ `data_source_preparing`,
`data_source_loading_manifest`) in EN/RU/ZH/ES/HI; 3 `ZipExtractorTests`. **Two of those changes are
WinUI-specific and are deliberately excluded here — the white-on-white fix (Android text is already
visible) and the async manifest read (Android is already coroutine-based).** Android also needs no
UI-thread marshaling (StateFlow) and must add an entry-count pass that Windows got free from
`ZipArchive.Entries.Count`.
