# Plan: Refine the Teaching Rhythm-List Scroll-Button Appearance on Android

**Created:** 2026-07-13
**Status:** completed
**Owner:** AI Assistant
**Started:** 2026-07-13
**Finished:** 2026-07-13

**Target (Android) source root:** `E:\VLN_Project\CardioSimulator\app\src\main\java\com\example\cardiosimulator\`
**Reference (Windows) source root:** `E:\VLN_Project\CardioSimulatorWin\src\`

**Builds on (completed):** [`2026-07-android-teaching-rhythm-list-scroll-buttons.md`](../completed/2026-07-android-teaching-rhythm-list-scroll-buttons.md) — that plan *added* the two floating page-scroll buttons; this one *reskins* them.

---

## 1. Background & Goals

The Teaching rhythm drawer's floating up/down page-scroll buttons shipped as **two 52×52 fully-circular accent FABs** overlapping the list's bottom-right corner. In use on Windows the customer flagged three problems, all cosmetic:

1. **The circular shape looks inappropriate** for a list-paging control.
2. **They cover the pathology titles** underneath — floating glyphs sitting directly on top of the list text, with nothing between them, read as broken.
3. **On press they went transparent / invisible** — a WinUI-specific quirk (setting `Background` locally on a `Button` is discarded by the default control template's Pressed/PointerOver visual states, which swap in a near-transparent theme brush).

### Windows redesign (reference — already shipped)
`RhythmChoosingPanel.xaml` (`CardioSimulatorWin/.../Controls/`) — the floating `StackPanel` of two circular buttons was replaced with an **opaque, bordered chip** containing two **rectangular** buttons:

- Container: a `Border` anchored bottom-right (`Margin="0,0,8,8"`), `Padding="3"`, `CornerRadius="8"`, `Background="{StaticResource PanelBackgroundBrush}"` (opaque white), `BorderBrush="{StaticResource ControlBorderBrush}"`, `BorderThickness="1"`. Because it is opaque, the rhythm titles underneath are cleanly hidden behind the chip instead of showing through.
- Buttons: `40×34`, `CornerRadius="6"` (rectangular, not circular), inner `StackPanel` `Spacing="4"`, glyphs `&#xE70E;` (up) / `&#xE70D;` (down) at `FontSize="16"`.
- **Pressed-state fix:** switched from a local `Background="{AccentBrush}"` to `Style="{StaticResource AccentButtonStyle}"`. Since the app remaps the system accent to green, the accent button's Pressed/PointerOver states are proper *darker-green* ramps that stay visible.

### Android goal
Port the **visual redesign** (smaller rectangular buttons inside an opaque bordered chip) to the Android scroll buttons, keeping the existing opt-in scoping (Teaching drawer only) and the existing paging behavior unchanged.

> **Note — problem #3 does not exist on Android.** Compose's `FilledIconButton` with `filledIconButtonColors(containerColor = AccentGreen, contentColor = Color.White)` keeps its container color while pressed (it draws a translucent *state-layer overlay* on top, it does **not** swap the background out). So the "invisible on press" bug is a WinUI template artifact only. This plan is therefore **appearance-only** — no state/behavior change is needed for Android to match the fixed Windows look.

---

## 2. Current state (Android)

The buttons live in the shared `RhythmSelector` composable, added by the predecessor plan:

- [RhythmSelector.kt](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/ui/panels/RhythmSelector.kt) **lines 347–393** — an `if (showScrollButtons) { Column(align BottomEnd, padding end=10/bottom=10, spacedBy 10.dp) { FilledIconButton(size 52.dp, circular, AccentGreen/White, KeyboardArrowUp, icon 28.dp) ; FilledIconButton(… KeyboardArrowDown …) } }` block, overlaying the `LazyColumn` inside the wrapping `Box`.
- Opt-in via the `showScrollButtons: Boolean = false` param (line 83), enabled only at the Teaching drawer call site (`TeachingScreen.kt`). **No scoping change in this plan.**

Theme tokens already available (imported via `ui.theme.*` at line 69, [Color.kt](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/ui/theme/Color.kt)) map 1:1 to the Windows brushes used:
- `PanelBackground` = `0xFFFFFFFF` ↔ `PanelBackgroundBrush`
- `ControlBorder`   = `0xFFE0E4EC` ↔ `ControlBorderBrush`
- `AccentGreen`     = `0xFF33A06A` ↔ `AccentBrush`

Already imported and reusable: `RoundedCornerShape` (line 18), `Surface` (line 39), `Color` (line 51), `size`/`padding`/`Column`/`Arrangement` (layout), `FilledIconButton`/`IconButtonDefaults`.

---

## 3. Non-goals

- **No behavior change.** Paging stays `animateScrollBy(±viewportHeight * 0.9f)`; clamp-at-ends is unchanged.
- **No scoping change.** Still Teaching-drawer-only; the other `RhythmSelector` hosts keep the plain list.
- **No new icons.** Keep the single-chevron `KeyboardArrowUp` / `KeyboardArrowDown` (matching Windows `E70E`/`E70D`); do not switch to double-chevrons (reserved for Expand/Collapse-All).
- **No press-state hack.** `filledIconButtonColors` already keeps the fill on press; do not add custom pressed handling.

---

## 4. Plan

### Phase 1 — Reskin the scroll buttons in `RhythmSelector.kt`

Replace the `if (showScrollButtons) { Column(...) { … } }` block (lines 347–393) with an **opaque bordered chip** wrapping two **rectangular** buttons:

```kotlin
            // Compact page-scroll buttons anchored to the list's bottom-right. Opt-in
            // (Teaching drawer only); page by ~90% of the viewport so a sliver of the
            // previous view stays for context. animateScrollBy clamps at the ends.
            // Wrapped in an opaque, bordered chip so the rhythm titles underneath stay
            // legible instead of showing through the floating buttons.
            if (showScrollButtons) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = PanelBackground,
                    border = BorderStroke(1.dp, ControlBorder),
                    shadowElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier.padding(3.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledIconButton(
                            onClick = {
                                scrollScope.launch {
                                    val page = listState.layoutInfo.viewportSize.height * 0.9f
                                    listState.animateScrollBy(-page)
                                }
                            },
                            modifier = Modifier.size(width = 40.dp, height = 34.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = AccentGreen,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Scroll up",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        FilledIconButton(
                            onClick = {
                                scrollScope.launch {
                                    val page = listState.layoutInfo.viewportSize.height * 0.9f
                                    listState.animateScrollBy(page)
                                }
                            },
                            modifier = Modifier.size(width = 40.dp, height = 34.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = AccentGreen,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll down",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
```

Deltas vs. the current block:
- Wrap the two buttons in a `Surface` chip: `RoundedCornerShape(8.dp)`, `color = PanelBackground` (opaque), `border = BorderStroke(1.dp, ControlBorder)`, `shadowElevation = 3.dp`, inner `padding(3.dp)`.
- Chip offset `end = 8 / bottom = 8` (was `10 / 10`); inner button spacing `4.dp` (was `10.dp`).
- Buttons `size(width = 40.dp, height = 34.dp)` (was circular `size(52.dp)`) + explicit `shape = RoundedCornerShape(6.dp)`.
- Icon size `18.dp` (was `28.dp`).
- Colors, `onClick` paging, icons, and the `showScrollButtons` gate are **unchanged**.

### Phase 2 — Imports

Add the one missing import:

```kotlin
import androidx.compose.foundation.BorderStroke
```

Already present / no change: `RoundedCornerShape`, `Surface`, `Color`, `size` (the two-arg `Modifier.size(width, height)` overload uses the same `androidx.compose.foundation.layout.size` import already at line 15), `padding`, `Column`, `Arrangement`, `FilledIconButton`, `IconButtonDefaults`, `Icon`, `Icons.Default.KeyboardArrowUp/Down`, `PanelBackground`/`ControlBorder`/`AccentGreen` (via `ui.theme.*`).

---

## 5. Risks & open questions

- **`FilledIconButton` min touch target.** Material may enforce a 48.dp minimum touch target and log a warning for a 40×34 visual. This is cosmetic only (the visual shrinks; the touch area may stay ≥48). If it misbehaves, wrap in `CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false)` or swap `FilledIconButton` for a `Surface`+`clickable`. Decide on-device — **do not** pre-emptively add the workaround.
- **Shadow under an opaque chip on a light drawer.** `shadowElevation = 3.dp` should read as a subtle lift against the white drawer; if it looks heavy, drop to `2.dp` or `1.dp`. Match the Windows feel (a quiet bordered chip, not a raised card).
- **Chip vs. dashboard overlap.** As before, the chip overlays only the `LazyColumn` inside the `Box`; the `ClinicalDashboard` remains a sibling below the `Box` (unchanged), so the chip never covers the dashboard.

---

## 6. Verification

### 6.1 Build
- `./gradlew :app:assembleDebug` — confirm it compiles; watch the new `BorderStroke` import resolves and the `FilledIconButton(shape = …)` overload is accepted.

### 6.2 Manual (Teaching screen, rhythm drawer open)
- Scroll buttons are **rectangular** (rounded 6.dp corners), **not circular**.
- They sit inside a **white bordered chip** in the bottom-right; the rhythm titles beneath the chip are hidden by it, not bleeding through.
- **Pressing** a button keeps it green (state-layer darkening only) — never blank/transparent.
- Up pages toward the top, Down toward the bottom, each ~90% of a viewport; both no-op at the respective end. (behavior unchanged)
- Other rhythm hosts (Constructor, Data source, Test-constructor picker) still show **no** scroll buttons.

---

## 7. PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Reskin Teaching rhythm scroll buttons (rectangular, opaque chip) | 1–2 | Single file (`RhythmSelector.kt`); +1 import; appearance-only |

---

## Outcome

- **Result:** shipped
- **PRs:** N/A (applied directly)
- **Deviations from plan:** None.
- **Follow-ups spawned:** None.
