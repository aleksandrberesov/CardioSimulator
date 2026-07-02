# Compare-target dialog: keep the lead selector visible with long pathology names (Android parity)

**Status:** active
**Owner:** a.beresov
**Started:** 2026-06-30
**Related issues / PRs:** Windows→Android sync. Source change: `CardioSimulatorWin/src/CardioSimulator.App/Controls/ComparisonTargetDialog.cs`.

## Goal

On the Windows port, the "Select comparison target" dialog had a layout bug: when
the pathology (rhythm) list contained a long name — Russian variants especially —
the left list widened to fit the text and pushed the right-hand **lead selector**
off the dialog, hiding it. This plan brings the Android compare-target dialog to
the same guarantee: **the lead selector stays fully visible regardless of how long
the pathology names are.**

Why now: the bug was just fixed on Windows; this is the routine Windows→Android
parity pass so the two builds don't drift.

## Current state

**Windows (the fix being ported), `Controls/ComparisonTargetDialog.cs`:**
- Dialog content was a horizontal `StackPanel { leftColumn, rightColumn }`. A
  horizontal StackPanel hands each child **unconstrained** width.
- The pathology `ListView` had only `MinWidth = 280` (no max) and each item was a
  bare string → an implicit **non-wrapping** `TextBlock`. A long name made the
  list measure as wide as the text, the left column overran the dialog, and the
  right-side lead grid was clipped/hidden.
- Fix: pin the list to `Width = 280` (a hard cap, not a minimum) and make items
  **wrap** — each `ListViewItem.Content` is now a `TextBlock { TextWrapping = Wrap }`
  with `HorizontalContentAlignment = Stretch` (on both the item and the `ListView`).
  Long names now wrap onto multiple lines inside the fixed 280px column; the lead
  grid keeps its place.

**Android (the target), `app/src/main/java/com/example/cardiosimulator/ui/dialogs/ComparisonTargetDialog.kt`:**
- The dialog is a `Dialog` → `Surface(fillMaxWidth(0.95f).fillMaxHeight(0.9f))`
  → `Column` → a `Row(Modifier.weight(1f))` holding the two sides
  (`ComparisonTargetDialog.kt:91`).
- Left side is `Column(Modifier.weight(1f))` with a `LazyColumn` of rhythm rows
  (`:93`–`:124`); each row is a `Text(... .fillMaxWidth())` with **no `maxLines`**,
  so it already soft-wraps (`:112`–`:120`).
- Right side is `Column(Modifier.weight(0.6f))` with a `LazyVerticalGrid` of lead
  toggles (`:129`–`:162`).

**Key finding — Android is structurally NOT exposed to the Windows bug.** Compose
distributes a `Row`'s width by *weight* (left `1f` / right `0.6f` → 62.5% / 37.5%
of the dialog's 95% width), measuring weighted children against that share rather
than their content's intrinsic width. The long-name `Text` wraps inside the left
column's 62.5%; it cannot grow the column or shove the lead grid off-screen the way
the unconstrained Windows StackPanel did. So this is a **verify-first + light
hardening** port, not a behavior change. (Same shape as prior syncs where Android
already did the right thing — don't manufacture a fix that isn't needed.)

## Non-goals

- No redesign of the dialog (no two-pane→tabbed, no resizable splitter).
- No change to selection logic, OK/Cancel enablement, preset save/load, or the
  `ComparisonTarget` model.
- Not touching the abandoned copies under `.gemini/*` or `.claude/worktrees/*`.

## Plan

### Phase 1 — Verify (do this first; it may be the whole job)
- Build & run; open Teaching → monitor compare → "Select comparison target".
- Switch language to RU so the longest Cyrillic `nameRu` values show.
- Scroll the rhythm list to the longest entries and confirm:
  - The lead grid (V1…V6, I/II/III, aVR/aVL/aVF) stays fully visible and tappable.
  - Long names **wrap** within the left column instead of being clipped to one line
    or truncated with an ellipsis.
- Repeat in portrait on a narrow phone (~360dp) — the 37.5% right share must still
  fit a 2-column lead grid with the widest label (`aVR`).
- If all pass: the parity guarantee already holds. Record the outcome and either
  stop here or apply Phase 2 as belt-and-suspenders.

### Phase 2 — Light hardening (optional, only if Phase 1 shows any squeeze)
- **Guarantee the lead column a floor** so it can never be squeezed below a usable
  width on very narrow screens: add `Modifier.widthIn(min = 140.dp)` to the right
  `Column` (keep the `weight(0.6f)`), or bump the weight (e.g. `0.7f`) if the 2-col
  grid looks cramped in portrait.
- **Make wrapping explicit and intentional** on the rhythm `Text` (`:112`): it
  currently relies on the default (`softWrap = true`, no `maxLines`). Leave it
  multi-line, but if product prefers single-line rows, use
  `maxLines = 2, overflow = TextOverflow.Ellipsis` — *never* `maxLines = 1` without
  ellipsis (that would clip mid-word). Default multi-line wrap is the closest match
  to the Windows fix.
- No string-resource changes; `R.string.rhythm_selector_title`,
  `R.string.constructor_lead_label`, `R.string.monitor_compare` are already wired.

### Phase 3 — Polish
- If any layout constant changed, re-check the other entry points to this dialog
  (preset editing path that passes `initialPathologyId`/`initialLead`) so the
  initial-scroll `LaunchedEffect` (`:68`–`:73`) still lands on the right row.

## Risks & open questions

- **Is Phase 2 even needed?** Open until Phase 1 verification on a real narrow
  device. Expectation: no — weights already protect the layout. (Resolve with a
  screenshot.)
- Single unbreakable token (a very long word with no spaces) in a `nameRu`: Compose
  clips it at the column edge (default `overflow = Clip`), still no sibling shove.
  Acceptable; matches the Windows behavior of wrapping within a fixed width.

## Verification

- `./gradlew :app:assembleDebug` passes.
- Manual: RU language, longest rhythm names, compare-target dialog open — lead grid
  fully visible in both landscape and portrait; long names wrap, not clipped.
- Screenshot before/after (or just "after", since pre-fix Android likely already
  looks correct) attached to the PR.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Verify compare-target dialog keeps lead selector visible with long RU names | 1 | May ship as a no-code-change verification note if layout already holds |
| 2 | Harden compare-target dialog lead-column min width (if needed) | 2 | Only if Phase 1 shows a squeeze on narrow screens |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:** shipped / dropped / partial
- **PRs:** #…
- **Deviations from plan:** …
- **Follow-ups spawned:** …
