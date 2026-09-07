"""Static merge guards for the canvas-first Studio workspace.

The Studio ships ONE rule: the composition owns 100 % of the interface and the
☰ menu is the only navigation. `StudioLayoutInjector.inject()` therefore builds
just the stage, the transient state overlays, the on-demand bottom sheet host,
the `SidebarView` menu and the ☰ button — no top strip, no transport row, no
right-edge radial triggers, no landscape source rail, no floating pills, and
no attached radial wheel. The verbs that chrome used to own live in
`SidebarTree` (Section → Sub-menu → Sub-sub-menu), whose leaves call back
through `RadialMenus.Host`, and `RadialMenus.kt` stays compiled as the verb
library that the menu is written against.

These guards assert BOTH halves of that contract, which is what makes them
useful: they fail if a control is orphaned (a verb no surface can reach), and
they fail if someone bolts persistent chrome back onto the canvas.

These check integration wiring, not Android runtime behaviour. Real inset,
rotation, gesture and recording smoke tests still require an Android device.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/com/rehman/ahmedreactionstudio"
editor = (SRC / "editor/EditorActivity.kt").read_text()
injector = (SRC / "editor/StudioLayoutInjector.kt").read_text()
radial = (SRC / "editor/RadialMenus.kt").read_text()
sidebar = (SRC / "editor/SidebarView.kt").read_text()
tree = (SRC / "editor/SidebarTree.kt").read_text()
errors = []
passed = []

# Everything inject() actually puts on screen, as opposed to the retained
# (unattached) view objects it hands the activity for crash-safety.
def inject_body(text):
    i = text.index("    fun inject(activity: EditorActivity, root: FrameLayout) {")
    j = text.find("\n    }\n", i)
    return text[i:j]

INJECT = inject_body(injector)


def check(name, condition):
    (passed if condition else errors).append(name)


def method(name, prefix="private fun"):
    start = editor.find(f"    {prefix} {name}(")
    if start < 0:
        errors.append(f"missing {name}")
        return ""
    end = editor.find("\n    }\n", start)
    return editor[start:end]


def contains(text, needle, name):
    check(name, needle in text)


def count(text, needle):
    return text.count(needle)


# ---------------------------------------------------------------- hygiene ---
check("no unresolved editor conflict markers",
      "<<<<<<< " not in editor and ">>>>>>> " not in editor
      and "<<<<<<< " not in injector and ">>>>>>> " not in injector)
check("one toolbar, not the disabled Phase 2 legacy pill", "USE_QUICK_BAR" not in editor)
check("four-side viewport API only", "setChromeInsets" not in editor)

# ------------------------------------------- StudioLayoutInjector: canvas ---
# The canvas-first rule, asserted structurally rather than by eye.
check("stage fills the whole root, not a measured slot",
      'ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))' in INJECT)
check("the injector contains no retained-but-dead chrome builders",
      "private fun pillBtn" not in injector and "fun buildTransport" not in injector)
check("StageView created once", count(injector, "activity.stage = StageView(activity)") == 1)
check("StageView host bound", count(injector, "activity.stage.host = activity") == 1)
check("menu is added over the canvas (z-order = function)",
      INJECT.index("activity.stage = StageView(activity)") <
      INJECT.index("root.addView(activity.sidebar"))
check("the ☰ is the last thing under the progress card",
      INJECT.index("root.addView(activity.sidebar") <
      INJECT.index("buildMenuButton(activity, root)") <
      INJECT.index("activity.buildProgOverlay(root)"))
check("one menu button, tagged for the insets pass",
      count(injector, 'btn.id = R.id.sidebar_toggle') == 1 and 'tag = "menuButton"' in injector)
check("the menu button drives the sidebar, nothing else",
      count(injector, "activity.toggleSidebar()") == 1)
check("sidebar is hidden until opened (can never eat a canvas touch)",
      "visibility = View.GONE" in sidebar and "GONE" in sidebar)
check("the radial wheel is never instantiated in the chrome",
      "RadialMenuView(activity)" not in INJECT)
check("no bottom sheet host is added twice", count(injector, 'tag = "bottomSheet"') == 1)
check("sheet starts hidden (on-demand only)",
      "visibility = View.GONE" in injector[injector.index("buildSheetHost"):])
check("transport seek object exists exactly once (state-safe legacy writes)",
      count(injector, "activity.seek = SeekBar(activity)") == 1)
check("recording readout exists and is hidden until recording",
      'tag = "recChip"' in injector and "buildRecChip(activity, root)" in INJECT)
check("the empty canvas explains itself without a card",
      "buildEmptyHint" in INJECT and "shouldShowCoach()" in INJECT)

# REMOVED chrome: persistent controls must not come back onto the canvas.
for tag, why in (
    ('tag = "topStrip"', "a top strip steals canvas height"),
    ('tag = "transportRow"', "a persistent transport row steals canvas height"),
    ('tag = "bottomDock"', "a persistent bottom dock steals canvas height"),
    ('tag = "wheelRail"', "the radial trigger rail is replaced by the menu"),
    ('tag = "sourceRail"', "the landscape source rail is replaced by the menu"),
    ("FloatingControls", "floating canvas pills are replaced by the menu"),
    ("buildTabBar", "the tab bar is replaced by the menu"),
):
    check(f"no {tag.strip(chr(34))} anywhere in the workspace", tag not in injector)

# Panels still reachable from the menu, not deleted with the chrome.
check("sources sheet builder exists", "fun buildSourcesPanel(" in editor)
check("mixer sheet builder exists", "fun buildMixerPanel(" in editor)
check("menu reaches the source dock / mixer sheets",
      "h.openDockPanel()" in tree and "h.openMixerPanel()" in tree)

# ----------------------------------------------------------------- the menu ---
# Every verb the removed chrome used to own must be reachable from the tree,
# otherwise the redesign silently orphaned a feature. This is derived from the
# Host interface itself, so adding a verb without wiring it fails CI.
host_body = radial[radial.index("interface Host {"):]
host_body = host_body[:host_body.index("\n    }")]
verbs = sorted(set(__import__("re").findall(r"fun (\w+)\(", host_body)))
# openFlashRing is the wheel-era entry point; its implementation now opens the
# menu at the Light branch, so it is intentionally not a row of its own.
ALLOW_UNWIRED = {"openFlashRing"}
unwired = [v for v in verbs if f"h.{v}(" not in tree and v not in ALLOW_UNWIRED]
check(f"every Host verb is reachable from the menu ({len(verbs)} verbs)", not unwired)
if unwired:
    errors.append("orphaned verbs (no menu row): " + ", ".join(unwired))

for needle, name in (
    ("h.saveNow()", "menu Save is wired"),
    ("h.quickExport()", "menu Export is wired"),
    ("h.openExportPanel()", "menu export settings is wired"),
    ("h.openDiagnostics()", "menu diagnostics is wired"),
    ("h.addVideo()", "menu add-video is wired"),
    ("h.addImage()", "menu add-image is wired"),
    ("h.addCameraLive()", "menu live camera is wired"),
    ("h.addTextSource()", "menu text overlay is wired"),
    ("h.addScreen()", "menu screen record is wired"),
    ("h.undo()", "menu undo is wired"),
    ("h.redo()", "menu redo is wired"),
    ("h.hideSource(l)", "menu hide is wired (undo snack path)"),
    ("h.deleteSource(l)", "menu delete is wired (undo snack path)"),
    ('h.ctrl.moveZ(l.id, "up")', "menu move-up Z-order is wired"),
    ('h.ctrl.moveZ(l.id, "down")', "menu move-down Z-order is wired"),
    ("h.ctrl.toggleMuted(", "menu mute is wired"),
    ("h.ctrl.toggleSolo(", "menu solo is wired"),
    ("h.ctrl.setVolume(", "menu volume is wired"),
    ("h.ctrl.setOpacity(", "menu opacity is wired"),
    ("h.ctrl.toggleLocked(", "menu lock is wired"),
    ("h.ctrl.toggleLoop(", "menu loop is wired"),
    ("h.ctrl.setFit(", "menu fit/fill is wired"),
    ("h.ctrl.duplicate(", "menu duplicate is wired"),
    ("h.ctrl.setAsCanvasBackground(", "menu background promotion is wired"),
    ("h.setAspect(Aspect.R169)", "menu 16:9 is wired"),
    ("h.setAspect(Aspect.R916)", "menu 9:16 is wired"),
    ("h.setAspect(Aspect.R11)", "menu 1:1 is wired"),
    ("h.toggleMasterPlay()", "menu play/pause is wired"),
    ("h.nudge(", "menu transport stepping is wired"),
    ("h.restart()", "menu restart is wired"),
    ("h.toggleCompositeRecording()", "menu record is wired"),
    ("h.setOrientPolicyByName(", "menu rotation policy is wired"),
    ("h.pickSaveFolder()", "menu save folder is wired"),
    ("h.toggleStatsHud()", "menu stats overlay is wired"),
    ("h.enterFullCanvas()", "menu immersive canvas is wired"),
    ("h.closeProject()", "menu close project is wired"),
):
    check(name, needle in tree)

# the tree must really be three levels deep (menu → submenu → sub-sub-menu)
check("sections nest at least three levels",
      tree.count("F(R.drawable.") >= 8 and "children = lightItems" in tree)
check("per-source branch is keyed by layer id (state survives rebuilds)",
      '"layer." + l.id' in tree)
check("destructive rows route through the host, never the controller",
      "h.ctrl.delete(" not in tree and "h.ctrl.toggleVisible(" not in tree)

# ------------------------------------------------- EditorActivity wiring ---
# Rotation (and the first layout) must delegate to the injector, and the
# re-layout path must never bounce engine/camera lifecycle.
relayout = method("relayoutChrome")
contains(relayout, "StudioLayoutInjector.inject(this, rootFrame)",
         "rotation re-layout delegates to the Studio injector")
for dangerous in ("startLiveCamera(", "engine.attach(", "engine.release(", "engine.pauseAll("):
    check(f"rotation does not call {dangerous}", dangerous not in relayout)
reconf = method("onConfigurationChanged", "override fun")
contains(reconf, "relayoutChrome()", "configuration change delegates one re-layout path")
contains(reconf, "syncPreviewTarget()", "configuration change re-targets the preview")
contains(reconf, "stage.refresh()", "configuration change refreshes the stage")
contains(reconf, "setSheet(tab)", "rotation restores the open sheet")

# ORDER matters on rotation: inject() builds a NEW SidebarView (closed) and a
# NEW empty-canvas overlay (a LinearLayout is born VISIBLE). A re-inject that
# reads state after the rebuild sees defaults, so these two orders are asserted.
def before(text, first, second, why):
    a, b = text.find(first), text.find(second)
    check(why, a >= 0 and b >= 0 and a < b)


before(reconf, "val wasSidebarOpen", "relayoutChrome()",
       "rotation records the open menu before rebuilding the chrome")
before(reconf, "if (wasSidebarOpen)", "stage.refresh()",
       "rotation re-opens the menu on the fresh chrome")
contains(reconf, "updateEmptyState()", "rotation re-asserts the empty-canvas overlay")
check("the empty overlay is not born visible above the canvas",
      "overlay.visibility = if (activity.proj" in injector)

# First build must go through the injector too (not a second chrome).
build_ui = method("buildUi")
contains(build_ui, "StudioLayoutInjector.inject(this, root)", "buildUi injects the Studio workspace")
contains(build_ui, "setContentView(root)", "buildUi installs the root view")
# Exactly one injection: a second inject() would double every overlay and hand
# the canvas a second stage (the "two sidebars, one dead" failure class).
check("buildUi injects the workspace exactly once",
      count(build_ui, "StudioLayoutInjector.inject(this, root)") == 1)
check("buildUi keeps the window immersive at rest",
      "UI.immersive(this, true)" in build_ui)

# Ticks: transport UI ~20 Hz, HUD ~2 Hz, HUD never in Full Canvas.
tick = method("onTick")
contains(tick, "lastUiTickMs", "transport tick throttled")
contains(tick, "lastHudMs", "HUD tick throttled")
contains(tick, "!fullCanvas &&", "HUD ticks do not escape Full Canvas")

# Selection / sheets keep their refresh wiring.
contains(method("select", "override fun"), "rebuildSourceDock()",
         "selection refreshes the source dock")
adv = method("openAdvancedSheet", "fun")
contains(adv, "rebuildSourceDock()", "advanced sheet refreshes the source dock")
check("advanced sheet exists and is reachable from the menu",
      "fun openAdvancedSheet(l: Layer)" in editor and "h.openAdvanced(l)" in tree)
contains(method("setSheet", "fun"), "setFullCanvas(false)", "opening a sheet exits Full Canvas")
contains(method("setFullCanvas"), "fullCanvas = on", "Full Canvas state toggles")

# recChip (recording indicator) must respect Full Canvas.
check("recording chip respects Full Canvas",
      "recChip.visibility = if (fullCanvas) View.GONE else View.VISIBLE" in editor)

# Retained main-activity behaviours from the pre-redesign code.
contains(method("applyOrientationFor"), "SCREEN_ORIENTATION_UNSPECIFIED",
         "main's aspect-independent phone orientation retained")
contains(method("onDestroy", "override fun"), "removeOnGlobalLayoutListener",
         "main's layout listener cleanup retained")

# ------------------------------------------------- cross-file dependencies ---
for rel, needle in (
    ("editor/StageView.kt", "Compositor.chromeRect"),
    ("editor/PreviewEngine.kt", "LayerType.IMAGE"),
    ("core/Model.kt", "fun placeNewPip"),
    ("export/CompositionRecorder.kt", "ClipCursor"),
    # Row height must FIT a 48dp touch target (BUG-11); it was 52dp and is
    # now 56dp. Pin the intent (>= 56) rather than an exact literal.
    ("editor/SourceDock.kt", "private val ROW_DP = 56"),
):
    text = (SRC / rel).read_text()
    contains(text, needle, f"preserved integration dependency {rel}: {needle}")

# The StageView/PreviewEngine pair must still be created together by the
# injector in both orientations (preview draws through the engine).
check("preview engine referenced by the editor", "engine.attach(" in editor)

for name in passed:
    print("  OK ", name)
for name in errors:
    print("  FAIL", name)
print(f"{len(passed)} checks passed, {len(errors)} failed")
if errors:
    raise SystemExit(1)
print("BRANCH INTEGRATION STATIC VALIDATION OK")
