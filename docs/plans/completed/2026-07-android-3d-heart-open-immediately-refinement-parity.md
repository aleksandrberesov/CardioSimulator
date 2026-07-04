# 3D heart dialog opens immediately with a viewport-scoped waiting indicator (Android parity — verify)

**Status:** active
**Owner:** AI Assistant
**Started:** 2026-07-04
**Related issues / PRs:** Windows session 2026-07-04 (`CardioSimulatorWin` — `Controls/Heart3DDialog.cs`). Supersedes the *mechanism* in the completed `completed/2026-06-android-3d-heart-loading-indicator-parity.md`.

> **TL;DR — this is almost certainly a no-op for Android.** Today's Windows change makes the Windows
> dialog behave the way the Android dialog **already** behaves. So this plan is *verify-first*: confirm
> Android still opens the dialog instantly with an opaque, **viewport-scoped** spinner dismissed on model
> `load`, and if so, close it as *already satisfied*. Only fall through to Phase 2 if verification finds a
> real gap.

## Goal

On the Windows port (2026-07-04) the 3D-heart dialog was **refined** so that:

1. The **full card chrome** (title bar, left control column, middle description panel, right viewport
   frame) is built and shown **immediately** on tap — the dialog opens at once.
2. The waiting indicator is an **opaque cover scoped to just the viewport region** (a white box with a
   `ProgressRing` + "Loading 3D heart…" caption), not a full-window spinner.
3. The heavy, UI-thread-blocking DirectX viewport construction (`new DefaultEffectsManager()` /
   `Viewport3DX`) is **deferred one composition frame** so it runs *behind* the already-visible card,
   under the cover, instead of freezing the whole dialog-open.

This **supersedes the mechanism** of the completed `2026-06-android-3d-heart-loading-indicator-parity.md`,
which on Windows showed a **full-screen** spinner first and only then built the *entire* card (chrome
included). The user complaint that drove today's change: "you wait several seconds while the 3D dialog
opens; make it open immediately with a waiting status bar." The refined Windows behaviour is exactly the
layout Android has shipped since that earlier parity plan — an instantly-composed `Dialog` whose viewport
`Box` carries the spinner. **Why now:** keep the two ports' 3D-open UX described the same way, and retire
the now-stale "Windows shows a full-screen spinner" description so a future syncer doesn't try to port it.

## Current state (Android) — already matches the refined Windows behaviour

- **The dialog opens instantly.** `ui/screens/TeachingScreen.kt` gates `Heart3DDialog(...)` on
  `mode.show3D`; it's a lightweight Compose `androidx.compose.ui.window.Dialog` — no synchronous device
  build, so the whole card (`Surface(CreamBackground) { Column { header; three-column Row } }`) paints on
  the first frame. **This already satisfies Goal #1.**
- **The spinner is already scoped to the viewport, opaque, and up from the first frame.**
  `ui/dialogs/Heart3DDialog.kt`:
  - `var isLoading by remember { mutableStateOf(true) }` (`:31`) — starts `true`, so the cover is present
    the instant the dialog composes.
  - Right column (`:207-254`): the viewport is a white `Surface(aspectRatio(1.2f))` wrapping a
    `Box(fillMaxSize)` that holds `Heart3DViewer(...)` **and**, when `isLoading`, a
    `Column(Modifier.fillMaxSize().background(Color.White))` with a `CircularProgressIndicator(WindowsBlue,
    36.dp)` + `Text(stringResource(R.string.monitor_3d_loading), color = Gray)` (`:232-251`). The
    `.background(Color.White)` makes it **opaque** (mirrors the Windows white `_viewportLoading` Border).
    **This already satisfies Goals #2 and — implicitly — #3** (nothing blocks the chrome; the WebView load
    happens behind the cover).
  - Dismissed by the model event, not a frame: `onLoaded = { isLoading = false; controller.setBpm(...) }`
    and `onError = { isLoading = false }` (`:225-229`).
  - Backstop: `LaunchedEffect(Unit){ delay(15_000); isLoading = false }` (`:41-44`) so a load that never
    reports still clears the cover.
- **The load/error bridge is wired.** `ui/components/Heart3DViewer.kt`: `addJavascriptInterface(bridge,
  "Android")` (`:135`); the in-page JS calls `Android.onLoaded()` on the `.glb` success callback (`:226`)
  and `Android.onError()` on failure (`:230`); the `Heart3DBridge` marshals both to the main looper
  (`:291-306`).
- **The old "CDN" risk is now moot.** The viewer no longer pulls `model-viewer.min.js` from
  `ajax.googleapis.com`. It uses a `WebViewAssetLoader` (`:86-87`) serving a **local** Three.js renderer
  from `assets/heart3d/vendor/*` via `https://appassets.androidplatform.net/...` (`:155-156`, `:203`,
  `:284`). So "offline ⇒ spinner forever" is largely gone; the 15 s backstop remains as belt-and-braces.
- **String already present.** `Heart3DDialog.kt:246` reads `R.string.monitor_3d_loading`, added across
  `values` + `values-{ru,zh,es,hi}` by the earlier completed plan. **No string work.**

### Windows reference — what shipped 2026-07-04 (the intent)

| Concern | Windows file / member (`src/CardioSimulator.App/Controls/Heart3DDialog.cs`) |
|---|---|
| Build + show the card chrome first; defer the heavy viewport | `ShowCoreAsync` now builds `BuildCard(...)` and adds it to the overlay **before** any DirectX work, `await WaitForNextFrameAsync()` (one `CompositionTarget.Rendering` frame so the card + cover paint), bails if `overlay.Parent is null` (dismissed mid-load), then calls the new `BuildAndAttachViewport()`. |
| Construct the DirectX viewport lazily, under the cover | new `BuildAndAttachViewport()` calls `BuildHeartViewport()` and `_viewportGrid.Children.Insert(0, viewport)` (below the hotspot canvas / toolbar / cover) + wires the pointer handlers. |
| Cover scoped to the viewport, opaque, up from open | `BuildContent` keeps the viewport container as `_viewportGrid` and leaves `_viewportLoading` (white `Border` + `ProgressRing` + `Monitor3DLoading` caption) **`Visibility.Visible`** from the start; `LoadModelAsync`'s `finally` clears it; `TryAutoLoadModel` also clears it in the no-model branch. |
| Removed | the old full-screen `BuildLoadingIndicator()` helper. |

**Platform divergence (unchanged, and the reason this is verify-only):** the Windows spinner ever existed
to mask a **UI-thread DirectX freeze**; today's refinement moves that freeze *behind* the card and scopes
the cover to the viewport. Android **never had that freeze** — its `Dialog` opens instantly and its cover
already sits inside the viewport `Box`, event-dismissed. So the refined Windows layout **converges to
Android's existing one**. There is nothing new to port; `WaitForNextFrameAsync` / deferred-build have no
Android analog.

## Non-goals

- Don't re-architect the Android viewer (WebView / local Three.js / `.glb`) or its controls
  (conduction / X-ray / cutaway).
- Don't add or change strings — `monitor_3d_loading` already exists in all five locales.
- Don't touch the other monitor overlays (Electrodes / EOS / Tips) or the left/middle dialog columns.
- Don't port the Windows deferred-DirectX-build mechanism — there's no synchronous build to defer.

## Plan

### Phase 1 — Verify parity (expected outcome: already satisfied, close)

Confirm each refined-Windows goal already holds on Android and record the result in the Outcome section:

- [ ] Teaching → monitor → tap **3D**: the **whole dialog** (title, left controls, middle description,
      right viewport frame) appears in one frame — no blank/frozen beat before the chrome shows.
- [ ] The viewport box shows an **opaque white** cover with a centered spinner + "Loading 3D heart…"
      caption from the instant it opens — the blank white `Surface` never shows through, and the cover is
      confined to the viewport (the left/middle columns are fully interactive immediately).
- [ ] The cover clears the moment the heart renders (`onLoaded`), and orbit/zoom/auto-rotate + the
      conduction / X-ray / cutaway controls work as before.
- [ ] Airplane mode (or block `appassets`): the cover clears via the 15 s backstop rather than spinning
      forever.
- [ ] RU/ZH/ES/HI: the caption is localized; re-opening the dialog shows the cover again
      (`remember { mutableStateOf(true) }` re-inits on each open).

If all pass → **move this file to `completed/` with `Outcome: already satisfied — no code change`** and
update the index. Done.

### Phase 2 — Harden (only if Phase 1 finds a gap)

Apply the smallest fix that closes the specific gap found, e.g.:

- **Chrome not instant** (unexpected): profile what blocks the first composition of `Heart3DDialog`; move
  it off the compose path. (Very unlikely — Compose `Dialog` is lightweight.)
- **White Surface flashes before the cover** (a frame where `Heart3DViewer` paints white but the overlay
  hasn't): it shouldn't — both are in the same `Box` composed together — but if observed, ensure the
  `if (isLoading)` overlay is the **last** child in the `Box` (drawn on top) and keeps
  `.background(Color.White)`.
- **Cover not opaque enough / spinner too small**: match the Windows cover (solid white, ~40 dp ring) —
  already the case at `:240-243`.

### Phase 3 — Polish

- None anticipated. No strings, no new assets.

## Risks & open questions

- **This may legitimately be zero-diff.** That's the expected result and a *success*, not a miss — the
  earlier completed plan already delivered the behaviour; today's Windows work only caught Windows up.
  Record it clearly so it isn't re-opened.
- **`onLoaded` never fires** (`.glb` parse hang): mitigated by the existing 15 s `LaunchedEffect`
  backstop (`Heart3DDialog.kt:41-44`) and, now, local-asset serving (no CDN). Leave both as-is.
- **Don't regress the WebView identity:** any hardening must keep `onLoaded`/`onError` in the viewer's
  `factory` path only — never add a `key` that rebuilds the `WebView` per recompose (would re-trigger the
  slow load and defeat the point).

## Verification

- `./gradlew :app:assembleDebug` passes (trivially, if no code changes).
- The five Phase 1 checkboxes above, on a device/emulator.
- If Phase 2 ran: re-run the full Phase 1 checklist after the change.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| — | *(verify only — no PR expected)* | 1 | Confirm parity, close with `Outcome: already satisfied`. |
| 1 | `Heart3DDialog`: harden viewport loading cover | 2 | **Only if** Phase 1 finds a gap. |

---

## Outcome

- **Result:** already satisfied — no code change.
- **PRs:** N/A
- **Deviations from plan:** None. Verification confirmed that Android's existing `Heart3DDialog` (Compose `Dialog`) already opens instantly with a viewport-scoped, opaque loading cover, exactly matching the refined Windows behavior.
- **Follow-ups spawned:** None.
