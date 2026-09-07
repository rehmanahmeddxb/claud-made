# Ahmed Reaction Studio — Full Usability & UI Audit

**App:** Ahmed Reaction Studio (`com.rehman.ahmedreactionstudio`), v1.0.0
**Source audited:** `AhmedReactionStudio-source.zip` (1.5 MB, 151 files, ~28k LOC incl. tools) from `origin/main` @ `1afa218`
**Audit date:** 2026-09-07 · **Auditor:** Arena Agent (static code audit + UX heuristic evaluation)
**Scope:** 100% of user-facing UI — Splash, Home, New Project, Editor, Radial menus, Camera, Screen Capture, Export/Recording, Diagnostics — judged **only on usability & UI** (not codec/pipeline correctness).
**Method:** extracted ZIP → read every UI file (`ui/`, `editor/`, `camera/`, `capture/`, `util/`, `res/`) → mapped navigation, gestures, dialogs, feedback, touch targets, a11y labels → scored against Nielsen's 10 heuristics, Material touch/type/color guidance, and mobile video-editor expectations (CapCut / KineMaster / OBS). No device was available, so this is a static audit; dynamic checks are flagged for on-device verification.

> **One-line verdict:** the engine is genuinely good (single state model, undo, preview==export, capability-aware flash, autosave), but **the shipped editor UI in this ZIP is a gutted "minimal chrome" build** — ~60% of the documented studio UI is stubbed out and invisible. What remains is a canvas plus 5 icon-only radial triggers. That single fact drives almost every critical finding below.

---

## Table of contents

1. [Overall scores](#1-overall-scores)
2. [What was actually audited (inventory)](#2-what-was-actually-audited-inventory)
3. [The #1 finding: minimal chrome vs documented UI](#3-the-1-finding-minimal-chrome-vs-documented-ui)
4. [Navigation & information architecture](#4-navigation--information-architecture)
5. [Screen-by-screen audit](#5-screen-by-screen-audit)
6. [Task walkthroughs (can users actually do the job?)](#6-task-walkthroughs-can-users-actually-do-the-job)
7. [Radial menu system audit](#7-radial-menu-system-audit)
8. [Canvas gestures audit](#8-canvas-gestures-audit)
9. [Visual design audit](#9-visual-design-audit)
10. [Accessibility audit](#10-accessibility-audit)
11. [Feedback, errors & empty states](#11-feedback-errors--empty-states)
12. [Responsive / orientation / device audit](#12-responsive--orientation--device-audit)
13. [Microcopy audit](#13-microcopy-audit)
14. [Prioritized recommendations](#14-prioritized-recommendations)
15. [Quick wins (≤1 day each)](#15-quick-wins-1-day-each)
16. [Suggested target IA (portrait)](#16-suggested-target-ia-portrait)
17. [On-device verification checklist](#17-on-device-verification-checklist)
18. [Appendix A — full issue list](#appendix-a--full-issue-list)
19. [Appendix B — files reviewed](#appendix-b--files-reviewed)

---

## 1. Overall scores

| Dimension | Score /10 | Grade | One-line reason |
|---|---|---|---|
| First-time learnability | **2** | F | Icon-only rail, no onboarding, empty hint mismatches icon, radial-only navigation |
| Everyday efficiency (returning user) | **4** | D | Frequent jobs buried 2–4 taps in rings; no persistent transport/undo/record |
| Task completion (can you finish a reaction video?) | **4** | D | Core path works via wheels, but seek, export settings, mixer sliders, stop-recording chip are missing/invisible |
| Navigation & IA | **3** | F | No persistent nav; Sources/Layers/Dock naming drift; dead "Test" wheel; sheets built but never shown |
| Visual design & consistency | **6** | C+ | Coherent dark + orange system, good vector icons; but raw dialogs, tiny type, spacing drift |
| Feedback & error handling | **5** | C− | Good capability-aware flash + autosave + recovery; but 85 toasts, destructive ops mostly toast-only |
| Accessibility | **3** | F | 37 labeled controls (improving) but <48dp targets everywhere, canvas chrome is TalkBack-invisible, color-only states |
| Responsiveness / orientation | **4** | D | configChanges avoids restarts (good) but forced rotation on aspect change; rail overlaps canvas; no tablet plan |
| **Overall usability** | **3.8** | **D+** | Engine B+, UI D. Fix the chrome regression and this jumps to B− without touching pipelines |

**How to read this:** a D+ does not mean "rewrite the app." It means "restore the UI you already designed." The docs (`UI Plan2.md`, `docs/UI_AUDIT_REPORT.md`, `STEP5_REPORT.md`) describe a substantially better editor than what this ZIP renders. The fastest path to a B is to **re-wire the existing panels, not invent new ones**.

---

## 2. What was actually audited (inventory)

### Screens & surfaces that exist in code

| # | Surface | File | Actually visible in this build? |
|---|---|---|---|
| S0 | Splash (animated brand) | `ui/SplashActivity.kt` (240 lines) | ✅ Yes |
| S1 | Home (project list) | `ui/HomeActivity.kt` (350 lines) | ✅ Yes |
| S2 | New-project dialog | `HomeActivity.showNewDialog()` | ✅ Yes (raw AlertDialog) |
| S3 | Editor canvas | `editor/StageView.kt` (750 lines) + `StudioLayoutInjector` | ✅ Yes — fullscreen |
| S4 | Right-edge wheel rail (5 triggers) | `StudioLayoutInjector.inject()` | ✅ Yes — the ONLY editor chrome |
| S5 | Radial menu overlay | `editor/RadialWheel.kt` (526) + `RadialMenus.kt` (513) | ✅ Yes |
| S6 | Empty-state hint | `StudioLayoutInjector` (1-line label) | ✅ Yes (minimal) |
| S7 | Back button (top-left) | `StudioLayoutInjector` | ✅ Yes |
| S8 | Camera (fullscreen recorder) | `camera/CameraActivity.kt` (780) | ✅ Yes (separate activity) |
| S9 | Diagnostics | `ui/DiagnosticsActivity.kt` (204) | ✅ Yes |
| S10 | Progress overlay / Snackbar | `EditorActivity.buildProgOverlay/buildSnackBar` | ⚠️ Built only if called; `buildUi()` no longer calls them — **likely never added to root** |
| — | Top bar (back, name, aspect, undo/redo, gear) | `EditorActivity.buildTopBar()` | ❌ **Stubbed to `{}` — invisible** |
| — | Transport bar (play, time, seek, duration) | `buildTransportBar()` | ❌ **Stubbed — invisible** |
| — | Quick Control Bar (per-source pill) | `refreshQuickBar()` | ❌ **Stubbed — invisible** |
| — | Bottom tab bar (Layers/Add/Audio/Text/Export) | `buildTabBar()` | ❌ **Stubbed — invisible** |
| — | Bottom sheet + Sources/Mixer/Export panels | `buildSheet/buildSourcesPanel/buildMixerPanel/buildExportPanel` | ❌ **Stubbed — invisible** |
| — | Source dock list | `rebindDock()` creates unattached container | ❌ **Built but never attached — invisible** |
| — | Advanced ("All settings") sheet | `openAdvancedSheet()` builds into unattached `panelContent` | ❌ **Built but never shown** |
| — | Record button / rec chip / stats HUD / hidden pill | Initialized as unattached views | ❌ **Invisible** |
| — | Side rail / Full-canvas exit | `buildSideRail/buildFullCanvasExit/setFullCanvas` guards on unattached views | ❌ **Inert** |
| — | `SourcesPanel`, `MixerPanel`, `ControlsPanel` classes | 208/206/168 lines, well-built OBS docks | ❌ **Never instantiated in `inject()` — dead code in this build** |

### Counts that matter

- **85 toast call sites** (`UI.toast`) vs **1 snackbar helper** (`showUndoSnack`, wired only to source-delete).
- **37 `contentDescription` assignments** (up from 0 in the old audit — real progress) but concentrated in panels that are currently invisible.
- **5 radial triggers**, **0 with text labels** (icon-only), **1 dead** ("Test" → "Nothing here yet").
- **16 AlertDialogs** (all raw platform dialogs, none themed as sheets).
- **0 onboarding / coach-mark / help surfaces.**
- **Touch targets below 48dp:** rail 46dp, close 40dp, dock eyes 44dp, mixer mini-chips 36×32dp, canvas handles ~20dp visual / 24dp hit, default `IconBtn.sized()` = 42dp.

---

## 3. The #1 finding: minimal chrome vs documented UI

`StudioLayoutInjector.inject()` (269 lines) opens with this comment:

> MINIMAL CHROME: fullscreen canvas + 5 radial-wheel triggers. Every lateinit var EditorActivity declares still gets a real (but often invisible/unattached) object assigned here, so any existing call site … stays crash-safe even though none of those legacy panels are shown anymore.

And `EditorActivity.kt` (2,746 lines) confirms it — these are all empty stubs:

```kotlin
private fun buildTopBar(vararg args: Any?) { }
private fun buildSheet(vararg args: Any?) { }
private fun buildTabBar(vararg args: Any?) { }
private fun buildTransportBar(vararg args: Any?) { }
private fun buildSourcesPanel(vararg args: Any?) { }
private fun buildMixerPanel(vararg args: Any?) { }
private fun buildExportPanel(vararg args: Any?) { }
private fun refreshQuickBar(vararg args: Any?) { }
private fun updateEmptyState(vararg args: Any?) { }
private fun updateHiddenPill(vararg args: Any?) { }
```

**Consequences (each is a P0 on its own):**

1. **No transport.** No play button, timecode, seekbar, or duration anywhere persistent. The only playback control is Play/Pause inside the Play wheel. Scrubbing to time a reaction is impossible.
2. **No undo/redo affordance.** Exists only as 2 petals inside Project wheel (2+ taps, must know it exists).
3. **No project identity.** No title, no aspect indicator, no save state in the editor. Users can't confirm which project is open.
4. **No mixer sliders.** `MixerPanel` (with real sliders) is dead code; the Audio wheel offers only ±10% stepped taps. Fine gain riding is impossible.
5. **No export settings UI.** `buildExportPanel` is empty, so Export → "Export settings…" renders an empty sheet (title + ✕ only, and even that on an unattached container). Codec/resolution/quality/fps choices from the README don't exist in the UI. Only Quick Export 720p30 H.264 works.
6. **No source list.** The dock is unattached; the only source list is Sources wheel (radial, paged, no drag-reorder, no persistent visibility).
7. **No recording indicator.** `recChip` ("● STOP …") and `recordBtn` are unattached. During a composite or screen recording there is no in-editor stop surface — users must find the notification (screen) or re-open the Play wheel (composite). Screen-record stop chip is documented in toasts ("tap the top chip to stop") but the chip doesn't exist.
8. **No stats HUD, no hidden-sources pill, no full-canvas mode.** All reference unattached views.
9. **Progress overlay & snackbar likely never attached.** `buildUi()` only calls `inject()`; nothing calls `buildSnackBar(root)` / `buildProgOverlay(root)` anymore (verify on device — if missing, export progress and undo-snackbar are also invisible, leaving toasts as the only feedback).
10. **README/docs describe a different app.** Users and reviewers following the docs will look for tabs, quick bar, dock, and export picker that aren't there. This is a trust-breaking discrepancy, not just a missing feature.

**Recommendation:** treat this as a P0 regression, not a design direction. Either (a) restore the Step-5/PR-24 chrome (top bar + transport + tabs + sheets) and keep the 5-wheel rail as power shortcuts, or (b) if minimalism is intentional, finish it honestly: delete the ~1,500 lines of dead panel code, remove the docs that promise them, and design the missing essentials (transport, undo, export settings, recording indicator) as first-class minimal surfaces. Shipping half of each is the worst of both.

---

## 4. Navigation & information architecture

| Check | Verdict | Detail |
|---|---|---|
| Persistent navigation | ❌ Fail | No bottom bar, no top bar. The 5-icon rail is the only persistent UI and it has no labels, no active states, no badges (except none — recording state isn't reflected on the rail). |
| Back behavior | ⚠️ Partial | `onBackPressed`: wheel → pop, fullCanvas → exit, sheet → close, else save + finish. Sensible order, but with sheets unattached the middle steps are dead; Back from editor always exits (autosave mitigates data loss). No "unsaved changes" nuance needed thanks to autosave — good. |
| Deep-linking / entry points | ⚠️ Partial | Home → Editor → (Camera | Diagnostics | System picker | Notification for screen rec). Camera is a separate activity (good separation). Screen-rec stop lives outside the app (notification) — acceptable only if the in-app chip existed as backup; it doesn't. |
| Naming (one concept, one word) | ❌ Fail | "Sources" (wheel/panel) vs "Layers" (sheet title, code `Layer`) vs "Dock" (code `SourceDock`, old ring) = 3 words for one list. "Fill" = cover-crop AND "Fill canvas" anchor AND "Fill: crop to box" toggle. "Rename" = project rename AND layer rename. "Record" = composite record AND camera-take record AND screen record. Pick one term per concept (§13). |
| Feature discoverability | ❌ Fail | Zero onboarding, zero coach marks, zero help. Icon-only rail + hidden long-press + hidden pinch + paged rings = new users must guess. The empty hint says "Tap ⊕ Sources" but the rail shows a layers icon, not ⊕ — the one hint points at a control that doesn't look like the hint. |
| Dead ends & placeholders | ❌ Fail | "Test" wheel → "Nothing here yet" disabled petal. Violates the project's own Rule 5 (no dead buttons) and reads as broken to users. |

---

## 5. Screen-by-screen audit

### S0 — Splash (`SplashActivity.kt`)

**What it is:** 2.4s brand intro (pulse rings, springy badge, wordmark, tagline, version) + 320ms fade to Home. Window background themed to avoid white flash — nice craft.

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| S0-1 | Minor | Unskippable 2.7s on every cold start. No tap-to-skip. | Add tap-to-advance (keep animation for delight, respect impatience). 5-line fix. |
| S0-2 | Minor | No `contentDescription` / TalkBack handling; decorative animation not marked `importantForAccessibility=no`. | Mark decorative, label version. |
| S0-3 | Minor | Ignores reduced-motion setting; overshoot + infinite pulses can disorient. | Skip/shorten when `Settings.Global.ANIMATOR_DURATION_SCALE==0` or accessibility reduced-motion. |
| S0-4 | Positive | Themed `windowBackground` (no flash), version shown, clean handoff with `overridePendingTransition(0,0)`. | Keep. |

### S1 — Home (`HomeActivity.kt`) — project list

**What it is:** header (▶ badge, title, subtitle, Diag chip), "stored on device" hint, `ListView` of project cards, full-width "+ New project" button. Long-press card → Open/Rename/Duplicate/Delete sheet (good addition).

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| S1-1 | Major | `ListView` + `BaseAdapter` with **no view recycling** (`getView` inflates a new card every time, ignores `convert`). Will jank with many projects; also no item spacing (cards touch — `divider=null`, no margins). | Migrate to `RecyclerView`, or at minimum reuse `convert` + add 8dp card margins. |
| S1-2 | Major | Destructive ✕ chip sits on every card next to the open target. Confirm dialog mitigates, but mis-tap + muscle-memory "Delete" is a data-loss path with **no undo** (project delete has no snackbar-undo; media is deleted from disk). | Remove per-card ✕; keep delete in long-press menu + add swipe-to-delete with Undo. |
| S1-3 | Medium | Empty state = blank list + button. No illustration, no "what is a reaction project" hint, no sample/template. First-run users stare at white space. | Add empty-state art + 1-line explainer + "Create your first project" affordance (button already exists; add context). |
| S1-4 | Medium | No search / sort / filter. Fine for <10 projects, painful beyond. | Add sort (recent/name) when list >5; search later. Low priority. |
| S1-5 | Medium | "ⓘ Diag" chip label is jargon. Users won't guess it opens hardware diagnostics. | Rename to "Diagnostics" or move under an overflow; keep the good `contentDescription`. |
| S1-6 | Minor | Thumbnails: async + downsampled + tag-guarded (good fix), but no placeholder shimmer and decode happens per-row with no memory cache — scrolling re-decodes. | Add placeholder + LruCache. |
| S1-7 | Positive | Relative timestamps ("2 hours ago"), rename that loads full project before save (no layer loss), duplicate, long-press menu, async thumbs. | Keep all. |

### S2 — New-project dialog (`showNewDialog()`)

Raw `AlertDialog` with EditText + 3 aspect chips.

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| S2-1 | Major | **Blank/whitespace names accepted** on create (`store.create(name, aspect)` with no trim/empty check — rename has the check, create doesn't). Home list can fill with untitled projects. | Trim; reject empty with inline error; keep "My Reaction" default but select-all so typing replaces it. |
| S2-2 | Medium | Default 16:9 applied **after** `show()` → visible chip flicker (all unselected → 16:9 pops in). | Set selection before `show()`. |
| S2-3 | Medium | Chip tap handler is fragile (`chips.values.firstOrNull { it === c }` — looks up by identity instead of using the loop var). Works today, breaks on refactor. | Capture `a` directly in the listener. |
| S2-4 | Medium | Dialog is unthemed platform AlertDialog while the rest of the app is custom dark sheets — visual break. No canvas-size/orientation explainer (users pick "9:16" without knowing it forces portrait). | Theme to match (dark rounded sheet) + 1-line hint per aspect ("16:9 — YouTube landscape" etc.). |
| S2-5 | Minor | Chips are 40dp tall (<48dp). | Bump to 48dp rows. |

### S3 — Editor shell (minimal chrome)

**What it is:** fullscreen canvas + right-edge 5-icon rail + top-left back + radial overlay + 1-line empty hint. That's it.

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| S3-1 | **Critical** | No transport (play/time/seek/duration). Reaction editing without scrubbing is like text editing without a cursor. | Restore a minimal transport: play/pause + time + seek, docked bottom, canvas fits above it. P0. |
| S3-2 | **Critical** | No recording indicator or stop surface in-editor. Toasts reference a "top chip" that doesn't exist. | Persistent REC pill (top-center, pulsing red dot + elapsed + tap-to-stop) whenever `recording \|\| ScreenCaptureService.running \|\| liveCam?.recording`. P0. |
| S3-3 | **Critical** | Rail overlaps canvas (canvas is MATCH_PARENT behind rail; no viewport insets subtracted for rail). What-you-see ≠ what-exports at the right edge; taps near edge hit rail not canvas. | Subtract rail bounds in `applyViewportInsets` / `setViewportInsets` so canvas fits clear of rail. P0. |
| S3-4 | Major | No project name / aspect / save state anywhere. Users can't answer "which project am I in? did it save?" | Minimal top strip: back + name + aspect + save dot. Or put name+aspect in the Sources wheel header AND a persistent mini-label. |
| S3-5 | Major | No persistent undo/redo. Buried in Project wheel. | Floating undo/redo (top-right, appears after first mutation, fades when stack empty) or top-strip buttons. |
| S3-6 | Major | Rail is icon-only, right-edge-only. Left-handed users, small hands, and landscape-with-right-hand-holding-phone all suffer. No labels, no long-press tooltips verified. | Add labels under icons (like `dockBtn`'s 9.5sp labels do elsewhere) or a first-run coach bubble; allow rail side swap in settings; ensure `TooltipCompat` on long-press. |
| S3-7 | Major | "Test" trigger (⭐) opens a wheel with a single disabled "Nothing here yet" petal. Reads as broken. | Delete the trigger until the feature exists. 10-minute fix, high embarrassment-removal value. |
| S3-8 | Medium | Empty hint "Tap ⊕ Sources to add your first camera or video" — but no ⊕ exists; the Sources icon is stacked layers. Text/blind users get a wrong instruction. | Reword to match reality ("Tap the Layers icon on the right…") + arrow/pulse animation pointing at the rail + make hint tappable (tap hint opens Sources wheel). |
| S3-9 | Medium | Back button is 40dp, floating top-left over canvas with no scrim — low contrast over bright video, small target, no label. | 48dp + scrim circle + "Back" tooltip; confirm it autosaves (it does — say so in microcopy on first back: "All changes saved"). |
| S3-10 | Minor | Forced orientation per aspect (`applyOrientationFor`: 16:9→landscape, 9:16→portrait) fights the user and jars on foldables/tablets. | Don't force-rotate; fit canvas in current orientation and let the user rotate. At most, suggest rotation once via snackbar. |

### S4 — Export & recording flows

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| S4-1 | **Critical** | Export settings UI doesn't exist (`buildExportPanel` empty). Codec/resolution/quality/fps from README unobtainable; "Export settings…" opens an empty sheet. | Restore settings sheet (4 rows + estimate + export CTA) as a real bottom sheet; persist prefs (code already reads `PREF_EXP_*` — keep that). P0. |
| S4-2 | Major | `quickExport()` hardcodes quality=1/maxDim=720/fps=30/H264 and calls `warnLiveBeforeExport()` which **returns true and shows a dialog instead of exporting** whenever a live layer is visible — so "Quick export" often doesn't export on first tap. Confusing. | Make the dialog's primary action explicit ("Record instead" vs "Export frozen frame") and remember choice per project; don't make quick-export feel broken. |
| S4-3 | Major | Recording readiness is hidden: `canRecordComposite()` (needs live+clip) only surfaces as an enabled/disabled petal + a disabled "Needs a live camera + a video" petal inside the Play wheel. Users must open the wheel to learn why record is unavailable. | Persistent record readiness: disabled REC pill with reason inline ("Add a camera + video to record"), tap opens guided setup. (This was fixed once per old docs — regressed with minimal chrome.) |
| S4-4 | Medium | Export progress overlay exists in code but (per §3.9) may never be attached; verify. If missing, long exports show nothing cancellable. | Verify on device; ensure determinate progress + cancel + "don't leave" guidance. |
| S4-5 | Medium | Post-export dialog offers View/Share (good) but "View" opens containing folder — correct per code comment, yet label "View" suggests playing the video. | Label buttons explicitly: "Open location" / "Play" / "Share". |
| S4-6 | Positive | Codec filtering by device capability, live-freeze warning (honest about limitations), export-cancel cleans half-files, destructive ops locked during export. | Keep all — this honesty is a differentiator. |

### S5 — Camera (`CameraActivity`)

Fullscreen Camera2 recorder: preview + top bar (Close, title by role, status) + bottom dock (record, switch, torch, zoom slider, timer).

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| S5-1 | Medium | Activity forces `SENSOR_LANDSCAPE` while Home is portrait-locked → jarring rotation on entry/exit. Justified for 16:9 takes (code comment says so) but disorienting for 9:16 portrait-reaction users. | Follow project aspect: landscape for 16:9, portrait for 9:16, sensor for 1:1. |
| S5-2 | Medium | Permission denial just toasts + finishes ("Camera permission is needed…"). No rationale, no settings shortcut. Second denial = user stranded. | Explain-before-ask + "Open settings" action (the rollback doc notes this improvement was lost — re-add it). |
| S5-3 | Minor | Zoom is a bare SeekBar with no value readout, no pinch-to-zoom on preview, no focus-tap affordance documented. | Add % label; consider tap-to-focus + pinch zoom (standard camera expectations). |
| S5-4 | Minor | Timer shows only while recording; no clip-length cap warning, no storage-full pre-check messaging. | Show free-space + max-duration hint pre-record. |
| S5-5 | Positive | Role-aware title ("Record main canvas" vs "Record PiP reaction"), serialized open/close (no double-session crashes), real hardware torch with honest fallback. | Keep. |

### S6 — Screen capture (`ScreenCaptureService` + editor wiring)

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| S6-1 | **Critical** | In-editor stop chip is unattached (see S3-2). Only stop path is the system notification — which users may dismiss or miss. A recording with no visible stop is a panic moment. | Same fix as S3-2: persistent in-editor stop pill. Also make notification non-dismissable while running (if not already). |
| S6-2 | Major | No pre-flight sheet: no countdown, no "everything on screen will be captured" warning, no audio-source note, inconsistent stop labeling (notification vs chip vs toast). | Add 3-2-1 countdown + capture-disclosure + unified "Stop screen recording" label everywhere. |
| S6-3 | Minor | Post-capture file is consumed into project automatically (good) but with a generic "Screen record" name and no "retake / keep" choice. | Name with timestamp; offer keep/retake snackbar. |

### S7 — Diagnostics (`DiagnosticsActivity`)

Capability screen: Android/device/RAM/storage/GL/cameras/torch/codecs/export-default + copy.

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| S7-1 | Minor | EGL version read is bogus (`eglGetCurrentContext` + `glGetString` with no context → usually null/"?"). Shows a "?" row that erodes trust. | Fix with real EGL probe or drop the row. |
| S7-2 | Minor | Wall of `label: value` rows, no sections, no copy button verified in head (check tail), no "share report" for bug reports. | Group into sections (Device / Cameras / Codecs / Storage) + "Copy report" + "Share" buttons. |
| S7-3 | Positive | Hardware-aware (detects, never assumes), torch per-side describe, encoder hw/sw split. Exactly what support needs. | Keep; link it from export-error dialogs ("Why can't I export HEVC? → Diagnostics"). |

---

## 6. Task walkthroughs (can users actually do the job?)

Scored on the **actual minimal-chrome build**, not the docs. Taps counted from editor open.

| Task | Path today | Taps | Verdict |
|---|---|---|---|
| Add first source (video as canvas) | Rail → Sources → Add source → Video file → picker | 4 + system picker | ⚠️ Passable but 4 taps for the most common action; empty hint mislabels the entry |
| Add PiP camera over video | Rail → Sources → Add source → Camera live (grant perms) | 4 + perms | ⚠️ Same; no guidance that first=canvas, rest=PiP (only a wheel subtitle) |
| Mute the video, keep mic | Rail → Audio → [video] → Mute (keepOpen) → scrim to close | 4 | ⚠️ Works; but no persistent mute state visible without reopening wheel |
| Set volume to exactly 80% | Rail → Audio → [clip] → tap +10%/−10% repeatedly from current | 3–8 imprecise taps | ❌ No slider = no precision; each tap re-renders ring under finger |
| Hide a source temporarily | Rail → Sources → [source] → Hide → close | 4 | ⚠️ Works; recovery requires remembering it exists (no hidden pill) |
| Reorder z (bring PiP to front) | Rail → Sources → [source] → Arrange → To front → close | 5 | ❌ 5 taps for a one-tap-need; no drag-reorder (dock dead) |
| Seek to 0:17 to time a reaction | — | ∞ | ❌ **Impossible.** No seekbar. |
| Undo a mistaken move | Rail → … wait, Project wheel isn't on the rail. Sources? No. Play? No. | ∞ | ❌ **Near-impossible.** Undo lives in Project wheel, reachable only via root wheel — but the root wheel has no trigger in minimal chrome! Verify: `RadialMenus.root()` exists but no rail button calls `root()`. Undo/redo, rename, save, canvas settings, and project actions may be **entirely unreachable**. |
| Change canvas 16:9 → 9:16 | — | ∞ | ❌ **Unreachable** (Canvas wheel only via root wheel). Aspect is locked to project creation. |
| Export with last settings | Rail → Sources → … no. Only via Export wheel via root. | ∞ | ❌ **Unreachable** except Quick Export — which itself is in Export wheel via root. **Verify urgently:** if root wheel is untriggerable, there is NO export path at all in this build. |
| Record composite (cam+clip) | Rail → Play → Start recording → (perform) → reopen Play → Stop & save | 4 + reopen | ⚠️ Works if you know; no persistent REC state; must remember to reopen wheel to stop |
| Stop screen recording | Notification shade → tap stop | 2 (outside app) | ⚠️ Works but in-app chip missing; toast misdirects to "top chip" |

> **🔴 Showstopper hypothesis (needs 2-minute on-device confirm):** the rail triggers `sources()`, `audioWheel()`, `playStop()`, `lightRoot()`, `testWheel()` — but **nothing triggers `root()`**, and `root()` is the only parent of **Canvas, Export, and Project** wheels. `openRootWheel()` exists but its caller (`studioBtn`) is reassigned by `inject()` to the Sources trigger. If confirmed, **aspect change, background color, export (all forms), rename, save-now, snapshot, stats toggle, undo/redo, and diagnostics-from-editor are unreachable.** That would make this build unshippable regardless of polish. Either add a 6th "More/Studio" trigger for `root()` (10-minute fix) or wire those wheels into the existing five.

---

## 7. Radial menu system audit

The wheel itself (`RadialWheel.kt`) is well-engineered: live-state lambdas, paging at 8/page, hub-as-back, scrim-to-close, keepOpen toggles, edge-clamping, shrink-to-fit, badges, disabled-with-reason petals, haptics, TalkBack labels on hub+petals. Credit where due — the *component* is good. The *system* around it is not.

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| R-1 | **Critical** | Root wheel unreachable (see §6 hypothesis). 3 of 7 feature areas orphaned. | Add Studio/More trigger or re-home Canvas/Export/Project under the five wheels. P0. |
| R-2 | Major | KeepOpen toggles **re-render under the finger** (`refresh()` rebuilds ring; petal positions can shift as badges change width). Users double-tap and hit a moved petal. | Re-render state in place without re-layout (update labels/badges on existing views) or freeze geometry during a session. |
| R-3 | Major | Paging ("More 1/2") for >8 sources with 7-per-page slices is disorienting in a spatial menu — items move pages as sources are added. | For Sources, switch to a scrollable list sheet beyond 8 (rings are for verbs, lists are for nouns). |
| R-4 | Medium | No petal icons-to-labels redundancy problem — labels are good — but petal size 46–52dp with 2-line labels at small sizes truncates ("Front: no LED — use screen light" will clip). | Max 2 lines with ellipsize + full text in TalkBack + smaller badge text. |
| R-5 | Medium | Scrim tap closes everything with no confirm — mid-flow accidental taps lose wheel position (not data, thanks to autosave, but flow). | Fine as-is for leaves; consider Back-stack memory (reopen to same level) for folders. Low priority. |
| R-6 | Medium | Long-press 460ms on canvas opens wheel at finger — hijacks slow drags, zero affordance. | Raise to 550ms + show press-and-hold ripple + only when movement <8dp; add visible "⋮/◉" affordance per selected source so long-press isn't the only path. |
| R-7 | Positive | Disabled-with-reason petals (no-LED explanations), live badges (REC/LIVE/MUTED/…), hub shows level title+subtitle, spring animations, edge-safe layout. | Keep; extend the disabled-with-reason pattern to every gated action (record readiness, export). |

---

## 8. Canvas gestures audit

`StageView` handles: tap select, tap-empty deselect, drag move + snap, 8-handle resize, rotate knob, pinch scale+rotate, double-tap (text edit only — the dangerous double-tap-hide was correctly removed), long-press wheel.

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| G-1 | Major | Handles are ~20dp visual / 24dp hit — below the 48dp a11y floor and hard to grab on dense canvases. Rotate knob floats above box, tiny. | 48dp hit targets (keep visuals small); add "Reset size/rotation" recovery (exists in code per old docs — verify reachable in minimal chrome; if not, re-add to source wheel). |
| G-2 | Major | Snap is invisible (no guides), clamp is silent (full-bleed drag no-ops with no explanation), hidden/transparent sources untappable with no alternative select path except wheel. | Draw snap guides + edge glow on clamp + toast/hint "source is hidden — show it from Sources" when tapping its last-known area. |
| G-3 | Medium | Pinch scale+rotate is undiscoverable (no hint, no tutorial, no reset). | First-selection coach tip ("Pinch to resize • drag corners • tap ⋮ for more") shown 3 times then never. |
| G-4 | Medium | No multi-select, no nudge (arrow-key/dpad) for precision, no numeric position/size readout. Pixel-perfect PiP placement is luck. | Add dpad/arrow nudge + position readout in source properties (when properties sheet is restored). P2. |
| G-5 | Positive | Double-tap-hide removed (was a data-loss-feeling accident), locked-tap explains + offers unlock, gestures in normalized units (no snap-every-drag bug), selection frame follows visible bounds incl. rotation. | Keep. |

---

## 9. Visual design audit

### What's working (keep)

- **Coherent dark studio system:** BG `#101218` → BG2/BG3 ladder, FG/FG2 text ramp, orange-red `ACCENT #FF5A2C` + amber `ACCENT2`, OK green, DANGER red. Consistent across Splash/Home/Editor/Camera — reads as one product.
- **Real vector icon set:** 50+ `ic_*.xml` Material-style icons, tinted via `Ic.get()`, consistent 22–24dp optical sizing. `IconBtn` press-pulse + haptic is genuinely premium-feeling.
- **Splash craft:** themed window bg, spring badge, pulse rings, letter-spaced wordmark — best-looking screen in the app.
- **Selection chrome:** orange frame + 8 handles + rotate knob + label pill following exact visible bounds incl. rotation — matches CapCut/KineMaster expectations.

### What needs work

| # | Severity | Area | Finding | Recommendation |
|---|---|---|---|---|
| V-1 | Major | Type scale | No scale: 27sp wordmark → 18sp titles → 16/15/14/13/12/11.5/11/10.5/10/9.5sp scattered. 9.5–10sp labels (dock labels, solo note, tagline) are below comfortable mobile reading. | Adopt a 5-step scale (20/16/14/12/10.5 min) + enforce min 11sp for functional text. |
| V-2 | Major | Dialogs | All 16 dialogs are raw platform AlertDialogs — different corner radius, button style, and (on some OEMs) light theme vs dark app. Export warnings, rename, delete-confirm all visually "leave the app." | One themed dialog/sheet builder (dark, 16dp radius, accent primary) used everywhere. |
| V-3 | Medium | Buttons | 4 button grammars coexist: `UI.btn` (Button + gradient), `UI.chip` (TextView pill), `pillBtn` (TextView pill, different radius), raw `Button` in panels (different radius/stroke). Same action looks different per screen. | Unify: Primary pill (accent) / Secondary pill (BG3) / Danger text-button. Delete the rest. |
| V-4 | Medium | Touch/feedback | Buttons lack pressed/ripple states (only IconBtn has press feedback). Chips don't show pressed state. Users can't tell taps registered. | Add ripple/foreground + pressed alpha to all tappables. |
| V-5 | Medium | Spacing | Hardcoded 2/4/6/8/10/12/14/16/20/22/36dp with no grid; card padding 12/10, sheet padding 14/6, rail padding 4/8. Feels hand-tuned per screen, not systematic. | 8dp grid + 3 paddings (8/16/24) + 2 radii (12/20). Lint in review. |
| V-6 | Medium | Contrast | FG2 `#A0A6B4` on BG `#101218` ≈ 7:1 (fine), but dim 11sp hints + 9.5sp labels + disabled petals + white text on ACCENT (3.2:1 — fails for small text) need checking. White-on-orange buttons are borderline. | Darken button text to `#1A0B00` or deepen accent for text; audit all pairs to WCAG AA (4.5:1 text, 3:1 large/UI). |
| V-7 | Minor | Letter-spacing | `letterSpacing 0.26` tagline + 0.06 section headers + 0.02 wordmark — overused; wide tracking at 10sp hurts readability. | Reserve wide tracking for the splash tagline only. |
| V-8 | Minor | Avatar/branding | Home "▶" badge is a text glyph in a tinted box, not the splash gradient badge — two brand marks. Camera "Close" chip uses back icon + text (good) but editor back is icon-only (inconsistent). | One badge component reused; back = icon + "Back" text on first use. |
| V-9 | Note | No light mode | Deliberate dark-only is fine for a video tool (studio apps are dark), but make it explicit (no half-themed light dialogs on some OEMs — force dark theme). | `AppCompatDelegate.MODE_NIGHT_YES` equivalent / theme parent stays dark; test on Samsung/Xiaomi (aggressive theming). |

---

## 10. Accessibility audit

| Check (WCAG / Material) | Status | Evidence |
|---|---|---|
| TalkBack labels on icon-only controls | ⚠️ Partial (37 sites, up from 0) | Rail triggers, dock rows, mixer rows, wheel petals labeled — good. Gaps: Splash (none), Home logo (decorative, unmarked), project-card ✕/Copy have labels (good) but row tap vs chip tap order unverified, canvas handles/knob (custom-drawn, invisible to TalkBack), seekbar (missing entirely), zoom slider (no value announcement). |
| Touch targets ≥48dp | ❌ Fail | Rail 46, close 40, IconBtn default 42, dock eyes/mutes 44, mixer chips 36×32, Home chips 34–40 tall, canvas handles 24 hit, radial petals 46–52 (borderline + label overflow). |
| Focus & keyboard | ❌ Fail | No focus order declared; dpad/Tab users can't reach canvas sources or wheel petals reliably; no keyboard shortcuts (space=play, Z=undo expected in editors). |
| Color-only meaning | ❌ Fail | REC red dot, MUTED/HIDDEN badges (text — OK), but solo/active tint-only states, save-dot (missing UI anyway), flash on/off by label (good — text, not color-only). Mixed; audit each state for text/shape redundancy. |
| Contrast | ⚠️ Needs audit | See V-6. Likely 2–3 failures (white-on-accent, dim hints at small sizes). |
| Reduced motion | ❌ Fail | Splash overshoot/pulses, wheel bloom, sheet overshoot — none check reduced-motion. |
| Text scaling | ⚠️ Risk | `textSize` in sp (good) but fixed-height rows (34–48dp) + `maxLines=1` + no ellipsize in places → large-font clipping likely. Test at 200%. |
| Screen-reader flow for core task | ❌ Fail | Add-source-via-wheel is linearly navigable (OK), but canvas arrange/resize/rotate is pointer-only with no alternative (no numeric controls reachable — advanced sheet unattached). A blind user cannot complete the core job. |

**Minimum viable a11y sprint (P1):** 48dp pass on rail/close/dock/chips → label Splash/decorative → announce recording/export state via `announceForAccessibility` → restore advanced sheet (gives non-pointer path to geometry/audio) → honor reduced motion. Full canvas a11y (explore-by-touch handles) is P2.

---

## 11. Feedback, errors & empty states

| Area | Verdict | Detail |
|---|---|---|
| Toasts (85 sites) | ⚠️ Overused | Correct for transient confirmations ("Saved", "Flash on"). Wrong for: errors (import failed, permission denied, screen-capture unavailable), destructive results (deleted without undo — except source-delete which has the snackbar), and gated actions ("Stop the export first", "Select a source first"). Toasts evaporate; errors need persistence + action. |
| Snackbar with Undo | ✅ Exists, ⚠️ Under-wired | `showSnack/showUndoSnack` is the right pattern and source-delete uses it. Project delete, hide, mute, solo, remove-source, aspect change do not. Wire all 6. |
| Progress | ✅ Good component, ❓ Wiring | `buildProgOverlay` (determinate + cancel) replaces deprecated ProgressDialog — right call. But (a) may never be attached in minimal chrome (§3.9 — verify), (b) export uses it? confirm `runExport` calls `showProgress` (head shows exportCancel wiring — likely yes). Recording shows no elapsed/progress anywhere persistent. |
| Empty states | ❌ Weak | Editor: 1-line hint (mismatched, untappable). Home: blank. Audio wheel: "No audio sources yet" disabled petal (good pattern — extend it). Sources wheel: "Add the first source" folder (good). |
| Errors | ⚠️ Mixed | Honest capability errors (flash, codecs, undecodable import reported not crashed — excellent). But permission denials, missing files, and export failures are toast-only with no recovery action. |
| Save feedback | ✅ Good engine, ❌ No UI | Autosave + snapshot recovery + saveDirty flag exist — but no save indicator is visible in minimal chrome (top-bar meta line is stubbed). Users can't see "● unsaved / ✓ saved". |

---

## 12. Responsive / orientation / device audit

| Check | Verdict | Detail |
|---|---|---|
| Portrait phones | ⚠️ Usable | Canvas + right rail works; rail eats ~56dp of canvas width (unaccounted — see S3-3). Wheel clamps to screen (good). |
| Landscape phones | ❌ Poor | Old design had a right side-rail with tabs/sources/controls; minimal chrome has the same 5-icon rail but canvas is now wide-short — rail overlap is proportionally worse; no landscape-specific transport. Forced 16:9→landscape rotation fires on aspect change (jarring). |
| Tablets / foldables | ❌ Untested | No `sw600dp` layouts, no multi-pane, forced orientation fights large screens, `supportsRtl=false` (excludes RTL locales unnecessarily — check if intentional). |
| Cutout / insets | ⚠️ Partial | `readSystemInsets` + `applyViewportInsets` + `setViewportInsets` engine is thoughtful (system bars + cutout subtracted, breathing margin, postage-stamp fallback). But `inject()` never wires insets for the rail/close button, and full-canvas immersive flags reference stubbed views. Engine good, wiring incomplete. |
| Config change | ✅ Good | `configChanges=orientation|screenSize|…` + re-`inject()` on rotate: no activity restart, camera/decoders/clock survive. Correct for a media app. (Cost: every rotate rebuilds chrome — ensure wheel state/selection survive; `selectedId` persists via field, wheel stack does not — reopening wheel after rotate loses position. Minor.) |
| API 26–36 range | ⚠️ Verify | minSdk 26 (Android 8) declared; immersive flags branch API 30+ vs legacy (good); `requestLegacyExternalStorage` (needed pre-29 only — harmless); MediaStore publish path for 29+ exists (`MediaSave`). Test oldest + newest (edge-to-edge enforcement on 35+ will punish the missing insets wiring). |

---

## 13. Microcopy audit

**Good (keep the voice):** "Front: no LED — use screen light" (honest + actionable), "Solo = only soloed sources are heard (nothing else is changed or lost)" (plain-language reassurance), "Every source shows its whole frame", "Live camera on the canvas" dialog (explains freeze-frame limitation instead of failing silently), "Projects are stored on this device only. No accounts, no cloud." (clear privacy promise).

**Fix:**

| Location | Current | Problem | Suggestion |
|---|---|---|---|
| Empty hint | "Tap ⊕ Sources…" | ⊕ doesn't exist | "Tap Layers on the right to add your first camera or video" + make it tappable |
| Rail (none) | (no labels) | Icon-only | Add labels: Sources · Audio · Play · Light · (delete Test) |
| Test wheel | "Nothing here yet" | Ships a dead end | Delete trigger until real |
| Export warn | "Export frozen frame" / "Start recording" / "Cancel" (3 buttons) | Correct but heavy; neutral+negative+positive order confuses | Primary: "Record instead (recommended)"; Secondary: "Export frozen frame"; Text: "Cancel" |
| Toast | "Stop the export first" / "Locked while exporting" (2 wordings, 3 sites) | Same state, different words | One string: "Export in progress — stop it to make changes" + add Stop action |
| Toast | "Select a source first" | No pointer to how | "Select a source first (tap it on the canvas or in Sources)" |
| Toast | "Recording screen — tap the top chip to stop" | Chip doesn't exist | Fix chip first, then "Recording… tap ■ Stop (top) to finish" |
| Sources vs Layers | Both used | One list, two names | **Sources** everywhere user-facing (OBS term, matches wheel); `Layer` stays code-only |
| Fill | 3 meanings | Ambiguous | Cover ("Fill frame — may crop") vs Fit ("Show whole frame") vs "Set as background" (never "fill canvas") |
| Diag chip | "ⓘ Diag" | Jargon | "Diagnostics" |
| Home delete | "Delete project" + message | Fine, but no undo | Keep confirm; add "Undo" window is impossible post-disk-delete — instead soften: move to trash/rename `.bak` for 7 days (P2) or keep confirm + type-name for large projects |

---

## 14. Prioritized recommendations

### P0 — Ship-blockers (fix before any release/testflight)

| ID | Fix | Why | Effort |
|---|---|---|---|
| P0-1 | **Confirm + fix root-wheel reachability.** If Canvas/Export/Project are orphaned, add a 6th "Studio" trigger for `root()` (or re-home those wheels). | Without this, export/aspect/undo may be literally impossible. | S (hours) |
| P0-2 | **Restore minimal transport**: play/pause + current time + seekbar + duration, docked bottom, canvas fitted above. | Reaction timing without scrub is impossible; it's the #1 editor expectation. | M (1–2 days, `PreviewEngine` already exposes clock/seek) |
| P0-3 | **Persistent recording indicator + stop**: REC pill (top-center) for composite/screen/camera-take with elapsed + tap-to-stop. Fix the lying "top chip" toast. | Recordings with no visible stop = panic + lost takes. | S–M |
| P0-4 | **Subtract rail + pills from canvas viewport** (`setViewportInsets`) so WYSIWYG holds at edges. | Current overlap breaks the app's core promise (preview==export). | S |
| P0-5 | **Restore export settings sheet** (codec/res/quality/fps + estimate + Export CTA) reading/writing existing `PREF_EXP_*`. | README promises it; only-720p30-H264 silently limits every user. | M |
| P0-6 | **Delete the "Test" trigger** (or wire it). | Shipping a dead button reads as broken. | XS (minutes) |
| P0-7 | **Attach progress overlay + snackbar to root** (verify `buildSnackBar/buildProgOverlay` are called in `inject()`); wire export progress + cancel visibly. | Long exports with no UI feel frozen; users force-kill and lose files. | S |
| P0-8 | **Record-readiness pill**: always-visible REC entry showing state (ready / "add camera + video" + tap-to-setup). | Headline feature currently discoverable only inside a wheel. | S |

### P1 — Major (next sprint; the B− push)

| ID | Fix | Effort |
|---|---|---|
| P1-1 | Restore top strip: back + project name + aspect (tap → picker, not blind cycle) + save dot + undo/redo. | M |
| P1-2 | Restore sources list as a real sheet (rows + eye/mute + drag-reorder + status), not just a radial. Rings for verbs, lists for nouns. | M |
| P1-3 | Restore advanced/source-properties sheet (opacity/volume sliders, arrange, anchors, text controls) — also the non-pointer a11y path. | M |
| P1-4 | 48dp touch-target pass (rail, close, chips, dock toggles, mixer chips, petals min). | S |
| P1-5 | Snackbar-with-Undo for project delete (via trash), hide, mute/solo, aspect change; retire those toasts. | S |
| P1-6 | First-run onboarding: 3-slide overlay (Sources → Arrange on canvas → Play/Record/Export) + tappable empty hint + rail coach bubble. | M |
| P1-7 | Screen-record pre-flight (countdown + disclosure + unified stop label) + non-dismissable notification. | S |
| P1-8 | Themed dialog builder; migrate all 16 AlertDialogs; unify button grammar (Primary/Secondary/Danger). | M |
| P1-9 | Home hygiene: RecyclerView + recycling + card margins, remove per-card ✕ (long-press/swipe instead), empty-state art, placeholder thumbs + cache. | M |
| P1-10 | New-project dialog: reject blank names, pre-select 16:9 before show, theme + aspect explainer lines. | S |
| P1-11 | Stop forcing rotation on aspect change; suggest instead. Camera follows project aspect. | S |
| P1-12 | KeepOpen re-render without layout shift (update-in-place); cap Sources ring → list beyond 8. | S–M |

### P2 — Polish & delight (backlog)

- Snap guides + clamp glow + hidden-source tap hint; handle hit ≥48dp; pinch-hint coach tip; dpad nudge + numeric geometry readout.
- Text scaling to 200% without clipping; keyboard shortcuts (Space, Ctrl+Z/Y, Del); focus order; `announceForAccessibility` on record/export state; reduced-motion respect.
- Contrast audit to WCAG AA; 5-step type scale; 8dp grid; pressed/ripple states; one brand badge.
- Sort/search Home; project trash (7-day) or type-to-confirm for large deletes; timestamped take names + keep/retake snackbar.
- Diagnostics sections + copy/share report; link from export errors; drop/fix EGL row.
- Storage-full + max-duration pre-checks for recording; zoom % + tap-focus + pinch in Camera.
- Tablet/foldable layouts; RTL re-evaluation; API 35 edge-to-edge pass.
- Sample project/template on first run ("Try a 2-source demo") — biggest single learnability lever after onboarding.

**Suggested order:** P0-1 (verify first — 2 min) → P0-6 (minutes) → P0-4 → P0-3 → P0-7 → P0-8 → P0-2 → P0-5 → P1-1 → P1-2/P1-3 → P1-6 → rest.

---

## 15. Quick wins (≤1 day each)

1. Delete "Test" trigger. (minutes)
2. Add 6th "Studio" trigger → `root()` (if P0-1 confirms orphan). (hours)
3. Tap-to-skip splash. (minutes)
4. Reject blank project names. (minutes)
5. Reword + tappable empty hint ("Tap Layers…" opens Sources wheel). (hours)
6. Rename "ⓘ Diag" → "Diagnostics". (minutes)
7. Unify "Stop the export first" strings. (minutes)
8. 48dp rail + close button. (hours)
9. Rail labels (or long-press tooltips via TooltipCompat). (hours)
10. Post-export buttons: "Open location / Play / Share". (hours)

---

## 16. Suggested target IA (portrait)

Minimal-chrome-compatible — keeps the 5-wheel soul, adds the missing essentials without restoring the full Step-5 sheet stack:

```text
┌────────────────────────────────────────┐
│ ‹ Back   My Reaction   16:9 ▾    ↶ ↷   │  top strip (48dp, scrim; save dot on name)
├────────────────────────────────────────┤
│                                        │
│                                        │
│            CANVAS (fitted               │
│         clear of rail+pills)           │
│                                        │
│   ● REC 00:12 — tap to stop (if rec)   │  overlays: REC pill top-center; hidden pill if any
│                                        │
├────────────────────────────────────────┤
│  ▶   0:17 ━━━━━●━━━━ 1:04    [Layers]  │  transport dock (bottom; seek + time + list shortcut)
└────────────────────────────────────────┘
   + right-edge rail: Sources · Audio · Play · Light · Studio(more)
   + wheel overlay for all drill-down (unchanged component)
   + bottom sheets ONLY for: source list, source properties, export settings
```

Rules: rail = shortcuts; transport = always visible; sheets = lists/sliders/pickers only; wheels = verbs. Nothing paged that could be scrolled; nothing icon-only that could be labeled.

---

## 17. On-device verification checklist

Static audit can't confirm rendering. On a real phone (1 small + 1 large, portrait + landscape, API 26 + API 35):

- [ ] Editor opens: is there REALLY no top bar/transport/tabs/sheets (confirm minimal chrome)?
- [ ] Can you reach Export, Canvas, Project wheels at all (P0-1)?
- [ ] During composite/screen recording: any REC UI? How do you stop?
- [ ] Export progress visible + cancellable? Post-export dialog buttons?
- [ ] "Export settings…" — empty sheet or crash?
- [ ] Advanced sheet (source ⋮ / properties) — visible?
- [ ] Undo/redo reachable? How many taps?
- [ ] Seek to a timestamp — possible?
- [ ] Rail overlaps canvas content at right edge?
- [ ] Rotate with wheel open / recording / camera live — state survives?
- [ ] TalkBack through add→arrange→record→export — where does it break?
- [ ] 200% font + largest display size — clipping?
- [ ] Reduced motion on — splash/wheel/sheets calm?
- [ ] Deny camera/mic/screen perms twice — recovery path?
- [ ] 20 projects on Home — scroll jank? Thumbs?
- [ ] Dark OEM theme (Samsung/Xiaomi) — dialogs stay dark?

---

## Appendix A — full issue list

 severity: 🔴 Critical · 🟠 Major · 🟡 Medium · 🟢 Minor · 🔵 Positive

| ID | Sev | Screen | Issue | Recommendation |
|---|---|---|---|---|
| MIN-1 | 🔴 | Editor | Top bar / transport / tabs / sheets stubbed; ~60% of documented UI invisible | P0-2/P0-5/P1-1: restore essentials ( §3) |
| MIN-2 | 🔴 | Editor | Root wheel (`root()`) has no trigger → Canvas/Export/Project possibly unreachable | P0-1: verify on device; add Studio trigger |
| MIN-3 | 🔴 | Editor | No seek/transport — reaction timing impossible | P0-2 |
| MIN-4 | 🔴 | Editor | No in-editor recording indicator/stop; toast references nonexistent chip | P0-3 |
| MIN-5 | 🔴 | Editor | Rail overlaps canvas; WYSIWYG broken at right edge | P0-4 |
| MIN-6 | 🔴 | Editor | Export settings sheet empty; only 720p30 H264 available | P0-5 |
| MIN-7 | 🔴 | Editor | Progress overlay + snackbar possibly never attached | P0-7: verify + attach |
| MIN-8 | 🔴 | Screen rec | No in-app stop path; notification-only | P0-3 |
| NAV-1 | 🟠 | Global | No persistent nav; icon-only rail, no labels/states | P1-1 + S3-6 |
| NAV-2 | 🟠 | Global | Sources/Layers/Dock + Fill×3 + Rename×2 + Record×3 naming drift | §13 unify |
| NAV-3 | 🟠 | Global | Zero onboarding/help/coach marks | P1-6 |
| NAV-4 | 🟠 | Editor | Dead "Test" wheel | P0-6: delete |
| S0-1 | 🟢 | Splash | Unskippable 2.7s | Tap-to-skip |
| S0-2/3 | 🟢 | Splash | No a11y label; ignores reduced motion | Mark decorative; respect setting |
| S1-1 | 🟠 | Home | ListView, no recycling, cards touch | RecyclerView + margins |
| S1-2 | 🟠 | Home | Per-card ✕ next to open; delete has no undo | Remove ✕; swipe+Undo / trash |
| S1-3 | 🟡 | Home | Blank empty state | Art + explainer |
| S1-4 | 🟡 | Home | No search/sort | Sort when >5 |
| S1-5 | 🟡 | Home | "ⓘ Diag" jargon | "Diagnostics" |
| S1-6 | 🟢 | Home | No thumb placeholder/cache | Shimmer + LruCache |
| S2-1 | 🟠 | New dlg | Blank names accepted | Trim + reject + select-all default |
| S2-2 | 🟡 | New dlg | Default chip flickers post-show | Pre-select before show |
| S2-3 | 🟡 | New dlg | Fragile chip lookup by identity | Capture loop var |
| S2-4 | 🟡 | New dlg | Unthemed dialog; no aspect explainer | Theme + hints |
| S2-5 | 🟢 | New dlg | 40dp chips | 48dp |
| S3-4 | 🟠 | Editor | No project name/aspect/save UI | P1-1 top strip |
| S3-5 | 🟠 | Editor | Undo/redo buried/unreachable | Floating/top undo-redo |
| S3-6 | 🟠 | Editor | Right-only icon rail | Labels + side swap + tooltips |
| S3-8 | 🟡 | Editor | Empty hint mismatched + untappable | Reword + tap-to-open + arrow |
| S3-9 | 🟡 | Editor | 40dp back, no scrim/label | 48dp + scrim + tooltip |
| S3-10 | 🟢 | Editor | Forced rotation on aspect change | Suggest, don't force |
| S4-2 | 🟠 | Export | Quick-export often shows dialog instead of exporting | Clarify actions + remember choice |
| S4-3 | 🟠 | Record | Readiness hidden in wheel | Always-visible readiness pill |
| S4-4 | 🟡 | Export | Progress/cancel wiring unverified | Verify on device |
| S4-5 | 🟡 | Export | "View" mislabels "open location" | Explicit labels |
| S5-1 | 🟡 | Camera | Forced landscape jars portrait users | Follow project aspect |
| S5-2 | 🟡 | Camera | Perm denial = toast + dead end | Rationale + settings shortcut |
| S5-3 | 🟢 | Camera | Bare zoom slider; no value/pinch/focus | % label + gestures |
| S5-4 | 🟢 | Camera | No duration/storage pre-check | Hint pre-record |
| S6-2 | 🟠 | Screen rec | No pre-flight/countdown/disclosure | Add sheet + countdown |
| S6-3 | 🟢 | Screen rec | Generic name; no keep/retake | Timestamp + snackbar |
| S7-1 | 🟢 | Diag | Bogus EGL row | Fix or drop |
| S7-2 | 🟢 | Diag | Wall of rows; no share | Sections + copy/share |
| R-2 | 🟠 | Wheel | KeepOpen re-layout under finger | Update in place |
| R-3 | 🟠 | Wheel | Sources paging disorients | List sheet beyond 8 |
| R-4 | 🟡 | Wheel | Long labels clip | Ellipsize + TalkBack full text |
| R-6 | 🟡 | Canvas | 460ms long-press hijacks drags | 550ms + ripple + visible affordance |
| G-1 | 🟠 | Canvas | 24dp handle hits; tiny knob | 48dp hits + reset recovery |
| G-2 | 🟠 | Canvas | Invisible snap; silent clamp; hidden untappable | Guides + glow + hint |
| G-3 | 🟡 | Canvas | Pinch undiscoverable | Coach tip |
| G-4 | 🟡 | Canvas | No nudge/readout/multi-select | Dpad + readout (P2) |
| V-1 | 🟠 | Visual | No type scale; 9.5–10sp functional text | 5-step scale, 11sp min |
| V-2 | 🟠 | Visual | 16 raw dialogs break theme | Themed builder |
| V-3 | 🟡 | Visual | 4 button grammars | Unify to 3 |
| V-4 | 🟡 | Visual | Missing pressed states | Ripple/alpha |
| V-5 | 🟡 | Visual | No spacing grid | 8dp grid |
| V-6 | 🟡 | Visual | Contrast risks (white-on-accent etc.) | WCAG AA audit |
| A11Y | 🟠 | Global | <48dp targets; canvas invisible to TalkBack; no focus/keyboard/reduced-motion | §10 sprint |
| FB-1 | 🟡 | Global | 85 toasts incl. errors/destructive | Snackbar+action for errors/gates |
| FB-2 | 🟡 | Global | Undo-snackbar only on source delete | Wire 6 more ops |
| RESP | 🟠 | Global | Landscape/tablet/foldable poor; insets half-wired; API 35 risk | §12 fixes |
| COPY | 🟡 | Global | 9 microcopy fixes (see §13) | Batch pass |

**Positives to protect:** single state model + undo + autosave + snapshot recovery · preview==export compositor · capability-aware flash with honest fallbacks · device-filtered codecs · undecodable-import reporting · selection chrome following visible bounds · IconBtn micro-interactions · relative timestamps + long-press Home menu · determinate progress + cancel component · disabled-with-reason petals + live badges.

---

## Appendix B — files reviewed

All paths under `_extracted/` (unzipped from `AhmedReactionStudio-source.zip`):

- `README.md`, `UI Plan2.md` (728 lines), `kotlin android reaction.md`, `build-apk.sh`, `.github/workflows/android.yml`
- `docs/`: `UI_AUDIT_REPORT.md` (762), `UI_PLAN2_EXECUTION_2026-09-05.md`, `UI_ROLLBACK_2026-09-06.md`, `STEP5_REPORT.md`, `OBS_SOURCE_PLAN.md`, `RADIAL_OBS_AUDIT.md`, `PHASE2_CANVAS_SELECTION_AUDIO.md`, `BRANCH_CONSOLIDATION_2026-09-05.md`, `CRASH_AUDIT_2026-09-05.md`, `PLAYBACK_EXPORT_FIX_PLAN.md`, `P0_PIPELINE_FIX.md`, `STEP4_TORCH_FIX.md`
- `app/AndroidManifest.xml`, `res/values/styles.xml`, `res/drawable/ic_*.xml` (50+ icons), `res/drawable/*panel.jpeg`, `res/mipmap-*`
- `App.kt`, `util/Util.kt` (UI toolkit), `ui/SplashActivity.kt`, `ui/HomeActivity.kt`, `ui/DiagnosticsActivity.kt`
- `editor/EditorActivity.kt` (2,746 — read in full via sections), `StudioLayoutInjector.kt` (269, full), `RadialMenus.kt` (513, full), `RadialWheel.kt` (526, partial-deep), `StageView.kt` (750, partial-deep), `SourceDock.kt` (369, partial), `SourcesPanel.kt` (208, full), `MixerPanel.kt` (206, full), `ControlsPanel.kt` (168, full), `Icons.kt` (115, full), `PreviewEngine.kt` (787, skimmed for UI surface), `LiveCamera.kt` (775, skimmed), `EffectsPanel.kt`, `PropertiesPanel.kt` (stubs)
- `camera/CameraActivity.kt` (780, UI sections), `camera/TorchController.kt` (skimmed), `capture/ScreenCaptureService.kt` (skimmed), `core/*` + `export/*` (skimmed for UI-reachable behavior only)
- Reference mockups: `attached_images/*.jpeg`, `image_*.jpeg` (AI-generated Sources/Audio-Mixer concepts — evaluated as direction, not shipped UI)

---

## Fix log

### 2026-09-07 — P0-1 fixed (root-wheel reachability) + P0-6 (dead "Test" trigger)

**Correction to §6:** code re-read during the fix showed `onLongPressCanvas()` opens `RadialMenus.root()` on empty-canvas long-press (`EditorActivity.kt` ~L1295), so the root wheel was never *literally* unreachable — but a hidden 460 ms long-press with zero affordance is undiscoverable in practice. The P0-1 verdict (no visible path to Canvas/Export/Project) stands.

**Changes (also promotes the ZIP contents to repo root as the source of truth; the ZIP is removed, git history retains the original):**

1. `app/.../editor/StudioLayoutInjector.kt` — rail slot 5 is now **Studio → `RadialMenus.root()`** (`ic_wheel` icon) instead of the dead "Test" placeholder; `studioBtn` now points at it. Fixes P0-1 + P0-6 together.
2. `app/.../editor/RadialMenus.kt` — root ring's **Audio** petal now opens the working `audioWheel()` instead of the unattached mixer sheet (silent no-op before).
3. `app/.../editor/EditorActivity.kt` — new `sheetAttached()` guard (= attached to window, not just initialized) with honest fallbacks that auto-heal when sheets return (P0-5/P1-2/P1-3):
   - `openDockPanel()` → Sources ring + toast; `openMixerPanel()` → Audio ring + toast.
   - `openExportPanel()` → dialog showing last export prefs (codec/res/fps/quality) with **Quick export now** action.
   - `enterFullCanvas()` → toast "You're already viewing the full canvas" (no phantom ✕).
   - `openAdvancedSheet()` ("Advanced…" petal) → source's own ring + toast instead of invisible no-op.
4. `.gitignore` — added `artifacts/` (APK output dir).

**Now reachable with visible taps:** Canvas aspect/background · Quick Export · Export-settings fallback dialog · Rename · Save now · Snapshot · Undo/Redo · Diagnostics · Close project. Still pending (later P0s): transport/seek (P0-2), REC pill (P0-3), rail viewport insets (P0-4), full export sheet (P0-5), progress/snackbar attach (P0-7), record-readiness pill (P0-8).

**On-device verify:** open editor → tap ⭐-replacement Studio trigger (5th rail icon) → root ring blooms → drill into Canvas (change aspect), Export (quick export), Project (undo/redo/rename/diagnostics); open a source ring → "Advanced…" → source ring re-opens with toast (no dead end).

---

*End of audit. Next step: confirm P0-1 on a device (2 minutes), then work P0 in the suggested order. Say the word and I'll start implementing — P0-1 + P0-6 + quick wins first.*
