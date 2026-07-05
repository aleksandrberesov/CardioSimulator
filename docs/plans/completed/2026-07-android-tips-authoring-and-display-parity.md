# Plan — Tips: authoring + on-monitor display (Windows→Android parity)

**Status:** completed
**Owner:** a.beresov
**Started:** 2026-07-04
**Related:** Windows→Android feature parity. **Source of truth = the Windows port** (`CardioSimulatorWin`). Supersedes the earlier `2026-06-android-tips-window-parity` plan (that ported the *palette window*, which Windows has since **retired**).

## Goal

Bring the Android **tips (подсказки)** feature up to the Windows port, which grew from a palette scaffold into a full annotation system across four rounds:

1. A **tips element palette** with 9 kinds (arrow, whole-lead highlight, rectangular area, **freeform area**, ECG-part slice, vertical/horizontal guide lines with **end-cap variants**, label, **points**), plus a **lead picker** for the whole-lead highlight — hosted **in the Constructor** (not a floating window).
2. A **data-space overlay model** placed by drawing on the editable lead, **persisted** in the `.dat`, and **rendered** live on the trace.
3. Overlays **rendered on the monitor grid** (Teaching + all-leads preview) in each lead's cell, plus a **text comments/explanations window** whose numbered "Видим:" list is saved and shown as a card on the monitor.
4. The monitor **Tips button = a visibility toggle** (show/hide authored overlays + comments) that **highlights** when active.

**Why now:** the customer is actively iterating this feature on Windows and wants Android to match. Android currently has only the round-0 scaffold (a palette overlay window + a `selectedTipKind`), so it's several rounds behind.

## Current state (Android)

Android already has a **partial scaffold** that mirrors the *original* Windows `TipsWindow` (which Windows has since deleted). Reconcile with it rather than duplicating:

- **Model** — `domain/MonitorModeModel.kt`:
  - `enum class TipOverlayKind { Arrow, LeadArea, GraphArea, EcgPart, VerticalLines, HorizontalLines, Label }` (`:41-49`) — **7 kinds, missing `FreeformArea` + `Points`**, and no end-cap enum.
  - `MonitorModeModel` has `showTips: Boolean = false` (`:79`) and `selectedTipKind: TipOverlayKind = Arrow` (`:81`).
- **`domain/Pathology.kt`** — `PathologyFile` (`:71-81`) has `significantPoints` but **no `tips` / `tipComments`**. `LeadStream` (`:52-68`). No `TipPoint` / `TipOverlay` data class, no `TipLineEndCap`.
- **Persistence** — `domain/PathologyParser.kt`: `parsePathology` (`:89-123`) reads `markers:` (`:101`, `parseMarkers` `:209`); `serializePathology` (`:125-168`) writes `markers:` (`:145-152`). **No `tips:` / `tip_notes:` handling.** (Note: Android has no `elements:` field — Windows does; ignore that, it's unrelated.)
- **Authoring** — `ui/viewmodels/ConstructorViewModel.kt`: `enum class ToolMode { Select, Trace, Position, Points, Photo, Pan }` (`:32-39`) — **no `Tips`**. `setToolMode` (`:111`). No tip mutators/state.
- **Tool sidebar** — `ui/panels/ToolModePanel.kt`: iterates `ToolMode.entries` (`:33`) → one icon per mode (`:44-48`). Adding `ToolMode.Tips` gives it a button automatically (pick an icon).
- **Palette window (to retire)** — `ui/components/MonitorOverlays.kt` → `fun TipsOverlay(selectedKind, onKindSelected, onClose, …)` (`:37`): a 300dp translucent-blue box with 7 `TipKindChip`s + a "Видим:" preview card + note. This is a **direct port of the now-deleted Windows `TipsWindow`**. Shown in `ui/screens/TeachingScreen.kt:469-473` (`if (mode.showTips) { TipsOverlay(selectedKind = mode.selectedTipKind, onKindSelected = { monitorViewModel.setSelectedTipKind(it) }, onClose = { monitorViewModel.setShowTips(false) }) }`).
- **Tips button (already a toggle!)** — `ui/panels/MonitorControlPanel.kt:411-412`: `onClick = { viewModel.setShowTips(!monitorMode.showTips) }, isActive = monitorMode.showTips`. **This already matches Windows round 4** (toggle + highlight). Only what `showTips` *gates* changes: today it opens the palette window; after this plan it gates authored-overlay + comments visibility.
- **VM setters** — `ui/viewmodels/MonitorViewModel.kt`: `setShowTips` (`:260`), `setSelectedTipKind` (`:268`).
- **Rendering** — `ui/display/Lead.kt`: `fun Lead(… significantPoints … showImpulseLabels …)` (`:59`), draws `SignificantPointOverlay` at `:158-161` when `showImpulseLabels`. This is the **injection point** for per-cell tip rendering. `ui/display/EditableLead.kt` (`fun EditableLead(… toolMode …)`, `:48`) is the single-lead editing canvas — the placement-gesture host; today it handles `Select/Points` taps (`:167-173`) and `Trace` drags (`:178`).
- **Strings** — `res/values/strings.xml` (+ `values-ru`? base is EN; locale variants `values-zh`, `values-es`, `values-hi`). Already present (used by `TipsOverlay`): `monitor_tips_window_title`, `monitor_tips_types_header`, `monitor_tips_type_arrow|lead_area|graph_area|ecg_part|vertical_lines|horizontal_lines|label`, `monitor_tips_preview_header` (= "Видим:"/"We see:"), `monitor_tips_note`.

### What Windows did (reference — the target behaviour)

All in `CardioSimulatorWin/src/`:
- **Model** `CardioSimulator.Core/Domain/TipOverlay.cs`: enums `TipOverlayKind` (9: adds `FreeformArea`, `Points`), `TipLineEndCap { Plain, Dots, Arrows }`; `readonly record struct TipPoint(float Sample, float Adc)` where **`Adc` is baseline-RELATIVE amplitude** (0 = isoline — the same zeroing the trace uses, so one geometry maps on both the raw-sample editable lead and the pre-zeroed monitor grid); `record TipOverlay(Kind, IReadOnlyList<TipPoint> Points, string? Text, Lead? Lead, TipLineEndCap EndCap)`. `Lead` is the **home lead** (the cell the overlay renders in). `ToolMode.Tips` added.
- **Model** `Core/Domain/Pathology.cs`: `PathologyFile.Tips` + `PathologyFile.TipComments` (both `init` lists).
- **Persistence** `Core/Domain/PathologyParser.cs`: header fields `tips:` and `tip_notes:` (see encoding below). Round-trip unit tests in `PathologyParserTests.cs`.
- **Rendering** `CardioSimulator.App/Rendering/EcgRenderer.cs`: `DrawTips(...)` (cell-based: originX/baselineY/clip bounds; `x = originX + sample·stepX`, `y = baselineY − amp·stepY`) called from **both** `RenderEditableLead` (editable canvas, ungated) and the multi-lead `Render` per cell (gated on `mode.ShowTips`, only overlays whose home lead == the cell's lead). `DrawTipCommentsCard(...)` draws the "Видим:" numbered card screen-anchored top-left (gated on `ShowTips`, suppressed in compare mode).
- **Authoring** `App/Controls/EditableLeadControl.cs`: `ToolMode.Tips` press/drag/release builds a `TipOverlay` in data space (`DataAt` inverse map), live preview while dragging, `TipPlaced` event; 2-point kinds (Arrow/GraphArea/EcgPart) need a drag, FreeformArea needs ≥3 pts, the rest are single-click. `App/Screens/ConstructorScreen.cs`: `BuildTipsPanel()` inline in the mode-panel host (kind radios + lead dropdown for LeadArea + end-cap dropdown for the line kinds + Undo-last/Clear-all + a **"Комментарии / explanations…"** button → `ShowTipCommentsDialog()` multiline editor); `OnTipPlaced` prompts a caption for Arrow/Label and stamps the **home lead** (`= FocusedLead`, except LeadArea keeps its chosen lead). `App/ViewModels/ConstructorViewModel.cs`: `AddTip / RemoveLastTip / ClearTips / SetTipComments`, `SelectedTipKind / SelectedTipEndCap / SelectedTipLead`.
- **Monitor display** `App/Controls/EcgMonitorControl.cs` gained `Tips` + `TipComments`; `App/ViewModels/RhythmViewModel.cs` exposes `Tips` + `TipComments` (set in `SelectRhythm` from the read `PathologyFile`); `App/Controls/MonitorView.cs` pushes them to the monitor.
- **Toggle** `Core/Domain/MonitorMode.cs`: `ShowTips = true` (default on). `MonitorViewModel.SetShowTips`. The Teaching `MonitorControlPanel` Tips tab toggles it + `TipsTab.IsActive = mode.ShowTips`. The Windows floating `TipsWindow.cs` was **DELETED**.

### `.dat` persistence encoding (copy exactly for cross-platform files)

One header line each, tolerant parse (skip malformed), omitted when empty (so existing files are untouched):

- **`tips:`** — overlays joined by `~`, fields within an overlay by `|`:
  `Kind|EndCap|Lead|Text|s:a;s:a;…`
  - `Kind` = `TipOverlayKind.name`, `EndCap` = `TipLineEndCap.name`, `Lead` = lead token or empty, `Text` = **percent-escaped** (see below), points = `sample:amp` pairs (invariant-culture floats, `.` decimal) joined by `;`.
- **`tip_notes:`** — comments joined by `~`, each **percent-escaped**.
- **Percent-escape** (so text can't collide with delimiters or wrap the single header line): `%`→`%25`, `|`→`%7C`, `~`→`%7E`, `\r`→`%0D`, `\n`→`%0A` (unescape in reverse: `%0A,%0D,%7E,%7C,%25`).
- Floats: format/parse with **invariant/US locale** (`String.format(Locale.US, ...)` / `toFloatOrNull()`), never the device locale — a comma decimal would corrupt the CSV.

## Non-goals

- No per-overlay select/move/delete (Windows only has undo-last / clear-all + free-text comment edit). Match that.
- Comments card stays fixed top-left, not user-positionable (Windows parity).
- Don't touch `elements:` — that's a Windows-only lead annotation Android doesn't have.
- No change to the Tips button's *toggle+highlight* mechanics in `MonitorControlPanel.kt:411` — only what `showTips` gates.
- Editable-lead canvas always shows tips while authoring (never gated by `showTips`).

## Plan

Phased so each phase is a shippable PR. Order matters: model → persistence → authoring → rendering → display/toggle reconcile.

### Phase 1 — Model + persistence (no UI)
- **`domain/MonitorModeModel.kt`**: extend `TipOverlayKind` with `FreeformArea, Points` (keep existing order, append). Add `enum class TipLineEndCap { Plain, Dots, Arrows }`. (Leave `selectedTipKind` on `MonitorModeModel` for now; Phase 4 moves authoring state to the Constructor.)
- **`domain/Pathology.kt`**: add
  ```kotlin
  data class TipPoint(val sample: Float, val adc: Float) // adc = baseline-relative amplitude, 0 = isoline
  data class TipOverlay(
      val kind: TipOverlayKind,
      val points: List<TipPoint>,
      val text: String? = null,
      val lead: Lead? = null,          // home lead (which cell it renders in)
      val endCap: TipLineEndCap = TipLineEndCap.Plain,
  )
  ```
  and to `PathologyFile`: `val tips: List<TipOverlay> = emptyList(), val tipComments: List<String> = emptyList(),`.
- **`domain/PathologyParser.kt`**: in `parsePathology`, read `header["tips"]`→`parseTips`, `header["tip_notes"]`→`parseTipComments`; pass both into the `PathologyFile(...)` constructor. In `serializePathology`, after the `markers:` block (`:152`) write `tips:` (if non-empty) then `tip_notes:` (if non-empty). Add private `serializeTips/parseTips`, `parseTipComments`, `escapeTipText/unescapeTipText` mirroring `PathologyParser.cs` exactly (encoding above). **Use `Locale.US` for all float format/parse.**
- **Test** (`PathologyParserTest` or equivalent JUnit): round-trip a file with several tips (incl. reserved chars `| ~` in a caption) + tip_notes; assert the `tips:` / `tip_notes:` values contain no newline, kinds/leads/end-caps/points/comments survive, and reserved chars round-trip. Mirror `PathologyParserTests.SerializeThenParse_RoundTripsTips` + `_RoundTripsTipComments`.

### Phase 2 — Rendering on the monitor grid + comments card
- **`ui/display/Lead.kt`**: add params `tips: List<TipOverlay> = emptyList()` and gate. In the cell draw (near the `SignificantPointOverlay` call `:158-161`), draw the overlays whose `lead == this cell's lead`, mapping data→px like the trace: `x = traceLeft + sample*pxPerSample`, `y = baselineY - amp*pxPerAdcCount` (reuse the cell's existing scale math). Per-kind draw (Compose `DrawScope`): arrow + arrowhead; whole-cell translucent fill (LeadArea) + lead label; rect (GraphArea); closed `Path` (FreeformArea); vertical band (EcgPart); full-height/‑width guide lines with dot/arrow end-caps; white-bg label; filled dots (Points). Clip to the cell. Colours: reuse the Windows blue `#1976D2` stroke + `alpha≈60/255` fill.
- **"Видим:" card**: add a Compose overlay (Box in `TeachingScreen`/monitor host, or a `drawText` in the monitor Canvas) that, when the pathology has `tipComments` **and** `mode.showTips` **and** not compare mode, shows a translucent dark card top-left with `stringResource(R.string.monitor_tips_preview_header)` ("Видим:") + numbered lines. (Reusing the existing preview-header string.)
- **Wire data to the monitor**: `ui/viewmodels/RhythmViewModel.kt` — where `selectRhythm` sets `significantPoints` from the read `PathologyFile`, also expose `tips` + `tipComments` (new `StateFlow`s). Thread them through the Teaching monitor composition (`TeachingScreen.kt`) into `Lead(...)` and the card. Gate grid overlays + card on `mode.showTips` (Phase 4 makes the toggle meaningful; default stays as set in Phase 4).

### Phase 3 — Authoring in the Constructor (palette + placement + comments)
- **`ConstructorViewModel.kt`**: add `ToolMode.Tips` to the enum (`:32-39`). Add tip state (`selectedTipKind`, `selectedTipEndCap`, `selectedTipLead` StateFlows) **here** (Constructor owns authoring, matching Windows — see Phase 4 for removing them from `MonitorModeModel`). Add `addTip(TipOverlay)`, `removeLastTip()`, `clearTips()`, `setTipComments(List<String>)` that `copy()` the `targetFile` and mark metadata dirty (mirror `significantPoints` mutators + `ConstructorViewModel.cs`).
- **`ui/panels/ToolModePanel.kt`**: `ToolMode.Tips` now appears in the `ToolMode.entries` loop (`:33`); give it an icon (e.g. `Icons.Default.Comment` / `EditNote`).
- **Inline tips panel** (Constructor mode-panel area, alongside `SelectPanel/DrawPanel/…` at `ConstructorScreen.kt:828-903`): add a `ToolMode.Tips -> TipsPanel(...)` branch. `TipsPanel` = kind radios (9, in Windows order), a **lead dropdown** shown for `LeadArea`, an **end-cap dropdown** shown for the line kinds, **Undo-last / Clear-all** buttons, and a **"Комментарии / explanations…"** button opening a multiline dialog → `setTipComments(text.split('\n'))`. (Port `BuildTipsPanel` + `ShowTipCommentsDialog` from `ConstructorScreen.cs`.)
- **Placement** in `ui/display/EditableLead.kt`: when `toolMode == ToolMode.Tips`, a `pointerInput` gesture builds a `TipOverlay` in data space (invert the trace map: `sample = (x - traceLeft)/pxPerSample`, `amp = (baselineY - y)/pxPerAdcCount`) — 2-point kinds from press→release drag, FreeformArea from the drag path (≥3 pts), single-click for the rest; draw a **live preview** while dragging. On release, stamp the **home lead = focusedLead** (LeadArea keeps its dropdown lead) and, for Arrow/Label, prompt a caption; then `addTip(...)`. Also draw the focused lead's committed overlays on the editable canvas (filter `lead == focusedLead || lead == null`), **ungated** by `showTips`. Mirror `EditableLeadControl.cs` (`OnPointerPressed/Moved/Released`, `BuildTipOverlay`, `DataAt`) + `OnTipPlaced`.
- **Strings** (`res/values/strings.xml` + `values-zh/-es/-hi`): add `monitor_tips_type_graph_area_rect`, `monitor_tips_type_freeform_area`, `monitor_tips_type_points`, `monitor_tips_lead_pick_header`, `monitor_tips_line_cap_header|_plain|_dots|_arrows`, `constructor_tips_title|_note|_button|_undo|_clear|_text_prompt|_comments|_comments_help`. Copy the five-language values verbatim from Windows `AppStrings.cs` (En/Ru/Zh/Es/Hi). **Gotcha:** any `{0}`-style .NET placeholder → Android `%1$s` / `%1$d`; these particular strings have no placeholders, but keep the rule in mind. The `constructor_tips_note` value contains parenthetical examples — keep them.

### Phase 4 — Reconcile the toggle; retire the palette window
- **Repurpose `showTips`**: it already toggles + highlights via `MonitorControlPanel.kt:411-412` — no change there. Change its default to `true` in `MonitorModeModel.kt:79` (`showTips = true`) to match Windows (`ShowTips = true`), so authored tips show by default and the tab starts lit.
- **Retire the palette window**: delete the `if (mode.showTips) { TipsOverlay(...) }` block at `TeachingScreen.kt:469-473`, and delete `fun TipsOverlay(...)` + its `TipKindChip` helper in `MonitorOverlays.kt` (and the `import … TipsOverlay` in `TeachingScreen.kt:73`). `showTips` now gates the **authored overlays + "Видим:" card** wired in Phase 2, not the palette.
- **Move authoring state off `MonitorModeModel`**: remove `selectedTipKind` from `MonitorModeModel.kt:81` and `setSelectedTipKind` from `MonitorViewModel.kt:268` (now lives on `ConstructorViewModel` from Phase 3). Remove `monitor_tips_window_title` / `monitor_tips_types_header` / `monitor_tips_note` usages that only fed the deleted overlay (leave the string entries — harmless — or delete across all locales; your call).
- **All-leads preview** (Constructor read-only overview, if present on Android — the `AllLeadsPreviewOverlay` referenced in the sibling plan): force `showTips = true` for that render so authoring always shows tips regardless of the Teaching toggle (Windows `RefreshAllLeadsOverlay` sets `ShowTips = true`).

## Risks & open questions

- **Coordinate math** is the crux. Confirm Android's `Lead.kt` cell mapping (traceLeft / pxPerSample / pxPerAdcCount / baselineY) so tips land exactly on the trace. **Store amplitude baseline-relative** (not raw ADC) or the editable lead (raw samples) and the grid (pre-zeroed) will disagree — this is why Windows changed `TipPoint.Adc` to baseline-relative (Windows round 3). Verify against a known feature (e.g. an R-peak) at multiple speed/gain/zoom settings.
- **Running monitor drift**: tips use a static sample→x map (like the pQRSt overlay), so they align **when paused** and drift while scrolling. Match Windows — acceptable.
- **Compose vs Win2D**: Windows draws in a single Canvas pass with a zoom transform + `1/zoom` stroke counter-scaling. Android's `Lead.kt` may already handle zoom per its own convention — follow the file's existing stroke-width approach so tip strokes stay visually constant (see the `monitor-zoom-rendering` convention).
- **Locale floats** — must be `Locale.US` in the parser (see encoding). A round-trip test on a comma-decimal locale would catch a regression.
- **`selectedTipKind` removal** — grep for every reader before deleting it from `MonitorModeModel` (currently `TeachingScreen.kt:471`, `MonitorViewModel.kt:268-269`). All should be gone after Phase 3/4.
- **Icon choice** for the `ToolMode.Tips` sidebar entry and whether the Constructor also wants a hide/show toggle (Windows does not — editable canvas always shows). Deferred: no Constructor-side visibility toggle.

## Verification

- Build: `./gradlew :app:assembleDebug` succeeds; new JUnit persistence test passes.
- **Authoring**: Constructor → load a pathology → Tips tool → the inline panel shows 9 kinds; picking `LeadArea` reveals the lead dropdown, a line kind reveals the end-cap dropdown. Drawing on the trace (drag for arrow/area, click for points/lines/label) places overlays; Undo-last / Clear-all work; the "Комментарии…" dialog saves lines. **Save** persists (reopen the pathology → tips + comments survive).
- **Display**: open the all-leads preview (Constructor) → overlays appear in their leads' cells + the "Видим:" card top-left. Teaching → select the same rhythm → overlays + card show on the live monitor.
- **Toggle**: Teaching **Подсказки** tab hides/shows the overlays + card and lights when on (already wired). Palette window no longer appears.
- **Cross-platform file**: a `.dat` saved on Windows with tips loads on Android (and vice-versa) — same geometry, captions, comments.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Tips: data model + `.dat` persistence (`tips:` / `tip_notes:`) | 1 | `MonitorModeModel.kt` enums, `Pathology.kt`, `PathologyParser.kt` + JUnit round-trip. Locale.US floats. |
| 2 | Tips: render overlays on the monitor grid + "Видим:" card | 2 | `Lead.kt` draw, `RhythmViewModel` exposure, `TeachingScreen` wiring. Baseline-relative amp. |
| 3 | Tips: authoring in the Constructor (palette + placement + comments) | 3 | `ConstructorViewModel` (`ToolMode.Tips` + mutators), `ToolModePanel`, `ConstructorScreen` inline panel + comments dialog, `EditableLead` placement, strings ×5 langs. |
| 4 | Tips: reconcile the toggle; retire the palette overlay | 4 | Default `showTips=true`, delete `TipsOverlay` + `TeachingScreen:469-473`, move `selectedTipKind` off `MonitorMode`, all-leads-preview forces on. |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:** shipped
- **PRs:** #1, #2, #3, #4
- **Deviations from plan:** none
- **Follow-ups spawned:** none
