---
name: plan-sync-manager
description: >-
  Audits, manages, and syncs feature parity implementation plans between
  docs/plans/sync, docs/plans/active, and docs/plans/completed. Use when asked to
  check uncompleted plans, audit feature parity specs, or move plans into active
  or completed states.
---

# Plan Sync Manager

## Overview
This skill manages the lifecycle of implementation plans and feature parity specifications across:
- `docs/plans/sync/` (imported feature specs needing parity implementation)
- `docs/plans/active/` (plans currently in-flight)
- `docs/plans/completed/` (shipped or retired plans)

It provides a CLI helper script (`tools/plan_sync_helper.py` or `scripts/plan_sync_helper.py`) to quickly audit status and safely move plans between folders.

## Quick Start

### 1. Audit Uncompleted Plans
Audit the status of plan files across `sync/`, `active/`, and `completed/`:
```bash
python tools/plan_sync_helper.py audit
```
To write the audit report to a JSON file:
```bash
python tools/plan_sync_helper.py audit --output .artifacts/plan_audit.json
```

### 2. Move Uncompleted Plans to Active
To activate all plans in `docs/plans/sync/` that do not exist in `docs/plans/completed/`:
```bash
python tools/plan_sync_helper.py activate
```

### 3. Move a Shipped Plan to Completed
When work on a plan is finished:
```bash
python tools/plan_sync_helper.py complete 2026-08-android-some-feature-parity.md
```

## Utility Scripts

### `tools/plan_sync_helper.py`
The CLI tool supports three main subcommands:

- **`audit`**: Lists total plan counts and identifies uncompleted sync plans.
  - Options: `--output FILE` (specifies path to save structured JSON report).
- **`activate`**: Moves uncompleted plans from `sync/` into `active/`.
  - Options: `--dry-run` (previews file moves without changing disk state).
- **`complete <filename>`**: Moves a plan from `active/` or `sync/` into `completed/`.
  - Options: `--dry-run` (previews file move).

## Manual Workflow Guidelines

When working with plans manually or modifying `docs/plans/README.md`:
1. **Uncompleted Plans**: A plan in `docs/plans/sync/` is considered uncompleted if no matching filename exists in `docs/plans/completed/`.
2. **Moving Plans**:
   - In-progress work lives in `docs/plans/active/`.
   - Once implemented and merged, move the plan file into `docs/plans/completed/`.
3. **Updating Index**: Remember to update the index in `docs/plans/README.md` under the `### Active` or `### Completed` sections when moving plans.

## Common Mistakes
- **Deleting sync specs instead of moving**: Do not delete plans from `sync/` — use `activate` to move them to `active/`.
- **Duplicate filename conflicts**: Always verify if a file already exists in `active/` before moving to avoid accidental overwrites.
