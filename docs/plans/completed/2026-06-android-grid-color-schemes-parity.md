# Port the new monitor paper-grid colour schemes to Android (yellow / ECG-film pink / bedside monitor)

**Status:** active
**Owner:** AI Assistant
**Started:** 2026-06-28

---

## Goal

Bring the Android monitor's paper-grid scheme set in line with the Windows port (changed 2026-06-28).
Three user-visible changes:

1. **Rename the existing cream/khaki scheme** (currently labelled "Pink"/"Розовая", but actually a cream
   paper with a teal trace) to **"Yellow"**. Colours unchanged; only the name and enum member change.
2. **Add a genuinely pink scheme** — a rose grid with **black** graphics — labelled **"Пленка ЭКГ"**
   ("ECG film"). This is a brand-new scheme.
3. **Turn the "Blank" sheet into a "Bedside monitor"** — **black** background with a **green** trace
   (the classic scope look), no grid — and relabel it accordingly.

End state for the scheme picker (order matters):
**Yellow · Blue/Gray · ECG film · Bedside monitor**

### Why now

The Windows port shipped all three on 2026-06-28. The key architectural change there: the trace /
calibration-pulse / lead-label colour used to be a single global teal constant; it is now **per-scheme**
(teal for yellow & blue-gray, black for pink, green for bedside). Android has the **same** global-teal
assumption today (`EcgTraceTeal` is hard-wired into `PreviewPane`, `CalibrationPulse`, and `Lead`), so
this plan ports both the palette additions **and** that refactor.

## Current state (Android)

The Android colour model is split across three places, and the trace colour is global:

- **Enum** — `domain/MonitorModeModel.kt:8-12`:
  ```kotlin
  enum class GridScheme(@param:StringRes val labelRes: Int) {
      Pink(R.string.grid_scheme_pink),       // actually cream/khaki, see below
      BlueGray(R.string.grid_scheme_blue_gray),
      Blank(R.string.grid_scheme_blank)
  }
  ```
  Default is `gridScheme = GridScheme.Pink` (`:60`). **Note Android keeps `Blank` *in* the enum**, unlike
  Windows where blank is a separate `BlankSheet` bool — so on Android the bedside change is just another
  enum branch (simpler than Windows).
- **Grid background + line colours** — `ui/display/Modifers.kt` `Modifier.ekgGrid()`:
  - bg `when` (`:31-35`): `Pink → PaperBackground` (cream `#FCFCEC`), `BlueGray → #FFF0F4F7`,
    `Blank → Color.White`.
  - `Blank` early-returns with just a background, no grid (`:37-43`).
  - minor/major line `when` (`:45-52`): only `Pink → GridMinor/GridMajor` (khaki) and `BlueGray → …`.
- **Trace colour is global teal** — there is no per-scheme trace colour:
  - `ui/components/PreviewPane.kt:35` `color: Color = EcgTraceTeal` (the waveform; also used for the
    bedside/sweep path `:141` and `:101/:119`).
  - `ui/components/CalibrationPulse.kt:23` `color: Color = EcgTraceTeal`.
  - `ui/display/Lead.kt:100` and `:143` hard-code the lead title `color = EcgTraceTeal`.
  - `Lead` calls `PreviewPane(...)` (`:121`) and `CalibrationPulse()` (`:112`) **without** a colour, so
    everything is teal regardless of scheme.
- **Colour tokens** — `ui/theme/Color.kt:19-22`: `EcgTraceTeal #2C6E8E`, `PaperBackground #FCFCEC`,
  `GridMinor #E6E4CE`, `GridMajor #D2CEA6` (these are exactly the Windows "Yellow" palette).
- **Settings picker** — `ui/dialogs/SettingsDialog.kt:195-211` does `GridScheme.entries.forEach { … }`
  with `FilterChip(label = stringResource(scheme.labelRes))`. **It enumerates the enum**, so adding
  `Yellow` and reordering the enum updates the picker automatically — no list edit needed.
- **Persistence** — `ui/viewmodels/MonitorViewModel.kt:39-43` reads the pref via
  `GridScheme.valueOf(schemeName)` inside a `try/catch` (`:157` writes `scheme.name`). Stored **by name**,
  same as Windows.
- **Static lecture figures** — `data/EcgSvgRenderer.kt:42-47` already keys colours by string and
  **already renders pink + black**: `"Pink" → ("#FFF5F5","#FDE4E4","#F9BDBD")`, `TRACE_COLOR = "#111111"`,
  default `gridscheme="Pink"`. (So the live monitor and the SVG figures disagree today — exactly the
  inconsistency Windows had. After this change the live `Pink` scheme matches the SVG figures.)
- **Strings** — `grid_scheme_pink` / `grid_scheme_blue_gray` exist in all four locales;
  **`grid_scheme_blank` exists only in `values/` and `values-ru/`** — it is **missing from `values-es/`
  and `values-zh/`** (currently falling back to English "Blank").
- **Threading is already done for us:** every screen passes `gridScheme = scheme` into `Lead(...)`
  (`TeachingScreen.kt:449`, `TestingScreen.kt:106`, `ExaminationScreen.kt:172`, `OSKEScreen.kt:127`,
  `ConstructorScreen.kt:685`, `OskeConstructorScreen.kt:113`, `TestConstructorScreen.kt:73`), and
  `Monitor.kt` hands `scheme` to its `content` lambda (`:79`, `:246`). So **if the trace colour is derived
  inside `Lead` from `gridScheme`, every path — including compare-mode panes — gets it for free, with no
  screen edits.**

### Windows reference files (read these first)

| Concern | Windows file |
|---|---|
| Enum (`Yellow,BlueGray,Pink`), label keys, default `Yellow` | `src/CardioSimulator.Core/Domain/MonitorMode.cs` |
| Palette: per-scheme colours **+ Trace colour**, new pink, `Bedside`, `Palette(scheme,blankSheet)` overload | `src/CardioSimulator.App/Rendering/EcgColors.cs` |
| Threading the per-scheme trace colour through the renderer | `src/CardioSimulator.App/Rendering/EcgRenderer.cs` |
| Scheme chips (Yellow/Blue-Gray/ECG film/Bedside) | `src/CardioSimulator.App/Screens/SettingsContent.cs` |
| Strings (4 locales): `grid_scheme_yellow`, retuned `grid_scheme_pink`, renamed `grid_scheme_bedside` | `src/CardioSimulator.App/Localization/AppStrings.cs` |
| Trace colour passed from the controls | `src/CardioSimulator.App/Controls/PreviewPaneControl.cs`, `EditableLeadControl.cs` |

### Canonical colours (keep Android == Windows, ARGB)

| Scheme | Background | Minor line | Major line | Trace / pulse / label |
|---|---|---|---|---|
| **Yellow** (was "Pink") | `#FCFCEC` | `#E6E4CE` | `#D2CEA6` | teal `#2C6E8E` |
| **Blue/Gray** | `#F0F4F7` | `#DDE4E9` | `#BCC6CF` | teal `#2C6E8E` |
| **ECG film** (new pink) | `#FFF5F5` | `#FDE4E4` | `#F9BDBD` | black `#111111` |
| **Bedside monitor** (was Blank) | `#000000` | — (no grid) | — | green `#00E04A` |

## Non-goals

- Don't change scroll/sweep behaviour, calibration, zoom/pan, or the significant-point overlay colours
  (those stay their own red/blue/green, exactly like Windows).
- Don't touch the Electrodes / 3D / EOS / Tips windows or the filter/artifact pipeline.
- Don't rework `EcgSvgRenderer` beyond the one optional alias in Phase 5 — its pink figures already match.

## Plan

### Phase 1 — Single source of truth for grid colours (mirror Windows `EcgColors`)

Add the new tokens and a small palette helper so `ekgGrid()` and `Lead` read the **same** definition
(this is the Kotlin analog of `GridPalette` + `EcgColors.Palette`).

- In `ui/theme/Color.kt` add:
  ```kotlin
  // Pink "ECG film" paper (rose grid, black graphics)
  val PinkPaperBackground = Color(0xFFFFF5F5)
  val PinkGridMinor       = Color(0xFFFDE4E4)
  val PinkGridMajor       = Color(0xFFF9BDBD)
  val EcgTraceBlack       = Color(0xFF111111)
  // Bedside monitor (green on black)
  val BedsideBackground   = Color(0xFF000000)
  val BedsideTraceGreen   = Color(0xFF00E04A)
  ```
  (Yellow reuses the existing `PaperBackground` / `GridMinor` / `GridMajor` / `EcgTraceTeal`.)
- Add a palette helper (e.g. in `Color.kt` or a new `GridPalette.kt` in `ui/theme`):
  ```kotlin
  data class GridPalette(val background: Color, val minor: Color, val major: Color, val trace: Color)

  fun GridScheme.palette(): GridPalette = when (this) {
      GridScheme.Yellow   -> GridPalette(PaperBackground, GridMinor, GridMajor, EcgTraceTeal)
      GridScheme.BlueGray -> GridPalette(Color(0xFFF0F4F7), Color(0xFFDDE4E9), Color(0xFFBCC6CF), EcgTraceTeal)
      GridScheme.Pink     -> GridPalette(PinkPaperBackground, PinkGridMinor, PinkGridMajor, EcgTraceBlack)
      GridScheme.Blank    -> GridPalette(BedsideBackground, BedsideBackground, BedsideBackground, BedsideTraceGreen)
  }
  ```

### Phase 2 — Domain: rename `Pink`→`Yellow`, add new `Pink`, default `Yellow`

- `domain/MonitorModeModel.kt`:
  ```kotlin
  enum class GridScheme(@param:StringRes val labelRes: Int) {
      Yellow(R.string.grid_scheme_yellow),
      BlueGray(R.string.grid_scheme_blue_gray),
      Pink(R.string.grid_scheme_pink),
      Blank(R.string.grid_scheme_bedside)
  }
  ```
  and change the default `gridScheme: GridScheme = GridScheme.Yellow` (`:60`).
- Keep the enum member **`Blank`** (only its label + colours change) so existing persisted `"Blank"` prefs
  still resolve. `Yellow` is declared first so it is the natural default.
- The compiler will flag the three `Monitor.kt` previews that call `setGridScheme(GridScheme.Pink)`
  (`:352`, `:436`) — they still compile (Pink exists) but now show the new pink; optionally switch them to
  `GridScheme.Yellow` to keep previews looking like the default.

### Phase 3 — Grid rendering: `ekgGrid()` reads the palette; bedside goes black

In `ui/display/Modifers.kt`, replace the inline `when` blocks with the palette and make bedside black:

- Default the modifier's `scheme` param to `GridScheme.Yellow` (`:27`).
- `val pal = scheme.palette()` and use `pal.background` for the background; for `GridScheme.Blank`
  keep the early-return (no grid) but with `pal.background` (now **black**) instead of `Color.White`
  (`:34`, `:37-43`).
- Use `pal.minor` / `pal.major` for the line `when`s (`:45-52`) — this also removes the current
  non-exhaustive `when` (it omits `Blank` today; the palette covers all four).

### Phase 4 — Per-scheme trace/pulse/label colour in `Lead.kt`

This is the core refactor (Windows: trace colour threaded through `EcgRenderer`). Android only needs to
touch `Lead`, since it already receives `gridScheme`:

- In `ui/display/Lead.kt`, compute `val traceColor = gridScheme.palette().trace` and pass it to:
  - the calibration pulse: `CalibrationPulse(modifier = …, color = traceColor)` (`:112`),
  - the trace: `PreviewPane(… , color = traceColor)` (`:121-127`),
  - the lead title `Text(color = traceColor)` in **both** the normal (`:100`) and compare (`:143`) blocks.
- `PreviewPane` and `CalibrationPulse` already expose a `color` param, so no signature changes — only the
  call sites in `Lead` and the default in `PreviewPane`/`CalibrationPulse` can stay `EcgTraceTeal`.
- Bedside sweep: `PreviewPane`'s blank-mode carrier highlight (`:126-130`) is `Color.White.copy(0.4f)` —
  white-on-black reads fine, leave it. The green trace now comes from `color = traceColor`.
- Compare-mode title chip (`:148`) is a translucent-white pill behind the title — legible on every paper;
  leave as is.

### Phase 5 — Strings (all four locales) + optional SVG alias

- **Rename** `grid_scheme_pink` value (it now labels the real pink scheme). Per the customer, use the
  literal Russian string **`Пленка ЭКГ` in *all four* locales** (not translated), matching Windows:
  | locale | value |
  |---|---|
  | `values/` | `Пленка ЭКГ` |
  | `values-ru/` | `Пленка ЭКГ` |
  | `values-zh/` | `Пленка ЭКГ` |
  | `values-es/` | `Пленка ЭКГ` |
- **Add** `grid_scheme_yellow`: `Yellow` / `Жёлтая` / `黄色` / `Amarillo`.
- **Rename** `grid_scheme_blank` → **`grid_scheme_bedside`** (update the enum's `labelRes` ref in Phase 2)
  and provide it in **all four** locales (es & zh are currently missing the blank string):
  `Bedside monitor` / `Прикроватный монитор` / `床旁监护仪` / `Monitor de cabecera`.
  Remove the now-unused `grid_scheme_blank` from `values/` and `values-ru/`.
- *(Optional, keeps lecture `<ecg>` figures consistent)* In `data/EcgSvgRenderer.kt:42-46` add
  `"Yellow" to GridColors("#FCFCEC", "#E6E4CE", "#D2CEA6")` so an explicit `gridscheme="Yellow"` resolves
  to cream. Leave `"Pink"` (real pink + `TRACE_COLOR #111111`) and the `"Pink"` default as-is.

## Risks & open questions

- **Persisted-pref migration (same nuance as Windows):** prefs are stored by enum *name*. A user who had
  explicitly picked the old `"Pink"` (cream) will, after this change, resolve to the **new** Pink (black
  trace) — the two names are indistinguishable, so there is no clean migration. Everyone on the default
  (no stored pref) lands on `Yellow` = the same cream look as before. `"Blank"` prefs keep working only if
  you **keep the `Blank` enum member** (we do). *Acceptable; no code workaround.*
- **`when` exhaustiveness:** moving to `scheme.palette()` removes the current non-exhaustive `when` in
  `ekgGrid()`. Make sure `palette()` itself is exhaustive over all four members (it is above).
- **Green shade:** `#00E04A` matches Windows. If it looks too neon on AMOLED, tune in `Color.kt` only.
- **Don't re-introduce a global trace colour:** after Phase 4, `EcgTraceTeal` should remain only as the
  yellow/blue-gray palette entry (and as harmless composable defaults), not as the de-facto trace colour.

## Verification

- Settings → grid scheme shows **Yellow · Blue/Gray · ECG film · Bedside monitor** in that order, each
  localised (check ru/zh/es, especially the new bedside string in es/zh).
- **Yellow** looks identical to the old default (cream paper, teal trace).
- **ECG film**: rose grid with a **black** trace, pulse, and lead labels.
- **Bedside monitor**: **black** background, no grid, **green** trace/pulse/labels; the sweep carrier is
  still visible.
- Switch schemes live on the Teaching monitor and in **compare mode** (per-pane) — colours follow in both.
- The editor footer (`PreviewPane`) and any `RenderEditableLead`-equivalent surfaces follow the scheme.
- App builds; no missing-string fallbacks (every `grid_scheme_*` resolves in all four locales).

## PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Theme: pink/bedside tokens + `GridScheme.palette()` | 1 | Pure additions; no behaviour yet |
| 2 | Domain: enum `Yellow,BlueGray,Pink,Blank` + default Yellow | 2 | Fix preview call sites |
| 3 | `ekgGrid()` reads palette; bedside background black | 3 | Grid colours per scheme |
| 4 | Per-scheme trace/pulse/label colour in `Lead.kt` | 4 | The teal→per-scheme refactor |
| 5 | Strings (4 locales) + optional SVG `Yellow` alias | 5 | Rename pink, add yellow, rename blank→bedside |

---

## Cross-reference

Windows commit/session: monitor grid-scheme rework on 2026-06-28 (CardioSimulatorWin) — added pink + bedside,
renamed pink→yellow, pink label → "Пленка ЭКГ". This plan is the Android counterpart.
