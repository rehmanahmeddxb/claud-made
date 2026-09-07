# Landscape Studio Interface — Plan

**Created:** 2026-09-07
**Status:** proposal — no code written yet, awaiting your approval
**Scope:** the studio shell (orientation, radial wheel, source control, audio mixer,
recording, settings/export/download folder). Media pipelines are untouched.

---

## 0. What you asked for (restated so we agree before code)

1. **New project → pick 16:9 → the phone turns to landscape** and opens a
   *landscape studio interface* (not the portrait shell rotated).
2. That studio is driven by a **radial wheel with petals and sub-petals**.
3. It must give one-tap access to: **Add sources · Audio mixer · Recording
   (start/stop) · Settings** (export quality, **download / save folder
   selection**, etc.).
4. Every source process must be **easy and fully controllable**: add, delete,
   mute, hide, lock, reorder, fit, volume, solo, pause.

---

## 1. What already exists in this repo (do not rebuild)

Verified by reading `HEAD`:

| Thing | Where | State |
|---|---|---|
| Radial wheel engine with petals + sub-petals + spring bloom | `editor/RadialWheel.kt` (`PAGE = 8`) | working |
| Ring tree: `Sources · Add · Audio · Light · Canvas · Export · Project` | `editor/RadialMenus.kt` | working |
| Per-source verbs (hide/mute/solo/lock/loop/fit/volume/opacity/delete) via one controller + undo | `core/Sources.kt`, `core/Model.kt` | working |
| Audio mixer view (mute/solo/level per source) | `editor/MixerPanel.kt` | built, **not attached** in minimal chrome |
| Source dock mini-mixer (eye/mute, drag Z-order) | `editor/SourceDock.kt` | built, not attached |
| Composite recording start/stop + REC pill | `EditorActivity.recordButtonTap()`, `recChip` | working |
| Export settings dialog (codec / resolution / quality / fps, sticky prefs) | `EditorActivity.showExportSettings()` | working |
| Saving to a public album | `export/MediaSave.kt` (`ALBUM = AhmedReactionStudio`) | working, **folder is hardcoded** |
| Landscape side rail | `EditorActivity.sideRail` | allocated but `buildSideRail()` is a **no-op stub** |
| Orientation on aspect change | `EditorActivity.applyOrientationFor()` | **deliberately disabled** (BUG-07) — this is why 16:9 does not rotate |

So four real gaps: **rotation is off**, **the landscape rail is a stub**, **the
mixer/dock sheets are orphaned**, **there is no folder picker / settings home**.

---

## 2. Design of the landscape studio

Landscape phone, canvas is the whole screen; chrome floats over the letterbox
margins so the 16:9 composition is never squeezed.

```
┌──────────────────────────────────────────────────────────────┐
│ ▸ Project name        ● REC 00:12          ⛶  ⋮              │  top strip (auto-hides)
│                                                              │
│   ┌───────────────── 16:9 CANVAS (contain-fit) ──────────┐   │
│ S │                                                      │   │
│ O │        [selected source: handles + rotate knob]      │   │
│ U │                                                      │   │
│ R └──────────────────────────────────────────────────────┘   │
│ C                                                        ◉   │  ← wheel hub (draggable,
│ E    ◀◀  ▶/⏸  ▶▶      ●  REC                                 │     bottom-right default)
└──────────────────────────────────────────────────────────────┘
   ↑ left source rail (collapsible): one row per source
```

### 2.1 Left source rail (the OBS "Sources" dock)
Reuses `SourceDock` — finally attached. One row per source, top-most first:

`≡ drag  |  icon + name  |  👁  |  🔇  |  ⏯  |  🔒  |  ⋮`

- tap row = select on canvas; long-press = open that source's radial ring
- `⋮` = delete / duplicate / rename / promote to canvas background / fit mode
- collapsible to a 44dp icon strip so the canvas can breathe
- badges: LIVE / REC / HIDDEN / MUTED / SOLO / LOCK (already in `badgeOf`)

### 2.2 The radial wheel (petals + sub-petals)
Hub sits bottom-right, **draggable and remembered per orientation**; a long-press
anywhere on empty canvas also blooms it at the finger.

```
◉ Studio (root, 7 petals — never pages)
├── Sources ▸  [Add source] + one petal per source
│              └── per-source ring: Select · Hide · Mute · Solo · Lock ·
│                  Fit/Fill · Volume · Opacity · Z-order · Pause · Loop ·
│                  Speed · Advanced… · Delete (danger, undoable)
├── Add ▸       Video · Image · Live camera · Camera take · Screen record · Text
├── Audio ▸     Master level · Mic gain · Mute all · Open Mixer ▸ (full panel)
│              └── per-source: mute · solo · volume
├── Record ▸    ● Start/Stop composite · ◉ Camera take · ▣ Screen record ·
│               Snapshot frame · [recording settings ▸]
├── Canvas ▸    16:9 / 9:16 / 1:1 · Background colour · Fit all · Full canvas
├── Export ▸    Quick export (last settings) · Export settings… · Open folder
└── Settings ▸  Export quality · Save/download folder · Orientation lock ·
                Stats HUD · Diagnostics · Rename/Save/Close project
```

Change vs today: **Light** folds under the per-camera ring and Settings, and a
dedicated **Record** petal is promoted to root (you asked for recording to be
top-level). Still 7 petals → still never paginates.

### 2.3 Recording bar
A permanent transport strip along the bottom-centre: `◀◀ 10s · ▶/⏸ · ▶▶ 10s ·
● REC`. Red pulsing REC pill top-centre while recording, tap = stop. Destructive
verbs stay locked during recording (already enforced).

---

## 3. Work items (in order)

### P0 — the shell
- **P0-1 Rotate on 16:9.** Replace the disabled `applyOrientationFor()` with an
  honest policy: on **project creation** with 16:9 → open the editor in
  `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`; 9:16 → portrait; 1:1 → unspecified.
  Mid-edit aspect changes only *suggest* a rotation (snackbar "Rotate?"),
  never slam it — that was the BUG-07 complaint. A `Settings → Orientation
  lock: Auto / Follow canvas / Off` switch makes it user-owned.
- **P0-2 Landscape chrome.** Implement `buildSideRail()` for real; `relayoutChrome()`
  switches between landscape (left rail + right wheel hub) and portrait
  (bottom dock) on every config change without losing selection or playback.
- **P0-3 Attach `SourceDock`** into the rail with live eye/mute/lock/⋮ wiring.

### P1 — control surfaces
- **P1-1 Attach `MixerPanel`** as a real right-side sheet in landscape; Audio ring
  "Open Mixer" points to it instead of toasting "being restored".
- **P1-2 Record petal** at root + bottom transport strip with start/stop and timer.
- **P1-3 Per-source ring completion**: volume, opacity, z-order, speed, delete —
  all through `SourceController` so undo/redo keeps working.
- **P1-4 Draggable wheel hub** with per-orientation remembered position.

### P2 — settings & folder
- **P2-1 Settings ring/sheet**: one home for export quality, fps, codec (reusing
  the existing sticky prefs), stats HUD, diagnostics, orientation policy.
- **P2-2 Download folder selection.** `ACTION_OPEN_DOCUMENT_TREE` + persisted
  URI permission, stored in prefs (`PREF_SAVE_TREE`). `MediaSave.publishVideo()`
  gains a "user tree first" branch and falls back to the current MediaStore
  album if the permission was revoked — with the dialog still reporting the
  *real* location, never a lie.
- **P2-3 "Open folder" verb** in Export ring, pointed at the chosen tree.

### P3 — polish
Auto-hiding chrome after 3 s idle, haptics on petal commit, landscape-safe
insets for notches, first-run coach mark on the hub.

---

## 4. Rules I will hold myself to

1. Every verb goes through `SourceController` — no ring mutates a `Layer` directly.
2. Preview == export. No control that only affects preview.
3. Hide ≠ delete; delete always offers UNDO via the snackbar host.
4. Capability-aware UI: no fake buttons (no torch row on a lens with no LED).
5. No box ticked until it is built and checked on device.

---

## 5. What I need from you

- **Rotation model:** hard-rotate on 16:9 project creation (your request) with an
  opt-out in Settings — OK?
- **Wheel vs rail:** keep both (rail = fast list, wheel = power tool), or wheel only?
- **Folder:** SAF tree picker (works on Android 11+, any folder incl. SD card) is
  the plan — confirm.

Say go and I'll start at P0-1.

---

## 6. Execution report — 2026-09-07

Built with the offline toolchain (`./build-apk.sh` → **BUILD OK**) and all four
static validators green (`validate-integration/pipeline/torch/ux-guards`).

| Item | State | Where |
|---|---|---|
| P0-1 rotate on 16:9 | **done** | `EditorActivity.applyOrientationFor()` now follows a `PREF_ORIENT` policy: `canvas` (default → 16:9 = sensor landscape, 9:16 = portrait, 1:1 = free), `auto`, `lock`. Applied on project open and on deliberate aspect change. |
| P0-2 landscape chrome, no overlap | **done** | `StudioLayoutInjector`: bottom transport gets left/right margins equal to the source rail + wheel hub widths, so nothing sits under anything. |
| P0-3 source rail attached | **done** | `SourceDock` rows now live in a permanent left rail (`tag = "sourceRail"`) in landscape: tap select · 👁 · 🔇 · drag Z · long-press advanced · + Add. |
| Canvas never covered | **done** | `refreshViewportInsets()` gained a LEFT inset from the rail; the 16:9 composition contain-fits beside the chrome. |
| Rail count trimmed | **done** | 5 wheel triggers → 4 (Sources · Audio · Record · Studio). The removed Play/Flash were duplicates and made the column tall enough to clip in landscape. |
| Record ring | **done** | `RadialMenus.record()` at root: start/stop composite · play/pause · camera take · screen record · snapshot · Light ▸ · restart. |
| Settings ring | **done** | `RadialMenus.settings()`: export quality · save folder · reset folder · Rotation ▸ · stats HUD · Project ▸ · diagnostics. |
| Download folder | **done** | SAF `ACTION_OPEN_DOCUMENT_TREE` (`REQ_PICK_FOLDER`), persisted in `PREF_SAVE_TREE`, consumed by `MediaSave.publishVideo(..., treeUri)` which writes + verifies bytes and falls back to the public album if the grant was revoked. |
| Full Canvas | **updated** | hides rail, wheel column and dock together. |

### Still open (next pass)
- Draggable wheel hub with remembered position (P1-4).
- `MixerPanel` as a right-side landscape sheet (the Audio ring already gives
  mute/solo/volume/mic gain, so nothing is unreachable).
- Auto-hiding chrome after idle (P3).
