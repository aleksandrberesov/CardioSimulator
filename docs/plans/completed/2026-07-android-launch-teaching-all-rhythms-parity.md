# Launch on Teaching + "All rhythms" (drop last-mode restore)

**Status:** active
**Owner:** Aleksandr (a.beresov)
**Started:** 2026-07-02
**Related issues / PRs:** Behavior already shipped on the Windows port (`CardioSimulatorWin`); this plan ports it to Android.
**Direction:** **Windows → Android**

## Goal

Every app launch should open on the **Teaching** screen with **"All rhythms"** selected (the monitor / rhythms window), regardless of which operating mode the user was last in. Today the app **restores the last-used mode** on launch, so someone who was last in Constructor (or Testing, etc.) reopens there instead of Teaching. The Windows port removed this restore so the app always lands on its home screen; we want the two apps back in sync. Why now: this came straight from the Windows change made in the same session — keep parity while it's fresh.

## Current state

**Windows (source of truth for the target behavior)** — `CardioSimulatorWin/src/CardioSimulator.App/`:
- `ViewModels/AppViewModel.cs` — the constructor no longer restores the persisted mode; it explicitly forces Teaching: `_appState.UpdateMode(_appState.OperatingModes.First(m => m.Id == OperatingMode.Teaching))`. The old `ParseSavedMode()` helper was deleted, and `UpdateOperatingMode` no longer writes `Prefs.LastOperatingMode`.
- `Data/DataSourcePrefs.cs` — the now-dead `LastOperatingMode` property + `last_operating_mode` key were removed.
- "All rhythms" was already guaranteed: on entering Teaching, `Screens/MainScreen.cs` (`BuildForMode`) calls `SelectCourse(null)`, and `TeachingScreen` opens the all-rhythms monitor — no change was needed there.

**Android today (verify before editing):**
- `app/.../ui/viewmodels/AppViewModel.kt`:
  - **Line ~212–219** — the `init` block restores the last mode from prefs (this is what defeats "always Teaching"):
    ```kotlin
    p.lastOperatingMode.first()?.let { modeName ->
        try {
            val modeId = OperatingMode.valueOf(modeName)
            operatingModes.find { it.id == modeId }?.let { modeModel ->
                updateOperatingMode(modeModel, persist = false)
            }
        } catch (_: Exception) {}
    }
    ```
  - **Line ~313–321** — `updateOperatingMode(mode, persist = true)` persists the mode via `prefs?.setLastOperatingMode(mode.id.name)`.
  - **Line ~160** — `private val _selectedCourseId = MutableStateFlow<String?>(ALL_RHYTHMS_ID)` — the course filter **already defaults to "All rhythms"** at construction. ✅ No change needed for the "All rhythms" half.
- `MainActivity.kt:56` — `appBuilder.build(initialMode = OperatingMode.Teaching)` — the default selected mode is **already Teaching** (and `OperatingMode.Teaching` is the first enum entry, so even `modes.first()` would resolve to it). So once the restore block is gone, launch falls back to Teaching automatically.
- `app/.../data/DataSourcePrefs.kt`:
  - **Line ~129–131** — `val lastOperatingMode: Flow<String?>`.
  - **Line ~251–254** — `suspend fun setLastOperatingMode(mode: String)`.
  - **Line ~272** — `private val KEY_LAST_OPERATING_MODE = stringPreferencesKey("last_operating_mode")`.

Implement the *behavior* idiomatically in Kotlin; do not transliterate the C#.

## Non-goals

- **Not** re-porting the "reset course to All rhythms on entering Teaching mid-session" guard — that already shipped on Android (`2026-06-teaching-mode-switch.md`, completed). This plan only fixes the **launch** mode. The course default (`ALL_RHYTHMS_ID`) is already correct.
- Not touching language / theme / TCP restore — those stay persisted and restored.
- No change to `MainActivity`'s explicit `initialMode = OperatingMode.Teaching` (it's already correct and becomes the single source of the launch default).
- Not touching any other screen's interaction model.

## Plan

Single, independently shippable change (one PR).

### Phase 1 — Always launch on Teaching (drop last-mode restore)
- In `AppViewModel.kt` `init`, **delete** the `p.lastOperatingMode.first()?.let { … }` restore block (~lines 212–219). With it gone, `_selectedOperatingMode` keeps its constructed value (`appState.selectedOperatingMode` = Teaching, per `MainActivity.kt:56`), so every launch opens on Teaching. `_selectedCourseId` already defaults to `ALL_RHYTHMS_ID`, so the user lands on the all-rhythms monitor.
- Leave a short comment in place of the deleted block explaining the app intentionally always launches on Teaching (mirror the Windows comment) so a future reader doesn't "restore" it as a regression.

### Phase 2 — Remove the now-dead mode persistence
- In `AppViewModel.kt`, simplify `updateOperatingMode`: drop the `persist` parameter and the `viewModelScope.launch { prefs?.setLastOperatingMode(mode.id.name) }` write. It becomes:
  ```kotlin
  fun updateOperatingMode(mode: OperatingModeModel) {
      appState.updateMode(mode)
      _selectedOperatingMode.value = mode
      // The mode is not persisted: the app always launches on Teaching (see MainActivity).
  }
  ```
- **Verify callers:** the only `persist = false` caller is the restore block being deleted in Phase 1; the remaining callers (mode selector in `TopControlPanel.kt` / wherever the mode chip lives, and any `Ctrl`-style shortcuts) call `updateOperatingMode(mode)` with the default `persist = true`. Confirm with a project-wide search for `updateOperatingMode(` and drop any now-invalid named `persist =` argument.
- In `DataSourcePrefs.kt`, remove the dead members: the `lastOperatingMode` flow (~129–131), `setLastOperatingMode` (~251–254), and `KEY_LAST_OPERATING_MODE` (~272). Confirm nothing else references them first (grep `lastOperatingMode` / `setLastOperatingMode` / `KEY_LAST_OPERATING_MODE`).

> Phase 2 is cleanup and can fold into the same PR as Phase 1, but keep it a separate commit so the behavioral change and the dead-code removal are reviewable apart.

## Risks & open questions

- **Migration / stale pref:** removing the DataStore key is forward-safe (an unread key is simply ignored; DataStore won't crash on an unknown/leftover entry). No migration needed.
- **Drift:** line numbers above are from the current tree — re-grep before editing; the `init` block is long and may have shifted.
- **Hidden caller of `updateOperatingMode(persist = …)`:** low risk, but the project-wide search in Phase 2 is the gate. If some flow legitimately wants a non-persisting update, it can just call `updateOperatingMode(mode)` now (nothing persists anymore).
- **Config-change / process-death:** on Android a config change recreates `AppViewModel` from scratch; with the restore gone, a rotate/return-from-background that recreates the VM will reset to Teaching. This matches the Windows single-window behavior and the intent ("always launch on Teaching"), but confirm it isn't jarring in manual QA. If it is, that's a follow-up (persist within-process only), not part of this parity change.

## Verification

- `./gradlew :app:assembleDebug` clean.
- Manual on device/emulator:
  1. Launch, switch to **Constructor** (or any non-Teaching mode), fully close the app.
  2. Relaunch → app opens on **Teaching** with the **all-rhythms monitor** showing (not Constructor, not a course).
  3. Repeat from **Testing** and **Course Constructor** → still opens on Teaching.
  4. Within a session, switching modes still works normally (only the *launch* default changed).

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Always launch on Teaching + All rhythms (drop last-mode restore) | 1–2 | Delete restore block; remove `lastOperatingMode` persistence + pref. Two commits. |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:** shipped / dropped / partial
- **PRs:** #…
- **Deviations from plan:** …
- **Follow-ups spawned:** …
