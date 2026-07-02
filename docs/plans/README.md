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
- [`2026-06-android-3d-heart-loading-indicator-parity.md`](active/2026-06-android-3d-heart-loading-indicator-parity.md) —
  overlay a `CircularProgressIndicator` + "Loading 3D heart…" caption on the 3D-heart viewport while the
  WebView/`<model-viewer>`/`.glb` spin up (blank white box today); dismiss it on the model's `load` event
  via a JS bridge. Windows→Android *intent* port — the Windows spinner masks a UI-thread freeze, the
  Android one masks async WebView loading. *Spec ready — not started.*
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
