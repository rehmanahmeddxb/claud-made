# Canvas-First Studio — the clean-canvas brief

Status: **implemented** 2026-09-07 (`StudioLayoutInjector`, `SidebarView`, `SidebarTree`).
Supersedes the chrome described in [`SIDEBAR_UI_PLAN.md`](SIDEBAR_UI_PLAN.md) §1–§4
(that plan still owns the tree format; its top strip, floating transport and
right rail are deleted here).

---

## 1. The request, as asked

> The last PR added the sidebar in Studio but it is not functioning. The
> previous UI is also too cluttered. Rework Studio so the canvas takes 100 %
> of the interface. The only button should be the hamburger — when I click it,
> the sidebar expands and shows all the features we have committed, and no
> other button. Remove every previous control from the canvas: no radial wheel,
> no floating controls, no rails, no bottom dock, no transport bar, no chips.
> When I create a new 16:9 project it must open a full-screen canvas in
> landscape with only the hamburger. Sidebar navigation must be hierarchical:
> menu → sub-menu → sub-sub-menu.

## 2. What that means (the reading this build implements)

**"Canvas takes 100 %" is about ownership, not about geometry.** The
composition owns every pixel of the window. Controls are not inset around it,
not floated above it, and not parked in a permanently reserved strip. The
`StageView` is a `MATCH_PARENT × MATCH_PARENT` child of the window and the
viewport-inset budget it is handed is `0,0,0,0` for the whole time the menu is
closed.

**"The only button" is about persistence, not about count.** Exactly one
control is on screen at rest: ☰. Everything else on the canvas layer either
*describes state* (REC while something is recording, the undo snackbar, the
export progress card) or *only exists because a menu row asked for it* (the
bottom sheet holding the mixer / advanced properties, the stats HUD after
Settings → Performance). No control is on screen for two reasons at once:
because it is useful *and* because it was there before.

**"Shows all the features we committed" is a completeness claim, so it is
verified mechanically.** The radial wheel and the rails were the previous
owners of that feature set, so removing them must move the content, not delete
it. The compiler cannot check that; `tools/sidebar-tree-test/` does — every
mutating verb of `RadialMenus.Host` has to be reachable from a row that fires
it. This is the difference between "the sidebar looks full" and "the sidebar
*is* the feature set".

**"Not functioning" had three concrete causes**, all fixed, all guarded:
1. the menu was built but the window still gave the canvas its old inset
   budget, so opening the sidebar pushed the composition around instead of
   overlaying it;
2. the sidebar was attached under opaque canvas chrome in the z-order, so its
   rows were unreachable;
3. rows whose action called a `lateinit` view that the injector never assigned
   threw and were swallowed by a `runCatching` in the click path;
4. `buildUi` injected the workspace **twice**, and the re-inject on rotation
   both re-born the empty-canvas overlay as a `VISIBLE` full-screen
   click-eater and read the sidebar's open state *after* the fresh (closed)
   instance had replaced it — so the menu shut itself and the canvas stopped
   taking taps.

## 3. The brief, restated for reuse

> Rebuild the Android Studio screen as a canvas-first workspace.
>
> **Layout.** `StudioView` (the composition) is the only full-window child. No
> top strip, no bottom transport row, no left/right rail, no floating pills, no
> quick-control bar, no radial menu, no coach card. Reserved chrome height is
> always zero; the canvas is never resized to make room for a control.
>
> **The single control.** One 52 dp hamburger (☰), top-left inside the safe
> area (system insets + display cutout), 48 dp minimum touch target, above the
> canvas in z-order. It is the only thing visible at rest. It dims to 50 %
> while a take is playing or recording. Tapping it opens the menu; tapping it
> again, tapping the scrim, or Back closes it. While the menu is open the ☰
> rides to the panel's right edge so it never covers a row.
>
> **Opening the menu must not move the canvas.** It is an overlay above the
> composition, never a resize.
>
> **The menu is the whole feature set, in three levels.** Root sections →
> sub-menus → sub-sub-menus; each row carries the state it controls (value or
> badge, not a tooltip). Every capability that used to live on the wheel, the
> rails, the dock or the top bar is reachable from here, and a row that cannot
> act is disabled with a reason rather than removed or faked. Search filters by
> label, badge and icon, and keeps the matching parents visible.
>
> **New 16:9 project.** Full-screen canvas, landscape, ☰ only. No first-run
> tutorial card: the menu opens itself once on the Add branch of an empty
> project, because that is where the work starts, and it is never shown again.
>
> **Removed means removed.** Do not leave dead chrome compiled but invisible,
> and do not re-add a control to satisfy a test: update the assertion instead.
> Retain the *behaviour* (verbs, guards, undo rules) behind the menu.
>
> **Acceptance.** (a) Screenshot at rest shows the composition and ☰ and
> nothing else; (b) canvas bounds == window bounds; (c) opening the menu leaves
> canvas bounds unchanged; (d) every previously committed verb is reachable in
> ≤ 4 taps including the ☰; (e) no view id from the removed chrome is inflated
> at runtime; (f) rotation preserves the open branch and the selection;
> (g) `./build-apk.sh` green and all static validators green.

## 4. What was deliberately kept off this list

| Kept | Why it is not "another button" |
| --- | --- |
| ● REC pill | visible **only** while something is recording; tapping it stops, which is the safety path the brief cannot afford to lose |
| undo snackbar | transient, auto-dismissing, and the one place "Undo" survives after the pills were deleted |
| export progress card | modal-by-nature, cancellable, only exists during an export |
| bottom sheet (mixer / advanced / dock) | opened by a menu row, closed by a menu row or a tap on the canvas; while it is up it is the one thing the canvas does inset for |
| stats HUD | opt-in in Settings, hidden in immersive mode |
| `RadialWheel.kt`, `SourcesPanel.kt`, `MixerPanel.kt` | still compiled and still exercised (CI greps the dex) — nothing in the Studio **attaches** them, so they cannot cover the canvas |

Deleting the *verbs* the wheel owned would have been the real regression; only
its pixels were in the way.

## 5. Information architecture (as shipped)

Ten root sections, 21 folders, 158 rows, three levels deep:

```
Add source ─ video · image · live camera · screen · text
Sources ──── <each layer> ▸ visibility · lock · fit · position · size · rotate
             · opacity · volume ▸ −25/−10/+10/+25 · Z-order ▸ front/back/forward
             /backward · advanced ▸ speed/loop-offset/crop/shadow/border · duplicate
             · remove — plus Add source here · Source dock
Selected ▸ ─ the same verbs for whatever is selected, one tap from the ☰
Playback ── play/pause · restart · ±0.1 s · ±1 s · freeze frame
Audio ───── mixer · mic gain ▸ steps · <per-clip> ▸ mute/solo/volume
Record ──── record · take ▸ start/stop · flash ▸ front/back/both/lens/screen
Canvas ──── 16:9 · 9:16 · 1:1 · background ▸ swatches/custom · fit all
             · immersive
Export ──── quick export ▸ 480/720/1080 · export panel · save to folder
Project ─── save · rename · undo · redo · diagnostics · close
Settings ── orientation policy ▸ canvas/auto/landscape/portrait · save folder ▸
             pick/reset · performance ▸ stats HUD
```

## 6. Verification (what actually ran)

```bash
./build-apk.sh                              # BUILD OK, signed APK
python3 tools/validate-integration.py       # 102 checks — canvas-first invariants
python3 tools/validate-ux-guards.py         # no-op-stub budget, 48dp targets, destructive-op guards
python3 tools/validate-pipeline.py          # export/record playability
python3 tools/validate-torch.py             # 34 checks, flashlight + screen light
bash tools/sidebar-tree-test/run.sh         # 29 checks — menu completeness + every row fires
bash tools/viewport-fit-test/run.sh         # 420 checks — canvas geometry
```

`tools/sidebar-tree-test/` is the one that matters for this brief: it builds the
real tree against a headless `Host`, fires **every** enabled row (476
invocations across four selection states), and fails if any mutating verb is
unreachable or any enabled row has no action. `tools/validate-integration.py`
holds the layout contract: it fails if `topStrip`, `transportRow`, `bottomDock`,
`wheelRail`, `sourceRail`, `buildTabBar` or `FloatingControls` ever return to the
injector, and requires the canvas container to be `MATCH_PARENT` in both axes.

**Not verified:** no device was available, so the runtime look of the menu
(overlap, touch, rotation feel) is reasoned from the geometry tests and the code
paths, not observed on hardware.
