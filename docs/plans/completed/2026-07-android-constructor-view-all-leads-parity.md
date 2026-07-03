# Android parity plan — Constructor "Show all 12 leads (read-only)" preview

**Status:** ACTIVE · **Created:** 2026-07-02 · **Origin:** Windows port (`CardioSimulatorWin`)

## What was built on Windows (port this)

A new **icon button** in the Constructor ECG screen toolbar that opens a **read-only,
static (non-scrolling) 12-lead grid preview** of the pathology currently being edited.

Behaviour, exactly as shipped on Windows:

1. **Toolbar icon button** (a grid/"view all" glyph), shown only when a pathology is loaded
   (i.e. next to Rename / Group / Duplicate / Delete / **Calculate derived**). Tooltip/label:
   *"Show all 12 leads (read-only)"*.
2. Tapping it opens a preview that shows **all 12 leads** in a **grid** (4 columns × 3 rows),
   rendered **statically** (no sweep, `isRunning = false`), with the standard grid + calibration
   pulse + lead titles — the same per-lead cell renderer the monitor uses.
3. The preview is **read-only**: there is **no pointer/edit wiring** on it. The editing tools stay
   on the normal canvas underneath.
4. It **reflects unsaved edits** — the waveforms are built from the in-memory edited pathology
   (`targetFile.leads`), baseline-zeroed, with any **missing derived leads synthesized** exactly the
   way the repository does: III/aVR/aVL/aVF from I+II, and V1/V3/V4/V5 from V2+V6.
5. **The toolbar and the rhythm list stay visible.** The preview covers **only the canvas working
   area**, not the top toolbar and not the left rhythm drawer. (This was an explicit customer
   correction — the first Windows cut covered the whole screen and had to be re-scoped.)
6. **Live refresh:** with the preview open, selecting a different rhythm in the still-visible list
   re-renders the preview for the newly-selected pathology.
7. A **close (✕)** button in the preview's own top bar dismisses it (title bar also shows the
   pathology name).
8. While the preview is open the Windows build **hides the significant-points drawer handle** (an
   edit affordance) but **keeps the rhythm drawer**.

### Windows files touched (for reference)
- `src/CardioSimulator.App/Screens/ConstructorScreen.cs` — `_viewAllButton`, `BuildAllLeadsOverlay()`,
  `OnViewAllClick`/`RefreshAllLeadsOverlay`/`CloseAllLeadsOverlay`, `BuildAllLeadsMap()`, overlay
  placed in the canvas cell (content row 3), live-refresh hooks in `OnEditorChanged`/`OnAppChanged`.
- `src/CardioSimulator.App/Localization/AppStrings.cs` — `constructor_view_all_leads` ×5 languages.

---

## Android mapping (Jetpack Compose)

File: `app/src/main/java/com/example/cardiosimulator/ui/screens/ConstructorScreen.kt`.

Compose makes several Windows mechanics **unnecessary** — call these out so nobody ports dead
machinery:

- **Live refresh is automatic.** Build the 12-lead map inside `remember(targetFile, baseline) { … }`.
  `constructorViewModel.selectPathology(id)` swaps `targetFile`, which recomposes and rebuilds the
  map — **no manual observers** (the Windows `RefreshAllLeadsOverlay()` hooks have no counterpart).
- **Z-order is source order.** Placing the overlay before the drawer `Box` in the canvas `Box`
  keeps the rhythm drawer drawing on top (visible/tappable) — no explicit "add last" dance.

### Step 1 — toolbar button

In the `if (targetFile != null) { … }` toolbar block (around the existing
`IconButton { showCalculateDerivedDialog = true }` / `Icons.Default.Calculate`), add:

```kotlin
IconButton(onClick = { showAllLeads = true }) {
    Icon(
        Icons.Default.GridView, // fallback: Icons.Default.ViewModule / Icons.Default.GridOn
        contentDescription = stringResource(R.string.constructor_view_all_leads)
    )
}
```

Add the state near the other `show*Dialog` flags:

```kotlin
var showAllLeads by remember { mutableStateOf(false) }
```

### Step 2 — the read-only overlay

The canvas lives in `Box(modifier = Modifier.weight(1f).fillMaxWidth())` (the block that renders the
editor `Row` and, last, the `Box(align = TopStart){ rhythmDrawer(); pointsDrawer() }`).

Insert the overlay **as a child of that canvas `Box`, after the editor `Row` and before the drawer
`Box`**, so the toolbar + lead tabs (siblings in the outer `Column`) stay visible and the rhythm
drawer draws on top:

```kotlin
if (showAllLeads && targetFile != null) {
    AllLeadsPreviewOverlay(
        targetFile = targetFile!!,
        monitorMode = monitorMode,
        baseline = rhythmViewModel.repository.manifest()?.baseline ?: 1024,
        titleName = displayTitle,             // localized name already computed for the toolbar
        onClose = { showAllLeads = false }
    )
}
```

Gate the **points-drawer handle** off while the preview is open (Windows parity — keep the rhythm
drawer, hide the points handle):

```kotlin
// in the TopStart drawer Box:
if (!isDrawerFixed) rhythmDrawer()
if (!showAllLeads) pointsDrawer()
```

### Step 3 — the overlay composable

Self-contained; mirrors the internals of `Monitor` (pixel scale) + the Teaching per-cell render
(`LeadsGrid` + `Lead`, aliased `LeadView` in TeachingScreen). **No changes to `Monitor.kt`** — we do
not reuse `Monitor` because it reads `count`/`scheme` from the shared `MonitorViewModel`, and we must
not mutate the shared monitor mode for a transient preview.

```kotlin
@Composable
private fun BoxScope.AllLeadsPreviewOverlay(
    targetFile: PathologyFile,
    monitorMode: MonitorModeModel,
    baseline: Int,
    titleName: String,
    onClose: () -> Unit,
) {
    // Build the 12-lead map from the *edited* file (reflects unsaved edits). remember(targetFile,…)
    // is what makes it refresh when a different rhythm is picked in the still-visible list.
    val map = remember(targetFile, baseline) {
        buildMap<Lead, Points> {
            fun zeroed(l: Lead): List<Float>? =
                targetFile.leads[l]?.samples?.map { (it - baseline).toFloat() }
            for (lead in Lead.entries) {
                val direct = zeroed(lead)
                if (direct != null) { put(lead, Points(direct)); continue }
                val synth = when (lead) {
                    Lead.III, Lead.aVR, Lead.aVL, Lead.aVF -> {
                        val i = zeroed(Lead.I); val ii = zeroed(Lead.II)
                        if (i != null && ii != null)
                            DerivedLeads.combineIII_aVR_aVL_aVF(i, ii, lead) else null
                    }
                    Lead.V1, Lead.V3, Lead.V4, Lead.V5 -> {
                        val v2 = zeroed(Lead.V2); val v6 = zeroed(Lead.V6)
                        if (v2 != null && v6 != null)
                            DerivedLeads.combineV1_V3_V4_V5(v2, v6, lead) else null
                    }
                    else -> null
                }
                if (!synth.isNullOrEmpty()) put(lead, Points(synth))
            }
        }
    }

    val scheme = monitorMode.gridScheme
    val density = LocalDensity.current
    // 12-lead layout ⇒ displayScaleFactor(12); mirrors Monitor.kt's pxPerMm formula.
    val pxPerMm = density.density * (160f / 25.4f) * monitorMode.displayScale * displayScaleFactor(12)
    val pixelScale = remember(pxPerMm, monitorMode.speed, monitorMode.calibration) {
        PixelScale(pxPerMm = pxPerMm, paperSpeedMmPerSec = monitorMode.speed,
                   gainZoomY = 1f, cal = monitorMode.calibration, zoom = 1f)
    }

    Surface(modifier = Modifier.matchParentSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: pathology title + close.
            Surface(tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(titleName, style = MaterialTheme.typography.titleMedium,
                         modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                    }
                }
            }
            // 12-lead static grid (4×3). No transformable/pointerInput ⇒ read-only.
            CompositionLocalProvider(LocalPixelScale provides pixelScale) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .ekgGrid(scheme = scheme, showBackground = true)
                ) {
                    LeadsGrid(rows = 3, columns = 4, itemCount = 12) { _, lead ->
                        LeadView(
                            points = lead?.let { map[it] }?.takeIf { it.values.size >= 2 }
                                ?: Points(emptyList()),
                            title = lead?.name ?: "",
                            isRunning = false,
                            xOffsetPx = 0f,
                            gridScheme = scheme,
                            significantPoints = targetFile.significantPoints,
                            calibration = monitorMode.calibration
                        )
                    }
                }
            }
        }
    }
}
```

Imports to add (some already present): `com.example.cardiosimulator.domain.DerivedLeads`,
`com.example.cardiosimulator.data.displayScaleFactor`, `androidx.compose.runtime.CompositionLocalProvider`,
`androidx.compose.material.icons.filled.Close`, `androidx.compose.material.icons.filled.GridView`,
and `com.example.cardiosimulator.ui.display.Lead as LeadView` (as TeachingScreen does),
`com.example.cardiosimulator.ui.display.LeadsGrid`. (`LocalPixelScale`, `PixelScale`, `Points`,
`Lead`, `ekgGrid` are already imported in ConstructorScreen.kt.)

### Step 4 — strings (all 5 locales)

Add `constructor_view_all_leads` to **every** `app/src/main/res/values*/strings.xml`
(`values`, `values-ru`, `values-zh`, `values-es`, `values-hi`) — do not skip zh/es. Reuse the
existing `cd_close` for the close button.

| locale | value |
|--------|-------|
| en (`values`) | `Show all 12 leads (read-only)` |
| ru (`values-ru`) | `Показать все 12 отведений (только просмотр)` |
| zh (`values-zh`) | `显示全部 12 导联（只读）` |
| es (`values-es`) | `Mostrar las 12 derivaciones (solo lectura)` |
| hi (`values-hi`) | `सभी 12 लीड दिखाएँ (केवल-पठन)` |

---

## Verify before coding
- `DerivedLeads.combineIII_aVR_aVL_aVF` / `combineV1_V3_V4_V5` take **baseline-zeroed** float lists
  and return baseline-zeroed floats (confirmed 2026-07-02) — so the synthesized `Points` match the
  direct leads. Do **not** double-subtract the baseline.
- `Lead.entries` is the 12 leads in canonical order (same set/order as `LEAD_ORDER` in
  `LeadsGrid.kt`).
- Confirm the actual field name for samples (`stream.samples`) and the `PathologyFile.leads` map on
  the current `ConstructorViewModel.targetFile` type before wiring.
- Pick a grid icon that resolves against the project's Material Icons dependency
  (`Icons.Default.GridView`; fall back to `ViewModule`/`GridOn` if the extended set is absent).

## Gotchas
- **Do not reuse `Monitor(...)`** by mutating `monitorViewModel` count/scheme for the preview — it is
  the shared VM and would leak into the editor's own monitor and other screens. Render the grid
  directly as above.
- **Overlay scope:** it must live inside the canvas `Box` (weight 1f), *not* wrap the whole screen —
  the toolbar, lead tabs, and rhythm drawer must remain visible (customer requirement).
- **Read-only:** attach no `pointerInput`/`transformable`/`clickable` to the preview cells.
- Keep the preview **static** (`isRunning = false`, `xOffsetPx = 0f`) — it is a snapshot, not a live
  sweep.

## Acceptance criteria
- [ ] Grid icon button appears in the Constructor toolbar only when a pathology is loaded.
- [ ] Tapping it shows a static 4×3 grid of all 12 leads (derived leads synthesized), reflecting
      unsaved edits.
- [ ] The top toolbar and the left rhythm list remain visible; the points-drawer handle is hidden
      while open.
- [ ] Selecting another rhythm in the visible list updates the preview live.
- [ ] The preview cannot be edited (no drag/tap effects on the traces); ✕ closes it.
- [ ] `constructor_view_all_leads` present in all 5 `strings.xml`.
