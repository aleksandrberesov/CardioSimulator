# Plan: Move the Электроды button next to Фильтры with a divider between them (Android parity)

**Created:** 2026-07-02
**Status:** ACTIVE
**Direction:** **Windows → Android** (the usual). Built in the WinUI 3 port first; Android must catch
up. The Windows port is the **reference implementation** — match its layout, adapting to Compose.

**Target (Android) source root:** `E:\VLN_Project\CardioSimulator\app\src\main\java\com\example\cardiosimulator\`
**Reference (Windows) source root:** `E:\VLN_Project\CardioSimulatorWin\src\`

## Goal

On the Teaching-mode monitor bottom control row, move the **Электроды** button so it sits directly
**after Фильтры**, and drop a **vertical divider between Фильтры and Электроды**. This regroups the
middle-left cluster from `Электроды · Артефакты · Фильтры · 3D` into
`Артефакты · Фильтры ‖ Электроды · 3D`, so the two dropdown options (Артефакты/Фильтры) read as one
group and the windowed options (Электроды/3D) as another, separated by a hairline. Purely a layout
reorder — no behaviour, string, or model changes.

## Current state

- **Windows (done, reference):** `CardioSimulator.App/Controls/MonitorControlPanel.xaml`. The panel is
  a single `Grid`; the middle-left group is now `Artifacts (col 6) · Filters (col 7) · divider (col 8)
  · Electrodes (col 9) · 3D (col 10)`. The divider is a 1px `Border` with `HairlineBrush`. Everything
  is wired by `x:Name`, not column index, so only the XAML column layout changed (plus a doc-comment
  refresh in `.xaml.cs`). The `ElectrodesTab` keeps its tri-state highlight
  (`ApplyElectrodesVisual`: neutral / green OK / red fault).
- **Android (to change):** `ui/panels/MonitorControlPanel.kt`, the **"Middle-left section"** `Row`
  (currently ~lines 216–357, `Modifier.weight(3.5f)`). Present child order:
  1. `Электроды` `Tab` — `weight(1f)`, `isActive = monitorMode.electrodeStateUserSet`,
     `activeColor = if (electrodeFault) ElectrodeFaultRed else AccentGreen` (~lines 222–228).
  2. `Артефакты` `Box(weight 1.5f)` with its `DropdownMenu` (~lines 230–302).
  3. `Фильтры` `Box(weight 1.5f)` with its `DropdownMenu` + `SqiBadge` (~lines 304–348).
  4. `3D` `Tab(weight 1f)` (~lines 350–356).
- `ControlPanelDivider()` (`ui/components/ControlPanelDivider.kt`) is a 1dp `VerticalDivider`, black by
  default, **non-weighted** — it already separates the top-level sections at lines 214, 359, 396, and
  can be dropped inside a weighted `Row` between two cells without disturbing the weight math.

## Non-goals

- No change to any button's action, `isActive` logic, colours, tooltips, or the Электроды tri-state
  highlight — this is a reorder only.
- No new strings, drawables, model fields, or view-model methods.
- Don't touch the other sections (left dropdowns, pQRSt/ЭОС/Подсказки, ruler/compare/start-stop) or
  the top-level section dividers.

## Plan

### Phase 1 — Reorder the middle-left cluster
In `MonitorControlPanel.kt`, inside the `Modifier.weight(3.5f)` `Row`, change the child order to:

1. `Артефакты` `Box(weight 1.5f)` (move up, unchanged).
2. `Фильтры` `Box(weight 1.5f)` (move up, unchanged).
3. **`ControlPanelDivider()`** — new, between Фильтры and Электроды.
4. `Электроды` `Tab(weight 1f)` (move down; keep `isActive`/`activeColor`/`onClick` exactly as-is,
   including the `electrodeFault` val — just relocate it below the two Boxes).
5. `3D` `Tab(weight 1f)` (unchanged).

Update the section comment `// Middle-left section: Electrodes, Artifacts, 3D` →
`// Middle-left group: Artifacts, Filters | Electrodes, 3D`.

That's the whole change. The remaining weighted children (1.5 + 1.5 + 1 + 1) still divide the space
after the 1dp divider takes its intrinsic width; the `spacedBy(4.dp)` gap absorbs the extra cell.

## Risks & open questions

- **Divider colour/consistency:** use the default `ControlPanelDivider()` (black) to match the other
  in-panel section dividers. Windows uses `HairlineBrush`; the Android panel has always used the black
  `VerticalDivider` for its section breaks, so keep that for visual consistency — do **not** introduce
  a new hairline token.
- **Width budget:** the section `Row` is `weight(3.5f)`; adding a 1dp divider + one 4dp gap is
  negligible, but eyeball the row on a phone-width preview to confirm the Электроды/Фильтры labels
  aren't clipped. If tight, this row already lives inside the horizontally scrollable panel.

## Verification

- Build passes.
- Teaching monitor bottom row reads `… ‖ Артефакты▾ · Фильтры▾ ┃ Электроды · ♥3D ‖ pQRSt …`.
- A hairline divider is visible between Фильтры and Электроды.
- Электроды still opens its window and still shows the tri-state highlight (neutral → green OK → red
  fault); Артефакты/Фильтры dropdowns and the SQI badge behave exactly as before.
- Nothing shifted in the left dropdowns or the pQRSt/ЭОС/Подсказки/ruler/compare/start-stop sections.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Move Электроды next to Фильтры with divider | 1 | Single-file reorder in `MonitorControlPanel.kt` |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:** shipped / dropped / partial
- **PRs:** #…
- **Deviations from plan:** …
- **Follow-ups spawned:** …
