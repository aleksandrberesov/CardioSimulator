# Plan: Port the encrypted content-pack pipeline (CSP2) to Android

**Created:** 2026-07-16
**Status:** ACTIVE — NOT STARTED
**Direction:** **Windows → Android**

**Target (Android) source root:** `E:\VLN_Project\CardioSimulator\app\src\main\java\com\example\cardiosimulator\`
**Reference (Windows) source root:** `E:\VLN_Project\CardioSimulatorWin\src\`

**Depends on:** `CardioSimulatorWin\docs\plans\sync\2026-07-android-delta-binary-dataset-parity.md`
(the CSD1 delta-binary `.dat` decoder). **Packs contain CSD1 waveforms — without that decoder a pack
opens but every rhythm fails to parse.** Do it first, or fold it in as Phase 0.

---

## 1. Background & goals

### What shipped on Windows (2026-07-16)

Two changes landed together:

1. **Pack-only.** The app no longer accepts plaintext ZIPs or extracted folders for *either* dataset
   (ECG + courses). Encrypted `*.pak` is the only input. Every zip/text path was deleted from the
   app layer; nothing plaintext is ever written to `%LOCALAPPDATA%`.
2. **CSP2 — a chunked container that is read lazily.** The old `CSP1` wrapped the whole ZIP in one
   AES-GCM message, so it could only be decrypted whole. `CSP2` frames the plaintext into 64 KiB
   chunks, each its own AES-GCM message with its own nonce and tag, so a pack is randomly accessible
   straight off disk.

Measured on the real 1.67 GB / 45,206-record pack (Windows):

| | CSP1 | CSP2 |
|---|---|---|
| Steady managed memory | 1,671 MB | **42 MB** |
| Peak allocation at open | 3,344 MB | **205 MB** |
| App working set | ~3.4 GB | **~380 MB** |
| Max pack size | 2,047 MB (hard `byte[]` cap) | **no limit** |

### Why this matters more on Android than it did on Windows

**On Android the CSP1 approach is not merely wasteful — it is impossible.** An Android app's heap is
capped (commonly 128–512 MB, `largeHeap` buys a little more, and it is per-device). Decrypting a
1.67 GB pack into a single `ByteArray` would OOM instantly, and `ByteArray` is itself `Int`-indexed
(max ~2 GB) exactly like .NET's `byte[]`. So:

> Android can never ship the large dataset without the chunked container. CSP2 is not an
> optimisation here; it is the enabling change.

### Android's starting point — verify, but expect nothing

There is **no pack support on Android at all.** Greps across `app/src` for `ContentCrypto`, `CSP1`,
`CSP2`, `.pak`, `javax.crypto`, `AES/GCM` return **zero matches**. The data layer is entirely
plaintext/zip:

- `data/AssetPathologySource.kt` — bundled dataset from `assets/`
- `data/FilePathologySource.kt`, `data/PathologyZipExtractor.kt`
- `data/FileCourseSource.kt`, `data/CourseZipExtractor.kt`, `data/SampleCourseSeeder.kt`
- `data/ZipCompressor.kt`, `data/DataSourcePrefs.kt` (SAF tree URI of the picked ZIP)

**The upside of being this far behind: there is no CSP1 reader to stay compatible with, so Android
should implement CSP2 *only* and never grow a CSP1 code path.** All vendor packs were migrated to
CSP2 on 2026-07-16 (7 packs in `E:\VLN_Project\Data`, plus both bundled `Assets\*.pak`), so nothing
in the field needs CSP1 on Android. This costs nothing today and doubles in cost if deferred.

### Desired Android outcome

- The app loads its ECG + course data from `*.pak` only, decrypting lazily, never extracting.
- A large pack (hundreds of MB to multi-GB) opens on a phone without OOM.
- No plaintext dataset ever lands in app storage or cache.

---

## 2. The load-bearing problem: Java has no `ZipArchive(Stream)`

This is the single biggest divergence from Windows and must be settled before any code is written.

The Windows design is `ZipArchive` sitting on a seekable decrypt-on-demand `Stream`. **The JDK has no
equivalent:**

- `java.util.zip.ZipFile` requires a **real `File`** on disk — which is exactly the plaintext-on-disk
  the whole feature forbids. Not an option.
- `java.util.zip.ZipInputStream` is **sequential-only**. It cannot seek to the end-of-central-
  directory record, so it cannot index a pack; you would have to stream the entire 1.67 GB to find
  one record.

**Recommended: `org.apache.commons.compress.archivers.zip.ZipFile(SeekableByteChannel)`.** Commons
Compress is pure Java, works on Android, and its `ZipFile` accepts a `SeekableByteChannel` — the JDK
gap this plan needs closed. So the port becomes:

```
CSP2 pack file ──► ChunkedPackChannel : SeekableByteChannel  (decrypts 64 KiB chunks on demand)
                        └──► commons-compress ZipFile ──► entries ──► CSD1 parser
```

Add to `app/build.gradle.kts`: `implementation("org.apache.commons:commons-compress:1.26.2")`
(verify current version; it is small and has no native deps).

**Alternatives if that is rejected** (record the decision here):
- *(a)* Hand-write a ZIP central-directory reader over the channel (~250–350 lines: EOCD + Zip64
  EOCD, central directory, local headers, `Inflater` for Deflate). Doable, but it is real parsing
  code with real edge cases (Zip64 matters — the 45k pack has >65,535 entries).
- *(b)* Change the container to drop ZIP-in-a-blob entirely in favour of a self-indexed format with
  an encrypted table of contents. This was the runner-up design on Windows and is *easier* to port,
  but it forks the format across platforms — **only do this if it is done on Windows too.**

> **Open question (decide in Phase 1):** commons-compress vs hand-written reader. Recommendation:
> commons-compress. Do not silently pick (a) — it is a week of work hiding in a "small" decision.

---

## 3. The format — implement exactly, or packs will not open

Ported from `CardioSimulatorWin\src\CardioSimulator.Core\Data\ChunkedPack.cs` and `ContentCrypto.cs`.
Every value below is load-bearing; a single mismatch means "wrong key / corrupt pack" and nothing else.

### Container layout (little-endian)

```
off  len  field
  0    4  magic "CSP2"
  4    1  version = 1
  5    3  reserved (0)
  8   16  salt          - fresh per pack; PBKDF2 input
 24    8  nonceBase     - fresh per pack; high 8 bytes of every nonce
 32    4  chunkSize     - plaintext bytes per chunk (65536)
 36    8  plainLength   - total plaintext length
 44    4  reserved (0)
 48   16  headerTag     - GCM tag over an EMPTY plaintext, AAD = bytes[0..48]
 64   ..  chunk 0: ciphertext(len0) || tag(16)
          chunk 1: ciphertext(len1) || tag(16)  ...
```

- `HeaderLen = 64`, `TagLen = 16`, `chunkSize = 65536`.
- Every chunk but the last is exactly `chunkSize` plaintext bytes, so
  **`frameOffset(i) = 64 + i * (chunkSize + 16)`** — pure arithmetic, no index table to load.
- `chunkCount = ceil(plainLength / chunkSize)`; `0` iff `plainLength == 0`.
- Last chunk's plaintext length = `plainLength - (chunkCount-1) * chunkSize`.
- **Validate at open:** file length must equal
  `64 + (chunkCount-1)*(chunkSize+16) + lastLen + 16` (or `64` when empty). Rejects truncation/padding.

### Crypto

- **AES-256-GCM**, 12-byte nonce, 16-byte tag.
  Java: `Cipher.getInstance("AES/GCM/NoPadding")`, `GCMParameterSpec(128, nonce)` — note **128 is
  bits**, matching .NET's 16 *bytes*.
- **Nonce = `nonceBase(8) || big-endian uint32(chunkIndex)`.** The header uses the reserved index
  `0xFFFFFFFF`; max chunk index is `0xFFFFFFFE`.
  > **This is the most dangerous line in the port.** Salt *and* nonceBase are fresh-random per pack,
  > so the derived key is unique per pack and each chunk index is used exactly once under it. Nonce
  > reuse under one key collapses GCM's confidentiality **and** authenticity. Nothing here may ever
  > be made deterministic. Write the nonce-uniqueness test first.
- **Per-chunk AAD = `big-endian uint32(index) || isFinal(1 byte)`** (5 bytes) →
  `cipher.updateAAD(aad)` before `doFinal`. Reordering and truncation are then detectable.
- **Header tag:** encrypt an *empty* plaintext with nonce index `0xFFFFFFFF` and AAD = `header[0..48]`;
  verify at open before reading any chunk. This binds salt, nonceBase, chunkSize and plainLength.

### Key derivation — must match Windows byte-for-byte

From `ContentCrypto.DeriveKey` / `ContentCrypto.Secret` (`ContentCrypto.cs:100-143`):

- `PBKDF2WithHmacSHA256`, **100,000 iterations**, **32-byte** key, salt = the header's 16 bytes.
  Java: `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` +
  `PBEKeySpec(secretAsChars?, salt, 100_000, 256)` — **careful:** `PBEKeySpec` takes a `char[]` and
  Java's PBKDF2 encodes it as UTF-8; the Windows side feeds **raw bytes**. If the secret bytes are
  not valid UTF-8 this silently derives a *different* key. Use a raw-byte PBKDF2
  (e.g. BouncyCastle `PKCS5S2ParametersGenerator`, or hand-rolled HMAC-SHA256 PBKDF2) rather than
  `PBEKeySpec`. **Verify against a known Windows pack before building anything on top.**
- The secret is two XOR-masked byte arrays combined as `a[i] ^ b[i] ^ 0x5A` (mask `0x5A`), assembled
  at call time so it never appears as one constant. Port both arrays verbatim from
  `ContentCrypto.cs:118-143`. Same threat model as Windows: casual-copy protection, **not** DRM —
  the key ships in the binary. Consider R8/ProGuard obfuscation, and do not add logging that prints
  key material.

> **Non-negotiable acceptance gate for Phase 1:** Android opens `Pathologies.regrouped.subset-500.pak`
> (18 MB, CSP2, in `E:\VLN_Project\Data`) and reads `manifest.txt` + one `.dat`. If the key
> derivation is off by one byte this fails immediately — which is exactly what you want.

---

## 4. Plan

### Phase 0 — CSD1 delta-binary decoder *(prerequisite)*
- Land `2026-07-android-delta-binary-dataset-parity.md`. Packs contain CSD1 `.dat`; without it every
  pathology in a pack fails to parse.

### Phase 1 — CSP2 read core (no wiring)
- `data/crypto/ContentCrypto.kt` — secret assembly, PBKDF2 (raw-byte!), `looksLikePack(bytes)`.
- `data/crypto/ChunkedPack.kt` — layout constants, `frameOffset`, `chunkCount`, `chunkLength`,
  `expectedFileLength`, `writeNonce`, `writeAad`.
- `data/crypto/ChunkedPackChannel.kt` — `SeekableByteChannel` over the pack: an LRU of ~8 decrypted
  64 KiB chunks, one `Cipher`, key held once and zeroed on close. Reads that straddle chunk
  boundaries must loop. Guard with a lock — the cache is shared mutable state.
- Add commons-compress; `data/EncryptedArchive.kt` = `ZipFile(ChunkedPackChannel)` + `readByName` /
  `readPath` / `entryPaths` / `fileNamesWithExtension`, mirroring
  `CardioSimulatorWin\src\CardioSimulator.Core\Data\EncryptedArchive.cs`.
- Source of the file: `contentResolver.openFileDescriptor(uri, "r")` →
  `FileInputStream(pfd.fileDescriptor).channel` gives a **seekable** `FileChannel` for a local SAF
  document. (Caveat below.)

### Phase 2 — Sources + load the bundled pack
- `EncryptedPathologySource` / `EncryptedCourseSource` over `EncryptedArchive`, matching the Windows
  read semantics exactly (flat `readByName` for pathologies, tree `readPath` for courses; every read
  returns null rather than throwing on a missing entry).
- Bundle `Assets/Pathologies.pak` + `Courses.pak` (the 500-subset; **18 MB is fine in `assets/`, the
  1.67 GB pack is not** — see risks) and load them at startup.
- **`assets/` is compressed and served through `AssetManager`, which does not give a seekable
  channel.** Either mark `.pak` as `noCompress` and use `openFd()` → `FileDescriptor` + offset/length
  (a slice of the APK — workable), or copy the bundled pack once into `filesDir` on first run (it is
  *ciphertext*, so this does **not** violate the no-plaintext invariant). Prefer the copy: simpler,
  and it makes the bundled and user-picked paths identical. Decide in Phase 2.

### Phase 3 — Writable overlay (keeps the Constructor working)
- Android has **no Full/Limited edition split** (no `AppEdition`/`BuildConfig.LIMITED` — greps are
  empty), so unlike Windows this is **not** optional: the Constructor exists in the only build, and
  once the dataset is a read-only pack, editing breaks without an overlay.
- Port `WritableEncryptedOverlay` + `Overlay{Pathology,Course}Source`: copy-on-write, deltas in a
  small encrypted pack in app storage, delete-bundled = tombstone. **It must be encrypted at rest** —
  editing/duplicating copies decrypted bundle content into it, so a plaintext overlay would let
  "duplicate everything" reconstruct the dataset.
- **Per-pack overlays**, keyed by a hash of the pack identity (Windows: `AppPaths.PathologyOverlayPakFor`).
  One shared overlay would replay one pack's edits/tombstones onto another pack's ids.
- Overlay `.dat` entries are written as **CSD1 binary**, not text (Windows learned this the hard way:
  a text overlay silently de-optimised every export).

### Phase 4 — Pack-only cutover
- Delete the plaintext paths: `PathologyZipExtractor`, `CourseZipExtractor`, `SampleCourseSeeder`'s
  zip seeding, `AssetPathologySource`'s zip path, `FilePathologySource`/`FileCourseSource` from the
  app's load flow, and the extracted dataset dirs under `filesDir`. Start the repositories on inert
  empty sources until a pack loads.
- SAF picker: request `*/*` and **identify by magic, not extension** — SAF cannot filter reliably by
  extension, and the Windows loader already routes on the 4-byte magic (a `.pak` renamed `.zip` still
  works, and vice versa). `takePersistableUriPermission` so the pick survives restart.
- Port `dropLegacyPicks()`: clear a saved pick that **exists but is not a pack** (an upgrading
  install); **keep** a pick whose file is merely missing (may be removable storage). Persist a pick
  **only after it loads** — a non-null saved pick suppresses the bundled pack, so a bad pick would
  otherwise strand the app with no data and no way back.
- Loading UI: phases only — *Opening pack* → *Loading list* → *Loaded N*. **Do not build a per-record
  progress bar.** A pack decodes nothing at load; Windows measured a full verify pass at **135 s** for
  45k records and rejected it. There is no per-record work to report.

### Phase 5 — Companion performance fixes *(not optional)*
Windows measured both; without them CSP2 is **slower** than the old path:
- **Index `readByName`.** It was a linear scan over entries → a 45k-entry pack made manifest loading
  quadratic. Build a name→entry map once (case-insensitive, first-match-wins).
- **Memoise the last-read pathology in `PathologyRepository`.** `leadWaveform` reads the whole file
  per lead, so one 12-lead rhythm = **13 reads of the same `.dat`**. Free against an in-RAM buffer;
  13 decrypt+inflate cycles against a lazy pack. Verify Android has the same shape
  (`data/PathologyRepository.kt`), and invalidate the memo wherever the manifest reloads.

---

## 5. Non-goals

- **No CSP1 reader, ever.** Nothing on Android has ever read a pack; every vendor pack is CSP2.
- **No new container format.** Match Windows byte-for-byte or packs stop being cross-platform.
- Not porting the Windows customer-facing converter scripts (`convert-courses-to-pak.ps1`,
  `build-course-converter.ps1`) — desktop tooling; packs arrive already converted.
- Not porting the Windows `Limited` edition split.
- Not changing `EcgCalibration`, waveform maths, or any rendering.

---

## 6. Risks & open questions

- **[OPEN] commons-compress vs a hand-written ZIP reader.** Decide in Phase 1. Recommendation:
  commons-compress. Zip64 is required (>65,535 entries in the 45k pack) — verify the chosen path
  handles it.
- **[OPEN] Bundled-asset access.** `AssetManager` gives no seekable channel. Copy-to-`filesDir`
  (ciphertext, invariant-safe) vs `noCompress` + `openFd()` slice. Recommendation: copy.
- **[RISK] PBKDF2 `char[]` vs raw bytes.** Java's `PBEKeySpec` UTF-8-encodes a `char[]`; Windows
  feeds raw bytes. This will silently derive the wrong key rather than fail loudly. Use a raw-byte
  PBKDF2 and validate against a real pack **before** writing anything else.
- **[RISK] SAF channels are not always seekable.** `openFileDescriptor` on a *cloud* provider
  (Drive, etc.) can return a pipe, where `FileChannel.position()` fails. Detect and refuse with a
  clear message ("copy the pack to this device first") rather than crashing or silently buffering
  1.67 GB.
- **[RISK] Tamper surfaces at read, not open.** Per-chunk tags mean a damaged chunk throws from a
  `readPathology` call deep in a ViewModel, not at open. Every read path must degrade to
  null/"missing", never crash the app.
- **[RISK] APK/AAB size.** The 1.67 GB pack cannot be bundled (Play caps AAB well below it). The
  large dataset must be side-loaded via SAF; only the small subset ships in the app.
- **[RISK] Scope.** This is the largest sync plan to date: a crypto core, a new dependency, a source
  layer, an overlay, and a cutover. Phases 1–2 are independently shippable (read-only, behind the
  existing zip path) — **do not cut over in Phase 4 until 1–3 are proven on-device.**
- **[NOTE] Windows verification gap:** no pack >2 GB was ever actually built. The ceiling is
  structurally gone (nothing allocates a pack-sized array) but unproven above 2 GB — on Android,
  where `ByteArray` is `Int`-indexed too, keep every buffer chunk-sized and the limit never applies.

---

## 7. Verification

Per phase, concrete:

- **Phase 1:** unit tests ported from `ChunkedPackTests.cs` (26 tests, all passing on Windows):
  round-trip at every chunk boundary (`0`, `1`, `chunkSize-1`, `chunkSize`, `chunkSize+1`, exact
  multiples); **nonce uniqueness across the index space incl. the header index**; tamper matrix
  (header salt / nonceBase / chunkSize / plainLength / tag, first chunk, middle chunk, last tag);
  **swapped chunks rejected**; truncated and padded packs rejected; channel reads byte-identical to a
  buffered baseline at many random offset/length pairs incl. straddling; reads survive cache eviction.
  **Gate:** open the real `Pathologies.regrouped.subset-500.pak` and read `manifest.txt` + one `.dat`.
- **Phase 2:** on-device, bundled pack loads; rhythm list populates; a waveform renders correctly
  (compare samples against the Windows reader for the same id).
- **Phase 3:** create/edit/delete/duplicate on a pack build; the pack file stays **byte-identical**;
  the overlay file is not readable as a ZIP (i.e. actually encrypted); tombstoned items stay gone.
- **Phase 4:** no plaintext dataset anywhere under `filesDir`/`cacheDir` after a full run
  (`adb shell run-as ... find`); a legacy saved ZIP pick is dropped and the app falls back to the
  bundled pack instead of showing an empty list.
- **Phase 5 / headline:** load the **1.67 GB** `Pathologies.All.regrouped.pak` via SAF on a real
  device. **Must not OOM.** Record peak heap (`adb shell dumpsys meminfo`) — Windows: 42 MB managed /
  ~380 MB working set. Then measure rhythm-switch latency before/after the memo fix.

## 8. PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | CSD1 delta-binary decoder | 0 | separate plan; prerequisite |
| 2 | CSP2 crypto core + chunked channel + tests | 1 | no wiring; tests are the deliverable |
| 3 | EncryptedArchive over commons-compress ZipFile | 1 | + gradle dep; the Java/.NET gap |
| 4 | Encrypted{Pathology,Course}Source + bundled pack | 2 | read-only, zip path still default |
| 5 | Writable encrypted overlay (per-pack) | 3 | Constructor must keep working |
| 6 | Pack-only cutover + SAF magic routing + dropLegacyPicks | 4 | deletes the zip paths |
| 7 | readByName index + repository memo | 5 | without these it is slower than today |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:**
- **Deviations from plan:**
- **Follow-ups spawned:**
