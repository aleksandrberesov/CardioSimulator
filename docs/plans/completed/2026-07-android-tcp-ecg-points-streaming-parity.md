# TCP: live ECG `points` streaming + the text-ZIP upload contract

**Status:** active
**Owner:** unassigned
**Started:** 2026-07-16
**Direction:** **Windows → Android**
**Reference (Windows) source:** `E:\VLN_Project\CardioSimulatorWin\src\CardioSimulator.App\ViewModels\AppViewModel.cs`
(the `── TCP link ──` section), `src\CardioSimulator.Core\Data\PlainTextZipWriter.cs`,
`src\CardioSimulator.Core\Domain\PathologyParser.cs`, `tests\CardioSimulator.Core.Tests\PlainTextZipWriterTests.cs`
**Target (Android) source root:** `E:\VLN_Project\CardioSimulator\app\src\main\java\com\example\cardiosimulator\`

---

## ⚠️ Read this first — this plan reverses a decision in a sibling active plan

`active/2026-07-android-limited-edition-and-export-tcp-parity.md` §Goal item 2 says: *"in pack mode,
**stop the TCP dataset upload**"*, on the reasoning that the upload is an exfil hole and *"shipping
CSP2 without this is spending the effort and leaving the door open."*

**That is no longer the product direction.** On 2026-07-16 the Windows dataset upload was
deliberately **re-enabled**, in plaintext, by explicit owner decision — the stated rationale being
that the server requires it and *"secure will be supported on another level"*. Windows now uploads
the whole dataset as an **unencrypted text ZIP** on every connect.

So on Android: **keep `sendUploadArchive`. Do not remove it.** The exfil analysis in that plan is
still factually correct — the TCP target is student-editable and auto-applies after a 1 s debounce
(`SettingsDialog.kt:100-105`, `:248-349`), so anyone can point the app at their own socket and
receive the dataset. What changed is that this is now an **accepted** risk to be handled at the
transport/network layer, not by removing the feature.

**Action required before starting either plan:** reconcile the two. Someone must decide whether the
sibling plan's egress item is dropped, or narrowed to "harden the target field" (e.g. require an
explicit Apply, or lock the target in the Limited build). This plan does not resolve that; it only
refuses to silently contradict it.

---

## Goal

Bring the Android TCP link to the new Windows behaviour. Three parts, very different sizes:

1. **Live `points` streaming — NET-NEW, this is ~all the work.** While the monitor runs, stream the
   selected pathology's waveforms to the peer as `points` frames, paced to the sample rate, looping
   the record. Neither platform had this; Windows now does.
2. **`start` carries the real `sampleRate`** — currently hardcoded `null` on both. One-line fix.
3. **The upload — VERIFY ONLY, no code change expected.** See below: Android already does exactly
   what Windows was just changed *to* do.

## Current state (Android — verified 2026-07-16, with line numbers)

**The upload already matches the new Windows contract.** `AppViewModel.kt:505-532`
`sendUploadArchive()` zips `filesDir/pathologies` → `cacheDir/upload.zip` (`ZipCompressor.zipToCache`,
`ZipCompressor.kt:34-42`), sends `UploadMessage(filename = "Pathologies.zip", size = zipFile.length())`,
streams the bytes with `input.copyTo(out)`, and deletes the temp in a `finally` (`:530`). Fired from
the connect path at `:434`. That is *create text ZIP → send → delete*, i.e. the exact flow Windows was
just rewritten to produce. **Android gets this for free because its dataset is loose text files on
disk** — there is no pack, so there is nothing to decode.

Everything else needed already exists:

- `network/TcpMessage.kt:25-33` — `PointsMessage(id, lead, identy, offset, values)`, a faithful mirror
  of the Windows record. **Codec-only: nothing constructs it.** Same as Windows before this change.
- `network/TcpProtocol.kt:43-48` — already encodes `points` (drops `offset` when 0).
- `AppViewModel.kt:465-487` `sendStartCommand`, `:489-503` `sendStopCommand`.
- `AppViewModel.kt:205` `tcpSendMutex` — the existing send lock. **Load-bearing, see gotchas.**
- `AppViewModel.kt:99` — `val repository: PathologyRepository? = null`. The VM already holds the
  repository. This is what makes the design below possible.
- `PathologyRepository.kt:141` `leadWaveform(id, lead)`, `:47` `readPathology(id)`,
  `:104` `manifest()?.leadOrder ?: Lead.entries`.

## The one design decision that matters

**Do NOT copy the Windows `sendStartCommand` signature.** Windows threads the waveforms in from the
caller:

```csharp
SendStartCommand(pathology, name, waveforms, calibration)   // Windows
```

Windows gets away with that because it has **exactly one** call site (`MainScreen.xaml.cs:470`).
**Android has seven, across five screens:**

| File | Lines |
| --- | --- |
| `MainScreen.kt` | 275, 368 |
| `ExaminationScreen.kt` | 139 |
| `OSKEScreen.kt` | 102 |
| `TestConstructorScreen.kt` | 190, 318 |
| `TestingScreen.kt` | 73 |

Four of those pass only a `pathologyId` and have **no waveforms in scope at all**. Copying the
Windows signature would mean plumbing waveform state into Examination, OSKE, Testing and the Test
Constructor purely to satisfy the TCP layer — bad coupling, and impossible in the screens that never
load waveforms.

**Instead: resolve the waveforms inside the VM from the pathology id**, via the repository it already
has. `sendStartCommand(pathology, name)` keeps its current signature, **all seven call sites stay
untouched**, and streaming works from every screen for free.

This is a **deliberate, better-than-Windows divergence** — record it as such, not as a parity gap.
(Windows arguably should be refactored the same way; out of scope here.)

## Steps

1. **`sampleRate` on `start`** — `AppViewModel.kt:477`: replace `sampleRate = null` with the real rate
   from `EcgCalibration` (`data/EcgCalibration.kt:9`, `sampleRateHz: Float = 500f`), rounded to `Int`.
   Windows sends `(int)Math.Round(rate)`.

2. **Add the pump to `AppViewModel`.** Mirror the Windows shape
   (`AppViewModel.cs` `StartPointsStream` / `StopPointsStream` / `PointsLoopAsync`):
   - A nullable `Job` field (`pointsJob`) instead of Windows' `CancellationTokenSource`.
   - On `sendStartCommand`, **after** the `start` frame is written, resolve waveforms by id and launch
     the pump. Windows only pumps if the `start` write succeeded — keep that ordering.
   - Snapshot the waveforms into a plain `Map<Lead, FloatArray>` before looping; do not read live
     state inside the loop (a rhythm change mid-run must not retarget an in-flight stream).
   - Drop empty leads at snapshot time — an empty `FloatArray` divides by zero at the wrap.
   - Per lead, keep a cursor; emit `PointsChunkSamples` (50) samples per frame; `offset` is the
     frame's start index **within the record** and wraps with the loop:
     `cursor = (offset + count) % values.size`.
   - Pace with `delay(periodMs)` where `periodMs = 50 * 1000.0 / sampleRateHz` (100 ms at 500 Hz).
     Windows uses `PeriodicTimer`; `delay` drifts slightly, which is fine for a display feed.

3. **Cancel the pump everywhere Windows does** — `sendStopCommand` (before the socket check, so it
   stops even when already disconnected), `disconnectTcp` (`:456`), and the connect loop's failure
   path. Also cancel on VM clear so it cannot outlive the scope.

4. **Stale-socket guard.** Each tick, bail if `tcpSocket !== socket` captured at launch, or the state
   is no longer `Connected`. Without it a pump from a previous connection writes into a new one.

5. **Tests** — `app/src/test/java/.../` : snapshot isolation (mutate the source map after start, assert
   frames unchanged), cursor wrap (`offset` returns to 0 at the record end), short-record handling
   (record < 50 samples emits one full-record frame per tick), and that stop cancels the job.

## Gotchas

- **`tcpSendMutex` is load-bearing, not decorative.** The upload writes the ZIP as **raw, unframed
  bytes** after its header line. A `points` frame interleaved into that stream corrupts the upload.
  `sendUploadArchive` already holds the mutex across header **and** body (`:512`) — **the pump must
  take the same `tcpSendMutex` for every frame.** A pump that writes outside the lock will corrupt
  uploads intermittently, under load, in a way that looks like a server bug. This is the single
  highest-risk item here.
- **Do NOT port Windows' `SendAllAsync`.** It exists because .NET's `Socket.SendAsync` may accept a
  partial write. `java.io.OutputStream.write(ByteArray)` writes all bytes or throws, and `copyTo`
  loops — Android is already correct. Porting it would be cargo-culted noise.
- **Blocking I/O needs `Dispatchers.IO`.** The existing senders already `launch(Dispatchers.IO)`;
  the pump must too.
- **`Lead` encodes via `toString()`** in `TcpProtocol.kt` — matches Windows' `Lead.ToString()`. Don't
  hand-roll lead tokens.
- **Float formatting:** let `JSONArray.put(Double)` do it. Any manual `String.format` needs
  `Locale.US`, or a comma-decimal locale silently emits invalid JSON — the same trap as the tips port.

## When the CSP2 pack plan lands, this breaks — and silently

`sync/2026-07-android-content-pack-csp2-parity.md` replaces the loose text dataset with encrypted
packs. **The moment it does, step 3's "verify only" stops being true.** Packs store `.dat` waveforms
as `CSD1` delta-binary (`sync/2026-07-android-delta-binary-dataset-parity.md`), so a ZIP built from
pack entries is a **ZIP of binary** — still a perfectly valid ZIP that uploads cleanly and that the
server then misreads. No exception, no failed upload, just wrong content.

That is exactly the problem Windows solved with `PlainTextZipWriter.cs`: sniff each entry for the
`CSD1` magic, decode binary `.dat` back to text (`ParsePathology` → `SerializePathology`), pass
already-text entries through **verbatim** so formatting is preserved. Windows had to make
`PathologyParser.HasBinaryMagic` public for this rather than re-declare the magic bytes, since the
format note names the magic as the sole discriminator.

**Whichever plan lands second owns porting that.** Windows' `PlainTextZipWriterTests.cs` (5 tests)
is the spec to mirror; its key assertion is that what lands in the ZIP has **no** `CSD1` magic. Use
the manifest's `leadOrder` for the text re-serialization — the packer wrote the binary with it
(`ContentPacker/Program.cs:362-388`), so passing it back is the exact inverse and cannot drop a lead.

## Verification

Windows' upload half is **confirmed against the real server** (2026-07-16): header
`{"type":"upload","id":"…","filename":"Pathologies.zip","size":26343490}`, 26,343,490 bytes sent and
received, temp file gone afterwards. **The Windows `points` half has never been run end-to-end** — it
is unit-tested only at the codec layer. Do not treat the Windows implementation as a proven reference
for the streaming shape; it is a compiling, reviewed, but unexercised design. If the Android pump
disagrees with the server, suspect both sides.

To check Android: point the app's TCP target at the classroom server (or any line-reading listener),
connect, pick a rhythm, press Start. Expect `{"type":"start",…,"sampleRate":500}` followed by
`{"type":"points","lead":"I","identy":"<id>","offset":N,"values":[…]}` at ~10 frames/second/lead.
