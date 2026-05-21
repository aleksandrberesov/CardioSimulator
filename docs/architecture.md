# CardioSimulator — Application Structure & Dependencies

This document maps the classes, objects, and instances of the
CardioSimulator Android app and the wiring between them. It describes
the **target architecture** built around the pathology dataset format
documented in [data-structure.md](data-structure.md): one `.dat` file
per pathology, all 12 leads inside, raw ADC samples — no anchors, no
part/series indirection, no per-record calibration.

The codebase is a layered MVVM-with-Compose viewer for that dataset.
There is no editor in the target architecture; edits to the dataset
are an upstream concern of `build_pathologies.py`, not of the app.

```
                ┌─────────────────────────────────────┐
                │      UI (Jetpack Compose)           │
                │  screens / panels / components /    │
                │  display                            │
                └──────────────┬──────────────────────┘
                               │ collectAsState / events
                ┌──────────────▼──────────────────────┐
                │           ViewModels                │
                │  AppViewModel · MonitorViewModel ·  │
                │  RhythmViewModel                    │
                └──────────────┬──────────────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
   ┌────────┐            ┌──────────────┐       ┌──────────┐
   │ domain │            │     data     │       │ network  │
   │ models │◀───────────│ EcgRepo      │       │ Tcp*     │
   │ /math  │            │ EcgSource    │       │ Sockets  │
   └────────┘            │ Prefs · Zip  │       └──────────┘
                         │ Calibration  │
                         └──────────────┘
```

---

## MVVM Architecture — Detailed Class Connections

### Full dependency graph

Arrows show **constructor injection / direct call / field access** (`──►`).
Dashed arrows (`╌╌►`) show **StateFlow emissions** consumed via `collectAsState`.

```
╔══════════════════════════════════════════════════════════════════════════════╗
║  ANDROID FRAMEWORK                                                           ║
║                                                                              ║
║  MainActivity.onCreate                                                       ║
║     │  creates via AppBuilder.build()                                        ║
║     ├──────────────────────────────────► AppStateModel                       ║
║     │      (holds OperatingModeModel list, selected mode, language, tcp)     ║
║     │                                                                        ║
║     │  creates directly                                                      ║
║     ├──────────────────────────────────► EcgRepository(AssetEcgSource)       ║
║     │                                                                        ║
║     │  creates directly                                                      ║
║     ├──────────────────────────────────► DataSourcePrefs(context)            ║
║     │                                                                        ║
║     │  creates via Compose viewModel { }                                     ║
║     └──────────────────────────────────► AppViewModel(appState, repo,        ║
║                                                ctx, prefs)                   ║
╚═══════════════════════╤══════════════════════════════════════════════════════╝
                        │ passes AppViewModel down
                        ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  VIEWMODEL LAYER                                                             ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ AppViewModel                                  (central hub)         │    ║
║  │                                                                     │    ║
║  │  Injected dependencies:                                             │    ║
║  │    AppStateModel ◄─ operatingModes, selectedOperatingMode           │    ║
║  │    EcgRepository ◄─ setSource / loadManifest / readPathology        │    ║
║  │    DataSourcePrefs ◄─ read/write treeUri, language, tcp, theme      │    ║
║  │    Context ◄─ SAF resolver, filesDir, DataStore                     │    ║
║  │                                                                     │    ║
║  │  Internal collaborators (owned / called):                           │    ║
║  │    TcpProtocol.encode/decode ──► TcpMessage sealed hierarchy        │    ║
║  │    java.net.Socket (tcpSocket) ─ connect / read / write             │    ║
║  │    ZipExtractor.extract ──► FileEcgSource → EcgRepository           │    ║
║  │    DerivedLeads.object ──► synthesize missing limb / V leads        │    ║
║  │                                                                     │    ║
║  │  Emitted StateFlows (consumed by UI via collectAsState):            │    ║
║  │    selectedOperatingMode : StateFlow<OperatingModeModel>            │    ║
║  │    selectedLanguage      : StateFlow<Language>                      │    ║
║  │    tcpIp / tcpPort       : StateFlow<String/Int>                    │    ║
║  │    isDarkTheme           : StateFlow<Boolean>                       │    ║
║  │    tcpConnectionState    : StateFlow<TcpConnectionState>            │    ║
║  │    dataState             : StateFlow<DataState>                     │    ║
║  │    isDataConfirmed       : StateFlow<Boolean>                       │    ║
║  │    lastAck               : StateFlow<TcpMessage.AckMessage?>        │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ MonitorViewModel                                                    │    ║
║  │                                                                     │    ║
║  │  Injected: DataSourcePrefs? ◄─ read/persist gridScheme              │    ║
║  │  Domain:   MonitorModeModel (immutable snapshot, copied on update)  │    ║
║  │            EcgCalibration  (embedded inside MonitorModeModel)       │    ║
║  │            GridScheme / SeriesScheme enums                          │    ║
║  │                                                                     │    ║
║  │  Emits:                                                             │    ║
║  │    monitorMode : StateFlow<MonitorModeModel>                        │    ║
║  │      (count · gridScheme · seriesScheme · speed · scale ·           │    ║
║  │       displayScale · calibration · isRunning)                       │    ║
║  │                                                                     │    ║
║  │  Setters (called by UI panels):                                     │    ║
║  │    setSeriesCount / setSeriesScheme / setGridScheme(persist?)       │    ║
║  │    setSpeed / setScale / setCalibration / setDisplayScale           │    ║
║  │    setIsRunning                                                     │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ RhythmViewModel                                                     │    ║
║  │                                                                     │    ║
║  │  Injected: EcgRepository ◄─ pathologies / readPathology /           │    ║
║  │                               leadWaveform                          │    ║
║  │                                                                     │    ║
║  │  Emits:                                                             │    ║
║  │    rhythms         : StateFlow<List<PathologyEntry>>                │    ║
║  │    selectedRhythm  : StateFlow<PathologyEntry?>                     │    ║
║  │    waveforms       : StateFlow<Map<Lead, Points>>                   │    ║
║  │                                                                     │    ║
║  │  Actions: loadManifest() / selectRhythm(id) / refresh()             │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
╚═══════════════════════╤══════════════════════════════════════════════════════╝
                        │ StateFlows collected by Composables
                        ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  UI LAYER (Jetpack Compose)                                                  ║
║                                                                              ║
║  MainScreen(AppViewModel)                                                    ║
║    │  creates (keyed by mode)                                                ║
║    ├──► MonitorViewModel(prefs)                                              ║
║    │  creates (keyed by mode + "_rhythm")                                    ║
║    ├──► RhythmViewModel(ecgRepository)                                       ║
║    │                                                                         ║
║    │  observes: selectedOperatingMode · dataState · isDataConfirmed          ║
║    │                                                                         ║
║    ├── if !isDataConfirmed or Loading/Error/NotConfigured:                   ║
║    │       DataSourceScreen(AppViewModel, RhythmViewModel, dataState)        ║
║    │         └─ SAF picker ──► AppViewModel.setDataZip()                     ║
║    │                                                                         ║
║    ├── if showSettings:                                                      ║
║    │       SettingsDialog(MonitorViewModel, AppViewModel)                    ║
║    │         └─ reads/writes monitorMode, isDarkTheme, language              ║
║    │                                                                         ║
║    ├── AppControlPanel(AppViewModel)                                         ║
║    │     └─ mode switcher, TCP toggle, language picker                       ║
║    │                                                                         ║
║    └── when selectedMode:                                                    ║
║         Teaching     → TeachingScreen(App, Monitor, Rhythm)                  ║
║         │   └─ RhythmChoosingPanel(RhythmViewModel)                          ║
║         │   └─ MonitorControlPanel(AppViewModel, MonitorViewModel)           ║
║         │   └─ Monitor / LeadsGrid / Lead ◄── waveforms StateFlow            ║
║         │                                                                    ║
║         Testing      → TestingScreen(App, Monitor, Rhythm)                   ║
║         │   └─ TestingControlPanel(AppViewModel, MonitorViewModel)           ║
║         │   └─ Monitor / LeadsGrid / Lead                                    ║
║         │                                                                    ║
║         Examination  → ExaminationScreen(App, Monitor, Rhythm)               ║
║         OSKE         → OSKEScreen(App, Monitor, Rhythm)                      ║
║             (same panel+display structure as Teaching)                       ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### StateFlow data flow (reactive bindings)

```
AppViewModel StateFlows           Consumed by
─────────────────────────────────────────────────────────────────────
selectedOperatingMode   ──╌╌►  MainScreen (routing), AppControlPanel
selectedLanguage        ──╌╌►  AppControlPanel, screens
tcpConnectionState      ──╌╌►  AppControlPanel, MonitorControlPanel
dataState               ──╌╌►  MainScreen (guard), DataSourceScreen
isDataConfirmed         ──╌╌►  MainScreen (guard)
isDarkTheme             ──╌╌►  CardioSimulatorTheme (wraps Material3)
tcpIp / tcpPort         ──╌╌►  SettingsDialog
lastAck                 ──╌╌►  MonitorControlPanel (feedback display)

MonitorViewModel StateFlows
─────────────────────────────────────────────────────────────────────
monitorMode             ──╌╌►  Monitor (speed, scale, grid, running)
                               MonitorControlPanel (reflects settings)
                               SettingsDialog (calibration)

RhythmViewModel StateFlows
─────────────────────────────────────────────────────────────────────
rhythms                 ──╌╌►  RhythmChoosingPanel (list)
selectedRhythm          ──╌╌►  RhythmChoosingPanel (highlight)
waveforms               ──╌╌►  LeadsGrid → Lead (render Points per lead)
```

### UI component ownership tree

```
CardioSimulatorTheme
└── MainScreen
    ├── [guard] DataSourceScreen
    │     └── SAF file picker ──► AppViewModel.setDataZip()
    ├── [overlay] SettingsDialog
    │     ├── reads/writes MonitorViewModel.monitorMode
    │     └── reads/writes AppViewModel.{isDarkTheme, selectedLanguage, tcpIp, tcpPort}
    ├── AppControlPanel
    │     ├── reads AppViewModel.{selectedOperatingMode, tcpConnectionState, selectedLanguage}
    │     └── calls AppViewModel.{setOperatingMode, toggleTcpConnection, setLanguage}
    └── [mode screen]  ← one of four, keyed by OperatingMode
          ├── RhythmChoosingPanel
          │     ├── reads  RhythmViewModel.{rhythms, selectedRhythm}
          │     └── calls  RhythmViewModel.selectRhythm()
          ├── MonitorControlPanel
          │     ├── reads  AppViewModel.tcpConnectionState, lastAck
          │     ├── reads  MonitorViewModel.monitorMode
          │     └── calls  AppViewModel.sendStartCommand / sendStopCommand
          │                MonitorViewModel.setIsRunning
          ├── TestingControlPanel          (Testing only)
          ├── TeachingControlPanel         (Teaching only)
          ├── Monitor                      ← display
          │     ├── reads MonitorViewModel.monitorMode (speed, scale, grid, running)
          │     └── provides LocalPixelScale to subtree
          └── LeadsGrid
                └── Lead  ×N
                      ├── reads RhythmViewModel.waveforms[lead] → Points
                      └── reads LocalPixelScale (PixelScale + EcgCalibration)
```

---

## 1. Package layout

| Package | Role |
|---|---|
| `com.example.cardiosimulator` | `MainActivity` — Compose entry point |
| `…domain` | Pure-Kotlin models: app state, pathology / lead models, derived-lead math |
| `…data` | Storage / persistence: `EcgRepository`, `EcgSource` family, `DataSourcePrefs`, ZIP I/O, calibration, pixel scaling |
| `…network` | TCP layer: `TcpProtocol` (encode/decode), `TcpMessage` sealed hierarchy, `TcpConnectionState` |
| `…ui.viewmodels` | Three `ViewModel`s: `AppViewModel`, `MonitorViewModel`, `RhythmViewModel` |
| `…ui.screens` | Top-level Composables, one per `OperatingMode` + `MainScreen` + `DataSourceScreen` + `SettingsScreen` |
| `…ui.panels` | Side / control panels reused across screens |
| `…ui.display` | Waveform render Composables: `Monitor`, `Lead`, `LeadsGrid` |
| `…ui.components` | Lower-level visual building blocks (chart canvas, grid, labels) |
| `…ui.theme` | Material3 theme, colors, typography |
| `…ui.utils` | Misc helpers |

---

## 2. Domain layer (`…domain`)

Pure-Kotlin, no Android imports (except a couple of `@StringRes` references).
These types are the source of truth the UI and view-models manipulate.

### Application state

| Type | Kind | Role | Depends on |
|---|---|---|---|
| `OperatingMode` | enum | `Teaching`, `Testing`, `Examination`, `OSKE` | — |
| `OperatingModeModel` | data class | One entry per `OperatingMode` (id + description) | `OperatingMode` |
| `AppBuilder` | class | Builder collecting `OperatingModeModel`s, produces `AppStateModel` | `OperatingModeModel`, `AppStateModel` |
| `AppStateModel` | data class (mutable) | Holds selected mode, language, TCP target | `OperatingModeModel`, `Language` |
| `Language` | enum | `EN`, `RU`, `ZH`, `ES` with locale tags | — |

### Pathology / waveform model

The new dataset packs one file per pathology, all 12 leads inside. The
domain types mirror that flat shape — no `WaveformPart`, no
`EcgSeries`, no `SeriesPartRef`. See [data-structure.md](data-structure.md)
for the on-disk grammar.

| Type | Kind | Role |
|---|---|---|
| `Lead` | enum | 12-lead identifiers (`I`, `II`, …, `V6`) |
| `PathologyEntry` | data class | Manifest row: `(id, titleEn, nameRu, leadsCount, fileName)` |
| `PathologyManifest` | data class | Parsed `manifest.txt`: `(version, baseline, leadOrder, entries: List<PathologyEntry>)` |
| `LeadStream` | data class | One lead block: `(lead: Lead, samples: IntArray)` — raw ADC, baseline-centered on 1024 |
| `PathologyFile` | data class | Parsed `<pathology>.dat`: `(id, titleEn, nameRu, leads: Map<Lead, LeadStream>)` |
| `DerivedLeads` | `object` | Einthoven / Goldberger + V-projection math used to fill missing leads (e.g., `emd` ships only 6) |

### Display values

| Type | Kind | Role |
|---|---|---|
| `Points` | wrapper around `List<Float>` | Baseline-zeroed sample buffer for one lead, ready for the renderer |

---

## 3. Data layer (`…data`)

### Source abstraction

```
        ┌──────────────────┐
        │   EcgSource      │   interface (read-only)
        └──┬───────────────┘
           │ implemented by
           │
   ┌───────┴─────────┐
   │                 │
┌──▼──────────┐  ┌───▼────────────┐
│AssetEcgSource│  │FileEcgSource   │
│ (bundled    │  │ (extracted ZIP │
│  assets)    │  │  in filesDir)  │
└─────────────┘  └────────────────┘
```

Both sources are **read-only** — the app only consumes pathologies; it
does not produce them. Producing pathologies is the job of
`Data/build_pathologies.py`.

| Type | Role | Notes |
|---|---|---|
| `EcgSource` | Interface: `readManifest() / readPathology(id) / listPathologies()` | Storage-agnostic |
| `AssetEcgSource` | Reads `Pathologies/` from APK assets (or directly from the bundled zip) | Default at boot |
| `FileEcgSource` | Reads from a directory in app `filesDir/pathologies` populated by `ZipExtractor` | Selected after the user picks a custom ZIP via SAF |
| `EcgRepository` | Holds the current `EcgSource`, caches the manifest, lazily reads pathology files on demand, and exposes baseline-zeroed `Points` per `(pathology, lead)` |

### `EcgRepository` API surface

```kotlin
class EcgRepository(source: EcgSource) {
    fun setSource(newSource: EcgSource)         // swap & invalidate caches
    fun loadManifest()                          // eager: read manifest.txt
    fun pathologies(): List<PathologyEntry>     // from manifest, sorted by title
    fun readPathology(id: String): PathologyFile?   // lazy: open the .dat file
    fun leadWaveform(id: String, lead: Lead): Points?
}
```

`leadWaveform` is the single entry point the renderer consumes: it
returns the baseline-zeroed `Points` for one lead of one pathology,
optionally synthesizing the lead via `DerivedLeads` if the file does
not contain it.

### Persistence

| Type | Role |
|---|---|
| `DataSourcePrefs` | DataStore wrapper: `treeUri`, `languageTag`, `tcpIp`, `tcpPort`, `isDarkTheme`, `gridScheme` |
| `ZipExtractor` | `object` — unzips a SAF `Uri` (a `Pathologies.zip`) into `filesDir/pathologies`. The new ZIP is flat UTF-8 throughout, so no charset detection is needed |

### Calibration / scaling

| Type | Role |
|---|---|
| `EcgCalibration` | Physical constants: gain (mm/mV), sample rate (Hz), ADC counts/mV. The new dataset format does **not** encode any of these — they are fixed by the playback engine, so `EcgCalibration` is the single source of truth |
| `PixelScale` | Derives `pxPerMm / pxPerMv / pxPerSec / pxPerSample / pxPerAdcCount` from `EcgCalibration` + display density |
| `LocalPixelScale` | `CompositionLocal` providing `PixelScale` down the Compose tree |
| `Points` | Wrapper around `List<Float>` (sample buffer ready for display) |

---

## 4. Network layer (`…network`)

| Type | Role |
|---|---|
| `TcpConnectionState` | Sealed: `Disconnected` / `Connecting` / `Connected` / `Error(msg)` |
| `TcpMessage` | Sealed hierarchy: `StartCommand`, `StopCommand`, `PointsMessage`, `AckMessage` |
| `TcpProtocol` | `object` — JSON encode/decode + line-framing helper; throws `TcpProtocolException` |
| `TcpProtocolException` | Wraps invalid JSON or missing fields |

The TCP socket itself lives inside `AppViewModel.connectionJob` /
`tcpSocket`; the network package is a pure protocol module. See
[tcp-protocol.md](tcp-protocol.md) for the wire format.

---

## 5. View-model layer (`…ui.viewmodels`)

### AppViewModel — the central hub

`AppViewModel(appState, ecgRepository?, appContext?, prefs?, tcpReconnectIntervalMs)`

Holds:
- App-wide `StateFlow`s: `selectedLanguage`, `selectedOperatingMode`,
  `tcpIp / tcpPort`, `isDarkTheme`, `tcpConnectionState`, `dataState`,
  `isDataConfirmed`, `lastAck`.
- TCP socket lifecycle: `connectTcp / disconnectTcp / toggleTcpConnection / dismissTcpError`.
- Data-source lifecycle: `setDataZip → loadFromSaf → ZipExtractor.extract → FileEcgSource → EcgRepository.setSource → loadManifest`.

Dependencies in (constructor):
- `AppStateModel` (domain)
- `EcgRepository` (data)
- `DataSourcePrefs` (data)
- Android `Context` (only for SAF / DataStore / filesDir)

Dependencies out:
- emits to UI via `StateFlow`
- talks to `TcpProtocol`, `TcpMessage`

### MonitorViewModel

`MonitorViewModel(prefs?)`

- One `StateFlow<MonitorModeModel>`.
- Setters: `setSeriesCount / setSeriesScheme / setGridScheme / setSpeed / setScale / setCalibration / setDisplayScale / setIsRunning`.
- Persists `gridScheme` through `DataSourcePrefs`.
- Re-created per `OperatingMode` (keyed by `selectedMode.id.name` in `MainScreen`).

### RhythmViewModel

`RhythmViewModel(ecgRepository)`

- `StateFlow`s: `rhythms` (`List<PathologyEntry>`), `selectedRhythm`
  (`PathologyEntry?`), `waveforms` (`Map<Lead, Points>`).
- `loadManifest()` pulls the pathology index from `EcgRepository`.
- `selectRhythm(id)` lazily reads the pathology `.dat`, baseline-zeroes
  each lead, fills in missing leads via `DerivedLeads`, and publishes
  the result as `waveforms`.
- Recreated per `OperatingMode` keyed by `"<mode>_rhythm"`.

---

## 6. UI layer (Compose)

### Activity / entry point

```
MainActivity.onCreate
 ├─ AppBuilder().addMode(...).build(initial = Teaching)  ──▶ AppStateModel
 ├─ EcgRepository(AssetEcgSource(assets))                ──▶ default repo
 ├─ DataSourcePrefs(context)                             ──▶ persistence
 └─ AppViewModel(appState, repo, ctx, prefs)
       │
       ▼
   CardioSimulatorTheme(darkTheme=…)
       │
       ▼
   MainScreen(viewModel)
```

### MainScreen routing

```
MainScreen(viewModel: AppViewModel)
 ├─ create MonitorViewModel (keyed by mode)
 ├─ create RhythmViewModel  (keyed by mode + "_rhythm")
 ├─ if !isDataConfirmed | NotConfigured | Loading | Error →
 │      DataSourceScreen(viewModel, rhythmViewModel, state)
 │
 ├─ if showSettings → SettingsDialog(monitorViewModel, appViewModel)
 │
 ├─ AppControlPanel(viewModel, onSettingsClick)
 └─ when (selectedMode.id):
        Teaching     → TeachingScreen(   viewModel, monitorViewModel, rhythmViewModel)
        Testing      → TestingScreen(    viewModel, monitorViewModel, rhythmViewModel)
        Examination  → ExaminationScreen(viewModel, monitorViewModel, rhythmViewModel)
        OSKE         → OSKEScreen(       viewModel, monitorViewModel, rhythmViewModel)
```

### Screen → panel / display map

| Screen | Uses panels | Uses display |
|---|---|---|
| `DataSourceScreen` | (SAF picker) | — |
| `SettingsScreen` / `SettingsDialog` | (settings widgets) | — |
| `TeachingScreen` | `RhythmChoosingPanel`, `MonitorControlPanel` | `Monitor`, `LeadsGrid`, `Lead` |
| `TestingScreen` | `RhythmChoosingPanel`, `TestingControlPanel`, `MonitorControlPanel` | `Monitor`, `LeadsGrid`, `Lead` |
| `ExaminationScreen` | (similar to Teaching) | `Monitor`, `LeadsGrid`, `Lead` |
| `OSKEScreen` | (similar) | `Monitor`, `LeadsGrid`, `Lead` |
| `MainScreen` (top bar) | `AppControlPanel` | — |

### Components (`…ui.components`)

Reusable visual primitives consumed by screens / display:

`AutoResizeText`, `CalibrationPulse`, `ChartCanvas` (incl. `chartArea`),
`ControlPanelDivider`, `Label`, `Modifers`, `Tab` / `PaperGridLegend`.

### Display (`…ui.display`)

`Monitor` — paper grid + scrolling chrome, takes a `MonitorViewModel`.
`LeadsGrid` — arranges N `Lead` cells in row × column grid.
`Lead` — renders one lead's `Points` (view-only).
`ekgGrid` / `Modifers` — modifier helpers for the paper grid.

### Theme (`…ui.theme`)

`CardioSimulatorTheme` (Composable) — wraps Material3 with `Color`, `Type`.

---

## 7. Lifetime / instance summary

| Instance | Created in | Lifetime |
|---|---|---|
| `MainActivity` | Android framework | Process |
| `AppStateModel` | `AppBuilder.build()` inside `MainActivity.onCreate` | Activity instance |
| `EcgRepository` | `MainActivity.onCreate` (starts wrapping `AssetEcgSource`, later swapped to `FileEcgSource`) | Activity instance |
| `DataSourcePrefs` | `MainActivity.onCreate` | Activity instance |
| `AppViewModel` | Compose `viewModel { … }` factory | Outlives configuration changes |
| `MonitorViewModel` | `MainScreen`, keyed per `OperatingMode` | Recreated when mode changes |
| `RhythmViewModel` | `MainScreen`, keyed per `OperatingMode` | Recreated when mode changes |
| TCP `Socket` + `connectionJob` | `AppViewModel.connectTcp()` | Until `disconnectTcp` / VM clear |
| `DerivedLeads`, `TcpProtocol`, `ZipExtractor` | `object` singletons | Process |

---

## 8. Data-flow walkthroughs

### App launch with previously chosen ZIP

```
MainActivity
  → AppViewModel.init { prefs.treeUri.first() }
  → AppViewModel.loadFromSaf(ctx, savedUri)
      → if FileEcgSource(targetDir).isValid() reuse it
      → else ZipExtractor.extract(ctx, uri, targetDir)
      → EcgRepository.setSource(FileEcgSource(targetDir))
      → EcgRepository.loadManifest()
  → _dataState = Ready(pathologyCount)
  → MainScreen sees Ready → dispatches to mode screen
```

### Selecting a pathology

```
RhythmChoosingPanel tap on "Atrial tachycardia"
  → RhythmViewModel.selectRhythm("tachpm")
       → EcgRepository.readPathology("tachpm")  ──► PathologyFile
       → for each Lead L:
            samples = file.leads[L]?.samples
                    ?: DerivedLeads.synthesize(L, file.leads)
            points  = Points(samples.map { (it - 1024).toFloat() })
       → _waveforms.value = mapOf(L → Points, ...)
  → LeadsGrid → Lead reads waveforms[lead] → ChartCanvas redraws
```

### Sending a Start command over TCP

```
MonitorControlPanel button
   → AppViewModel.sendStartCommand(pathologyId)
        TcpMessage.StartCommand{id, params}
        TcpProtocol.encode(msg) + "\n"
        socket.outputStream.write(...)
   → server replies with line → TcpProtocol.decodeOrNull(line)
        if AckMessage → _lastAck.value = msg
```

---

## 9. Quick class-dependency cheat-sheet

Legend: `──►` = creates / injects / calls  ·  `╌╌►` = emits StateFlow  ·  `◄implements` = interface

```
──── CONSTRUCTION / INJECTION ────────────────────────────────────────────────

MainActivity ──► AppBuilder ──► AppStateModel
                                └── OperatingModeModel × 4
            ──► EcgRepository(AssetEcgSource)
                  ├── EcgSource ◄implements AssetEcgSource (boot)
                  └── EcgSource ◄implements FileEcgSource  (after SAF pick)
            ──► DataSourcePrefs
            ──► AppViewModel(AppStateModel, EcgRepository, Context, DataSourcePrefs)
                  ├── TcpProtocol.object  ──► TcpMessage (encode/decode)
                  ├── java.net.Socket     (tcpSocket)
                  ├── ZipExtractor.object
                  └── DerivedLeads.object (limb / V-lead math)

MainScreen ──► AppViewModel
          ──► MonitorViewModel(DataSourcePrefs)
                └── MonitorModeModel ──► EcgCalibration
                                    ──► GridScheme / SeriesScheme
          ──► RhythmViewModel(EcgRepository)
                └── EcgRepository ──► PathologyEntry / PathologyFile
                                  ──► Points (baseline-zeroed lead buffers)

Screen* ──► Panels*  ──► AppViewModel  (mode switch / TCP)
                     ──► MonitorViewModel (setSpeed / setScale / setGridScheme)
                     ──► RhythmViewModel  (selectRhythm / loadManifest)
Screen* ──► display.Monitor / Lead / LeadsGrid
              └── components.{ChartCanvas, CalibrationPulse, …}
              └── LocalPixelScale (CompositionLocal) ──► PixelScale ──► EcgCalibration

──── STATEFLOW EMISSIONS (╌╌►) ───────────────────────────────────────────────

AppViewModel    ╌╌► selectedOperatingMode  → MainScreen, AppControlPanel
                ╌╌► selectedLanguage       → AppControlPanel, Screens
                ╌╌► tcpConnectionState     → AppControlPanel, MonitorControlPanel
                ╌╌► dataState              → MainScreen (guard), DataSourceScreen
                ╌╌► isDataConfirmed        → MainScreen (guard)
                ╌╌► isDarkTheme            → CardioSimulatorTheme
                ╌╌► lastAck                → MonitorControlPanel

MonitorViewModel╌╌► monitorMode            → Monitor, MonitorControlPanel, SettingsDialog

RhythmViewModel ╌╌► rhythms                → RhythmChoosingPanel (list)
                ╌╌► selectedRhythm         → RhythmChoosingPanel (highlight)
                ╌╌► waveforms              → LeadsGrid → Lead (render per-lead Points)

──── SINGLETONS (object / process lifetime) ──────────────────────────────────

DerivedLeads       ← used by RhythmViewModel (fill missing leads)
TcpProtocol        ← used by AppViewModel    (JSON encode / decode)
ZipExtractor       ← used by AppViewModel    (SAF import → filesDir)
```

`*` = one of the four `OperatingMode` variants.

---

## 10. What changed from the legacy architecture

For readers familiar with the previous Parts/Series-based design, the
target architecture removes the following:

- **Editor mode** and its screen (`EditorScreen`, `AnchorInspector`,
  `SeriesInspector`, `EditorViewModel`).
- **Editable working copies**: `EditablePart`, `EditableSeries`,
  `UndoStack<T>`, `AnchorClipboard`, `PartNamer`.
- **Anchor model**: `AnchorPoint`, `SourceSpec`, `bakeAnchorsToSamples`,
  `AnchorCanvas` / `AnchorHandleOverlay`, `PreviewPane`,
  `BlockTimeline`.
- **Part/series split**: `WaveformPart`, `EcgSeries`, `SeriesPartRef`,
  `BlockFlags`, `PathologyGroup`, `EcgFileFormat`.
- **Per-record calibration**: `aMax` / `aValue` / `duration` /
  `samplesPerMv` / `effectiveSampleRateHz` and the
  `pxPerSampleFor` / `pxPerAdcCountFor` overrides on `PixelScale`.
- **Writable sources**: `EcgSource.writeSeries / writePart / isWritable`,
  `ZipCompressor`, `dirtyParts` / `dirtySeries` tracking, manual
  upload.
- **Charset detection**: `decodeEcgText`, `EcgRepository.fixEncoding`,
  multi-charset zip extraction — the new format is strictly UTF-8.

What remains: a viewer that loads a `Pathologies.zip`, presents a list
of pathologies, renders 12 leads on a paper-grid monitor, supports four
operating modes (Teaching, Testing, Examination, OSKE), and streams
playback commands over TCP.

---

_Source tree:_ `app/src/main/java/com/example/cardiosimulator/`.
