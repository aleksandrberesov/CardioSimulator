# Show a loading indicator while the 3D heart viewer spins up (Android parity)

**Status:** DONE (2026-06-30)
**Owner:** AI Assistant
**Started:** 2026-06-30

**Direction:** **Windows → Android.** The Windows port (`CardioSimulatorWin`) shipped this on
2026-06-30; Android must catch up. The Windows port is the reference for *behaviour/intent* — match the
user-visible result (a waiting element appears immediately, goes away once the heart is ready), adapting
the mechanism to Kotlin/Compose + WebView.

> **Important platform divergence (read before porting):** the Windows fix defers a *synchronous DirectX
> device build* (`new DefaultEffectsManager()`) that froze the UI thread before the overlay even
> appeared, by painting a `ProgressRing` first and constructing the viewport one frame later. **Android
> has no such synchronous build** — the `Heart3DDialog` is a Compose `Dialog` that opens instantly. The
> slow, feedback-less part on Android is *inside* the viewport: the `WebView` inflates, fetches Google's
> `model-viewer.min.js` from a CDN, then downloads/parses the `.glb` — and the white viewport box sits
> **blank** the whole time. So do **not** try to literally port `WaitForNextFrameAsync` / a deferred
> build; there's nothing to defer. Port the *intent*: overlay a waiting indicator on the viewport,
> visible from the moment the dialog opens, dismissed when the model reports `load`.

---

## Goal

When the user taps **3D** on the monitor control panel, give immediate visual feedback that the heart is
loading, instead of a blank white square. Concretely: overlay a Compose `CircularProgressIndicator` +
"Loading 3D heart…" caption on the 3D viewport area; remove it once the `<model-viewer>` fires its
`load` event (or show an error state on `error`).

### Why now

The Windows port made this change on 2026-06-30 (it was visibly janky there — the click appeared to do
nothing for a beat). Android has the *same UX symptom* via a different cause: the `<model-viewer>`'s own
HTML `<div slot="poster">` ("Loading 3D Heart...") only appears **after** the WebView has loaded its HTML
and the CDN script has initialised — the gap *before* that (WebView construction + remote script fetch)
shows nothing. A native Compose overlay closes that gap and reads consistently with the rest of the app's
loading states (`CircularProgressIndicator` + a localized caption, like `DataSourceScreen`).

## Current state (Android)

- **The button** — `ui/panels/MonitorControlPanel.kt:302-308`. A `Tab(text = "3D", …, onClick = {
  viewModel.setShow3D(!monitorMode.show3D) }, isActive = monitorMode.show3D)`. Toggles a flag; no loading
  state today.
- **The flag** — `ui/viewmodels/MonitorViewModel.kt:226-228`: `fun setShow3D(show: Boolean) {
  _monitorMode.update { it.copy(show3D = show) } }`.
- **The dialog gate** — `ui/screens/TeachingScreen.kt:275-277`: `if (mode.show3D) { Heart3DDialog(onDismiss
  = { monitorViewModel.setShow3D(false) }) }`. Opens **instantly** (lightweight Compose `Dialog`).
- **The dialog** — `ui/dialogs/Heart3DDialog.kt`. A `Dialog { Surface(Cream) { Column { header row;
  three-column Row } } }`. The right column (`:94-113`) is a white `Surface` (`aspectRatio(1f)`,
  `Color.White`, light-gray border) that hosts `Heart3DViewer(Modifier.fillMaxSize(), modelPath =
  "heart3d/heart.glb")`, with an `ECG lead` button beneath. **This white Surface is the box that sits
  blank while loading.**
- **The viewer** — `ui/components/Heart3DViewer.kt`. An `AndroidView` wrapping a `WebView`:
  - Loads inline HTML (`loadDataWithBaseURL`, `:136`) that pulls
    `model-viewer/3.5.0/model-viewer.min.js` **from `ajax.googleapis.com`** (`:69` — a network fetch) and
    points `<model-viewer src=…/assets/$modelPath>` at the bundled `.glb` (`:93`).
  - Already has a `<div slot="poster">` "Loading 3D Heart..." (`:99-104`) — but that's model-viewer's
    *own* poster, shown only after the script initialises.
  - Already wires JS `load` / `error` listeners (`:115-123`) — but they only `console.log` /
    toggle an in-page error div. **Nothing is reported back to Compose.**
  - `onRelease = { it.destroy() }` (`:139`).
- **JS→Compose bridge idiom (mirror this)** — `ui/components/LectureWebView.kt`: `addJavascriptInterface(
  LectureBridge(onCellEdit, onMonitorClick), "Android")` (`:135`) + a small class with `@JavascriptInterface`
  methods that marshal back to the main thread via `Handler(Looper.getMainLooper()).post { … }` (`:164-180`).
- **Spinner idiom (mirror this)** — `ui/screens/DataSourceScreen.kt:213-215`: `CircularProgressIndicator()`
  + `Spacer(8.dp)` + `Text(stringResource(R.string.data_source_loading))`.
- **Colors** — `ui/dialogs/DialogComponents.kt`: `CreamBackground = 0xFFF2EFE6`, `WindowsBlue = 0xFF5B9BD5`.
- **Strings** — `res/values/strings.xml:97-105` has `monitor_3d_*`. Locales `values-{ru,zh,es,hi}` carry
  `monitor_3d_title`. **No** `monitor_3d_loading` anywhere yet.

### Windows reference (what shipped 2026-06-30, the intent to match)

| Concern | Windows file / member |
|---|---|
| Show a spinner *before* the heavy viewport build; build the card one frame later; bail if dismissed mid-load | `src/CardioSimulator.App/Controls/Heart3DDialog.cs` — `ShowCoreAsync` now `async`: adds `BuildLoadingIndicator()` (a `ProgressRing` + caption), `await WaitForNextFrameAsync()` (one `CompositionTarget.Rendering` frame so the spinner paints + the compositor animates it off-thread), then builds `BuildCard(...)` and swaps the spinner out. `Close()` null-guards `_viewport`; bails if `overlay.Parent is null`. |
| Loading string | `src/CardioSimulator.App/Localization/AppStrings.cs` — `Monitor3DLoading` / `monitor_3d_loading`, added in EN/RU/ZH/ES/HI |

**Windows mechanism vs. Android:** Windows' spinner exists to cover a *UI-thread freeze*; once the frame
paints, the `ProgressRing` animates on the compositor thread even while the thread is busy. On Android the
UI thread is never frozen — the gap is *async I/O inside the WebView*. So the Android overlay is dismissed
by an **event** (model `load`), not by "a frame elapsed". Same user-visible result, different trigger.

## Non-goals

- Don't replace the WebView / `<model-viewer>` stack or change the model source/`.glb`.
- Don't remove model-viewer's in-page `<div slot="poster">` — leave it as the inner fallback; the Compose
  overlay simply sits on top and covers the earlier (script-fetch) gap too.
- Don't touch the other monitor overlays (Electrodes/EOS/Tips) or the left/middle dialog columns.
- Don't localise anything beyond the new caption (match Windows, which only added `monitor_3d_loading`).

## Plan

### Phase 1 — `Heart3DViewer`: report load/error back to Compose

Add optional callbacks and bridge the existing JS events (mirror `LectureWebView`'s `LectureBridge`):

```kotlin
@Composable
fun Heart3DViewer(
    modifier: Modifier = Modifier,
    modelPath: String = "heart3d/heart.glb",
    onLoaded: () -> Unit = {},
    onError: () -> Unit = {},
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                …
                addJavascriptInterface(Heart3DBridge(onLoaded, onError), "Android")
                …
            }
        },
        onRelease = { it.destroy() },
    )
}

private class Heart3DBridge(
    private val onLoaded: () -> Unit,
    private val onError: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun onLoaded() = main.post { onLoaded.invoke() }
    @JavascriptInterface fun onError() = main.post { onError.invoke() }
}
```

In the inline HTML's `<script>` (`Heart3DViewer.kt:115-123`), call the bridge from the existing listeners:

```js
modelViewer.addEventListener('load', () => { console.log("Model loaded"); Android.onLoaded(); });
modelViewer.addEventListener('error', (event) => { …existing… ; Android.onError(); });
```

> Guard for `typeof Android !== 'undefined'` if you want the HTML to stay viewable outside the app, but
> since it's only ever hosted in this WebView it's optional.

### Phase 2 — `Heart3DDialog`: overlay the waiting indicator on the viewport

Wrap the viewer in a `Box` and overlay a Compose spinner driven by local state, dismissed on `onLoaded`:

```kotlin
var isLoading by remember { mutableStateOf(true) }   // reset only when the dialog re-opens

Surface(
    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
    shape = RoundedCornerShape(8.dp),
    color = Color.White,
    border = BorderStroke(1.dp, Color.LightGray),
) {
    Box(Modifier.fillMaxSize()) {
        Heart3DViewer(
            modifier = Modifier.fillMaxSize(),
            modelPath = "heart3d/heart.glb",
            onLoaded = { isLoading = false },
            onError = { isLoading = false },   // stop spinning; model-viewer shows its in-page error div
        )
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = WindowsBlue)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.monitor_3d_loading),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
```

Because `Heart3DDialog` is only composed while `mode.show3D` is true and is removed on dismiss, the
`remember { mutableStateOf(true) }` naturally re-initialises to `true` on each open — no manual reset
needed.

### Phase 3 — Strings (mirror the Windows keys exactly)

Add `monitor_3d_loading` to `res/values/strings.xml` and each locale (Windows added EN/RU/ZH/ES/HI):

| File | Value |
|---|---|
| `values/strings.xml` | `Loading 3D heart…` |
| `values-ru/strings.xml` | `Загрузка 3D-сердца…` |
| `values-zh/strings.xml` | `正在加载 3D 心脏…` |
| `values-es/strings.xml` | `Cargando corazón 3D…` |
| `values-hi/strings.xml` | `3D हृदय लोड हो रहा है…` |

(Use the same text as the Windows `AppStrings` entries for a clean cross-platform match.)

## Risks & open questions

- **`load` may never fire (offline / CDN blocked):** the `model-viewer.min.js` is fetched from
  `ajax.googleapis.com`; with no network the script never initialises and neither `load` nor `error`
  fires from it, leaving the spinner forever. **Mitigation:** add a `LaunchedEffect(Unit)` timeout in the
  dialog (e.g. `delay(15_000); isLoading = false`) as a backstop, matching the spirit of the existing
  10 s `setTimeout` warning in the HTML (`Heart3DViewer.kt:126-130`). Consider also wiring the WebView's
  `onReceivedError` → `onError` so a failed page load also clears the spinner. *(Optional but recommended
  — call out if deferred.)*
- **JS calls Android before the bridge is ready:** `addJavascriptInterface` is registered at WebView
  construction, before `loadDataWithBaseURL`, so `Android.onLoaded()` is available by the time the model
  loads. Still, the `typeof Android !== 'undefined'` guard is cheap insurance.
- **Thread marshaling:** `@JavascriptInterface` methods run on the WebView's JS thread — `post` to the
  main looper before touching Compose state (the `LectureBridge` does exactly this). Don't set
  `isLoading` directly from the bridge method.
- **Recomposition / WebView identity:** keep the new params (`onLoaded`/`onError`) out of `update {}`;
  they're only read in `factory`. Don't introduce a `key` that would rebuild the WebView on every
  recompose (that would re-trigger the whole slow load).
- **No behaviour change for the model itself:** orbit/zoom/auto-rotate, the `.glb`, and the in-page poster
  are untouched — this is purely an overlay + an event callback.

## Verification

- `./gradlew :app:assembleDebug` passes.
- Teaching → monitor → tap **3D**: the dialog opens immediately and the right-hand viewport shows a
  centered spinner + "Loading 3D heart…" instead of a blank white box.
- Once the heart renders, the spinner disappears and orbit/zoom/auto-rotate work as before.
- Airplane mode (or block the CDN): the spinner clears via the timeout backstop (Risk #1) rather than
  spinning forever; model-viewer's in-page error div may also show.
- RU/ZH/ES/HI locales show the translated caption; re-opening the dialog shows the spinner again.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | `Heart3DViewer`: `onLoaded`/`onError` via JS bridge | 1 | Mirrors `LectureWebView`'s `LectureBridge` |
| 2 | `Heart3DDialog`: spinner overlay on the viewport | 2 | `Box` overlay + `isLoading` state; timeout backstop |
| 3 | `monitor_3d_loading` strings (EN/RU/ZH/ES/HI) | 3 | Same text as Windows `AppStrings` |

*(Phases 1–2 are coupled — the overlay is useless without the load callback — so PRs 1+2 can ship
together; PR 3 is independent.)*

---

## Cross-reference

Windows session 2026-06-30 (CardioSimulatorWin): made `Heart3DDialog.ShowCoreAsync` async — it shows
`BuildLoadingIndicator()` (a `ProgressRing` + "Loading 3D heart…" caption), awaits one composition frame
(`WaitForNextFrameAsync`) so the spinner paints before the synchronous DirectX viewport build, then swaps
in the real card; `Close()` null-guards `_viewport` and the build bails if dismissed mid-load. Added
`monitor_3d_loading` (EN/RU/ZH/ES/HI). **The Windows spinner masks a UI-thread freeze; the Android spinner
masks async WebView/model loading — same UX, event-dismissed instead of frame-dismissed.**
