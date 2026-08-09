#!/usr/bin/env python3
"""
plan_sync_helper.py

CLI tool to audit, move, and sync feature parity implementation plans between:
- docs/plans/sync/ (raw/imported specs)
- docs/plans/active/ (in-flight active work)
- docs/plans/completed/ (shipped plans)
"""

import argparse
import json
import os
import re
import shutil
import sys

PLANS_DIR = "docs/plans"
SYNC_DIR = os.path.join(PLANS_DIR, "sync")
ACTIVE_DIR = os.path.join(PLANS_DIR, "active")
COMPLETED_DIR = os.path.join(PLANS_DIR, "completed")


def get_plan_files(folder_path):
    if not os.path.exists(folder_path):
        return []
    return [f for f in os.listdir(folder_path) if f.endswith(".md") and f != "README.md" and f != "_template.md"]


def parse_plan_title(file_path):
    try:
        with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
            for line in f:
                line = line.strip()
                if line.startswith("# "):
                    return line[2:].strip()
    except Exception:
        pass
    return os.path.basename(file_path)


def audit_plans(output_file=None):
    sync_files = set(get_plan_files(SYNC_DIR))
    completed_files = set(get_plan_files(COMPLETED_DIR))
    active_files = set(get_plan_files(ACTIVE_DIR))

    uncompleted_sync = sorted(list(sync_files - completed_files))
    completed_sync = sorted(list(sync_files & completed_files))

    report = {
        "summary": {
            "sync_total": len(sync_files),
            "completed_total": len(completed_files),
            "active_total": len(active_files),
            "sync_completed": len(completed_sync),
            "sync_uncompleted": len(uncompleted_sync)
        },
        "uncompleted_sync_plans": [
            {
                "file": f,
                "title": parse_plan_title(os.path.join(SYNC_DIR, f))
            } for f in uncompleted_sync
        ],
        "completed_sync_plans": [
            {
                "file": f,
                "title": parse_plan_title(os.path.join(COMPLETED_DIR, f))
            } for f in completed_sync
        ],
        "active_plans": [
            {
                "file": f,
                "title": parse_plan_title(os.path.join(ACTIVE_DIR, f))
            } for f in sorted(list(active_files))
        ]
    }

    if output_file:
        os.makedirs(os.path.dirname(os.path.abspath(output_file)), exist_ok=True)
        with open(output_file, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2, ensure_ascii=False)
        print(f"[SUCCESS] Audit report written to: {output_file}")
    else:
        print(f"=== Plan Audit Summary ===")
        print(f"Sync Total: {report['summary']['sync_total']}")
        print(f"Completed Total: {report['summary']['completed_total']}")
        print(f"Active Total: {report['summary']['active_total']}")
        print(f"Sync Uncompleted: {report['summary']['sync_uncompleted']}")
        print(f"Sync Completed: {report['summary']['sync_completed']}")
        print("\n--- Uncompleted Sync Plans ---")
        for p in report['uncompleted_sync_plans']:
            print(f"  - {p['file']}: {p['title']}")


def move_uncompleted_to_active(dry_run=False):
    sync_files = set(get_plan_files(SYNC_DIR))
    completed_files = set(get_plan_files(COMPLETED_DIR))
    uncompleted = sorted(list(sync_files - completed_files))

    if not uncompleted:
        print("[INFO] No uncompleted plans found in docs/plans/sync/.")
        return

    os.makedirs(ACTIVE_DIR, exist_ok=True)
    moved_count = 0

    for f in uncompleted:
        src = os.path.join(SYNC_DIR, f)
        dst = os.path.join(ACTIVE_DIR, f)
        if dry_run:
            print(f"[DRY-RUN] Would move {f} -> {ACTIVE_DIR}")
        else:
            shutil.move(src, dst)
            print(f"[MOVED] {f} -> {ACTIVE_DIR}")
            moved_count += 1

    if not dry_run:
        print(f"[SUCCESS] Moved {moved_count} uncompleted plan(s) to docs/plans/active/.")


def complete_plan(filename, dry_run=False):
    src = None
    if os.path.exists(os.path.join(ACTIVE_DIR, filename)):
        src = os.path.join(ACTIVE_DIR, filename)
    elif os.path.exists(os.path.join(SYNC_DIR, filename)):
        src = os.path.join(SYNC_DIR, filename)

    if not src:
        print(f"[ERROR] Plan file '{filename}' not found in active or sync directories.", file=sys.stderr)
        sys.exit(1)

    os.makedirs(COMPLETED_DIR, exist_ok=True)
    dst = os.path.join(COMPLETED_DIR, filename)

    if dry_run:
        print(f"[DRY-RUN] Would move {filename} -> {COMPLETED_DIR}")
    else:
        shutil.move(src, dst)
        print(f"[SUCCESS] Moved {filename} to {COMPLETED_DIR}")


def main():
    parser = argparse.ArgumentParser(description="Manage feature parity implementation plans.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # Audit command
    audit_parser = subparsers.add_parser("audit", help="Audit plans across sync, active, and completed folders.")
    audit_parser.add_argument("--output", "-o", help="Optional JSON output filepath.")

    # Move uncompleted to active command
    activate_parser = subparsers.add_parser("activate", help="Move uncompleted plans from sync/ to active/.")
    activate_parser.add_argument("--dry-run", action="store_true", help="Preview moves without making changes.")

    # Complete single plan command
    complete_parser = subparsers.add_parser("complete", help="Move a plan to completed/.")
    complete_parser.add_argument("filename", help="Filename of the plan to complete.")
    complete_parser.add_argument("--dry-run", action="store_true", help="Preview move without making changes.")

    args = parser.parse_args()

    if args.command == "audit":
        audit_plans(output_file=args.output)
    elif args.command == "activate":
        move_uncompleted_to_active(dry_run=args.dry_run)
    elif args.command == "complete":
        complete_plan(args.filename, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
