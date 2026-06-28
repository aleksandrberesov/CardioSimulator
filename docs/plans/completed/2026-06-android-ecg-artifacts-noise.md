# Port multi-select ECG noise artifacts (with real noise on the trace) to Android

**Status:** completed
**Owner:** AI Assistant
**Started:** 2026-06-28
**Finished:** 2026-06-28

---

## Goal

Make the Teaching-monitor **Артефакты** menu do two things it doesn't do today on Android:

1. **Allow several artifacts at once** (multi-select), instead of a single radio choice.
2. **Actually perturb the displayed trace** — each selected artifact (muscle/EMG, mains, baseline
   wander, electrode contact, motion) generates real additive noise via the existing BioSPPy Kotlin
   port and is summed onto the live signal **before** the cleanup filter, so a student can e.g. overlay
   mains hum and watch the low-pass filter remove it.

Why now: the Windows port shipped this (2026-06-27); the Android artifact dropdown is a single-select
**scaffold that has no effect on the trace** (the previous parity plan explicitly deferred "real
artifact noise"). This plan closes that gap.

## Current state

Android already has the scaffolding and the DSP it needs — this is mostly wiring + one new file.

- **Domain** — `domain/MonitorModeModel.kt:22-29` defines `enum class EcgArtifact { None, Muscle, Mains,
  Baseline, Contact, Motion }`, and `MonitorModeModel` carries a **single** `artifact: EcgArtifact =
  EcgArtifact.None` (`:69`). The filter analog (`filterType: EcgFilterType`, `:70`) is fully working —
  use it as the template.
- **View-model** — `ui/viewmodels/MonitorViewModel.kt:210-216` has `setArtifact(EcgArtifact)` and
  `setFilterType(...)`. Neither persists to `DataSourcePrefs` today (unlike the Windows port, which
  persists `monitor_artifacts`). Keep Android consistent with its own filter (i.e. no persistence)
  unless we decide otherwise — see open questions.
- **Panel UI** — `ui/panels/MonitorControlPanel.kt:199-233` renders the Артефакты `DropdownMenu`:
  single-select, each item's `onClick` calls `setArtifact(...)` **and** closes the menu
  (`artifactsMenuExpanded = false`). The Filters dropdown right below (`:235-266`) is the same pattern.
- **Per-lead processing (the injection point)** — `ui/display/Lead.kt:48-69`: a
  `remember(points, filterType)` block converts `points` → `DoubleArray`, applies the Butterworth
  filter through `signals/biosppy/Filter.filterSignal(...)`, and wraps back into `Points`. **This is the
  Android analog of the Windows `MonitorView.UpdateWaveforms`** and is exactly where artifact noise must
  be added (before the filter `when`).
- **Threading** — `ui/display/Monitor.kt` calls `Lead(... filterType = mode.filterType ...)` at three
  layout sites (`:253`, `:292`, `:331` — one-column / two-column / grid). Compare-mode panes thread
  `filterType` in the screens too (e.g. `TeachingScreen.kt:445`). `artifacts` must follow the same path.
- **BioSPPy DSP** — `signals/biosppy/Filter.kt` already provides `butterworth(order, Wn, band)`,
  `filtfilt(b, a, x)`, and `filterSignal(...)`. There is **no** artifact/noise generator yet (Windows
  added `BioSPPy.Net/Synthesizers/Ecg/EcgArtifactGenerator.cs`).
- **Strings** — `monitor_artifacts` + `monitor_artifact_{none,muscle,mains,baseline,contact,motion}`
  already exist in all four locales (`res/values{,-ru,-zh,-es}/strings.xml`). **No new strings required.**

### Windows reference files (read these first)

| Concern | Windows file |
|---|---|
| Noise generator (port this) | `src/BioSPPy.Net/Synthesizers/Ecg/EcgArtifactGenerator.cs` |
| Flags enum + model field | `src/CardioSimulator.Core/Domain/MonitorMode.cs` (`[Flags] EcgArtifacts`, `MonitorModeModel.Artifacts`) |
| Setter | `src/CardioSimulator.App/ViewModels/MonitorViewModel.cs` (`SetArtifacts`) |
| Apply noise → filter | `src/CardioSimulator.App/Controls/MonitorView.cs` (`UpdateWaveforms`, `AddArtifacts`, `ActiveKinds`, `PeakToPeak`) |
| Multi-select menu | `src/CardioSimulator.App/Controls/MonitorControlPanel.xaml.cs` (`OnArtifactsClick`, `AddArtifactCheck`) |
| Host wiring | `src/CardioSimulator.App/Screens/MainScreen.xaml.cs` (`ArtifactSelected += … SetArtifacts`) |
| Unit tests | `tests/CardioSimulator.Core.Tests/BioSPPyNetTests.cs` (artifact tests) |

## Non-goals

- Re-tuning the existing Filters feature, or matching Windows filter cutoffs/orders (Android uses order
  4 @ 25/3 Hz; Windows uses order 2 @ 40/0.5 Hz). **Leave the Android filter exactly as is.**
- Per-artifact intensity sliders, 60 Hz mains option, or animating the noise over time (Windows adds it
  statically to the looped buffer; match that).
- Touching Electrodes/3D/EOS/Tips windows.

## Plan

### Phase 1 — Domain + view-model: single artifact → a set

Kotlin has no `[Flags]` enum; the idiomatic equivalent of the Windows `[Flags] EcgArtifacts` is a
`Set<EcgArtifact>`.

- In `MonitorModeModel.kt`, replace `val artifact: EcgArtifact = EcgArtifact.None` with
  `val artifacts: Set<EcgArtifact> = emptySet()`. (Keep the `EcgArtifact` enum as-is; `None` becomes
  unused as a member but harmless — or drop it and represent "none" as the empty set. Recommend keeping
  it so the existing `R.string.monitor_artifact_none` label still maps to a clear-all action.)
- In `MonitorViewModel.kt`, replace `setArtifact(...)` with:
  ```kotlin
  fun setArtifacts(artifacts: Set<EcgArtifact>) {
      _monitorMode.update { it.copy(artifacts = artifacts) }
  }
  ```
- Fix the two other references the compiler will flag (search `\.artifact\b` and `setArtifact`).

### Phase 2 — BioSPPy: `EcgArtifactGenerator.kt`

Port `EcgArtifactGenerator.cs` to `signals/biosppy/EcgArtifactGenerator.kt`. Reuse the existing
`EcgArtifact` enum for the kind (skip `None`) instead of adding a separate `EcgArtifactKind`.

```kotlin
package com.example.cardiosimulator.signals.biosppy

object EcgArtifactGenerator {
    // Adds the given artifact's noise to a copy of `signal`. Noise scales to the signal's own
    // peak-to-peak; `intensity` (1.0 default) scales it; `seed` makes the stochastic kinds reproducible.
    fun apply(signal: DoubleArray, kind: EcgArtifact, samplingRate: Double,
              intensity: Double = 1.0, seed: Int = 0): DoubleArray

    // Length-n additive noise scaled against `referenceAmplitude` (the host peak-to-peak).
    fun generate(n: Int, kind: EcgArtifact, samplingRate: Double,
                 referenceAmplitude: Double, intensity: Double, seed: Int): DoubleArray
}
```

Port each kind faithfully (constants/algorithms are in the C# file — copy them):
- **Mains** — `0.06·pp·intensity · (sin(2π·50·t) + 0.2·sin(2π·150·t))`.
- **Muscle (EMG)** — Box-Muller Gaussian white noise, high-pass-filtered to >25 Hz via
  `Filter.butterworth(2, doubleArrayOf(25.0/nyq), "highpass")` + `Filter.filtfilt(...)`, then scaled to
  target std `0.05·pp·intensity`. (Fall back to raw white noise if `n` is too small for `filtfilt`'s
  padlen — wrap in try/catch like `Lead.kt` does.)
- **Baseline** — sum of slow sines (0.15/0.3/0.5 Hz, weights 1/0.5/0.3, random phases), amp `0.22·pp`.
- **Motion** — ~0.35 Gaussian bumps/sec at random positions, width 0.2–0.6 s, amp up to `0.5·pp`.
- **Contact** — ~0.8 "pops"/sec, exponential decay τ≈40 ms, amp up to `0.6·pp`.

Helpers to port: `peakToPeak`, `scaleToStd`, Box-Muller `whiteNoise`. Use `kotlin.random.Random(seed)`
(seeded) for determinism. Mirror the C# numeric constants exactly so the two ports look identical.

### Phase 3 — Apply noise in `Lead.kt`, before the filter

- Add a param `artifacts: Set<EcgArtifact> = emptySet()` to `fun Lead(...)`.
- Rework the `remember` block (note the **expanded key set** so it recomputes when artifacts/calibration
  change, and **artifacts are added before the filter `when`**):
  ```kotlin
  val processedPoints = remember(points, artifacts, filterType, calibration) {
      val active = EcgArtifact.entries.filter { it != EcgArtifact.None && it in artifacts } // stable order
      if ((active.isEmpty() && filterType == EcgFilterType.NONE) || points.values.size < 50) {
          points
      } else try {
          val signal = points.values.map { it.toDouble() }.toDoubleArray()
          val samplingRate = calibration.sampleRateHz.toDouble()
          if (active.isNotEmpty()) {
              val refPp = EcgArtifactGenerator.peakToPeak(signal)   // or inline
              var seed = (title.hashCode() * 31)                    // per-lead, stable across recomposition
              for (kind in active) {
                  val noise = EcgArtifactGenerator.generate(signal.size, kind, samplingRate, refPp, 1.0, seed++)
                  for (i in signal.indices) signal[i] += noise[i]
              }
          }
          val filtered = when (filterType) { /* unchanged */ else -> signal }
          Points(filtered.map { it.toFloat() })
      } catch (e: Exception) { points }
  }
  ```
  Seeding per lead (and incrementing per kind) keeps the noise stable across Compose recompositions —
  important, since the monitor recomposes constantly while scrolling.
- In `Monitor.kt`, pass `artifacts = mode.artifacts` to all three `Lead(...)` call sites (`:253/:292/:331`).
- Thread `artifacts` through any compare-pane path that already passes `filterType` (search the screens
  for `filterType = mode.filterType`, e.g. `TeachingScreen.kt:445`, and add `artifacts = mode.artifacts`
  beside it).

### Phase 4 — Multi-select dropdown in `MonitorControlPanel.kt`

Compose makes this easy: a `DropdownMenu` does **not** auto-dismiss on item click — it only closes when
you flip `expanded`. So the fix is to **stop closing the menu** on each toggle (the Windows port had to
swap `MenuFlyout` for a checkbox `Flyout` for the same reason).

- Replace the artifacts `DropdownMenu` body (`:208-232`) with:
  - A **"None"** `DropdownMenuItem` → `viewModel.setArtifacts(emptySet())` (closing here is fine, or keep
    open — match the Windows "None clears without closing" behaviour by leaving `expanded` untouched).
  - One `DropdownMenuItem` per `EcgArtifact.entries.filter { it != EcgArtifact.None }`, each with a
    `trailingIcon`/`leadingIcon` showing a check when `artifact in monitorMode.artifacts`, and:
    ```kotlin
    onClick = {
        val next = if (artifact in monitorMode.artifacts) monitorMode.artifacts - artifact
                   else monitorMode.artifacts + artifact
        viewModel.setArtifacts(next)
        // deliberately NOT setting artifactsMenuExpanded = false → stays open for multi-select
    }
    ```
- Reflect state on the `Tab`: `isActive = monitorMode.artifacts.isNotEmpty()`, and optionally a count in
  the text (Windows shows `Артефакты (2)`); reuse `R.string.monitor_artifacts` — no new string needed.

### Phase 5 — (Optional) persistence + tests

- **Persistence:** the Android Filter doesn't persist, so leaving artifacts non-persistent keeps the two
  consistent. If we do want parity with Windows' `monitor_artifacts` pref, add a `DataSourcePrefs`
  key storing the set as a comma-joined name list and load it in `MonitorViewModel.init`.
- **Tests:** if there's a JVM unit-test source set for `signals/biosppy`, port the three Windows artifact
  tests from `BioSPPyNetTests.cs` (perturbs-signal, deterministic-per-seed, intensity-scales-amplitude).

## Risks & open questions

- **Performance:** the muscle artifact runs a `filtfilt` per lead per recomposition (up to 12 leads).
  `Lead.kt` already filtfilts per lead behind `remember`, so this is within the existing budget — but
  verify there's no jank when several artifacts are on with 12 leads. If needed, hoist processing out of
  the composable into a derived flow.
- **Seed source:** `title.hashCode()` is convenient but compare-mode panes may share titles. If two
  panes look suspiciously identical, seed off the pane index / lead enum ordinal instead.
- **`None` enum member:** keep it (maps to the existing "clear all" string) or drop it (empty set ==
  none). Recommend keeping. — *Decide during Phase 1.*
- **Persistence parity:** deferred to Phase 5; default is "match the Android filter" (no persistence).

## Verification

- Open Teaching → monitor. Артефакты tab opens the menu; **toggling items keeps the menu open**, each
  shows a check, and the tab goes active (and shows a count if implemented).
- Selecting **Mains** makes the trace fuzzy with 50 Hz hum; **Baseline** makes the isoline wander;
  **Muscle/Contact/Motion** each look distinct; **multiple at once** sum visibly.
- With **Mains** on, switching Filters → Low-pass visibly cleans the hum (artifacts apply before filter).
- **None** clears all noise. Noise is stable while scrolling (no reshuffle each frame).
- Build passes; no missing-string fallbacks; existing filter behaviour unchanged.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Domain: artifacts as a Set + view-model setter | 1 | Compile-only; no behaviour yet |
| 2 | BioSPPy: EcgArtifactGenerator.kt | 2 | Pure DSP; port from C#, unit-test if possible |
| 3 | Apply artifact noise in Lead.kt + thread through Monitor/screens | 3 | Trace now reacts |
| 4 | Multi-select Артефакты dropdown | 4 | Stay-open menu + checks + active tab |
| 5 | (Optional) persistence + tests | 5 | Only if we want pref parity |

---

## Outcome

Successfully ported the multi-select ECG artifacts feature from Windows to Android.
- **Domain:** `MonitorModeModel` now uses a `Set<EcgArtifact>` for artifacts.
- **DSP:** `EcgArtifactGenerator.kt` was ported from C#, implementing Mains, Muscle, Baseline, Motion, and Contact noise.
- **UI:** `MonitorControlPanel.kt` updated to support multi-select with checkmarks and stay-open behavior.
- **Rendering:** Artifact noise is now injected in `Lead.kt` before applying filters.
- **Testing:** Unit tests for `EcgArtifactGenerator` were ported and verified.
- **Threading:** Artifact state is correctly threaded through all relevant screens (Teaching, Examination, OSKE, Constructors).
