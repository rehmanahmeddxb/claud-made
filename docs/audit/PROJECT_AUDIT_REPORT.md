# Project Audit Report — Ahmed Reaction Studio

**Audited commit:** `3e27c43` (branch `arena/01a07b4d-claud-made`, base `main`)
**Audit date:** 2026-09-07
**Method:** full static read of all 45 Kotlin sources (~17k LOC), manifest, resources, CI workflow, build script; local reproduction of the CI toolchain and a **successful local APK build** (`BUILD OK`, 1,472,209 bytes); JVM regression suites executed.
**Not done:** no physical Android device or emulator was available. Every claim about runtime camera/codec/hardware behaviour is marked **NOT VERIFIED (needs device)**.

---

## A. Executive summary

### What this application does

A **local-first native Android reaction-video studio**. The user creates a project with a canvas aspect (16:9 / 9:16 / 1:1), adds *sources* — a local video, a live camera feed, a camera take, a screen recording, an image, or a text overlay — arranges them on the canvas as picture-in-picture layers, mixes their audio, then **records** the live composition or **exports** it offline to MP4/WebM.

It is deliberately built **without Gradle, AGP, AndroidX or any third-party dependency**. It compiles with raw `kotlinc` against an AOSP `android.jar` stub, dexes with `d8`, and signs with `apksigner`. The entire UI is constructed in Kotlin code; there is not a single layout XML.

### Who the intended user is

A solo content creator making reaction videos on a phone, offline, with no account and no cloud. The README's promise is "what you see is exactly what gets exported."

### What the application can currently do

Verified present and wired in code:

- Project CRUD with atomic-ish JSON persistence, rotating recovery snapshot, and a crash "open flag".
- A single normalized geometry model (`LayerFit`) shared by the preview compositor **and** the exporter — the strongest architectural decision in the codebase.
- Undo/redo over full layer-list JSON snapshots, including canvas aspect.
- OBS-style source verbs behind one command object (`SourceController`).
- Live Camera2 feed composited onto the canvas as a real layer; separate fullscreen recorder as a fallback.
- Capability-checked torch (front/back/both) plus a screen-light fallback.
- Screen capture via MediaProjection in a foreground service.
- Audio: mic capture, per-clip cursors on a sample-count master clock, resampler, limiter — all pure Kotlin and JVM-testable.
- Export with a codec picker, quality presets, resolution/fps choice, and a post-export playability probe.
- A restored studio chrome (top strip, transport, sources/mixer panels, radial wheel rail) — PR #1.

### What is missing

Ordered by how much it hurts:

1. **Undo coverage is a lie in three places.** `showUndoSnack` is only reachable from surfaces that were stubbed out. The wheel's Hide/Delete petals and the panel's remove call `ctrl.*` directly with **no snackbar at all** (§B-1).
2. **No thumbnail is ever written.** `ProjectStore.saveThumb()` has **zero callers**, so every Home card shows an empty grey box forever (§B-3).
3. **Home `ListView` ignores `convertView`** — it inflates a brand-new card hierarchy on every scroll frame and starts a new `Thread` per bind (§B-4).
4. **No empty state on Home.** Zero projects = a blank screen under a header.
5. **Screen recording has no pre-flight**; the aspect/orientation forcing fights the user (§B-6, §B-7).
6. No tests beyond four hand-rolled JVM `main()` harnesses. No instrumentation tests, no CI lint.

### Biggest technical problems

| # | Problem | Why it matters |
|---|---|---|
| 1 | **`EditorActivity.kt` is 3,290 lines** and implements two interfaces, owns camera, recorder, export, undo, dialogs, insets and 79 toasts | Every change risks an unrelated regression; it is the file the whole history of this repo keeps breaking |
| 2 | **~19 dead stub methods** (`private fun buildTopBar(vararg args: Any?) { }`) | `vararg Any?` stubs silently swallow *any* call — the compiler cannot tell you the call was meant to do something |
| 3 | **Fire-and-forget `Thread {}`** in Home and elsewhere; no structured concurrency, no cancellation | Leaks on rotation; bitmap decode races the recycled row |
| 4 | **`catch (_: Exception) { }` used ~120×**, including around every file write | Data-loss failures are silent; `ProjectStore.save` cannot fail loudly |
| 5 | Storage writes are **not atomic on failure** — `tmp.renameTo()` result is unchecked | A full disk can leave `project.json` stale with no warning |

### Biggest UX problems

1. Destructive/semi-destructive ops give **no undo affordance** on the surfaces users actually reach (wheel, panels).
2. **Touch targets below 48dp**: wheel rail 46dp, transport play 44dp, dock handle 44dp, mixer mute 36×32dp, Home chips 34dp.
3. **Forced rotation** on aspect change — picking 16:9 slams the phone into landscape mid-edit.
4. Home is bare: no empty state, no thumbnails, a 34dp ✕ sitting next to a 34dp Copy chip.
5. **79 toasts in one activity** as the primary feedback channel; toasts are unactionable and stack.

### Biggest product problems

- The **preview↔export equality claim is only structurally true** (same `Compositor`), but is unverified for the live-camera path, where offline export freezes the last camera frame. That is a defensible design, but it is nowhere communicated to the user.
- **No project-level media management**: `copyIntoMedia` duplicates bytes on every import and on every project duplicate, with no size accounting and no cleanup of orphaned media.

### Biggest risks

| Risk | Severity | Note |
|---|---|---|
| Media orphaning / unbounded storage growth | High | Deleting a layer never deletes its copied media file |
| `copyIntoMedia` collision naming produces `clip.mp4_1` | Medium | **Verified by simulation** — extension is destroyed, so the file no longer sniffs as MP4 |
| Silent save failure | Medium | Every persistence path swallows exceptions |
| Single 3,290-line activity | High | Maintainability; this is where CI kept going red |
| No automated UI/instrumentation test | Medium | Only four JVM math harnesses guard 17k LOC |

### Overall maturity

**A strong engine wearing a fragile, hand-rolled UI.** The geometry/audio/export core is genuinely well-reasoned and unusually well-commented. The presentation layer is one enormous activity plus an injector, held together by `isInitialized` guards and stub methods.

| Dimension | Score | Justification |
|---|---:|---|
| Architecture | **5/10** | Excellent core model (`LayerFit`, `SourceController`, `AudioMath`); ruined by a 3.3k-line god activity and 19 no-op stubs |
| UI/UX | **5/10** | Chrome restored in PR #1 and genuinely coherent; undermined by sub-48dp targets, 79 toasts, forced rotation |
| Functionality | **7/10** | The full reaction-video workflow is implemented end to end |
| Reliability | **5/10** | Autosave + snapshot recovery are good; ~120 silent catch blocks and unchecked renames are not |
| Performance | **5/10** | Sound codec tuning; Home re-inflates every row and spawns a thread per bind |
| Security | **8/10** | Local-only, no network, no secrets, no exported components, scoped permissions. Debug keystore password in `build-apk.sh` is by design for CI |
| Database | **6/10** | JSON-on-disk is right-sized; no schema migration path despite `SCHEMA = 1`; no media GC |
| API | **N/A** | No network layer, by design |
| Testing | **3/10** | 4 JVM harnesses + CI dex-symbol asserts; no unit framework, no instrumentation |
| Maintainability | **4/10** | Superb comments, terrible file sizes; stubs actively hide breakage |
| Product completeness | **6/10** | Core loop complete; hygiene, states and polish incomplete |

**Weighted overall: 5.4/10** — usable, promising, not yet professional.

---

## B. Findings discovered by this audit (not previously reported)

Each is traced to a file and line, with the verification status stated honestly.

### B-1 — Undo snackbar unreachable from the surfaces users touch  ·  P1
`EditorActivity.kt:665` defines `showUndoSnack`. It is called from `changeAspect` (1073), `showHideFeedback` (1125), `quickToggle` (1134), `duplicateLayer` (1169), and the advanced-sheet delete (1333). But:
- `RadialMenus.kt:234` (Hide petal) calls `h.ctrl.toggleVisible(l.id)` directly.
- `RadialMenus.kt:287` (Delete petal) calls `h.ctrl.delete(l.id)` directly.
- `StudioLayoutInjector.kt:571/576` (panel eye + Hide) call `ctrl.toggleVisible` directly.
- `EditorActivity.removeSelectedSource()` (1594) deletes with no feedback at all.

**Impact:** deleting a source from the wheel — the primary navigation surface — is silent and offers no undo. **VERIFIED by code inspection.**

### B-2 — `pushUndoLight()` throttle can drop the *first* mutation  ·  P2
`EditorActivity.kt:1646`: `lastUndoPush` initialises to `0L`, so the first call passes; but it is assigned **outside** the `if`, so a rapid slider drag pushes one snapshot then suppresses for 350 ms — correct — yet a *single* slider tweak followed within 350 ms by a *different* verb also gets suppressed. Undo then jumps two edits back. **VERIFIED by inspection; low frequency, real.**

### B-3 — Project thumbnails are never generated  ·  P1
`ProjectStore.saveThumb()` (`ProjectStore.kt:144`) has **no callers anywhere in the codebase** (grep confirms). `HomeActivity` reads `store.thumbFile(p.id)` and checks `.exists()`, which is always false. **VERIFIED.**

### B-4 — Home list re-inflates every row and threads per bind  ·  P1
`HomeActivity.kt:262`: `getView(pos, convert, parent)` never uses `convert`. It builds ~12 views per card each time, and at line 297 starts a raw `Thread` per bind to decode a thumbnail. With thumbnails fixed (B-3) this becomes a real jank and leak source. **VERIFIED.**

### B-5 — `copyIntoMedia` collision naming destroys the file extension  ·  P2
`ProjectStore.kt:118-121`: on collision it produces `"${name}_$i"` → **`clip.mp4_1`**. Downstream `MediaExtractor`/`MediaMetadataRetriever` sniff content, so it usually still decodes, but the file is no longer recognisable to any file manager and `MediaSave` MIME guessing degrades. **VERIFIED by simulation.**

### B-6 — Aspect change force-rotates the device  ·  P2
`EditorActivity.applyOrientationFor` (274) sets `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` for 16:9. Choosing a 16:9 canvas therefore physically rotates the UI mid-edit. It is called from `onCreate` *and* `changeAspect` (1068). **VERIFIED.**

### B-7 — Screen recording has no pre-flight  ·  P2
`startScreenCapture` (1753) checks only `ScreenCaptureService.running` and MediaProjection availability. No free-storage check, no notification-permission pre-check (POST_NOTIFICATIONS is requested elsewhere, at 1727, but not before starting the FGS whose notification is mandatory on API 33+). **VERIFIED by inspection; runtime behaviour NOT VERIFIED.**

### B-8 — Deleting a layer orphans its media bytes  ·  P2
`SourceController.delete` removes the `Layer` from the list. Nothing deletes `<project>/media/<file>`. Import → delete → repeat grows storage without bound. **VERIFIED.**

### B-9 — Live camera ignores the project aspect  ·  P3
`LiveCamera.kt:67` hardcodes `WANT = Size(960, 540)` (16:9) and picks the closest sensor output by area. On a 9:16 project the feed is still captured 16:9 and letterboxed by `fit`. Not a bug per se, but it wastes pixels and contradicts "camera follows project aspect". **VERIFIED.**

### B-10 — `Layer.clone()` copies the id  ·  P3
`Model.kt` `clone()` passes `id` through. `SourceController.duplicate` immediately overwrites it, and `ProjectStore.duplicate` keeps it deliberately — but any future caller gets a duplicate-id bug for free. **VERIFIED; latent.**

### B-11 — Nineteen `vararg Any?` no-op stubs  ·  P2
e.g. `EditorActivity.kt:1046` `private fun buildExportPanel(vararg args: Any?) { }`. Because the parameter is `vararg Any?`, **any** call site compiles. This is precisely how "the panel is built but never shown" regressions survive compilation. **VERIFIED.**

### B-12 — Repo hygiene: 1.5 MB source zip and duplicated images committed  ·  P3
`AhmedReactionStudio-source.zip` (1,492,954 B) plus `image_*.jpeg` duplicated at repo root *and* in `attached_images/`. `.gitignore` covers only `build_out/`, `*.log`, `artifacts/`. **VERIFIED.**

---

## C. Architecture map (as actually found)

```
SplashActivity ──▶ HomeActivity ──▶ EditorActivity (3290 LOC, god object)
                        │                  │
                        │                  ├── StudioLayoutInjector (651)  chrome
                        │                  ├── StageView (750)             gestures
                        │                  ├── PreviewEngine (787)         decode+clock
                        │                  ├── LiveCamera (775)            Camera2
                        │                  ├── RadialMenuView/Menus (1045) navigation
                        │                  └── Sources/Mixer/Controls/Properties panels
                        │
                   ProjectStore ──▶ filesDir/projects/<id>/{project.json, snapshot.json,
                                                            thumb.png, media/*}
core/    Model(Layer, Project, LayerFit, LayerPresets) · SourceController · UndoStack · ViewportFit
export/  Exporter · CompositionRecorder · AudioMath(Resampler, ClipCursor, Limiter) · YuvWriter
         EncoderConfig · ExportValidator · MediaSave
camera/  CameraActivity (fullscreen fallback) · TorchController
capture/ ScreenCaptureService (MediaProjection FGS)
```

**Single-state property holds:** `Compositor.draw()` is called by `PreviewEngine`, `CompositionRecorder`, `Exporter` **and** `snapshotFrame()`. That is the reason preview==export is credible.

---

## D. Verification status of this audit

| Claim class | Status |
|---|---|
| Code structure, call graphs, dead code, stub methods | **VERIFIED** (grep + read) |
| Local APK builds from a clean toolchain | **VERIFIED** — `BUILD OK`, signed v1+v2, 1,472,209 B |
| JVM math suites (audio, viewport, layers, geometry) | **VERIFIED** — all pass locally |
| Touch-target sizes | **VERIFIED** by reading the dp constants |
| Camera/torch/codec/MediaProjection runtime behaviour | **NOT VERIFIED — requires a physical device** |
| Export A/V sync, real output playability | **NOT VERIFIED — requires a physical device** |
| Performance/jank claims | **Inferred from code**, not profiled |
