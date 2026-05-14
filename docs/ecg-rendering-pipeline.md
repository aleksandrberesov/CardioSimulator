# ECG Rendering Pipeline: From ZIP to Screen

This document describes how raw ECG data stored in a ZIP archive is
transformed into visual waveform paths on the Android screen. The pipeline
has **7 distinct stages**, each with its own constants and formulas.

---

## Pipeline overview

```
ZIP file
  |
  v
[Stage 1]  Unzip & charset detection  (ZipDecompressor)
  |
  v
[Stage 2]  File parsing: key-value text -> domain model  (EcgSource / EcgFileFormat)
  |
  v
[Stage 3]  Waveform assembly & baseline zeroing  (EcgRepository)
  |
  v
[Stage 4]  Derived-lead synthesis  (DerivedLeads)
  |
  v
[Stage 5]  Anchor interpolation (editor path only)  (CurveInterpolation)
  |
  v
[Stage 6]  Pixel scaling: physical units -> screen pixels  (PixelScale)
  |
  v
[Stage 7]  Path rendering & grid drawing  (ChartCanvas / CalibrationPulse / ekgGrid / AnchorHandleOverlay)
```

---

## Stage 1 -- ZIP extraction

**File:** `data/ZipDecompressor.kt`

The ZIP archive contains two directories:
- `Series/` -- series descriptor files (one per ECG lead/rhythm)
- `Parts/` -- waveform-part files (raw sample data)

### Charset detection

ZIP entry names often contain Cyrillic text encoded in legacy charsets.
The decompressor tries three candidate encodings and picks the one that
produces the most Russian characters (U+0410..U+044F, plus yo/Yo):

| Priority | Charset         |
|----------|-----------------|
| 1        | UTF-8           |
| 2        | IBM866 (CP866)  |
| 3        | windows-1251    |

The winning charset is used for the actual extraction pass.

### File-content decoding

Individual `.txt` files inside the archive go through a second
charset-detection pass (`decodeEcgText()` in `EcgSource.kt` and
`EcgData.kt`):

1. Check for UTF-16 BOM (FF FE or FE FF).
2. Try strict UTF-8.
3. Decode with windows-1251 and IBM866 in parallel.
4. Count Russian characters in each candidate; pick the winner.

---

## Stage 2 -- File parsing

**Files:** `domain/EcgData.kt`, `data/EcgSource.kt`

### Data files format

Each file is plain text with `key:value` lines. The parser
(`EcgFileFormat.readKeyValues`) splits on the first colon per line.

### Part file (`Parts/`)

| Key         | Type          | Meaning                                             |
|-------------|---------------|-----------------------------------------------------|
| `identy`    | String        | Unique part identifier                              |
| `title`     | String        | Display name                                        |
| `lead`      | Lead enum     | I, II, III, aVR, aVL, aVF, V1..V6                  |
| `pathology` | String        | Diagnosis label                                     |
| `amplitude` | Float         | Legacy gain multiplier (viewer path only)           |
| `duration`  | Int (ms)      | Total waveform duration in milliseconds             |
| `points`    | CSV of Int    | Raw ADC sample values (integer, baseline ~1024)     |
| `source`    | SourceSpec    | Semicolon-delimited metadata (see below)            |

### Source spec (inside `source:`)

Semicolon-separated `key:value` pairs. Parenthesised tuples inside
`points:` are either **anchor points** `(x, y, curve)` or **series
block refs** `(x, y, partIdenty, offset [, flags])`.

Key calibration fields:

| Field   | Default | Meaning                                   |
|---------|---------|-------------------------------------------|
| `max`   | 200     | `AMax` -- full-scale source-unit range    |
| `value` | 2       | `AValue` -- equivalent millivolt range    |

### Derived values on WaveformPart

```
effectiveSampleRateHz = samples.size / (duration / 1000)    [Hz]

samplesPerMv          = AMax / AValue                       [source-units / mV]
```

With defaults: `samplesPerMv = 200 / 2 = 100` source-units per mV.

### Series file (`Series/`)

References a sequence of parts via `SeriesPartRef(x, y, partIdenty, offset, flags)`.
Also carries its own `aMax`/`aValue` for series-level calibration.

---

## Stage 3 -- Waveform assembly

**File:** `data/EcgRepository.kt`

### Constants

| Constant            | Value    | Meaning                                  |
|---------------------|----------|------------------------------------------|
| `SAMPLE_BASELINE`   | 1024.0   | ADC zero-point offset                    |

### Viewer path (`assembleWaveform`)

Concatenates all parts of a series into a single float list. Per sample:

```
output[i] = (sample[i] - 1024) * amplitude
```

where `amplitude` is the legacy per-part gain factor (defaults to 1.0
when absent).

### Editor path (`assembleWaveformParts` + `baselineZeroedSamples`)

Returns parts separately, without the legacy `amplitude` multiplication.
The renderer applies per-part `samplesPerMv` instead:

```
baselineZeroed[i] = sample[i] - 1024
```

The per-part `samplesPerMv` is passed to `ChartCanvas` so the renderer
can use `pxPerAdcCountFor(samplesPerMv)` for correct gain.

---

## Stage 4 -- Derived-lead synthesis

**File:** `domain/DerivedLeads.kt`

When only a subset of leads is recorded, the missing ones are computed.

### From leads I and II (Einthoven / Goldberger)

```
III  =  II - I
aVR  = -(I + II) / 2
aVL  =  (2*I - II) / 2
aVF  =  (2*II - I) / 2
```

Operates sample-by-sample. Inputs are truncated to the shorter length.

### From leads V2 and V6 (angular projection)

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

---

## Stage 5 -- Anchor interpolation (editor only)

**File:** `domain/CurveInterpolation.kt`

In the editor, waveform shape is defined by **anchor points** (x, y)
with easing curves. The `bakeAnchors()` function converts these to
sample-level data. These baked samples are then wrapped in `Points` and fed into the same `ChartCanvas` rendering path as Teaching mode.

### Easing functions

All easing functions map normalised progress `t in [0, 1]` to output `[0, 1]`:

| Curve         | Formula                                                        |
|---------------|----------------------------------------------------------------|
| `LINEAR`      | `t`                                                            |
| `SINE`        | `-(cos(pi*t) - 1) / 2`                                        |
| `SINE_IN`     | `1 - cos(t * pi/2)`                                           |
| `SINE_OUT`    | `sin(t * pi/2)`                                                |
| `QUAD`        | `t<0.5: 2*t^2`, else `1 - (-2t+2)^2 / 2`                     |
| `QUAD_IN`     | `t^2`                                                          |
| `QUAD_OUT`    | `1 - (1-t)^2`                                                 |
| `CUBIC`       | `t<0.5: 4*t^3`, else `1 - (-2t+2)^3 / 2`                     |
| `CUBIC_IN`    | `t^3`                                                          |
| `CUBIC_OUT`   | `1 - (1-t)^3`                                                 |
| `QUART`       | `t<0.5: 8*t^4`, else `1 - (-2t+2)^4 / 2`                     |
| `QUART_IN`    | `t^4`                                                          |
| `QUART_OUT`   | `1 - (1-t)^4`                                                 |
| `CIRC`        | `t<0.5: (1-sqrt(1-(2t)^2))/2`, else `(sqrt(1-(-2t+2)^2)+1)/2`|
| `CIRC_IN`     | `1 - sqrt(1 - t^2)`                                           |
| `CIRC_OUT`    | `sqrt(1 - (t-1)^2)`                                           |

### Cubic Bezier segments

Three consecutive anchors with `CUBIC*` curves are consumed as one Bezier
segment with 4 control points (start, handle1, handle2, end):

```
y(t) = (1-t)^3 * P0 + 3*(1-t)^2*t * P1 + 3*(1-t)*t^2 * P2 + t^3 * P3
```

### Plain eased segments

For non-Bezier pairs (a -> b), the interpolation is:

```
k = ease(b.curve, t)
x = a.x + t * span
y = a.y + (b.y - a.y) * k
```

Samples are emitted at integer x positions between anchors.

---

## Stage 6 -- Pixel scaling

**Files:** `data/PixelScale.kt`, `data/EcgCalibration.kt`, `ui/display/Monitor.kt`

This is the core of the coordinate mapping. A single anchor value
`pxPerMm` (pixels per millimetre) derives every other scale.

### Calibration constants

```kotlin
// EcgCalibration.kt
gainMmPerMv    = 10.0    // standard ECG gain: 10 mm per millivolt
sampleRateHz   = 500.0   // default sample rate (Hz)
adcCountsPerMv = 256.0   // ADC units per millivolt
```

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

| Term            | Value (at mdpi, displayScale=0.4)              |
|-----------------|-------------------------------------------------|
| `160 / 25.4`   | 6.2992 dp/mm                                    |
| `density`       | 1.0                                              |
| `displayScale`  | 0.4                                              |
| **pxPerMm**     | **2.5197 px/mm**                                 |

### Derived PixelScale values

All formulas below use the constants from `EcgCalibration` and `MonitorModeModel`:

```
pxPerMv      = gainMmPerMv * pxPerMm * gainZoomY
             = 10 * pxPerMm * scale

pxPerSec     = paperSpeedMmPerSec * pxPerMm
             = 25 * pxPerMm

pxPerSample  = pxPerSec / sampleRateHz
             = (25 * pxPerMm) / 500

pxPerAdcCount = pxPerMv / adcCountsPerMv
              = (10 * pxPerMm * scale) / 256
```

### Per-part overrides

When a part has its own `AMax`/`AValue`/`duration`:

```
pxPerSampleFor(hz)     = pxPerSec / hz
                        (e.g., for 250 Hz: pxPerSec / 250)

pxPerAdcCountFor(spmv) = pxPerMv / samplesPerMv
                        (e.g., for AMax=200, AValue=2: pxPerMv / 100)
```

### Numeric example (mdpi, default settings)

| Value            | Formula                       | Result       |
|------------------|-------------------------------|--------------|
| pxPerMm          | 1.0 * 6.2992 * 0.4           | 2.52 px/mm   |
| pxPerMv          | 10 * 2.52 * 1.0              | 25.2 px/mV   |
| pxPerSec         | 25 * 2.52                    | 63.0 px/s    |
| pxPerSample      | 63.0 / 500                   | 0.126 px     |
| pxPerAdcCount    | 25.2 / 256                   | 0.098 px/ADC |
| smallGridStepPx  | 2.52                         | 2.52 px      |
| largeGridStepPx  | 2.52 * 5                     | 12.6 px      |

### Editor source-anchored mode

In editor mode, the grid is aligned to source coordinate units instead
of physical mm. One small grid square equals one source unit:

```
sourcePxPerMm = (AMax / AValue / 10) * (physicalPxPerMm / (160 / 25.4))
```

This keeps anchor dragging snapped to integer source units.

---

## Stage 7 -- Path rendering

### 7a. Waveform polyline

**File:** `ui/components/ChartCanvas.kt`

For each lead, the baseline-zeroed sample array is drawn as a polyline:

```
baselineY = canvas.height / 2

For each sample i:
    x[i] = i * stepX
    y[i] = baselineY - (sample[i] * stepY)
```

where:
- `stepX = pxPerSampleFor(sampleRateHz)` (or global `pxPerSample`)
- `stepY = pxPerAdcCountFor(samplesPerMv)` (or global `pxPerAdcCount`)

The Y axis is **inverted**: positive millivolts go UP on screen
(subtracted from baseline).

Path rendering uses:
- Stroke width: **2 dp**
- Stroke cap: `Round`
- Stroke join: `Round`

### 7b. Calibration pulse

**File:** `ui/components/CalibrationPulse.kt`

Standard ECG calibration: a rectangular pulse **1 mV tall, 200 ms wide**.

```
pulseHeight = pxPerAdcCountFor(samplesPerMv) * samplesPerMv   (per-part mode)
            = 1.0 * pxPerMv                                   (global mode)

pulseWidth  = 0.2 * pxPerSec

Shape:  baseline --> wing (4dp) --> up pulseHeight --> across pulseWidth --> down --> wing (4dp)
```

- Stroke width: **1.5 dp**
- Wing width: **4 dp**
- Start offset: **8 dp** from left edge

### 7c. ECG grid

**File:** `ui/display/Modifers.kt`

Standard ECG paper grid with 1 mm (small) and 5 mm (large) squares.

```
smallStep = pxPerMm        (= smallGridStepPx)
largeStep = pxPerMm * 5    (= largeGridStepPx)
```

| Parameter   | Small grid | Large grid |
|-------------|------------|------------|
| Stroke      | 0.5 dp     | 1.5 dp     |
| Interval    | 1 mm       | 5 mm       |

Grid color schemes:

| Scheme   | Background | Small lines | Large lines |
|----------|------------|-------------|-------------|
| Pink     | #FFF5F5    | #FDE4E4     | #F9BDBD     |
| BlueGray | #F0F4F7    | #DDE4E9     | #BCC6CF     |

Both vertical and horizontal lines are drawn independently in four
passes: V-small, V-large, H-small, H-large.

### 7d. Viewport zoom and pan

**File:** `ui/display/Monitor.kt`

The `Monitor` composable wraps the grid+leads in a `graphicsLayer`
transform that supports pinch-zoom and drag:

```
scaleX = scaleY = scale        (range: 1.0 to 5.0)
translationX = offset.x        (clamped to prevent overscroll)
translationY = offset.y

maxX = containerWidth * (scale - 1) / 2
maxY = containerHeight * (scale - 1) / 2
```

Layout schemes:

| Scheme      | Columns | Typical use     |
|-------------|---------|-----------------|
| OneColumn   | 1       | Rhythm strip    |
| TwoColumn   | 2       | 6+6 leads       |
| Grid        | 4       | 3x4 12-lead     |

### 7e. Anchor handles (Editor only)

**File:** `ui/components/AnchorCanvas.kt`

In editor mode, `AnchorHandleOverlay` sits directly over the `ChartCanvas` to provide interactive drag handles for anchor points. It delegates the actual polyline rendering to the shared `ChartCanvas` pipeline, managing only gesture detection and the drawing of circular interaction handles (`HANDLE_HIT_RADIUS_PX`).

---

## Complete transformation chain (single sample)

Tracing one raw integer sample from the ZIP file to a pixel coordinate:

```
Given:
  raw_sample = 1124           (integer from points: field)
  SAMPLE_BASELINE = 1024
  AMax = 200, AValue = 2
  samplesPerMv = 200/2 = 100
  gainMmPerMv = 10 mm/mV
  pxPerMm = 2.52 px/mm (example)
  gainZoomY = 1.0
  sample_index = 42

Step 1: Baseline zeroing
  zeroed = 1124 - 1024 = 100 source-units

Step 2: Source-units to millivolts (conceptual)
  mV = 100 / 100 = 1.0 mV

Step 3: Pixel scaling
  pxPerMv = 10 * 2.52 * 1.0 = 25.2 px/mV
  pxPerAdcCount = 25.2 / 100 = 0.252 px/source-unit
  stepY = 0.252

Step 4: X position
  pxPerSec = 25 * 2.52 = 63.0 px/s
  effectiveSampleRateHz = 500 Hz
  stepX = 63.0 / 500 = 0.126 px/sample
  x = 42 * 0.126 = 5.292 px

Step 5: Y position
  baselineY = canvas.height / 2  (e.g., 400 px)
  y = 400 - (100 * 0.252) = 400 - 25.2 = 374.8 px

Result: point at (5.29, 374.8) on the canvas
  -> 1.0 mV deflection above baseline
  -> at 84 ms from the start (42 samples at 500 Hz)
```

---

## Summary of all constants

| Constant            | Value     | Unit             | Source file              |
|---------------------|-----------|------------------|--------------------------|
| SAMPLE_BASELINE     | 1024      | ADC units        | EcgRepository.kt:167     |
| gainMmPerMv         | 10        | mm/mV            | EcgCalibration.kt:8      |
| sampleRateHz        | 500       | Hz               | EcgCalibration.kt:9      |
| adcCountsPerMv      | 256       | ADC/mV           | EcgCalibration.kt:10     |
| AMax (default)      | 200       | source-units     | EcgData.kt:125           |
| AValue (default)    | 2         | mV               | EcgData.kt:127           |
| paperSpeed          | 25        | mm/s             | MonitorModeModel.kt:22   |
| displayScale        | 0.4       | dimensionless    | MonitorModeModel.kt:24   |
| gainZoomY           | 1.0       | dimensionless    | MonitorModeModel.kt:23   |
| dp-to-mm factor     | 160/25.4  | dp/mm (= 6.2992) | Monitor.kt:73            |
| small grid          | 1         | mm               | PixelScale.kt:37         |
| large grid          | 5         | mm               | PixelScale.kt:38         |
| thin stroke         | 0.5       | dp               | Modifers.kt:42           |
| thick stroke        | 1.5       | dp               | Modifers.kt:43           |
| waveform stroke     | 2.0       | dp               | ChartCanvas.kt:72        |
| cal pulse stroke    | 1.5       | dp               | CalibrationPulse.kt:19   |
| cal pulse width     | 200       | ms               | CalibrationPulse.kt:35   |
| cal pulse height    | 1         | mV               | CalibrationPulse.kt:13   |
| zoom range          | 1.0 - 5.0| dimensionless    | Monitor.kt:105           |

---

## Summary of all formulas

| Formula                         | Definition                                           | File                 |
|---------------------------------|------------------------------------------------------|----------------------|
| `pxPerMm`                      | `density * (160/25.4) * displayScale`                | Monitor.kt:73        |
| `pxPerMv`                      | `gainMmPerMv * pxPerMm * gainZoomY`                  | PixelScale.kt:31     |
| `pxPerSec`                     | `paperSpeedMmPerSec * pxPerMm`                       | PixelScale.kt:32     |
| `pxPerSample`                  | `pxPerSec / sampleRateHz`                            | PixelScale.kt:34     |
| `pxPerAdcCount`                | `pxPerMv / adcCountsPerMv`                           | PixelScale.kt:36     |
| `pxPerSampleFor(hz)`           | `pxPerSec / hz`                                      | PixelScale.kt:44     |
| `pxPerAdcCountFor(spmv)`       | `pxPerMv / spmv`                                     | PixelScale.kt:52     |
| `samplesPerMv`                 | `AMax / AValue`                                      | EcgData.kt:139-140   |
| `effectiveSampleRateHz`        | `samples.size / (duration / 1000)`                   | EcgData.kt:130-133   |
| `baselineZeroed`               | `sample - 1024`                                      | EcgRepository.kt:98  |
| `viewerSample`                 | `(sample - 1024) * amplitude`                        | EcgRepository.kt:78  |
| `x[i]` (screen)                | `i * stepX`                                          | ChartCanvas.kt:62    |
| `y[i]` (screen)                | `baselineY - (sample[i] * stepY)`                    | ChartCanvas.kt:63    |
| `sourcePxPerMm` (editor)       | `(AMax/AValue/10) * (physPxPerMm / (160/25.4))`      | PixelScale.kt:75     |
| `III`                          | `II - I`                                             | DerivedLeads.kt:34   |
| `aVR`                          | `-(I + II) / 2`                                      | DerivedLeads.kt:35   |
| `aVL`                          | `(2*I - II) / 2`                                     | DerivedLeads.kt:36   |
| `aVF`                          | `(2*II - I) / 2`                                     | DerivedLeads.kt:37   |
| V-lead projection              | `alpha*V2[i] + beta*V6[i]`                           | DerivedLeads.kt:80   |
| ease(SINE, t)                  | `-(cos(pi*t) - 1) / 2`                               | CurveInterpolation.kt:19 |
| bezierY(P0,P1,P2,P3,t)        | `(1-t)^3*P0 + 3(1-t)^2*t*P1 + 3(1-t)*t^2*P2 + t^3*P3` | CurveInterpolation.kt:44 |
| eased interpolation            | `a.y + (b.y - a.y) * ease(curve, t)`                 | CurveInterpolation.kt:97 |
