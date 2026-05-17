# CardioSimulator — Application Structure & Dependencies

This document maps the classes, objects, and instances of the
CardioSimulator Android app and the wiring between them. It is generated
from the source tree at `app/src/main/java/com/example/cardiosimulator/`.

The codebase follows a layered MVVM-with-Compose architecture:

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
   ┌────────┐            ┌──────────┐           ┌──────────┐
   │ domain │            │   data   │           │ network  │
   │ models │            │ EcgRepo  │           │ Tcp*     │
   │ /logic │◀───────────│ Sources  │           │ Sockets  │
   └────────┘            │ Prefs    │           └──────────┘
                         │ Zip*     │
                         └──────────┘
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
║                                               ctx, prefs)                   ║
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
║  │    EcgRepository ◄─ setSource / load / assemble / pathologies       │    ║
║  │    DataSourcePrefs ◄─ read/write treeUri, language, tcp, theme      │    ║
║  │    Context ◄─ SAF resolver, filesDir, DataStore                     │    ║
║  │                                                                     │    ║
║  │  Internal collaborators (owned / called):                           │    ║
║  │    TcpProtocol.encode/decode ──► TcpMessage sealed hierarchy        │    ║
║  │    java.net.Socket (tcpSocket) ─ connect / read / write             │    ║
║  │    ZipDecompressor.unzip ──► FileEcgSource → EcgRepository          │    ║
║  │    ZipCompressor.repack ──► TCP upload / export                     │    ║
║  │    EditablePart  (mutable working copies, map by identy)            │    ║
║  │    EditableSeries (mutable working copies, map by identy)           │    ║
║  │    UndoStack<EditablePart>   (partUndo,   capped snapshot ring)     │    ║
║  │    UndoStack<EditableSeries> (seriesUndo, capped snapshot ring)     │    ║
║  │    DerivedLeads.object ──► generate limb/precordial leads           │    ║
║  │    EcgFileFormat.object ──► serialize parts/series on save          │    ║
║  │                                                                     │    ║
║  │  Emitted StateFlows (consumed by UI via collectAsState):            │    ║
║  │    selectedOperatingMode : StateFlow<OperatingModeModel>            │    ║
║  │    selectedLanguage      : StateFlow<Language>                      │    ║
║  │    tcpIp / tcpPort       : StateFlow<String/Int>                    │    ║
║  │    isDarkTheme           : StateFlow<Boolean>                       │    ║
║  │    tcpConnectionState    : StateFlow<TcpConnectionState>            │    ║
║  │    dataState             : StateFlow<DataState>                     │    ║
║  │    isDataConfirmed       : StateFlow<Boolean>                       │    ║
║  │    isUploading           : StateFlow<Boolean>                       │    ║
║  │    lastAck               : StateFlow<TcpMessage.AckMessage?>        │    ║
║  │    dirtyParts            : StateFlow<Set<String>>                   │    ║
║  │    dirtySeries           : StateFlow<Set<String>>                   │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ MonitorViewModel                                                    │    ║
║  │                                                                     │    ║
║  │  Injected: DataSourcePrefs? ◄─ read/persist gridScheme             │    ║
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
║  │  Injected: EcgRepository ◄─ pathologies / allSeries / allParts /   │    ║
║  │                               assembleWaveform                      │    ║
║  │                                                                     │    ║
║  │  Emits:                                                             │    ║
║  │    rhythms         : StateFlow<List<PathologyGroup>>                │    ║
║  │    selectedRhythm  : StateFlow<PathologyGroup?>                     │    ║
║  │    waveforms       : StateFlow<Map<Lead, Points>>                   │    ║
║  │    allSeries       : StateFlow<List<EcgSeries>>                     │    ║
║  │    allParts        : StateFlow<List<WaveformPart>>                  │    ║
║  │                                                                     │    ║
║  │  Actions: loadData() / selectRhythm(pathology) / updateWaveform()  │    ║
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
║    │         └─ SAF picker ──► AppViewModel.setDataFolder()                  ║
║    │                                                                         ║
║    ├── if showSettings:                                                      ║
║    │       SettingsDialog(MonitorViewModel, AppViewModel)                    ║
║    │         └─ reads/writes monitorMode, isDarkTheme, language              ║
║    │                                                                         ║
║    ├── AppControlPanel(AppViewModel)                                         ║
║    │     └─ mode switcher, TCP toggle, language picker                       ║
║    │                                                                         ║
║    └── when selectedMode:                                                    ║
║         Teaching     → TeachingScreen(App, Monitor, Rhythm)                 ║
║         │   └─ RhythmChoosingPanel(RhythmViewModel)                         ║
║         │   └─ MonitorControlPanel(AppViewModel, MonitorViewModel)           ║
║         │   └─ Monitor / LeadsGrid / Lead ◄── waveforms StateFlow           ║
║         │                                                                   ║
║         Testing      → TestingScreen(App, Monitor, Rhythm)                  ║
║         │   └─ TestingControlPanel(AppViewModel, MonitorViewModel)           ║
║         │   └─ Monitor / LeadsGrid / Lead                                   ║
║         │                                                                   ║
║         Examination  → ExaminationScreen(App, Monitor, Rhythm)              ║
║         OSKE         → OSKEScreen(App, Monitor, Rhythm)                     ║
║         │   (same panel+display structure as Teaching)                      ║
║         │                                                                   ║
║         Editor       → EditorScreen(App, Monitor, Rhythm)                   ║
║             └─ RhythmChoosingPanel(RhythmViewModel)                         ║
║             └─ tabs:                                                        ║
║                  AnchorInspector(AppViewModel)                              ║
║                  │   └─ drag gestures ──► AppViewModel.mutatePart()         ║
║                  │   └─ AnchorClipboard.object (cut/paste)                  ║
║                  SeriesInspector(AppViewModel, RhythmViewModel)             ║
║                      └─ AppViewModel.mutateSeries()                         ║
║             └─ display:                                                     ║
║                  ChartCanvas ◄─ EditablePart.bakedSamples()                 ║
║                  AnchorHandleOverlay ◄─ EditablePart.anchors                ║
║                  CalibrationPulse ◄─ LocalPixelScale                        ║
║                  PreviewPane                                                ║
║                  BlockTimeline ◄─ EditableSeries.partRefs                   ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### StateFlow data flow (reactive bindings)

```
AppViewModel StateFlows           Consumed by
─────────────────────────────────────────────────────────────────────
selectedOperatingMode   ──╌╌►  MainScreen (routing), AppControlPanel
selectedLanguage        ──╌╌►  AppControlPanel, EditorScreen, screens
tcpConnectionState      ──╌╌►  AppControlPanel, MonitorControlPanel
dataState               ──╌╌►  MainScreen (guard), DataSourceScreen
isDataConfirmed         ──╌╌►  MainScreen (guard)
isDarkTheme             ──╌╌►  CardioSimulatorTheme (wraps Material3)
tcpIp / tcpPort         ──╌╌►  SettingsDialog
isUploading             ──╌╌►  EditorScreen (progress indicator)
lastAck                 ──╌╌►  MonitorControlPanel (feedback display)
dirtyParts              ──╌╌►  EditorScreen (unsaved indicator)
dirtySeries             ──╌╌►  EditorScreen (unsaved indicator)

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
                               EditorScreen (waveform preview)
allSeries               ──╌╌►  SeriesInspector (list of series to edit)
allParts                ──╌╌►  AnchorInspector (list of parts to edit)
```

### UI component ownership tree

```
CardioSimulatorTheme
└── MainScreen
    ├── [guard] DataSourceScreen
    │     └── SAF file picker ──► AppViewModel.setDataFolder()
    ├── [overlay] SettingsDialog
    │     ├── reads/writes MonitorViewModel.monitorMode
    │     └── reads/writes AppViewModel.{isDarkTheme, selectedLanguage, tcpIp, tcpPort}
    ├── AppControlPanel
    │     ├── reads AppViewModel.{selectedOperatingMode, tcpConnectionState, selectedLanguage}
    │     └── calls AppViewModel.{setOperatingMode, toggleTcpConnection, setLanguage}
    └── [mode screen]  ← one of five, keyed by OperatingMode
          ├── RhythmChoosingPanel          (Teaching / Testing / Editor / OSKE)
          │     ├── reads  RhythmViewModel.{rhythms, selectedRhythm}
          │     └── calls  RhythmViewModel.selectRhythm()
          ├── MonitorControlPanel          (Teaching / Testing / Examination / OSKE)
          │     ├── reads  AppViewModel.tcpConnectionState, lastAck
          │     ├── reads  MonitorViewModel.monitorMode
          │     └── calls  AppViewModel.sendStartCommand / sendStopCommand
          │                MonitorViewModel.setIsRunning
          ├── TestingControlPanel          (Testing only)
          ├── TeachingControlPanel         (Teaching only)
          ├── Monitor                      ← display
          │     ├── reads MonitorViewModel.monitorMode (speed, scale, grid, running)
          │     └── provides LocalPixelScale to subtree
          ├── LeadsGrid
          │     └── Lead  ×N
          │           ├── reads RhythmViewModel.waveforms[lead] → Points
          │           └── reads LocalPixelScale (PixelScale + EcgCalibration)
          │
          └── [Editor only]
                ├── AnchorInspector
                │     ├── reads  AppViewModel.{editablePart, dirtyParts}
                │     ├── calls  AppViewModel.mutatePart / undoPart
                │     └── uses   AnchorClipboard.object (cut / paste)
                ├── SeriesInspector
                │     ├── reads  RhythmViewModel.{allSeries, allParts}
                │     ├── reads  AppViewModel.dirtySeries
                │     └── calls  AppViewModel.mutateSeries / undoSeries
                ├── ChartCanvas
                │     └── renders EditablePart.bakedSamples() → FloatArray
                ├── AnchorHandleOverlay
                │     └── drag ──► AppViewModel.mutatePart { ep.anchors[i] = newAnchor }
                ├── CalibrationPulse
                │     └── reads LocalPixelScale
                ├── PreviewPane
                └── BlockTimeline
                      └── reads EditableSeries.partRefs
```

---

## 1. Package layout

| Package | Role |
|---|---|
| `com.example.cardiosimulator` | `MainActivity` — Compose entry point |
| `…domain` | Pure-Kotlin models: app state, ECG data, editor mutation, derived leads, anchor baking |
| `…data` | Storage / persistence: `EcgRepository`, `EcgSource` family, `DataSourcePrefs`, ZIP I/O, calibration, pixel scaling |
| `…network` | TCP layer: `TcpProtocol` (encode/decode), `TcpMessage` sealed hierarchy, `TcpConnectionState` |
| `…ui.viewmodels` | Three `ViewModel`s: `AppViewModel`, `MonitorViewModel`, `RhythmViewModel` |
| `…ui.screens` | Top-level Composables, one per `OperatingMode` + `MainScreen` + `DataSourceScreen` + `SettingsScreen` |
| `…ui.panels` | Side / control panels reused across screens |
| `…ui.display` | Waveform render Composables: `Monitor`, `Lead`, `LeadsGrid`, `EditableLead` |
| `…ui.components` | Lower-level visual building blocks (canvas, anchors, tabs, labels) |
| `…ui.theme` | Material3 theme, colors, typography |
| `…ui.utils` | Misc helpers |

---

## 2. Domain layer (`…domain`)

Pure-Kotlin, no Android imports (except a couple of `@StringRes` references).
These types are the “source of truth” the UI and view-models manipulate.

### Application state

| Type | Kind | Role | Depends on |
|---|---|---|---|
| `OperatingMode` | enum | `Teaching`, `Testing`, `Examination`, `OSKE`, `Editor` | — |
| `OperatingModeModel` | data class | One entry per `OperatingMode` (id + description) | `OperatingMode` |
| `AppBuilder` | class | Builder collecting `OperatingModeModel`s, produces `AppStateModel` | `OperatingModeModel`, `AppStateModel` |
| `AppStateModel` | data class (mutable) | Holds selected mode, language, TCP target | `OperatingModeModel`, `Language` |
| `Language` | enum | `EN`, `RU`, `ZH`, `ES` with locale tags | — |

### ECG data model

| Type | Kind | Role |
|---|---|---|
| `Lead` | enum | 12-lead identifiers (`I`, `II`, …, `V6`) |
| `AnchorPoint` | data class | `(x, y)` — editor source-of-truth |
| `BlockFlags` | value class | Bitmask flags on a `SeriesPartRef` (FREQUENTLY, WITHSOUND, …) |
| `SourceSpec` | data class | Parsed `source:` block: lead/pathology/max/value/center/anchors/seriesRefs |
| `SeriesPartRef` | data class | Reference from a series to a part with `(x,y,partIdenty,offset,flags)` |
| `WaveformPart` | data class | A part: identy, lead, samples, duration, `aMax/aValue`, optional `source` |
| `EcgSeries` | data class | A series: identy, displayName, lead, pathology, list of `SeriesPartRef` |
| `EcgFileFormat` | internal object | Parser / serializer for the RP5-compatible key:value text format |

### Editor model

| Type | Role | Used by |
|---|---|---|
| `EditablePart` | Mutable mirror of `WaveformPart`; anchors are authoritative, samples cached | `AppViewModel`, `EditorScreen`, `AnchorInspector` |
| `EditableSeries` | Mutable mirror of `EcgSeries`; in-place mutation of `partRefs` | `AppViewModel`, `SeriesInspector`, `BlockTimeline` |
| `UndoStack<T>` | Capped per-record snapshot stack | `AppViewModel.partUndo` / `seriesUndo` |
| `AnchorClipboard` | App-wide `object` (singleton) for cut/paste of anchor lists | `EditorScreen`, `AnchorInspector` |
| `PartNamer` | App-wide `object` for `MakeTitle` / `MakeIdenty` helpers | Editor flows |
| `AnchorBaking` (top-level `bakeAnchorsToSamples()`) | Straight-line baking of anchors → samples | `EditablePart.bakedSamples()` |
| `DerivedLeads` | `object` — Einthoven/Goldberger + V-projection math | `AppViewModel.generateDerivedLimbLeads()` / `generateDerivedPrecordialLeads()` |

---

## 3. Data layer (`…data`)

### Source abstraction

```
        ┌──────────────────┐
        │   EcgSource      │   interface
        └──┬───────────────┘
           │ implemented by
           │
   ┌───────┴─────────┐
   │                 │
┌──▼──────────┐  ┌───▼────────────┐
│AssetEcgSource│  │FileEcgSource   │   read-write, used in Editor
│ (read-only) │  │ (extracted ZIP)│
└─────────────┘  └────────────────┘
```

| Type | Role | Notes |
|---|---|---|
| `EcgSource` | Interface: `listSeries / listParts / readSeries / readPart / writeSeries / writePart / isWritable / rootDir` | Storage-agnostic |
| `AssetEcgSource` | Reads bundled `Series/` and `Parts/` from APK assets | Read-only; default at boot |
| `FileEcgSource` | Reads/writes a directory in app `filesDir/unzipped_ecg` | Auto-detects `Series/Parts` subdirs; atomic write via temp file |
| `EcgRepository` | Holds the current `EcgSource`, indexes `series` & `parts`, groups pathologies, assembles waveforms | Owns `seriesIndex`, `partsIndex`; `setSource()` swaps in a new `EcgSource` |
| `PathologyGroup` | Pathology + per-lead series-identy map | Returned by `EcgRepository.pathologies()` |

### Persistence

| Type | Role |
|---|---|
| `DataSourcePrefs` | DataStore wrapper: `treeUri`, `languageTag`, `tcpIp`, `tcpPort`, `isDarkTheme`, `gridScheme` |
| `ZipDecompressor` | `object` — unzips a SAF `Uri` into `filesDir/unzipped_ecg` with charset auto-detection |
| `ZipCompressor` | `object` — repackages `filesDir/unzipped_ecg` to a SAF `Uri` (or cache file for TCP upload) |

### Calibration / scaling

| Type | Role |
|---|---|
| `EcgCalibration` | Physical constants: gain mm/mV, sample rate, ADC counts/mV |
| `PixelScale` | Derives `pxPerMm / pxPerMv / pxPerSec / pxPerSample` from `EcgCalibration` + density; has Editor-only `sourceAnchored` builder |
| `LocalPixelScale` | `CompositionLocal` providing `PixelScale` down the Compose tree |
| `Points` | Wrapper around `List<Float>` (sample buffer for display) |

---

## 4. Network layer (`…network`)

| Type | Role |
|---|---|
| `TcpConnectionState` | Sealed: `Disconnected` / `Connecting` / `Connected` / `Error(msg)` |
| `TcpMessage` | Sealed hierarchy: `StartCommand`, `StopCommand`, `PointsMessage`, `UploadMessage`, `AckMessage` |
| `TcpProtocol` | `object` — JSON encode/decode + line-framing helper; throws `TcpProtocolException` |
| `TcpProtocolException` | Wraps invalid JSON or missing fields |

The TCP socket itself lives inside `AppViewModel.connectionJob` / `tcpSocket`; the network package is a pure protocol module.

---

## 5. View-model layer (`…ui.viewmodels`)

### AppViewModel — the central hub

`AppViewModel(appState, ecgRepository?, appContext?, prefs?, tcpReconnectIntervalMs)`

Holds:
- App-wide `StateFlow`s: `selectedLanguage`, `selectedOperatingMode`, `tcpIp / tcpPort`, `isDarkTheme`, `tcpConnectionState`, `dataState`, `isDataConfirmed`, `isUploading`, `lastAck`, `exportResult`, `dirtyParts`, `dirtySeries`.
- Editor working copies: `editableParts`, `editableSeries`, `partUndo`, `seriesUndo`.
- TCP socket lifecycle: `connectTcp / disconnectTcp / toggleTcpConnection / dismissTcpError`.
- Data-source lifecycle: `setDataFolder → loadFromSaf → ZipDecompressor.unzip → FileEcgSource → EcgRepository.setSource → reload`.
- Editor API: `editablePart / editableSeries / mutatePart / mutateSeries / undoPart / undoSeries / saveAll / discardEdits / exportZip`.
- Derived-lead generators using `DerivedLeads`.
- Manual upload (`uploadEditedData`) — re-zips current dir and sends over the existing TCP socket.

Dependencies in (constructor):
- `AppStateModel` (domain)
- `EcgRepository` (data)
- `DataSourcePrefs` (data)
- Android `Context` (only for SAF / DataStore / filesDir)

Dependencies out:
- emits to UI via `StateFlow`
- talks to `TcpProtocol`, `TcpMessage`
- mutates `EditablePart` / `EditableSeries`

### MonitorViewModel

`MonitorViewModel(prefs?)`

- One `StateFlow<MonitorModeModel>`.
- Setters: `setSeriesCount / setSeriesScheme / setGridScheme / setSpeed / setScale / setCalibration / setDisplayScale / setIsRunning`.
- Persists `gridScheme` through `DataSourcePrefs`.
- Re-created per `OperatingMode` (keyed by `selectedMode.id.name` in `MainScreen`).

### RhythmViewModel

`RhythmViewModel(ecgRepository)`

- `StateFlow`s: `rhythms`, `selectedRhythm`, `waveforms`, `allSeries`, `allParts`.
- `loadData()` pulls from `EcgRepository`; `selectRhythm(pathology)` assembles per-lead `Points` via `EcgRepository.assembleWaveform`.
- Recreated per `OperatingMode` keyed by `"<mode>_rhythm"`.

---

## 6. UI layer (Compose)

### Activity / entry point

```
MainActivity.onCreate
 ├─ AppBuilder().addMode(...).build(initial = Editor)   ──▶ AppStateModel
 ├─ EcgRepository(AssetEcgSource(assets))               ──▶ default repo
 ├─ DataSourcePrefs(context)                            ──▶ persistence
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
        Editor       → EditorScreen(     viewModel, monitorViewModel, rhythmViewModel)
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
| `EditorScreen` | `RhythmChoosingPanel`, `AnchorInspector`, `SeriesInspector` (via tabs) | `Monitor`, `CalibrationPulse`, `ChartCanvas`, `AnchorHandleOverlay`, `PreviewPane`, `BlockTimeline` |
| `MainScreen` (top bar) | `AppControlPanel` | — |

### Components (`…ui.components`)

Reusable visual primitives consumed by screens / display:

`AnchorCanvas` (`AnchorHandleOverlay`, `AnchorSpace`), `AutoResizeText`, `BlockTimeline`,
`CalibrationPulse`, `ChartCanvas` (incl. `chartArea`), `ControlPanelDivider`,
`Label`, `Modifers`, `PreviewPane`, `ReferenceOverlay`, `Tab` / `PaperGridLegend`.

### Display (`…ui.display`)

`Monitor` — paper grid + scrolling chrome, takes a `MonitorViewModel`.
`LeadsGrid` — arranges N `Lead` cells in row × column grid.
`Lead` — renders one lead’s `Points` (view-only).
`EditableLead` — renders one lead with anchor overlay (Editor mode).
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
| `AnchorClipboard` | `object` singleton | Process |
| `EcgFileFormat`, `DerivedLeads`, `PartNamer`, `TcpProtocol`, `ZipCompressor`, `ZipDecompressor` | `object` singletons | Process |

---

## 8. Data-flow walkthroughs

### App launch with previously chosen folder

```
MainActivity
  → AppViewModel.init { prefs.treeUri.first() }
  → AppViewModel.loadFromSaf(ctx, savedUri)
      → if FileEcgSource(targetDir).isValid() reuse it
      → else ZipDecompressor.unzip(ctx, uri, targetDir)
      → EcgRepository.setSource(FileEcgSource(targetDir))
      → EcgRepository.load()       (reads Series/Parts via EcgFileFormat)
  → _dataState = Ready(seriesCount, partsCount)
  → MainScreen sees Ready → dispatches to mode screen
```

### Editing an anchor in Editor mode

```
EditorScreen → AnchorHandleOverlay (drag)
   ↓
AppViewModel.mutatePart(identy) { ep.anchors[i] = newAnchor }
   ↓
partUndo[identy].push(snapshot)
ep.samples = null           (invalidate baked cache)
_dirtyParts += identy
   ↓
bakedPoints remember key changes → EditablePart.bakedSamples() rebakes
   ↓ ChartCanvas(bakedPoints) redraws waveform; AnchorHandleOverlay redraws handles
   ↓
On save: AppViewModel.saveAll()
   → for each dirty id → EcgFileFormat.writePart(ep.toWaveformPart())
   → FileEcgSource.writePart(name, text)
```

### Sending a Start command over TCP

```
MonitorControlPanel button
   → AppViewModel.sendStartCommand(pathology, name)
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
                                └── OperatingModeModel × 5
            ──► EcgRepository(AssetEcgSource)
                  ├── EcgSource ◄implements AssetEcgSource (boot)
                  └── EcgSource ◄implements FileEcgSource  (after SAF pick)
            ──► DataSourcePrefs
            ──► AppViewModel(AppStateModel, EcgRepository, Context, DataSourcePrefs)
                  ├── TcpProtocol.object  ──► TcpMessage (encode/decode)
                  ├── java.net.Socket     (tcpSocket)
                  ├── ZipDecompressor.object
                  ├── ZipCompressor.object
                  ├── EditablePart        (mutable, per identy)
                  │     └── AnchorBaking (bakeAnchorsToSamples)
                  ├── EditableSeries      (mutable, per identy)
                  ├── UndoStack<EditablePart>   (partUndo)
                  ├── UndoStack<EditableSeries> (seriesUndo)
                  ├── DerivedLeads.object (Einthoven / Goldberger math)
                  └── EcgFileFormat.object (serialize on save)

MainScreen ──► AppViewModel
          ──► MonitorViewModel(DataSourcePrefs)
                └── MonitorModeModel ──► EcgCalibration
                                    ──► GridScheme / SeriesScheme
          ──► RhythmViewModel(EcgRepository)
                └── EcgRepository ──► PathologyGroup
                                  ──► Points (assembled waveform buffers)

Screen* ──► Panels*  ──► AppViewModel  (calls mutate / undo / save / TCP)
                     ──► MonitorViewModel (setSpeed / setScale / setGridScheme)
                     ──► RhythmViewModel  (selectRhythm / loadData)
Screen* ──► display.Monitor / Lead / LeadsGrid / EditableLead
              └── components.{ChartCanvas, AnchorHandleOverlay, CalibrationPulse, …}
              └── LocalPixelScale (CompositionLocal) ──► PixelScale ──► EcgCalibration

──── STATEFLOW EMISSIONS (╌╌►) ───────────────────────────────────────────────

AppViewModel    ╌╌► selectedOperatingMode  → MainScreen, AppControlPanel
                ╌╌► selectedLanguage       → AppControlPanel, Screens
                ╌╌► tcpConnectionState     → AppControlPanel, MonitorControlPanel
                ╌╌► dataState              → MainScreen (guard), DataSourceScreen
                ╌╌► isDataConfirmed        → MainScreen (guard)
                ╌╌► isDarkTheme            → CardioSimulatorTheme
                ╌╌► dirtyParts             → EditorScreen (unsaved indicator)
                ╌╌► dirtySeries            → EditorScreen (unsaved indicator)
                ╌╌► lastAck                → MonitorControlPanel
                ╌╌► isUploading            → EditorScreen

MonitorViewModel╌╌► monitorMode            → Monitor, MonitorControlPanel, SettingsDialog

RhythmViewModel ╌╌► rhythms                → RhythmChoosingPanel (list)
                ╌╌► selectedRhythm         → RhythmChoosingPanel (highlight)
                ╌╌► waveforms              → LeadsGrid → Lead (render per-lead Points)
                ╌╌► allSeries              → SeriesInspector
                ╌╌► allParts               → AnchorInspector

──── SINGLETONS (object / process lifetime) ──────────────────────────────────

AnchorClipboard    ← used by AnchorInspector / EditorScreen  (cut / paste)
PartNamer          ← used by Editor flows  (MakeTitle / MakeIdenty)
DerivedLeads       ← used by AppViewModel  (12-lead math)
EcgFileFormat      ← used by WaveformPart.parse / EcgSeries.parse / AppViewModel.saveAll
TcpProtocol        ← used by AppViewModel  (JSON encode / decode)
ZipCompressor      ← used by AppViewModel  (export / TCP upload)
ZipDecompressor    ← used by AppViewModel  (SAF import → filesDir)
```

`*` = one of the five `OperatingMode` variants.

---

_Last regenerated from source on the working tree under_
`app/src/main/java/com/example/cardiosimulator/`.
