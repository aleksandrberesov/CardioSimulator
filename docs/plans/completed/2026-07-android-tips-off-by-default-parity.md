# Tips overlays OFF by default (monitor) — Android parity

**Status:** completed
**Owner:** AI Assistant
**Started:** 2026-07-13
**Finished:** 2026-07-13
**Related issues / PRs:** Windows→Android sync. Reference change on the Windows port:
`CardioSimulatorWin/src/CardioSimulator.Core/Domain/MonitorMode.cs` — `MonitorModeModel.ShowTips`
default flipped `true → false` (2026-07-13).

**Target (Android) source root:** `E:\VLN_Project\CardioSimulator\app\src\main\java\com\example\cardiosimulator\`
**Reference (Windows) source root:** `E:\VLN_Project\CardioSimulatorWin\src\`

## Goal

The **Tips** button on the monitor is a visibility toggle for authored tip overlays + the "Видим:"
comments card. Today it starts **on**, so every pathology that carries tips shows them the moment it
loads — before the student chooses to reveal them. Make tips **hidden by default**; the student opts in
by tapping the **Подсказки/Tips** tab. Why now: the Windows port just made this change and we keep the
two platforms behaviorally identical. This is a one-line default flip (plus a no-touch note on the
Constructor preview), so there's no reason to defer it.

## Current state

- **Default lives in the model.** [`domain/MonitorModeModel.kt:83`](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/domain/MonitorModeModel.kt#L83)
  declares `val showTips: Boolean = true`. This is the *only* seed for the live monitor's tip
  visibility — there is **no** DataStore/preference persistence for `showTips` (grep confirms it appears
  only in `MonitorModeModel`, `MonitorViewModel`, `TeachingScreen`, `ConstructorScreen`, `Lead`,
  `MonitorControlPanel`). So flipping this default is the whole behavior change. Mirrors the old Windows
  `MonitorMode.cs` default that was just changed.
- **Toggle plumbing (leave as-is).**
  [`ui/viewmodels/MonitorViewModel.kt:260`](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/ui/viewmodels/MonitorViewModel.kt#L260)
  `setShowTips(show)` → `copy(showTips = show)`, and the tab at
  [`ui/panels/MonitorControlPanel.kt:411`](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/ui/panels/MonitorControlPanel.kt#L411)
  toggles `!monitorMode.showTips` and lights via `isActive = monitorMode.showTips`. With the default now
  `false`, the tab correctly starts **unhighlighted** and rendering is gated off — no extra wiring.
- **Render gate.** [`ui/display/Lead.kt:178`](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/ui/display/Lead.kt#L178)
  `if (showTips && tips.isNotEmpty())` and the comments card at
  [`ui/screens/TeachingScreen.kt:485`](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/ui/screens/TeachingScreen.kt#L485)
  `if (mode.showTips && tipComments.isNotEmpty() && !mode.isCompareMode)` both key off `mode.showTips`, so
  they follow the default automatically.
- **Constructor authoring preview — must STAY forced-on.**
  [`ui/screens/ConstructorScreen.kt:1312`](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/ui/screens/ConstructorScreen.kt#L1312)
  passes `showTips = true` into its all-leads preview so an author always sees the overlays they're
  editing, independent of the monitor toggle. This is the exact analogue of Windows
  `ConstructorScreen.cs:1040` (`ShowTips = true // authoring preview always shows tips…`), which was
  **deliberately left unchanged**. Do **not** touch it.
- **Existing Teaching dispose reset.**
  [`ui/screens/TeachingScreen.kt:220`](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/ui/screens/TeachingScreen.kt#L220)
  already calls `setShowTips(false)` in the screen's `onDispose` (alongside electrodes/3D/EOS/ruler),
  so tips reset off whenever the student leaves Teaching. Today the `= true` default only leaks through on
  the *first* Teaching visit after launch; every later visit is already off. Flipping the default makes the
  first visit consistent with the rest. See the divergence note under Risks before deciding whether to keep
  this line.

## Non-goals

- No change to the tips **authoring** feature, the `.dat` `tips:` / `tip_notes:` serialization, or the
  tip render/placement overlays — this is purely the initial *visibility* default. (The authoring/display
  port itself is tracked by `2026-07-android-tips-authoring-and-display-parity.md`.)
- No new preference/persistence for the toggle — Windows has none either; the default is the whole story.
- No relabeling of the Tips tab or restyling of the comments card.

## Plan

### Phase 1 — Flip the default
- In [`domain/MonitorModeModel.kt:83`](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/domain/MonitorModeModel.kt#L83)
  change `val showTips: Boolean = true,` → `val showTips: Boolean = false,`.
- Confirm nothing else seeds `showTips = true` for the live monitor (only `ConstructorScreen.kt:1312`
  should, and it stays true). `grep -rn "showTips = true" app/src/main` should return the Constructor
  preview line and nothing else.
- Leave `MonitorViewModel.setShowTips`, `MonitorControlPanel` tab, `Lead`, and `TeachingScreen` render
  gate untouched — they read the flag and now inherit the `false` default.

### Phase 2 — Polish / decision on the dispose reset
- Decide on the `TeachingScreen.kt:220` `setShowTips(false)` in `onDispose` (see Risks). Recommended:
  **keep it** — it costs nothing, and resetting transient monitor overlays on leaving Teaching is the
  established pattern for its siblings (electrodes/3D/EOS/ruler). Only remove it if we want tips to be
  "sticky within a session" to match Windows exactly (Windows has no such reset).

## Risks & open questions

- **Session stickiness divergence (open).** Windows keeps a single `MonitorViewModel` for the app session,
  so once a student turns tips *on* they stay on across screen switches (the record default only applies at
  construction). Android's `onDispose` reset turns tips back *off* every time the student leaves Teaching.
  Both start **off by default** after this change — which is the whole ask — but they differ on whether a
  user's "on" choice survives leaving the screen. **Recommendation:** treat exact stickiness parity as out
  of scope; keep Android's reset. Revisit only if a stakeholder wants the choice to persist. *(Resolved
  choice pending sign-off.)*
- Low blast radius otherwise: no serialization, migration, or string changes; the flag is UI-transient.

## Verification

- **Build:** `./gradlew :app:assembleDebug` passes.
- **Unit:** existing `PathologyParserTest` (which covers `tips` / `tipComments` round-trips) still green —
  parsing/serialization is untouched, so no test changes expected.
- **Manual (device/emulator):**
  1. Launch the app → open **Teaching** → **All rhythms** → select a pathology that carries tips (e.g. one
     authored with arrows/labels + "Видим:" notes).
  2. Confirm on load: **no** tip overlays drawn, **no** comments card, and the **Подсказки/Tips** tab is
     **not** highlighted. (Before this change, the first pathology after launch showed them.)
  3. Tap the **Tips** tab → overlays + comments card appear and the tab highlights. Tap again → they hide.
  4. Open the **Constructor** on a pathology with tips → the all-leads preview still shows the overlays
     (authoring preview is forced-on and must be unaffected).

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Default monitor tips off (Android parity) | 1 (+2) | One-line default flip in `MonitorModeModel`; decide/keep the Teaching dispose reset. No strings. |

---

## Outcome

- **Result:** shipped
- **PRs:** N/A (applied directly)
- **Deviations from plan:** None. Decided to keep the `TeachingScreen` `onDispose` reset as recommended.
- **Follow-ups spawned:** None.
