# Plan: Add the monitor vertical-scale (mm/mV gain) dropdown — Windows → Android

**Created:** 2026-07-02
**Status:** NOT STARTED
**Direction:** **Windows → Android** (reverse of the usual). The feature was built in the WinUI 3
port first, at the customer's request: *"add another button to change the vertical scale of the
monitor, mm/mV; the dropdown should contain 2.5, 5, 10, 20, 40."* Android must gain the same
control so both platforms expose selectable amplitude calibration.

**Target (Android) source root:** `E:\VLN_Project\CardioSimulator\app\src\main\java\com\example\cardiosimulator\`
**Reference (Windows) source root:** `E:\VLN_Project\CardioSimulatorWin\src\`

---

## What the feature is (and is NOT)

A new **Gain** dropdown pill in the monitor's bottom control row, sitting **immediately to the
right of the Speed dropdown** (final order: **Count · Scheme · Speed · Gain · Scale**). It shows the
current gain as a number with a `mm/mV` sub-label and opens a menu of **2.5 / 5 / 10 / 20 / 40**
mm/mV. Standard clinical gain is **10 mm/mV** (the default).

This drives `EcgCalibration.gainMmPerMv`, which feeds `PixelScale.pxPerMv = gainMmPerMv · pxPerMm ·
gainZoomY`. Raising it makes every waveform taller against the **fixed** paper grid — the true
clinical mm/mV control (at 20 mm/mV a 1 mV deflection spans 4 large cells instead of 2).

**It is distinct from the existing `scale` (%) dropdown**, which is a whole-view zoom
(100/200/300/400/500%) applied in `Monitor.kt` and does NOT change the mm/mV calibration. Do not
merge or replace `scale`. Gain changes amplitude calibration only and must leave the user's zoom/pan
untouched.

**Good news — the model + renderer are already wired.** `EcgCalibration.gainMmPerMv` exists (default
`10f`) and `Monitor.kt` already builds `pixelScale` with `remember(..., mode.calibration, ...)` keyed
on `mode.calibration` and even formats `mode.calibration.gainMmPerMv` for the grid label. So the only
work is a **setter + persistence + one dropdown + strings**. No renderer or calibration-model change.

---

## Reference: the exact Windows changes to mirror

| Concern | Windows file | Change |
|---|---|---|
| Calibration model | `CardioSimulator.Core/Data/EcgCalibration.cs` | none — `GainMmPerMv` already exists (10) |
| ViewModel setter + load | `CardioSimulator.App/ViewModels/MonitorViewModel.cs` | new `SetGain(float)` (writes `Calibration.GainMmPerMv`, persists `monitor_gain`); constructor loads `monitor_gain` |
| Control panel UI | `CardioSimulator.App/Controls/MonitorControlPanel.xaml(.cs)` | new `GainTab` between Speed and Scale; `OnGainClick` flyout of 2.5/5/10/20/40; `mm/mV` sub-label; shows current gain |
| Strings | `CardioSimulator.App/Localization/AppStrings.cs` | `monitor_gain_title` + `monitor_gain_unit` in EN/RU/ZH/ES/HI |

The Windows menu values and the display formatter (whole numbers render bare, fractions keep one
decimal — `2.5`, `10`, `40`):

```csharp
foreach (var gain in new[] { 2.5f, 5f, 10f, 20f, 40f }) { ... _viewModel?.SetGain(gain); }
private static string FormatGain(float gain) =>
    gain % 1 == 0 ? ((int)gain).ToString() : gain.ToString("0.#");
```

---

## Steps (Android)

### 1. `ui/viewmodels/MonitorViewModel.kt` — option list, setter, load

Add the option list next to `availableScales`:

```kotlin
val availableGains = listOf(2.5f, 5f, 10f, 20f, 40f)
```

Add a setter mirroring `setScale` (updates the nested `calibration`, persists under a new pref):

```kotlin
fun setGain(gainMmPerMv: Float, persist: Boolean = true) {
    _monitorMode.update { it.copy(calibration = it.calibration.copy(gainMmPerMv = gainMmPerMv)) }
    if (persist) {
        viewModelScope.launch {
            prefs?.setMonitorGain(mode.name, gainMmPerMv)
        }
    }
}
```

In the `init { viewModelScope.launch { … } }` block, load it (place next to the `monitorScale`
load):

```kotlin
prefs?.monitorGain(modeName)?.first()?.let { gain ->
    setGain(gain, persist = false)
}
```

### 2. `data/DataSourcePrefs.kt` — new per-mode float pref (mirror `monitor_scale`)

Add a read flow, a writer, and a key, copying the `monitorScale` / `setMonitorScale` /
`KEY_MONITOR_SCALE` trio exactly (per-mode key with a legacy global fallback):

```kotlin
fun monitorGain(mode: String): Flow<Float?> = context.dataSourceDataStore.data.map { prefs ->
    fun getFloat(name: String): Float? = when (val v = prefs.asMap().entries.find { it.key.name == name }?.value) {
        is Float -> v
        is Double -> v.toFloat()
        is String -> v.toFloatOrNull()
        else -> null
    }
    getFloat("${mode}_monitor_gain") ?: getFloat("monitor_gain")
}

suspend fun setMonitorGain(mode: String, gain: Float) {
    context.dataSourceDataStore.edit { prefs ->
        prefs[floatPreferencesKey("${mode}_monitor_gain")] = gain
    }
}

// in the companion object, next to KEY_MONITOR_SCALE:
private val KEY_MONITOR_GAIN = floatPreferencesKey("monitor_gain")
```

(Match whatever the local `getFloat` helper actually supports — copy the body of `monitorScale`
verbatim and only rename `scale` → `gain`.)

### 3. `ui/panels/MonitorControlPanel.kt` — the Gain dropdown

In the **"Left section: Count, Scheme, Speed, Scale"** `Row`, insert a new `Box` **between the Speed
box and the Scale box** so the order becomes Count · Scheme · Speed · **Gain** · Scale. Bump that
Row's `weight(4f)` → `weight(5f)` (it now holds five equal cells) so the pills keep the Speed/Scale
proportions.

```kotlin
Box(modifier = Modifier.weight(1f)) {
    var gainMenuExpanded by remember { mutableStateOf(false) }
    val gain = monitorMode.calibration.gainMmPerMv
    val formattedGain = if (gain % 1 == 0f) gain.toInt().toString() else gain.toString()
    Tab(
        text = formattedGain,
        subText = stringResource(R.string.monitor_gain_unit),
        showChevron = true,
        onClick = { gainMenuExpanded = true },
        modifier = Modifier.fillMaxWidth()
    )
    DropdownMenu(
        expanded = gainMenuExpanded,
        onDismissRequest = { gainMenuExpanded = false }
    ) {
        viewModel.availableGains.forEach { gainOption ->
            val display = if (gainOption % 1 == 0f) gainOption.toInt().toString() else gainOption.toString()
            DropdownMenuItem(
                text = { Text("$display ${stringResource(R.string.monitor_gain_unit)}") },
                onClick = {
                    viewModel.setGain(gainOption)
                    gainMenuExpanded = false
                }
            )
        }
    }
}
```

This mirrors the existing Speed box (same `Tab` + `subText` + `DropdownMenu` shape). Formatting
follows the Speed convention (`2.5`, `10`, `40`).

### 4. `res/values*/strings.xml` — the unit label (×5 locales)

Add next to `monitor_speed_unit` in each file. Reuse the mm/mV wording already present in the
`monitor_scale_label_format` strings for consistency:

| File | string |
|---|---|
| `values/strings.xml` (en) | `<string name="monitor_gain_unit">mm/mV</string>` and `<string name="monitor_gain_title">Gain</string>` |
| `values-ru/strings.xml` | `<string name="monitor_gain_unit">мм/мВ</string>` and `<string name="monitor_gain_title">Усиление</string>` |
| `values-zh/strings.xml` | `<string name="monitor_gain_unit">毫米/毫伏</string>` and `<string name="monitor_gain_title">增益</string>` |
| `values-es/strings.xml` | `<string name="monitor_gain_unit">mm/mV</string>` and `<string name="monitor_gain_title">Ganancia</string>` |
| `values-hi/strings.xml` | `<string name="monitor_gain_unit">mm/mV</string>` and `<string name="monitor_gain_title">गेन</string>` |

`monitor_gain_title` is optional (the Tab only needs the unit sub-label) but include it for parity
and any future tooltip. **Watch the placeholder/escaping gotcha:** these are plain strings with no
`%` — no `%%` needed — but keep them inside the same `<resources>` block and don't reuse a name that
already exists.

---

## Non-goals / do NOT

- Do **not** touch `adcCountsPerMv` (1024) — that is the separate ADC-calibration constant from the
  earlier amplitude-scale sync; this feature only changes `gainMmPerMv`.
- Do **not** alter or remove the `scale` (%) zoom dropdown or `availableScales`.
- Do **not** change `PixelScale`, `Monitor.kt` rendering, the grid density, or the calibration
  pulse (it is drawn from `pxPerMv` and correctly stays 2 large cells tall — its *pixel* height
  grows with gain, which is physically right).
- Do **not** clamp the trace for high gain; let tall waves extend as Windows does.

---

## Verification

Build and open Teaching → monitor, then:

1. The bottom row shows **Count · Scheme · Speed · Gain · Scale**; the Gain pill reads `10` with a
   `mm/mV` sub-label by default.
2. Open the Gain menu → **2.5, 5, 10, 20, 40 mm/mV** present.
3. Pick **20**: every waveform doubles in height against the (unchanged) grid; a 1 mV calibration
   pulse now spans **4 large cells**. Pick **5**: waves halve; the pulse spans **1 large cell**.
   The `scale` (%) zoom is unaffected, and pan/zoom are not reset by a gain change.
4. The grid-scale label (`monitor_scale_label_format`, `… mm/mV`) updates to the chosen value
   (it already reads `mode.calibration.gainMmPerMv` in `Monitor.kt`).
5. Change gain, leave the screen and return: the choice persists (per-mode `${mode}_monitor_gain`
   DataStore key). Switch language: the sub-label localizes (мм/мВ, 毫米/毫伏, …).

---

## Acceptance checklist
- [ ] `MonitorViewModel.setGain(Float, persist)` updates `calibration.gainMmPerMv` and persists.
- [ ] `MonitorViewModel.availableGains = [2.5, 5, 10, 20, 40]`; loaded from prefs in `init`.
- [ ] `DataSourcePrefs`: `monitorGain(mode)` flow + `setMonitorGain(mode, gain)` + key, per-mode with
      global fallback (mirrors `monitor_scale`).
- [ ] Gain dropdown inserted **between Speed and Scale**; left Row weight `4f → 5f`.
- [ ] `monitor_gain_unit` (+ `monitor_gain_title`) added to all five `strings.xml`.
- [ ] Renderer/`PixelScale`/`adcCountsPerMv`/`scale`% untouched.
- [ ] Visual + persistence + localization checks pass.
