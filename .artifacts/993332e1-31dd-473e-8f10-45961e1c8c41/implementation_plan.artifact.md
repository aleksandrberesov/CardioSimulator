# Implementation Plan - Delta-binary (`CSD1`) `.dat` format parity

Teach the Android app to read the compact `CSD1` delta-binary `.dat` encoding used by the Windows version. This will allow the app to consume binarized datasets, reducing uncompressed size by ~56%.

## User Review Required

> [!IMPORTANT]
> This change modifies how `.dat` files are read in `AssetPathologySource` and `FilePathologySource`. Instead of reading them as `String` (UTF-8), they will now be read as `ByteArray`. The `PathologyParser` will then determine if it's a binary `CSD1` file or a legacy text file based on the magic header.

## Proposed Changes

### Domain Logic

#### [MODIFY] [PathologyParser.kt](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/domain/PathologyParser.kt)

- Define `CSD1_MAGIC` constant (`byteArrayOf(0x43, 0x53, 0x44, 0x31)`).
- Add `parsePathology(bytes: ByteArray): PathologyFile` as the main entry point.
- Implement `hasMagic(bytes: ByteArray): Boolean` to sniff the file type.
- Extract `buildFromHeader(header: Map<String, String>, leads: Map<Lead, LeadStream>): PathologyFile` to share header parsing logic.
- Implement `parsePathologyBinary(bytes: ByteArray): PathologyFile` for the new binary format.
- Update `parsePathology(text: String)` to use the refactored `buildFromHeader`.
- Implement `readString(buf: ByteBuffer): String?` and `decodeUtf8(bytes: ByteArray): String` (handling optional BOM).

---

### Data Sources

#### [MODIFY] [AssetPathologySource.kt](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/data/AssetPathologySource.kt)

- Update `readPathology(id: String)` to read bytes: `assets.open(path).use { it.readBytes() }`.
- Pass the bytes to `PathologyParser.parsePathology(bytes)`.

#### [MODIFY] [FilePathologySource.kt](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/data/FilePathologySource.kt)

- Update `readPathology(id: String)` to read bytes: `file.readBytes()`.
- Pass the bytes to `PathologyParser.parsePathology(bytes)`.

---

## Verification Plan

### Automated Tests

- Add `PathologyParserTest.kt` cases for:
    - Binary `CSD1` parsing with delta-encoded samples.
    - Two's-complement wrap-around for int16 deltas.
    - Mixed text/binary dataset compatibility.
    - UTF-8 BOM handling in text files.
- Run tests: `./gradlew :app:testDebugUnitTest --tests "com.example.cardiosimulator.data.PathologyParserTest"`

### Manual Verification

- Deploy the app to a device/emulator.
- Verify that the existing text-based rhythms still load correctly.
- (If a binary fixture is available) Import or bundle a binary `.dat` file and verify it loads identically to its text counterpart.
