# ZIP export recursion — confirm parity, de-duplicate, add regression test (Android)

**Status:** completed
**Owner:** AI Assistant
**Started:** 2026-06-29
**Finished:** 2026-06-29

**Direction:** **Windows → Android**, but this one is a *catch-up that already happened*. On Windows
(`CardioSimulatorWin`) the course/pathology ZIP export was broken — `ZipCompressor` only packed
top-level files (`Directory.GetFiles(sourceDir)`), so an exported Courses archive contained just
`manifest.txt` and dropped every nested directory (`cardio-101/`, `…/lectures/…`). The fix made the
archiver **recursive**, preserving the relative directory structure and normalizing path separators.

**Android already does this.** `data/ZipCompressor.kt` walks the tree with `walkTopDown()` and writes
relative entries, so courses/pathologies already export with their full hierarchy. There is **no
user-facing bug to fix on Android.** This plan exists so the two ports stay structurally in sync and
so Android gains the regression test Windows just added — i.e. lock the correct behaviour in, don't
change it.

---

## The Windows change (reference)

`src/CardioSimulator.Core/Data/ZipCompressor.cs` — `WriteArchive` went from flat to recursive:

```csharp
// before — only top-level files, entry name = bare filename
foreach (var file in Directory.GetFiles(sourceDir))
    archive.CreateEntryFromFile(file, Path.GetFileName(file), CompressionLevel.Optimal);

// after — recurse, keep relative path, force forward slashes
var fullSourceDir = Path.GetFullPath(sourceDir);
foreach (var file in Directory.GetFiles(fullSourceDir, "*", SearchOption.AllDirectories))
{
    var relativePath = Path.GetRelativePath(fullSourceDir, file).Replace('\\', '/');
    archive.CreateEntryFromFile(file, relativePath, CompressionLevel.Optimal);
}
```

Structure on Windows: a single private core `WriteArchive(string sourceDir, Stream output)` with two
thin public wrappers — `Zip(sourceDir, destPath)` (export to a file) and `ZipToTemp(sourceDir, …)`
(snapshot for TCP upload). A new unit test, `tests/CardioSimulator.Core.Tests/ZipCompressorTests.cs`,
asserts a nested file (`subdir/lecture.html`) survives the round-trip with a `/` separator. Source
plan: `CardioSimulatorWin/docs/plans/complete/export_courses_fix.md`.

---

## Current state (Android) — already correct

`app/src/main/java/com/example/cardiosimulator/data/ZipCompressor.kt`:

- **`zip(context, sourceDir, destUri)`** — SAF export. Used by `AppViewModel.exportZip` (pathologies,
  `PATHOLOGIES_DIR`) and `AppViewModel.exportCoursesZip` (courses, `COURSES_DIR`).
- **`zipToCache(context, sourceDir, fileName)`** — cache snapshot for TCP upload (`uploadCourses`,
  pathology auto-upload on connect).

Both methods already:

```kotlin
val rootPath = sourceDir.absolutePath
sourceDir.walkTopDown().forEach { f ->
    if (f.absolutePath == rootPath) return@forEach
    val rel = f.absolutePath.removePrefix(rootPath).trimStart(File.separatorChar)
    if (f.isDirectory) { zos.putNextEntry(ZipEntry("$rel/")); zos.closeEntry() }
    else { zos.putNextEntry(ZipEntry(rel)); f.inputStream().use { it.copyTo(zos) }; zos.closeEntry() }
}
```

So nested course content (`cardio-101/lectures/01-intro.en.html`) is already packed. **Verify and
keep — do not "fix".**

### Two cosmetic divergences from Windows (intentional, leave as-is)

1. **Separator normalization** — Windows adds `.Replace('\\', '/')` because .NET on Windows yields
   `\` in relative paths. On Android `File.separatorChar` is `/`, so entries are already
   forward-slashed; no `.replace('\\','/')` is needed. (Adding one would be a harmless no-op — skip
   it; don't add noise.)
2. **Explicit directory entries** — Android emits a `dir/` `ZipEntry` for each folder; Windows emits
   files only (consumers create parent dirs implicitly). Both are valid ZIP and both Android
   extractors (`CourseZipExtractor`, `PathologyZipExtractor`) handle either. Keep Android's explicit
   dir entries — don't churn this to match Windows byte-for-byte.

---

## The actual parity work

Small, behaviour-preserving. The point is structural parity + a regression guard, nothing user-facing.

### 1. De-duplicate the walk into a single core (mirror Windows `WriteArchive`)

`zip` and `zipToCache` contain the **same** walk/zip loop copy-pasted. Extract it into one private
helper that writes to an `OutputStream`, matching Windows' `WriteArchive(sourceDir, output)` shape:

```kotlin
private fun writeArchive(sourceDir: File, out: OutputStream) {
    ZipOutputStream(out).use { zos ->
        val rootPath = sourceDir.absolutePath
        sourceDir.walkTopDown().forEach { f ->
            if (f.absolutePath == rootPath) return@forEach
            val rel = f.absolutePath.removePrefix(rootPath).trimStart(File.separatorChar)
            if (f.isDirectory) {
                zos.putNextEntry(ZipEntry("$rel/")); zos.closeEntry()
            } else {
                zos.putNextEntry(ZipEntry(rel)); f.inputStream().use { it.copyTo(zos) }; zos.closeEntry()
            }
        }
    }
}
```

`zip` and `zipToCache` keep their existing guards (`exists()/isDirectory`, SAF stream open, cache
file delete) and just call `writeArchive(sourceDir, out)`. Pure refactor — no behaviour change.

> This extraction is what makes the logic unit-testable without an Android `Context` (see below): the
> two public methods need `Context`/`Uri`/`cacheDir`, but `writeArchive` takes only `File` + a plain
> `OutputStream`.

### 2. Add the regression test (mirror `ZipCompressorTests.cs`)

Android unit tests are **plain JUnit4 — no Robolectric, no Android `Context`** (see existing tests in
`app/src/test/java/.../data/`). So test the extracted, Context-free core. Make `writeArchive`
test-visible — either `internal` (with `testImplementation` seeing it) or package-private via an
`@VisibleForTesting internal` helper.

New file `app/src/test/java/com/example/cardiosimulator/data/ZipCompressorTest.kt`:

- Build a temp source dir with a top-level `manifest.txt` and a nested `subdir/lecture.html`.
- Zip it via `writeArchive(sourceDir, ByteArrayOutputStream())` (or a temp `File` output stream).
- Re-open with `java.util.zip.ZipInputStream` / `ZipFile` and collect entry names.
- Assert the names contain `manifest.txt` and `subdir/lecture.html` (forward slash); content of the
  nested entry round-trips. Mirror the Windows assertions; tolerate the extra `subdir/` directory
  entry Android emits (assert *contains* the files, don't assert exact count = 2 the way the Windows
  test does, since Android also writes the `dir/` marker).
- Clean up the temp dir in `@After`.

That's the deliverable — it locks Android's already-correct recursion against future regressions, the
same guard Windows now has.

---

## Verification

- `./gradlew :app:testDebugUnitTest` (or the project's unit-test task) — the new `ZipCompressorTest`
  passes; existing tests stay green.
- Manual (optional, already-working sanity): in the app, **Settings → Export Courses ZIP**; open the
  resulting archive and confirm the full tree is present — `manifest.txt`, `cardio-101/course.txt`,
  `cardio-101/lectures/01-intro.en.html`. **Export Pathologies ZIP** still produces a valid archive
  with the `.dat`/manifest content. (Both already work; this just confirms no regression from the
  refactor.)

## Files

- `app/src/main/java/com/example/cardiosimulator/data/ZipCompressor.kt` — extract a shared
  `writeArchive(sourceDir, out)`; `zip` and `zipToCache` delegate to it. **No behaviour change.**
- `app/src/test/java/com/example/cardiosimulator/data/ZipCompressorTest.kt` — **new**, recursion
  round-trip regression test (mirrors Windows `ZipCompressorTests.cs`).

## Out of scope / do NOT do

- Do **not** make any behavioural change to how archives are written — Android is already recursive
  and correct.
- Do **not** add `.replace('\\','/')` (no-op on Android) or strip the explicit `dir/` entries — those
  are accepted divergences from the Windows source, documented above.

---

> Windows source of this sync: `CardioSimulatorWin/docs/plans/complete/export_courses_fix.md`
> (+ `ZipCompressor.cs`, `tests/.../ZipCompressorTests.cs`). Related Android export docs:
> `docs/architecture.md` (the `ZipCompressor.zip` / `zipToCache` export + TCP-upload paths),
> `docs/plans/completed/2026-05-course-constructor.md` (Phase 4 — Export + TCP push).
