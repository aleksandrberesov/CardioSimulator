# CardioSimulator — Application Structure & Dependencies

This document maps the classes, objects, and instances of the
CardioSimulator Android app and the wiring between them. It describes the
**current architecture**, built around the pathology dataset format
documented in [data-structure.md](data-structure.md): one `.dat` file per
pathology, all 12 leads inside, raw ADC samples — no anchors, no
part/series indirection, no per-record calibration.

The codebase is a layered MVVM-with-Compose viewer and editor for that
dataset. The editor renders through the **same** `Points → ChartCanvas`
pipeline as the viewer, adding per-sample drag handles
(`SampleHandleOverlay`).

```
                ┌─────────────────────────────────────┐
                │      UI (Jetpack Compose)           │
                │  screens / panels / display /       │
                │  components / theme                 │
                └──────────────┬──────────────────────┘
                               │ collectAsState / events
                ┌──────────────▼──────────────────────┐
                │           ViewModels                │
                │  AppViewModel · MonitorViewModel ·  │
                │  RhythmViewModel · EditorViewModel  │
                └──────────────┬──────────────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
   ┌────────┐            ┌──────────────────┐   ┌──────────┐
   │ domain │            │       data       │   │ network  │
   │ models │◀───────────│ PathologyRepo    │   │ TcpProto │
   │ /math  │            │ PathologySource  │   │ TcpMsg   │
   └────────┘            │ Prefs · Zip*     │   │ Socket   │
                         │ PixelScale · Cal │   └──────────┘
                         └──────────────────┘
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
║     │  builds via AppBuilder.addMode(…).build(initialMode = Teaching)        ║
║     ├──────────────────────────────────► AppStateModel                       ║
║     │      (operatingModes list, selected mode, language, tcp ip/port)       ║
║     │                                                                        ║
║     │  creates directly                                                      ║
║     ├──────────────────────────────────► PathologyRepository(               ║
║     │                                        AssetPathologySource(assets))   ║
║     │                                                                        ║
║     │  creates directly                                                      ║
║     ├──────────────────────────────────► DataSourcePrefs(appContext)         ║
║     │                                                                        ║
║     │  creates via Compose viewModel { factory }                             ║
║     └──────────────────────────────────► AppViewModel(appState, repository,  ║
║                                                appContext, prefs)            ║
╚═══════════════════════╤══════════════════════════════════════════════════════╝
                        │ passes AppViewModel down
                        ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  VIEWMODEL LAYER                                                             ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ AppViewModel                                  (central hub)         │    ║
║  │                                                                     │    ║
║  │  Injected dependencies (all nullable except appState):              │    ║
║  │    AppStateModel  ◄─ operatingModes, selected mode/lang/tcp         │    ║
║  │    PathologyRepository? (public `repository`) ◄─ setSource /        │    ║
║  │                          loadManifest / pathologies / readPathology │    ║
║  │    Context? (appContext) ◄─ SAF resolver, filesDir, DataStore       │    ║
║  │    DataSourcePrefs? (public `prefs`) ◄─ treeUri/lang/tcp/theme      │    ║
║  │    tcpReconnectIntervalMs : Long = 5000                             │    ║
║  │                                                                     │    ║
║  │  Internal collaborators (owned / called):                           │    ║
║  │    TcpProtocol.encode/decodeOrNull ──► TcpMessage hierarchy         │    ║
║  │    java.net.Socket (tcpSocket) ─ connect / read lines / write       │    ║
║  │    ZipCompressor.zipToCache ──► upload snapshot over TCP            │    ║
║  │    ZipCompressor.zip ──► exportZip to SAF destination               │    ║
║  │    PathologyZipExtractor.extract ──► FilePathologySource → repo     │    ║
║  │                                                                     │    ║
║  │  Emitted StateFlows (consumed by UI via collectAsState):            │    ║
║  │    selectedOperatingMode : StateFlow<OperatingModeModel>            │    ║
║  │    selectedLanguage      : StateFlow<Language>                      │    ║
║  │    tcpIp / tcpPort       : StateFlow<String> / <Int>               │    ║
║  │    isDarkTheme           : StateFlow<Boolean>  (default true)       │    ║
║  │    tcpConnectionState    : StateFlow<TcpConnectionState>            │    ║
║  │    dataState             : StateFlow<DataState>                     │    ║
║  │    isDataConfirmed       : StateFlow<Boolean>                       │    ║
║  │    lastAck               : StateFlow<TcpMessage.AckMessage?>        │    ║
║  │                            (set on ACK; not yet read by any UI)     │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ MonitorViewModel(prefs: DataSourcePrefs?)                           │    ║
║  │                                                                     │    ║
║  │  Domain:  MonitorModeModel (immutable, copied on each setter)       │    ║
║  │           EcgCalibration (embedded in MonitorModeModel)            │    ║
║  │           GridScheme / SeriesScheme enums                          │    ║
║  │  Emits:   monitorMode : StateFlow<MonitorModeModel>                 │    ║
║  │             (count · gridScheme · seriesScheme · speed · scale ·    │    ║
║  │              displayScale · calibration · isRunning)                │    ║
║  │  Setters: setSeriesCount / setSeriesScheme / setGridScheme(persist?)│    ║
║  │           setSpeed / setScale / setCalibration / setDisplayScale /  │    ║
║  │           setIsRunning   (gridScheme persisted via prefs)           │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ RhythmViewModel(repository: PathologyRepository)                   │    ║
║  │  Emits:   rhythms        : StateFlow<List<PathologyEntry>>          │    ║
║  │           selectedRhythm : StateFlow<PathologyEntry?>               │    ║
║  │           waveforms      : StateFlow<Map<Lead, Points>>             │    ║
║  │  Actions: loadManifest() (+ nameRu enrichment from .dat) /          │    ║
║  │           selectRhythm(id) / refresh()                              │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ EditorViewModel(repository: PathologyRepository)                   │    ║
║  │  Holds:   targetFile : State<PathologyFile?> (Compose State)        │    ║
║  │  Emits:   focusedLead : StateFlow<Lead> (default II)                │    ║
║  │           dirtyLeads  : StateFlow<Set<Lead>>                        │    ║
║  │           isSaving    : StateFlow<Boolean>                          │    ║
║  │  Actions: selectPathology(id) / selectLead(lead) /                  │    ║
║  │           setSample(lead, index, adcValue) / revertLead(lead) /     │    ║
║  │           save()  → repository.writePathology()                     │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
╚═══════════════════════╤══════════════════════════════════════════════════════╝
                        │ StateFlows collected by Composables
                        ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  UI LAYER (Jetpack Compose)                                                  ║
║                                                                              ║
║  MainScreen(AppViewModel)                                                    ║
║    │  creates (keyed by mode.id.name)                                        ║
║    ├──► MonitorViewModel(prefs = appViewModel.prefs)                         ║
║    │  creates (keyed by mode + "_rhythm")                                    ║
║    ├──► RhythmViewModel(appViewModel.repository!!)                           ║
║    │  creates (keyed by mode + "_editor")                                    ║
║    ├──► EditorViewModel(appViewModel.repository!!)                           ║
║    │                                                                         ║
║    │  observes: selectedOperatingMode · dataState · isDataConfirmed          ║
║    │                                                                         ║
║    ├── [guard] if !isDataConfirmed or dataState ∈ {NotConfigured,            ║
║    │       Loading, Error}:                                                  ║
║    │       DataSourceScreen(App, Rhythm, state)                              ║
║    │         └─ SAF OpenDocument ──► AppViewModel.setDataFolder()           ║
║    │                                                                         ║
║    ├── [overlay] if showSettings:                                           ║
║    │       SettingsDialog(Monitor, App) → SettingsContent                    ║
║    │         └─ theme · grid · language · TCP · change folder · export      ║
║    │                                                                         ║
║    ├── TopControlPanel(App)                     ← top bar                     ║
║    │     ├─ mode dropdown                                                    ║
║    │     └─ per-mode panel: Teaching/Testing/Editor                          ║
║    │                                                                         ║
║    ├── BottomControlPanel(onSettingsClick)      ← bottom bar                  ║
║    │     └─ Settings gear                                                    ║
║    │                                                                         ║
║    └── when selectedMode.id:   ← mode screen (weight 15f)                    ║
║         Teaching     → TeachingScreen(App, Monitor, Rhythm)                  ║
║         │   ├─ RhythmChoosingPanel(App, rhythms, onRhythmSelect)            ║
║         │   ├─ Monitor → LeadsGrid → Lead  ◄── waveforms                    ║
║         │   └─ MonitorControlPanel(Monitor, onStartStopClick)               ║
║         │        └─ sendStartCommand / sendStopCommand                      ║
║         Testing      → TestingScreen(App, Monitor, Rhythm)                   ║
║         │   ├─ Monitor → LeadsGrid → Lead  (no rhythm panel)                ║
║         │   └─ MonitorControlPanel(Monitor, onStartStopClick)               ║
║         Examination  → ExaminationScreen   (empty stub)                      ║
║         OSKE         → OSKEScreen          (empty stub)                      ║
║         Editor       → EditorScreen(App, Monitor, Rhythm, Editor)            ║
║             ├─ RhythmChoosingPanel ──► EditorViewModel.selectPathology      ║
║             ├─ toolbar: Save / Revert Lead (when dirtyLeads ≠ ∅)            ║
║             ├─ ScrollableTabRow over Lead.entries (dirty leads in red)      ║
║             └─ Monitor → EditableLead → ChartCanvas + SampleHandleOverlay   ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### StateFlow data flow (reactive bindings)

```
AppViewModel StateFlows           Consumed by
─────────────────────────────────────────────────────────────────────
selectedOperatingMode   ──╌╌►  MainScreen (routing), TopControlPanel
selectedLanguage        ──╌╌►  RhythmChoosingPanel, EditorScreen,
                               SettingsContent
tcpIp / tcpPort         ──╌╌►  SettingsContent
isDarkTheme             ──╌╌►  MainActivity (theme), SettingsContent
tcpConnectionState      ──╌╌►  SettingsContent (status dot + connect)
dataState               ──╌╌►  MainScreen (guard), DataSourceScreen
isDataConfirmed         ──╌╌►  MainScreen (guard)
lastAck                 ──╌╌►  (none yet — set on ACK, no UI reader)

MonitorViewModel StateFlows
─────────────────────────────────────────────────────────────────────
monitorMode             ──╌╌►  Monitor (pxPerMm, speed, scale, grid, running)
                               MonitorControlPanel (count/scheme/speed/scale)
                               SettingsContent (grid scheme)

RhythmViewModel StateFlows
─────────────────────────────────────────────────────────────────────
rhythms                 ──╌╌►  RhythmChoosingPanel (list), TeachingScreen
selectedRhythm          ──╌╌►  Teaching/Testing (start cmd args)
waveforms               ──╌╌►  LeadsGrid → Lead (render Points per lead)

EditorViewModel StateFlows / State
─────────────────────────────────────────────────────────────────────
targetFile              ──╌╌►  EditorScreen (toolbar title, focused lead)
focusedLead             ──╌╌►  EditorScreen (tab selection, EditableLead)
dirtyLeads              ──╌╌►  EditorScreen (Save/Revert + red tab labels)
isSaving                ──╌╌►  EditorViewModel-internal (save guard)
```

### UI component ownership tree

```
CardioSimulatorTheme (MainActivity, darkTheme = isDarkTheme)
└── MainScreen
    ├── [guard]   DataSourceScreen
    │     └── SAF OpenDocument ──► AppViewModel.setDataFolder()
    ├── [overlay] SettingsDialog → SettingsContent
    │     ├── theme chips        ──► AppViewModel.updateDarkTheme
    │     ├── grid-scheme chips  ──► MonitorViewModel.setGridScheme
    │     ├── language chips     ──► AppViewModel.updateLanguage
    │     ├── TCP ip/port + link ──► updateTcpConnection / toggleTcpConnection
    │     ├── change folder      ──► AppViewModel.setDataFolder
    │     └── export ZIP         ──► AppViewModel.exportZip
    ├── TopControlPanel                              ← top bar (weight 2f)
    │     ├── mode dropdown      ──► updateOperatingMode
    │     ├── TeachingControlPanel  (Teaching; education-program dropdown)
    │     └── TestingControlPanel   (Testing; placeholder label + pause)
    ├── [mode screen]  ← one of five (weight 15f)
    │     ├── RhythmChoosingPanel        (Teaching, Editor, details dialog)
    │     │     ├── reads  selectedLanguage + rhythms
    │     │     └── calls  onRhythmSelect (selectRhythm / selectPathology)
    │     ├── MonitorControlPanel        (Teaching, Testing)
    │     │     ├── reads  MonitorViewModel.monitorMode
    │     │     └── calls  setSeriesCount/Scheme, setSpeed/Scale,
    │     │                setIsRunning, onStartStopClick
    │     ├── Monitor                    ← display wrapper
    │     │     ├── reads MonitorViewModel.monitorMode
    │     │     ├── computes PixelScale, provides LocalPixelScale
    │     │     └── ekgGrid + pinch-zoom/drag (graphicsLayer)
    │     ├── LeadsGrid → Lead  ×N       (viewer modes)
    │     │     ├── reads RhythmViewModel.waveforms[lead] → Points
    │     │     └── Lead: CalibrationPulse + label + ChartCanvas
    │     └── EditableLead               (Editor only)
    │           ├── reads EditorViewModel.targetFile[focusedLead]
    │           └── ChartCanvas + SampleHandleOverlay ──► setSample
    └── BottomControlPanel                           ← bottom bar (weight 1f)
          └── Settings gear      ──► onSettingsClick
```

---

## 1. Package layout

| Package | Role |
|---|---|
| `com.example.cardiosimulator` | `MainActivity` — Compose entry point |
| `…domain` | Pure-Kotlin models: app state, pathology / lead models + parser, derived-lead math |
| `…data` | Storage / persistence: `PathologyRepository`, `PathologySource` family, `DataSourcePrefs`, ZIP I/O, `Points`, calibration, pixel scaling |
| `…network` | TCP layer: `TcpProtocol` (JSON encode/decode), `TcpMessage` sealed hierarchy, `TcpConnectionState` |
| `…ui.viewmodels` | `AppViewModel`, `MonitorViewModel`, `RhythmViewModel`, `EditorViewModel`, `DataState` |
| `…ui.screens` | `MainScreen`, one screen per `OperatingMode`, `DataSourceScreen`, `SettingsScreen` (dialog), plus `Modifiers.kt` (section/scrollbar modifiers) |
| `…ui.panels` | `TopControlPanel`, `BottomControlPanel`, `MonitorControlPanel`, `RhythmChoosingPanel`, `TeachingControlPanel`, `TestingControlPanel` |
| `…ui.display` | Waveform render Composables: `Monitor`, `Lead`, `EditableLead`, `LeadsGrid`, `Modifers.kt` (`ekgGrid`) |
| `…ui.components` | Lower-level visuals: `ChartCanvas`, `CalibrationPulse`, `SampleHandleOverlay`, `Tab`, `Label`, `AutoResizeText`, `ControlPanelDivider` |
| `…ui.theme` | Material3 theme, colors, typography |
| `…ui.utils` | Misc helpers (`StringUtils`) |

---

## 2. Domain layer (`…domain`)

Pure-Kotlin, no Android imports except a couple of `@StringRes` references.
These types are the source of truth the UI and view-models manipulate.

### Application state

| Type | Kind | Role | File |
|---|---|---|---|
| `OperatingMode` | enum | `Teaching`, `Testing`, `Examination`, `OSKE`, `Editor` (each carries a `@StringRes titleRes`) | `OperatingModeModel.kt` |
| `OperatingModeModel` | data class | `(id: OperatingMode, description: String)` | `OperatingModeModel.kt` |
| `AppBuilder` | class | Collects `OperatingModeModel`s, `build(initialMode?)` → `AppStateModel` | `AppBuilder.kt` |
| `AppStateModel` | data class (mutable) | Selected mode, language, TCP ip/port (defaults `192.168.1.100:8080`); `updateMode/Language/TcpConnection` | `AppStateModel.kt` |
| `Language` | enum | `EN`, `RU`, `ZH`, `ES` with locale tags + `fromTag` | `AppStateModel.kt` |
| `GridScheme` | enum | `Pink`, `BlueGray` (`@StringRes labelRes`) | `MonitorModeModel.kt` |
| `SeriesScheme` | enum | `OneColumn`, `TwoColumn`, `Grid` | `MonitorModeModel.kt` |
| `MonitorModeModel` | data class | `count, gridScheme, seriesScheme, speed=25, scale=1, displayScale=0.4, calibration, isRunning` | `MonitorModeModel.kt` |

### Pathology / waveform model (`Pathology.kt`)

All of these live in a single file, `domain/Pathology.kt`. They mirror the
flat on-disk shape — no `WaveformPart`, no `EcgSeries`, no `SeriesPartRef`.

| Type | Kind | Role |
|---|---|---|
| `Lead` | enum | 12-lead identifiers (`I … V6`) + `fromToken()` |
| `PathologyManifest` | data class | `(version, baseline, leadOrder, entries)`; `SUPPORTED_VERSION = "1.0"` |
| `PathologyEntry` | data class | Manifest row `(id, titleEn, nameRu, leadsCount, fileName)` |
| `LeadStream` | data class | One lead block `(lead, samples: IntArray)` — raw ADC |
| `PathologyFile` | data class | Parsed `<id>.dat` `(id, titleEn, nameRu, leads: Map<Lead, LeadStream>)` |
| `PathologyParser` | `object` | Parse/serialize `manifest.txt` and `<id>.dat` (UTF-8, `key:value`, blank-line-separated blocks; `FormatException` on bad input) |
| `DerivedLeads` | `object` | Einthoven/Goldberger (`combineIII_aVR_aVL_aVF`) + V-projection (`combineV1_V3_V4_V5`) used to fill missing leads |

### Display values

| Type | Kind | Role | File |
|---|---|---|---|
| `Points` | `data class(values: List<Float>)` | Baseline-zeroed sample buffer for one lead, ready for the renderer | `data/Points.kt` |

---

## 3. Data layer (`…data`)

### Source abstraction

```
        ┌──────────────────┐
        │ PathologySource  │   interface (read-only contract)
        └──┬───────────────┘
           │ implemented by
   ┌───────┴──────────────┐
   │                      │
┌──▼──────────────────┐ ┌─▼────────────────────┐
│ AssetPathologySource│ │ FilePathologySource  │
│ assets/Pathologies/ │ │ filesDir/pathologies │
│ (boot default, RO)  │ │ (+ writePathology)   │
└─────────────────────┘ └──────────────────────┘
```

| Type | Role | Notes |
|---|---|---|
| `PathologySource` | Interface: `readManifest() / readPathology(id) / listPathologies()` | Storage-agnostic, read-only |
| `AssetPathologySource` | Reads `assets/Pathologies/` (flat, UTF-8) | Default at boot |
| `FilePathologySource` | Reads/writes a directory (default `filesDir/pathologies`); `writePathology` is atomic (`.tmp` + rename); `isValid()` | Writable source for the editor |
| `PathologyRepository` | Holds the current `PathologySource`, caches the manifest, lazily reads pathologies, exposes `leadWaveform(id, lead)` (baseline-zeroing + `DerivedLeads` synthesis), and `writePathology` (file-backed only) | Central data gateway |
| `PathologyZipExtractor` | `object`: extracts a SAF `Pathologies.zip` into `filesDir/pathologies`, flattening nested dirs (UTF-8, no charset detection) | Used by `setDataFolder` |
| `ZipCompressor` | `object`: `zip(sourceDir, destUri)` for explicit export; `zipToCache(sourceDir)` for the TCP upload snapshot | Two distinct flows |
| `DataSourcePrefs` | DataStore (`ecg_data_source`) for `treeUri`, `languageTag`, `tcpIp`, `tcpPort`, `isDarkTheme`, `gridScheme` | Persists across reboots |
| `EcgCalibration` | `(gainMmPerMv=10, sampleRateHz=500, adcCountsPerMv=256)` | Fixed physical calibration |
| `PixelScale` | Derives `pxPerMv/pxPerSec/pxPerSample/pxPerAdcCount/grid steps` from a single `pxPerMm` anchor + `EcgCalibration`; provided via `LocalPixelScale` | See [ecg-rendering-pipeline.md](ecg-rendering-pipeline.md) |

### `DataState` lifecycle (`ui/viewmodels/AppViewModel.kt`)

```
NotConfigured ──setDataFolder──► Loading ──► Ready(pathologyCount)
                                        └──► Error(Unreadable | Empty | BadManifest)
```

`MainScreen` shows `DataSourceScreen` until `dataState == Ready` **and**
`isDataConfirmed == true`.

---

## 4. Network layer (`…network`)

A line-delimited **JSON** protocol over a raw `java.net.Socket`, owned by
`AppViewModel`.

| Type | Role |
|---|---|
| `TcpConnectionState` | `Disconnected` / `Connecting` / `Connected` / `Error(message)` |
| `TcpMessage` | Sealed hierarchy: `StartCommand(sampleRate?, params)`, `StopCommand`, `PointsMessage(lead?, identy?, offset, values)`, `UploadMessage(filename, size)`, `AckMessage(filename, bytes)` — each with a `type` tag and optional `id` |
| `TcpProtocol` | `object`: `encode/toJson`, `decode/decodeOrNull/fromJson`, `decodeFrames`; uses `org.json`; raises `TcpProtocolException` on malformed input |

Connection behavior (`AppViewModel.connectTcp`):

1. Connects with a timeout, sets `Connected`, then **auto-uploads** the
   current dataset: `ZipCompressor.zipToCache` → `UploadMessage` header +
   raw ZIP bytes (`sendUploadArchive`). This happens on every connect,
   independent of the explicit `exportZip` button.
2. Reads newline-delimited frames; an `AckMessage` updates `lastAck`.
3. On drop, retries every `tcpReconnectIntervalMs` (default 5 s) while the
   job is active. `sendStartCommand` / `sendStopCommand` write under a
   `Mutex`.

---

## 5. What changed from the legacy architecture

For readers familiar with the previous Parts/Series-based design, the
current architecture removes:

- **Legacy Editor** anchor-based design (replaced by the raw-sample
  `EditorScreen` + `EditableLead` + `SampleHandleOverlay`).
- **Editable working copies**: `EditablePart`, `EditableSeries`,
  `UndoStack<T>`, `AnchorClipboard`, `PartNamer`.
- **Anchor model**: `AnchorPoint`, `SourceSpec`, `bakeAnchorsToSamples`,
  `AnchorCanvas` / `AnchorHandleOverlay`, `PreviewPane`, `BlockTimeline`.
- **Part/series split**: `WaveformPart`, `EcgSeries`, `SeriesPartRef`,
  `BlockFlags`, `PathologyGroup`, `EcgFileFormat`.
- **Per-record calibration**: `aMax` / `aValue` / `duration` /
  `samplesPerMv` overrides on `PixelScale`.
- **Charset detection**: the new format is strictly UTF-8.

**Vestigial leftovers still in the tree** (worth knowing, candidates for
cleanup):

- `AppViewModel.lastAck` — updated on every `AckMessage`, but no UI reads it.
- `Examination` / `OSKE` mode screens are empty placeholder stubs.

What remains: a viewer and a raw-sample editor that load a
`Pathologies.zip`, present a searchable pathology list, render up to 12
leads on a paper-grid monitor, and stream playback commands (and a dataset
upload) over TCP.

---

_Source tree:_ `app/src/main/java/com/example/cardiosimulator/`.
