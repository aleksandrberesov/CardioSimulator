# Exclude courses from TCP uploads — confirm parity, drop the unwired `uploadCourses` (Android)

**Status:** completed
**Owner:** AI Assistant
**Started:** 2026-06-29
**Finished:** 2026-06-29
**Direction:** **Windows → Android** — and, like the ZIP-recursion sync, this is a *catch-up that already
happened*. The desired end-state behaviour already holds on Android; the deliverable is a small,
behaviour-preserving cleanup so the two ports stay structurally in sync.

## Goal

On Windows (`CardioSimulatorWin`) the TCP connect handler used to auto-upload **both**
`Pathologies.zip` **and** `Courses.zip` on every successful connection. The customer wants course
content excluded from what gets pushed to the server, so the Windows fix removed the `Courses.zip`
branch — on connect, Windows now sends `Pathologies.zip` only.

This plan brings Android into documented parity with that decision. **Android already does not upload
courses on connect**, so there is *no behaviour change to make*. What's left is to (1) confirm the
parity, (2) decide the fate of the now-orphaned `uploadCourses()` method, and (3) keep the docs
honest.

## The Windows change (reference)

`src/CardioSimulator.App/ViewModels/AppViewModel.cs` — `SendUploadArchiveAsync` (called once per
connect from `ConnectionLoopAsync`) dropped its courses branch:

```csharp
// before — both archives pushed on every connect
private async Task SendUploadArchiveAsync(Socket socket, CancellationToken ct)
{
    await SendSingleArchiveAsync(socket, AppPaths.PathologiesDir, "Pathologies.zip", ct);
    // ...check courses are valid/non-empty, then:
    await SendSingleArchiveAsync(socket, AppPaths.CoursesDir, "Courses.zip", ct);
}

// after — pathologies only
private async Task SendUploadArchiveAsync(Socket socket, CancellationToken ct)
{
    await SendSingleArchiveAsync(socket, AppPaths.PathologiesDir, "Pathologies.zip", ct);
}
```

After this change Windows has **no remaining path** that uploads courses over TCP — the on-connect
push was the only one. Source plan: `CardioSimulatorWin/docs/plans/complete/upload_courses_fix.md`.

## Current state (Android) — already correct

`app/src/main/java/com/example/cardiosimulator/ui/viewmodels/AppViewModel.kt`:

- **`connectTcp()`** (line ~367) → on a successful connect (line ~380) calls **`sendUploadArchive()`**.
- **`sendUploadArchive()`** (line ~451) zips `filesDir/pathologies` and sends a single
  `UploadMessage(filename = "Pathologies.zip", …)` + raw bytes. **It never touches courses.** So the
  on-connect behaviour already matches the post-fix Windows behaviour. ✅
- **`uploadCourses()`** (line ~552) is the *only* code path that sends `Courses.zip`
  (`UploadMessage(filename = "Courses.zip", …)`). It is a separate public method — **not** part of the
  connect handler — and a repo-wide search finds **zero callers** (`grep -r uploadCourses app/`
  returns only the declaration). It is dead/unwired code: no button, no menu, no automatic trigger
  invokes it.

So the two ports actually diverged the *other* way before this fix: Windows auto-pushed courses on
connect; Android never did. The Windows fix removes Windows' auto-push, converging both ports on
"courses are not sent over TCP." Android needs **no behaviour change** to honour the goal.

`docs/architecture.md` (≈ lines 615–631) already documents this split: connect "auto-uploads … filename
`Pathologies.zip`", and "The course bundle can **also** be uploaded **manually** via `uploadCourses()`".

## Non-goals

- Do **not** add courses to `sendUploadArchive()` or the connect path — that's the exact opposite of
  the goal.
- Do **not** change anything about the `Pathologies.zip` auto-upload — it stays as-is.
- No protocol/`TcpMessage`/`TcpProtocol` changes. `UploadMessage` keeps supporting arbitrary
  filenames; we're only removing one unused *caller*, not the message type.

## Plan

One phase, low-risk. The substantive item is removing dead code so Android, like post-fix Windows,
has **no** courses-over-TCP path at all.

### Phase 1 — Remove the orphaned courses-upload path + fix docs

1. **Delete `uploadCourses()`** from `AppViewModel.kt` (the whole `fun uploadCourses() { … }` block,
   ~lines 552–579). Rationale: it is unreachable (zero callers), and after the Windows fix neither
   port should expose a courses-over-TCP path. Removing it is the faithful sync and removes a latent
   "push courses" capability the customer asked to exclude. Verify the build still compiles — there
   should be nothing to fix up, precisely because nothing calls it.

2. **Update `docs/architecture.md`** — delete the trailing paragraph (≈ lines 630–631):
   > "The course bundle can also be uploaded manually via `uploadCourses()`, which sends the
   > `filesDir/courses/` directory as `Courses.zip`."
   The `Connection behavior` list (Pathologies-only auto-upload) is already correct and stays. Leave
   `exportCoursesZip` (SAF export to a user-chosen file) untouched — that is a *local file export*, not
   a TCP upload, and is out of scope.

> **Decision — recommended: remove.** If the team would rather keep `uploadCourses()` dormant for a
> possible future "push courses to server" feature, the acceptable alternative is to **keep the method
> but leave it unwired** and instead add a one-line `// Intentionally not invoked — courses are
> excluded from TCP uploads (parity with Windows, 2026-06).` comment above it, and keep the
> architecture.md sentence but reword it to note it is currently unused. The plan above assumes
> removal; pick one and apply consistently across code + doc.

## Risks & open questions

- **Risk: none functional.** `uploadCourses()` has no callers, so removal cannot change runtime
  behaviour. The only "risk" is the keep-vs-remove judgement call above, which is cosmetic/dead-code
  hygiene, not behaviour.
- **Open question (resolved 2026-06-29):** "Does Android auto-upload courses on connect like
  pre-fix Windows did?" — **No.** `sendUploadArchive()` sends `Pathologies.zip` only; courses only
  ever went out via the unwired `uploadCourses()`.

## Verification

- **Build:** `./gradlew :app:assembleDebug` (or the project's compile/unit-test task) succeeds with no
  unresolved references after the deletion — confirming nothing referenced `uploadCourses()`.
- **Static check:** `grep -rn "uploadCourses" app/` returns nothing after removal (or only the
  intentional comment, if the keep-dormant alternative was chosen).
- **Manual smoke (already-working, confirms no regression):** connect to the TCP server; observe that
  exactly one `UploadMessage` with `filename = "Pathologies.zip"` is sent on connect and **no**
  `Courses.zip` frame is ever emitted. Course content still loads/displays locally and
  `exportCoursesZip` still writes a local archive (both unaffected).

## Files

- `app/src/main/java/com/example/cardiosimulator/ui/viewmodels/AppViewModel.kt` — remove the unwired
  `uploadCourses()` method (~lines 552–579). **No change** to `sendUploadArchive()` /
  `connectTcp()` / `exportCoursesZip`.
- `docs/architecture.md` — drop the "course bundle can also be uploaded manually via `uploadCourses()`"
  paragraph (~lines 630–631).

## Out of scope / do NOT do

- Do **not** modify `sendUploadArchive()` or add any courses branch to the connect path.
- Do **not** touch `exportCoursesZip` / SAF course export — local file export, not a TCP upload.
- Do **not** alter `TcpMessage.UploadMessage`, `TcpProtocol`, or `ZipCompressor`.

---

> Windows source of this sync: `CardioSimulatorWin/docs/plans/complete/upload_courses_fix.md`
> (+ `src/CardioSimulator.App/ViewModels/AppViewModel.cs` — `SendUploadArchiveAsync`). Related Android
> docs: `docs/architecture.md` §4 (connection behaviour / TCP upload),
> `docs/plans/completed/2026-05-course-constructor.md` (Phase 4 — Export + TCP push, where the
> courses-upload path was originally introduced).

## Outcome

- **Result:** completed
- **Changes:** Removed the unused `uploadCourses()` method from `AppViewModel.kt` and updated `docs/architecture.md` to remove references to it. This brings Android into structural parity with the Windows port regarding course exclusion from TCP uploads.
- **Verification:** App compiles successfully; `uploadCourses` is no longer present in the codebase.
