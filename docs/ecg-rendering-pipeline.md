# ECG Rendering Pipeline: From ZIP to Screen

This document describes how the pathology dataset documented in
[data-structure.md](data-structure.md) (one `.dat` file per pathology,
all 12 leads inside, raw ADC samples) is transformed into visual
waveform paths on the Android screen.

It describes the **target rendering pipeline**, organized around the
new flat data format. The pipeline has **5 stages**; the legacy
anchor-baking and per-part-calibration stages from the Parts/Series
era are gone.

---

## Pipeline overview

```
Pathologies.zip
  │
  ▼
[Stage 1]  ZIP extraction          (ZipExtractor)
  │
  ▼
[Stage 2]  Manifest + .dat parsing (EcgSource → PathologyFile)
  │
  ▼
[Stage 3]  Baseline zeroing &      (EcgRepository.leadWaveform)
           derived-lead synthesis   ─ DerivedLeads (if a lead is missing)
  │
  ▼
[Stage 4]  Pixel scaling           (PixelScale + EcgCalibration)
  │
  ▼
[Stage 5]  Line rendering          (ChartCanvas / CalibrationPulse / ekgGrid)
  │        [Editor only]           (SampleHandleOverlay)
```

What is **no longer** in the pipeline:

- Charset detection for filenames and file contents — the new format
  is strictly UTF-8 throughout.
- Anchor baking (`bakeAnchorsToSamples`) — the editor now edits raw ADC
  samples directly through the same projection.
- Per-record calibration overrides — `AMax` / `AValue` / `duration` no
  longer exist in the data file. The playback engine's
  `EcgCalibration` is the single source of truth.
- Multi-part X-offset layout (`EditableLead`) — each lead is a single
  contiguous stream.

---

## Stage 1 — ZIP extraction

**File:** `data/ZipExtractor.kt`

The user-selected `Pathologies.zip` (or the asset-bundled equivalent)
contains:

- `manifest.txt` — dataset header + per-pathology index.
- `<pathology>.dat` — one file per pathology, 56 in the v1.0 dataset.

Layout is flat (no subdirectories), encoding is UTF-8 throughout, line
endings are LF. The extractor copies entries into
`filesDir/pathologies/` without any encoding heuristics: the new format
is intentionally homogeneous so there is nothing to detect.

```
Pathologies.zip
├── manifest.txt
├── 1abblock.dat
├── 2abblock1.dat
├── …
└── wpwrightlat.dat
                   ──extract──►   filesDir/pathologies/
                                  ├── manifest.txt
                                  ├── 1abblock.dat
                                  └── …
```

The asset-backed source can skip this stage entirely — it reads the
same layout from `assets/Pathologies/`.

---

## Stage 2 — Manifest + `.dat` parsing

**Files:** `data/EcgSource.kt`, `domain/PathologyFile.kt`

### 2.1 Manifest

`EcgSource.readManifest()` is called once after each source swap. It
parses `manifest.txt` into a `PathologyManifest`:

```kotlin
data class PathologyManifest(
    val version: String,            // "1.0"
    val baseline: Int,              // 1024
    val leadOrder: List<Lead>,      // I, II, III, aVR, aVL, aVF, V1..V6
    val entries: List<PathologyEntry>,
)

data class PathologyEntry(
    val id: String,                 // "tachpm"
    val titleEn: String,            // "Atrial tachycardia, and pacemaker migration"
    val nameRu: String?,            // "Предсердная тахикардия и миграция водителя ритма"
    val leadsCount: Int,            // 12 (or 6 for emd)
    val fileName: String,           // "tachpm.dat"
)
```

The manifest's `version` field MUST be validated before any further
reads — older or newer formats are rejected with an explicit error
rather than parsed loosely.

### 2.2 Pathology files

`EcgSource.readPathology(id)` is called lazily, only when the user
selects a rhythm. The `.dat` parser walks the file top-to-bottom:

| Section | Keys | Notes |
|---|---|---|
| Header | `pathology`, `title`, `name`, `leads` | One block, terminated by a blank line. |
| Lead block × N | `lead`, `count`, `points` | One block per lead, separated by blank lines. |

```kotlin
data class PathologyFile(
    val id: String,
    val titleEn: String,
    val nameRu: String?,
    val leads: Map<Lead, LeadStream>,
)

data class LeadStream(
    val lead: Lead,
    val samples: IntArray,      // raw ADC, baseline-centered on 1024
)
```

`points:` is parsed as a comma-separated `IntArray`. The parser checks
that `samples.size == count` and that `lead` is a member of
`Lead.values()`. Missing leads (only `emd` today, which lacks V1–V6) are
simply absent from the map — there is no placeholder.

### 2.3 Removed concepts

The new format removes everything that previously lived in the
parser:

- No `source:` block, no `SourceSpec`, no `(x, y)` anchor tuples.
- No `(x, y, partIdenty, offset, flags)` series block refs.
- No `amplitude`, `duration`, `max`, `value` fields.
- No `Series/` directory and no `SeriesPartRef`.

---

## Stage 3 — Baseline zeroing & derived-lead synthesis

**File:** `data/EcgRepository.kt`

### 3.1 Baseline zeroing

The single transform applied per sample:

```
output[i] = sample[i] - 1024
```

`1024` is the dataset-level baseline declared by `manifest.txt`
(`baseline:1024`). The renderer expects baseline-zeroed floats, so this
happens at the repository boundary:

```kotlin
fun leadWaveform(id: String, lead: Lead): Points? {
    val file = readPathology(id) ?: return null
    val src  = file.leads[lead]?.samples
            ?: DerivedLeads.synthesize(lead, file.leads)   // §3.2
            ?: return null
    return Points(FloatArray(src.size) { i -> (src[i] - SAMPLE_BASELINE).toFloat() })
}

companion object { private const val SAMPLE_BASELINE = 1024 }
```

There is no `amplitude` multiplier — the new format guarantees a uniform
gain across the corpus.

### 3.2 Derived-lead synthesis

**File:** `domain/DerivedLeads.kt`

When a pathology ships only a subset of leads (today: `emd`, which has
I, II, III, aVR, aVL, aVF but no V1–V6), missing leads are computed
from the present ones.

#### From leads I and II (Einthoven / Goldberger)

```
III  =  II - I
aVR  = -(I + II) / 2
aVL  =  (2*I - II) / 2
aVF  =  (2*II - I) / 2
```

Operates sample-by-sample. Inputs are truncated to the shorter length.

#### From leads V2 and V6 (angular projection)

Precordial leads are modelled at fixed angles on the chest:

| Lead | Angle (degrees) |
|------|-----------------|
| V1   | 115             |
| V2   | 94 (basis)      |
| V3   | 70              |
| V4   | 45              |
| V5   | 23              |
| V6   | 0 (basis)       |

V2 and V6 form a 2D basis. Each missing lead is reconstructed by
decomposing its angle into that basis:

```
Given target angle a, V2 angle = 94 deg, V6 angle = 0 deg:

det   = cos(V2a) * sin(V6a) - cos(V6a) * sin(V2a)

alpha = (cos(a) * sin(V6a) - cos(V6a) * sin(a)) / det
beta  = (cos(V2a) * sin(a) - cos(a) * sin(V2a)) / det

target_sample[i] = alpha * V2[i] + beta * V6[i]
```

If neither basis pair is available (e.g., the requested lead is V1 but
the file ships only limb leads), `synthesize` returns `null` and the UI
shows that lead as empty.

---

## Stage 4 — Pixel scaling

**Files:** `data/PixelScale.kt`, `data/EcgCalibration.kt`, `ui/display/Monitor.kt`

This stage is the coordinate mapping from baseline-zeroed ADC samples
to screen pixels. With the new fixed-calibration format, every
pathology uses the same `EcgCalibration` — there are no per-record
overrides.

### Calibration constants

```kotlin
// EcgCalibration.kt
gainMmPerMv    = 10.0    // standard ECG gain: 10 mm per millivolt
sampleRateHz   = 500.0   // dataset-wide sample rate (Hz)
adcCountsPerMv = 256.0   // ADC units per millivolt
```

`sampleRateHz` is the value chosen by the playback engine; it is **not**
stored in any `.dat` file and is treated as a project-wide constant.

### MonitorModeModel defaults

```kotlin
// MonitorModeModel.kt
speed        = 25       // paper speed, mm/s
scale        = 1.0      // Y-axis zoom multiplier
displayScale = 0.4      // global display shrink factor
```

### Computing pxPerMm (the anchor)

```
pxPerMm = density * (160 / 25.4) * displayScale
```

where:
- `density` = Android display density (1.0 at mdpi, 2.0 at xhdpi, etc.)
- `160 / 25.4` = conversion from Android dp to mm (160 dp = 1 inch, 1 inch = 25.4 mm)
- `displayScale` = global zoom factor (default 0.4)

| Term            | Value (at mdpi, displayScale = 0.4)             |
|-----------------|--------------------------------------------------|
| `160 / 25.4`    | 6.2992 dp/mm                                     |
| `density`       | 1.0                                              |
| `displayScale`  | 0.4                                              |
| **pxPerMm**     | **2.5197 px/mm**                                 |

### Derived PixelScale values

All formulas below use the constants from `EcgCalibration` and `MonitorModeModel`:

```
pxPerMv       = gainMmPerMv * pxPerMm * gainZoomY
              = 10 * pxPerMm * scale

pxPerSec      = paperSpeedMmPerSec * pxPerMm
              = 25 * pxPerMm

pxPerSample   = pxPerSec / sampleRateHz
              = (25 * pxPerMm) / 500

pxPerAdcCount = pxPerMv / adcCountsPerMv
              = (10 * pxPerMm * scale) / 256
```

### Numeric example (mdpi, default settings)

| Value            | Formula                       | Result       |
|------------------|-------------------------------|--------------|
| pxPerMm          | 1.0 * 6.2992 * 0.4            | 2.52 px/mm   |
| pxPerMv          | 10 * 2.52 * 1.0               | 25.2 px/mV   |
| pxPerSec         | 25 * 2.52                     | 63.0 px/s    |
| pxPerSample      | 63.0 / 500                    | 0.126 px     |
| pxPerAdcCount    | 25.2 / 256                    | 0.098 px/ADC |
| smallGridStepPx  | 2.52                          | 2.52 px      |
| largeGridStepPx  | 2.52 * 5                      | 12.6 px      |

Every pathology renders against the same `PixelScale`.

---

## Stage 5 — Line rendering

### 5a. Waveform lines

**File:** `ui/components/ChartCanvas.kt`

For each lead, the baseline-zeroed sample array is drawn as a
continuous connected path (polyline):

```
baselineY = canvas.height / 2

For each sample i:
    x[i] = i * pxPerSample
    y[i] = baselineY - (sample[i] * pxPerAdcCount)
```

The Y axis is **inverted**: positive millivolts go UP on screen
(subtracted from baseline).

Line rendering uses `drawPoints(PointMode.Polygon, ...)`:
- Stroke width: **1.5 dp**
- Stroke cap: `Round`

### 5b. Calibration pulse

**File:** `ui/components/CalibrationPulse.kt`

Standard ECG calibration: a rectangular pulse **1 mV tall, 200 ms wide**.

```
pulseHeight = 1.0 * pxPerMv
pulseWidth  = 0.2 * pxPerSec

Shape:  baseline → wing (4 dp) → up pulseHeight → across pulseWidth → down → wing (4 dp)
```

- Stroke width: **1.5 dp**
- Wing width: **4 dp**
- Start offset: **8 dp** from left edge

### 5c. Editor handles

**File:** `ui/components/SampleHandleOverlay.kt`

In Editor mode, an overlay draws draggable handles on top of the
rendered waveform. Projection uses the same `x[i]`, `y[i]` formulas
from § 5a.

- **Stride:** handles are subsampled to maintain a minimum visual
  spacing (default 8 dp).
- **Interaction:** vertical drag on any region snaps to the nearest
  sample and translates Δy → ΔADC units via `1/pxPerAdcCount`.

### 5d. ECG grid

**File:** `ui/display/Modifers.kt`

Standard ECG paper grid with 1 mm (small) and 5 mm (large) squares.

```
smallStep = pxPerMm        (= smallGridStepPx)
largeStep = pxPerMm * 5    (= largeGridStepPx)
```

| Parameter | Small grid | Large grid |
|-----------|------------|------------|
| Stroke    | 0.5 dp     | 1.5 dp     |
| Interval  | 1 mm       | 5 mm       |

### 5e. Viewport zoom and pan

**File:** `ui/display/Monitor.kt`

The `Monitor` composable wraps the grid + leads in a `graphicsLayer`
transform that supports pinch-zoom and drag.

### 5f. Lead layout

**Files:** `ui/display/Lead.kt`, `ui/display/EditableLead.kt`

Each `Lead` cell wraps `ChartCanvas` in a `Row` with two columns.
`EditableLead` further overlays `SampleHandleOverlay`.

---

## Complete transformation chain (single sample)

Tracing one raw integer sample from the `.dat` file to a pixel coordinate:

```
Given:
  raw_sample      = 1124          (integer from points: field)
  baseline        = 1024
  adcCountsPerMv  = 256
  gainMmPerMv     = 10 mm/mV
  pxPerMm         = 2.52 px/mm     (example)
  gainZoomY       = 1.0
  sample_index    = 42
  sampleRateHz    = 500

Step 1: Baseline zeroing
  zeroed = 1124 - 1024 = 100 ADC units

Step 2: ADC units to millivolts (conceptual)
  mV = 100 / 256 ≈ 0.391 mV

Step 3: Pixel scaling
  pxPerMv       = 10 * 2.52 * 1.0  = 25.2  px/mV
  pxPerAdcCount = 25.2 / 256       = 0.098 px/ADC

Step 4: X position
  pxPerSec    = 25 * 2.52   = 63.0   px/s
  pxPerSample = 63.0 / 500  = 0.126  px/sample
  x = 42 * 0.126 = 5.292 px

Step 5: Y position
  baselineY = canvas.height / 2          (e.g., 400 px)
  y = 400 - (100 * 0.098) = 400 - 9.8 = 390.2 px

Result: point at (5.29, 390.2) on the canvas
```

---

## Summary of all constants

| Constant          | Value     | Unit             | Source file               |
|-------------------|-----------|------------------|---------------------------|
| baseline          | 1024      | ADC units        | manifest.txt + EcgRepository |
| gainMmPerMv       | 10        | mm/mV            | EcgCalibration.kt         |
| sampleRateHz      | 500       | Hz               | EcgCalibration.kt         |
| adcCountsPerMv    | 256       | ADC/mV           | EcgCalibration.kt         |
| paperSpeed        | 25        | mm/s             | MonitorModeModel.kt       |
| displayScale      | 0.4       | dimensionless    | MonitorModeModel.kt       |
| gainZoomY         | 1.0       | dimensionless    | MonitorModeModel.kt       |
| dp-to-mm factor   | 160/25.4  | dp/mm (= 6.2992) | Monitor.kt                |
| small grid        | 1         | mm               | PixelScale.kt             |
| large grid        | 5         | mm               | PixelScale.kt             |
| thin stroke       | 0.5       | dp               | Modifers.kt               |
| thick stroke      | 1.5       | dp               | Modifers.kt               |
| waveform line     | 1.5       | dp               | ChartCanvas.kt            |
| handle stride min | 8         | dp               | SampleHandleOverlay.kt    |
| cal pulse stroke  | 1.5       | dp               | CalibrationPulse.kt       |
| cal pulse width   | 200       | ms               | CalibrationPulse.kt       |
| cal pulse height  | 1         | mV               | CalibrationPulse.kt       |
| zoom range        | 1.0 – 5.0 | dimensionless    | Monitor.kt                |
| cal area width    | 48        | dp               | Lead.kt                   |
| lead label size   | 16        | sp               | Lead.kt                   |

---

## What changed from the legacy pipeline

For readers familiar with the previous Parts/Series-based pipeline:

- **Charset detection** is gone. The new ZIP is UTF-8 throughout.
- **Two parser passes** (Parts + Series) collapse into a single
  pathology-file parser.
- **Waveform assembly** is no longer "concatenate the parts of a
  series" — each lead is already a contiguous stream.
- **Anchor baking** is gone. The editor edits raw samples directly.
- **Per-part calibration** is gone. Every pathology renders against the
  same dataset-wide `EcgCalibration`.
- **Legacy Editor UI** (BlockTimeline, AnchorCanvas, PreviewPane) is
  replaced by the unified `Lead → ChartCanvas` path with
  `SampleHandleOverlay`.
