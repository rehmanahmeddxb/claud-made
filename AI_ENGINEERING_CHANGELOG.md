# AI Engineering Changelog

## 2026-09-07 — Canvas-first Studio: the hamburger is the only button

**Scope:** the sidebar shipped by the previous PR was attached but not usable,
and the interface around it was still carrying five generations of chrome. This
rework makes the canvas own 100 % of the Studio and the menu own 100 % of the
controls. Brief + reasoning:
[`docs/STUDIO_CLEAN_CANVAS_PROMPT.md`](docs/STUDIO_CLEAN_CANVAS_PROMPT.md).

- `StudioLayoutInjector` was rewritten. It is now the **only** place chrome is
  created: canvas container (`MATCH_PARENT` × `MATCH_PARENT`) → empty hint →
  REC chip → stats HUD → on-demand sheet → snackbar → **sidebar** → ☰ →
  progress card. The top strip, transport row, tab bar, both rails, the source
  dock rail, the aspect/undo/redo pills, the radial wheel and the coach card
  are not built at all — `FloatingControls.kt` is deleted, and `activity.wheel`
  is deliberately left uninitialised so `wheelReady()` is permanently false and
  a ring can never bloom over the picture.
- The menu is an **overlay**: `refreshViewportInsets()` hands the stage
  `0,0,0,0` and only lifts the bottom edge while a sheet is open, so opening ☰
  never resizes or shifts the composition. `menuBtn.translationX` rides to the
  panel's right edge while it is open so the button never covers a row.
- `SidebarView` was rebuilt around stable ids (section/item paths), so
  expansion state, **Expand all**, search (label + badge + icon name, parents
  kept for context) and "reopen where you left off after rotation" all work.
  `openTo(section, vararg itemIds)` deep-links: long-press a source → that
  source's own branch; canvas tap on an empty project → Add.
- `SidebarTree` is now the feature inventory: **10 sections, 21 folders,
  158 rows, 3 levels deep**, including the per-layer
  Volume ▸ ±25/±10 and Light ▸ front/back/both/lens/screen branches that used
  to need the wheel. A row that cannot act is disabled with a reason.
- Every legacy path was re-routed to the menu instead of to dead chrome
  (`openFlashRing`, `openMixerPanel`, `openDockPanel`, `buildSourcesPanel`'s
  Add, `toggleSourcesSheet`, `openAdvancedSheet`'s no-sheet fallback,
  `onTapEmpty`, `onLongPressCanvas`, `showMicGainDialog`), so no verb became
  unreachable when its button was deleted.
- Four real bugs, found only because the whole workspace was re-read; any one of
  them alone explains "the sidebar does not function":
  1. **the screen light was added at the bottom of the root frame**, i.e. *under*
     the stage's opaque letterbox, so the flashlight fallback never glowed. It is
     now added above the stage, re-raising the sheet / menu / snackbar, and is
     explicitly hidden while the stats HUD is up so `setOpaque(false)` cannot
     smear the overlay;
  2. **`buildUi` injected the workspace twice** — first stage, first sidebar and
     every overlay were built twice into one root;
  3. **rotation read `sidebar.isOpen` *after* re-injecting**, which is always
     false for a freshly built `SidebarView`, so the menu snapped shut mid-edit;
  4. **the empty-canvas overlay was born `VISIBLE` on every re-inject**: a
     full-screen click-eater above the canvas for projects that already have
     sources. It is seeded from the project now, and `updateEmptyState()` runs
     after every re-layout.
  Plus the ☰ sitting over the panel header while the menu was open (it now rides
  to the panel's right edge), and a redundant ✕ in that header: one control, not
  two — the ☰, the scrim and Back close the menu.

### Verification performed
- Reproduced the CI toolchain locally; **`build-apk.sh` → `BUILD OK`**
  (1,505,204 B signed APK), no `error:` from kotlinc.
- `tools/validate-integration.py` **rewritten for canvas-first** (102 checks,
  0 fail): it now *requires* the full-bleed canvas and *fails* if
  `topStrip`/`transportRow`/`bottomDock`/`wheelRail`/`sourceRail`/`buildTabBar`
  /`FloatingControls` come back, while keeping every behavioural assertion
  (undo-before-mutate, sheet dismissal, live-camera rules, orientation policy).
- New **`tools/sidebar-tree-test/`** (29 checks, wired into CI): a generated
  headless `Host` builds the real tree, fires **every enabled row** (476
  invocations across four selection states) and asserts (a) no enabled row is
  a no-op, (b) no folder is empty, (c) ids are unique inside a parent,
  (d) nesting reaches three levels, (e) **all 45 mutating verbs of
  `RadialMenus.Host` are reachable from the menu**, (f) badges follow live
  state. Adding a verb without a row now fails CI instead of shipping an
  orphaned feature.
- `onConfigurationChanged` now delegates to `relayoutChrome()` (one re-layout
  path for every shape change) and the rail-only helpers `railWidthPx` /
  `dockBtn` are deleted with the rails they served.
- Existing suites green: pipeline, torch 34, ux-guards (stub budget still 11),
  audio 32, viewport 420, layers 28, step-2 geometry.
- All 35 dex feature symbols required by CI are present in the shipped APK
  (`classes.dex` substring check) — removing chrome did not remove behaviour.
- **No device was available**: the menu's runtime look (touch, overlap,
  rotation feel) is reasoned from geometry + code paths, not observed.

### Deliberately NOT done
- The `RadialMenus` verb inventory and the panel widgets were **not** deleted:
  the menu is checked against them, and CI greps them in the dex. Removing the
  wheel's pixels was the request; removing its capability was not.
- No ✕ button was added to immersive mode, and no second way to open the menu
  (edge swipe, keyboard shortcut, long-press-☰) — the brief says one control.
- No coach card / onboarding overlay: on the first empty project the menu opens
  itself on Add, once.
- Mixer/advanced properties remain a bottom sheet rather than sidebar rows:
  they need sliders and numeric entry, and a 300 dp tree is the wrong widget
  for a drag. They are still only reachable *from* the menu.

## 2026-09-07 — Full audit + Phase 1 (backlog items 1-5)

**Scope:** repository-wide audit (45 Kotlin files, ~17k LOC) followed by the
highest-priority fixes. Audit reports live in `docs/audit/`.

### Verification performed
- Reproduced the CI toolchain locally; **`build-apk.sh` → `BUILD OK`** (1,476,305 B signed APK).
- All existing suites pass: pipeline 81, torch 34, integration 74, audio 32, viewport 420, layers 28, geometry green.
- New `tools/validate-ux-guards.py` (8 guards) passes and is wired into CI.
- All 30 CI dex feature symbols present; 8 new symbols confirmed in the shipped dex.
- **No device was available** — all camera/codec/hardware runtime behaviour remains UNVERIFIED. See `docs/audit/TEST_PLAN.md` Part 2.

### Fixed
- **BUG-01/02/03** Destructive ops (delete/hide) from the radial wheel and Sources panel were completely silent with no undo affordance. All five paths now funnel through `deleteSource()`/`hideSource()`.
- **BUG-04** `ProjectStore.saveThumb()` had zero callers — every Home card was a grey box. Thumbnails now render through the shared `Compositor`.
- **BUG-05** Home `ListView` ignored `convertView` and spawned a thread per bind. Now ViewHolder recycling + single executor + 4 MB LRU cache.
- **BUG-06** Home had no empty state.
- **BUG-07** Aspect change force-rotated the handset mid-edit. Orientation is now the user's choice.
- **BUG-08** Screen recording had no pre-flight; added storage/notification checks + themed dialogs.
- **BUG-09** Deleted layers orphaned their media. Reclamation is **undo-aware** — bytes survive while any undo/redo snapshot references them.
- **BUG-10** Media collision naming produced `clip.mp4_1`; now `clip_1.mp4`.
- **BUG-11** 48dp touch-target pass across rail, transport, dock, mixer, chips.
- **BUG-12** Undo throttle could swallow a distinct edit; coalescing is now kind-aware.
- **BUG-13** CI guard against reintroducing no-op `vararg Any?` stubs.
- **BUG-14** Live camera feed now targets the project aspect.
- **BUG-17** (partial) `ProjectStore.save()` returns success; failures surface to the user.

### Deliberately NOT done
- **BUG-18** `EditorActivity.kt` is 3,290 lines. Splitting it blind, with no UI tests and no device, would produce regressions indistinguishable from these fixes. Sequenced as Phase 2 in `docs/audit/MASTER_IMPROVEMENT_PLAN.md`.
- Global sweep of ~120 silent `catch (_: Exception) {}` — same reasoning.

### Known limitation
Canvas resize handles were raised 24→28dp, **not** 48dp. Nine handles on one box cannot each be 48dp without overlapping on small PiPs. Rationale in `docs/audit/UX_UI_AUDIT.md` §3.
