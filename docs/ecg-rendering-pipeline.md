# ECG Rendering Pipeline: From ZIP to Screen

This document describes how the pathology dataset documented in
[data-structure.md](data-structure.md) (one `.dat` file per pathology,
all 12 leads inside, raw ADC samples) is transformed into visual
waveform paths on the Android screen.

It describes the **current rendering pipeline** as implemented, organized
around the flat data format. The pipeline has **5 stages**; the legacy
anchor-baking and per-part-calibration stages from the Parts/Series era
are gone.

---

## Pipeline overview

```
Pathologies.zip
  │
  ▼
[Stage 1]  ZIP extraction          (PathologyZipExtractor)
  │
  ▼
[Stage 2]  Manifest + .dat parsing (PathologySource → PathologyParser
  │                                  → PathologyFile)
  ▼
[Stage 3]  Baseline zeroing &      (PathologyRepository.leadWaveform)
           derived-lead synthesis   ─ DerivedLeads (if a lead is missing)
  │
  ▼
[Stage 4]  Pixel scaling           (PixelScale + EcgCalibration, in Monitor)
  │
  ▼
[Stage 5]  Line rendering          (ChartCanvas / CalibrationPulse / ekgGrid)
  │        [Editor only]           (SampleHandleOverlay)
```

What is **no longer** in the pipeline:

- Charset detection for filenames and file contents — the new format
  is strictly UTF-8 throughout.
- Anchor baking (`bakeAnchorsToSamples`) — the editor edits raw ADC
  samples directly through the same projection.
- Per-record calibration overrides — `AMax` / `AValue` / `duration` no
  longer exist in the data file. `EcgCalibration` is the single source of
  truth. (One vestige remains: an unused `samplesPerMv` parameter on
  `CalibrationPulse`; see § 5b.)
- Multi-part X-offset layout — each lead is a single contiguous stream.

---

## Stage 1 — ZIP extraction

**File:** `data/PathologyZipExtractor.kt`

The user-selected `Pathologies.zip` (or the asset-bundled equivalent)
contains:

- `manifest.txt` — dataset header + per-pathology index.
- `<pathology>.dat` — one file per pathology.

`PathologyZipExtractor.extract(context, zipUri, targetDir)` deletes any
existing `targetDir`, then copies each entry into it, **flattening nested
directories** (`entry.name.substringAfterLast('/')`). Encoding is UTF-8;
there are no charset heuristics.

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

The asset-backed source (`AssetPathologySource`) skips this stage
entirely — it reads the same layout from `assets/Pathologies/`. Extraction
is triggered from `AppViewModel.setDataFolder` → `loadFromSaf`, which then
swaps the repository to a `FilePathologySource`.

---

## Stage 2 — Manifest + `.dat` parsing

**Files:** `data/PathologySource.kt` (+ `AssetPathologySource` /
`FilePathologySource`), `domain/PathologyParser.kt`, `domain/Pathology.kt`

Both source implementations delegate text parsing to the pure-Kotlin
`PathologyParser`.

### 2.1 Manifest

`PathologySource.readManifest()` parses `manifest.txt` into a
`PathologyManifest`:

```kotlin
data class PathologyManifest(
    val version: String,            // header key "version"  → must == "1.0"
    val baseline: Int,              // header key "baseline"  → 1024
    val leadOrder: List<Lead>,      // header key "lead_order": I,II,III,aVR,…,V6
    val entries: List<PathologyEntry>,
)

data class PathologyEntry(
    val id: String,                 // "tachpm"
    val titleEn: String,            // "Atrial tachycardia, and pacemaker migration"
    val nameRu: String?,            // "Предсердная тахикардия и миграция…"
    val leadsCount: Int,            // 12 (or 6 for emd)
    val fileName: String,           // "tachpm.dat"  (derived as "$id.dat")
)
```

- The header is `key:value` lines terminated by a blank line; the header
  key is `lead_order` (snake_case) even though the field is `leadOrder`.
- Each index row below the header is **semicolon-delimited**:
  `pathology:<id>;leads:<n>;title:<en>;name:<ru>`.
- `version` is validated against `PathologyManifest.SUPPORTED_VERSION`
  (`"1.0"`); a mismatch throws `PathologyParser.FormatException`.

### 2.2 Pathology files

`PathologySource.readPathology(id)` is called lazily, only when the user
selects a rhythm. `PathologyParser.parsePathology` splits the file into
blank-line-separated blocks:

| Section | Keys | Notes |
|---|---|---|
| Header | `pathology`, `title`, `name`, `leads` | First block. |
| Lead block × N | `lead`, `count`, `points` | One block per lead. |

```kotlin
data class PathologyFile(
    val id: String,
    val titleEn: String,
    val nameRu: String?,
    val leads: Map<Lead, LeadStream>,   // insertion-ordered (LinkedHashMap)
)

data class LeadStream(
    val lead: Lead,
    val samples: IntArray,      // raw ADC, baseline-centered on 1024
)
```

`points:` is parsed as a comma-separated `IntArray`. The parser throws
`FormatException` if `samples.size != count`, if `lead` is unknown
(`Lead.fromToken`), or if a required key is missing. Missing leads (only
`emd` today, which lacks V1–V6) are simply absent from the map — there is
no placeholder. `PathologyParser` also provides `serializeManifest` /
`serializePathology` for the editor save path.

### 2.3 Removed concepts

The format removes everything that previously lived in the parser:

- No `source:` block, no `SourceSpec`, no `(x, y)` anchor tuples.
- No `(x, y, partIdenty, offset, flags)` series block refs.
- No `amplitude`, `duration`, `max`, `value` fields.
- No `Series/` directory and no `SeriesPartRef`.

---

## Stage 3 — Baseline zeroing & derived-lead synthesis

**File:** `data/PathologyRepository.kt`

### 3.1 Baseline zeroing

The single transform applied per sample:

```
output[i] = sample[i] - baseline      // baseline = 1024
```

`baseline` comes from the parsed manifest (`PathologyManifest.baseline`),
falling back to `DEFAULT_BASELINE = 1024`. The renderer expects
baseline-zeroed floats, so this happens at the repository boundary:

```kotlin
fun leadWaveform(id: String, lead: Lead): Points? {
    val file = readPathology(id) ?: return null
    val baseline = manifest?.baseline ?: DEFAULT_BASELINE

    file.leads[lead]?.let { stream ->
        return Points(stream.samples.map { (it - baseline).toFloat() })
    }
    return synthesize(lead, file.leads, baseline)?.let { Points(it) }
}

companion object { private const val DEFAULT_BASELINE = 1024 }
```

There is no `amplitude` multiplier — the format guarantees a uniform gain
across the corpus. `Points` is simply `data class Points(val values: List<Float>)`.

### 3.2 Derived-lead synthesis

**File:** `domain/DerivedLeads.kt` (orchestrated by
`PathologyRepository.synthesize`, a private helper)

When a pathology ships only a subset of leads (today: `emd`, which has
I, II, III, aVR, aVL, aVF but no V1–V6), missing leads are computed from
the present ones. The repository routes by target lead:

```kotlin
when (target) {
    III, aVR, aVL, aVF -> DerivedLeads.combineIII_aVR_aVL_aVF(zeroed(I), zeroed(II), target)
    V1, V3, V4, V5     -> DerivedLeads.combineV1_V3_V4_V5(zeroed(V2), zeroed(V6), target)
    else               -> null
}
```

(The convenience sets `DerivedLeads.DerivableFromIandII` and
`DerivableFromV2andV6` enumerate these.)

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
the file ships only limb leads), synthesis returns `null` and the lead is
rendered empty.

---

## Stage 4 — Pixel scaling

**Files:** `data/PixelScale.kt`, `data/EcgCalibration.kt`, `ui/display/Monitor.kt`

This stage is the coordinate mapping from baseline-zeroed ADC samples
to screen pixels. With the fixed-calibration format, every pathology uses
the same `EcgCalibration` — there are no per-record overrides. `Monitor`
builds the `PixelScale` and publishes it through `LocalPixelScale`.

### Calibration constants

```kotlin
// EcgCalibration.kt
gainMmPerMv    = 10f     // standard ECG gain: 10 mm per millivolt
sampleRateHz   = 500f    // dataset-wide sample rate (Hz)
adcCountsPerMv = 256f    // ADC units per millivolt
```

`sampleRateHz` is a project-wide constant; it is **not** stored in any
`.dat` file.

### MonitorModeModel defaults

```kotlin
// MonitorModeModel.kt
speed        = 25       // paper speed, mm/s
scale        = 1f       // Y-axis zoom multiplier
displayScale = 0.4f     // global display shrink factor
```

### Computing pxPerMm (the anchor) — `Monitor.kt`

```
pxPerMm = density.density * (160 / 25.4) * displayScale
```

where:
- `density.density` = Android display density (1.0 at mdpi, 2.0 at xhdpi)
- `160 / 25.4` = dp→mm conversion (160 dp = 1 inch = 25.4 mm)
- `displayScale` = global zoom factor (default 0.4)

| Term            | Value (at mdpi, displayScale = 0.4)             |
|-----------------|--------------------------------------------------|
| `160 / 25.4`    | 6.2992 dp/mm                                     |
| `density`       | 1.0                                              |
| `displayScale`  | 0.4                                              |
| **pxPerMm**     | **2.5197 px/mm**                                 |

`Monitor` constructs `PixelScale(pxPerMm, paperSpeedMmPerSec = speed,
gainZoomY = 1.0f, cal = calibration)`.

### Derived PixelScale values (`PixelScale.kt`)

```
pxPerMv         = cal.gainMmPerMv * pxPerMm * gainZoomY   = 10 * pxPerMm * 1.0
pxPerSec        = paperSpeedMmPerSec * pxPerMm            = 25 * pxPerMm
pxPerSample     = pxPerSec / cal.sampleRateHz             = (25 * pxPerMm) / 500
pxPerAdcCount   = pxPerMv / cal.adcCountsPerMv            = (10 * pxPerMm) / 256
smallGridStepPx = pxPerMm
largeGridStepPx = pxPerMm * 5
```

> Note: `gainZoomY` is hard-wired to `1.0f` in `Monitor`; the Y-axis zoom
> the user controls is `mode.scale`, applied as a `graphicsLayer`
> transform on the whole monitor (§ 5e), not folded into `pxPerMv`.

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

`ChartCanvas(points, modifier, color)` reads `points.values` (ignores
buffers shorter than 2) and `LocalPixelScale.current`, then projects via
two shared helpers:

```kotlin
fun projectDots(values, originX, stepX, stepY, baselineY) =
    values.mapIndexed { i, v -> Offset((originX + i) * stepX, baselineY - v * stepY) }

fun DrawScope.drawDots(dots, color) =
    drawPoints(dots, PointMode.Polygon, color, strokeWidth = 1.5.dp, cap = Round)
```

with `stepX = pxPerSample`, `stepY = pxPerAdcCount`, `baselineY = height/2`.

```
For each sample i:
    x[i] = i * pxPerSample
    y[i] = baselineY - (zeroedSample[i] * pxPerAdcCount)
```

The Y axis is **inverted**: positive millivolts go UP on screen. Drawing
happens in a `drawWithCache { onDrawBehind { … } }` block so projection is
recomputed only when inputs change. Line: **1.5 dp**, `StrokeCap.Round`.

### 5b. Calibration pulse

**File:** `ui/components/CalibrationPulse.kt`

Standard ECG calibration: a rectangular pulse **1 mV tall, 200 ms wide**.

```
pulseHeight = 1.0 * pxPerMv          // when samplesPerMv == 0 (the only case used)
pulseWidth  = 0.2 * pxPerSec
Shape: baseline → wing(4 dp) → up pulseHeight → across pulseWidth → down → wing(4 dp)
```

- Stroke width: **1.5 dp** · Wing width: **4 dp** · Start offset: **8 dp**
- **Vestigial:** the composable still accepts `samplesPerMv: Float = 0f`
  (a legacy per-record gain hook whose KDoc references `AMax/AValue`).
  When `> 0` it would scale the pulse by `pxPerAdcCount * samplesPerMv`,
  but neither `Lead` nor `EditableLead` passes it, so the global-gain
  branch is always taken.

### 5c. Editor handles

**File:** `ui/components/SampleHandleOverlay.kt`

In Editor mode, `SampleHandleOverlay(samples: IntArray, baseline: Int,
onSampleChanged)` draws draggable handles over the rendered waveform,
using the same projection (with explicit `baseline`):

```
y[i] = baselineY - (samples[i] - baseline) * pxPerAdcCount
```

- **Stride:** handles are subsampled so spacing ≥ 8 dp
  (`ceil(8dp / stepX)`); handle radius is **3 dp**.
- **Interaction:** `detectDragGestures` snaps the drag X to the nearest
  sample index and converts Δy → ΔADC via `(-dragAmount.y / stepY)`,
  calling `onSampleChanged(index, currentAdc + deltaAdc)`.

### 5d. ECG grid

**File:** `ui/display/Modifers.kt`  (`Modifier.ekgGrid(scheme)`)

Standard ECG paper grid with 1 mm (small) and 5 mm (large) squares,
drawn in `drawBehind` over a scheme-colored background (`Pink` /
`BlueGray`).

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

`Monitor` wraps the grid + leads in a `graphicsLayer` driven by a
`rememberTransformableState`: pinch updates `scale` (coerced to
**1.0–5.0**, mirrored into `MonitorViewModel.setScale`) and drag updates a
clamped `offset`. It also computes rows/columns from `mode.count` and
`seriesScheme` (`OneColumn`=1, `TwoColumn`=2, `Grid`=4 max columns).

### 5f. Lead layout

**Files:** `ui/display/Lead.kt`, `ui/display/EditableLead.kt`,
`ui/display/LeadsGrid.kt`

- `Lead` is a `Row`: a 48 dp `Box` holding the `CalibrationPulse` + the
  lead label (Serif, bold, 16 sp), then a weighted `Box` with `ChartCanvas`.
- `EditableLead` mirrors that layout but converts the raw `LeadStream`
  to zeroed `Points` for `ChartCanvas` and overlays `SampleHandleOverlay`.
- `LeadsGrid` (a `ColumnScope` extension) arranges N cells in
  `rows × columns`, column-major (`itemIndex = colIndex * rows + rowIndex`),
  pulling each lead from the default `LEAD_ORDER` (I…V6).

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

Step 1: Baseline zeroing (PathologyRepository.leadWaveform)
  zeroed = 1124 - 1024 = 100 ADC units → Points.values[42] = 100f

Step 2: ADC units to millivolts (conceptual)
  mV = 100 / 256 ≈ 0.391 mV

Step 3: Pixel scaling (PixelScale)
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

| Constant          | Value     | Unit             | Source file                  |
|-------------------|-----------|------------------|------------------------------|
| baseline          | 1024      | ADC units        | manifest.txt + PathologyRepository |
| gainMmPerMv       | 10        | mm/mV            | EcgCalibration.kt            |
| sampleRateHz      | 500       | Hz               | EcgCalibration.kt            |
| adcCountsPerMv    | 256       | ADC/mV           | EcgCalibration.kt            |
| paperSpeed        | 25        | mm/s             | MonitorModeModel.kt          |
| displayScale      | 0.4       | dimensionless    | MonitorModeModel.kt          |
| gainZoomY         | 1.0       | dimensionless    | Monitor.kt (hard-wired)      |
| dp-to-mm factor   | 160/25.4  | dp/mm (= 6.2992) | Monitor.kt                   |
| small grid        | 1         | mm               | PixelScale.kt                |
| large grid        | 5         | mm               | PixelScale.kt                |
| thin stroke       | 0.5       | dp               | Modifers.kt                  |
| thick stroke      | 1.5       | dp               | Modifers.kt                  |
| waveform line     | 1.5       | dp               | ChartCanvas.kt               |
| handle stride min | 8         | dp               | SampleHandleOverlay.kt       |
| handle radius     | 3         | dp               | SampleHandleOverlay.kt       |
| cal pulse stroke  | 1.5       | dp               | CalibrationPulse.kt          |
| cal pulse width   | 200       | ms               | CalibrationPulse.kt          |
| cal pulse height  | 1         | mV               | CalibrationPulse.kt          |
| zoom range        | 1.0 – 5.0 | dimensionless    | Monitor.kt                   |
| cal area width    | 48        | dp               | Lead.kt / EditableLead.kt    |
| lead label size   | 16        | sp               | Lead.kt / EditableLead.kt    |

---

## What changed from the legacy pipeline

For readers familiar with the previous Parts/Series-based pipeline:

- **Charset detection** is gone. The ZIP is UTF-8 throughout.
- **Two parser passes** (Parts + Series) collapse into a single
  `PathologyParser`.
- **Waveform assembly** is no longer "concatenate the parts of a
  series" — each lead is already a contiguous stream.
- **Anchor baking** is gone. The editor edits raw samples directly via
  `SampleHandleOverlay`.
- **Per-part calibration** is gone. Every pathology renders against the
  same `EcgCalibration`. (Only the unused `CalibrationPulse.samplesPerMv`
  hook survives as a vestige; see § 5b.)
- **Legacy Editor UI** (BlockTimeline, AnchorCanvas, PreviewPane) is
  replaced by the unified `Lead → ChartCanvas` path with
  `SampleHandleOverlay`.
