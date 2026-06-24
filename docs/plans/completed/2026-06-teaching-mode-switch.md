# Teaching screen: course selector as a mode switch (+ Course Constructor RU-name fix)

**Status:** active
**Owner:** Aleksandr (a.beresov)
**Started:** 2026-06-20
**Related issues / PRs:** Customer feedback (Николай, 2026-06-19). Behavior already shipped on the Windows port (`CardioSimulatorWin`); this plan ports it to Android.

## Goal

Make the Teaching screen course-first with the course selector acting as a **mode switch**: picking **"All rhythms"** shows the **Monitor** (the rhythms window) as a standalone mode and is the **default** on entering Teaching; picking a **course** shows that course's **lectures**. The monitor can still pop over a lecture on demand. Plus a small Course Constructor fix: typing a course/lecture **name in Russian** glitches (spell-check/auto-correct), English is fine. Why now: the Windows port already does all this from the same customer feedback; we want the two apps back in sync.

## Current state

Android today (verify before editing — the screen has drifted from older docs):

- `app/.../ui/screens/TeachingScreen.kt` — Monitor is the base (rhythm drawer left, monitor center `weight(1f)`); a top-right `School` `IconButton` toggles `showCourseOverlay` to a full-screen course-viewer overlay (private `CourseViewerOverlay` composable, ~line 356, with its own lectures `SideDrawer`). A lecture/rhythm **title `Surface`** sits above the monitor (~lines 220–241). Course selection comes from `appViewModel.selectedCourseId` (`AppViewModel.ALL_RHYTHMS_ID` sentinel).
- `app/.../ui/panels/TeachingControlPanel.kt` — **already** has the course dropdown + a **lecture** dropdown shown only when a real course is selected (lines ~92–122, uses `LectureSelector`), plus a **Start/Stop** `Tab` (lines ~124–133). No rhythm picker for the "All rhythms" case.
- `app/.../ui/panels/MonitorControlPanel.kt`, `BottomControlPanel.kt`, `TopControlPanel.kt`, `RhythmSelector.kt`, `LectureSelector.kt` — supporting panels.
- ViewModels: `AppViewModel` (`ALL_RHYTHMS_ID`, `selectedCourseId`, `selectCourse`), `RhythmViewModel` (`rhythms`, `selectedRhythm`, `selectRhythm`, last-rhythm restore from prefs), `MonitorViewModel`, `CourseViewerViewModel` (`selectCourse`, `selectLecture`, `lectures`, `selectedLecture`, `lecture`).
- `app/.../ui/panels/CourseConstructorControlPanel.kt:229` — the create/rename name `TextField(value = text, onValueChange = …, singleLine = true)` has **no** `KeyboardOptions(autoCorrect = false)`. The "All in one" import field is the `OutlinedTextField` ~line 117.

Source-of-truth for target behavior: the Windows port (`E:\VLN_Project\CardioSimulatorWin`) — `Screens/TeachingScreen.cs`, `Controls/TeachingControlPanel.cs`, `Controls/CourseViewerPanel.cs`, `Controls/MonitorViewerOverlay.cs`, `Screens/MainScreen.xaml.cs`, `Controls/CourseConstructorScreen.cs`. Implement the *behavior* in idiomatic Compose; do not transliterate WinUI code.

## Non-goals

- **No "pathology description card."** Windows built one for the all-rhythms case, then removed it — showing the Monitor supersedes it. Do not add it.
- No port of WinUI "airspace" hide/show logic (WebView vs Win2D) — Compose has no equivalent.
- Not touching Testing / Examination / OSKE / Constructor (editor) interaction models.
- The inline `<ecg>` lecture-embed "open on monitor" feature is out of scope here (handle separately if/when it lands).

## Plan

Each phase is independently shippable.

### Phase 1 — Course selector drives the main view (+ default to All rhythms)
- In `TeachingScreen.kt`, replace the manual `showCourseOverlay` toggle with content driven by `selectedCourseId`:
  - **All rhythms** (`== ALL_RHYTHMS_ID` or null) → Monitor is the main content (standalone; no close affordance).
  - **A course** → the course's lectures are the main content.
- Keep a way to pop the Monitor **over** a lecture (the existing `School`/heart button) **with** a close/back control that returns to the lecture. In all-rhythms mode the Monitor has **no** close control (leave via the course selector).
- Default to **All rhythms** when entering Teaching mode — reset only on actual mode entry, not on every recomposition/config change (guard against language/orientation rebuilds wiping the user's course; mirror the Windows `_lastBuiltMode != Teaching` guard).

### Phase 2 — Context-sensitive second selector + auto-selection
- In `TeachingControlPanel.kt`, when **All rhythms** is selected, show a **rhythm** picker (from `RhythmViewModel.rhythms`, select via `selectRhythm`) in the slot currently used by the lecture dropdown. Keep the lecture dropdown for the course case.
- Auto-selection:
  - Course selected → auto-select its **first lecture** (unless one is already selected).
  - Switch to All rhythms → ensure a rhythm is selected: prefer the **last-used** (persisted) rhythm, else the **first**. Make this robust against the async rhythm-list load and the VM's own last-rhythm restore (don't let "pick first" overwrite the restored last rhythm); use a non-persisting select for the auto-pick.

### Phase 3 — Chrome cleanup
- Remove the lecture/rhythm **title `Surface`** above the monitor in `TeachingScreen.kt` (the name already shows in the top-panel dropdown).
- Remove the **Start/Stop** `Tab` from `TeachingControlPanel.kt` (and any now-dead `monitorViewModel`/`onStartStopClick` plumbing). Start/Stop stays available via the bottom monitor controls.
- Show the **monitor control panel** only when the Monitor is the main view (all-rhythms) or popped over a course — hidden while reading a course's lectures.
- **Verify** whether Android's bottom bar has a Compare button that duplicates the monitor panel's Compare in Teaching; if so remove the duplicate (keep it where it's the sole access). Skip if Android has no such duplicate.

### Phase 4 — Course Constructor RU-name fix
- On the create/rename name `TextField` (`CourseConstructorControlPanel.kt:229`) and any other course/lecture name field, set `keyboardOptions = KeyboardOptions(autoCorrect = false)` (use `autoCorrectEnabled = false` if your Compose version deprecates `autoCorrect`; consider `capitalization = KeyboardCapitalization.None`). Data layer is fine — this is purely the IME spell-check/auto-correct fighting non-English input.

## Risks & open questions

- **Drift:** the Android Teaching screen already partially matches Windows (lecture dropdown exists). Read current code per phase; implement only the delta, don't regress.
- **Auto-select vs restore race (Phase 2):** ordering between the rhythm manifest load, the VM's last-rhythm restore, and the "first if none" pick. Prefer the persisted last id explicitly so the reactive "first" pick can't clobber it.
- **Compare button (Phase 3):** unresolved until verified on Android — may be a no-op there.
- **`autoCorrect` API (Phase 4):** name/deprecation varies by Compose version — match the project's version.

## Verification

- `./gradlew :app:assembleDebug` clean after each phase.
- Manual on-device/emulator:
  - Enter Teaching → Monitor shows (All rhythms) by default.
  - Pick a course → its first lecture shows, **no** title bar; monitor control panel hidden.
  - Second dropdown switches lecture ↔ rhythm with the selector; switching back to All rhythms restores the last (or first) rhythm.
  - Open monitor over a lecture → has a close/back; all-rhythms monitor has none.
  - Constructor: typing a Russian course name is smooth (no squiggle/auto-correct interference).

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Teaching: course selector drives main view + default All rhythms | 1 | Core restructure of `TeachingScreen.kt` |
| 2 | Teaching: rhythm picker for All rhythms + auto-select | 2 | `TeachingControlPanel.kt` + VMs |
| 3 | Teaching: remove title bar / top start-stop, gate monitor controls | 3 | Chrome cleanup; verify Compare |
| 4 | Course Constructor: disable auto-correct on name fields | 4 | Small, independent |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:** shipped / dropped / partial
- **PRs:** #…
- **Deviations from plan:** …
- **Follow-ups spawned:** …
