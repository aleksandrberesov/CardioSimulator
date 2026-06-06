# CardioSimulatorMac — Swift / macOS Development Prompt

> **Purpose.** This is the authoritative project brief for the macOS port of
> CardioSimulator. Feed the entire document to an AI assistant at the start
> of each session, or use it as the project `CLAUDE.md`. Every structural
> decision mirrors the Android reference app
> (`../CardioSimulator/docs/architecture.md`); only the platform layer changes.

---

## 0. Project brief

Build **CardioSimulatorMac**, a native macOS application for ECG waveform
simulation, pathology education, and course authoring.

| Item | Value |
|---|---|
| Target platform | macOS 13+ (Ventura), with AppKit bridge where SwiftUI gaps exist |
| Primary language | Swift 5.9+ |
| UI framework | SwiftUI |
| Reactive layer | `@Observable` macro (Swift 5.9+) for ViewModels; `AsyncStream` / `AsyncThrowingStream` for long-running sources |
| Concurrency | Swift Structured Concurrency (`async/await`, `Task`, `actor`) — no `DispatchQueue` except for legacy API bridges |
| Persistence | `UserDefaults` wrapped in a typed `Preferences` class; `FileManager` for file I/O |
| ZIP | Swift Package `ZIPFoundation` (or `libarchive` if unavailable) |
| TCP | `Network.framework` — `NWConnection` over `.tcp` |
| WebView | `WKWebView` wrapped in `NSViewRepresentable` |
| Canvas | SwiftUI `Canvas` + `GraphicsContext` for waveform drawing |
| File picker | `.fileImporter` modifier (SwiftUI) or `NSOpenPanel` for sandboxed open |
| Bundle resources | `Assets.xcassets` for images; bundled `Pathologies/` and `courses/` folders as folder references in the Xcode target |
| Package manager | Swift Package Manager (SPM) — no CocoaPods / Carthage |

Data formats, network protocol, localization rules, and editor patterns are
**identical** to the Android version. See:
- [`../CardioSimulator/docs/data-structure.md`](../../CardioSimulator/docs/data-structure.md)
- [`../CardioSimulator/docs/course-format.md`](../../CardioSimulator/docs/course-format.md)
- [`../CardioSimulator/docs/tcp-protocol.md`](../../CardioSimulator/docs/tcp-protocol.md)
- [`../CardioSimulator/docs/ecg-rendering-pipeline.md`](../../CardioSimulator/docs/ecg-rendering-pipeline.md)

---

## 1. Layered architecture

Enforce a strict four-layer separation. No layer may import from a layer
above it. Swift's `internal` / `package` / `public` access modifiers are
the enforcement mechanism — domain and data types are `internal` to their
module; ViewModels are never imported by the data layer.

```
┌──────────────────────────────────────────────┐
│  UI  (Views / Panels / Components / Theme)   │  SwiftUI
└──────────────────┬───────────────────────────┘
                   │  @Observable state / actions
┌──────────────────▼───────────────────────────┐
│  ViewModels  (@Observable classes)           │  Swift Concurrency
└──┬────────────────┬─────────────────────────-┘
   │                │
┌──▼──────┐  ┌──────▼──────────────────────────┐
│ Domain  │  │  Data / Network                 │
│ (pure   │◄─│  repositories · sources         │
│  Swift) │  │  prefs · ZIP · NWConnection     │
└─────────┘  └─────────────────────────────────┘
```

### Platform translation

| Android | macOS Swift |
|---|---|
| `data class Foo(val x: Int)` | `struct Foo: Equatable { let x: Int }` (value types) |
| `sealed class State` | `enum State` with associated values |
| `StateFlow<T>` | `@Published var state: T` (Combine) **or** `var state: T` inside `@Observable` |
| `viewModelScope.launch { }` | `Task { }` stored as `private var task: Task<Void,Never>?` |
| `Dispatchers.IO` | `Task.detached(priority: .utility) { }` |
| `Dispatchers.Default` | `Task.detached(priority: .background) { }` |
| `withContext(Dispatchers.IO) { }` | `await Task.detached(priority: .utility) { }.value` or `await withCheckedContinuation { }` |
| `collectAsState()` | Automatic in SwiftUI via `@State` / `@Bindable` on `@Observable` objects |
| `MutableStateFlow` | `var` property on an `@Observable` class |
| `combine(flow1, flow2) { }` | `withObservationTracking { }` or Combine `Publishers.CombineLatest` |

---

## 2. Dual-pipeline pattern

Two independent, structurally identical pipelines. Never merge them.

```
PathologyPipeline                    CoursePipeline
─────────────────────────────────    ──────────────────────────────────
PathologySource (protocol)           CourseSource (protocol)
  AssetPathologySource               (no asset-backed source needed;
  FilePathologySource                 use bundled default on first run)
PathologyRepository (@Observable)    CourseRepository (@Observable)
PathologyZipExtractor               CourseZipExtractor
ZipCompressor                       ZipCompressor (same object)
```

```swift
// Source protocol shape — identical for both pipelines
protocol PathologySource {
    func readManifest() throws -> PathologyManifest
    func readPathology(id: String) throws -> PathologyFile
    func listPathologies() -> [String]
}

protocol CourseSource {
    func readManifest() throws -> CourseManifest
    func readCourse(courseId: String) throws -> Course
    func readLecture(courseId: String, lectureId: String, language: String) throws -> Lecture?
    func listCourses() -> [String]
    func listLectures(courseId: String) -> [String]
}
```

Both pipelines share the same `DataState` enum:

```swift
enum DataState: Equatable {
    case notConfigured
    case loading
    case ready(count: Int)
    case error(DataError)
}

enum DataError: Equatable {
    case unreadable, empty, badManifest
}
```

---

## 3. ViewModels

Use `@Observable` (Swift 5.9+) — **not** `ObservableObject`/`@Published`.
Each ViewModel is a plain class, allocated and stored as `@State` in the
owning View. Never use `@EnvironmentObject` as the primary injection
mechanism — pass dependencies through initialisers.

```swift
@Observable
final class AppViewModel {
    // injected
    let repository: PathologyRepository
    let courseRepository: CourseRepository
    let prefs: Preferences

    // state
    var selectedMode: OperatingMode = .teaching
    var selectedLanguage: Language = .en
    var dataState: DataState = .notConfigured
    var courseDataState: DataState = .notConfigured
    var isDataConfirmed: Bool = false
    var courses: [CourseEntry] = []
    var selectedCourseId: String? = AppViewModel.allRhythmsId
    var tcpIp: String = "192.168.1.100"
    var tcpPort: Int = 8080
    var connectionState: TcpConnectionState = .disconnected
    var isDarkTheme: Bool = true

    // actions
    func updateLanguage(_ language: Language) { … }
    func updateMode(_ mode: OperatingMode) { … }
    func setDataFolder(url: URL) async { … }
    func setCourseDataFolder(url: URL) async { … }
    func loadSampleCourses() async { … }
    func exportZip(to url: URL) async { … }
    func exportCoursesZip(to url: URL) async { … }
    func sendStartCommand(pathologyId: String?, name: String?) { … }
    func sendStopCommand() { … }
    func uploadCourses() { … }
    func confirmData() { isDataConfirmed = true }
    func selectCourse(_ id: String?) { selectedCourseId = id }

    static let allRhythmsId = "all_rhythms"
    static let pathologiesDir = "pathologies"
    static let coursesDir = "courses"
}
```

### All ViewModels

| ViewModel | Swift storage in owning View |
|---|---|
| `AppViewModel` | `@State private var appVM = AppViewModel(…)` in `ContentView` |
| `MonitorViewModel` | `@State private var monitorVM = MonitorViewModel(mode:prefs:)` keyed by mode |
| `RhythmViewModel` | `@State private var rhythmVM = RhythmViewModel(repo:mode:prefs:appVM:)` keyed by mode |
| `ConstructorViewModel` | `@State private var constructorVM = ConstructorViewModel(repo:mode:prefs:)` keyed by mode |
| `CourseConstructorViewModel` | `@State private var courseConstructorVM = …` keyed by mode |
| `CourseViewerViewModel` | `@State private var courseViewerVM = …` keyed by mode |

**Keying pattern** — re-create a ViewModel when the mode changes:

```swift
// In ContentView or MainScreen
@State private var monitorVM: MonitorViewModel

// Recreate on mode change
.onChange(of: appVM.selectedMode) { _, newMode in
    monitorVM = MonitorViewModel(mode: newMode, prefs: appVM.prefs)
}
```

Or use SwiftUI's `.id(selectedMode)` modifier to force re-creation of a
sub-view that owns the ViewModel.

---

## 4. Operating modes

```swift
enum OperatingMode: String, CaseIterable, Identifiable {
    case teaching         = "Teaching"
    case testing          = "Testing"
    case examination      = "Examination"
    case oske             = "OSKE"
    case constructor      = "Constructor"
    case courseConstructor = "CourseConstructor"

    var id: String { rawValue }
    var titleKey: LocalizedStringKey { LocalizedStringKey(rawValue) }
}
```

---

## 5. UI structure

```
ContentView (root)
├── [guard] DataSourceView          (shown until isDataConfirmed)
├── [sheet] SettingsView
├── TopBar    weight 2
│     ├── Mode picker (Picker / segmented control)
│     └── Per-mode controls (TeachingControlPanel / TestingControlPanel)
├── ContentArea  weight 15
│     switch selectedMode:
│     ├── .teaching          → TeachingScreen
│     ├── .testing           → TestingScreen
│     ├── .examination       → ExaminationScreen (placeholder)
│     ├── .oske              → OSKEScreen (placeholder)
│     ├── .constructor       → ConstructorScreen
│     └── .courseConstructor → CourseConstructorScreen
└── BottomBar  weight 2
      ├── Settings button
      └── Slot (mode-dependent):
            .teaching          → MonitorControlPanel
            .constructor       → ConstructorControlPanel
            .courseConstructor → CourseConstructorControlPanel
            default            → EmptyView()
```

### Layout weights — SwiftUI equivalent

```swift
VStack(spacing: 0) {
    TopBar(…)
        .frame(height: geometry.size.height * 2/19)
    ContentArea(…)
        .frame(maxHeight: .infinity)   // weight 15
    BottomBar(…)
        .frame(height: geometry.size.height * 2/19)
}
```

### Side drawers

Implement as `HStack` overlays that slide from the leading/trailing edge
via `offset(x:)` animation. The tab handle is always visible. Never use
`NavigationSplitView` for these — it changes the layout hierarchy.

```swift
ZStack(alignment: .leading) {
    MainContent()
    SideDrawer(isExpanded: $isExpanded) {
        RhythmSelector(…)
    }
}
```

### Canvas rendering

Use SwiftUI `Canvas` for all waveform drawing:

```swift
Canvas { context, size in
    let path = projectPath(values: points.values,
                           stepX: pixelScale.pxPerSample,
                           stepY: pixelScale.pxPerAdcCount,
                           baselineY: size.height / 2)
    context.stroke(path,
                   with: .color(.green),
                   style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
}
```

`PixelScale` is computed once in `MonitorView` from `pxPerMm` and passed
down via SwiftUI `Environment`:

```swift
private struct PixelScaleKey: EnvironmentKey {
    static let defaultValue = PixelScale.default
}
extension EnvironmentValues {
    var pixelScale: PixelScale {
        get { self[PixelScaleKey.self] }
        set { self[PixelScaleKey.self] = newValue }
    }
}
```

---

## 6. Domain layer

Pure Swift — zero Foundation/AppKit/UIKit imports (except `Foundation`
primitives like `Date`, `URL.path`). All types are value types (`struct`,
`enum`) unless shared mutable state is explicitly required.

### Application state types

```swift
enum Language: String, CaseIterable {
    case en, ru, zh, es
    var tag: String { rawValue }
    var displayName: String { … }
    static func from(tag: String?) -> Language? { … }
}

enum GridScheme: String, CaseIterable  { case pink, blueGray, blank }
enum SeriesScheme: String, CaseIterable { case oneColumn, twoColumn, grid }

struct MonitorModeModel {
    var count: Int = 1
    var gridScheme: GridScheme = .pink
    var seriesScheme: SeriesScheme = .oneColumn
    var speed: Float = 25
    var scale: Float = 1
    var displayScale: Float = 0.4
    var calibration: EcgCalibration = .default
    var isRunning: Bool = false
    var isCompareMode: Bool = false
    var comparisonTargets: [Int: ComparisonTarget] = [:]
    var comparisonPresets: [ComparisonPreset] = []
}
```

### Pathology model

```swift
enum Lead: String, CaseIterable {
    case I, II, III, aVR, aVL, aVF, V1, V2, V3, V4, V5, V6
    static func from(token: String) -> Lead? { … }
}

struct PathologyManifest {
    let version: String
    let baseline: Int
    let leadOrder: [Lead]
    let entries: [PathologyEntry]
    static let supportedVersion = "1.0"
}

struct PathologyEntry: Identifiable {
    let id: String
    let titleEn: String
    let nameRu: String?
    let leadsCount: Int
    var fileName: String { "\(id).dat" }
}

struct LeadStream {
    let lead: Lead
    var samples: [Int]          // raw ADC, baseline ~1024
}

struct PathologyFile {
    let id: String
    var titleEn: String
    var nameRu: String?
    var leads: [Lead: LeadStream]
    var significantPoints: [SignificantPoint] = []
}
```

### Course model

```swift
struct CourseManifest {
    let version: String
    let entries: [CourseEntry]
    static let supportedVersion = "1.0"
}

struct CourseEntry: Identifiable {
    let id: String
    let titleEn: String
    let nameRu: String?
    let lecturesCount: Int
    let pathologies: [String]
}

struct Course: Identifiable {
    let id: String
    var titleEn: String
    var nameRu: String?
    var authors: String?
    var languages: [String]
    var lectures: [LectureEntry]
    var pathologies: [String]
}

struct LectureEntry: Identifiable {
    let id: String
    let titleEn: String
    let nameRu: String?
}

struct Lecture: Identifiable {
    let id: String
    let courseId: String
    let language: String
    var frontMatter: LectureFrontMatter
    var rawHtml: String         // verbatim — never re-serialize through DOM
}

struct LectureFrontMatter {
    var id: String
    var order: Int = 0
    var title: String = ""
    var schemaVersion: Int = 1
    var extras: [(key: String, value: String)] = []   // ordered, for lossless round-trip
}
```

### Parsers

```swift
// One object per format — matches Android PathologyParser / CourseParser
enum PathologyParser {
    static func parseManifest(_ text: String) throws -> PathologyManifest
    static func parsePathology(_ text: String) throws -> PathologyFile
    static func serializeManifest(_ manifest: PathologyManifest) -> String
    static func serializePathology(_ file: PathologyFile) -> String

    struct FormatError: Error { let message: String }
}

enum CourseParser {
    static func parseManifest(_ text: String) throws -> CourseManifest
    static func parseCourse(_ text: String) throws -> Course
    static func parseLecture(_ text: String, courseId: String, language: String) throws -> Lecture
    static func serializeManifest(_ manifest: CourseManifest) -> String
    static func serializeCourse(_ course: Course) -> String
    static func serializeLecture(_ lecture: Lecture) -> String

    struct FormatError: Error { let message: String }
}
```

### Derived leads math

```swift
enum DerivedLeads {
    static let derivableFromIandII: Set<Lead> = [.III, .aVR, .aVL, .aVF]
    static let derivableFromV2andV6: Set<Lead> = [.V1, .V3, .V4, .V5]

    static func combineIIIaVRaVLaVF(i: [Float], ii: [Float], target: Lead) -> [Float]
    static func combineV1V3V4V5(v2: [Float], v6: [Float], target: Lead) -> [Float]
}
```

---

## 7. Data layer

### File sources

```swift
// Pathology
protocol PathologySource {
    func readManifest() throws -> PathologyManifest
    func readPathology(id: String) throws -> PathologyFile
    func listPathologies() -> [String]
}

final class AssetPathologySource: PathologySource {
    // reads from Bundle.main / app resources
}

final class FilePathologySource: PathologySource {
    let root: URL
    func isValid() -> Bool
    func writePathology(_ file: PathologyFile) throws   // atomic: .tmp + rename
    func deletePathology(id: String) throws
}

// Course — same shape
protocol CourseSource { … }
final class FileCourseSource: CourseSource {
    let root: URL
    func isValid() -> Bool
    func writeLecture(_ lecture: Lecture) throws
    func writeLectureRaw(courseId: String, lectureId: String,
                         language: String, body: String) throws
    func writeCourse(_ course: Course) throws
    func writeAnswers(courseId: String, lectureId: String,
                      language: String, json: String) throws
    func deleteLecture(courseId: String, lectureId: String, language: String) throws
}
```

### Repositories

```swift
@Observable
final class PathologyRepository {
    private(set) var manifest: PathologyManifest?
    private var source: any PathologySource

    func setSource(_ newSource: any PathologySource)
    @discardableResult func loadManifest() async -> Bool
    func pathologies() -> [PathologyEntry]
    func readPathology(_ id: String) -> PathologyFile?
    func leadWaveform(id: String, lead: Lead) -> Points?   // baseline-zeroed + derived
    func writePathology(_ file: PathologyFile) async -> Bool
    func deletePathology(id: String) async -> Bool
    func duplicatePathology(id: String) async -> String?
}

@Observable
final class CourseRepository {
    private(set) var manifest: CourseManifest?
    // … mirrors PathologyRepository
}
```

### ZIP & file utilities

```swift
enum PathologyZipExtractor {
    static func extract(from url: URL, to targetDir: URL) async -> Bool
    // flattens nested directories; UTF-8 throughout
}

enum CourseZipExtractor {
    static func extract(from url: URL, to targetDir: URL) async -> Bool
}

enum ZipCompressor {
    static func zip(sourceDir: URL, to destURL: URL) async
    static func zipToCache(sourceDir: URL, name: String) async -> URL?
}

enum SampleCourseSeeder {
    static func seed(to targetDir: URL) async -> Bool
    // copies bundled Assets/courses/ → targetDir
}
```

### ECG SVG renderer

```swift
struct EcgTrace {
    let lead: Lead
    let points: Points
}

enum EcgSvgRenderer {
    static func renderSvg(traces: [EcgTrace], pixelScale: PixelScale) -> String
    // produces inline <svg> — same projection math as Canvas rendering
}
```

### Preferences (UserDefaults wrapper)

```swift
final class Preferences {
    private let store: UserDefaults

    // Global
    var treeUrl: URL? { get set }
    var coursesTreeUrl: URL? { get set }
    var languageTag: String? { get set }
    var tcpIp: String? { get set }
    var tcpPort: Int? { get set }
    var isDarkTheme: Bool? { get set }
    var lastOperatingMode: String? { get set }
    var lastEditorRhythmId: String? { get set }
    var lastCourseId: String? { get set }

    // Per-mode helpers
    func gridScheme(mode: String) -> String?
    func lastRhythmId(mode: String) -> String?
    func monitorSpeed(mode: String) -> Float?
    func monitorScale(mode: String) -> Float?
    func monitorDisplayScale(mode: String) -> Float?
    func monitorSeriesCount(mode: String) -> Int?
    func monitorSeriesScheme(mode: String) -> String?
    func comparisonPresets(mode: String) -> String?
    func lastLectureId(mode: String) -> String?

    // Setters (all per-mode variants write to "${mode}_" key)
    func setGridScheme(_ value: String, mode: String)
    func setLastRhythmId(_ value: String?, mode: String)
    func setMonitorSpeed(_ value: Float, mode: String)
    // … etc.
}
```

**Key taxonomy** (identical to Android):

| Prefix | Scope | Examples |
|---|---|---|
| *(none)* | Global | `tree_url`, `language_tag`, `tcp_ip`, `dark_theme` |
| `${mode}_` | Per mode | `${mode}_grid_scheme`, `${mode}_last_rhythm_id` |
| `${mode}_monitor_` | Per-mode monitor | `${mode}_monitor_speed`, `${mode}_monitor_scale` |
| `${mode}_comparison_` | Per-mode compare | `${mode}_comparison_presets` |
| `courses_` | Course global | `courses_tree_url`, `last_course_id` |
| `${mode}_last_` | Per-mode selection | `${mode}_last_lecture_id` |

Per-mode reads fall back to the legacy global key when no per-mode value
exists.

---

## 8. Network layer

Use `Network.framework` (`NWConnection`) — **not** `URLSession` or raw
BSD sockets.

```swift
// Protocol types — identical to Android
enum TcpConnectionState: Equatable {
    case disconnected, connecting, connected, error(String)
}

enum TcpMessage {
    case startCommand(id: String?, sampleRate: Int?, params: [String: String])
    case stopCommand(id: String?)
    case pointsMessage(id: String?, lead: Lead?, identy: String?,
                       offset: Int, values: [Float])
    case uploadMessage(id: String?, filename: String, size: Int64)
    case ackMessage(id: String?, filename: String, bytes: Int64)
}

enum TcpProtocol {
    static func encode(_ message: TcpMessage) -> String
    static func decode(_ line: String) throws -> TcpMessage
    static func decodeOrNil(_ line: String) -> TcpMessage?

    struct ProtocolError: Error { let message: String }
}
```

**Connection lifecycle in `AppViewModel`:**

```swift
private var connection: NWConnection?
private var reconnectTask: Task<Void, Never>?

func connectTcp() {
    reconnectTask = Task {
        while !Task.isCancelled {
            let conn = NWConnection(host: .init(tcpIp),
                                    port: .init(rawValue: UInt16(tcpPort))!,
                                    using: .tcp)
            // … set state handler, start, await connected
            // … send upload archive on connect
            // … drain incoming lines
            // … on disconnect: wait tcpReconnectIntervalMs, retry
        }
    }
}
```

Writes are serialised through a Swift `actor` or `AsyncStream`-based
write queue (replaces Android `Mutex`):

```swift
actor TcpSender {
    func send(_ message: TcpMessage) async throws { … }
}
```

---

## 9. Pixel scaling & calibration

```swift
struct EcgCalibration: Equatable {
    var gainMmPerMv: Float = 10
    var sampleRateHz: Float = 500
    var adcCountsPerMv: Float = 256
    static let `default` = EcgCalibration()
}

struct PixelScale: Equatable {
    let pxPerMm: Float
    let pxPerMv: Float
    let pxPerSec: Float
    let pxPerSample: Float
    let pxPerAdcCount: Float
    let smallGridStepPx: Float
    let largeGridStepPx: Float

    init(pxPerMm: Float,
         paperSpeedMmPerSec: Float = 25,
         gainZoomY: Float = 1,
         cal: EcgCalibration = .default) { … }
}

struct Points: Equatable {
    let values: [Float]    // baseline-zeroed
}
```

`pxPerMm` is computed in `MonitorView`:

```swift
let screenScale = NSScreen.main?.backingScaleFactor ?? 1
// 72 pt = 1 inch = 25.4 mm → 72/25.4 pt/mm
let pxPerMm = Float(screenScale) * Float(72 / 25.4) * mode.displayScale
```

---

## 10. ConstructorViewModel — editor patterns

```swift
enum EditingAlgorithm: String, CaseIterable {
    case cosine, spline, bezier, loess, mls
}

enum ToolMode: String {
    case select, trace, position
}

@Observable
final class ConstructorViewModel {
    // edit state
    var targetFile: PathologyFile?
    var focusedLead: Lead = .II
    var selectedIndex: Int = 0
    var dirtyLeads: Set<Lead> = []
    var isMetadataDirty: Bool = false
    var isSaving: Bool = false

    // editing config
    var editingAlgorithm: EditingAlgorithm = .cosine
    var editingRadius: Int = 100    // 1…1000

    // photo tracing
    var referenceImageUrl: URL?
    var toolMode: ToolMode = .select
    var imageOffset: CGPoint = .zero
    var imageScale: CGFloat = 1
    var imageRotationDeg: Double = 0
    var imageAlpha: Double = 0.5
    var imageLocked: Bool = false
    var ghostTrace: [Int]?          // auto-detect candidate

    // sub-integer accumulator (per-lead, same size as samples)
    private var floatBuffers: [Lead: [Float]] = [:]

    // undo / redo — per lead, depth 20
    private var undoStacks: [Lead: [[Int]]] = [:]
    private var redoStacks: [Lead: [[Int]]] = [:]

    // actions
    func selectPathology(id: String, persist: Bool = true) async
    func selectLead(_ lead: Lead)
    func selectIndex(_ index: Int)
    func selectNext() / func selectPrevious()
    func selectSignificantPoint(type: EcgPointType)
    func moveSelectedUp() / func moveSelectedDown()
    func setSample(lead: Lead, index: Int, adcValue: Int)
    func traceSamples(lead: Lead, updates: [Int: Int])  // batch, no kernel
    func startStroke(lead: Lead)                        // snapshot → undo stack
    func undo(lead: Lead) / func redo(lead: Lead)
    func toggleSignificantPoint(lead: Lead, index: Int, type: EcgPointType)
    func rename(newTitle: String, language: Language)
    func calculateDerivedLeads()
    func revertLead(_ lead: Lead) async
    func save() async
    func deleteCurrentPathology() async
    func duplicateCurrentPathology() async

    // photo
    func setReferenceImageUrl(_ url: URL?)  // sets toolMode = .position
    func setToolMode(_ mode: ToolMode)
    func setImageOffset(_ offset: CGPoint)
    func setImageScale(_ scale: CGFloat)
    func setImageRotation(_ deg: Double)
    func setImageAlpha(_ alpha: Double)
    func setImageLocked(_ locked: Bool)
    func resetImageTransform()
    func setGhostTrace(_ trace: [Int]?)
    func applyGhostTrace()

    // constants
    static let defaultEditingRadius = 100
    static let minEditingRadius = 1
    static let maxEditingRadius = 1000
    static let maxUndoDepth = 20
    static let adcMin = 0
    static let adcMax = 2048
}
```

---

## 11. CourseConstructorViewModel

```swift
@Observable
final class CourseConstructorViewModel {
    let repository: CourseRepository
    let mode: OperatingMode
    let prefs: Preferences?

    var selectedCourseId: String?
    var lectures: [LectureEntry] = []
    var selectedLectureId: String?
    var draft: String = ""
    var answers: [String: [String: String]] = [:]   // quizId → (row,col → value)
    var previewLecture: Lecture?
    var isDirty: Bool { draft != savedText || answers != savedAnswers }
    var isSaving: Bool = false

    func setLanguage(_ tag: String)
    func selectCourse(_ courseId: String) async
    func selectLecture(_ lectureId: String) async
    func setHtml(_ text: String)     // updates draft + schedules 200ms preview parse
    func insertSnippet(_ html: String)
    func setTableCell(quizId: String, row: Int, col: Int, value: String)
    func save() async
    func revert()
    func createCourse(id: String, title: String) async
    func createLecture(id: String, title: String) async
    func renameLecture(newTitle: String) async
    func deleteLecture() async
    func restore() async             // one-shot restore from prefs

    private var savedText: String = ""
    private var savedAnswers: [String: [String: String]] = [:]
    private var previewTask: Task<Void, Never>?
    private static let previewDebounceMs: UInt64 = 200_000_000
}
```

---

## 12. CourseViewerViewModel

```swift
@Observable
final class CourseViewerViewModel {
    let repository: CourseRepository
    let mode: OperatingMode
    let prefs: Preferences?

    var selectedCourseId: String?
    var lectures: [LectureEntry] = []
    var selectedLectureId: String?
    var lecture: Lecture?

    func setLanguage(_ tag: String)
    func selectCourse(_ courseId: String) async
    func selectLecture(_ lectureId: String) async
    func closeLecture()
    func restore() async
}
```

---

## 13. MonitorViewModel

```swift
@Observable
final class MonitorViewModel {
    let mode: OperatingMode
    let prefs: Preferences?

    var monitorMode: MonitorModeModel = .init()

    var availableSeriesCounts: [Int] {
        monitorMode.isCompareMode ? [1,2,3,4,5,6,12] : [1,6,12]
    }
    var availableSeriesSchemes = SeriesScheme.allCases
    var availableSpeeds: [Float] {
        mode == .teaching ? [12.5, 25, 50] : [25, 50]
    }
    var availableScales: [Float] = [1,2,3,4,5]

    func setSeriesCount(_ count: Int, persist: Bool = true)
    func setSeriesScheme(_ scheme: SeriesScheme, persist: Bool = true)
    func setGridScheme(_ scheme: GridScheme, persist: Bool = true)
    func setSpeed(_ speed: Float, persist: Bool = true)
    func setScale(_ scale: Float, persist: Bool = true)
    func setDisplayScale(_ scale: Float, persist: Bool = true)
    func setCalibration(_ cal: EcgCalibration)
    func setIsRunning(_ running: Bool)
    func toggleCompareMode(defaultPathologyId: String? = nil)
    func setComparisonTarget(paneIndex: Int, target: ComparisonTarget)
    func saveCurrentAsPreset(name: String)
    func applyPreset(_ preset: ComparisonPreset)
}
```

---

## 14. Localization

### UI strings

Use SwiftUI `LocalizedStringKey` and `.strings` files. Never hardcode
display text. All string keys mirror the Android `strings.xml` names.

```swift
// Example
Text("mode_teaching")               // resolves in Localizable.strings
Text(OperatingMode.teaching.titleKey)
```

Supported languages: `en` (base), `ru`, `zh`, `es`. Add `.lproj` folders
for each.

### Language switching

Language switching is in-process (no app restart):

```swift
// In AppViewModel
func updateLanguage(_ language: Language, persist: Bool = true) {
    selectedLanguage = language
    // Update Bundle locale override
    UserDefaults.standard.set([language.tag], forKey: "AppleLanguages")
    // Notify CourseViewerViewModel and CourseConstructorViewModel
    // to reload the open lecture in the new language
}
```

### Content files

- Files: `<lecture-id>.en.html`, `<lecture-id>.ru.html`, …
- Fallback language constant: `"en"`.
- The `FileCourseSource.readLecture(courseId:lectureId:language:)` method
  tries `language` first, then `"en"`. The returned `Lecture.language`
  records which variant was actually loaded.

---

## 15. WKWebView integration (LectureWebView)

```swift
struct LectureWebView: NSViewRepresentable {
    let lecture: Lecture
    let resolveEcg: (String, Lead?) -> [EcgTrace]
    var answers: [String: [String: String]] = [:]
    var onCellEdit: ((String, Int, Int, String) -> Void)? = nil

    func makeNSView(context: Context) -> WKWebView { … }
    func updateNSView(_ webView: WKWebView, context: Context) { … }

    // Rewrite pipeline (called before loadHTMLString):
    // 1. Find all <ecg pathology="…" lead="…"> elements
    // 2. Call resolveEcg → [EcgTrace]
    // 3. Call EcgSvgRenderer.renderSvg → inline <svg>
    // 4. Replace <ecg> with <figure><svg>…</svg></figure>
    // 5. Inject KaTeX auto-render script from bundled assets/katex/
    // 6. Inject quiz answer values into <input> cells
    // 7. Set WKUserContentController message handler for cell edits
}
```

Keep the WebView **in the SwiftUI view tree at all times** when the
overlay is visible. Use `.opacity(showOverlay ? 1 : 0)` + `allowsHitTesting(showOverlay)` to hide without unmounting (preserves scroll
position and quiz state).

---

## 16. ECG grid drawing

```swift
// Modifier equivalent — drawn via Canvas overlay
extension View {
    func ekgGrid(scheme: GridScheme, xOffsetPx: CGFloat = 0) -> some View {
        self.overlay {
            EcgGridCanvas(scheme: scheme, xOffsetPx: xOffsetPx)
        }
    }
}

struct EcgGridCanvas: View {
    let scheme: GridScheme
    let xOffsetPx: CGFloat
    @Environment(\.pixelScale) var scale

    var body: some View {
        Canvas { ctx, size in
            // fill background
            // draw small grid (pxPerMm) with 0.5pt stroke
            // draw large grid (pxPerMm * 5) with 1.5pt stroke
            // translate by xOffsetPx.truncatingRemainder(dividingBy: largeStep)
        }
    }
}
```

| Scheme | Background | Small grid | Large grid |
|---|---|---|---|
| `.pink` | `#FFF5F5` | `#FDE4E4` | `#F9BDBD` |
| `.blueGray` | `#F0F4F7` | `#DDE4E9` | `#BCC6CF` |
| `.blank` | `#FFFFFF` | *(none)* | *(none)* |

---

## 17. TraceOverlay

Freehand tracing in the Constructor. Replaces `SampleHandleOverlay` when
`toolMode == .trace`.

```swift
struct TraceOverlay: View {
    let sampleCount: Int
    let baseline: Int
    let onStrokeStart: () -> Void
    let onTrace: ([Int: Int]) -> Void

    @Environment(\.pixelScale) var scale

    var body: some View {
        // DragGesture with .onChanged:
        //   index = Int(location.x / scale.pxPerSample)
        //   value = baseline + Int((baselineY - location.y) / scale.pxPerAdcCount)
        //   interpolate across skipped columns
        //   call onTrace(updates)
        // DragGesture with .onBegan: call onStrokeStart()
        Color.clear.contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { … }
                .onEnded { … })
    }
}
```

---

## 18. What NOT to do

- **Do not** use `ObservableObject` + `@Published` — use `@Observable`.
- **Do not** use `@EnvironmentObject` as the primary DI mechanism — pass
  dependencies through initialisers.
- **Do not** use `DispatchQueue.main.async` where `@MainActor` suffices.
- **Do not** use `NavigationSplitView` for side drawers — it changes layout.
- **Do not** use `@State` for anything that needs to survive a view rebuild
  — it should be in a ViewModel.
- **Do not** mix platform I/O into the domain layer.
- **Do not** re-serialize lecture HTML through `WKWebView` or `XMLParser` —
  write back `Lecture.rawHtml` verbatim.
- **Do not** hardcode display strings — use `LocalizedStringKey`.
- **Do not** unmount `WKWebView` on overlay hide — use opacity/hit-testing.
- **Do not** place the reference image outside the `EditableLead` coordinate frame.
- **Do not** call `loadHTMLString` on every `View.updateNSView` call — diff
  the lecture `id` + `language` and reload only when they change.

---

## 19. Verification checklist

- [ ] Language switching is instantaneous; no app restart required.
- [ ] Content files fall back to English when the requested `.html` is absent.
- [ ] Dirty-state flags clear only on confirmed save success.
- [ ] Per-mode ViewModel state survives mode switching.
- [ ] Last rhythm / course / lecture restores from `UserDefaults` on cold start.
- [ ] TCP reconnects every 5 s on drop; re-uploads the dataset on each connect.
- [ ] Atomic writes: crash mid-save leaves previous file intact (`.tmp` + rename).
- [ ] Undo/redo is per-lead and does not affect other leads.
- [ ] Course overlay `WKWebView` retains scroll position when toggled.
- [ ] Comparison presets round-trip through `UserDefaults` correctly.
- [ ] Photo underlay: gestures in `.select` / `.trace` mode do not move the image.
- [ ] Manifest version mismatch throws `FormatError`, not a silent mis-parse.
- [ ] `decode` throws on bad JSON; `decodeOrNil` returns `nil` silently.
- [ ] Canvas waveform and ECG grid scroll in sync (single `xOffsetPx` source).
- [ ] `pxPerMm` updates on window-density change (`NSScreen.backingScaleFactor`).
