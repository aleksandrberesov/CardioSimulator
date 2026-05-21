# Editor mode — parity with the desktop editor

**Status:** dropped
**Outcome:** Superseded by `docs/plans/active/2026-05-architecture-and-rendering-migration.md`. The anchor-based design was dropped in favor of direct raw-sample editing on a unified rendering pipeline.

**Owner:** a.beresov
**Started:** 2026-05-11
**Dropped:** 2026-05-21

---

*(Historical content follows)*

# Editor mode — parity with the desktop editor

**Status:** in-progress (initial implementation landed; on-device QA pending)
**Owner:** a.beresov
**Started:** 2026-05-11
**Related issues / PRs:** —

> **Update 2026-05-17 — interpolation removed.** The `EasingCurve` enum,
> cubic-Bezier handling, and the per-anchor curve dropdown described in
> Phase 1 / PR 5 have been removed. Anchors are now joined by straight
> line segments only, and waveforms render as discrete dots rather than a
> polyline. Treat the `curve` / `EasingCurve` / Bezier references below as
> historical.

## Goal

CardioSimulator's Editor mode is currently a read-only multi-lead viewer with part-selection highlighting. The legacy desktop editor is a full waveform editor: point-level segment editing, block-level series composition, derived-lead generation, and DB persistence. This plan closes the functional gap on Android using the existing ZIP-backed data flow, so CardioSimulator becomes a usable replacement and not just a viewer.

... [rest of file]
