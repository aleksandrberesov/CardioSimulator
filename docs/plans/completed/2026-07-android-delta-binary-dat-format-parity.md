# Delta-binary (`CSD1`) `.dat` format parity

**Status:** active
**Owner:** unassigned
**Started:** 2026-07-16
**Direction:** **Windows → Android** (reverse of the usual — the format was built in the WinUI 3 port first)
**Related issues / PRs:** —
**Reference (Windows) source:** `E:\VLN_Project\CardioSimulatorWin\src\CardioSimulator.Core\Domain\PathologyParser.cs`

## Goal

Teach Android to read the compact **`CSD1` delta-binary** `.dat` encoding that Windows now uses for
the pathology dataset. Windows stores each lead's waveform as delta-encoded 16-bit integers instead
of comma-separated decimal text, which cut the real 45,206-record dataset from **12.40 GB → 5.42 GB**
uncompressed and made distribution packs **~29 % smaller** (e.g. All: 2,467 MB → 1,672 MB).

**Why now:** the Windows dataset pipeline has been reworked to be *binary-first* and the plaintext
ZIP stage is retired — the master is binarized once, then packs are built straight from it. Android
today can only parse **text** `.dat`, so the moment it is handed a binarized dataset it fails. This
is purely additive: a text dataset keeps working untouched, because the decoder only diverges when it
sees the 4-byte `CSD1` magic.

## Current state

Android (`app/src/main/java/com/example/cardiosimulator/`):

- `domain/PathologyParser.kt:89` — `parsePathology(text: String)` is **text-only**: `splitBlocks(text)`
  → header map + per-lead blocks → `parseIntCsv(block["points"])`. Header fields are extracted inline
  at `:94-103` (id/title/name/group/description/clinicalCase/number/markers/tips/tipComments) and the
  `PathologyFile` is built at `:124-127`.
- `domain/Pathology.kt:17-18` — `enum class Lead { I, II, III, aVR, aVL, aVF, V1, V2, V3, V4, V5, V6 }`.
  **Verified: identical declaration order to Windows**, so the binary's enum-ordinal lead index maps
  straight across. Do not reorder this enum.
- `domain/Pathology.kt:52-55` — `data class LeadStream(val lead: Lead, val samples: IntArray)`.
  **Android has no `elements` field** and its text parser already ignores the `elements:` line.
- `data/AssetPathologySource.kt:25-28` — `readPathology` calls `readText("$baseDir/$id.dat")`
  (`:38-40` = `assets.open(path).use { String(it.readBytes(), UTF_8) }`). Text-decoding a binary file
  corrupts it — this must read **bytes**.
- `data/FilePathologySource.kt:29` — `PathologyParser.parsePathology(file.readText(Charsets.UTF_8))`.
  Same problem; this is the path a user-imported dataset takes.
- `data/PathologyZipExtractor.kt:60-72` — copies entry bytes to disk verbatim, never text-decodes.
  **No change needed.**
- **Android has no `.pak` / `ContentCrypto` reader at all** (grep for `AesGcm|CSP1|decrypt|\.pak`
  returns nothing). That protection feature is Windows-only so far.

## Non-goals

- **Porting the encrypted `*.pak` reader** (`ContentCrypto` / `EncryptedArchive`). That is a separate,
  larger piece of work. This plan makes the *waveform decoder* ready, which is a prerequisite for it.
- **A Kotlin writer/packer.** Android is **decode-only** — packs are produced by the Windows offline
  `ContentPacker`. `serializePathologyBytes` is optional and only if you want a symmetric round-trip test.
- Changing raw sample values, the amplitude scale, or the manifest grammar. Encoding-only change.

## The `CSD1` format (authoritative)

All multi-byte integers **little-endian**. A *string* is `int32 length` (**−1 = null**) then that many
UTF-8 bytes.

```
offset  type            field
0       byte[4]         magic 'C','S','D','1'
4       string          header text block
        int32           lead count N
   N × :
        uint8           lead index (enum ordinal: I=0 … V6=11)
        string          elements text (nullable; Android reads and DISCARDS)
        int32           sampleCount
        int16[]         delta samples
```

- **There is NO version byte.** The 4-byte magic is the sole discriminator; an incompatible framing
  change bumps the magic (`CSD2`) instead. (An earlier Windows draft had a version byte after the
  magic — it was removed. Do not implement one, or every real pack will fail to parse.)
- **Header text block** is exactly what the *text* serializer emits before the first `lead:` block
  (`pathology:`/`title:`/`number:`/`name:`/`group:`/`clinical_case:`/`description:`/`leads:`/
  `markers:`/`tips:`/`tip_notes:`). Parse it with the **existing** `splitBlocks` + header logic — no new
  metadata code, and metadata round-trips exactly as text does. It contains no lead blocks → one block.
- **Delta samples:** first delta is `sample[0] − 0`, then `sample[i] − sample[i-1]`, as signed 16-bit.
  Reconstruction **must** use two's-complement wrap-around so it stays exact even when the gap between
  two samples overflows a 16-bit delta:
  `val value = (prev + delta).toShort().toInt()` (mirrors the C# `(short)(prev + delta)` cast).

## Plan

### Phase 1 — Decoder in `PathologyParser.kt`

Keep `parsePathology(text: String)`; add a `ByteArray` overload that sniffs the magic. Refactor the
header-field extraction currently inline at `:94-103` + `:124-127` into a shared
`buildFromHeader(header, leads)` so the text and binary paths produce identical `PathologyFile`s.

```kotlin
private val CSD1_MAGIC = byteArrayOf(0x43, 0x53, 0x44, 0x31) // "CSD1"

fun parsePathology(bytes: ByteArray): PathologyFile {
    if (bytes.isEmpty()) throw FormatException("pathology: empty file")
    return if (hasMagic(bytes)) parsePathologyBinary(bytes) else parsePathologyText(decodeUtf8(bytes))
}

fun parsePathology(text: String): PathologyFile = parsePathologyText(text)

private fun hasMagic(b: ByteArray) = b.size >= 5 &&
    b[0] == CSD1_MAGIC[0] && b[1] == CSD1_MAGIC[1] && b[2] == CSD1_MAGIC[2] && b[3] == CSD1_MAGIC[3]

private fun decodeUtf8(b: ByteArray): String =            // tolerate a UTF-8 BOM
    if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte())
        String(b, 3, b.size - 3, Charsets.UTF_8) else String(b, Charsets.UTF_8)

private fun parsePathologyBinary(bytes: ByteArray): PathologyFile {
    val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    buf.position(CSD1_MAGIC.size)                         // no version byte follows the magic

    val headerText = readString(buf) ?: throw FormatException("pathology: binary missing header")
    val header = splitBlocks(headerText).firstOrNull() ?: emptyMap()

    val leadCount = buf.int
    val leads = linkedMapOf<Lead, LeadStream>()
    repeat(leadCount) {
        val idx = buf.get().toInt() and 0xFF
        if (idx >= Lead.entries.size) throw FormatException("pathology: lead index $idx out of range")
        val lead = Lead.entries[idx]
        readString(buf)                                   // elements text — Android has none; discard
        val n = buf.int
        if (n < 0) throw FormatException("pathology: negative sample count")
        val samples = IntArray(n)
        var prev = 0
        for (i in 0 until n) {
            val v = (prev + buf.short).toShort().toInt()  // two's-complement wrap-around
            samples[i] = v; prev = v
        }
        leads[lead] = LeadStream(lead, samples)
    }
    return buildFromHeader(header, leads)
}

private fun readString(buf: java.nio.ByteBuffer): String? {
    val len = buf.int
    if (len < 0) return null
    val b = ByteArray(len); buf.get(b); return String(b, Charsets.UTF_8)
}
```

### Phase 2 — Route the byte read paths

- `data/AssetPathologySource.kt:25-28` → read the asset as **bytes**:
  ```kotlin
  override fun readPathology(id: String): PathologyFile? = runCatching {
      val bytes = readBytes("$baseDir/$id.dat") ?: return null
      PathologyParser.parsePathology(bytes)
  }.getOrNull()
  // readBytes(path) = assets.open(path).use { it.readBytes() }
  ```
  `manifest.txt` / `groups.txt` stay text — they are never binarized.
- `data/FilePathologySource.kt:29` → `PathologyParser.parsePathology(file.readBytes())`.
  This covers user-imported datasets (`PathologyZipExtractor` flattens entries to disk and
  `FilePathologySource` reads them).
- `data/PathologyZipExtractor.kt` — no change.

### Phase 3 — (Optional) ship a binarized asset dataset

Once Phase 1+2 land, the bundled `assets/Pathologies/` can be binarized to shrink the APK, using the
Windows tool (no Kotlin packer needed):

```
ContentPacker binarize <assets/Pathologies> <assets/Pathologies.bin>
```

then replace the asset folder. `manifest.txt`/`groups.txt` are copied verbatim by the tool. Expect
~56 % off the uncompressed `.dat` bytes. Ship this only after Phase 1 tests are green.

## Risks & open questions

- **How does Android actually receive a binary dataset?** Windows now emits only loose binary files +
  encrypted `*.pak`, and Android can read neither a `.pak` nor (today) a binary `.dat`. After this plan
  Android can consume **a ZIP of binarized loose files** and **binarized assets**. Full parity with the
  Windows distribution still needs the `.pak` reader (out of scope — see Non-goals).
- **Enum drift.** The lead index is an enum *ordinal*. If `Lead` is ever reordered on either platform,
  every pack silently decodes to the wrong leads. Verified identical today (`Pathology.kt:18`);
  consider a unit test asserting `Lead.entries.map { it.name }` equals the canonical order.
- **~89 of 45,206 real records (0.2 %) stay plain text** — Windows' encoder refuses samples outside
  int16 (corrupt source spikes of ±33 mV, e.g. `ecg37094` lead V5). Android must therefore tolerate
  **mixed** text/binary `.dat` in one dataset. The magic-sniff handles this automatically; just don't
  assume "binary dataset ⇒ every file is binary".
- `LeadStream` has no `elements`, so the per-lead elements string is read and thrown away. If Android
  ever gains element annotations, wire it to `parseElements` rather than changing the framing.

## Verification

- **Unit tests** (`PathologyParserTest`), mirroring the Windows suite:
  - a text `.dat` and its `CSD1` form parse to **equal** `PathologyFile`s (leads, samples, title, name,
    group, clinical_case, number, markers, tips, tip_notes);
  - the int16-overflow vector `{30000, -30000, 30000, 0, 32767, -32768}` reconstructs exactly (proves
    the two's-complement wrap);
  - a pathology with only some leads present decodes only those leads;
  - text path still parses, including with a leading UTF-8 BOM.
- **Fixture source of truth:** generate one with the Windows tool —
  `ContentPacker binarize <dirWithOneDat> <outDir>` — and commit the bytes as a test resource.
- **End-to-end:** binarize a copy of the assets dataset, run the app, confirm rhythms render
  identically to the text dataset; then confirm an unmodified **text** dataset still loads (regression).

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | `PathologyParser`: decode `CSD1` delta-binary `.dat` | 1 | Decoder + `buildFromHeader` refactor + unit tests. No behaviour change for text. |
| 2 | Read `.dat` as bytes in asset/file sources | 2 | Two small edits; enables binary datasets end-to-end. |
| 3 | Binarize the bundled asset dataset | 3 | Optional APK-size win; data-only change. |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*
