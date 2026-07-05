# Constructor toolbar: pathology title on its own row (parity + hardening)

**Status:** completed
**Owner:** AI Assistant
**Started:** 2026-07-04
**Related issues / PRs:** Customer feedback (Windows): *"Из-за длинного названия, Панель
настроек уезжает вправо и не видно ее, может отдельно в новую строку вынести ее."*
(A long pathology title pushes the settings/action buttons off to the right; suggestion —
move it to its own line.)

## Goal

Port the Windows ECG-Constructor toolbar fix to Android for UX parity: put the **pathology
title on its own row**, with the **action buttons ("settings panel") on the row below**, so a
long title can never crowd or hide the buttons. On Windows this was a real off-screen bug; on
Android the same layout also solves the *related* problem of the button strip overflowing on
narrow (phone) screens — see Current state for why the exact Windows symptom does **not**
reproduce on Android.

## What shipped on Windows (reference)

`CardioSimulatorWin/src/CardioSimulator.App/Screens/ConstructorScreen.cs`, `BuildLayout()`:

- The toolbar was a single **horizontal `StackPanel`** with `_title` (a `TextBlock`) as the
  **first child**, followed by ~17 action buttons. Because a `StackPanel` gives each child its
  intrinsic size in order with no weighting, a long title consumed horizontal space and pushed
  every button to the right — and the toolbar had no horizontal scroll, so they vanished.
- Fix — split into two rows:
  - A **vertical** `toolbarColumn` whose first child is `_title`, now with
    `TextWrapping = NoWrap` + `TextTrimming = CharacterEllipsis` (so a pathological-length name
    is clipped with `…` instead of forcing the row wider).
  - The horizontal `toolbar` (all the buttons) is added below, wrapped in a horizontal
    `ScrollViewer` (`HorizontalScrollBarVisibility = Auto`, vertical disabled) as a last-resort
    fallback so the button strip is always reachable.
- `UpdateCanvasAndPreview()` also sets `ToolTipService.SetToolTip(_title, _title.Text)` so the
  full title is available on hover when it is ellipsized.

## Current state (Android)

`app/src/main/java/com/example/cardiosimulator/ui/screens/ConstructorScreen.kt`:

- The toolbar is a `Surface(Modifier.fillMaxWidth().height(56.dp))` (`:538`) wrapping a single
  `Row(horizontalArrangement = Arrangement.spacedBy(16.dp))` (`:543`).
- The title is the **first** child: `Text(displayTitle, style = titleMedium, modifier =
  Modifier.weight(1f))` (`:548-552`).
- After it: `Add` (`:554`), `Synthesizer` (`:558`), the import dropdown `Box` (`:562`), the
  undo/redo `Row` gated on `referenceImageUri != null` (`:588`), the `targetFile != null` block
  of `Edit / Info / Label / Healing / ContentCopy / Delete / Calculate / GridView` IconButtons
  (`:599-647`), and the `Save` / `Revert` buttons gated on dirty state (`:649-658`).

**Why the exact Windows symptom does not reproduce here:** the title has `Modifier.weight(1f)`.
In a Compose `Row`, non-weighted children (all the buttons) are measured **first** and reserve
their intrinsic width; the weighted title then gets only the *remainder* (and `fill = true`
pins it to exactly that). So a long title is squeezed/clipped rather than pushing the buttons
off — it cannot cause the reported bug. This mirrors the
`2026-06-android-compare-dialog-lead-selector-layout-parity` situation, where Android's weighted
`Row` already dodged a Windows off-screen bug.

**What *can* still go wrong on Android:** when a `targetFile` is loaded there are ~10 IconButtons
+ Save/Revert. On a narrow (phone-portrait) width their combined intrinsic width can exceed the
row, the weighted title collapses to ~0, and the trailing buttons (Save/Revert) clip off the
right edge with no way to scroll to them. The two-row layout fixes that too.

## Non-goals

- No behavior/logic changes — layout only. Same buttons, same click handlers, same dialogs.
- No new string resources (the change adds no user-visible text).
- Not touching the lead `TabRow`, the editor canvas, the drawers, or `AllLeadsPreviewOverlay`
  (its own top bar already uses `weight(1f)` and is out of scope).
- Not changing the Windows side further (already shipped).

## Plan

### Phase 1 — Verify (decide literal vs. hardening)
- Build & run; open Constructor, pick a pathology with a long RU name (switch app language to
  RU). Confirm the **buttons stay put** and only the **title** truncates — i.e. the exact
  Windows bug does *not* reproduce (expected, due to `weight(1f)`).
- Shrink to phone-portrait width with a `targetFile` loaded (all IconButtons + Save/Revert
  visible). Confirm whether the **button strip overflows / Save/Revert clip off**. This is the
  real Android defect the two-row layout addresses.
- If neither reproduces on target devices, this can stay a *visual-parity-only* change; proceed
  to Phase 2 regardless for parity with Windows.

### Phase 2 — Two-row toolbar
In `ConstructorScreen.kt`, change the toolbar `Surface` from a single `Row` to a `Column` of two
rows:

1. Drop the fixed `.height(56.dp)` on the toolbar `Surface` (`:539`) — let it wrap two rows
   (or set an explicit two-row height, e.g. `heightIn(min = 56.dp)`; wrapping is cleaner).
2. Inside the `Surface`, wrap the existing content in a `Column(Modifier.fillMaxWidth())`:
   - **Row 1 — title.** `Text(displayTitle, style = MaterialTheme.typography.titleMedium,
     maxLines = 1, overflow = TextOverflow.Ellipsis, modifier =
     Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))`. Remove the old
     `Modifier.weight(1f)` from the title (no longer in a shared row).
   - **Row 2 — buttons.** The existing `Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
     verticalAlignment = Alignment.CenterVertically)` **minus the title `Text`**, plus
     `Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)`
     so the strip scrolls when it can't fit (the Windows `ScrollViewer` analog).
3. New imports:
   - `androidx.compose.foundation.horizontalScroll`
   - `androidx.compose.ui.text.style.TextOverflow`
   (`rememberScrollState` at `:9` and the layout imports at `:8` are already present.)

Result: `[ title …………………… ]` / `[ + ♪ ⤓ | ✎ ⓘ 🏷 ✚ ⧉ 🗑 = ▦  Save  Revert → ]` (row 2
scrolls horizontally if needed).

### Phase 3 — Polish (optional)
- The Windows hover tooltip for the ellipsized title has **no direct touch equivalent**. If
  desired, wrap the title in Material3 `TooltipBox` + `PlainTooltip` (long-press to reveal the
  full name). Low priority — Android users can also read the full name in the rhythm drawer list.
- Sanity-check vertical budget: two ~44–52 dp rows (~96–104 dp total) vs. the old 56 dp. Confirm
  the editor canvas below (`Box(Modifier.weight(1f))`, `:684`) still has adequate height on the
  shortest supported screen.

## Risks & open questions

- **Surface height.** Removing `.height(56.dp)` lets the toolbar grow to two rows and eats a bit
  of vertical space from the canvas. Acceptable (matches Windows), but verify on a small screen.
- **Row-2 vertical alignment.** After the title leaves the row, keep
  `verticalAlignment = Alignment.CenterVertically` so icons stay centered in their row.
- **Tooltip.** Deferred to Phase 3; Android has no hover, so skip unless the customer asks.
- **Open:** do we want the button row *centered* or *start-aligned* when it *does* fit? Windows
  is start-aligned (buttons begin at the left). Match that (default `Row` start arrangement) —
  do **not** add `Arrangement.End`.

## Verification

- `./gradlew :app:assembleDebug` (or the project's standard debug build) passes; no new warnings.
- Manual: long RU pathology name → title truncates on its own row, **all** buttons visible on the
  row below; nothing clipped.
- Manual, phone-portrait, `targetFile` loaded → button row scrolls horizontally; Save/Revert
  reachable.
- Visual diff vs. Windows: title-on-top, buttons-below, left-aligned, scrollable.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Constructor: pathology title on its own toolbar row | 1–2 (+3 optional) | Single file (`ConstructorScreen.kt`), 2 new imports, no new strings, layout-only |

---

## Outcome

- **Result:** Implemented the two-row toolbar layout in `ConstructorScreen.kt`. The title now occupies its own row with ellipsis overflow, and the action buttons are in a horizontally scrollable row below.
- **PRs:** N/A (applied directly)
- **Deviations from plan:** None.
- **Follow-ups spawned:** None.
