# Development Plan - New Requirements 250526

## Overview
Implement features and fixes requested in `docs/requirements/new requirements 250526.txt`.

## Phase 1: Editor Lead Calculations (High Priority)
Goal: Allow users to automatically calculate derived leads in the Editor mode.

1.  **Update `EditorViewModel.kt`**:
    *   Add `calculateDerivedLeads()` method.
    *   Handle baseline subtraction/addition when using `DerivedLeads` utility.
    *   Mark derived leads as "dirty" to enable saving.
2.  **Update `EditorScreen.kt`**:
    *   Add a "Calculate Derived Leads" button in the toolbar or a dedicated panel.
    *   Show a confirmation dialog before overwriting existing lead data.

## Phase 2: UI Fixes and Enhancements
1.  **Rename App to "ECG Constructor"**:
    *   Update `strings.xml` for Russian and English.
    *   Update `MainScreen.kt` or `MainActivity.kt` if necessary.
2.  **Rhythm Panel Improvements**:
    *   **Fix List Reset**: Use `rememberSaveable` or hoist `LazyListState` to prevent the rhythm list from resetting to the top when the drawer is toggled.
    *   **Add Label**: Add "ECG Rhythms" (ЭКГ ритмы) title to the `RhythmChoosingDrawer`.
    *   **Pathology Name on Top**: Ensure the pathology name is clearly visible at the top of the `EditorScreen`.
3.  **Playback Controls in Editor**:
    *   Add "Start/Stop" buttons to the Editor toolbar.
    *   Link to `MonitorViewModel` to control the scrolling state.

## Phase 3: Rendering and Settings
1.  **ECG Movement with Grid**:
    *   Verify and fix the rendering pipeline to ensure the grid moves at the same speed as the ECG wave.
2.  **"Blank Sheet" Setting**:
    *   Add a toggle in `SettingsScreen.kt`.
    *   Update `Monitor.kt` or `ChartCanvas.kt` to hide the grid and show a "black bar" (or similar marker) when enabled.

## Phase 4: Comparative View (Compare Button)
1.  **Add "Compare" button** to the bottom panel.
2.  **Implement Comparative Screen**:
    *   Show multiple rhythms in the same lead (default Lead II).
    *   Allow selecting which rhythms to compare.
    *   Support saving the comparison configuration.

## Phase 5: Education and Courses
1.  **Education Content**:
    *   Implement data models for Courses and Lectures.
    *   Create `TeachingScreen` improvements for "Program 1" (General course) and others.
2.  **Course Constructor**:
    *   Implement a UI to create and edit education courses.

---

### Implementation Task 1: Lead Calculations
- [ ] Add `calculateDerivedLeads` to `EditorViewModel`.
- [ ] Add UI trigger in `EditorScreen`.
- [ ] Verify calculations with a test case.
