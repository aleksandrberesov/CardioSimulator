# Plan — ЭОС window: refined content + live axis computation + trace highlight (Android parity)

**Status:** active
**Owner:** a.beresov
**Started:** 2026-07-05
**Related:** Windows→Android UI/behaviour parity. Source of truth = Windows port
`CardioSimulatorWin/src/CardioSimulator.App/Controls/EosWindow.cs`,
`.../Core/Domain/EosAxis.cs`, `.../App/Analysis/EosAnalyzer.cs`,
`.../App/Rendering/EcgRenderer.cs` (DrawEosHighlight), plus the `monitor_eos_*` strings in
`.../App/Localization/AppStrings.cs`. Supersedes the EOS slice of
`docs/plans/sync/2026-06-android-monitor-panel-parity.md` (which left the overlay a scaffold).

## Goal

The Windows ЭОС ("electrical axis") window was reworked from a placeholder into a **live teaching
tool**, and Android must follow. Four things changed on Windows and need porting:

1. **Refined content** — the window now shows a numbered 7-step determination method, a coordinate
   diagram of the I/aVF construction, and the six axis-deviation variants with their angle ranges
   (replacing the old "Vector 1 / Vector 2 / Result:" placeholder).
2. **Live axis computation** — on open it measures q/R/S in leads I and aVF (BioSPPy QRS detection),
   computes vectors a = R−(q+S) and b, the α angle, and classifies it; the diagram is drawn from the
   real vectors and the matching variant is highlighted.
3. **On-trace highlight** — the QRS complexes of I and aVF that the axis is measured from are shaded
   on the monitor trace while the window is open.
4. **Live update** — while the window is open, changing the selected pathology re-runs the whole
   computation so the readout, diagram, highlighted variant, and trace shading track the new rhythm.

**Why now:** the Windows changes are done, built, and unit-tested; Android's EOS overlay is currently
an empty stub, so the two ports have visibly diverged on a customer-facing feature.

## Current state (Android)

- **Overlay is a STUB.** `ui/components/MonitorOverlays.kt:32`:
  ```kotlin
  @Composable
  fun EosOverlay(onClose: () -> Unit, modifier: Modifier = Modifier) {
      // ... existing EosOverlay implementation   ← renders nothing (HEAD is the same)
  }
  ```
  The sibling `TipsOverlay` (same file, from line 36) is a good structural template for the window
  chrome (translucent `WindowsBlue` box, title row + close icon, scrollable `Column`).
- **State + wiring already exist:**
  - `domain/MonitorModeModel.kt:78` — `val showEos: Boolean = false` (and `showImpulseLabels` at :71).
  - `ui/viewmodels/MonitorViewModel.kt:256` — `fun setShowEos(show: Boolean)`.
  - Toggle button: `ui/panels/MonitorControlPanel.kt:403-406` (`stringResource(R.string.monitor_eos)`,
    `onClick = { viewModel.setShowEos(!monitorMode.showEos) }`, `isActive = monitorMode.showEos`).
  - Render site: `ui/screens/TeachingScreen.kt:467-472` renders `EosOverlay(onClose = { … setShowEos(false) })`
    aligned `TopEnd`; also force-closed at `:209` (`setShowEos(false)`).
- **Waveforms are already in scope at the render site.** `TeachingScreen.kt:191`
  `val waveforms by rhythmViewModel.waveforms.collectAsState()` (a `Map<Lead, Points>`,
  baseline-zeroed; `RhythmViewModel.kt:68`), and `mode.calibration` at `:198`. `EosOverlay` at `:467`
  and the `LeadView(...)` calls at `:423` are in this same composable scope.
- **BioSPPy QRS pipeline exists in Kotlin** (same as Windows):
  - `signals/biosppy/QrsSegmenters.kt:54` `hamiltonSegmenter(signal: DoubleArray, fs): IntArray`,
    `:31` `correctRPeaks(signal, rpeaks, fs, tolSec=0.05)`.
  - `signals/biosppy/Landmarks.kt:175` `getLandmarks(signal, rpeaks, fs): List<EcgLandmarks>`;
    `EcgLandmarks` (`:5`) = `rPeak, qPeak, qrsStart, sPeak, qrsEnd, …`.
  - Reference usage: `ui/viewmodels/ConstructorViewModel.kt:233 autoDetectLandmarks(...)` calls all
    three (`:240`/`:241`/`:243`) and reads `lm.rPeak/qPeak/sPeak/qrsStart/qrsEnd`.
- **Calibration is identical to Windows** — `data/EcgCalibration.kt:7`:
  `gainMmPerMv=10f, sampleRateHz=500f, adcCountsPerMv=1024f`.
- **`Points`** = `data/Points.kt` `data class Points(val values: List<Float>)`.
- **Renderer / highlight injection point** — `LeadView` is aliased from `ui/display/Lead.kt`
  (`import … ui.display.Lead as LeadView`, e.g. `TeachingScreen.kt:83`). `fun Lead(...)` at
  `Lead.kt:59` already takes `significantPoints`, `showImpulseLabels`, `calibration`, `lead`. Inside
  its **Trace `Box`** (`Lead.kt:147`) it draws `PreviewPane` (the trace) then, gated on
  `showImpulseLabels && significantPoints.isNotEmpty()`, `SignificantPointOverlay(...)` at `:161-167`.
  That overlay (`ui/components/SignificantPointOverlay.kt`) is the pattern for a new
  sample-indexed band overlay.
- **Strings** — `res/values/strings.xml:146-149` still holds the OLD keys:
  `monitor_eos_window_title` (keep), `monitor_eos_vector_format`, `monitor_eos_note`,
  `monitor_eos_result` (remove). Five locales exist: `values/`, `values-ru/`, `values-zh/`,
  `values-es/`, `values-hi/`.
- **JVM tests** live under `app/src/test/java/com/example/cardiosimulator/{domain,signals,…}`.

## Key divergence from Windows (read before implementing)

Windows drives the highlight through **model state** (`MonitorModeModel.EosHighlightSpans`,
`MonitorViewModel.SetEosHighlight`) and keeps the window in sync with a **manual** `EosWindow.Update()`
call fired from a `RhythmViewModel.PropertyChanged` subscription in `MainScreen`, because its EOS panel
is an imperative `Popup` and its renderer is decoupled.

**Android is reactive Compose, so both of those are unnecessary — do NOT port them literally.** Compute
the axis once in `TeachingScreen` with `remember(waveforms, mode.calibration)`; recomposition on a
rhythm change re-runs it automatically. Pass the result into `EosOverlay` and the spans into `Lead(...)`.
This is a deliberate, documented divergence: **no `setEosHighlight`, no `eosHighlightSpans` model field,
no property-change listener.** (If a future refactor wants the spans on the model for symmetry, that's a
separate call — keep this PR minimal.)

## Non-goals

- No change to pQRSt (`showImpulseLabels`/`SignificantPointOverlay`), Tips, electrodes, artifacts,
  filters, or SQI.
- Don't modify the QRS-detection algorithms in `signals/biosppy/*` — only *call* them.
- Don't "correct" the customer's (partly unconventional / overlapping) angle bands — port them verbatim
  (see Phase 1).
- Don't try to keep the highlight aligned with the trace while it scrolls — use the same static
  sample→x mapping as `SignificantPointOverlay` (aligned when paused; matches Windows and Android's
  existing overlay convention).
- No 3D / 12-lead-embed / Tips-placement work (tracked elsewhere).

## Plan

### Phase 1 — Domain math + unit test (`domain/EosAxis.kt`)
Port `EosAxis.cs` 1:1:
- `enum class EosAxisClass { Normal, Horizontal, Vertical, LeftDeviation, RightDeviation, ExtremeDeviation }`
- `data class EosLeadMeasure(val qMm: Double, val rMm: Double, val sMm: Double) { val netMm get() = rMm - (qMm + sMm) }`
- `data class EosResult(val leadI: EosLeadMeasure, val leadAvf: EosLeadMeasure, val angleDeg: Double, val axisClass: EosAxisClass)`
- `data class EcgSpan(val startSample: Int, val endSample: Int)`
- `object EosAxis`:
  - `fun angleDegrees(netI: Double, netAvf: Double) = Math.toDegrees(atan2(netAvf, netI))`
  - `fun classify(angleDeg): EosAxisClass` — normalize to (-180,180], then:
    `[0,29]→Horizontal, [30,69]→Normal, [70,90]→Vertical, (90,180]→RightDeviation,
    (-90,0)→LeftDeviation, [-180,-90]→ExtremeDeviation`.
  - `fun from(leadI, leadAvf): EosResult`.
- Test `app/src/test/java/com/example/cardiosimulator/domain/EosAxisTests.kt` — mirror
  `EosAxisTests.cs`: `netMm`, the five `angleDegrees` cases (1,0→0; 0,1→90; 1,1→45; -1,0→180;
  0,-1→-90), the band table, and the worked example (I net=2, aVF net=6 → α≈71.6°, **Vertical** — note
  the customer slide loosely calls it "Normal"; classify mathematically).

### Phase 2 — Analyzer (`signals/EosAnalyzer.kt` or `domain/`)
Port `EosAnalyzer.cs`:
- `data class EosAnalysis(val result: EosResult, val highlightSpans: Map<Lead, List<EcgSpan>>)`
- `object EosAnalyzer { fun analyze(waveforms: Map<Lead, Points>?, calibration: EcgCalibration): EosAnalysis? }`
  - `measure(waveforms, Lead.I, cal)` and `Lead.aVF`; if either null → return null.
  - Per lead: build `DoubleArray` from `points.values` (already baseline-zeroed);
    `hamiltonSegmenter` → `correctRPeaks` → `getLandmarks` (wrap in try/catch → null on degenerate
    signal; `rpeaks.isEmpty()` → null). `toMm = gainMmPerMv / adcCountsPerMv`. Average across complexes:
    `rMm = mean(max(0, sig[rPeak]*toMm))`, `qMm = mean(max(0, -sig[qPeak]*toMm))`,
    `sMm = mean(max(0, -sig[sPeak]*toMm))` (skip `-1`/out-of-range indices; require ≥1 R). Collect a
    span `EcgSpan(qrsStart, qrsEnd)` when both valid and `qrsEnd > qrsStart`.
  - `EosAxis.from(i, f)` + `mapOf(Lead.I to iSpans, Lead.aVF to fSpans)`.

### Phase 3 — Strings (all 5 locales)
In `res/values*/strings.xml`, **remove** `monitor_eos_vector_format`, `monitor_eos_note`,
`monitor_eos_result` (keep `monitor_eos_window_title`) and **add** the keys below.
**GOTCHAS:** Windows `{0}`→Android `%1$s` (renumber +1); the readout args are pre-formatted strings so
use `%s` not `%d`/`%f`; escape any apostrophe as `\'` (none in the current text, but ES/HI edits must
watch for it); `&`→`&amp;`. Pull ZH/ES/HI from `AppStrings.cs` (the `Zh`/`Es`/`Hi` dictionaries) and
convert placeholders the same way. English + Russian in full:

```xml
<!-- values/strings.xml (EN) -->
<string name="monitor_eos_intro">How to determine the electrical axis (EOS):</string>
<string name="monitor_eos_step_1">Measure the q, R and S waves in lead I. Calculate R - (q + S) = the length of vector a in mm.</string>
<string name="monitor_eos_step_2">Measure the q, R and S waves in lead aVF. Calculate R - (q + S) = the length of vector b in mm.</string>
<string name="monitor_eos_step_3">Find the “I” axis on the coordinate grid and mark off the value of the first vector a on it (red).</string>
<string name="monitor_eos_step_4">Find the “aVF” axis on the coordinate grid and mark off the value of the second vector b on it (green).</string>
<string name="monitor_eos_step_5">Drop perpendiculars from the axes so that a rectangle (in this case) or a parallelogram is formed.</string>
<string name="monitor_eos_step_6">Draw the resultant vector (green) from the point where all the axes cross to the intersection of the perpendiculars.</string>
<string name="monitor_eos_step_7">Measure the angle between the zero axis and the resultant (blue) vector — this is the alpha angle, i.e. the electrical axis of the heart.</string>
<string name="monitor_eos_variants_header">Electrical axis variants:</string>
<string name="monitor_eos_variant_normal">Normal: from 30° to +69°</string>
<string name="monitor_eos_variant_horizontal">Horizontal: from +0° to +29°</string>
<string name="monitor_eos_variant_vertical">Vertical: from +70° to +90°</string>
<string name="monitor_eos_variant_left">Left axis deviation: from 0° to -90°</string>
<string name="monitor_eos_variant_right">Right axis deviation: from +91° to 180°</string>
<string name="monitor_eos_variant_extreme">Extreme deviation: from 180° to -90°</string>
<string name="monitor_eos_measured_header">For the current ECG:</string>
<string name="monitor_eos_lead_format">%1$s: q %2$s · R %3$s · S %4$s → %5$s = %6$s mm</string>
<string name="monitor_eos_angle_format">α = %1$s° — %2$s</string>
<string name="monitor_eos_no_data">Load a rhythm with clear QRS complexes in leads I and aVF to compute the axis.</string>
```
```xml
<!-- values-ru/strings.xml (RU) -->
<string name="monitor_eos_intro">Способ определения ЭОС:</string>
<string name="monitor_eos_step_1">Измерьте величину зубцов q, R и S в отведении I. Вычислите: R - (q + S) = длина вектора a в мм.</string>
<string name="monitor_eos_step_2">Измерьте величину зубцов q, R и S в отведении aVF. Вычислите: R - (q + S) = длина вектора b в мм.</string>
<string name="monitor_eos_step_3">Найдите на осях координат ось «I» и отложите на ней величину первого вектора a (красный цвет).</string>
<string name="monitor_eos_step_4">Найдите на осях координат ось «aVF» и отложите на ней величину второго вектора b (зелёный цвет).</string>
<string name="monitor_eos_step_5">Опустите перпендикуляры с осей так, чтобы получился прямоугольник (в данном случае) или параллелограмм.</string>
<string name="monitor_eos_step_6">Проведите результирующий вектор (зелёный цвет) от точки пересечения всех осей до пересечения перпендикуляров.</string>
<string name="monitor_eos_step_7">Измерьте угол между нулевой осью и результирующим (синим) вектором — это угол альфа, или электрическая ось сердца.</string>
<string name="monitor_eos_variants_header">Варианты электрической оси:</string>
<string name="monitor_eos_variant_normal">Нормальная: от 30° до +69°</string>
<string name="monitor_eos_variant_horizontal">Горизонтальная: от +0° до +29°</string>
<string name="monitor_eos_variant_vertical">Вертикальная: от +70° до +90°</string>
<string name="monitor_eos_variant_left">Отклонена влево: от 0° до -90°</string>
<string name="monitor_eos_variant_right">Отклонена вправо: от +91° до 180°</string>
<string name="monitor_eos_variant_extreme">Экстремальное отклонение: от 180° до -90°</string>
<string name="monitor_eos_measured_header">Для текущей ЭКГ:</string>
<string name="monitor_eos_lead_format">%1$s: q %2$s · R %3$s · S %4$s → %5$s = %6$s мм</string>
<string name="monitor_eos_angle_format">α = %1$s° — %2$s</string>
<string name="monitor_eos_no_data">Загрузите ритм с чёткими комплексами QRS в отведениях I и aVF, чтобы вычислить ось.</string>
```
(ZH/ES/HI: same keys, translate from `AppStrings.cs`. ZH uses a full-width colon `：` in the variant
strings — keep it; the name/range split below handles both `:` and `：`.)

### Phase 4 — Implement `EosOverlay` (mirror `EosWindow.BuildPanel`)
Change the signature to `fun EosOverlay(result: EosResult?, onClose: () -> Unit, modifier: Modifier = …)`
and build the scrollable content (reuse the `TipsOverlay` chrome: `WindowsBlue.copy(alpha=0.8f)` box,
title + close icon, `verticalScroll`):
1. Title `monitor_eos_window_title`; intro `monitor_eos_intro`.
2. Steps 1–7: a `Row` per step with the number in a fixed-width gutter + wrapped
   `stringResource(R.string.monitor_eos_step_1 …_7)`.
3. **Diagram** — a `Canvas(Modifier.size(190.dp))` (import already present) mirroring `EosWindow.Diagram`:
   faint hexaxial circle + 0/30/…/150° spokes, emphasized I (horizontal) & aVF (vertical) axes,
   `a = result?.leadI?.netMm ?: 2.0`, `b = result?.leadAvf?.netMm ?: 6.0`, `unit = r*0.85 / max(|a|,|b|,1e-3)`,
   red vector a along I, green vector b along aVF (sign → left/up), dashed rectangle
   (`PathEffect.dashPathEffect`), blue resultant to the corner; labels `I`/`aVF`/`a`/`b`/`α` via
   `drawContext.canvas.nativeCanvas.drawText`. Handles all four quadrants.
4. **Measured readout** (translucent card): if `result == null` show `monitor_eos_no_data`; else
   `monitor_eos_measured_header`, two `monitor_eos_lead_format` lines
   (`"I", q, R, S, "a", netI` and `"aVF", …, "b", netAvf` — format mm to 1 decimal), and
   `monitor_eos_angle_format(angle "%.0f", variantName)`.
5. **Variants list** `monitor_eos_variants_header` + the six `monitor_eos_variant_*`. The row whose
   class == `result?.axisClass` is emphasized (bold + a translucent pill). Bold just the name by
   splitting on the first `:` **or** `：` (handles ZH); `variantName(class)` = the substring before that
   separator, reused for the angle line.
Wire the call site — `TeachingScreen.kt:467`:
```kotlin
val eos = remember(waveforms, mode.calibration) { EosAnalyzer.analyze(waveforms, mode.calibration) }
if (mode.showEos) {
    EosOverlay(result = eos?.result, onClose = { monitorViewModel.setShowEos(false) },
               modifier = Modifier.align(Alignment.TopEnd))
}
```

### Phase 5 — On-trace QRS highlight
- Add an overlay composable `EosHighlightOverlay(points: Points, spans: List<EcgSpan>, modifier)` in
  `ui/components/` (next to `SignificantPointOverlay`), drawing translucent blue bands
  (`Color(0x331E88E5)` fill + `Color(0x991E88E5)` edges) over each span using the **same sample→x
  mapping** `SignificantPointOverlay` uses (clamp indices to `points.values.size`).
- Extend `Lead(...)` (`Lead.kt:59`) with `eosSpans: List<EcgSpan> = emptyList()`; inside the Trace
  `Box` (after `PreviewPane`, `Lead.kt:159`, so it sits under the markers) add:
  ```kotlin
  if (eosSpans.isNotEmpty()) {
      EosHighlightOverlay(points = processedPoints, spans = eosSpans, modifier = Modifier.fillMaxSize())
  }
  ```
- At the `LeadView(...)` call (`TeachingScreen.kt:423`) pass:
  ```kotlin
  eosSpans = if (mode.showEos && !mode.isCompareMode) eos?.highlightSpans?.get(lead).orEmpty() else emptyList(),
  ```
  Closing the window (`showEos=false`) drops the spans automatically — no explicit clear needed.

### Phase 6 — Polish / verify
Lint the strings (all 5 locales, no missing keys), confirm no leftover references to the removed
`monitor_eos_vector_format/_note/_result`, run the app.

## Risks & open questions

- **Placeholder gotcha (highest risk):** `{0}`→`%1$s` renumbering; `monitor_eos_lead_format` has SIX
  positional args — get the order right; use `%s` (args are pre-formatted). Escape apostrophes in
  translated strings.
- **`aVF` is a derived lead** — `RhythmViewModel.waveforms` includes it only if the manifest's lead
  order does. If absent, `analyze` returns null → the overlay shows the method + `monitor_eos_no_data`
  (correct graceful fallback, same as Windows).
- **Recompute cost on recompose:** wrap the analyze call in `remember(waveforms, mode.calibration)` so
  detection runs only when the rhythm actually changes — not every frame. Detection is O(n) and already
  runs for SQI in a `LaunchedEffect` (`TeachingScreen.kt:452`), so cost is acceptable.
- **Customer example classifies as Vertical, not "Normal"** — expected; the unit test pins it.
- **Compose Canvas text** needs `nativeCanvas.drawText` (no direct `drawText(String)` in `DrawScope`
  before Compose 1.6's `TextMeasurer`); use whichever the codebase already uses in `ChartCanvas.kt`.

## Verification

- `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` (EosAxisTests green).
- Manual, Teaching mode, "All rhythms":
  - Load a rhythm with clear QRS in I/aVF → tap **EOS** → window shows measured `I:`/`aVF:` q·R·S,
    `α = N° — <band>`, the diagram drawn from the real vectors, and the matching variant highlighted;
    the QRS of I and aVF are shaded on the trace.
  - **Switch pathology with the window open** → readout, diagram, highlighted variant, and trace
    shading all update to the new rhythm (Compose recomposition; no manual refresh).
  - Load a rhythm without detectable QRS / without aVF → window shows the method + "no data" note, no
    shading.
  - Close the window (tap EOS again / leave the monitor) → shading clears.
- Language switch EN/RU/ZH/ES/HI → all EOS text localized; variant name bolded correctly (incl. ZH `：`).

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | EOS: axis math + analyzer + unit test | 1–2 | `EosAxis.kt`, `EosAnalyzer.kt`, `EosAxisTests.kt`; no UI |
| 2 | EOS: refined window strings (5 locales) | 3 | remove 3 old keys, add the new set; placeholder conversion |
| 3 | EOS: implement overlay + live compute | 4 | `EosOverlay` body + `TeachingScreen` `remember`/call site |
| 4 | EOS: on-trace QRS highlight | 5 | `EosHighlightOverlay` + `Lead.kt` param + call-site wiring |

(2–4 can also ship as one PR if preferred; keep Phase 1 separate so the math lands with its test.)

---

## Outcome

- **Result:** shipped
- **PRs:** N/A (applied directly)
- **Deviations from plan:** `TipsOverlay` template was not available as it was retired; reused `SignificantPointOverlay` patterns instead. Consistent angle normalization used (180/-180 treated as RightDeviation, 181 as ExtremeDeviation).
