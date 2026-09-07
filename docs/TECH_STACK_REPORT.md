# Ahmed Reaction Studio — Full Tech Stack Report

**Package:** `com.rehman.ahmedreactionstudio`
**Version:** 1.0.0 (versionCode 1)
**Report date:** 2026-09-07
**Branch snapshot:** `arena/01a07b8e-claud-made` (from `main` @ `1985d5f`)
**Codebase size:** ~17,100 lines of Kotlin across 41 source files + `R.kt` (generated)
**Author of this report:** Arena.ai Agent (static code audit of the repo)

> Scope per request: **special deep-dive on (1) video rendering, (2) camera, (3) local media**,
> plus the **complete tech stack of the whole app**. Every claim below is traceable to a
> file path in this repo. No external SDK, no Jetpack, no NDK — 100% framework APIs.

---

## 1. Stack at a glance

| Layer | Technology | Notes |
|---|---|---|
| Language | **Kotlin (JVM 1.8 bytecode)** | Compiled with `kotlinc -jvm-target 1.8` |
| Platform | **Native Android, framework APIs only** | `android.*`, `org.json`, zero AndroidX / Jetpack / Compose / ExoPlayer / Glide / FFmpeg |
| UI toolkit | **Programmatic classic Views** (`View`, `TextView`, `LinearLayout`, custom `StageView`) | No XML layouts, no Fragments, no Compose; all UI built in Kotlin code |
| Min / Target SDK | **26 (Android 8.0) / 30 (Android 11)** | Runs on 8.0+; compile stub is API-30 `android.jar` |
| Build system | **Custom offline script `build-apk.sh`** (no Gradle/AGP) | `aapt2 → R.kt → kotlinc → d8 → apksigner` (v1+v2 signed) |
| Camera | **Camera2** (`CameraManager/Device/Session/CaptureRequest`) + `ImageReader` YUV + `MediaRecorder` | Two capture paths: live-in-canvas + fullscreen take recorder |
| Flash | **`CameraManager.setTorchMode()`** via `TorchController` + `FLASH_MODE_TORCH` fallback + screen-light fallback | Per-facing LED inventory, never faked |
| Video decode (preview/export) | **`MediaExtractor → MediaCodec (HW) → SurfaceTexture (OES) → GLES2/3 FBO → Bitmap`** | MX-Player/ExoPlayer-style continuous decode on a private GL thread |
| Video decode (fallback) | **`MediaMetadataRetriever` (`FrameSource`)** | Cached per-layer, auto-retries HW with back-off |
| Preview audio | **`MediaPlayer` per clip** (off-UI-thread `prepare()`) | Loop/mute/solo/volume, 1.2 s drift re-anchor |
| Render/composite | **Custom CPU `Compositor` on `android.graphics.Canvas`** | One code path for preview + record + export → "preview == export" |
| GPU helpers | **EGL14 + GLES20 (+GLES30 PBO when available), OES external textures, hand-written shaders** | Offscreen pbuffer context, async PBO read-back |
| Export video encode | **`MediaCodec` encoders (byte-buffer YUV input) → `MediaMuxer`** | H.264 / H.265 → MP4, VP8 / VP9 → WebM; VBR + long GOP tuning |
| Live record | **`CompositionRecorder`** (H.264+AAC/MP4 always) | 3-stage pipeline: mic thread → mix/encode thread → video thread |
| Audio capture | **`AudioRecord` (MIC, 44.1 kHz mono 16-bit, 48 kHz fallback + resample)** | Dedicated urgent-audio thread, HAL-timestamp clock anchoring |
| Audio decode/mix | **`MediaCodec` audio decode → mono float mix bus → peak limiter → AAC-LC** | Sample-count PTS clock, never wall-clock |
| Screen capture | **`MediaProjection + VirtualDisplay → MediaRecorder`** in foreground service | `ScreenCaptureService`, `mediaProjection` FGS type |
| Image decode | **`BitmapFactory` with `inSampleSize`** | Bounded stills, lazy per-layer |
| Text render | **`StaticLayout` + `TextPaint`** (memoised, bounded cache) | Drawn inside compositor, rotation-aware |
| Persistence | **JSON (`org.json`) + app-private files** | `filesDir/projects/<id>/{project.json, snapshot.json, thumb.png, media/}` |
| State/undo | **Custom `SourceController` command verbs + `Undo` stack + autosave + snapshot recovery** | Single mutation path, total undo |
| Crash reporting | **Custom `App` uncaught-exception handler → `filesDir/crashes/`** | Rolling 6 logs, surfaced in Diagnostics |
| Icons | **Vector drawables (`res/drawable/ic_*.xml`) + runtime `Icons.kt` tint helper** | Material-style, no image deps |
| CI | **GitHub Actions (`.github/workflows/android.yml`) + Python validators + JVM math tests** | Builds APK, uploads artifact |
| Toolchain sources | Temurin JDK 17/21, `kotlinc`, AOSP `android-30.jar`, `aapt2` (PyPI), `d8.jar`/`apksigner.jar` (build-tools libs) | Assembled by `tools/ci-toolchain.sh` under `/tmp/ahmed-tc` |
| Third-party deps | **NONE at runtime** | Only stdlib `kotlin-stdlib.jar` dexed in; no network libs at all |

---

## 2. Platform, language & build toolchain

### 2.1 Language & SDK levels
- **Kotlin only.** Every production file is `.kt` (`app/src/.../*.kt`). `R.kt` is generated
  from `R.java` by a Python step inside `build-apk.sh` (because the bundled JRE has no `javac`).
- **JVM target 1.8**, stdlib bundled as `kotlin-stdlib.jar` and dexed with the app classes.
- `minSdk 26 / targetSdk 30` are passed to `aapt2 link` (`--min-sdk-version 26
  --target-sdk-version 30`) and `--min-api 26` to D8. Manifest confirms installable on **Android 8.0+**.
- `android.jar` used for *compilation* may be a newer stub, but D8 is fed a **Java-8-format
  API-30 stub** (`android-d8.jar`) because the old D8 cannot parse Java-17 class files.

### 2.2 Dependency-free offline builder (`build-apk.sh`, 7 phases)
1. `aapt2 compile + link` → `base.apk` + `R.java`
2. Python converts `R.java → R.kt`
3. `kotlinc` compiles all `*.kt` (+ `R.kt`) against `android.jar` (2 GB heap, 4 GB retry; fails loudly on empty output so a stale APK is never shipped)
4. `classes/ → classes.jar → D8 → classes.dex` (with `--lib android-d8.jar`)
5. Python merges `base.apk + classes.dex → unsigned.apk`
6. `apksigner` signs **v1+v2** with a generated RSA keystore (`ahmed.keystore`)
7. `apksigner verify` + artifact lands at `artifacts/AhmedReactionStudio-1.0.0.apk`

### 2.3 CI (`.github/workflows/android.yml`)
Push/PR/manual → checkout → 4 Python static validators → JDK 17 setup →
`tools/ci-toolchain.sh` → JVM regression suites → `./build-apk.sh` → APK + log artifacts.
CI also asserts feature symbols exist in the dex (camera, torch, export, Phase-2/Step-5 markers).

### 2.4 Why "no Gradle / no AndroidX" matters
- Reproducible offline build; no Maven downloads at build time.
- Smaller APK, no transitive CVEs from support libs.
- Cost: everything (navigation, theming, image loading, video playback) is hand-rolled on
  framework APIs — which is exactly what sections 3–9 describe.

---

## 3. App architecture overview

```
┌─────────────┐   ┌─────────────┐   ┌──────────────────────┐   ┌──────────────┐
│ SplashActivity│──▶│HomeActivity │──▶│ EditorActivity       │──▶│CameraActivity│
│ (animated   │   │ (project    │   │ (fullscreen OBS-style│   │(fullscreen   │
│  intro)     │   │  home/CRUD) │   │  studio)              │   │ take recorder)│
└─────────────┘   └─────────────┘   └──────────────────────┘   └──────────────┘
                                            │  owns
        ┌───────────────────────────────────┼───────────────────────────────────┐
        ▼                                   ▼                                   ▼
  ┌───────────┐                     ┌──────────────┐                   ┌─────────────────┐
  │ StageView │◀── draws ──────────│ PreviewEngine │◀── frames ───────│ GpuVideoPipeline│
  │ (viewport │   Compositor.draw() │ (master clock│    (per-layer   │ (GL thread+EGL) │
  │ +gestures)│                     │ +MediaPlayers│     decode req)  │  N×GpuVideoDecoder│
  └───────────┘                     └──────────────┘                   └─────────────────┘
        │                                   │ ▲                                │
        │                           LiveCamera│ │ MediaKit.FrameSource (fallback)
        │                           (pushes   │ │ MediaKit.image (stills)
        │                            bitmaps) │ │
        ▼                                   ▼ ▼                                ▼
  ┌──────────────────────────────────────────────────────────────────────────────┐
  │  Project (layers) ← SourceController verbs ← UI (SourceDock, RadialWheel,   │
  │  panels, Quick Control Bar) · persisted by ProjectStore (JSON) · Undo stack │
  └──────────────────────────────────────────────────────────────────────────────┘
        │                                              │
        ▼                                              ▼
  ┌──────────────┐                            ┌──────────────────┐
  │   Exporter   │                            │CompositionRecorder│──▶ MP4 file
  │ (offline,    │                            │ (realtime, H.264/ │
  │  4 codecs)   │──▶ MP4/WebM file           │  AAC/MP4)         │
  └──────────────┘                            └──────────────────┘
        │                                              │
        └──────── both use Compositor + YuvWriter + MonotonicPts + ExportValidator
```

**Module map (`app/src/com/rehman/ahmedreactionstudio/`):**

| Package | Files | Responsibility |
|---|---|---|
| `ui/` | `SplashActivity`, `HomeActivity`, `DiagnosticsActivity` | Launch intro, project home, crash/codec diagnostics |
| `editor/` | `EditorActivity` (3,615 lines — the studio), `StageView`, `PreviewEngine`, `LiveCamera`, `SourceDock`, `RadialWheel`, `RadialMenus`, `MixerPanel`, `SourcesPanel`, `ControlsPanel`, `EffectsPanel`, `PropertiesPanel`, `StudioLayoutInjector`, `Icons` | All editing UI + preview orchestration |
| `camera/` | `CameraActivity`, `TorchController` | Fullscreen take recorder + the single LED driver |
| `capture/` | `ScreenCaptureService` | MediaProjection screen recorder (FGS) |
| `core/` | `Model` (Project/Layer/LayerFit), `Compositor`, `MediaKit`, `ProjectStore`, `Sources` (SourceController), `Undo`, `ViewportFit` | Domain model + shared render/probe/store logic |
| `core/gpu/` | `EglCore`, `GlUtil`, `GpuVideoDecoder` | Offscreen GL + continuous HW video decode |
| `export/` | `Exporter`, `CompositionRecorder`, `EncoderConfig`, `YuvWriter`, `AudioDecode`, `AudioMath`, `AudioMixer`, `ExportValidator`, `MediaSave` | Offline export + realtime record + audio/export infra |
| `util/` | `Util` | Theming/dimens/toast helpers (`UI`) |
| root | `App` | Crash-logging `Application` subclass |

---

## 4. ★ DEEP DIVE 1 — Video rendering stack (the "special" part)

This is the heart of the app. Three consumers — **live preview, realtime record, offline export** —
all render through **one deterministic function**: `Compositor.draw()` (`core/Compositor.kt`).
That single decision is why "what you see is exactly what gets exported."

### 4.1 Render pipeline (end to end)

```
 Local file ──▶ GpuVideoDecoder ──▶ OES tex ──▶ GLES blit ──▶ ARGB Bitmap ──┐
 Camera ──────▶ LiveCamera (YUV→ARGB CPU) ──▶ ARGB Bitmap ──────────────────┤
 Image ───────▶ BitmapFactory ──▶ ARGB Bitmap ──────────────────────────────┤
 Text ────────▶ (no bitmap; drawn by compositor from string) ───────────────┤
                                                                              ▼
                    PreviewEngine.frames[id] ──▶ Compositor.draw(canvas,W,H,project,bitmapFor)
                                                                              │
                                         ┌────────────────────────────────────┼────────────────┐
                                         ▼                                    ▼                ▼
                                   StageView.onDraw                     CompositionRecorder  Exporter
                                   (screen, ~60 Hz tick)                (record, 30 fps)     (offline, fps×dur)
```

### 4.2 The shared compositor (`core/Compositor.kt`, 209 lines)
- **Tech:** `android.graphics.Canvas.drawBitmap` + `drawColor` + `clipRect` + `save/rotate/restore`;
  `Paint(ANTI_ALIAS|FILTER_BITMAP)`; `StaticLayout` for text; `Typeface sans-serif-condensed bold`.
- Draws layers **in list order** (index 0 = background, last = topmost z). Skips `!visible` and `opacity ≤ 0.005`.
- Per-layer geometry: normalized box (`cx, cy, wN, hN`, canvas fractions) → pixels; rotation via `canvas.rotate(rotDeg)`.
- Per-source **fit mode** (OBS plan §3) via the single formula `LayerFit.drawnFrame()`:
  - `fill` (COVER): `scale = max(boxW/effW, boxH/effH)` — full-bleed, edges cropped.
  - `fit` (CONTAIN): `scale = min(boxW/effW, boxH/effH)` — whole frame, letterboxed. Camera layers default to `fit` so takes are never cut.
- `chromeRect()` computes the **exact visible picture** (`frame ∩ box`) — the selection border, 8 handles and hit-testing all use this same formula, so chrome can never drift from the picture.
- Text: `StaticLayout.Builder`, centered, 4% side padding, optional shadow, per-layer opacity; layouts memoised on `(text|width|size|shadow)` in a bounded (96-entry) cache — no per-frame rebuild, no GC hitches.
- **Never draws selection chrome** — `StageView` does that in a second pass, so no border can leak into a recording/export. `selectionId` param is reserved/unused by design.
- Reusable `Compositor.Ctx` holds `Paint`/`RectF`/`TextPaint`/cache — zero allocation per frame.

### 4.3 GPU video decode (`core/gpu/`, 1,161 lines total)
The "MX Player / ExoPlayer style" path. **Not** seek-grab thumbnails.

| Component | File | Tech & role |
|---|---|---|
| `GpuVideoPipeline` (singleton) | `GpuVideoDecoder.kt` | Owns one `HandlerThread("gpu-video")`, one shared `EglCore`, and a `ConcurrentHashMap<layerId, GpuVideoDecoder>`; `ensure()/post()/runSync()` marshal all codec+GL work onto the GL thread |
| `GpuVideoDecoder` (one per clip) | `GpuVideoDecoder.kt` (~530 lines) | `MediaExtractor` (video track select) → `MediaCodec.createDecoderByType(mime)` → `configure(fmt, oesSurface, …)` → forward decode; `KEY_LOW_LATENCY=1` on API 30+; per-decoder OES target + own `OesToBitmap` blitter (a shared blitter thrashed FBO reallocs on every multi-layer frame) |
| `EglCore` | `EglCore.kt` | Offscreen EGL14 context: tries **ES 3 + pbuffer** first (sets `es3=true` → PBO path), falls back to **ES 2**; `eglMakeCurrent` before every decode batch |
| `OesSurfaceTarget` | `EglCore.kt` | `GL_TEXTURE_EXTERNAL_OES` texture + `SurfaceTexture` + `Surface` as the codec output surface; `updateTexImage()` + `getTransformMatrix()` called synchronously right after `releaseOutputBuffer(render=true)` — deliberately **not** waiting on `onFrameAvailable` (which can never fire while the GL thread is busy; the old wait cost 16 ms + 1 frame latency per frame) |
| `GlUtil.OesToBitmap` | `GlUtil.kt` | Hand-written GLSL (vertex: MVP × ST-matrix texcoords, Y-flip; fragment: `samplerExternalOES` sample, **deliberately no BGRA swizzle** — `glReadPixels(GL_RGBA)` byte order already equals `ARGB_8888` memory order; a previous `.bgra` turned faces blue) → FBO render → read-back → `Bitmap` |
| Async read-back | `GlUtil.kt` (`readViaPbo`) | **ES3 ping-pong PBOs** (`GL_PIXEL_PACK_BUFFER`, `glMapBufferRange`): frame N queues the DMA and maps frame N−1 → ~1–3 ms vs 15–25 ms sync stall. First frame still does a sync read so the preview is never black. Full graceful degradation to sync `glReadPixels` without ES3 |
| `GlUtil.fitSize` | `GlUtil.kt` | Longest-side cap + force-even dims (YUV alignment) |

Decoder intelligence inside `advanceTo(mediaMs, maxSide, forceSeek, paced)`:
- Seek only on scrub/big jumps (`target+80ms < lastPts` backward, `>1.2s` forward gap); otherwise streams forward.
- **Preview pacing** (`paced=true`): if `target < publishedPts + avgFrameInterval`, returns the already-published bitmap for free — a 60 Hz tick on a 24 fps clip costs nothing.
- Catch-up drop: frames >120 ms behind target are released with `render=false`.
- Learns `avgFrameUs` (EMA) as the media's real cadence for the pacing check.
- Double-buffered output bitmaps (`bufA/bufB` + `published` pointer); `owns()` tells the engine which bitmaps it must never recycle.
- Broken decoders are **never cached** — one transient failure can't permanently demote a layer to software.

### 4.4 Preview engine (`editor/PreviewEngine.kt`, 787 lines)
- **Master clock:** `Handler(Looper.getMainLooper())` ticker at **~60 Hz (16 ms)**; `masterMs += dt` (clamped ≤120 ms/tick); wraps at `project.durationMs()`.
- **Independent per-layer clocks** (`Clock`: `freeze/resumeAt/wasPlaying`, speed-scaled): a paused layer freezes while others play; non-looping clips **hold last frame + auto-pause** at end; play-from-end restarts at 0:00.
- **Frame requests:** one decode per visible+playing layer per tick; in-flight layers skipped (slow decoder degrades fps, never queues debt). `forceSeek` set after scrub.
- **Adaptive quality:** each clip decoded at its **on-screen drawn size** (`StageView.visibleFrameMaxPx` via `layerTargetPx` — a 300 px PiP is decoded at ~300 px, not 960 px: ~10× fewer pixels through decode+readback+copy), × `adaptiveScale` (opens at 0.8, hunts 0.4–1.0 from measured `avgDecodeMs`; recovers when ticks are served by pacing alone). Paused/scrubbed frames get a 1.35× crispness boost.
- **Software fallback with retry:** GPU miss → cached `MediaKit.FrameSource` for that frame; `softStreak`/`gpuRetryAt` retry HW after N seconds — fallback is "last resort, not life sentence." HUD `stats()` reports `HW` vs `SW×n`, fps, ms/frame.
- **Audio monitor:** `MediaPlayer` per clip, created+`prepare()`d on a 2-thread pool (never UI thread), `seekTo+start` on main; drift re-anchor (>400 ms, ≤1/s); `monitorMuted` silences speakers during composite record (prevents mic echo/double-audio) without touching clocks/decoders.
- **External frames:** live-camera bitmaps registered in `externalIds` — engine never recycles producer-owned buffers (fix for "camera goes black when a video is added").
- **Stills:** `IMAGE` layers decoded once off-main via `MediaKit.image()` (`inSampleSize`), cached by id.

### 4.5 Viewport & stage (`editor/StageView.kt`, 757 lines + `core/ViewportFit.kt`)
- Custom `View` filling the screen; canvas **contain-fitted** into view minus measured chrome insets (top bar, bottom dock/sheet, system bars, cutout) + breathing margin: `scale = min(availW/canvasW, availH/canvasH)`, centered. Degenerate insets fall back to full view (never collapses to zero).
- `onDraw` = `drawColor(surround)` → translate+clip → `Compositor.draw()` → 1 px export-area frame → subtle hairlines on unselected sources → full selection chrome on the selected source (contrast underlay + 2.5 dp accent stroke, 8 handles, rotate knob, label pill with type+name+lock state — never color-only).
- Gestures in canvas-local px / normalized units: tap-select, **double-tap text to edit**, drag-with-snap (normalized 1.6% threshold, canvas+sibling edges/centers), true anchored 8-handle resize (opposite side stays put; crop-style free distort except live-camera corners which lock aspect), rotate knob with 90° snapping, 2-finger pinch scale+rotate, 460 ms long-press → radial menu at finger. 28 dp handle targets (documented honest limit vs 48 dp). One undo snapshot per gesture. `LayerFit.hitTest` is rotation-aware, topmost-first, skips hidden/transparent.
- Reports fitted canvas size via `onCanvasLayout` (drives decode targets + HUD).

---

## 5. ★ DEEP DIVE 2 — Camera stack (the "special" part)

Two complementary capture paths share one LED driver. No CameraX, noFoto libs — raw **Camera2**.

### 5.1 Path A — Live camera as a canvas source (`editor/LiveCamera.kt`, 800 lines) ⭐
The "reaction cam without leaving the canvas": Camera2 frames composite live with z-order/fit/opacity/transform like any layer.

| Aspect | Implementation |
|---|---|
| Session | `CameraManager.openCamera(id)` on `HandlerThread("live-cam")`; `TEMPLATE_PREVIEW` (or `TEMPLATE_RECORD` while recording); targets = `ImageReader.surface` (+ `MediaRecorder.surface` when recording) |
| Capture target | `ImageReader.newInstance(w, h, YUV_420_888, 3)`, `acquireLatestImage()` (drops stale, never queues debt) |
| Feed size | Sensor sizes ≤1280² ranked by **aspect-match to project canvas first**, then closeness to 960×540 budget — a 9:16 project gets a portrait sensor output instead of a cropped landscape frame (BUG-14 fix) |
| Record size | **Separate** `getOutputSizes(MediaRecorder::class)` table, closest ≤1080p to 720p (feeding the YUV size here broke `createCaptureSession` on some devices → fake "camera busy") |
| YUV→ARGB | **Hand-written integer-math converter** (`convert()`): BT.601-ish `Y1192` coefficients, plane `rowStride`/`pixelStride`-aware, rotation + front-mirror **folded into the destination index** — zero `Matrix`/`createBitmap` per frame (the old path GC-thrashed at 30 fps) |
| Rotation | `(sensorOrientation ± deviceRotation [+180 front]) % 360`; display rotation read live from `WindowManager` |
| Throttle | **~24 fps** (`TARGET_FPS_MS=42`) — canvas can't show more; conversion is the expensive part |
| Buffering | **Triple-buffered** reusable `ARGB_8888` bitmaps (`bufA/B/C` + `published`); never writes the bitmap the compositor/recorder is reading (fixes torn/black export frames); old-size buffers dropped on rotation swap (fixes `setPixels` size-mismatch crash) |
| Facing | `LENS_FACING_FRONT/BACK` pick with fallback to first id; `switchFacing()` refuses while recording; outgoing torch cleared unless "both flashes" mode |
| Recording | `MediaRecorder` (API 31+ ctor `MediaRecorder(ctx)`, else legacy): SURFACE video + MIC audio (if granted) → MPEG-4 → `cam_<ts>.mp4`: H.264 6 Mbps 30 fps, AAC 128 kbps 44.1 kHz, proper `setOrientationHint`; session rebuilt (preview+recorder targets) around `start()/stop()`; takes <40 KB discarded |
| Threading/state | All camera ops on the cam handler; `opening/device/session` state machine mirrors `CameraActivity` (fixes double-session crashes); torch applied after every session rebuild |

### 5.2 Path B — Fullscreen take recorder (`camera/CameraActivity.kt`, 780 lines)
DSLR-style overlay UI on a `TextureView`: preview + zoom slider (digital zoom via `SCALER_CROP_REGION`, label `1×–max×`) + front/back switch + flash + red record button + `MM:SS` timer + status line.
- Preview size: prefer 1280×720 from `getOutputSizes(SurfaceTexture)`, transform matrix handles sensor orientation + front mirror (`updatePreviewTransform`), sensor-landscape orientation.
- Record: same MediaRecorder recipe at **8 Mbps**; `setAudioSource` **before** `setOutputFormat` (the old order was an illegal state that killed every mic take); validates take (`durMs ≥ 300 ms`, `width ≠ 0`, `≥ 50 KB`) then returns `rel` path + `role(main/pip)` to the editor.
- Robustness: views added exactly once; serialized open/close (`opening/started`); permission result re-opens camera.

### 5.3 The single LED driver (`camera/TorchController.kt`, 326 lines)
The **only** code allowed to touch a real flash unit.

- **Primary mechanism:** `CameraManager.setTorchMode(cameraId, on)` — works **without opening a camera**, survives session rebuilds (record start/stop, facing switch, preview restart). Registered `TorchCallback` keeps `torchState` in sync; `TorchCallback.onTorchModeUnavailable` marks torch-less ids.
- **Inventory:** scans `cameraIdList` characteristics (`LENS_FACING` + `FLASH_INFO_AVAILABLE` + HW level); per-facing candidate lists sorted ascending (id "0" = main sensor = LED owner on all known devices). `hasFlash(front)` never lies — UI falls back honestly to screen light.
- **Failure taxonomy** (`Fail` enum → `failureText()`): no service / no flash / torch unsupported / in-use / disconnected / disabled-by-policy / error / permission-denied. `setTorch` returns `false` with a reason instead of pretending; turning off is best-effort over **all** candidate ids (some devices route the LED through a different id than the one switched on).
- **Fallback chain per side:** `setTorchMode` → (if refused *and* side reports a flash unit) `CaptureRequest.FLASH_MODE_TORCH` on the live repeating request (`torchViaRequest`) with `CONTROL_AE_MODE_ON` → (front with no LED) white overlay **screen light** at 85% alpha → (back with no LED) honest "No flash" disabled button, never faked.
- **Safety:** per-facing wanted-state (`frontTorch/backTorch`), explicit opt-in `bothTorches` mode (the only way an idle side stays lit); `releaseAll()` on every exit: stop/error/switch/pause/close/finish/disconnect — **no path can leave the rear LED burning**.
- Diagnostics surface: `describe(front)` (`"id 0 · LED on/off/unsupported"`), `usedTorchId()`, `isTorchOn()` from the framework callback.

### 5.4 Camera data flow (live path)

```
Sensor → CameraDevice → ImageReader(YUV_420_888) → convert() [YUV→ARGB + rotate + mirror]
  → triple-buffer Bitmap → PreviewEngine.setFrame(external) → Compositor.draw (z/fit/opacity)
  → StageView (preview) + CompositionRecorder (live motion IS recorded) / Exporter (liveFrames still)
```

---

## 6. ★ DEEP DIVE 3 — Local media stack (the "special" part)

"Local media" = everything imported from the device or captured in-app, copied into the project, probed, decoded on demand. **Original files are never touched; videos are never held in memory** (spec 53).

### 6.1 Import & storage
- **Sources:** Storage Access Framework picker (video/image content URIs) → `MediaKit.copyContentToFile()` streams URI → `filesDir/projects/<id>/media/<file>`; camera takes (`CameraActivity` / `LiveCamera.startRecording`) and screen takes (`ScreenCaptureService`) are written there directly.
- **Layout** (`ProjectStore`): `project.json` (authoritative, atomic tmp+rename write, returns success — BUG-17), `snapshot.json` (rotating recovery), `thumb.png` (home thumbnail), `media/`.
- **Accepted containers (import):** anything the framework can decode — **MP4, AVI, WebM, MKV, 3GP, MOV** (+ images incl. WebP). Un-decodable files are reported, not crashed on. AVI is import-only (no Android AVI muxer exists).

### 6.2 Probing (`MediaKit.probe`, `core/MediaKit.kt`)
`MediaMetadataRetriever` → `MediaInfo(durMs, width, height, rotation, mime)` (both path and content-URI overloads). Populates `Layer.durMs/srcW/srcH/srcRotation` at import; `LayerFit.effective()` applies rotation to get decoded-frame dimensions. `HomeActivity`/`EditorActivity` do copy+probe+bounds **off the main thread**.

### 6.3 Video decode (local files)
Two tiers, same as §4.3, viewed from the media side:

| Tier | Class | How it reads local media |
|---|---|---|
| HW (primary) | `GpuVideoDecoder` | `MediaExtractor.setDataSource(path)` → first `video/*` track → `MediaCodec` HW decoder → OES surface. Handles `KEY_ROTATION`/`rotation-degrees`, `INFO_OUTPUT_FORMAT_CHANGED` (mid-stream size/rotation updates), EOS; `SEEK_TO_PREVIOUS_SYNC` + `flush()` on scrub |
| SW (fallback) | `MediaKit.FrameSource` | One cached `MediaMetadataRetriever` per layer; `frameAt(ms, maxPx, closest)`: `getScaledFrameAtTime(…, maxPx, maxPx)` on API 27+, else `getFrameAtTime`; `PREVIOUS_SYNC` for playback speed, `CLOSEST_SYNC` for scrub accuracy; single `Matrix` pass for rotation+downscale; `lastDecodeMs`/`produced` telemetry |

Preview and export share the GPU tier (`Exporter.Dec` drives the same `GpuVideoPipeline` with `paced=false` — exact frame stepping, no duplicate frames).

### 6.4 Images & text
- Images: `MediaKit.image(path, maxPx)` — `inJustDecodeBounds` + power-of-2 `inSampleSize` → bounded `ARGB_8888`; preview lazy-decodes per layer (`PreviewEngine.requestImage`), export caches full-res in `heldImages`.
- Text: no file at all — `Layer(text, textColor, fontSizeN, shadow)` rendered by the compositor (§4.2).

### 6.5 Local-media lifetime

```
SAF URI / camera take / screen take
  → media/<file> (private copy) → probe → Layer(relPath, durMs, srcW/H/rot)
  → preview: GPU decode @ drawn size (adaptive) · export: GPU decode @ export size
  → record: decoded frames composited live · audio: pre-decoded to PCM (§8)
```

---

## 7. Export & recording stack

### 7.1 Offline exporter (`export/Exporter.kt`, 697 lines)
`Project → per-frame Compositor.draw @ W×H → ARGB → YUV → MediaCodec encoder → MediaMuxer`, on thread `"ahmed-export"`.

- **Codec matrix** (`Exporter.Codec.available()` filters by `MediaCodecList` encoders actually present):

| Choice | MIME | Container | Ext | Audio |
|---|---|---|---|---|
| H.264/AVC | `video/avc` | MP4 | `.mp4` | AAC 128k |
| H.265/HEVC | `video/hevc` | MP4 | `.mp4` | AAC 128k |
| VP8 | `video/x-vnd.on2.vp8` | WebM | `.webm` | video-only (WebM muxer audio varies) |
| VP9 | `video/x-vnd.on2.vp9` | WebM | `.webm` | video-only |

- **Encoder pick** (`pickEncoder`): HW (`omx./c2.` non-google/sw) before SW; `COLOR_FormatYUV420Flexible` first (defined for `getInputImage`), then NV12(21)/I420(19); **surface-only encoders skipped** (exporter feeds byte buffers).
- **Sizing** (`chooseSize`): landscape → width=`maxDim`, portrait → height=`maxDim` (default 720, clamp 240–1920), both rounded **down to ×16** (encoder alignment), min 160.
- **Tuning** (`EncoderConfig`): VBR (`BITRATE_MODE_VBR`), **4 s GOP** (vs old 1 s — keyframes cost ~10× P-frames), resolution-compensated bits-per-pixel table (720p reference, `pow(ref/px,0.22)`, HEVC/VP9 ×0.62), BT.709 limited-range color metadata on API 24+, quality presets Tiny/Small/**Balanced**/High with honest `predictedBytes`/`megabytesPerMinute` for the sheet. Deliberately **never forces profile/level/B-frames** — several chipsets emit unplayable streams when forced.
- **Video loop:** `totalFrames = dur×fps`; per frame: per-layer media time (`playing ? t×speed : pausedMediaMs`, loop-wrap or end-clamp), `Dec.seekTo()` (GPU forward / retriever fallback) → `Compositor.draw` → `YuvWriter.fillInput` → `queueInputBuffer` with `MonotonicPts` PTS.
- **Audio:** each unmuted clip → `AudioDecode.toPcmMono` → `ClipCursor` (composition-timeline cursor: seek/speed/loop) → float mix bus → `Limiter` → AAC chunks; PTS from sample count (`AudioMath.samplesToUs`); encoder-full → chunk retried, never skipped.
- **Muxing:** `MediaMuxer` (MP4/WebM); tracks added on `INFO_OUTPUT_FORMAT_CHANGED`; codec-config buffers never muxed; `BufferInfo.offset/size` → `position/limit` set portably; `muxer.start()` only when all tracks ready.
- **Reliability:** cancel flag; **compat retry** (H.264 + tight 2 s GOP + default rate control) when validation fails; `muxer.stop()` **before** `ExportValidator` (moov box only exists after stop); strict validation = exists + real bytes + framework probe-back (width+duration+audi
...[truncated 11375 chars]