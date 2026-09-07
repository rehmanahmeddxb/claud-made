# Ahmed Reaction Studio (Kotlin)

A real native Android reaction-video studio written in Kotlin against the
framework APIs (`android.app`, `android.view`, `Camera2`, `MediaCodec`,
`MediaMuxer`, OpenGL-free CPU compositor), packaged as
`com.rehman.ahmedreactionstudio`.

> **Architecture:** OBS-style source controls behind a canvas-first
> interface — see [`docs/OBS_SOURCE_PLAN.md`](docs/OBS_SOURCE_PLAN.md) for the
> source model and [`docs/STUDIO_CLEAN_CANVAS_PROMPT.md`](docs/STUDIO_CLEAN_CANVAS_PROMPT.md)
> for the workspace contract. The composition owns 100 % of the Studio; the one
> control on screen is ☰, and every source verb is a row in the same
> hierarchical menu — reachable, searchable, never buried in a settings screen.
> What you see is exactly what gets exported.

> **Consolidated baseline (2026-09-05):** Phase 2 canvas/selection/audio work
> and the Step 5 editor UI are kept together. See the
> [branch preservation report](docs/BRANCH_CONSOLIDATION_2026-09-05.md) for
> original commit IDs, conflict decisions, regression tests and device checks.

- Animated splash screen (`SplashActivity`) then project home.
- **Canvas-first studio**: the `StageView` fills the window edge to edge — no
  top strip, no bottom transport row, no rails, no floating pills, no radial
  wheel, nothing inset around the picture. The single persistent control is the
  **☰ menu button**; the sidebar overlays the canvas (opening it never resizes
  the composition) and carries everything: sources, transport, audio, record,
  canvas, export, project, settings. Full Canvas / immersive goes one step
  further and hides ☰ too — tap an empty area or press Back to come back.
- **Sources, OBS-style, from the tree**: every source has its own branch
  (👁 hide · 🔇 mute · ⏯ pause · 🔒 lock · fit · opacity · volume · Z-order ·
  advanced properties · duplicate · remove) nested menu → sub-menu →
  sub-sub-menu, and a mini-mixer **Source Dock** opens on demand from that
  branch. A row that cannot act right now is disabled *with a reason*, never a
  dead button. Hide ≠ delete; pause = hold last frame; hidden sources keep
  their audio; solo mutes everything else without destroying state.
- **Fit mode per source**: Fill (cover) or Fit (whole frame, letterboxed) —
  camera takes default to Fit, so a camera is never "cut out" of the canvas.
- **Main canvas first**: an empty project asks what the background is —
  local video, **recorded camera**, **screen recording**, or image; anything
  added afterwards is a PiP. Any source can later be promoted to canvas
  background (☰ → Sources → *source* → Advanced, or Canvas → Background).
- 16:9 / 9:16 / 1:1 canvases (16:9 default) with normalized geometry,
  independent phone orientation and an aspect picker (☰ → Canvas → Aspect
  ratio). A new 16:9 project opens full-screen in landscape with only ☰.
- Canvas gestures: tap select, **double-tap text to edit**, drag with snap,
  8-handle resize, rotate knob, pinch scale+rotate.
- Import **video in any decodable container (MP4, AVI, WebM, MKV, 3GP, MOV)**
  and images; text overlays. Un-decodable files are reported, not crashed on.
- Camera2 fullscreen capture: front/back switch, **hardware flash on any lens
  that has one (front OR back)** + selfie screen-light fallback, zoom slider,
  MP4 recording straight into the project.
- **Screen recording** via MediaProjection (foreground service) as a main
  canvas or a PiP source.
- Loop per source, per-source volume, opacity, z-order, undo/redo, autosave
  + snapshot recovery; destructive operations are locked while exporting.
- **Export codec picker: H.264 MP4, H.265/HEVC MP4, VP8 WebM, VP9 WebM**
  (only codecs the device actually encodes are offered), plus resolution /
  quality / frame-rate choices. AVI has no Android muxer, so it is import-only.

## Canvas & layer geometry

One rule set (`core/LayerFit.kt`) places every layer, and the same
`Compositor` draws the preview and the export, so what you place is what you
get:

- **Main canvas** — the layer box *is* the canvas (1 × 1). How the frame
  fills it is per-source: `fit = fill` cover-crops (full bleed), `fit = fit`
  letterboxes the **whole frame** inside the canvas (camera default — nothing
  gets cut).
- **Overlays / PiP** — the box keeps the *source* aspect ratio, is fitted into
  the reaction-cam area and pinned to a corner, so a portrait camera take is
  never squashed into a landscape sliver. Dragging keeps at least 40 % of the
  layer on canvas.
- **Selection frame (KineMaster/CapCut-style)** — exactly one source is
  selected, and its orange editing frame (border, 8 transform handles,
  rotate knob, label) is drawn around the source's **exact visible bounds**
  — the same `LayerFit.drawnFrame` rect the compositor draws the picture
  into — following position, size, scale and rotation, on 16:9 / 9:16 / 1:1
  and while video plays. It never surrounds the empty letterbox of a
  `fit`-mode source (no more border around the whole canvas for a portrait
  camera main) and never a second source. Unselected sources keep only a
  very subtle outline. All of it is one `StageView.onDraw` pass — no extra
  Android views, nothing to churn per video frame, nothing exported.
- **Preview decoding (GPU path)** — continuous hardware decode per clip:
  `MediaExtractor` → `MediaCodec` → OES `SurfaceTexture` → GL blit → bitmap,
  same model as MX Player / ExoPlayer (not seek-grab thumbnails). Falls back
  to a cached `MediaMetadataRetriever` only when HW open fails. Adaptive
  resolution keeps multi-layer previews fluid.

## Layout

- `app/src/.../ui` — `SplashActivity`, `HomeActivity`, `DiagnosticsActivity`
- `app/src/.../editor` — `EditorActivity` (canvas-first studio),
  `StudioLayoutInjector` (the ONLY place chrome is created), `SidebarView` +
  `SidebarTree` (the menu: sections → sub-menus → sub-sub-menus, search,
  restore), `StageView` (canvas gestures), `PreviewEngine`, `SourceDock`
  (on-demand mini mixer), `Icons` (vector-icon toolkit). `RadialWheel` /
  `RadialMenus` still compile — the verb inventory the menu is checked against —
  but nothing attaches them, so the wheel can never cover the canvas.
- `app/src/.../camera` — `CameraActivity` (Camera2 + MediaRecorder, crash-safe)
- `app/src/.../capture` — `ScreenCaptureService` (MediaProjection screen record)
- `app/src/.../export` — `Exporter` (H.264/H.265/VP8/VP9 MediaCodec pipeline)
- `app/src/.../core` — project model, `SourceController` (command layer),
  store, media probes, undo stack
- `res/drawable/ic_*.xml` — Material-style vector icon set
- `tools/sidebar-tree-test/` — fires every menu row headlessly and asserts every
  committed verb is reachable from the menu (the canvas-first completeness proof)
- `build-apk.sh` — dependency-free offline builder (aapt2 → R class →
  kotlinc → d8 → apksigner)
- `.github/workflows/android.yml` — CI that builds, verifies and uploads the APK

## Building

Prerequisites on the build host (the script tolerates env overrides, see header):
a JDK 17+, a `kotlinc`, an API-30 `android.jar`, `aapt2`, `d8.jar` and
`apksigner.jar`. Point `TC_ROOT`/`KOTLINC`/… at them or use the layout the
CI workflow assembles under `/tmp/ahmed-tc`.

```sh
./build-apk.sh
# artifacts/AhmedReactionStudio-1.0.0.apk  (signed, installable on Android 8+)
```

On GitHub, push any branch (or run the workflow manually) and download the
`AhmedReactionStudio-1.0.0-apk` artifact from the Actions run.

## Install

Android 8.0+ (min SDK 26). The APK is signed with a generated key — allow
"install unknown apps" when prompted.
