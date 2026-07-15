# Plan — ЭОС window: white text for the deviation variant rows (Android parity)

**Status:** active
**Owner:** a.beresov
**Started:** 2026-07-14
**Related:** Windows→Android UI parity. Source of truth = Windows port
`CardioSimulatorWin/src/CardioSimulator.App/Controls/EosWindow.cs` (`Variant(...)`).
Directly amends the **"Follow-up increment (2026-07-07) — red-highlight the abnormal (deviation)
axes"** section of `docs/plans/completed/2026-07-android-eos-live-axis-parity.md` (effect #1 of that
increment is being partly walked back).

## Goal

On Windows the three abnormal-axis rows in the ЭОС ("electrical axis") window
(`_left` / `_right` / `_extreme` — Left / Right / Extreme deviation) were the only red items in the
variants list. Per customer request they are now rendered in **white**, like the other three rows, so
the list no longer reads as three permanently-red "legend" entries. **Why now:** the Windows tweak is
done and built (0 warn / 0 err); leaving Android with the red rows re-opens a visible divergence on a
customer-facing screen that was just closed.

**Important — keep the other two red cues.** This is *only* about the inactive variant-row text
colour. The two remaining red signals from the 2026-07-07 increment stay exactly as they are:

1. The **red pill** behind the *active* row when the computed axis is a deviation (`EosWarningPill`).
2. The **red α readout** line (`α = N° — <band>`) when the computed axis is a deviation (`EosWarning`).

So after this change red survives *only* as an alert for the axis the current ECG actually has — it is
no longer used to pre-colour the three deviation rows as a static legend.

## Current state (Android)

All in `app/src/main/java/com/example/cardiosimulator/ui/components/MonitorOverlays.kt`:

- `VariantRow(fullText, isActive, warning)` at `:235`. Line **`:241`** is the exact analogue of the
  Windows line that changed:
  ```kotlin
  val textColor = if (isActive) Color.White else if (warning) EosWarning else Color.White
  ```
  → the `else if (warning) EosWarning` branch is what paints the inactive deviation rows red.
- The pill colour at `:242` — `val pillColor = if (warning) EosWarningPill else Color.White.copy(alpha = 0.25f)` — is the **red active-deviation pill**; **do not touch it.**
- The α readout at `:186`/`:193` (`color = if (isWarning) EosWarning else Color.White`) is the **red α line**; **do not touch it.**
- Colours `EosWarning = Color(0xFFFF5A5A)` (`:42`) and `EosWarningPill = Color(0x77E53935)` (`:43`) and
  the `isWarning(...)` helper (`:45`) all stay — `EosWarning` is still used by the α readout, and the
  `warning` param + `EosWarningPill` are still used by the active pill.

### What Windows did (the diff to mirror)

In `EosWindow.cs` `Variant(...)` the row text brush changed from
```csharp
Foreground = active ? White : (warning ? Warning : White),
```
to
```csharp
Foreground = White,
```
The `warning` parameter is retained (it still selects `WarningFill` — the red pill — for the active
deviation row), and `Measured(...)` still turns the α line red via `IsWarning(...)`. Presentation-only;
no domain / analyzer / string / test change.

## Non-goals

- **No** change to the red active-deviation pill (`EosWarningPill` at `:242`) or the red α readout
  (`:193`). Those stay red.
- **No** domain / `EosAxis` / `EosAnalyzer` / string / unit-test changes.
- **No** removal of the `warning` param, the `isWarning(...)` helper, or the `EosWarning` /
  `EosWarningPill` colours — all still referenced.
- No other EOS behaviour (diagram, live compute, on-trace highlight, localization) touched.

## Plan

### Phase 1 — White variant-row text (single line)
In `MonitorOverlays.kt`, `VariantRow`, change `:241`:
```kotlin
// before
val textColor = if (isActive) Color.White else if (warning) EosWarning else Color.White
// after
val textColor = Color.White
```
That's the whole functional change. Optionally tidy the now-dead ternary and refresh the adjacent
comment, but leave the `warning` param, `pillColor` (`:242`), and the α-line colour (`:193`) untouched.

### Phase 2 — Polish / verify
Confirm `EosWarning` and `EosWarningPill` are still referenced (α line + active pill) so no
"unused" lint appears, then build and smoke-test.

## Risks & open questions

- **Over-reaching the change** — the one real risk is also greying out the active pill or the α line.
  Don't: the request is specifically "the last three red list rows → white". Verify both red cues still
  appear on a deviation rhythm (Phase-2 checks below).
- `EosWarning` remains in use (α readout), so removing the `else if (warning) EosWarning` branch will
  not orphan the colour constant.

## Verification

- `./gradlew :app:assembleDebug` (and `:app:testDebugUnitTest` — unchanged, still green).
- Manual, Teaching mode, "All rhythms":
  - Open **EOS** on a **normal-axis** rhythm → all six variant rows are **white**; the active
    (Normal / Horizontal / Vertical) row keeps its neutral translucent-white pill; no red anywhere.
  - Open **EOS** on a **deviation** rhythm (α outside the normal band) → the six rows are still all
    white text, **but** the active deviation row keeps its **red pill** and the `α = …° — <band>`
    readout is still **red**. (Both red cues must survive.)
- Language switch EN/RU/ZH/ES/HI → unchanged (no string edits); ZH `：` split still bolds the active
  name correctly.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | EOS: white text for deviation variant rows | 1–2 | one-line `textColor` change in `VariantRow`; keep red active pill + red α line |

---

## Outcome

- **Result:** shipped
- **PRs:** N/A (applied directly)
- **Deviations from plan:** None.
- **Follow-ups spawned:** None.
