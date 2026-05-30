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
- [`2026-05-transit-from-legacy.md`](completed/2026-05-transit-from-legacy.md) —
  migration to flat-pathology architecture & unified rendering pipeline.
- [`2026-05-editor-rendering-parity.md`](completed/2026-05-editor-rendering-parity.md) —
  (Dropped) superseded by `2026-05-transit-from-legacy.md`.
- [`2026-05-editor-anchor-dot-projection.md`](completed/2026-05-editor-anchor-dot-projection.md) —
  (Dropped) superseded by `2026-05-transit-from-legacy.md`.
- [`2026-05-editor-mode.md`](completed/2026-05-editor-mode.md) — (Dropped)
  replaced by `2026-05-transit-from-legacy.md`. Anchor-based design dropped.
