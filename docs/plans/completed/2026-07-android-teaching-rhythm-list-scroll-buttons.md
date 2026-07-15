# Plan: Port Teaching Rhythm-List Scroll Up/Down Buttons to Android

**Created:** 2026-07-08  
**Status:** NOT STARTED  
**Direction:** **Windows → Android**

**Target (Android) source root:** `E:\VLN_Project\CardioSimulator\app\src\main\java\com\example\cardiosimulator\`  
**Reference (Windows) source root:** `E:\VLN_Project\CardioSimulatorWin\src\`

---

## 1. Background & Goals

On Windows, the **Teaching** screen's rhythm list (the collapsible left drawer) now shows **two large page-scroll buttons floating in the bottom-right corner** — an up button and a down button — so the long rhythm list can be paged without dragging the scrollbar (helpful on touch screens / large monitors).

### Windows implementation (reference)
- Buttons live in the shared rhythm-list control (`RhythmChoosingPanel.xaml` / `.xaml.cs`) as a vertical stack overlaying the `ListView`'s bottom-right corner: two 52×52 circular accent buttons with up/down chevron glyphs (`&#xE70E;` / `&#xE70D;`), `AccentBrush` background + `OnAccentBrush` (white) foreground.
- They are **opt-in** via a `ShowScrollButtons` property (default **false**), so the pickers/constructor keep the plain list.
- The property is threaded through the drawer wrapper (`RhythmChoosingDrawer.ShowScrollButtons`) and enabled **only for the Teaching drawer** in `MonitorViewerOverlay` (`_rhythmDrawer.ShowScrollButtons = true`).
- On click, each button finds the list's internal `ScrollViewer` and pages by **~90% of the viewport height** (`ChangeView`), clamped to the scroll range so it no-ops at the top/bottom.

### Android goal
Add the same two floating page-scroll buttons to the bottom-right corner of the rhythm list, **only in the Teaching rhythm drawer**, matching the opt-in scoping. The other `RhythmSelector` hosts (Constructor, Data source, Test constructor picker) keep the plain list.

The Android rhythm list is a `LazyColumn` inside the shared `RhythmSelector` composable, so paging is `LazyListState.animateScrollBy(±viewportHeightPx * 0.9f)` (Compose clamps at the ends automatically — no manual range check needed).

---

## 2. Part A: Add an opt-in `showScrollButtons` parameter to `RhythmSelector`

### File to change
- [RhythmSelector.kt](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/ui/panels/RhythmSelector.kt)

### A.1 — New parameter (default false, mirrors Windows `ShowScrollButtons` default)

In the `RhythmSelector` signature (currently around lines 66–74), add a parameter:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RhythmSelector(
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier,
    rhythms: List<PathologyEntry> = emptyList(),
    selectedId: String? = null,
    onRhythmSelect: (PathologyEntry) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    showScrollButtons: Boolean = false,   // NEW — large up/down page-scroll buttons (Teaching drawer only)
) {
```

### A.2 — Coroutine scope for the scroll animations

`listState` already exists (line 83: `val listState = rememberLazyListState()`). Add a coroutine scope right after it:

```kotlin
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()   // NEW
```

### A.3 — Wrap the `LazyColumn` in a `Box` and overlay the buttons

The list is currently (around lines 288–328):

```kotlin
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = listState
        ) {
            listItems.forEach { line ->
                ...
            }
        }
```

Move the `weight(1f)` onto a wrapping `Box` (weight is a `ColumnScope` modifier, so it must sit on the direct child of the outer `Column`), let the `LazyColumn` fill it, and add the floating buttons aligned to `BottomEnd`:

```kotlin
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                listItems.forEach { line ->
                    // ... unchanged item content ...
                }
            }

            // Large page-scroll buttons floating over the list's bottom-right corner.
            // Opt-in (Teaching rhythm drawer only); page by ~90% of the viewport so a sliver
            // of the previous view stays for context. animateScrollBy clamps at the ends.
            if (showScrollButtons) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledIconButton(
                        onClick = {
                            scrollScope.launch {
                                val page = listState.layoutInfo.viewportSize.height * 0.9f
                                listState.animateScrollBy(-page)
                            }
                        },
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = AccentGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Scroll up",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    FilledIconButton(
                        onClick = {
                            scrollScope.launch {
                                val page = listState.layoutInfo.viewportSize.height * 0.9f
                                listState.animateScrollBy(page)
                            }
                        },
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = AccentGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scroll down",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
```

> Keep the existing `ClinicalDashboard(...)` block (lines 330–336) **below** the new `Box`, still a direct child of the outer `Column`. This matches Windows, where the buttons sit at the bottom of the list area (`Grid.Row=2`) and the clinical dashboard is a separate row below (`Grid.Row=3`) — the buttons overlay only the list, not the dashboard.

### A.4 — Imports to add

Add to the import block:

```kotlin
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

Already present and reused: `Column`, `Arrangement`, `padding`, `size`, `fillMaxWidth`, `Alignment`, `Modifier`, `Color`, `Icon`, `dp`, `AccentGreen` (via `ui.theme.*`), `Icons.Default.KeyboardArrowDown`.

> **Gotcha — icon choice.** Use the single-chevron `KeyboardArrowUp` / `KeyboardArrowDown` (matches the Windows single chevrons `E70E`/`E70D`). Do **not** reuse `KeyboardDoubleArrowUp` / `KeyboardDoubleArrowDown` — those are already used in the header for Expand-All / Collapse-All and would read as the same action.

---

## 3. Part B: Enable the buttons for the Teaching drawer only

### File to change
- [TeachingScreen.kt](file:///E:/VLN_Project/CardioSimulator/app/src/main/java/com/example/cardiosimulator/ui/screens/TeachingScreen.kt)

At the Teaching rhythm-drawer `RhythmSelector` call (around lines 307–312, inside the `SideDrawer` `drawerContent`), pass `showScrollButtons = true`:

```kotlin
            drawerContent = {
                RhythmSelector(
                    appViewModel = appViewModel,
                    rhythms = rhythms,
                    selectedId = selectedRhythm?.id,
                    onRhythmSelect = { rhythmViewModel.selectRhythm(it.id) },
                    showScrollButtons = true,   // NEW — Teaching-only large scroll buttons
                )
            },
```

**Do NOT** add `showScrollButtons = true` to the other `RhythmSelector` call sites — leave them on the `false` default so they keep the plain list, matching the Windows scoping:
- `TeachingControlPanel.kt:122` (leave default)
- `ConstructorScreen.kt:539` (leave default)
- `DataSourceScreen.kt:157` (leave default)

> **Note — the two Teaching call sites.** There are `RhythmSelector` usages in both `TeachingScreen.kt:307` (the `SideDrawer` rhythm drawer — **this is the target**) and `TeachingControlPanel.kt:122`. The Windows change enables the buttons on the Teaching monitor's left **rhythm drawer** (`MonitorViewerOverlay._rhythmDrawer`), whose Android counterpart is the `SideDrawer` in `TeachingScreen.kt`. Enable there. If, on running the app, the intended list is actually the `TeachingControlPanel` instance, move the `showScrollButtons = true` to that call site instead — pick the one that is the scrollable rhythm **list** the user pages through in Teaching.

---

## 4. Part C: Verification

### 4.1 Build
- `./gradlew :app:assembleDebug` (or Android Studio build) — confirm it compiles; watch for the new imports resolving.

## Outcome

- **Result:** Completed.
- **PRs:** N/A (implemented directly).
- **Deviations from plan:** None.
- **Follow-ups spawned:** None.
