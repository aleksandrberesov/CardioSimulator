# Plan: Sync BioSPPy ECG DSP, Synthesis, and Landmark Features to Android

**Created:** 2026-06-27  
**Status:** COMPLETED  
**Direction:** **Windows → Android**

**Target (Android) source root:** `C:\VLN_Project\CardioSimulator\app\src\main\java\com\example\cardiosimulator\`  
**Reference (Windows) source root:** `E:\VLN_Project\CardioSimulatorWin\src\`  

---

## 1. Background & Goals

During the Windows development phase, we ported Python's **BioSPPy** library to C# (`BioSPPy.Net`) and integrated its features into the WinUI 3 user interface. This added:
- **Zero-Phase Digital Filtering** (Lowpass, Highpass, Bandpass Butterworth filters).
- **Signal Quality Indices (SQI)** with ZZ2018 fuzzy classification metrics.
- **Dolinský et al. Analytical Synthesizer** for generating physiological waveforms.
- **Landmark Auto-Detection** (QRS Hamilton segmenter + fiducial point extraction for P-Q-R-S-T peaks and boundaries).

To maintain absolute feature parity across platforms, the Android codebase must implement the same DSP capabilities in Kotlin (or utilizing a Kotlin-port library) and wire them to the corresponding Jetpack Compose UI elements.

---

## 2. Part A: Porting Core Algorithms to Kotlin

Create a package (e.g., `com.example.cardiosimulator.signals.biosppy`) containing the mathematical translation of the ported C# code.

### 2.1 Filtering & DSP Primitives
Implemented in `Filter.kt`, `Complex.kt`, `Dsp.kt`.

### 2.2 QRS Segmenters & Corrector
Implemented in `QrsSegmenters.kt`.

### 2.3 Fiducial Landmarks (P, Q, R, S, T Peaks & Boundaries)
Implemented in `Landmarks.kt`.

### 2.4 Signal Quality Indices (SQI)
Implemented in `Sqi.kt`.

### 2.5 Dolinský Analytical Synthesizer
Implemented in `Synthesizer.kt`.

---

## 3. Part B: UI Integration in Android (Compose)

### 3.1 Live Monitor Digital Filter Dropdown
1. **Model state**: Added `EcgFilterType` to `MonitorModeModel.kt`.
2. **Dropdown Menu**: Added "Filters" tab to `MonitorControlPanel.kt`.
3. **Dynamic Filtering**: Integrated into `Lead.kt` (used in `Monitor`, `TeachingScreen`, `TestingScreen`, `ExaminationScreen`, `OSKEScreen`, `OskeConstructorScreen`, `TestConstructorScreen`).

### 3.2 Floating SQI Status Card
1. **UI Layout**: Added `SqiCard.kt` component.
2. **Real-time update**: Integrated into `TeachingScreen.kt` monitor overlay.

### 3.3 Dolinský Synthesizer Dialog
1. **Toolbar Button**: Added to `ConstructorScreen.kt` toolbar.
2. **Parameter Sheet**: Added `SynthesizerDialog.kt`.
3. **Generation**: Integrated into `ConstructorViewModel.kt` (`generateSynthesizedBeat`).

### 3.4 Landmark Auto-Detection
1. **Sidebar Button**: Added to `SignificantPointPanel.kt`.
2. **Pipeline Execution**: Integrated into `ConstructorViewModel.kt` (`autoDetectLandmarks`).

---

## 4. Acceptance Checklist

- [x] Core `biosppy` package implemented in Kotlin with zero-phase filtering, segmenters, landmarks, SQIs, and synthesizers.
- [x] Filters tab added to the monitor toolbar; waveforms filter dynamically in zero-phase forward-backward Butterworth format.
- [x] Glassmorphic floating SQI card displays sSQI, kSQI, pSQI, and fuzzy ZZ2018 quality state (LED dot).
- [x] Synthesizer dialog implemented in the editor, allowing user-customized analytical signal generation.
- [x] "Auto-Detect" button in landmark panel extracts R-peaks and segment boundaries automatically.
- [ ] Unit tests written to verify algorithm correctness against reference Python data. (Pending separate task)
