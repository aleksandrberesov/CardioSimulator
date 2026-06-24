# WFDB signal-format support + PhysioNet import (Android)

**Status:** active (not started — full spec ready; Windows reference shipped & tested)
**Owner:** alexandr.beresov@gmail.com
**Started:** 2026-06-20
**Related issues / PRs:** —

## Goal

Bring the Android app to parity with the WinUI port's new **WFDB / PhysioNet**
feature: read and write WFDB records (`.hea` header + `.dat`/`.mat` signal
files), convert them to/from the app's native pathology dataset, download
records directly from physionet.org, and expose all of it as an **Import**
action in the Pathology Constructor. The reference implementation already
exists and is fully unit-tested on Windows
(`CardioSimulatorWin/src/CardioSimulator.Core/Data/Wfdb/*`,
`Network/PhysioNetClient.cs`, 147 Core tests green); this plan ports it 1:1 to
Kotlin so a downloaded/loaded 12-lead record opens directly in the editor and
renders on the monitor. Why now: the Windows side just landed it and the two
apps are kept at faithful parity.

## Current state

Android mirrors the Windows layering, so every touch-point already has a twin:

- **Sources/repo (writable path):**
  - `data/PathologySource.kt` — read-only interface (`readManifest`,
    `readPathology`, `listPathologies`).
  - `data/FilePathologySource.kt:41` `writePathology(file, leadOrder)` writes
    `<id>.dat` **and** adds/updates the `manifest.txt` entry atomically;
    `:80` `deletePathology`. This is the persistence hook the importer reuses.
  - `data/PathologyRepository.kt:50` `writePathology`, `:75`
    `duplicatePathology(id)`, `:86` `createPathology(id, titleEn, nameRu)`.
    Note: Android `createPathology` takes an explicit `id` (unlike Windows).
- **Calibration / domain (conversion target):**
  - `data/EcgCalibration.kt` — `adcCountsPerMv = 256f`, default baseline 1024.
    Same coordinate system as Windows, so the converter maps WFDB raw ADC →
    `1024 + mv*256` exactly as `WfdbConverter` does.
  - `domain/Pathology.kt:14` `enum class Lead` with `:18` `fromToken(raw)` —
    maps a WFDB signal description (`"I"`, `"aVR"`, `"V1"`…) to a `Lead`.
  - `domain/Pathology.kt` `PathologyFile`, `LeadStream`, `PathologyManifest`,
    `PathologyEntry`; `domain/PathologyParser.kt` `serializeManifest`.
- **Constructor (where Import lands):**
  - `ui/viewmodels/ConstructorViewModel.kt:44` ctor takes
    `PathologyRepository`; `:279` `selectPathology(id, persist)`;
    `createNewPathology()`, `duplicateCurrentPathology()`,
    `deleteCurrentPathology()`.
  - `ui/screens/ConstructorScreen.kt:91` already uses
    `rememberLauncherForActivityResult(ActivityResultContracts.GetContent())`
    for the reference image; `:280` the toolbar `IconButton`s (New / rename /
    duplicate / delete). Import button slots in next to New.
  - `RhythmViewModel` observes the repository manifest, so a newly written
    pathology surfaces in the rhythm drawer automatically (same as New).
- **Networking:** `network/TcpProtocol.kt` uses raw sockets; **no HTTP
  library** is on the classpath (`app/build.gradle.kts`). PhysioNet should use
  `java.net.HttpURLConnection` on `Dispatchers.IO` (zero new deps).
- **Permissions:** `AndroidManifest.xml:5` already declares
  `android.permission.INTERNET` (and `ACCESS_NETWORK_STATE`) — no manifest
  change needed.
- **Tests:** JUnit under `app/src/test/java/com/example/cardiosimulator/...`
  (e.g. `data/PathologyParserTest.kt`).
- **Sample data:** a real WFDB record set lives at
  `CardioSimulatorWin` additional dir `…/Data/010` (Chapman-Shaoxing,
  `JS00001.hea` + `JS00001.mat`); the Android repo also has a root `samples/`
  dir. Copy one small record into `app/src/test/resources/wfdb/` for a
  deterministic ground-truth test.

### WFDB format facts (so the implementer needn't rediscover)

- **`.hea` record line:** `name nsig [fs] [nsamp] [basetime] [basedate]`,
  e.g. `JS00001 12 500 5000`.
- **`.hea` signal line:**
  `file fmt[xN][:skew][+offset] gain[(baseline)]/units adcres adczero initval checksum blocksize description`,
  e.g. `JS00001.mat 16+24 1000/mV 16 0 -254 21756 0 I`. Lines starting `#` are
  comments. If baseline is absent it defaults to `adczero`; gain `0` means
  uncalibrated → default gain 200.
- **`.mat`** is a MATLAB Level-4 file: 20-byte header (5 little-endian int32:
  type, rows, cols, imagf, namelen), then the name (`val\0`), then int16 data
  **column-major**. WFDB writes `type=30` (LE, int16, full), shape
  `[channels x samples]`. The `+24` byte offset in the `.hea` = 20 + `len("val\0")`.
- **Key simplification:** column-major `[channels x samples]` is byte-identical
  to frame-interleaved format-16 ordering, so the same reshape decodes both
  `.mat` and raw `.dat`.
- **Checksum** = low 16 bits of the sum of a signal's samples, as a signed
  `short`.

## Non-goals

- No new third-party dependencies (no OkHttp/Retrofit — use `HttpURLConnection`).
- Writing only supports **format 16** (`.dat`) and `.mat`; reading supports
  16/61/80/212/24/32 + `.mat`. Other write formats are out of scope.
- No PhysioNet browse/search UI — the user types a project path + record name.
- No re-export of the dataset to WFDB from a batch tool (the converter exists,
  but no bulk UI).
- No change to the existing `.dat` pathology format or manifest schema.

## Plan

Port the Windows `Data/Wfdb/*` files to Kotlin under
`data/wfdb/` (package `com.example.cardiosimulator.data.wfdb`). Each Windows
file maps to one Kotlin file; behavior must match exactly (the Windows tests
are the spec).

### Phase 1 — WFDB core: models, header, MAT, codec (pure, unit-testable)
- `data/wfdb/WfdbModels.kt` ← `WfdbModels.cs`: `WfdbConstants`,
  `WfdbSignalSpec` (with `effectiveBaseline`/`effectiveGain`), `WfdbHeader`,
  `WfdbRecord(header, samples: Array<IntArray>)`.
- `data/wfdb/WfdbHeaderParser.kt` ← `WfdbHeaderParser.cs`: `parse(text)` /
  `serialize(header)`. Reuse regexes for the `fmt[xN][:skew][+off]` and
  `gain[(baseline)]/units` fields; comments → list (text after `#`).
- `data/wfdb/MatlabLevel4.kt` ← `MatlabLevel4.cs`: `readMatrix(bytes)` and
  `writeInt16Matrix(name, rows, cols, dataColumnMajor)` using `ByteBuffer` +
  `ByteOrder.LITTLE_ENDIAN`. `dataOffset(name) = 20 + name.length + 1`.
- `data/wfdb/WfdbSignalCodec.kt` ← `WfdbSignalCodec.cs`: `decode(...)`,
  `decodeFlat(format, …)` for 16/61/80/212/24/32, `encode(16, …)`,
  `reshape`/`flatten`. Port the 212 bit-packing and the truncation checks.
- **Tests** (port the Windows xUnit cases to JUnit):
  `WfdbHeaderParserTest`, `WfdbSignalCodecTest`, `MatlabLevel4Test` —
  round-trips + the 212/80/61 known vectors.
- **Shippable:** new package, nothing wired in yet.

### Phase 2 — WFDB reader/writer facade + converter
- `data/wfdb/WfdbReader.kt` ← `WfdbReader.cs`: `readHeader(path)`,
  `readRecord(path)`, and crucially the in-memory overload
  `readRecord(header, resolver: (String) -> ByteArray)` (group signals by
  file; `.mat` via `MatlabLevel4`, `.dat` via codec). The resolver overload is
  what both SAF import and PhysioNet use — neither has a tidy filesystem path.
- `data/wfdb/WfdbWriter.kt` ← `WfdbWriter.cs`: `writeRecord(record, dir,
  storage)` (enum `Dat`/`Mat`) + a `build(...)` that returns header + bytes
  without disk I/O; recompute checksum + initial values.
- `data/wfdb/WfdbConverter.kt` ← `WfdbConverter.cs`:
  `toPathologyFile(record, id, titleEn, nameRu)` (raw ADC → `1024 + mv*256`,
  `Lead.fromToken` on the description, skip unrecognized signals) and
  `fromPathologyFile(file, …)`.
- **Tests:** `WfdbReadWriteTest` (synthetic round-trip for Dat + Mat),
  `WfdbConverterTest`, plus a ground-truth test reading
  `app/src/test/resources/wfdb/JS00001.*` asserting every lead's decoded
  initial value and recomputed checksum equal the header.
- **Shippable:** full read/write library, still no UI.

### Phase 3 — Import persistence (Core wiring)
- `data/FilePathologySource.kt`: add `importPathology(file): String?` — derive
  a unique id from `file.id`/title (reuse the existing sanitize +
  unique-id helpers), then `writePathology(file.copy(id = newId))` (which adds
  the manifest entry). Mirror Windows `FilePathologySource.ImportPathology`.
- `data/PathologyRepository.kt`: add `importPathology(file): String?` —
  `(source as? FilePathologySource)?.importPathology(file)?.also { loadManifest() }`.
- `ui/viewmodels/ConstructorViewModel.kt`: add `importPathology(file): String?`
  → `repository.importPathology(file)?.also { selectPathology(it) }`.
- **Tests:** `WfdbImportTest` — convert→import→`readPathology` returns the
  rescaled samples (+1 mV→1280, 0→1024, −1 mV→768); manifest gains an entry;
  two imports of the same record get distinct ids.

### Phase 4 — Constructor UI: Import from file
- In `ConstructorScreen.kt`, add an **Import** `IconButton`
  (`Icons.Default.FileDownload` or `Upload`) next to New, opening a small menu
  (Compose `DropdownMenu`) with "Import WFDB file…" and "Download from
  PhysioNet…".
- File path uses SAF. **A WFDB record is multiple files and SAF content Uris
  don't expose siblings**, so use
  `rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments())`
  and have the user select the `.hea` **and** its `.mat`/`.dat` together. Read
  each `Uri` via `contentResolver.openInputStream(...).readBytes()`, key a map
  by display name, parse the `.hea`, then call
  `WfdbReader.readRecord(header) { name -> bytesByName[name] }`.
  (Fallback option if multi-select UX is poor: `OpenDocumentTree()` → pick the
  record's folder → list children.)
- Show a confirm dialog: "N signals · M ECG leads · K samples @ fs Hz" + an
  editable name (default from a `Title:` comment else the record name), then
  `WfdbConverter.toPathologyFile` → `viewModel.importPathology`. Disable
  Import when 0 recognized leads.
- Do decode/convert off the main thread (`viewModelScope` +
  `Dispatchers.Default`).
- **Verify:** import the bundled `Data/010/JS00001` (.hea + .mat); it appears
  in the rhythm drawer and renders 12 leads.

### Phase 5 — PhysioNet download + polish
- `network/PhysioNetClient.kt` ← `PhysioNetClient.cs`: suspend
  `downloadRecord(projectPath, record): WfdbRecord` building
  `https://physionet.org/files/<projectPath>/<record>.hea`, downloading the
  header + each distinct signal file via `HttpURLConnection` on
  `Dispatchers.IO`, then `WfdbReader.readRecord(header, resolver)` over an
  in-memory byte map. Also `downloadRecordFiles(...)` and `listRecords(...)`
  (the `RECORDS` index). Set a `User-Agent`. Wrap failures in a
  `PhysioNetException`.
- Constructor "Download from PhysioNet…" dialog: **Project path**
  (`mitdb/1.0.0` or `challenge-2021/1.0.3/training/chapman_shaoxing/g1`) +
  **Record** (`100` / `JS00001`), a progress indicator, inline error text;
  on success run the same confirm→convert→import flow as Phase 4.
- **Verify:** download `mitdb/1.0.0` record `100` (format 212) end-to-end on a
  device/emulator with network.

## Risks & open questions

- **SAF multi-file selection (Phase 4).** This is the main Android-specific
  divergence from Windows (which had a real file path + sibling `.hea`). The
  recommended answer is `OpenMultipleDocuments` (pick `.hea` + signal file);
  alternative is `OpenDocumentTree`. Decide during Phase 4 based on UX; the
  `readRecord(header, resolver)` overload supports either without changing
  core code. *(Open — resolve in Phase 4.)*
- **Format coverage for PhysioNet.** Many classic DBs use format 212 (handled).
  If a record uses an unsupported format the reader throws a clear
  "Unsupported WFDB read format" — surface that message, don't crash.
- **Large-record performance.** Decoding/serializing 12×5000 ints is fine off
  the main thread; keep it on `Dispatchers.Default`/`IO`. `.dat` text
  serialization of big records is the slowest part (same as Windows).
- **Sample-range overflow.** WFDB amplitudes can map outside the editor's
  0–2048 ADC clamp; rendering tolerates it, editing clamps. Don't clamp in the
  converter (stay faithful), as on Windows.
- **Endianness.** Use `ByteOrder.LITTLE_ENDIAN` everywhere except format 61
  (big-endian) and the 212 bit-packing.

## Verification

- Each phase: `./gradlew :app:testDebugUnitTest` green (new JUnit suites pass,
  ported from the Windows xUnit cases).
- Phase 2 ground-truth: reading the real `JS00001` reproduces every signal's
  declared initial value and checksum.
- Phase 4/5: manual on emulator — import `JS00001` from files and download
  `mitdb/1.0.0`/`100`; both appear in the drawer and render 12 leads; renaming
  works; error paths show a dialog rather than crashing.
- `./gradlew :app:assembleDebug` builds clean.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | WFDB core: models, header parser, MAT v4, signal codec | 1 | pure + tests |
| 2 | WFDB reader/writer facade + PathologyFile converter | 2 | + ground-truth test |
| 3 | Import persistence (FilePathologySource/Repository/VM) | 3 | + import test |
| 4 | Constructor: Import WFDB file (SAF multi-doc) | 4 | UI + confirm dialog |
| 5 | PhysioNet download + dialog | 5 | HttpURLConnection, INTERNET already granted |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:** —
- **PRs:** —
- **Deviations from plan:** —
- **Follow-ups spawned:** —
