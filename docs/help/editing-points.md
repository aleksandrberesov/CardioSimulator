# Manual Point Editing Workflow

This guide explains how to manually edit ECG waveforms in the Constructor.

## How to Edit Points

1. **Select Lead:** Click on the lead tab (I, II, III, etc.) you want to edit.
2. **Select Point:**
   - **On Canvas:** Tap directly on the ECG waveform. A red cursor and circle will highlight the selected sample.
   - **Keyboard/Buttons:** Use the **◀ (Previous)** and **▶ (Next)** buttons in the bottom panel to move sample by sample.
   - **Exact Time:** Tap the **time value** (e.g., `120 ms`) to type an exact timestamp.
3. **Nudge Value:**
   - Use **▼ (Down)** and **▲ (Up)** buttons to nudge the ADC value.
   - The change is applied using a **Smoothing Kernel** (Cosine, Spline, etc.) to keep the waveform smooth.
4. **Exact ADC Value:**
   - Tap the **ADC value** (e.g., `ADC: 1024`) to open a dialog and type an exact raw value.
5. **Trace Tool:**
   - Switch to **Trace** mode (Pencil icon) to draw freehand.

## Element Library (New)

Instead of editing point-by-point, you can insert pre-generated ECG elements:
1. Tap the **Library (+)** icon in the bottom panel.
2. Select an element (P-wave, QRS, T-wave, or a Full Cycle).
3. The element will be inserted starting at the currently selected index.
4. Use **Undo** if needed.
