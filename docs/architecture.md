# CardioSimulator — Application Structure & Dependencies

This document maps the classes, objects, and instances of the
CardioSimulator Android app and the wiring between them. It describes the
**current architecture**, built around two parallel data pipelines:

- **Pathology pipeline** — documented in [data-structure.md](data-structure.md):
  one `.dat` file per pathology, all 12 leads inside, raw ADC samples, plus
  an optional global `markers:` line of significant ECG points.
- **Course pipeline** — documented in [course-format.md](course-format.md):
  `Courses.zip` containing `manifest.txt`, one `<course-id>/course.txt` per
  course, and `<lecture-id>.<lang>.html` lecture files.

The codebase is a layered MVVM-with-Compose viewer and constructor for both
datasets. Both constructors render through the **same** `Points → ChartCanvas`
pipeline as the viewer.

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
                │  ConstructorViewModel ·             │
                │  CourseConstructorViewModel ·       │
                │  CourseViewerViewModel              │
                └──────────────┬──────────────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
   ┌────────┐       ┌──────────────────────┐   ┌──────────┐
   │ domain │       │        data          │   │ network  │
   │ models │◀──────│ PathologyRepo        │   │ TcpProto │
   │ /math  │       │ CourseRepo           │   │ TcpMsg   │
   │ /marks │       │ PathologySource      │   │ Socket   │
   │ /course│       │ CourseSource         │   └──────────┘
   └────────┘       │ Prefs · Zip*         │
                    │ PixelScale · Cal     │
                    └──────────────────────┘
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
║     ├──────────────────────────────────► CourseRepository(                  ║
║     │                                        FileCourseSource(filesDir))     ║
║     │                                                                        ║
║     │  creates directly                                                      ║
║     ├──────────────────────────────────► DataSourcePrefs(appContext)         ║
║     │                                                                        ║
║     │  creates via Compose viewModel { factory }                             ║
║     └──────────────────────────────────► AppViewModel(appState, repository,  ║
║                                                courseRepository, appContext, ║
║                                                prefs)                        ║
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
║  │    CourseRepository? (public `courseRepository`) ◄─ course bundle   │    ║
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
║  │    CourseZipExtractor.extract ──► FileCourseSource → courseRepo     │    ║
║  │    SampleCourseSeeder.seed ──► seeds starter course into filesDir   │    ║
║  │                                                                     │    ║
║  │  Emitted StateFlows (consumed by UI via collectAsState):            │    ║
║  │    selectedOperatingMode : StateFlow<OperatingModeModel>            │    ║
║  │    selectedLanguage      : StateFlow<Language>                      │    ║
║  │    tcpIp / tcpPort       : StateFlow<String> / <Int>               │    ║
║  │    isDarkTheme           : StateFlow<Boolean>  (default true)       │    ║
║  │    tcpConnectionState    : StateFlow<TcpConnectionState>            │    ║
║  │    dataState             : StateFlow<DataState>  (pathology bundle) │    ║
║  │    courseDataState       : StateFlow<DataState>  (course bundle)    │    ║
║  │    isDataConfirmed       : StateFlow<Boolean>                       │    ║
║  │    courses               : StateFlow<List<CourseEntry>>             │    ║
║  │       (sorted, prepended with a synthetic ALL_RHYTHMS_ID entry)     │    ║
║  │    selectedCourseId      : StateFlow<String?>                       │    ║
║  │                                                                     │    ║
║  │  Key actions:                                                       │    ║
║  │    selectCourse(id)           ──► _selectedCourseId                 │    ║
║  │    setCourseDataFolder(ctx, uri) ──► CourseZipExtractor → repo      │    ║
║  │    loadSampleCourses(ctx)     ──► SampleCourseSeeder → repo         │    ║
║  │    exportCoursesZip(ctx, uri) ──► ZipCompressor.zip (courses)       │    ║
║  │    uploadCourses()            ──► ZipCompressor.zipToCache → TCP    │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ MonitorViewModel(mode: OperatingMode, prefs: DataSourcePrefs?)      │    ║
║  │                                                                     │    ║
║  │  Domain:  MonitorModeModel (immutable, copied on each setter)       │    ║
║  │           EcgCalibration (embedded in MonitorModeModel)            │    ║
║  │           GridScheme {Pink, BlueGray, Blank}                       │    ║
║  │           SeriesScheme {OneColumn, TwoColumn, Grid}                │    ║
║  │           ComparisonTarget(pathologyId, lead)                      │    ║
║  │           ComparisonPreset(name, targets: Map<Int, ComparisonTarget>)│   ║
║  │  Emits:   monitorMode : StateFlow<MonitorModeModel>                 │    ║
║  │             (count · gridScheme · seriesScheme · speed · scale ·    │    ║
║  │              displayScale · calibration · isRunning ·               │    ║
║  │              isCompareMode · comparisonTargets · comparisonPresets)  │    ║
║  │  Setters: setSeriesCount / setSeriesScheme / setGridScheme /        │    ║
║  │           setSpeed / setScale / setDisplayScale / setCalibration /  │    ║
║  │           setIsRunning                                              │    ║
║  │  Compare: toggleCompareMode(defaultPathologyId?) /                  │    ║
║  │           setComparisonTarget(paneIndex, target) /                  │    ║
║  │           saveCurrentAsPreset(name) / applyPreset(preset)           │    ║
║  │  Persistence (per `mode.name` key, on `init` rehydrates from prefs):│    ║
║  │           every setter except setIsRunning/setCalibration takes a   │    ║
║  │           persist:Boolean=true flag and writes the new value back   │    ║
║  │           through DataSourcePrefs.setMonitor*                       │    ║
║  │           comparisonPresets are persisted via setComparisonPresets  │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ RhythmViewModel(repository, mode, prefs, appViewModel?)             │    ║
║  │  Emits:   rhythms             : StateFlow<List<PathologyEntry>>      │    ║
║  │             (in Teaching mode, filtered by appViewModel.selectedCourse│   ║
║  │              + courses pathology list; unfiltered in other modes)   │    ║
║  │           selectedRhythm      : StateFlow<PathologyEntry?>           │    ║
║  │           waveforms           : StateFlow<Map<Lead, Points>>         │    ║
║  │           comparisonWaveforms : StateFlow<Map<Int, Points>>          │    ║
║  │           significantPoints   : StateFlow<List<SignificantPoint>>    │    ║
║  │  Actions: loadManifest()   (+ nameRu enrichment by peeking .dat,    │    ║
║  │                              + restores last selection per mode)    │    ║
║  │           selectRhythm(id, persist) / refresh()                     │    ║
║  │           loadComparisonWaveform(index, pathologyId, lead)           │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ ConstructorViewModel(repository, mode, prefs)                       │    ║
║  │  Enums:   EditingAlgorithm {Cosine,Spline,Bezier,LOESS,MLS}        │    ║
║  │           ToolMode {Select, Trace, Position}                        │    ║
║  │  Holds:   targetFile : State<PathologyFile?> (Compose State)        │    ║
║  │  Emits:   focusedLead      : StateFlow<Lead> (default II)           │    ║
║  │           selectedIndex    : StateFlow<Int>                         │    ║
║  │           dirtyLeads       : StateFlow<Set<Lead>>                   │    ║
║  │           isMetadataDirty  : StateFlow<Boolean>                     │    ║
║  │           isSaving         : StateFlow<Boolean>                     │    ║
║  │           editingAlgorithm : StateFlow<EditingAlgorithm>            │    ║
║  │           editingRadius    : StateFlow<Int>  (1..1000, default 100) │    ║
║  │           referenceImageUri: StateFlow<Uri?>  (photo underlay)      │    ║
║  │           toolMode         : StateFlow<ToolMode>                    │    ║
║  │           imageOffset      : StateFlow<Offset>                      │    ║
║  │           imageScale       : StateFlow<Float>                       │    ║
║  │           imageRotationDeg : StateFlow<Float>                       │    ║
║  │           imageAlpha       : StateFlow<Float>  (default 0.5)        │    ║
║  │           imageLocked      : StateFlow<Boolean>                     │    ║
║  │           ghostTrace       : StateFlow<IntArray?>  (auto-detect)    │    ║
║  │  Actions: selectPathology(id, persist) — restores last via          │    ║
║  │             prefs.lastEditorRhythmId on init                        │    ║
║  │           selectLead(lead) / selectIndex(i) / selectNext/Previous() │    ║
║  │           selectSignificantPoint(type)  — cycle to next marker     │    ║
║  │           moveSelectedUp/Down()         — ±1 ADC on focused lead   │    ║
║  │           setSample(lead, index, adc)   — weighted kernel spread   │    ║
║  │           adjustSample (internal) — sub-integer float accumulator  │    ║
║  │           toggleSignificantPoint(lead, index, type)                │    ║
║  │           rename(title, language)       — sets titleEn or nameRu   │    ║
║  │           calculateDerivedLeads()       — fills III/aVR/aVL/aVF    │    ║
║  │             from I&II and V1/V3/V4/V5 from V2&V6                   │    ║
║  │           revertLead(lead) / save()  → repository.writePathology() │    ║
║  │           deleteCurrentPathology() / duplicateCurrentPathology()   │    ║
║  │           setEditingAlgorithm / setEditingRadius                   │    ║
║  │           setReferenceImageUri(uri) — loads photo, sets Position   │    ║
║  │           setToolMode / setImageOffset / setImageScale /            │    ║
║  │             setImageRotation / setImageAlpha / setImageLocked /     │    ║
║  │             resetImageTransform                                     │    ║
║  │           startStroke(lead)  — snapshots lead into undo stack       │    ║
║  │           traceSamples(lead, updates: Map<Int,Int>) — batch write   │    ║
║  │             (no kernel; used by TraceOverlay drag + auto-detect)    │    ║
║  │           setGhostTrace / applyGhostTrace — auto-detect preview     │    ║
║  │           undo(lead) / redo(lead)  (per-lead, depth 20)            │    ║
║  │  Editability: leads in DerivableFromIandII / DerivableFromV2andV6  │    ║
║  │             are read-only in the constructor (see [DerivedLeads]). │    ║
║  │  ADC range: 0..2048; baseline ~1024.                               │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ CourseConstructorViewModel(repository: CourseRepository, mode, prefs)│   ║
║  │  Emits:   selectedCourseId  : StateFlow<String?>                    │    ║
║  │           lectures          : StateFlow<List<LectureEntry>>          │    ║
║  │           selectedLectureId : StateFlow<String?>                    │    ║
║  │           draft             : StateFlow<String>   (raw HTML source) │    ║
║  │           answers           : StateFlow<Map<String, Map<String,String>>>│ ║
║  │           previewLecture    : StateFlow<Lecture?>  (debounced parse) │    ║
║  │           isDirty           : StateFlow<Boolean>                    │    ║
║  │           isSaving          : StateFlow<Boolean>                    │    ║
║  │  Actions: selectCourse(courseId) / selectLecture(lectureId)         │    ║
║  │           setHtml(text)   — updates draft + schedules preview parse │    ║
║  │           insertSnippet(html)  — appends HTML to draft              │    ║
║  │           setTableCell(quizId, row, col, value)                     │    ║
║  │           save()    → repository.writeLectureRaw + writeAnswers     │    ║
║  │           revert()  — resets draft + answers to last-saved state    │    ║
║  │           createCourse(courseId, title)                             │    ║
║  │           createLecture(lectureId, title)                           │    ║
║  │           renameLecture(newTitle)                                   │    ║
║  │           deleteLecture()                                           │    ║
║  │           restore()   — one-shot restore of last course/lecture     │    ║
║  │  Preview debounce: 200 ms after the last setHtml call               │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │ CourseViewerViewModel(repository: CourseRepository, mode, prefs)    │    ║
║  │  Emits:   selectedCourseId  : StateFlow<String?>                    │    ║
║  │           lectures          : StateFlow<List<LectureEntry>>          │    ║
║  │           selectedLectureId : StateFlow<String?>                    │    ║
║  │           lecture           : StateFlow<Lecture?>                   │    ║
║  │  Actions: setLanguage(tag)  — reloads open lecture in new language  │    ║
║  │           selectCourse(courseId) / selectLecture(lectureId)         │    ║
║  │           closeLecture()                                            │    ║
║  │           restore() — one-shot restore of last course (index only)  │    ║
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
║    │                    prefs = appViewModel.prefs,                          ║
║    │                    appViewModel = appViewModel)                         ║
║    │  creates (keyed by mode + "_editor")                                    ║
║    ├──► ConstructorViewModel(appViewModel.repository!!,                      ║
║    │                         mode = selectedMode.id,                         ║
║    │                         prefs = appViewModel.prefs)                     ║
║    │  creates (keyed by mode + "_course_editor")                             ║
║    ├──► CourseConstructorViewModel(appViewModel.courseRepository!!,          ║
║    │                               mode = selectedMode.id,                   ║
║    │                               prefs = appViewModel.prefs)               ║
║    │  creates (keyed by mode + "_course_viewer")                             ║
║    ├──► CourseViewerViewModel(appViewModel.courseRepository!!,               ║
║    │                          mode = selectedMode.id,                        ║
║    │                          prefs = appViewModel.prefs)                    ║
║    │                                                                         ║
║    │  observes: selectedOperatingMode · dataState · isDataConfirmed          ║
║    │            courseDataState                                               ║
║    │                                                                         ║
║    ├── [guard] if !isDataConfirmed or dataState ∈ {NotConfigured,            ║
║    │       Loading, Error}:                                                  ║
║    │       DataSourceScreen(App, Rhythm, state, courseState)                 ║
║    │         └─ SAF OpenDocument (zip) ──► AppViewModel.setDataFolder()      ║
║    │                                                                         ║
║    ├── [overlay] if showSettings:                                           ║
║    │       SettingsDialog(Monitor, App) → SettingsContent                    ║
║    │         └─ theme · grid · language · TCP · change folder · export      ║
║    │                                                                         ║
║    ├── TopControlPanel(App, Monitor, onStartStopClick)   ← top bar (2f)      ║
║    │     ├─ mode dropdown                                                    ║
║    │     └─ per-mode panel: TeachingControlPanel / TestingControlPanel       ║
║    │       (Examination, OSKE, Constructor, CourseConstructor → empty)       ║
║    │                                                                         ║
║    ├── [mode screen]  ← weight 15f                                          ║
║    │     Teaching          → TeachingScreen (rhythm drawer + monitor +       ║
║    │                           compare mode panels + course viewer overlay)  ║
║    │     Testing           → TestingScreen  (monitor, no rhythm picker)      ║
║    │     Examination       → ExaminationScreen (placeholder)                 ║
║    │     OSKE              → OSKEScreen  (placeholder)                       ║
║    │     Constructor       → ConstructorScreen  (editor + side drawers)      ║
║    │     CourseConstructor → CourseConstructorScreen (HTML editor + preview) ║
║    │                                                                         ║
║    └── BottomControlPanel(onSettingsClick) { slot }      ← bottom bar (2f)   ║
║          │   slot content depends on mode:                                   ║
║          ├─ Teaching          → MonitorControlPanel (start/stop fires TCP)   ║
║          ├─ Constructor       → ConstructorControlPanel                      ║
║          ├─ CourseConstructor → CourseConstructorControlPanel                ║
║          └─ else              → no slot                                      ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### StateFlow data flow (reactive bindings)

```
AppViewModel StateFlows           Consumed by
─────────────────────────────────────────────────────────────────────
selectedOperatingMode   ──╌╌►  MainScreen (routing), TopControlPanel
selectedLanguage        ──╌╌►  RhythmSelector, ConstructorScreen,
                               TeachingScreen, SettingsContent,
                               CourseConstructorScreen,
                               CourseViewerViewModel.setLanguage
tcpIp / tcpPort         ──╌╌►  SettingsContent
isDarkTheme             ──╌╌►  MainActivity (theme), SettingsContent
tcpConnectionState      ──╌╌►  SettingsContent (status dot + connect)
dataState               ──╌╌►  MainScreen (guard), DataSourceScreen
courseDataState         ──╌╌►  MainScreen (guard), DataSourceScreen
isDataConfirmed         ──╌╌►  MainScreen (guard)
courses                 ──╌╌►  TeachingScreen (course overlay),
                               CourseConstructorScreen (drawer),
                               RhythmViewModel (Teaching-mode filter)
selectedCourseId        ──╌╌►  RhythmViewModel (Teaching-mode filter)

MonitorViewModel StateFlows
─────────────────────────────────────────────────────────────────────
monitorMode             ──╌╌►  Monitor (pxPerMm, speed, scale, grid, running,
                                        isCompareMode, comparisonTargets)
                               MonitorControlPanel (count / scheme / speed / scale)
                               ConstructorControlPanel (speed / start-stop)
                               SettingsContent (grid scheme)
                               TeachingScreen (compare dialogs + pane routing)

RhythmViewModel StateFlows
─────────────────────────────────────────────────────────────────────
rhythms                 ──╌╌►  RhythmSelector (list), TeachingScreen
selectedRhythm          ──╌╌►  Teaching / Testing (start cmd args),
                               ConstructorScreen (toolbar title)
waveforms               ──╌╌►  LeadsGrid → Lead (render Points per lead)
comparisonWaveforms     ──╌╌►  TeachingScreen (per-pane Points in compare mode)
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

CourseConstructorViewModel StateFlows
─────────────────────────────────────────────────────────────────────
selectedCourseId        ──╌╌►  CourseConstructorScreen (drawer highlight)
lectures                ──╌╌►  CourseConstructorScreen (LectureSelector drawer)
selectedLectureId       ──╌╌►  CourseConstructorScreen (toolbar / Rename enabled)
draft                   ──╌╌►  CourseConstructorScreen (OutlinedTextField)
answers                 ──╌╌►  CourseConstructorScreen (LectureWebView)
previewLecture          ──╌╌►  CourseConstructorScreen (LectureWebView)
isDirty                 ──╌╌►  CourseConstructorScreen (Save/Revert enabled)
isSaving                ──╌╌►  CourseConstructorScreen (Save/Revert enabled)

CourseViewerViewModel StateFlows
─────────────────────────────────────────────────────────────────────
selectedCourseId        ──╌╌►  TeachingScreen (CourseViewerOverlay header)
lectures                ──╌╌►  TeachingScreen (LectureSelector in overlay)
selectedLectureId       ──╌╌►  TeachingScreen (LectureSelector highlight)
lecture                 ──╌╌►  TeachingScreen (LectureWebView in overlay)
```

### UI component ownership tree

```
CardioSimulatorTheme (MainActivity, darkTheme = isDarkTheme)
└── MainScreen
    ├── [guard]   DataSourceScreen(state, courseState)
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
    ├── [mode screen]  ← one of six (weight 15f)
    │     ├── TeachingScreen
    │     │     ├── Toolbar: rhythm title / "Compare mode" label
    │     │     ├── Monitor → LeadsGrid
    │     │     │     ├── normal mode: Lead ◄── waveforms
    │     │     │     └── compare mode: per-pane (Lead | placeholder clickable)
    │     │     │           comparisonWaveforms[index] ◄── RhythmViewModel
    │     │     ├── SideDrawer → RhythmSelector ──► selectRhythm
    │     │     │     (filtered by selectedCourseId when a course is active)
    │     │     ├── [compare mode] ComparisonPresetsDialog / ComparisonTargetDialog
    │     │     │                  SaveComparisonPresetDialog
    │     │     └── [overlay] CourseViewerOverlay (school-icon button toggles)
    │     │           ├── Toolbar: lecture/course title + close
    │     │           ├── LectureWebView ◄── CourseViewerViewModel.lecture
    │     │           └── SideDrawer → LectureSelector ──► selectLecture
    │     ├── TestingScreen
    │     │     ├── Monitor → LeadsGrid → Lead
    │     │     └── MonitorControlPanel (sendStart/StopCommand on toggle)
    │     ├── ExaminationScreen   (placeholder stub)
    │     ├── OSKEScreen          (placeholder stub)
    │     ├── ConstructorScreen
    │     │     ├── Toolbar: title + Rename + Generate Derived + Save + Revert
    │     │     ├── ScrollableTabRow over Lead.entries (dirty leads in red)
    │     │     ├── Monitor(staticGrid = true)
    │     │     │     ├── EditableLead → ChartCanvas + SignificantPointOverlay
    │     │     │     │   + SampleHandleOverlay  ──► setSample / selectIndex
    │     │     │     └── PreviewPane              ← HR=60 loop preview
    │     │     ├── SignificantPointPanel (right side, FilterChips per type)
    │     │     └── SideDrawers:
    │     │           ├── RhythmSelector       ──► selectPathology
    │     │           └── SignificantPointSelector ──► selectIndex
    │     └── CourseConstructorScreen
    │           ├── Toolbar: lecture title + New Course + New Lecture +
    │           │            Rename + Delete + Revert + Save
    │           ├── Row (weight 1f):
    │           │     ├── OutlinedTextField (HTML source draft, monospace)
    │           │     └── LectureWebView (live preview, debounced parse)
    │           └── SideDrawers:
    │                 ├── CourseSelector       ──► selectCourse
    │                 └── LectureSelector      ──► selectLecture
    └── BottomControlPanel                           ← bottom bar (weight 2f)
          ├── Settings gear      ──► onSettingsClick
          └── slot:
                ├─ Teaching          → MonitorControlPanel
                ├─ Constructor       → ConstructorControlPanel
                └─ CourseConstructor → CourseConstructorControlPanel
```

---

## 1. Package layout

| Package | Role |
|---|---|
| `com.example.cardiosimulator` | `MainActivity` — Compose entry point |
| `…domain` | Pure-Kotlin models: app state, pathology / lead models + parser, derived-lead math, ECG-point markers, course bundle models + parser, comparison types |
| `…data` | Storage / persistence: `PathologyRepository`, `PathologySource` family, `CourseRepository`, `CourseSource` family, `DataSourcePrefs`, ZIP I/O, `Points`, `EcgTrace`, calibration, pixel scaling, `EcgSvgRenderer`, `SampleCourseSeeder` |
| `…network` | TCP layer: `TcpProtocol` (JSON encode/decode), `TcpMessage` sealed hierarchy, `TcpConnectionState` |
| `…ui.viewmodels` | `AppViewModel`, `MonitorViewModel`, `RhythmViewModel`, `ConstructorViewModel`, `CourseConstructorViewModel`, `CourseViewerViewModel`, `DataState` |
| `…ui.screens` | `MainScreen`, one screen per `OperatingMode` (`TeachingScreen`, `TestingScreen`, `ExaminationScreen`, `OSKEScreen`, `ConstructorScreen`, `CourseConstructorScreen`), `DataSourceScreen`, `SettingsScreen` (dialog), comparison dialogs (`ComparisonPresetsDialog`, `ComparisonTargetDialog`, `SaveComparisonPresetDialog`), plus `Modifiers.kt` |
| `…ui.panels` | `TopControlPanel`, `BottomControlPanel`, `MonitorControlPanel`, `ConstructorControlPanel`, `CourseConstructorControlPanel`, `RhythmSelector`, `CourseSelector`, `LectureSelector`, `SignificantPointSelector`, `SignificantPointsControlPanel`, `TeachingControlPanel`, `TestingControlPanel` |
| `…ui.display` | Waveform render Composables: `Monitor`, `Lead`, `EditableLead`, `LeadsGrid`, `Modifers.kt` (`ekgGrid`) |
| `…ui.components` | Lower-level visuals: `ChartCanvas`, `CalibrationPulse`, `SampleHandleOverlay`, `SignificantPointOverlay`, `PreviewPane`, `SideDrawer`, `LectureWebView`, `Tab`, `Label`, `AutoResizeText`, `ControlPanelDivider`, `RepeatingClickable`, `TraceOverlay` |
| `…ui.theme` | Material3 theme, colors, typography |
| `…ui.utils` | Misc helpers: `EcgUtils`, point-type → display string, `TraceExtractor` (bitmap-based ECG trace auto-detection for photo tracing) |

---

## 2. Domain layer (`…domain`)

Pure-Kotlin, no Android imports except a couple of `@StringRes` references.
These types are the source of truth the UI and view-models manipulate.

### Application state

| Type | Kind | Role | File |
|---|---|---|---|
| `OperatingMode` | enum | `Teaching`, `Testing`, `Examination`, `OSKE`, `Constructor`, `CourseConstructor` (each carries a `@StringRes titleRes`) | `OperatingModeModel.kt` |
| `OperatingModeModel` | data class | `(id: OperatingMode, description: String)` | `OperatingModeModel.kt` |
| `AppBuilder` | class | Collects `OperatingModeModel`s, `build(initialMode?)` → `AppStateModel` | `AppBuilder.kt` |
| `AppStateModel` | data class (mutable) | Selected mode, language, TCP ip/port (defaults `192.168.1.100:8080`); `updateMode/Language/TcpConnection` | `AppStateModel.kt` |
| `Language` | enum | `EN`, `RU`, `ZH`, `ES` with locale tags + `fromTag` | `AppStateModel.kt` |
| `GridScheme` | enum | `Pink`, `BlueGray`, `Blank` (`@StringRes labelRes`) | `MonitorModeModel.kt` |
| `SeriesScheme` | enum | `OneColumn`, `TwoColumn`, `Grid` | `MonitorModeModel.kt` |
| `ComparisonTarget` | data class | `(pathologyId: String, lead: Lead)` — one pane in compare mode | `MonitorModeModel.kt` |
| `ComparisonPreset` | data class | `(name: String, targets: Map<Int, ComparisonTarget>)` — saved compare layout | `MonitorModeModel.kt` |
| `MonitorModeModel` | data class | `count, gridScheme, seriesScheme, speed=25, scale=1, displayScale=0.4, calibration, isRunning, isCompareMode=false, comparisonTargets, comparisonPresets` | `MonitorModeModel.kt` |

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

### Course bundle model (`Course.kt`, `CourseParser.kt`)

| Type | Kind | Role |
|---|---|---|
| `CourseManifest` | data class | `(version, entries: List<CourseEntry>)`; `SUPPORTED_VERSION = "1.0"` |
| `CourseEntry` | data class | Manifest row `(id, titleEn, nameRu, lecturesCount, pathologies: List<String>)` |
| `Course` | data class | Parsed `<course-id>/course.txt` `(id, titleEn, nameRu, authors, languages, lectures, pathologies)` |
| `LectureEntry` | data class | Ordered row inside `Course.lectures` `(id, titleEn, nameRu)` |
| `Lecture` | data class | Parsed `<lecture-id>.<lang>.html` `(id, courseId, language, frontMatter, rawHtml)` |
| `LectureFrontMatter` | data class | Front-matter keys: `id, order, title, schemaVersion, extras: Map<String,String>` |
| `CourseParser` | `object` | Parse/serialize all three course file types. `serializeManifest` / `serializeCourse` / `parseLecture` / `serializeLecture`. `FormatException` on bad input. |

### Significant-point model (`SignificantPoint.kt`)

| Type | Kind | Role |
|---|---|---|
| `EcgPointType` | enum | 11 ECG landmarks: `P_START`/`P_PEAK`/`P_END`, `QRS_START`/`Q_PEAK`/`R_PEAK`/`S_PEAK`/`QRS_END`, `T_START`/`T_PEAK`/`T_END`. Each carries an HTML-like `label` (e.g. `P<sub>s</sub>`) and a `descriptionRu`. |
| `SignificantPoint` | data class | `(index: Int, type: EcgPointType)` — one global marker on a pathology. Markers are stored per pathology, not per lead. |

### Display values

| Type | Kind | Role | File |
|---|---|---|---|
| `Points` | `data class(values: List<Float>)` | Baseline-zeroed sample buffer for one lead, ready for the renderer | `data/Points.kt` |
| `EcgTrace` | data class | `(lead: Lead, points: Points)` — one resolved lead waveform, used by `LectureWebView` / `EcgSvgRenderer` to embed ECG SVGs in lectures | `data/EcgSvgRenderer.kt` (co-located) |

---

## 3. Data layer (`…data`)

### Pathology source abstraction

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

### Course source abstraction

```
        ┌──────────────────┐
        │   CourseSource   │   interface (read-only contract)
        └──┬───────────────┘
           │ implemented by
        ┌──▼────────────────────────────────────┐
        │ FileCourseSource (filesDir/courses/)  │
        │ (+ writeLecture / writeLectureRaw /   │
        │    writeCourse / writeAnswers /       │
        │    deleteLecture)                     │
        └───────────────────────────────────────┘
```

| Type | Role | Notes |
|---|---|---|
| `PathologySource` | Interface: `readManifest() / readPathology(id) / listPathologies()` | Storage-agnostic, read-only |
| `AssetPathologySource` | Reads `assets/Pathologies/` (flat, UTF-8) | Default at boot |
| `FilePathologySource` | Reads/writes a directory (default `filesDir/pathologies`); `writePathology` is atomic (`.tmp` + rename); `isValid()` | Writable source for the constructor |
| `PathologyRepository` | Holds the current `PathologySource`, caches the manifest as a `StateFlow<PathologyManifest?>` (`manifestFlow`), lazily reads pathologies, exposes `leadWaveform(id, lead)` (baseline-zeroing + `DerivedLeads` synthesis), and `writePathology` (file-backed only; reloads the manifest on success) | Central data gateway |
| `PathologyZipExtractor` | `object`: extracts a SAF zip URI into `filesDir/pathologies`, flattening nested dirs (UTF-8) | Used by `setDataFolder` |
| `CourseSource` | Interface: `readManifest() / readCourse(id) / readLecture(id, lang) / listCourses() / listLectures(courseId)`. Fallback language: `"en"`. | Storage-agnostic, read-only |
| `FileCourseSource` | Reads `filesDir/courses/` layout; adds `writeLecture`, `writeLectureRaw`, `writeCourse`, `writeAnswers`, `deleteLecture` (all atomic via `atomicWriteText`); `isValid()` | Writable source for the course constructor |
| `CourseRepository` | Holds the current `CourseSource`, caches the manifest as a `StateFlow<CourseManifest?>` (`manifestFlow`), exposes `readCourse`, `readLecture`, `lectureEntries`, `readAnswers`, write wrappers (`withFileSource` guards) | Mirrors `PathologyRepository` in shape |
| `CourseZipExtractor` | `object`: extracts a SAF zip URI into `filesDir/courses`, flattening nested dirs | Used by `setCourseDataFolder` |
| `SampleCourseSeeder` | `object`: copies the bundled sample course template (from `assets/courses/`) into `filesDir/courses/` so authors can start editing immediately | Used by `loadSampleCourses` |
| `EcgSvgRenderer` | Converts a `PathologyFile` lead into an inline SVG using the same projection math as `ChartCanvas`, producing static ECG figures for `<ecg>` elements in lectures | Used by `LectureWebView` during the `<ecg>` → `<figure>` rewrite |
| `ZipCompressor` | `object`: `zip(sourceDir, destUri)` for explicit export; `zipToCache(sourceDir, name)` for the TCP upload snapshot | Two distinct flows |
| `DataSourcePrefs` | DataStore (`ecg_data_source`). Stores global keys (`tree_uri`, `language_tag`, `tcp_ip`, `tcp_port`, `dark_theme`, `last_operating_mode`, `last_editor_rhythm_id`, `courses_tree_uri`, `last_course_id`) **plus per-mode keys** of the form `${mode}_grid_scheme`, `${mode}_last_rhythm_id`, `${mode}_monitor_speed`, `${mode}_monitor_scale`, `${mode}_monitor_display_scale`, `${mode}_monitor_series_count`, `${mode}_monitor_series_scheme`, `${mode}_comparison_presets`, `${mode}_last_lecture_id`. Per-mode reads fall back to the legacy global keys when no per-mode value exists. | Persists across reboots |
| `EcgCalibration` | `(gainMmPerMv=10, sampleRateHz=500, adcCountsPerMv=256)` | Fixed physical calibration |
| `PixelScale` | Derives `pxPerMv/pxPerSec/pxPerSample/pxPerAdcCount/grid steps` from a single `pxPerMm` anchor + `EcgCalibration`; provided via `LocalPixelScale` | See [ecg-rendering-pipeline.md](ecg-rendering-pipeline.md) |

### `DataState` lifecycle (`ui/viewmodels/AppViewModel.kt`)

```
NotConfigured ──setDataFolder──► Loading ──► Ready(pathologyCount)
                                        └──► Error(Unreadable | Empty | BadManifest)
```

Used by **both** the pathology pipeline (`dataState`) and the course pipeline
(`courseDataState`) — the same sealed class serves both.

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

The course bundle can also be uploaded manually via `uploadCourses()`, which
sends the `filesDir/courses/` directory as `Courses.zip`.

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

**Placeholder screens** still in the tree: `Examination` and `OSKE`
render empty layouts — they are reserved for future modes. `Teaching`,
`Constructor`, and `CourseConstructor` are fully implemented.

What remains: a viewer and a raw-sample constructor that load a
`Pathologies.zip`, present a searchable/course-filtered pathology list,
render up to 12 leads on a paper-grid monitor with optional comparison mode,
stream playback commands (and a dataset upload) over TCP, let the user edit
raw ADC samples plus globally annotate ECG landmarks, and author/preview
HTML lecture content bundled in a `Courses.zip`.

---

_Source tree:_ `app/src/main/java/com/example/cardiosimulator/`.
