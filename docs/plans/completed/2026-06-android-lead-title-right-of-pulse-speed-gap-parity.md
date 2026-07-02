# Plan: Lead title — move right of the pulse, lift it, speed-aware trace start

**Created:** 2026-06-30
**Status:** COMPLETED
**Direction:** **Windows → Android** (reverse of the usual). The lead-label layout was iterated
on the WinUI 3 port in response to customer feedback on the live monitor; Android should follow.

**Supersedes:** the *placement* half of the completed
[`completed/2026-06-android-lead-title-color-placement-parity.md`](../completed/2026-06-android-lead-title-color-placement-parity.md).
That plan shipped two things on Android: **(1) title color = trace color** — *keep it, unchanged* —
and **(2) title in a strip to the LEFT of the calibration pulse, centered on the baseline** — *this
is what now changes.* Do **not** re-do the color; do **not** treat the new right-of-pulse layout as
a regression of the old left-strip layout.

**Android draw site:** `app/src/main/java/com/example/cardiosimulator/ui/display/Lead.kt` (the
per-lead cell: calibration pulse + title + trace). **Windows reference:**
`CardioSimulatorWin/src/CardioSimulator.App/Rendering/EcgRenderer.cs`.

---

## What changes (from the shipped left-strip layout)

The lead title (`I`, `II`, `III`, `aVR`, `aVL`, `aVF`, `V1`..`V6`) moves from a **left strip,
centered on the baseline** to **right of the calibration pulse, floating just above the isoline**.
And the **trace start becomes a function of paper speed** so the gap after the title scales with
speed instead of being a fixed distance.

```
BEFORE (shipped):   [ title strip | pulse | ──── trace ──── ]   (title left of pulse, on baseline)
AFTER  (this plan):  cellX
                      │  LeadIn      pulse              TitleGap   title (drawn wide, lifted up)
                      │ ├────┤ ├wing|0.2s plateau|wing┤ ├──┤ ├──────────────┐
                      │                                      ▲ floats TitleLift above the isoline
                      │ ── isoline ─────────────────────────────────────────────────────────────
                      │                                 └ only TitleClearance reserved ┘  gap │ trace →
                      │                                   (lifted title overlaps the trace's lead-in)
```

Still presentation-only: no change to waveform data, grid, calibration-pulse geometry, or amplitude
scale.

---

## Reference: the final Windows layout

Single source of truth in `EcgRenderer.cs`. The old fixed `CalAreaWidth = 80` constant was
**replaced by a method** `TraceLeft(PixelScale scale)` whose value depends on paper speed:

```csharp
// Per-cell left-margin constants (px at the reference scale)
private const float LeadIn         = 8f;     // cell-left → pulse
private const float PulseWing      = 4f;     // each pulse foot
private const float PulseSeconds   = 0.2f;   // pulse plateau width, in PAPER TIME (× PxPerSec)
private const float TitleGap       = 4f;     // pulse → lead title
private const float TitleArea      = 32f;    // DRAWN title width (fits aVR/aVL/aVF @ 14)
private const float TitleClearance = 18f;    // horizontal room RESERVED before the trace
private const float TitleLift      = 10f;    // px the title floats above the isoline
private const float TraceGapBase   = 3f;     // minimum title → trace gap (fixed)
private const float TraceGapSeconds= 0.05f;  // additional title → trace gap, in PAPER TIME
private const float LabelFontSize  = 14f;

public static float TraceLeft(PixelScale scale) =>
    LeadIn + 2f * PulseWing + PulseSeconds * scale.PxPerSec     // pulse  (scales with speed)
    + TitleGap + TitleClearance                                 // fixed
    + TraceGapBase + TraceGapSeconds * scale.PxPerSec;          // gap    (scales with speed)
//  = 41 + 0.25 * PxPerSec
```

- **Pulse:** `startX = cellX + LeadIn` (far left, no left strip); `pulseWidth = PulseSeconds *
  PxPerSec`; shape unchanged. Returns its right edge `pulseRight`.
- **Title:** drawn at `x = pulseRight + TitleGap`, **left-aligned, single line (no wrap)**, with its
  **bottom at `baselineY − TitleLift`** (floats ~10 px above the isoline). Drawn `TitleArea` (32)
  wide for readability but only `TitleClearance` (18) reserved before the trace → the title overlaps
  the trace's leading edge, kept clear of the waveform by the lift.
- **Trace start / width:** every layout (`main monitor`, `compare panes`, `editable lead`) uses
  `TraceLeft(scale)`; `traceWidth = cellW − TraceLeft(scale)`.

`PxPerSec = PaperSpeedMmPerSec × PxPerMm`, so the time terms are literally "this many seconds of
paper": `0.2 s` pulse, `0.05 s` extra gap. Effect: small gap at 12.5 mm/s, larger at 100 mm/s,
never colliding with the trace.

> **Important coupling:** `TraceLeft(scale)` is the **only** trace-start origin — it feeds the draw
> path, the editor's pixel→sample hit-testing (`EditableLeadControl`, 3 sites:
> `(x − TraceLeft(scale)) / PxPerSample`), and the image `TraceExtractor` (now takes a `traceLeft`
> param). Because it's a function of `scale`, draw and hit-test stay consistent **at every speed**.

---

## Steps (Android)

### 1. Move the title from the left strip to the right of the pulse
In `Lead.kt` (and any shared lead-cell layout):
- **Remove the left label strip** added by the previous plan (the ~32 dp box before the pulse) and
  put the **pulse back at the far left** (`startX = cellX + LeadIn`, ~8 dp; pulse shape unchanged).
- Draw the **title to the right of the pulse**, at `x = pulseRight + TitleGap` (~4 dp).
- Keep the title **trace color** and **~14 sp** (both already shipped).

### 2. Make the trace-start (cal-area) a function of paper speed
Replace Android's constant "trace left / cal-area width" with the analog of `TraceLeft`:

```
traceLeft(speed) = LeadIn + 2*PulseWing + PulseSeconds*pxPerSec(speed)
                 + TitleGap + TitleClearance
                 + TraceGapBase + TraceGapSeconds*pxPerSec(speed)
```

`pxPerSec(speed) = paperSpeedMmPerSec * pxPerMm` (Android already has both factors for the pulse and
grid). Fixed terms in **dp** (`LeadIn 8, PulseWing 4, TitleGap 4, TitleClearance 18, TitleLift 10,
TraceGapBase 3`); the `PulseSeconds 0.2` / `TraceGapSeconds 0.05` terms as **seconds × pxPerSec**.

### 3. Lift the title; draw it wider than the reserved clearance
- Position the title's **bottom ~`TitleLift` (10 dp) above the isoline** (not centered on it).
- Draw the title in a **~`TitleArea` (32 dp) wide, single-line** slot so `aVR/aVL/aVF` aren't
  clipped, even though only `TitleClearance` (18 dp) sits before the trace. The lifted title
  overlapping the trace's lead-in is **intentional**.
- If Android uses a Compose `Text` rather than `drawText`, place it after the pulse with a negative
  vertical `offset` (≈ −10 dp) / top-alignment, single line, `maxLines = 1`, `softWrap = false`.

### 4. Keep the editor/tap mapping consistent
If Android's lead **editor** maps tap-x → sample index via the trace-left offset
(`(x − traceLeft) / pxPerSample`), it must use the **same speed-dependent** `traceLeft(speed)` as the
draw path. Verify by tapping a known sample at **two different paper speeds** and confirming the
handle lands correctly both times. A constant offset here would shift every edited sample once the
trace start moves with speed.

### 5. Static course figures (if separate)
If Android renders course/lecture ECG figures separately from the live monitor, they use a **single
fixed paper speed** — put the label **right of the pulse, just above the baseline**, trace color,
~14 sp, with a **constant** trace start (no speed term, no lift/clearance tuning). Mirrors Windows
`EcgSvgRenderer` (`text-anchor="start"`, `y = baselineY − 4`).

---

## Verification
Open the monitor (12-lead) and check at **several paper speeds (12.5, 25, 100 mm/s)**, for `III`,
`aVR`, `aVL`, `aVF`:

| Check | Expected |
|---|---|
| Title color | Same teal as that lead's waveform line (unchanged) |
| Title position | **Right of** the pulse, **floating above** the isoline |
| 3-letter leads | `aVR`/`aVL`/`aVF` fully visible, not clipped |
| Trace start vs speed | Close to the pulse; title→trace gap **grows with speed** (small at 12.5, larger at 100); never overlaps the pulse or pushes the trace off-cell |
| Title vs trace | Lifted title floats over the waveform's lead-in without obscuring it |
| Editor (if any) | Tap selects the correct sample **at every speed** |

Run the Android test suite; nothing data/scale-related should change.

---

## Acceptance checklist
- [x] Pulse back at the **far left**; the left label strip is **removed**.
- [x] Lead title sits **right of the pulse**, **floating ~`TitleLift` above the isoline**.
- [x] Title kept **trace color** and **~14 sp** (carried over from the completed plan).
- [x] Title drawn `TitleArea` wide (no clip of `aVR/aVL/aVF`); only `TitleClearance` reserved.
- [x] **Trace-start is a function of paper speed** (pulse + title→trace gap scale with `pxPerSec`).
- [x] Editor tap→sample mapping (if present) uses the **same speed-dependent** trace-left.
- [x] Static course figures (if separate) use a fixed-speed trace start, label right of the pulse.
- [x] No change to waveform data, grid, calibration-pulse geometry, or amplitude scale.
