# Rhythm-selector Expand-All / Collapse-All buttons — icon parity (verify-only)

**Status:** completed
**Owner:** AI Assistant
**Started:** 2026-07-13
**Finished:** 2026-07-13
**Related:**
- Windows change (source of truth): `CardioSimulatorWin/src/CardioSimulator.App/Controls/RhythmChoosingPanel.xaml`
  — the two header buttons `ExpandAllButton` / `CollapseAllButton` had their icons fixed.
- Supersedes nothing. **Builds on** the already-**completed**
  [`2026-07-android-pathology-grouping-and-sorting-parity.md`](../completed/2026-07-android-pathology-grouping-and-sorting-parity.md),
  which first ported the Expand/Collapse-All controls to Android.

## Goal

Keep the shared Android `RhythmSelector`'s **Expand-All / Collapse-All** header buttons at parity with
the Windows port after a Windows-side bug fix. On Windows the two buttons **already existed and worked**,
but their `FontIcon` glyphs used **non-existent Segoe MDL2 Assets code points** (`&#xE9A1;` and `&#xE9A0;`),
which render as blank "tofu" boxes — so from a user's chair the buttons looked *missing* ("bring back the
two collapse-all / expand-all buttons"). The Windows fix swaps them for valid, recognizable chevron glyphs:

- **Expand All** → `&#xE70D;` (ChevronDown — "open everything", matching the header's expanded-state chevron)
- **Collapse All** → `&#xE70E;` (ChevronUp — "close everything up")

The handlers, tooltips, and behavior were unchanged on Windows — **this was an icon-rendering fix only.**

**Why now:** logged so the Windows fix isn't mistaken for a missing Android feature. The expectation is
that Android needs **no functional change** — but that claim must be verified against the current code, not
trusted, and the two small divergences below should be noted (and optionally reconciled).

## Current state (Android)

Android already ships these buttons and — unlike Windows before the fix — **never had the tofu-glyph bug**,
because it uses vector Material icons, not font code points:

- `ui/panels/RhythmSelector.kt:225-246` — inside the header `Row`, gated on `isGrouped && !isClinicalMode`:
  - **Expand All** `IconButton` → `appViewModel.expandAllRhythms()`, icon `Icons.Default.KeyboardDoubleArrowDown`,
    tint `AccentGreen`, `contentDescription = "Expand All"` (`:226-234`).
  - **Collapse All** `IconButton` → `appViewModel.collapseAllRhythms(groupKeys, subgroupKeys)` (keys gathered
    from the current `listItems`), icon `Icons.Default.KeyboardDoubleArrowUp`, tint `AccentGreen`,
    `contentDescription = "Collapse All"` (`:235-245`).
- `ui/viewmodels/AppViewModel.kt:384` `fun expandAllRhythms()` and `:389`
  `fun collapseAllRhythms(groupKeys, subgroupKeys)` — both present and wired to the collapsed-group /
  collapsed-subgroup state the list renders from.
- Per-header collapse/expand chevrons already use valid vectors (`KeyboardArrowRight` collapsed /
  `KeyboardArrowDown` expanded) at `RhythmGroupHeader`/`RhythmSubgroupHeader` (`:426`, `:469`).

`Icons.Default.KeyboardDoubleArrowDown` / `…Up` are standard `material-icons-extended` vectors that always
render — there is no Android analog of the "invalid font code point → blank box" failure. So the Windows bug
is **not reproducible** on Android.

## Non-goals

- Any change to the grouping / subgrouping / sorting / clinical logic (that shipped under the completed
  grouping-and-sorting plan).
- Reworking `expandAllRhythms()` / `collapseAllRhythms()` semantics.
- The Teaching-drawer page-scroll buttons (separate, already-completed plan).

## Plan

### Phase 1 — Verify (expected: nothing to change)
1. Open the rhythm selector (Teaching drawer) in **grouped, non-clinical** mode. Confirm the header shows,
   left-to-right: title · **Expand-All (double-down)** · **Collapse-All (double-up)** · group/A–Z toggle ·
   clinical toggle · pin. Both icons render (no blank box) and are tinted green.
2. Tap **Collapse All** → every group **and** every duplicate-title subgroup collapses to headers only.
   Tap **Expand All** → all groups/subgroups expand. Selecting a rhythm still auto-expands its group/subgroup
   (`RhythmSelector.kt:181-198` / `AppViewModel.expandGroupAndSubgroup`).
3. Confirm the buttons **hide** in **A–Z (flat)** mode and in **clinical** mode (the `isGrouped && !isClinicalMode`
   gate at `:225`) — collapse/expand-all is meaningless without groups.
4. If all of the above hold, this plan closes as **"already satisfied — no code change"** (see the completed
   `2026-07-android-3d-heart-open-immediately-refinement-parity.md` for that outcome shape).

### Phase 2 — Optional cosmetic reconciliation (only if a reviewer wants pixel parity)
Two intentional, low-stakes divergences — decide keep-or-align, don't change silently:

1. **Icon shape.** Windows now uses **single** chevrons (`E70D` / `E70E`); Android uses **double** chevrons
   (`KeyboardDoubleArrowDown` / `…Up`). The double chevron is arguably the *clearer* "…all" affordance, so the
   recommendation is **keep Android as-is** (and, if anything, consider a future reverse-sync giving Windows a
   double-chevron glyph such as `&#xEDDB;`/`&#xEDDC;` — **out of scope here**). No action needed for parity.
2. **Visibility gating.** Android **hides** the two buttons outside grouped/non-clinical mode
   (`:225`); Windows currently **always shows** them (in flat/clinical mode they're effectively no-ops, and
   `OnCollapseAllClick` re-derives its own filtered groups). This predates the icon fix and is unaffected by
   it. Android's gating is the better behavior — **keep it.** Flagged only so the divergence is on record; no
   change required.

## Risks & open questions

- None functional. The only risk is over-correcting: **do not** "fix" Android to always-show the buttons or
  to single chevrons just to match Windows literally — Android's current form is the better UX. Treat Phase 2
  as documentation unless a reviewer explicitly asks to align.

## Verification

Covered by Phase 1 steps 1–3. Definition of done: both header buttons render with recognizable icons in
grouped mode, collapse-all/expand-all mutate the whole list, and the buttons hide in flat/clinical mode. No
build change expected; if none is made, no APK rebuild is required beyond a smoke check.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | (verify) rhythm expand/collapse-all icon parity | 1 | Expected: no diff — close as already-satisfied |
| 2 | (optional) — | 2 | Only if a reviewer wants literal Windows icon/gating parity; recommendation is *don't* |

---

## Outcome

- **Result:** already satisfied — no code change
- **PRs:** N/A
- **Deviations from plan:** None. Verified code in `RhythmSelector.kt` matches the goal.
- **Follow-ups spawned:** None.
