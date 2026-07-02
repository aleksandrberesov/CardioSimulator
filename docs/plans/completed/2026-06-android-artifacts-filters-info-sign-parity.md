# Add an info "(!)" sign explaining the Artifacts & Filters menus (Android parity)

**Status:** DONE (2026-06-29)
**Owner:** AI Assistant
**Started:** 2026-06-29

**Direction:** **Windows → Android.** The Windows port (`CardioSimulatorWin`) shipped this on
2026-06-29; Android must catch up. The Windows port is the reference for behaviour/visuals — match it,
adapting idioms to Kotlin/Compose. Adapt one thing on the way over: the **filter cutoff frequencies in
the explanation text differ between platforms** — Android's filters are 25 Hz / 3 Hz / 3–25 Hz, not
Windows' 40 Hz / 0.5 Hz (see Phase "Strings"). Use Android's own numbers.

---

## Goal

Add a small **circled-info "(!)" sign** to the top of both the **Artifacts** dropdown and the
**Filters** dropdown on the Teaching-mode monitor's bottom control panel. The sign carries a short
explanation of how that menu works (what artifacts/filters do, how selection behaves). On Windows it's
a hover tooltip; on Android (touch) it should reveal the same text on tap (and on hover for
mouse/stylus). Purely additive UX — no change to artifact/filter behaviour.

### Why now

The Windows port added these two info signs on 2026-06-29 at the customer's request ("in menus
Artifacts and Filters add information sign (!) which would comment how it works"). It's a tiny,
self-contained help affordance; bringing it to Android keeps the two apps' monitor panels in lockstep.

## Current state (Android)

- **The two dropdowns** — `ui/panels/MonitorControlPanel.kt`, both inside the "Middle-left section"
  `Row` (`:189`):
  - **Artifacts** — `Box(weight 1.5f)` at `:201-267`. A `Tab(showChevron=true)` opens a `DropdownMenu`
    (`:217-266`) that iterates `EcgArtifact.entries` into `DropdownMenuItem`s (multi-select; a
    `Icons.Default.Check` trailing icon marks active ones; `None` clears all and the menu stays open).
  - **Filters** — `Box(weight 1.5f)` at `:269-300`. A `Tab(showChevron=true)` opens a `DropdownMenu`
    (`:277-299`) iterating `EcgFilterType.entries` → `DropdownMenuItem`s calling
    `viewModel.setFilterType(filterType)` then closing the menu.
  - Both `DropdownMenu`s accept arbitrary leading composables (the SQI-badge plan relies on the same),
    so a header `Row` + `HorizontalDivider()` before the `.entries.forEach` drops straight in.
- **Icons already imported** — the file uses `Icons.Default.Check` (`material-icons-core`).
  `Icons.Default.Info` is in the same core set, so **no new dependency** is needed. (`Icons.Outlined.Info`
  would pull in `material-icons-extended` — avoid it.)
- **Strings** — `res/values/strings.xml:85-96` holds `monitor_artifacts`, the five
  `monitor_artifact_*`, `monitor_filters`, and the four `monitor_filter_*`. Locales: `values` (en),
  `values-ru`, `values-es`, `values-zh` (no `values-hi` yet — Hindi is a separate active plan,
  `2026-06-android-hindi-language-parity.md`). **No** `monitor_*_info` keys exist.
- **Filter labels differ from Windows** — Android: `monitor_filter_lowpass` = "Low-pass (25Hz)",
  `monitor_filter_highpass` = "High-pass (3Hz)", `monitor_filter_bandpass` = "Band-pass (3-25Hz)".
  Windows uses 40 Hz / 0.5 Hz / 0.5–40 Hz. The info text must quote **Android's** numbers.

### Windows reference files (read these first)

| Concern | Windows file / member |
|---|---|
| The two helpers + both call sites | `src/CardioSimulator.App/Controls/MonitorControlPanel.xaml.cs` |
| Strings (EN/RU/ZH/ES/HI) | `src/CardioSimulator.App/Localization/AppStrings.cs` |

**Windows shape to mirror** (`MonitorControlPanel.xaml.cs`):
- `BuildMenuHeader(title, explanation)` → a horizontal row: a **bold title** `TextBlock` + an
  `BuildInfoSign(explanation)` icon (spacing 6, bottom margin 6).
- `BuildInfoSign(explanation)` → a `FontIcon` with the **Info glyph (``, a circled "i")**,
  14px, secondary-text colour, with a `ToolTip` whose content is a wrapped `TextBlock`
  (`TextWrapping=Wrap`, `MaxWidth=280`).
- **Artifacts flyout** now starts with `BuildMenuHeader(AppStrings.MonitorArtifacts,
  AppStrings.MonitorArtifactsInfo)` (it replaced the old plain bold title).
- **Filters flyout** now starts with `BuildMenuHeader("Filters", AppStrings.MonitorFiltersInfo)`
  inserted **above** the SQI badge (`BuildSqiBadge()`), then the divider, then the filter rows.
- New string keys: `monitor_artifacts_info`, `monitor_filters_info` (accessors
  `AppStrings.MonitorArtifactsInfo` / `MonitorFiltersInfo`), added across EN/RU/ZH/ES/HI.

## Non-goals

- Don't change artifact/filter selection behaviour, the enums, the trace processing, or the Tab visuals.
- Don't add `material-icons-extended` — use core `Icons.Default.Info`.
- Don't add `values-hi` here — that's the Hindi-parity plan's job (note the key for it to pick up).
- Don't reference an SQI "quality badge above" in the Android **filters** text yet: that badge is a
  separate not-yet-landed plan (`2026-06-android-sqi-badge-in-filter-dropdown.md`). Keep the filters
  explanation about the filters themselves so it reads correctly today.

## Plan

### Phase 1 — Shared `MenuInfoHeader` composable

Add a small reusable composable in `ui/panels/MonitorControlPanel.kt` (or `ui/components/` if you
prefer it shared), mirroring Windows' `BuildMenuHeader` + `BuildInfoSign`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuInfoHeader(title: String, explanation: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        val tooltipState = rememberTooltipState(isPersistent = false)
        val scope = rememberCoroutineScope()
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(explanation) } },   // wraps; cap width if it looks wide
            state = tooltipState
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = explanation,   // also surfaces the text to TalkBack
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { scope.launch { tooltipState.show() } }
            )
        }
    }
}
```

Notes:
- `TooltipBox` is the closest analogue to Windows' `ToolTip`: it shows on **hover** (mouse/stylus) and
  **long-press** for free, and we trigger it on **tap** via `tooltipState.show()` so a plain touch works
  too. `contentDescription` doubles as the a11y text.
- If `TooltipBox`/`rememberTooltipState` feels heavy or you hit an API-level/version snag, the
  acceptable fallback is **inline reveal**: an `IconButton` toggling a `var expanded` that shows the
  `explanation` `Text` (wrapped, `onSurfaceVariant`) on the next line inside the menu. Same information,
  one extra row of height. Either is fine; prefer the tooltip for visual parity.
- Imports to add as needed: `androidx.compose.material3.{TooltipBox, PlainTooltip, TooltipDefaults,
  rememberTooltipState, ExperimentalMaterial3Api}`, `androidx.compose.material.icons.filled.Info`,
  `androidx.compose.foundation.clickable`, `androidx.compose.ui.text.font.FontWeight`,
  `rememberCoroutineScope`, `kotlinx.coroutines.launch`.

### Phase 2 — Artifacts dropdown header

In the Artifacts `DropdownMenu` (`:217`), **before** the `EcgArtifact.entries.forEach` (`:221`):

```kotlin
MenuInfoHeader(
    title = stringResource(R.string.monitor_artifacts),
    explanation = stringResource(R.string.monitor_artifacts_info),
)
HorizontalDivider()
```

(Matches Windows, where the Artifacts flyout's bold title was replaced by the header-with-info-sign.)

### Phase 3 — Filters dropdown header

In the Filters `DropdownMenu` (`:277`), **before** the `EcgFilterType.entries.forEach` (`:281`):

```kotlin
MenuInfoHeader(
    title = stringResource(R.string.monitor_filters),
    explanation = stringResource(R.string.monitor_filters_info),
)
HorizontalDivider()
```

> Coordination with `2026-06-android-sqi-badge-in-filter-dropdown.md`: when that plan lands, the badge
> goes **below** this header (header → SQI badge → divider → filter rows), matching Windows' order
> (header above the badge). Whichever ships first, keep the info header at the very top.

### Phase 4 — Strings

Add to `res/values/strings.xml` (en) and the `-ru` / `-es` / `-zh` variants. Mind **Android string
escaping**: apostrophes → `\'`, literal double-quotes → `\"`. Values below are pre-escaped.

`values/strings.xml` (en):
```xml
<string name="monitor_artifacts_info">Artifacts overlay realistic noise on the trace so you can practise recognising poor signal quality. Tap one or more to add them; tap again to remove. They don\'t change the underlying rhythm — only how cleanly it is displayed. \"No artifacts\" clears them all.</string>
<string name="monitor_filters_info">Filters clean up the displayed trace. Low-pass (25 Hz) removes high-frequency muscle noise, high-pass (3 Hz) removes baseline wander, and band-pass (3–25 Hz) does both. Only one filter applies at a time.</string>
```

`values-ru/strings.xml`:
```xml
<string name="monitor_artifacts_info">Артефакты накладывают на кривую реалистичные помехи, чтобы тренировать распознавание плохого качества сигнала. Нажмите один или несколько, чтобы добавить, нажмите ещё раз, чтобы убрать. Они не меняют сам ритм — только чистоту отображения. «Без артефактов» убирает все сразу.</string>
<string name="monitor_filters_info">Фильтры очищают отображаемую кривую. ФНЧ (25 Гц) убирает высокочастотные мышечные помехи, ФВЧ (3 Гц) — дрейф изолинии, полосовой (3–25 Гц) — и то, и другое. Одновременно действует только один фильтр.</string>
```

`values-es/strings.xml`:
```xml
<string name="monitor_artifacts_info">Los artefactos superponen ruido realista sobre el trazado para practicar el reconocimiento de una calidad de señal deficiente. Toque uno o varios para añadirlos; toque de nuevo para quitarlos. No cambian el ritmo subyacente, solo la nitidez con que se muestra. «Sin artefactos» los borra todos.</string>
<string name="monitor_filters_info">Los filtros limpian el trazado mostrado. El paso bajo (25 Hz) elimina el ruido muscular de alta frecuencia, el paso alto (3 Hz) elimina la deriva de la línea base y el paso banda (3–25 Hz) hace ambas cosas. Solo se aplica un filtro a la vez.</string>
```

`values-zh/strings.xml`:
```xml
<string name="monitor_artifacts_info">伪影会在波形上叠加逼真的噪声，便于练习识别信号质量不佳的情况。点击一个或多个以添加，再次点击可移除。它们不会改变基础节律，只影响显示的清晰度。“无伪影”可一次清除全部。</string>
<string name="monitor_filters_info">滤波器用于净化显示的波形。低通 (25 Hz) 去除高频肌电噪声，高通 (3 Hz) 去除基线漂移，带通 (3–25 Hz) 两者兼有。同一时间只应用一个滤波器。</string>
```

> When the Hindi plan adds `values-hi/strings.xml`, copy the EN values for these two keys (or translate)
> there too. The Windows `AppStrings.cs` already has Hindi values for `monitor_artifacts_info` /
> `monitor_filters_info` to crib from — note that Windows' Hindi/EN filter text says 40 Hz/0.5 Hz, so
> swap to 25 Hz/3 Hz/3–25 Hz for Android.

## Risks & open questions

- **`TooltipBox` API:** it's Material3 and `@ExperimentalMaterial3Api`. If the project's Compose-BOM is
  old enough that `rememberTooltipState`/`TooltipBox` aren't present, use the inline-reveal fallback in
  Phase 1 — don't bump the BOM just for this.
- **Touch vs hover:** Windows is hover-only; Android phones have no hover, so the **tap-to-show** trigger
  (`tooltipState.show()`) is the important part. Verify a single tap on the (!) surfaces the text.
- **Tap target / menu dismissal:** the info icon lives in a header `Row`, not a `DropdownMenuItem`, so
  tapping it won't auto-dismiss the menu (good — the user reads the tip with the menu open). Confirm the
  `clickable` doesn't propagate to close the menu.
- **Tooltip width:** Windows caps the wrapped text at 280px. `PlainTooltip` wraps automatically; if it
  renders too wide on tablets, wrap the `Text` in a `Modifier.widthIn(max = 280.dp)`.
- **Filter-frequency text:** double-check the cutoffs against the actual `monitor_filter_*` labels at
  build time — if those labels change, update the info text to match.

## Verification

- `./gradlew :app:assembleDebug` passes.
- Teaching → monitor → open **Artifacts**: a bold "Artifacts" title with an **(i)** icon sits at the top,
  above a divider, above the artifact list. Tapping the (i) shows the explanation; the menu stays open.
- Open **Filters**: same header with the (i); tapping shows the filter explanation citing **25 Hz /
  3 Hz / 3–25 Hz**.
- RU/ES/ZH locales show the translated explanations; the (i) icon and layout are unchanged.
- TalkBack reads the explanation from the icon's `contentDescription`.

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | `MenuInfoHeader` composable (title + info tooltip) | 1 | Mirrors Windows `BuildMenuHeader`/`BuildInfoSign` |
| 2 | Artifacts & Filters dropdown headers | 2–3 | Two 3-line insertions before each `forEach` |
| 3 | Strings `monitor_artifacts_info` / `monitor_filters_info` (en/ru/es/zh) | 4 | Android-escaped; 25/3 Hz cutoffs |

*(All three are small and can ship as one PR.)*

---

## Cross-reference

Windows session 2026-06-29 (CardioSimulatorWin): added `BuildMenuHeader`/`BuildInfoSign` to
`MonitorControlPanel.xaml.cs`; the Artifacts flyout title became
`BuildMenuHeader(MonitorArtifacts, MonitorArtifactsInfo)` and the Filters flyout gained
`BuildMenuHeader("Filters", MonitorFiltersInfo)` above the SQI badge; both use a `FontIcon` Info glyph
(``) with a wrapped `ToolTip` (`MaxWidth=280`). Added `monitor_artifacts_info` /
`monitor_filters_info` to `AppStrings.cs` (EN/RU/ZH/ES/HI). Build: 0 warnings / 0 errors.

## Outcome

*(Fill in when status moves to completed/dropped.)*
