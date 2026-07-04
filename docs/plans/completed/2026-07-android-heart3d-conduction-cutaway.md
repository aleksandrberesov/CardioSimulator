# Sync Heart3D Conduction System + Cutaway to Android (Windows → Android)

**Status:** active
**Owner:** a.beresov
**Started:** 2026-07-03
**Related (Windows reference):** `src/CardioSimulator.App/Controls/Heart3DDialog.cs`, `src/CardioSimulator.App/Controls/ConductionSystem.cs` (in the `CardioSimulatorWin` repo). Customer asks from Nikolai: show the conduction-system animation, and a "half heart" cutaway to show it inside.

---

## 1. Goal

Bring two capabilities added to the Windows Heart3D viewer to Android:

1. **Conduction-system animation** — a glowing pathway (SA node → atria → AV node → His bundle → bundle branches → Purkinje → ventricular apex) with a bright pulse travelling it, paced to the heart rate, with a phase caption (P wave / PR segment / QRS / Diastole). The AV→His timing gap encodes the AV nodal delay (the PR pause). Pathway is authorable (tap to place nodes) and persisted.
2. **"Half heart" cutaway** — a runtime clip plane that slices the heart to reveal the interior, with a cut-position sweep, so the conduction animation can be shown *inside* the sliced heart. Plus an **X-ray** translucency toggle that fades the myocardium so the pathway shows through.

---

## 2. Current state (Android)

- **Renderer is Google `<model-viewer>` in a WebView**, not a native/GL scene: `app/src/main/java/com/example/cardiosimulator/ui/components/Heart3DViewer.kt`. It loads `model-viewer.min.js` **from a CDN** (`ajax.googleapis.com/.../3.5.0`) and the model `assets/heart3d/heart.glb` via `WebViewAssetLoader` (`appassets.androidplatform.net`). Only `camera-controls` + `auto-rotate` are enabled — **no hotspots, no custom geometry, no clipping.** A `Heart3DBridge` JS interface already exists (`Android.onLoaded()/onError()`).
- **Dialog:** `app/src/main/java/com/example/cardiosimulator/ui/dialogs/Heart3DDialog.kt` — a small Compose `Dialog`, 3-column layout, the viewer is a square in the right column. Far simpler than the Windows overlay (Windows also has hotspots + a Settings model picker that were never ported — out of scope here).
- **Open path:** `MonitorControlPanel.kt:383` toggles `MonitorMode.show3D`; the dialog is shown from that state.
- **Heart rate:** there is **no central "current-rhythm HR"** on Android. HR appears only ad-hoc (caliper Δt → bpm at `ui/display/Monitor.kt:280`; a bpm slider in `SynthesizerDialog.kt`). No `EcgMeasurements.HeartRateBpm` analogue is wired to the 3D open.
- **Locales:** `res/values` (en), `values-ru`, `values-zh`, `values-es`, `values-hi`.

## 3. Key architectural decision — replace `<model-viewer>` with a custom Three.js scene

`<model-viewer>` cannot draw arbitrary geometry (our path + pulse) or apply clip planes. Its engine **is** Three.js, so the intent-correct port is to **swap the `<model-viewer>` HTML for a hand-written Three.js scene** in the same WebView. Three.js gives us direct analogues of the Windows HelixToolkit work:

| Windows (HelixToolkit) | Android (Three.js) |
|---|---|
| `SceneNodeGroupModel3D` + Assimp import | `GLTFLoader` |
| `CameraController` orbit/zoom/pan | `OrbitControls` |
| `CrossSectionMeshNode` + `Plane1` | `renderer.localClippingEnabled = true` + `material.clippingPlanes = [THREE.Plane]` |
| Pulse geometry rebuilt per frame | move `pulse.position` per frame (no rebuild needed — the Windows rebuild was only to dodge an Element3D.Transform API quirk) |
| Phong/PBR alpha for X-ray | `material.transparent = true; material.opacity` |

**Bundle Three.js locally** (`three.min.js` + `GLTFLoader.js` + `OrbitControls.js` under `app/src/main/assets/heart3d/vendor/`) instead of a CDN — the app is used in classrooms that may be offline, and the current CDN model-viewer is already a reliability liability. Keep the `Heart3DBridge` pattern for Kotlin→JS control and JS→Kotlin persistence.

## 4. Non-goals

- Windows hotspots (a separate divergence — not part of this feature).
- A user-selectable model (Android ships the bundled `heart.glb`; no override dir).
- Tight per-sample ECG-cursor sync — the pulse loops at the heart rate, same as Windows.
- Filled cut-cap fidelity is best-effort (see Risks).

## 5. Plan

Each phase leaves the app shippable.

### Phase 1 — Custom Three.js viewer (parity baseline)
- Add `assets/heart3d/vendor/{three.min.js, GLTFLoader.js, OrbitControls.js}` (pin a version, e.g. r160).
- Rewrite the HTML in `Heart3DViewer.kt`: replace `<model-viewer>` with a full-window `<canvas>` and a Three.js scene — `WebGLRenderer({alpha:true})`, `PerspectiveCamera`, `OrbitControls`, `AmbientLight` + a camera-aimed `DirectionalLight`, `GLTFLoader` loading `https://appassets.androidplatform.net/assets/heart3d/heart.glb`, then frame the camera to the model bounding box. Keep `Android.onLoaded()/onError()`.
- Verify orbit/zoom + transparent background match today's behaviour.

### Phase 2 — Conduction model, timing template, persistence
- Kotlin data (`ui/components/ConductionSystem.kt` or a `domain/` file):
  ```kotlin
  data class ConductionNode(
      val key: String, val labelEn: String, val labelRu: String,
      val anchor: FloatArray, val arrivalMs: Float)
  ```
  Port the 7-stage **template verbatim** from Windows `ConductionSystem.cs` (`Template`): keys `sa, atria, av, his, bundles, purkinje, apex` with arrival ms `0, 60, 100, 170, 190, 210, 245` and the EN/RU labels + phase strings. The 100→170 gap (AV node → His) is the AV nodal delay — keep it.
- **Sidecar persistence in `filesDir`** (assets are read-only): `filesDir/heart.conduction.json`. A `ConductionStore` with `load(): List<ConductionNode>?` and `save(json)`. Default path is generated **in JS from the model's bounding box** (vertical base→apex layout, mirroring Windows `CreateDefault`) when no sidecar exists.
- Keep the conduction geometry + animation **in JS** (that is where the bbox and meshes live); Kotlin only persists JSON and pushes control state.

### Phase 3 — Conduction rendering + animation (JS)
- JS pathway module (inline in the HTML or `assets/heart3d/conduction.js`):
  - Build the path: gold `TubeGeometry`/thick line through node anchors + a small sphere per node; a bright **emissive** pulse sphere.
  - `requestAnimationFrame` loop: `cycleMs = 60000/bpm`; `t = elapsed % cycleMs`; interpolate the pulse position between the two nodes bracketing `t` by arrival time; past the last arrival → hide pulse (diastole). Set `pulse.position` (do **not** rebuild geometry).
  - Phase caption = an absolutely-positioned HTML `<div>` overlay, text from the stage the pulse is entering; hidden when not playing.
- Bridge (Kotlin→JS via `evaluateJavascript`): `setConductionPlaying(bool)`, `setBpm(int)`, `setPathway(jsonArray)`.

### Phase 4 — Cutaway + X-ray (JS)
- **Cutaway:** `renderer.localClippingEnabled = true`. Maintain a `THREE.Plane(normal, constant)`; on `setCutaway(true)` assign it to every model material's `clippingPlanes`, on false clear them. `setCutPosition(s in 0..1)` sweeps the plane along model **Z** (mirror Windows: `normal=(0,0,1)`, plane through `min.z + s*(max.z-min.z)`).
  - ⚠️ Three.js does **not fill the cut cap** — a hollow shell shows its interior back-faces. Windows fills the cap with `CrossSectionColor` (muted red). Mitigations, cheapest first: `material.side = THREE.DoubleSide` (interior visible, no true cap) → accept; or a **stencil-based cap** (proper solid cap, more work — defer to polish). Flag this as a known fidelity gap vs Windows.
- **X-ray:** `setXray(bool)` sets each model material `transparent=true; opacity=0.28` (cache originals to restore). Mirrors Windows Phong `DiffuseColor`/PBR `AlbedoColor` alpha.

### Phase 5 — Compose UI + bridge wiring
- Expose a controller from `Heart3DViewer` (e.g. remember the `WebView` and surface `fun eval(js: String)` callbacks, or hoist a `Heart3DController`).
- In `Heart3DDialog.kt` add two control groups (match Windows left-column intent):
  - **Conduction:** Play/Pause button, a bpm `Slider` (40–180) with a label, **X-ray** toggle, **Edit pathway** toggle.
  - **Cutaway:** **Cut in half** toggle, and a **Cut position** `Slider` (shown only when cut is on).
  - Each wires to the JS bridge calls above.
- **Edit pathway authoring:** in JS, a tap raycasts against the model → surface point → append the next template node (in anatomical order) → call `Android.saveConduction(json)` (persist to `filesDir`); a hint overlay names the next node (`1/7 … 7/7`). Kotlin exposes `@JavascriptInterface fun saveConduction(json: String)`.

### Phase 6 — Heart-rate feed
- Seed the bpm from the loaded rhythm. Android has no central HR: add a small helper that computes `60/RR` from the current rhythm's significant points (or R-peak spacing) at the 3D-open site, else default **75**. Lower priority — the manual slider already covers the demo. Mark as open (see §6).

### Phase 7 — Localization + mirror Windows caveats
- `strings.xml`: add the new keys to `values/` (en) and `values-ru/` (ru). Windows shipped these EN/RU-only; on Android the missing `zh/es/hi` keys **fall back to `values/` (English)** automatically — acceptable and matches intent (translate later if the customer wants). Keys: `monitor_3d_conduction`, `..._play`, `..._pause`, `..._rate`, `..._xray`, `..._solid`, `..._edit_pathway`, `..._done_editing`, `..._cutaway`, `..._cut_in_half`, `..._whole_heart`, `..._cut_position`, phase strings, and the `place_next`/`pathway_complete` hints.
- Carry the two pending Windows caveats: **cut axis is fixed to Z / keeps the far half** — may need a flip or axis control depending on the model's orientation (add if it comes out on the wrong side); **X-ray applies to the whole model only** (cut model stays opaque).

## 6. Risks & open questions

- **Cut cap not filled** in Three.js (hollow interior visible). Decision needed: accept `DoubleSide`, or invest in a stencil cap? (Windows fills it via `CrossSectionColor`.)
- **Cut side/axis** may be wrong for the specific `heart.glb` orientation — same open item as Windows (awaiting visual test there too).
- **HR source**: no wired current-rhythm HR on Android; Phase 6 adds a minimal computation or defaults. Confirm where the loaded rhythm's samples/peaks are accessible at the 3D-open site.
- **Three.js bundle**: pin a version and vendor it locally; verify GLTFLoader handles the same `heart.glb` model-viewer renders today. Removes the CDN dependency (a plus).
- **Coordinate space**: authored anchors and the default path must live in **model/local space** so the persisted JSON stays valid across sessions and matches Windows semantics.

## 7. Verification

1. Build; open 3D — base model renders and orbits (Phase 1 parity).
2. Play → pulse travels SA→apex, dwells at the AV node (PR pause), loops at the bpm slider; caption cycles P wave → PR → QRS → Diastole (Phase 3).
3. **Cut in half** → the slider slices the heart; the conduction pulse is visible **inside** the cut (Phase 4 — the core customer ask).
4. **X-ray** → myocardium fades; the pathway shows through.
5. **Edit pathway** → place 7 nodes by tapping; reopen the dialog → the authored path persists (filesDir JSON).
6. bpm reflects the loaded rhythm when available, else 75 (Phase 6).

## 8. PR breakdown

| # | PR title | Phase | Notes |
|---|----------|-------|-------|
| 1 | Heart3D: custom Three.js viewer replacing model-viewer | 1 | Vendor three.js locally; parity render |
| 2 | Heart3D: conduction model + timing template + sidecar | 2 | Kotlin data + filesDir store |
| 3 | Heart3D: conduction pathway render + pulse animation | 3 | JS module + bridge (play/bpm/pathway) |
| 4 | Heart3D: cutaway clip plane + X-ray | 4 | JS clipping + transparency |
| 5 | Heart3D: Compose controls + pathway authoring | 5 | Dialog UI + tap-to-place + save bridge |
| 6 | Heart3D: heart-rate feed | 6 | compute/ default bpm |
| 7 | Heart3D: localization + caveat parity | 7 | en/ru strings; flip/axis note |

---

## Outcome

*(Fill in when status moves to completed/dropped.)*

- **Result:** —
- **PRs:** —
- **Deviations from plan:** —
- **Follow-ups spawned:** —
