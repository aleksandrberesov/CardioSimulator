# Teaching "All rhythms" rhythm-info button (Android parity)

**Status:** completed
**Owner:** AI Assistant
**Started:** 2026-06-29
**Finished:** 2026-06-29

**Direction:** **Windows → Android.** The Windows port (`CardioSimulatorWin`) added this on
2026-06-29; Android must catch up. Windows is the reference for behaviour — match it, adapting idioms
to Kotlin/Compose.

---

## Goal

In **Teaching → "All rhythms"** (the standalone monitor view, where there is no title bar), add a
**graduation-cap icon button at the monitor's top-right corner**. Tapping it opens a **full-monitor
info screen** (an opaque overlay that takes over the entire monitor space, *not* a small corner popup)
describing the **currently selected rhythm**, composed from manifest data:

- **Localized name** (RU/EN per app language) + the other-language name underneath.
- **Leads:** the lead count (`PathologyEntry.leadsCount`).
- **Markers:** the distinct ECG significant-point labels (P, QRS, R, T…), in complex order, with the
  `<sub>…</sub>` tags stripped to plain text.

The screen has a header (title + close button) over the details and a close (✕) to return to the
monitor. This restores — as an on-demand full screen — the "pathology description card" content that
used to be a full panel before "All rhythms" became the monitor.

## Current state (Android) — what already exists

All of this is in `ui/screens/TeachingScreen.kt`, composable `MonitorOverlay` (`:230`):

- **All-rhythms gating is already there.** `TeachingScreen` calls `MonitorOverlay(..., onClose = null)`
  in the all-rhythms branch (`:144-150`) and `MonitorOverlay(..., onClose = { … })` when the monitor is
  a pop-over a course (`:167-174`). So **`onClose == null` ⟺ standalone all-rhythms view** — exactly the
  condition the Windows info button keys off (it shows iff the close button is hidden). The all-rhythms
  branch already renders **no title bar** (the `if (onClose != null || …)` header at `:371` is false).
- **All the data is already collected in `MonitorOverlay`'s scope:** `selectedRhythm` (`:238`),
  `significantPoints` (`:241`), `selectedLanguage` (`:242`), `mode` (`:243`).
- **The monitor's top-right corner currently holds the SQI card** (`:478-485`), aligned
  `Alignment.TopEnd` with `padding(top = 16.dp, end = 16.dp)`. Windows deliberately **moved its SQI card
  to the bottom-right** to free this corner for the info button — Android should do the same (Change 2).
- `EcgPointType` already carries the `<sub>`-tagged labels (`domain/SignificantPoint.kt:7-21`), and
  `PathologyEntry` already has `leadsCount`/`titleEn`/`nameRu` (`domain/Pathology.kt:39-43`). No domain
  changes needed.
> Note: there is a separate, unused placeholder `PathologyDescription` composable in this file
> (`:188-228`) that shows name + id + a hard-coded "Описание патологии будет доступно…" string. It is
> **not** what we want — leave it alone (or delete it if confirmed dead). The new screen composes
> name + lead count + markers like Windows, with no hard-coded text. (It is, however, a reasonable
> *layout* reference — a centered `Column` of `Text`s — for the new full-screen content.)

---

## Change 1 — add the rhythm-info button + full-monitor screen

In `MonitorOverlay`, add a state flag alongside the other `remember`ed flags (`:246-249`):

```kotlin
var showRhythmInfo by remember { mutableStateOf(false) }
```

Then, **inside the `Box(modifier = Modifier.weight(1f))` that wraps the `Monitor`** (`:399-502`), add
two things as the *last* children of that `Box` (so they float/cover above the `Monitor` and `SqiCard`):

1. The **graduation-cap button** at the top-right corner — shown only in the standalone all-rhythms view
   and outside compare mode (the screen describes a single selected rhythm; compare has no single rhythm
   and uses its own top bar).
2. The **full-monitor info screen** — an opaque `Surface(Modifier.fillMaxSize())` shown when
   `showRhythmInfo`, so it takes over the entire monitor `Box`. Because it is the last child it draws on
   top; being opaque + full-size, it fully hides the monitor while open.

```kotlin
// Graduation-cap button — standalone "All rhythms" view only (no title bar there). Mirrors the Windows
// top-right button; tapping it opens the full-monitor rhythm-info screen below.
if (onClose == null && !mode.isCompareMode) {
    IconButton(
        onClick = { showRhythmInfo = true },
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.School, // graduation-cap (mortarboard) — matches the Windows "Education" glyph
            contentDescription = stringResource(R.string.rhythm_info_tooltip)
        )
    }
}

// Full-monitor rhythm-info screen — opaque overlay filling the whole monitor Box. Header (title +
// close) over the scrollable details. Mirrors the Windows _infoScreen takeover.
if (showRhythmInfo && onClose == null) {
    RhythmInfoScreen(
        pathology = selectedRhythm,
        significantPoints = significantPoints,
        language = selectedLanguage,
        onClose = { showRhythmInfo = false },
        modifier = Modifier.fillMaxSize()
    )
}
```

And the screen composable (a private `@Composable` in the same file, mirroring the Windows
`BuildInfoScreen` / `BuildInfoContent` / `MarkerSummary`):

```kotlin
@Composable
private fun RhythmInfoScreen(
    pathology: PathologyEntry?,
    significantPoints: List<SignificantPoint>,
    language: Language,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Opaque so the monitor behind it is hidden; tonalElevation lifts it above the trace.
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background, tonalElevation = 8.dp) {
        Column(Modifier.fillMaxSize()) {
            // Header: title + close.
            Surface(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.rhythm_info_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                    }
                }
            }
            // Scrollable details.
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pathology == null) {
                    Text(text = stringResource(R.string.mode_teaching), style = MaterialTheme.typography.headlineSmall)
                    return@Column
                }
                val primary = if (language == Language.RU) pathology.nameRu ?: pathology.titleEn else pathology.titleEn
                val secondary = if (language == Language.RU) pathology.titleEn else pathology.nameRu
                Text(text = primary, style = MaterialTheme.typography.headlineMedium)
                if (!secondary.isNullOrBlank() && secondary != primary) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${stringResource(R.string.pathology_leads_label)}: ${pathology.leadsCount}",
                    style = MaterialTheme.typography.bodyLarge
                )
                // Distinct marker labels in complex order; the enum's declaration order IS complex order,
                // so sortedBy { ordinal } matches the Windows OrderBy((int)type). Strip <sub> tags.
                val markers = significantPoints
                    .map { it.type }
                    .distinct()
                    .sortedBy { it.ordinal }
                    .joinToString(", ") { it.label.replace("<sub>", "").replace("</sub>", "") }
                if (markers.isNotEmpty()) {
                    Text(
                        text = "${stringResource(R.string.pathology_markers_label)}: $markers",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
```

New imports for `TeachingScreen.kt`:
- `androidx.compose.material.icons.filled.School` (graduation-cap icon — the Material analog of the
  Windows "Education" glyph)
- `androidx.compose.foundation.rememberScrollState`, `androidx.compose.foundation.verticalScroll`
- `com.example.cardiosimulator.domain.PathologyEntry`
- `com.example.cardiosimulator.domain.SignificantPoint`

(`Surface`, `Column`, `Row`, `Spacer`, `height`, `fillMaxSize`, `fillMaxWidth`, `padding`,
`Arrangement`, `IconButton`, `Icon`, `Icons.Default.Close`, `Text`, `MaterialTheme`, `Alignment`,
`stringResource`, `Language` are all already imported. `R.string.cd_close` already exists.)

## Change 2 — move the SQI card to the bottom-right (clear the top-right corner)

So the info button and SQI card don't collide, mirror the Windows move of the SQI card from top-right
to bottom-right. In `MonitorOverlay` (`:478-485`):

```kotlin
// before
com.example.cardiosimulator.ui.components.SqiCard(
    signal = …,
    samplingRate = …,
    modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(top = 16.dp, end = 16.dp)
)

// after
com.example.cardiosimulator.ui.components.SqiCard(
    signal = …,
    samplingRate = …,
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(bottom = 16.dp, end = 16.dp)
)
```

> `EosOverlay` (`:487-492`) and `TipsOverlay` (`:494-501`) are also `Alignment.TopEnd`, but they are
> transient, user-triggered full cards (opened from the bottom panel) — when one is open it takes over
> that corner. The info button is gated to `!mode.isCompareMode` already; if overlap with those overlays
> looks bad in practice, additionally gate the button on `!mode.showEos && !mode.showTips`. Optional.

## Change 3 — strings (all four locales)

Add to `res/values/strings.xml`, `values-ru`, `values-zh`, `values-es` (after `monitor_tips_note`):

| key | en | ru | zh | es |
|-----|----|----|----|----|
| `rhythm_info_tooltip` | About this rhythm | О ритме | 关于此心律 | Acerca de este ritmo |
| `rhythm_info_title` | Rhythm information | Информация о ритме | 心律信息 | Información del ritmo |
| `pathology_leads_label` | Leads | Отведения | 导联 | Derivaciones |
| `pathology_markers_label` | Markers | Маркеры | 标记 | Marcadores |

(`monitor_tips_note` is at `values:80`, `values-ru:79`, `values-zh:74`, `values-es:75`.)

## Out of scope / do not touch

- The course pop-over path (`onClose != null`) — the info button is intentionally **not** shown there
  (matches Windows, where it appears only in the standalone all-rhythms view).
- The unused `PathologyDescription` placeholder composable (`TeachingScreen.kt:188-228`).
- Compare mode, presets, and the existing top-bar/save-preset header.

## Acceptance criteria

1. In Teaching → "All rhythms", a **graduation-cap (🎓) icon button** is visible at the monitor's
   **top-right corner**; it is **absent** when the monitor is popped over a course (course mode) and **absent** in
   compare mode.
2. Tapping it opens a **full-monitor screen** (opaque, covering the whole monitor area) showing: the
   selected rhythm's **localized name** (+ other-language name), **Leads: N**, and **Markers: …**
   (distinct point labels, plain text, complex order), under a header with a **close (✕)** button.
3. Tapping the close button returns to the monitor.
4. The screen reflects the current rhythm/language each time it is opened (it reads the live
   `selectedRhythm` / `significantPoints` / `selectedLanguage` state).
5. The **SQI card** now sits at the **bottom-right** and no longer overlaps the button.
6. New strings render in all four locales (EN/RU/ZH/ES).

## Files

- `app/src/main/java/com/example/cardiosimulator/ui/screens/TeachingScreen.kt` — add `showRhythmInfo`
  state + the graduation-cap button and the `if (showRhythmInfo) RhythmInfoScreen(...)` overlay inside
  the monitor `Box` (`:399`), add the `RhythmInfoScreen` composable, move `SqiCard` to `BottomEnd`
  (`:482`), add imports.
- `app/src/main/res/values/strings.xml` (+ `values-ru`, `values-zh`, `values-es`) — 4 new keys.

## Windows reference (shipped 2026-06-29)

- `src/CardioSimulator.App/Controls/MonitorViewerOverlay.cs` — `_info` button (Segoe MDL2
  "Education"/graduation-cap glyph U+E7BE) anchored top-right of the content grid; its `Click` calls
  `ShowInfoScreen(true)`, which opens `_infoScreen` — a **full-content-area** opaque `Grid` (spans both
  columns, added last so it draws on top) with a header (title + `Symbol.Cancel` close button) over a
  `ScrollViewer`/`_infoContent`. `BuildInfoContent()` (name + other-language name +
  `PathologyEntry.LeadsCount` + `MarkerSummary()`) is rebuilt each time the screen opens. Button
  visibility tied to `SetCloseButtonVisible` (shown iff the close button is hidden, i.e. all-rhythms
  only), which also closes the screen on a mode switch; tooltip from `AppStrings.RhythmInfoTooltip`,
  header title + content refreshed on language change.
- `src/CardioSimulator.App/Controls/MonitorView.cs` — the floating `_sqiCard` was moved to
  `VerticalAlignment.Bottom` (bottom-right) to clear the top-right corner for the info button.
- `src/CardioSimulator.App/Localization/AppStrings.cs` — keys `rhythm_info_tooltip`,
  `rhythm_info_title`, `pathology_leads_label`, `pathology_markers_label` (EN/RU/ZH/ES).
- `MarkerSummary()` logic: `significantPoints.Select(p => p.Type).Distinct().OrderBy((int)t)
  .Select(t => t.Label().Replace("<sub>","").Replace("</sub>",""))` — Android's `sortedBy { ordinal }`
  is the direct analog (the `EcgPointType` enum declaration order is the complex order on both sides).
