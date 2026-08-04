# Walkthrough - Delta-binary (`CSD1`) `.dat` format parity

I have implemented the `CSD1` delta-binary decoder in the Android app, achieving parity with the Windows version's compact data format.

## Changes Made

### Domain Layer
- **[PathologyParser.kt](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/domain/PathologyParser.kt)**:
    - Added support for `CSD1` magic header.
    - Implemented `parsePathology(ByteArray)` which detects if a file is binary or legacy text.
    - Refactored header parsing into `buildFromHeader` to ensure consistency between text and binary paths.
    - Implemented `parsePathologyBinary` using `ByteBuffer` (little-endian) to decode delta-encoded 16-bit samples with two's-complement wrap-around.
    - Added UTF-8 BOM detection for legacy text files.

### Data Layer
- **[AssetPathologySource.kt](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/data/AssetPathologySource.kt)**:
    - Updated `readPathology` to read assets as `ByteArray`.
- **[FilePathologySource.kt](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/data/FilePathologySource.kt)**:
    - Updated `readPathology` to read files as `ByteArray`.

### Testing
- **[PathologyParserTest.kt](file:///E:/VLN_Project/CardioSimulator/app/src/test/java/com/example/cardiosimulator/data/PathologyParserTest.kt)**:
    - Added `binary CSD1 parses correctly` test.
    - Added `binary CSD1 handles int16 overflow wrap-around` test to verify mathematical correctness of delta reconstruction.
    - Added `text pathology with UTF-8 BOM parses` test.
    - Verified that all 79 tests (including legacy ones) pass.

## Verification Results

### Automated Tests
Ran `:app:testDebugUnitTest`. All tests passed, including new binary format cases.

```
79 passed, 0 skipped, 0 failed
```

### Manual Verification
The changes ensure that any binarized dataset provided to the Android app will be parsed correctly, while maintaining full backward compatibility with existing text-based datasets.
