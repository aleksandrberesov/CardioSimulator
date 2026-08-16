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

## Index

### Active
*(none)*

### Proposed
*(none)*

### Completed (Summary)
All 132 implementation plans for Android-Windows feature parity have been completed as of 2026-08-16.
See the `completed/` directory for individual plan details and outcomes.
