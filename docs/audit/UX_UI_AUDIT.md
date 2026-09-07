# UX / UI Audit — Ahmed Reaction Studio

Audited at `3e27c43`, **after** PR #1 restored the studio chrome. This supersedes the pre-PR-#1 findings in `/UI_USABILITY_AUDIT.md`, which described a gutted "minimal chrome" build that no longer exists.

---

## 1. What changed since the last audit

The previous audit's headline finding — *"~60% of the documented studio UI is stubbed out and invisible"* — is **no longer true**. `StudioLayoutInjector` now attaches the top strip, transport, sources/mixer panels, snackbar and progress overlay. Eleven `vararg Any?` stubs remain (down from ~19), but the surfaces users need are live.

The remaining problems are therefore **quality** problems, not absence problems.

---

## 2. Feedback & destructive actions — the biggest finding

**The undo system was real but unreachable from the surfaces people actually use.**

`showUndoSnack()` existed and worked. But the radial wheel — the primary navigation surface, per the README — called `ctrl.delete()` and `ctrl.toggleVisible()` **directly**. So:

| Action | Path | Feedback before |
|---|---|---|
| Delete a source from the wheel | `RadialMenus.kt:287` | **None at all** |
| Hide a source from the wheel | `RadialMenus.kt:234` | **None at all** |
| Hide from the Sources panel eye | `StudioLayoutInjector.kt:571` | **None at all** |
| Remove from the Sources panel | `removeSelectedSource()` | **None at all** |
| Delete from the advanced sheet | `EditorActivity.kt:1333` | ✅ snack + UNDO |

Only the least-used path told the user what happened. **Fixed:** all five now funnel through `deleteSource()` / `hideSource()`, so "destroy → explain → offer UNDO" has one definition. A CI guard (`validate-ux-guards.py`) fails the build if any ring calls the controller directly again.

**Still open:** ~70 `UI.toast` calls remain in `EditorActivity`. A toast cannot be actioned, cannot be undone, and stacks unreadably. These should migrate to the snackbar (Phase 4).

---

## 3. Touch targets

Measured from the dp constants in source. Material and WCAG 2.5.5 both require 48dp.

| Control | Before | After | Note |
|---|---:|---:|---|
| Wheel rail trigger | 46dp | **48dp** | The most-tapped control in the editor |
| Transport play/pause | 44dp | **48dp** | |
| Record chip | 34dp | **48dp** | Primary action |
| Source dock row | 52dp | **56dp** | Now fits a 48dp target plus padding |
| Dock drag handle | 44dp | **48dp** | |
| Mixer mute/solo | 36×32dp | **48×48dp** | Was the smallest target in the app |
| Mixer strip header | 36dp | **48dp** | |
| `UI.chip` (Home ✕/Copy, aspect, Diagnostics) | 34dp | **48dp** | Used in ~10 places |
| New-project aspect chips | 40dp | **48dp** | |
| Advanced-sheet delete | 44dp | **48dp** | |
| **Canvas resize handles** | 24dp | **28dp** | ⚠️ see below |

### Canvas handles: an honest exception

The canvas handles were raised 24→28dp, **not** to 48dp, and I want to be explicit that this does not meet the guideline.

Eight resize handles plus a rotate knob sit on the perimeter of one box. At 48dp each, adjacent handles overlap on any PiP smaller than roughly 150dp — the user could no longer select a *specific* corner, which is worse than a small target. 28dp is the largest radius that keeps all nine distinguishable. The handles also shrink adaptively for tiny layers (floor raised 10→14dp) so a small PiP is not entirely covered by its own controls.

**Mitigation that already exists:** the layer body itself is a large drag target, and every transform is also reachable from the Arrange ring and the properties sheet at full size. **Recommended (Phase 4):** a "precision transform" mode with numeric position/size fields for accessibility.

---

## 4. Orientation

**Before:** picking a 16:9 canvas called `setRequestedOrientation(SENSOR_LANDSCAPE)` — the phone physically rotated, mid-edit, including for users who deliberately lock rotation. Picking 9:16 slammed it back to portrait.

That conflates two unrelated things: **the aspect of the video you are making** and **how you are holding the phone**. You can perfectly well compose a 16:9 video while holding the phone upright.

**Fixed:** orientation is now `UNSPECIFIED`. The canvas is contain-fitted by `ViewportFit` in either orientation, so every aspect stays fully editable both ways, and rotation is the user's decision. A CI guard blocks reintroduction.

---

## 5. Home screen

| Issue | Before | After |
|---|---|---|
| Thumbnails | `saveThumb()` had **zero callers** — every card was a grey box | Written on editor stop via the shared `Compositor` |
| List performance | `getView` ignored `convertView`, rebuilt ~12 views/bind, spawned a `Thread` per bind | ViewHolder recycling, one shared executor, 4 MB LRU cache, downsampled decode |
| Empty state | Blank screen under the header | Illustrated state + "Create your first project" CTA |
| Per-card ✕ | 34dp delete chip beside an identical-looking Copy chip, inside a row whose job is "tap to open" | **Removed.** One 48dp ⋮ opens the existing Open/Rename/Duplicate/Delete sheet |
| Stale bitmaps | Tag guard only | Tag guard + cache key includes `lastModified` |

**Rationale for removing the ✕:** an irreversible action should not be one mis-tap away from the row's primary gesture, and it should not look identical to a benign one. Nothing became less reachable — delete was already in the long-press menu, and the ⋮ makes that menu discoverable instead of hidden.

---

## 6. Dialogs

Errors used stock `AlertDialog`s — light-on-white system chrome inside a black studio app. Added `themedDialog()`: dark card, accent-coloured confirm, optional cancel. Applied to the new screen-record pre-flight; the remaining stock dialogs (new project, rename, delete, aspect picker) should migrate in Phase 4.

---

## 7. States audit

| Surface | Empty | Loading | Error | Success |
|---|---|---|---|---|
| Home | 🔧 added | n/a | ⚠️ silent load failure | n/a |
| Editor canvas | ✅ hint + tap-to-add | ✅ progress overlay | ⚠️ mostly toasts | ⚠️ toasts |
| Export | n/a | ✅ progress + cancel | ✅ validator message | ✅ snack + share |
| Screen record | n/a | ⚠️ none | 🔧 themed pre-flight | ✅ chip |
| Camera | n/a | ⚠️ none | ✅ busy/denied handling | ✅ |

---

## 8. Remaining UX debt (not addressed here)

1. **TalkBack cannot see the canvas.** `StageView` draws everything itself and exposes no virtual view hierarchy. A blind user cannot select or move a source. This is the single largest accessibility gap and needs `ExploreByTouchHelper` (Phase 4).
2. **~70 toasts** still carry primary feedback.
3. **Naming drift**: "Sources" / "Layers" / "Dock" all refer to the same concept across different surfaces.
4. **Landscape is a rail bolt-on**, not a designed layout; no tablet consideration.
5. **Colour-only state** on several toggles (active = accent tint, no icon/shape change) — fails for colour-blind users.
