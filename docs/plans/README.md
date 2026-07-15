# Plans

A lightweight place for implementation plans — research, phased rollout, PR
breakdowns. The point is to keep planning artifacts in the repo (so reviewers
and future-you can see *why* a change was shaped the way it was) without
turning into a documentation project.

## Layout

```
docs/plans/
  README.md          ← you are here
  _template.md       ← copy this when starting a new plan
  active/            ← in-flight work
  completed/         ← shipped (kept for posterity)
  proposed/          ← ideas worth keeping but not yet committed to
```

## Workflow

1. **Start a plan.** Copy `_template.md` into `active/` (or `proposed/` if
   you're still pitching it). Filename convention: `YYYY-MM-short-slug.md` —
   e.g. `2026-04-localization.md`. The date is when the plan was written, not
   when work finishes.
2. **Iterate.** Update the same file as scope changes. Plans are working
   documents, not snapshots — rewrite freely. Strike-through or `~~~` blocks
   are fine for showing what was abandoned.
3. **Ship.** When the last PR merges, move the file to `completed/`. Add a
   short *Outcome* section at the bottom: links to PRs, what changed vs. the
   plan, follow-ups spawned.
4. **Drop.** If a plan is abandoned, move it to `completed/` with an
   *Outcome: dropped* note and one sentence on why. Don't delete — the dead
   ends are often more useful than the wins.

## Running a plan with Claude

These plans are written to be re-entrant: hand any of them back to Claude
("continue the plan in `docs/plans/active/foo.md`") and it has enough context
to pick up. That's the whole reason for the structure — so a plan survives
across sessions and isn't lost in a chat transcript.

Good prompts when handing off:
- *"Read `docs/plans/active/foo.md` and start Phase 2."*
- *"What's left in `docs/plans/active/foo.md`? Just the punch list, please."*
- *"Update `docs/plans/active/foo.md` to reflect what we just did."*

## What belongs here vs. elsewhere

- **Belongs here:** multi-step implementation plans, architecture proposals
  with trade-offs, migration plans, phased rollouts.
- **Doesn't belong here:** API docs (put near the code), one-off bug fix
  notes (commit message is enough), product/UX specs (different audience —
  use a separate `docs/design/` if needed).

## Index

Keep this list current when you add or move a plan.

### Active
- [`2026-07-android-rhythm-expand-collapse-all-icon-parity.md`](active/2026-07-android-rhythm-expand-collapse-all-icon-parity.md) —
  Windows fixed the rhythm selector's **Expand-All / Collapse-All** header buttons, whose icons used
  non-existent Segoe MDL2 glyphs (`&#xE9A1;`/`&#xE9A0;`) and rendered as blank "tofu" (so they looked
  *missing*); swapped to valid chevrons (`E70D`/`E70E`). Icon-fix only — handlers/behavior unchanged.
  Android's shared `RhythmSelector` **already ships** these buttons with valid Material vectors
  (`KeyboardDoubleArrowDown`/`…Up` → `expandAllRhythms()`/`collapseAllRhythms()`, `RhythmSelector.kt:225-246`)
  and **never had the tofu bug** — so this is expected to be **verify-only, no code change** (builds on the
  completed grouping-and-sorting plan). Two noted divergences to leave as-is: Android uses double chevrons
  (clearer) and hides the buttons outside grouped/non-clinical mode. *Verify Phase 1, then close.*
- [`2026-07-android-teaching-rhythm-scroll-buttons-appearance-parity.md`](active/2026-07-android-teaching-rhythm-scroll-buttons-appearance-parity.md) —
  **reskin** the Teaching rhythm-drawer page-scroll buttons (builds on the completed
  `2026-07-android-teaching-rhythm-list-scroll-buttons`). The customer flagged the 52×52 **circular
  FABs** as inappropriate, **covering the pathology titles** underneath, and going **transparent on
  press**. Windows redesigned them to **40×34 rectangular** buttons (`CornerRadius 6`) inside an
  **opaque white bordered chip** (so titles no longer show through) using `AccentButtonStyle` (fixes
  the WinUI-only transparent-on-press bug). Windows→Android, **appearance-only**: wrap the two
  `FilledIconButton`s in a `Surface` chip (`PanelBackground` + `BorderStroke(ControlBorder)` +
  `RoundedCornerShape(8)` + shadow) and shrink them to `size(40,34)` + `RoundedCornerShape(6)` in
  `RhythmSelector.kt` (+1 import). Compose keeps the fill on press already, so problem #3 is a no-op
  on Android. No behavior/scoping change. *Spec ready — one PR.*
- [`2026-07-android-tips-off-by-default-parity.md`](active/2026-07-android-tips-off-by-default-parity.md) —
  make the monitor **Tips** visibility toggle start **off** (authored overlays + "Видим:" card hidden
  until the student taps the tab). Windows→Android; one-line default flip of `MonitorModeModel.showTips`
  `true → false` (`MonitorModeModel.kt:83`). Keep the Constructor authoring preview forced-on
  (`ConstructorScreen.kt:1312`); the Teaching `onDispose` `setShowTips(false)` already exists. No strings,
  no persistence. *Spec ready — one PR.*
- [`2026-07-android-tips-authoring-and-display-parity.md`](active/2026-07-android-tips-authoring-and-display-parity.md) —
  bring the **tips (подсказки)** feature up to the Windows port (4 rounds): a Constructor **element
  palette** (9 kinds + line end-caps + lead picker), a **data-space overlay model** placed by drawing
  on the trace and **persisted** in the `.dat` (`tips:` / `tip_notes:` header fields), **rendered** on
  the monitor grid (Teaching + all-leads preview) per lead cell, a **"Видим:" comments window** shown
  as a card, and the **Tips button as a visibility toggle** (already toggles+highlights on Android).
  **Supersedes** `2026-06-android-tips-window-parity` — Windows *retired* the palette window; the plan
  **reconciles** Android's existing `TipsOverlay`/`showTips`/`selectedTipKind` scaffold (move authoring
  to the Constructor, retire the overlay, repurpose `showTips` to gate authored overlays+card). Baseline-
  relative amplitude + per-overlay home lead are the correctness notes; floats must parse `Locale.US`.
  *Spec ready — 4 phases / 4 PRs.*
- [`2026-07-android-constructor-title-own-row-parity.md`](active/2026-07-android-constructor-title-own-row-parity.md) —
  Constructor toolbar: put the **pathology title on its own row** with the action buttons
  ("settings panel") on the row below, so a long title can't crowd/hide the buttons. Windows→Android;
  layout-only. Note the **divergence**: Android's title uses `Modifier.weight(1f)`, so the exact
  Windows off-screen bug doesn't reproduce (title squeezes instead) — the two-row layout is for
  parity **and** to fix button-strip overflow on narrow screens. Split the toolbar `Surface` `Row`
  (`ConstructorScreen.kt:538`) into a `Column` of {title `Text` (ellipsized) / horizontally
  scrollable button `Row`}; +2 imports (`horizontalScroll`, `TextOverflow`), no new strings.
  *Spec ready — one PR, Phases 1–2 (+optional touch tooltip).*
- [`2026-07-android-pathology-number-clinical-case-parity.md`](active/2026-07-android-pathology-number-clinical-case-parity.md) —
  add an optional 1-based `number` field to the pathology `.dat`/manifest (parsed like
  `group`/`clinical_case`) and surface it as numbered rows `{N} <title>` in **both** the
  rhythm and clinical lists, plus a `Clinical case №N` dashboard header in clinical mode.
  Windows→Android. Data-layer
  parity for the shared dataset now being enumerated + renamed to zero-padded
  `ecg00001.dat` by the Windows `tools/pathology-enumerate/` scripts (those offline tools
  are **not** ported). No new strings. *Already implemented in the working tree
  (2026-07-03) — plan is now verify + test + commit.*
- [`2026-07-android-launch-teaching-all-rhythms-parity.md`](active/2026-07-android-launch-teaching-all-rhythms-parity.md) —
  every launch opens on the **Teaching** screen with **"All rhythms"** selected, instead of
  restoring the last-used mode. Windows→Android. Delete the last-mode restore block in
  `AppViewModel.init` (default is already Teaching via `MainActivity.kt:56`; course already defaults
  to `ALL_RHYTHMS_ID`), then remove the now-dead `lastOperatingMode` persistence + DataStore key.
  *Spec ready — one PR, two commits.*
- [`2026-07-android-constructor-view-all-leads-parity.md`](active/2026-07-android-constructor-view-all-leads-parity.md) —
  Constructor gains a read-only **"Show all 12 leads"** static grid preview button.
  Windows→Android. Render the leads directly (not via `Monitor()`); Compose
  `remember(targetFile)` auto-refreshes, so skip the manual-observer machinery.
  *Spec ready — not started.*
- [`2026-07-android-test-ctor-themes-from-courses-parity.md`](active/2026-07-android-test-ctor-themes-from-courses-parity.md) —
  Test Constructor **Manage Themes** dialog gains a **"From courses"** picker so course titles
  authored in the Course Constructor can be pulled into the question-bank theme catalog with one
  tap. Windows→Android. Single screen file (`TestConstructorScreen.kt`) + 2 new strings in all 5
  locales. *Spec ready — one PR, Phases 1–3.*
- [`2026-06-android-compare-dialog-lead-selector-layout-parity.md`](active/2026-06-android-compare-dialog-lead-selector-layout-parity.md) —
  keep the **lead selector visible** in the compare-target dialog when pathology names are long
  (Russian variants). Windows→Android. Android's weighted `Row` already prevents the off-screen
  shift that bit Windows, so this is a **verify-first + light hardening** pass, not a behavior
  change. *Spec ready — start with Phase 1 verification.*
- [`2026-06-android-lead-title-right-of-pulse-speed-gap-parity.md`](active/2026-06-android-lead-title-right-of-pulse-speed-gap-parity.md) —
  move the lead title from the left strip to the **right of the calibration pulse**, lift it above
  the isoline, and make the **trace start a function of paper speed** (so the title→trace gap scales
  with speed). Windows→Android; supersedes the *placement* half of the completed
  `lead-title-color-placement-parity` (title color stays as shipped). *Spec ready — not started.*
- [`2026-06-android-artifacts-filters-info-sign-parity.md`](active/2026-06-android-artifacts-filters-info-sign-parity.md) —
  add a circled-info "(!)" sign to the top of the Artifacts and Filters monitor dropdowns, with a
  tap/hover tooltip explaining how each menu works. Windows→Android; additive UX. Note: the filter
  explanation must cite Android's own cutoffs (25/3 Hz), not Windows' 40/0.5 Hz. *Spec ready — not started.*
- [`2026-06-android-sqi-badge-in-filter-dropdown.md`](active/2026-06-android-sqi-badge-in-filter-dropdown.md) —
  move the SQI "Quality" badge off the monitor overlay into the top of the Filters dropdown; expose
  the readout via `MonitorViewModel.signalQuality` and compute it on the filtered trace. Windows→Android;
  supersedes the completed `sqi-badge-bottom-right` move. *Spec ready — not started.*
- [`2026-06-android-grid-scheme-pink-localization-parity.md`](active/2026-06-android-grid-scheme-pink-localization-parity.md) —
  localize the `grid_scheme_pink` ("ECG film") grid-scheme label in en/zh/es (currently the
  Cyrillic `Пленка ЭКГ` in every locale). Windows→Android; deliberately reverses the earlier
  "untranslated in all locales" choice. *Spec ready — 3 one-line string edits.*
- [`2026-06-android-electrode-fault-parity.md`](active/2026-06-android-electrode-fault-parity.md) —
  wire the Электроды window's state buttons (All OK / Swapped / Displacement) to a real ECG hookup
  fault on the live trace: RA/LA limb-lead reversal + precordial attenuation via a pure
  `ElectrodeFault` transform. Windows→Android port of a shipped, unit-tested feature. *Spec ready —
  not started.*
- [`2026-06-android-grid-color-schemes-parity.md`](active/2026-06-android-grid-color-schemes-parity.md) —
  monitor paper-grid scheme rework (Yellow / ECG-film pink / Bedside monitor) + per-scheme trace colour.
  Windows→Android. *Spec ready.*
- [`2026-06-wfdb-physionet-import.md`](active/2026-06-wfdb-physionet-import.md) —
  WFDB `.hea`/`.dat`/`.mat` read-write + PhysioNet download + an Import action
  in the Pathology Constructor. 1:1 port of the shipped, unit-tested Windows
  feature (`CardioSimulatorWin/.../Data/Wfdb/*`, `Network/PhysioNetClient.cs`).
  *Spec ready — not started.*
- [`2026-06-teaching-mode-switch.md`](active/2026-06-teaching-mode-switch.md) —
  Teaching course selector becomes a mode switch ("All rhythms" → Monitor,
  default on entry; a course → lectures), context-sensitive lecture/rhythm
  picker + auto-select, chrome cleanup; plus a Course Constructor RU-name
  auto-correct fix. Porting behavior already shipped on the Windows port.
- [`2026-04-localization.md`](active/2026-04-localization.md) — per-app
  language switcher (en/ru/zh/es), string extraction, a11y sweep. Code on
  `claude/charming-hertz-d394c5`; awaiting build + on-device QA + clinician
  review of translations before merge.
- [`2026-05-ecg-photo-tracing.md`](active/2026-05-ecg-photo-tracing.md) —
  digitize a real ECG photo (single strip) in the Constructor: positionable
  underlay + tool modes, freehand sweep tracing, then auto-detect. *Proposed —
  awaiting sign-off before Phase A.*

### Proposed
*(none)*

### Completed
- [`2026-07-android-constructor-filter-dropdown-parity.md`](completed/2026-07-android-constructor-filter-dropdown-parity.md) —
  add a **display Filters dropdown** (None / LP / HP / BP) to the **Constructor** bottom control
  panel, applied to preview and all-leads overview.
- [`2026-07-android-teaching-rhythm-list-scroll-buttons.md`](completed/2026-07-android-teaching-rhythm-list-scroll-buttons.md) —
  Teaching rhythm drawer gains large up/down page-scroll buttons.
- [`2026-07-android-teaching-default-two-column-12lead.md`](completed/2026-07-android-teaching-default-two-column-12lead.md) —
  Teaching monitor opens as a **12-lead, 2-column** layout by default on entry.
- [`2026-07-android-constructor-all-leads-button-in-lead-row-parity.md`](completed/2026-07-android-constructor-all-leads-button-in-lead-row-parity.md) —
  move the Constructor's **"All leads"** (show-all-12-leads) button out of the top toolbar and
  into the **lead-button row**, trailing after `V6` (`[I]…[V6] [All leads]`). Windows→Android;
  placement-only. Delete the view-all `IconButton` from the `targetFile != null` toolbar block
  (`ConstructorScreen.kt:641`) and re-add it after the lead `TabRow` (`:663`), wrapping the
  `TabRow` in a `Row` with `Modifier.weight(1f)` and gating the button on `targetFile != null`.
  No new strings/imports. *Spec ready — one PR, two phases.*
- [`2026-07-android-pathology-grouping-and-sorting-parity.md`](completed/2026-07-android-pathology-grouping-and-sorting-parity.md) —
  Implement collapsible subgroups for duplicate titles, complexity-based sorting, and expand/collapse all
  controls in the rhythm selector. Windows→Android parity.
- [`2026-07-android-3d-heart-open-immediately-refinement-parity.md`](completed/2026-07-android-3d-heart-open-immediately-refinement-parity.md) —
  Windows refined the 3D-heart dialog (2026-07-04) to open the **whole card chrome immediately** with an
  opaque, **viewport-scoped** spinner, deferring the heavy DirectX build behind it (supersedes the
  full-screen-spinner mechanism of the completed `2026-06-…-loading-indicator-parity`). The refined
  Windows layout **converges to what Android already ships** (instant Compose `Dialog`; spinner inside the
  viewport `Box`, dismissed on model `load`, 15 s backstop). Windows→Android. *Already satisfied — no code change.*
- [`2026-06-android-3d-heart-loading-indicator-parity.md`](completed/2026-06-android-3d-heart-loading-indicator-parity.md) —
  overlay a `CircularProgressIndicator` + "Loading 3D heart…" caption on the 3D-heart viewport while it
  loads (blank white box before); dismiss it on the model's `load` event via a JS bridge, with a 15 s
  backstop. Windows→Android *intent* port. Refined 2026-07-04 — see the active
  `2026-07-android-3d-heart-open-immediately-refinement-parity.md`.
- [`2026-07-android-adaptive-displayscale-lead-count-parity.md`](completed/2026-07-android-adaptive-displayscale-lead-count-parity.md) —
  scale the live monitor's `displayScale` **up as the lead count drops** (per-count table:
  1→×6, 2→×4.4, 3–4→×3.2, 5→×2.4, 6+→×2.0) so sparse layouts stop looking like a small trace in a
  sea of grid cells. Windows→Android; one edit at `Monitor.kt:119` + a `displayScaleFactor()`
  helper in `PixelScale.kt` (+ unit test); editor/preview left on base scale.
- [`2026-06-android-exclude-courses-tcp-upload-parity.md`](completed/2026-06-android-exclude-courses-tcp-upload-parity.md) —
  exclude courses from TCP uploads (structural parity with Windows).
- [`2026-05-transit-from-legacy.md`](completed/2026-05-transit-from-legacy.md) —
  migration to flat-pathology architecture & unified rendering pipeline.
- [`2026-05-editor-rendering-parity.md`](completed/2026-05-editor-rendering-parity.md) —
  (Dropped) superseded by `2026-05-transit-from-legacy.md`.
- [`2026-05-editor-anchor-dot-projection.md`](completed/2026-05-editor-anchor-dot-projection.md) —
  (Dropped) superseded by `2026-05-transit-from-legacy.md`.
- [`2026-05-editor-mode.md`](completed/2026-05-editor-mode.md) — (Dropped)
  replaced by `2026-05-transit-from-legacy.md`. Anchor-based design dropped.
