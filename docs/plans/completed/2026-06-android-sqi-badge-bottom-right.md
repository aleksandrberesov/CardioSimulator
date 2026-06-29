# Move the SQI "Quality" badge to the bottom-right corner (Android parity)

**Status:** completed
**Owner:** AI Assistant
**Started:** 2026-06-29
**Finished:** 2026-06-29

**Direction:** **Windows → Android.** The Windows port (`CardioSimulatorWin`) moved the monitor's
SQI "Quality" badge from the **top-right** to the **bottom-right** corner on 2026-06-29; Android must
match. The Windows port is the reference for behaviour — match it, adapting idioms to Kotlin/Compose.

---

## Goal

The floating signal-quality (SQI) card overlaid on the ECG monitor — the little badge showing
`Quality: Excellent` + a coloured dot + `sSQI/kSQI/pSQI` details — currently sits in the **top-right**
corner. Move it to the **bottom-right** corner.

That's the entire change: a corner re-anchor of one overlay. No behaviour, computation, content, or
styling changes.

### Why

On Windows the top-right corner is shared with floating buttons (the "All rhythms" rhythm-info button,
EOS/tips overlays), so the quality card was being nudged around to avoid them. Anchoring it bottom-right
keeps it clear of those top-right controls and is the new canonical position. Android should follow so
the two ports stay visually consistent.

---

## The Windows change (reference)

In `src/CardioSimulator.App/Controls/MonitorView.cs` the `_sqiCard` `Border` was flipped:

```csharp
// before
VerticalAlignment = VerticalAlignment.Top,
HorizontalAlignment = HorizontalAlignment.Right,
Margin = new Thickness(12),

// after
VerticalAlignment = VerticalAlignment.Bottom,
HorizontalAlignment = HorizontalAlignment.Right,
Margin = new Thickness(12),
```

A now-obsolete helper, `SetSqiTopInset(double)` — which only existed to push the card *down* from the
top so it wouldn't collide with the top-right rhythm-info button — was deleted (it was dead code, never
called), and the comment in `MonitorViewerOverlay.cs` that referenced it was updated to note the card
"sits at the bottom-right, clear of this top-right button."

---

## Current state (Android)

The SQI card is a self-contained composable (`ui/components/SqiCard.kt`) that takes a `modifier` and does
**not** set its own corner — the caller positions it. There is exactly **one** call site, in the Teaching
screen's monitor `Box`:

`app/src/main/java/com/example/cardiosimulator/ui/screens/TeachingScreen.kt:478-484`

```kotlin
com.example.cardiosimulator.ui.components.SqiCard(
    signal = sqiSignal.values.map { it.toDouble() }.toDoubleArray(),
    samplingRate = mode.calibration.sampleRateHz.toDouble(),
    modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(top = 16.dp, end = 16.dp)
)
```

The same `Box` also hosts, at `Alignment.TopEnd`, the `EosOverlay` (`:487-492`) and `TipsOverlay`
(`:494-501`). Nothing is currently anchored to `BottomEnd`/`BottomStart`/`BottomCenter` in this `Box`
(verified: no other `Alignment.Bottom*` in the file's monitor area), so the bottom-right corner is free —
the move introduces no overlap.

---

## Change — re-anchor to bottom-right

In `TeachingScreen.kt:481-483`, change the alignment and padding edge:

```kotlin
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(bottom = 16.dp, end = 16.dp)
)
```

That is the only required edit. `SqiCard.kt` is untouched (it already accepts any `modifier`).

### Notes / no-op checks
- **No Android analog of `SetSqiTopInset`.** Android never had a top-inset nudge for this card, so there
  is nothing to delete (unlike Windows). Skip that part of the Windows change.
- **EOS / Tips overlays stay at `Alignment.TopEnd`** — do not move them. They were never co-located with
  the badge in a way that needs adjusting; the badge simply leaves the top-right.
- If a future Android refactor lifts the SQI card out of `TeachingScreen` into the shared monitor
  component (mirroring Windows, where `_sqiCard` lives inside `MonitorView` and so shows everywhere the
  monitor does), the bottom-right anchor should travel with it. Out of scope here.

---

## Verification

- Run the app, open **Teaching**, start a rhythm so the monitor draws and the SQI card appears.
- Confirm the quality badge now renders in the **bottom-right** corner with ~16 dp inset from the bottom
  and right edges, and that it no longer overlaps the top-right area where the EOS / tips overlays open.
- Toggle EOS/tips overlays — they still appear top-right and no longer share a corner with the badge.

## Files

- `app/src/main/java/com/example/cardiosimulator/ui/screens/TeachingScreen.kt` — the single SqiCard
  call site (~line 478-484): `Alignment.TopEnd` → `Alignment.BottomEnd`, `padding(top = …)` →
  `padding(bottom = …)`.
- `app/src/main/java/com/example/cardiosimulator/ui/components/SqiCard.kt` — **no change** (positions via
  caller-supplied `modifier`).
