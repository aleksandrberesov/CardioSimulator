# CardioSimulator — Application Structure & Dependencies

This document maps the classes, objects, and instances of the
CardioSimulator Android app and the wiring between them. It describes the
**current architecture**, built around the pathology dataset format
documented in [data-structure.md](data-structure.md): one `.dat` file per
pathology, all 12 leads inside, raw ADC samples, plus an optional global
`markers:` line of significant ECG points — no anchors, no part/series
indirection, no per-record calibration.

The codebase is a layered MVVM-with-Compose viewer and constructor for that
dataset. The constructor (`OperatingMode.Constructor`) renders through the
**same** `Points → ChartCanvas` pipeline as the viewer, adding per-sample
selection/drag via `SampleHandleOverlay` and per-sample ECG-landmark
labelling via `SignificantPointOverlay`.

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
                │  RhythmViewModel ·                  │
                │  ConstructorViewModel               │
                └──────────────┬──────────────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
   ┌────────┐            ┌──────────────────┐   ┌──────────┐
   │ domain │            │       data       │   │ network  │
   │ models │◀───────────│ PathologyRepo    │   │ TcpProto │
   │ /math  │            │ PathologySource  │   │ TcpMsg   │
   │ /markers│           │ Prefs · Zip*     │   │ Socket   │
   └────────┘            │ PixelScale · Cal │   └──────────┘
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
║  │    DataSourcePrefs? (public `prefs`) ◄─ persists language/theme/    │    ║
║  │                          tcp/treeUri + per-mode monitor & rhythm    │    ║
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
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ MonitorViewModel(mode: OperatingMode, prefs: DataSourcePrefs?)      │    ║
║  │                                                                     │    ║
║  │  Domain:  MonitorModeModel (immutable, copied on each setter)       │    ║
║  │           EcgCalibration (embedded in MonitorModeModel)            │    ║
║  │           GridScheme {Pink, BlueGray, Blank}                       │    ║
║  │           SeriesScheme {OneColumn, TwoColumn, Grid}                │    ║
║  │  Emits:   monitorMode : StateFlow<MonitorModeModel>                 │    ║
║  │             (count · gridScheme · seriesScheme · speed · scale ·    │    ║
║  │              displayScale · calibration · isRunning)                │    ║
║  │  Setters: setSeriesCount / setSeriesScheme / setGridScheme /        │    ║
║  │           setSpeed / setScale / setDisplayScale / setCalibration /  │    ║
║  │           setIsRunning                                              │    ║
║  │  Persistence (per `mode.name` key, on `init` rehydrates from prefs):│    ║
║  │           every setter except setIsRunning/setCalibration takes a   │    ║
║  │           persist:Boolean=true flag and writes the new value back   │    ║
║  │           through DataSourcePrefs.setMonitor*                       │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ RhythmViewModel(repository, mode, prefs)                            │    ║
║  │  Emits:   rhythms          : StateFlow<List<PathologyEntry>>        │    ║
║  │           selectedRhythm   : StateFlow<PathologyEntry?>             │    ║
║  │           waveforms        : StateFlow<Map<Lead, Points>>           │    ║
║  │           significantPoints: StateFlow<List<SignificantPoint>>      │    ║
║  │  Actions: loadManifest()   (+ nameRu enrichment by peeking .dat,    │    ║
║  │                              + restores last selection per mode)    │    ║
║  │           selectRhythm(id, persist) / refresh()                     │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ ConstructorViewModel(repository, mode, prefs)                       │    ║
║  │  Holds:   targetFile : State<PathologyFile?> (Compose State)        │    ║
║  │  Emits:   focusedLead     : StateFlow<Lead> (default II)            │    ║
║  │           selectedIndex   : StateFlow<Int>                          │    ║
║  │           dirtyLeads      : StateFlow<Set<Lead>>                    │    ║
║  │           isMetadataDirty : StateFlow<Boolean>                      │    ║
║  │           isSaving        : StateFlow<Boolean>                      │    ║
║  │  Actions: selectPathology(id, persist) — restores last via          │    ║
║  │             prefs.lastEditorRhythmId on init                        │    ║
║  │           selectLead(lead) / selectIndex(i) / selectNext/Previous() │    ║
║  │           selectSignificantPoint(type)  — cycle to next marker     │    ║
║  │           moveSelectedUp/Down()         — ±1 ADC on focused lead   │    ║
║  │           setSample(lead, index, adc)   — gated by isLeadEditable  │    ║
║  │           toggleSignificantPoint(lead, index, type)                ║    ║
║  │           rename(title, language)       — sets titleEn or nameRu   │    ║
║  │           calculateDerivedLeads()       — fills III/aVR/aVL/aVF    │    ║
║  │             from I&II and V1/V3/V4/V5 from V2&V6                   │    ║
║  │           revertLead(lead) / save()  → repository.writePathology() │    ║
║  │  Editability: leads in DerivableFromIandII / DerivableFromV2andV6  │    ║
║  │             are read-only in the constructor (see [DerivedLeads]). │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
╚═══════════════════════╤══════════════════════════════════════════════════════╝
                        │ StateFlows collected by Composables
                        ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  UI LAYER (Jetpack Compose)                                                  ║
║                                                                              ║
║  MainScreen(AppViewModel)                                                    ║
║    │  creates (keyed by mode.id.name)                                        ║
║    ├──► MonitorViewModel(mode = selectedMode.id,                             ║
║    │                     prefs = appViewModel.prefs)                         ║
║    │  creates (keyed by mode + "_rhythm")                                    ║
║    ├──► RhythmViewModel(appViewModel.repository!!,                           ║
║    │                    mode = selectedMode.id,                              ║
║    │                    prefs = appViewModel.prefs)                          ║
║    │  creates (keyed by mode + "_editor")                                    ║
║    ├──► ConstructorViewModel(appViewModel.repository!!,                      ║
║    │                         mode = selectedMode.id,                         ║
║    │                         prefs = appViewModel.prefs)                     ║
║    │                                                                         ║
║    │  observes: selectedOperatingMode · dataState · isDataConfirmed          ║
║    │                                                                         ║
║    ├── [guard] if !isDataConfirmed or dataState ∈ {NotConfigured,            ║
║    │       Loading, Error}:                                                  ║
║    │       DataSourceScreen(App, Rhythm, state)                              ║
║    │         └─ SAF OpenDocument (zip) ──► AppViewModel.setDataFolder()      ║
║    │                                                                         ║
║    ├── [overlay] if showSettings:                                           ║
║    │       SettingsDialog(Monitor, App) → SettingsContent                    ║
║    │         └─ theme · grid · language · TCP · change folder · export      ║
║    │                                                                         ║
║    ├── TopControlPanel(App, Monitor, onStartStopClick)   ← top bar (2f)      ║
║    │     ├─ mode dropdown                                                    ║
║    │     └─ per-mode panel: TeachingControlPanel / TestingControlPanel       ║
║    │       (Examination, OSKE, Constructor render empty placeholders)        ║
║    │                                                                         ║
║    ├── [mode screen]  ← weight 15f                                          ║
║    │     Teaching     → TeachingScreen     (rhythm drawer + monitor)         ║
║    │     Testing      → TestingScreen      (monitor, no rhythm picker)       ║
║    │     Examination  → ExaminationScreen  (empty placeholder)               ║
║    │     OSKE         → OSKEScreen         (empty placeholder)               ║
║    │     Constructor  → ConstructorScreen  (editor + side drawers)           ║
║    │                                                                         ║
║    └── BottomControlPanel(onSettingsClick) { slot }      ← bottom bar (2f)   ║
║          │   slot content depends on mode:                                   ║
║          ├─ Teaching     → MonitorControlPanel(start/stop fires TCP)         ║
║          ├─ Constructor  → ConstructorControlPanel                           ║
║          └─ else         → no slot                                           ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### StateFlow data flow (reactive bindings)

```
AppViewModel StateFlows           Consumed by
─────────────────────────────────────────────────────────────────────
selectedOperatingMode   ──╌╌►  MainScreen (routing), TopControlPanel
selectedLanguage        ──╌╌►  RhythmSelector, ConstructorScreen,
                               TeachingScreen, SettingsContent
tcpIp / tcpPort         ──╌╌►  SettingsContent
isDarkTheme             ──╌╌►  MainActivity (theme), SettingsContent
tcpConnectionState      ──╌╌►  SettingsContent (status dot + connect)
dataState               ──╌╌►  MainScreen (guard), DataSourceScreen
isDataConfirmed         ──╌╌►  MainScreen (guard)

MonitorViewModel StateFlows
─────────────────────────────────────────────────────────────────────
monitorMode             ──╌╌►  Monitor (pxPerMm, speed, scale, grid, running)
                               MonitorControlPanel (count / scheme / speed / scale)
                               ConstructorControlPanel (speed / start-stop)
                               SettingsContent (grid scheme)

RhythmViewModel StateFlows
─────────────────────────────────────────────────────────────────────
rhythms                 ──╌╌►  RhythmSelector (list), TeachingScreen
selectedRhythm          ──╌╌►  Teaching / Testing (start cmd args),
                               ConstructorScreen (toolbar title)
waveforms               ──╌╌►  LeadsGrid → Lead (render Points per lead)
significantPoints       ──╌╌►  (read indirectly via ConstructorViewModel's
                                targetFile.significantPoints in editor)

ConstructorViewModel StateFlows / State
─────────────────────────────────────────────────────────────────────
targetFile              ──╌╌►  ConstructorScreen (toolbar, EditableLead),
                               ConstructorControlPanel (ADC display),
                               SignificantPointsControlPanel
focusedLead             ──╌╌►  ConstructorScreen (tab selection),
                               ConstructorControlPanel
selectedIndex           ──╌╌►  ConstructorScreen (SampleHandleOverlay),
                               ConstructorControlPanel (time/ADC fields),
                               SignificantPointSelector (highlight)
dirtyLeads              ──╌╌►  ConstructorScreen (Save/Revert + red tab labels)
isMetadataDirty         ──╌╌►  ConstructorScreen (enables Save)
isSaving                ──╌╌►  ConstructorViewModel-internal (save guard)
```

### UI component ownership tree

```
CardioSimulatorTheme (MainActivity, darkTheme = isDarkTheme)
└── MainScreen
    ├── [guard]   DataSourceScreen
    │     └── SAF OpenDocument (zip) ──► AppViewModel.setDataFolder()
    ├── [overlay] SettingsDialog → SettingsContent
    │     ├── theme chips        ──► AppViewModel.updateDarkTheme
    │     ├── grid-scheme chips  ──► MonitorViewModel.setGridScheme
    │     ├── language chips     ──► AppViewModel.updateLanguage
    │     ├── TCP ip/port + link ──► updateTcpConnection / toggleTcpConnection
    │     ├── change folder      ──► AppViewModel.setDataFolder
    │     └── export ZIP         ──► AppViewModel.exportZip
    ├── TopControlPanel                              ← top bar (weight 2f)
    │     ├── mode dropdown      ──► updateOperatingMode
    │     ├── TeachingControlPanel  (program dropdown + start/stop)
    │     └── TestingControlPanel   (placeholder label + pause)
    ├── [mode screen]  ← one of five (weight 15f)
    │     ├── TeachingScreen
    │     │     ├── SideDrawer → RhythmSelector ──► selectRhythm
    │     │     └── Monitor → LeadsGrid → Lead  ◄── waveforms
    │     ├── TestingScreen
    │     │     ├── Monitor → LeadsGrid → Lead
    │     │     └── MonitorControlPanel (sendStart/StopCommand on toggle)
    │     ├── ExaminationScreen   (empty stub)
    │     ├── OSKEScreen          (empty stub)
    │     └── ConstructorScreen
    │           ├── Toolbar: title + Rename + Generate Derived + Save + Revert
    │           ├── ScrollableTabRow over Lead.entries (dirty leads in red)
    │           ├── Monitor(staticGrid = true)
    │           │     ├── EditableLead → ChartCanvas + SignificantPointOverlay
    │           │     │   + SampleHandleOverlay  ──► setSample / selectIndex
    │           │     └── PreviewPane              ← HR=60 loop preview
    │           ├── SignificantPointPanel (right side, FilterChips per type)
    │           └── SideDrawers:
    │                 ├── RhythmSelector       ──► selectPathology
    │                 └── SignificantPointSelector ──► selectIndex
    └── BottomControlPanel                           ← bottom bar (weight 2f)
          ├── Settings gear      ──► onSettingsClick
          └── slot:
                ├─ Teaching     → MonitorControlPanel
                └─ Constructor  → ConstructorControlPanel
```

---

## 1. Package layout

| Package | Role |
|---|---|
| `com.example.cardiosimulator` | `MainActivity` — Compose entry point |
| `…domain` | Pure-Kotlin models: app state, pathology / lead models + parser, derived-lead math, ECG-point markers |
| `…data` | Storage / persistence: `PathologyRepository`, `PathologySource` family, `DataSourcePrefs`, ZIP I/O, `Points`, calibration, pixel scaling |
| `…network` | TCP layer: `TcpProtocol` (JSON encode/decode), `TcpMessage` sealed hierarchy, `TcpConnectionState` |
| `…ui.viewmodels` | `AppViewModel`, `MonitorViewModel`, `RhythmViewModel`, `ConstructorViewModel`, `DataState` |
| `…ui.screens` | `MainScreen`, one screen per `OperatingMode` (`TeachingScreen`, `TestingScreen`, `ExaminationScreen`, `OSKEScreen`, `ConstructorScreen`), `DataSourceScreen`, `SettingsScreen` (dialog), plus `Modifiers.kt` (section/scrollbar modifiers) |
| `…ui.panels` | `TopControlPanel`, `BottomControlPanel`, `MonitorControlPanel`, `ConstructorControlPanel`, `RhythmSelector`, `SignificantPointSelector`, `SignificantPointsControlPanel`, `TeachingControlPanel`, `TestingControlPanel` |
| `…ui.display` | Waveform render Composables: `Monitor`, `Lead`, `EditableLead`, `LeadsGrid`, `Modifers.kt` (`ekgGrid`) |
| `…ui.components` | Lower-level visuals: `ChartCanvas`, `CalibrationPulse`, `SampleHandleOverlay`, `SignificantPointOverlay`, `PreviewPane`, `SideDrawer`, `Tab`, `Label`, `AutoResizeText`, `ControlPanelDivider`, `RepeatingClickable` |
| `…ui.theme` | Material3 theme, colors, typography |
| `…ui.utils` | Misc helpers (`StringUtils`, point-type → display string) |

---

## 2. Domain layer (`…domain`)

Pure-Kotlin, no Android imports except a couple of `@StringRes` references.
These types are the source of truth the UI and view-models manipulate.

### Application state

| Type | Kind | Role | File |
|---|---|---|---|
| `OperatingMode` | enum | `Teaching`, `Testing`, `Examination`, `OSKE`, `Constructor` (each carries a `@StringRes titleRes`) | `OperatingModeModel.kt` |
| `OperatingModeModel` | data class | `(id: OperatingMode, description: String)` | `OperatingModeModel.kt` |
| `AppBuilder` | class | Collects `OperatingModeModel`s, `build(initialMode?)` → `AppStateModel` | `AppBuilder.kt` |
| `AppStateModel` | data class (mutable) | Selected mode, language, TCP ip/port (defaults `192.168.1.100:8080`); `updateMode/Language/TcpConnection` | `AppStateModel.kt` |
| `Language` | enum | `EN`, `RU`, `ZH`, `ES` with locale tags + `fromTag` | `AppStateModel.kt` |
| `GridScheme` | enum | `Pink`, `BlueGray`, `Blank` (`@StringRes labelRes`) | `MonitorModeModel.kt` |
| `SeriesScheme` | enum | `OneColumn`, `TwoColumn`, `Grid` | `MonitorModeModel.kt` |
| `MonitorModeModel` | data class | `count, gridScheme, seriesScheme, speed=25, scale=1, displayScale=0.4, calibration, isRunning` | `MonitorModeModel.kt` |

### Pathology / waveform model (`Pathology.kt`)

All of these live in `domain/Pathology.kt`. They mirror the flat on-disk
shape — no `WaveformPart`, no `EcgSeries`, no `SeriesPartRef`.

| Type | Kind | Role |
|---|---|---|
| `Lead` | enum | 12-lead identifiers (`I … V6`) + `fromToken()` |
| `PathologyManifest` | data class | `(version, baseline, leadOrder, entries)`; `SUPPORTED_VERSION = "1.0"` |
| `PathologyEntry` | data class | Manifest row `(id, titleEn, nameRu, leadsCount, fileName)` |
| `LeadStream` | data class | One lead block `(lead, samples: IntArray)` — raw ADC |
| `PathologyFile` | data class | Parsed `<id>.dat` `(id, titleEn, nameRu, leads: Map<Lead, LeadStream>, significantPoints: List<SignificantPoint>)` |
| `PathologyParser` | `object` | Parse/serialize `manifest.txt` and `<id>.dat` (UTF-8, `key:value`, blank-line-separated blocks; `FormatException` on bad input). The header `markers:` field decodes to `PathologyFile.significantPoints`. |
| `DerivedLeads` | `object` | Einthoven/Goldberger (`combineIII_aVR_aVL_aVF`) + V-projection (`combineV1_V3_V4_V5`) used to fill missing leads. The convenience sets `DerivableFromIandII` and `DerivableFromV2andV6` enumerate which leads the constructor treats as read-only. |

### Significant-point model (`SignificantPoint.kt`)

| Type | Kind | Role |
|---|---|---|
| `EcgPointType` | enum | 11 ECG landmarks: `P_START`/`P_PEAK`/`P_END`, `QRS_START`/`Q_PEAK`/`R_PEAK`/`S_PEAK`/`QRS_END`, `T_START`/`T_PEAK`/`T_END`. Each carries an HTML-like `label` (e.g. `P<sub>s</sub>`) and a `descriptionRu`. |
| `SignificantPoint` | data class | `(index: Int, type: EcgPointType)` — one global marker on a pathology. Markers are stored per pathology, not per lead. |

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
| `FilePathologySource` | Reads/writes a directory (default `filesDir/pathologies`); `writePathology` is atomic (`.tmp` + rename); `isValid()` | Writable source for the constructor |
| `PathologyRepository` | Holds the current `PathologySource`, caches the manifest as a `StateFlow<PathologyManifest?>` (`manifestFlow`), lazily reads pathologies, exposes `leadWaveform(id, lead)` (baseline-zeroing + `DerivedLeads` synthesis), and `writePathology` (file-backed only; reloads the manifest on success) | Central data gateway |
| `PathologyZipExtractor` | `object`: extracts a SAF zip URI into `filesDir/pathologies`, flattening nested dirs (UTF-8, no charset detection) | Used by `setDataFolder` |
| `ZipCompressor` | `object`: `zip(sourceDir, destUri)` for explicit export; `zipToCache(sourceDir, name)` for the TCP upload snapshot | Two distinct flows |
| `DataSourcePrefs` | DataStore (`ecg_data_source`). Stores global keys (`tree_uri`, `language_tag`, `tcp_ip`, `tcp_port`, `dark_theme`, `last_operating_mode`, `last_editor_rhythm_id`) **plus per-mode keys** of the form `${mode}_grid_scheme`, `${mode}_last_rhythm_id`, `${mode}_monitor_speed`, `${mode}_monitor_scale`, `${mode}_monitor_display_scale`, `${mode}_monitor_series_count`, `${mode}_monitor_series_scheme`. Per-mode reads fall back to the legacy global keys when no per-mode value exists. | Persists across reboots |
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
   current dataset: `ZipCompressor.zipToCache` → `UploadMessage` header
   (filename `Pathologies.zip`) + raw ZIP bytes (`sendUploadArchive`).
   This happens on every connect, independent of the explicit `exportZip`
   button.
2. Reads newline-delimited frames so a socket EOF (disconnect) is detected.
3. On drop, retries every `tcpReconnectIntervalMs` (default 5 s) while the
   job is active. `sendStartCommand` / `sendStopCommand` write under a
   `Mutex`.

`StartCommand.params` is currently populated with `{"pathology": <id>,
"name": <titleEn>}` from the currently selected rhythm.

---

## 5. What changed from the legacy architecture

For readers familiar with the previous Parts/Series-based design, the
current architecture removes:

- **Legacy Editor** anchor-based design (replaced by the raw-sample
  `ConstructorScreen` + `EditableLead` + `SampleHandleOverlay`
  + `SignificantPointOverlay`).
- **Editable working copies**: `EditablePart`, `EditableSeries`,
  `UndoStack<T>`, `AnchorClipboard`, `PartNamer`.
- **Anchor model**: `AnchorPoint`, `SourceSpec`, `bakeAnchorsToSamples`,
  `AnchorCanvas` / `AnchorHandleOverlay`, `BlockTimeline`.
- **Part/series split**: `WaveformPart`, `EcgSeries`, `SeriesPartRef`,
  `BlockFlags`, `PathologyGroup`, `EcgFileFormat`.
- **Per-record calibration**: `aMax` / `aValue` / `duration` /
  `samplesPerMv` overrides on `PixelScale`.
- **Charset detection**: the new format is strictly UTF-8.

**Empty placeholder screens** still in the tree: `Examination` and `OSKE`
render an empty layout — they are reserved for future modes.

What remains: a viewer and a raw-sample constructor that load a
`Pathologies.zip`, present a searchable pathology list, render up to 12
leads on a paper-grid monitor, stream playback commands (and a dataset
upload) over TCP, and let the user edit raw ADC samples plus globally
annotate ECG landmarks.

---

_Source tree:_ `app/src/main/java/com/example/cardiosimulator/`.
