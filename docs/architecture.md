# CardioSimulator — Application Structure & Dependencies

This document maps the classes, objects, and instances of the
CardioSimulator Android app and the wiring between them. It describes
the **target architecture** built around the pathology dataset format
documented in [data-structure.md](data-structure.md): one `.dat` file
per pathology, all 12 leads inside, raw ADC samples — no anchors, no
part/series indirection, no per-record calibration.

The codebase is a layered MVVM-with-Compose viewer and editor for that
dataset. The editor renders through the **same** `Points → ChartCanvas`
pipeline as the viewer, using per-sample drag handles.

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
                │  RhythmViewModel · EditorViewModel  │
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
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ EditorViewModel                                                     │    ║
║  │                                                                     │    ║
║  │  Injected: EcgRepository ◄─ readPathology / writePathology          │    ║
║  │                                                                     │    ║
║  │  Holds:                                                             │    ║
║  │    targetFile : MutableState<PathologyFile?> (full edit target)     │    ║
║  │                                                                     │    ║
║  │  Emits:                                                             │    ║
║  │    focusedLead : StateFlow<Lead>                                    │    ║
║  │    dirtyLeads  : StateFlow<Set<Lead>>                               │    ║
║  │    isSaving    : StateFlow<Boolean>                                 │    ║
║  │                                                                     │    ║
║  │  Actions: selectPathology(id) / selectLead(lead) /                  │    ║
║  │           setSample(lead, index, value) / revertLead(lead) / save() │    ║
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
║    │  creates (keyed by mode + "_editor")                                    ║
║    ├──► EditorViewModel(ecgRepository)                                       ║
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
║         Editor       → EditorScreen(App, Monitor, Rhythm, Editor)            ║
║         │   └─ RhythmChoosingPanel(RhythmViewModel)                          ║
║         │   └─ Monitor / EditableLead                                        ║
║         │        └─ ChartCanvas + SampleHandleOverlay                        ║
║         │                                                                    ║
║         Testing      → TestingScreen(App, Monitor, Rhythm)                   ║
║         │   └─ TestingControlPanel(AppViewModel, MonitorViewModel)           ║
║         │   └─ Monitor / LeadsGrid / Lead                                    ║
║         │                                                                    ║
║         Examination  → ExaminationScreen(App, Monitor, Rhythm)               ║
║         OSKE         → OSKEScreen(       viewModel, monitorViewModel, Rhythm)║
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

EditorViewModel StateFlows
─────────────────────────────────────────────────────────────────────
focusedLead             ──╌╌►  EditorScreen (lead tabs selector)
dirtyLeads              ──╌╌►  EditorScreen (toolbar Save/Revert display)
targetFile              ──╌╌►  Monitor → EditableLead (edit buffer)
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
    └── [mode screen]  ← one of five, keyed by OperatingMode
          ├── RhythmChoosingPanel
          │     ├── reads  RhythmViewModel.{rhythms, selectedRhythm}
          │     └── calls  RhythmViewModel.selectRhythm() / EditorViewModel.selectPathology()
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
          ├── LeadsGrid
          │     └── Lead  ×N
          │           ├── reads RhythmViewModel.waveforms[lead] → Points
          │           └── reads LocalPixelScale (PixelScale + EcgCalibration)
          └── EditableLead
                ├── reads EditorViewModel.targetFile[lead] → LeadStream
                ├── reads LocalPixelScale
                └── components.{ChartCanvas, SampleHandleOverlay}
```

---

## 1. Package layout

| Package | Role |
|---|---|
| `com.example.cardiosimulator` | `MainActivity` — Compose entry point |
| `…domain` | Pure-Kotlin models: app state, pathology / lead models, derived-lead math |
| `…data` | Storage / persistence: `EcgRepository`, `EcgSource` family, `DataSourcePrefs`, ZIP I/O, calibration, pixel scaling |
| `…network` | TCP layer: `TcpProtocol` (encode/decode), `TcpMessage` sealed hierarchy, `TcpConnectionState` |
| `…ui.viewmodels` | Four `ViewModel`s: `AppViewModel`, `MonitorViewModel`, `RhythmViewModel`, `EditorViewModel` |
| `…ui.screens` | Top-level Composables, one per `OperatingMode` + `MainScreen` + `DataSourceScreen` + `SettingsScreen` |
| `…ui.panels` | Side / control panels reused across screens |
| `…ui.display` | Waveform render Composables: `Monitor`, `Lead`, `EditableLead`, `LeadsGrid` |
| `…ui.components` | Lower-level visual building blocks (chart canvas, grid, sample handles) |
| `…ui.theme` | Material3 theme, colors, typography |
| `…ui.utils` | Misc helpers |

---

## 2. Domain layer (`…domain`)

Pure-Kotlin, no Android imports (except a couple of `@StringRes` references).
These types are the source of truth the UI and view-models manipulate.

### Application state

| Type | Kind | Role | Depends on |
|---|---|---|---|
| `OperatingMode` | enum | `Teaching`, `Testing`, `Examination`, `OSKE`, `Editor` | — |
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

| Type | Role | Notes |
|---|---|---|
| `EcgSource` | Interface: `readManifest() / readPathology(id) / listPathologies()` | Storage-agnostic |
| `AssetEcgSource` | Reads `Pathologies/` from APK assets (or directly from the bundled zip) | Default at boot; read-only |
| `FileEcgSource` | Reads from/writes to a directory in app `filesDir/pathologies`. | Writable source for Editor |
| `EcgRepository` | Holds the current `EcgSource`, caches the manifest, and lazily reads/writes pathology files. |

---

## 10. What changed from the legacy architecture

For readers familiar with the previous Parts/Series-based design, the
target architecture removes the following:

- **Legacy Editor** anchor-based design (replaced by the raw-sample
  `EditorScreen` & `SampleHandleOverlay`).
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
- **Charset detection**: `decodeEcgText`, `EcgRepository.fixEncoding`,
  multi-charset zip extraction — the new format is strictly UTF-8.

What remains: a viewer and a raw-sample editor that load a
`Pathologies.zip`, presents a list of pathologies, renders 12 leads on
a paper-grid monitor, and streams playback commands over TCP.

---

_Source tree:_ `app/src/main/java/com/example/cardiosimulator/`.
