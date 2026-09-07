package com.rehman.ahmedreactionstudio.editor

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.rehman.ahmedreactionstudio.R
import com.rehman.ahmedreactionstudio.core.Project
import com.rehman.ahmedreactionstudio.core.Aspect
import com.rehman.ahmedreactionstudio.util.UI

/**
 * THE STUDIO WORKSPACE — built once, rebuilt on rotation.
 *
 * Canvas-first means exactly one thing here: **the composition owns 100 % of
 * the interface.** There is no top bar, no bottom transport row, no right-edge
 * trigger rail, no landscape source rail, no floating play/record pills, no
 * radial wheel and no coach card. The only thing ever drawn on top of the
 * canvas is the ☰ menu button, plus transient state (the REC readout while
 * something is actually recording, the undo snackbar, the export progress
 * card) and on-demand panels opened *from* the menu.
 *
 * So the tree is three layers deep, in this order:
 *
 *   1. canvasContainer → StageView        (MATCH_PARENT × MATCH_PARENT)
 *   2. overlays that describe state        (empty hint · REC chip · stats HUD)
 *      + the on-demand bottom sheet        (mixer / advanced properties)
 *      + snackbar
 *   3. SidebarView (the menu)              (overlay, GONE until opened)
 *      ☰ menu button                      (above the menu, so a tap closes it)
 *      progress card                       (top of everything, cancellable)
 *
 * Every `lateinit var` the activity still declares gets a real object assigned
 * here — often unattached — so the many legacy call sites (`dock.rebuild()`,
 * `timeLabel.text = …`, `seek.progress = …`) stay crash-safe while none of
 * that chrome is on screen. That is deliberate: the behaviour was kept, the
 * pixels were removed.
 */
object StudioLayoutInjector {

    fun inject(activity: EditorActivity, root: FrameLayout) {
        activity.rootFrame = root
        assignRetainedViews(activity)

        // ── 1 · THE CANVAS — the whole interface, edge to edge ──────────────
        val canvasContainer = FrameLayout(activity)
        canvasContainer.tag = "canvasContainer"
        canvasContainer.setBackgroundColor(Color.rgb(4, 5, 7))
        root.addView(canvasContainer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        activity.stage = StageView(activity)
        activity.stage.host = activity
        canvasContainer.addView(activity.stage, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // ── 2 · transient state overlays (invisible until they have something
        //      to say, so the canvas stays clean while you edit) ─────────────
        buildEmptyHint(activity, root)
        buildRecChip(activity, root)
        buildStatsHud(activity, root)
        buildSheetHost(activity, root)
        activity.buildSnackBar(root)

        // ── 3 · THE MENU — the only navigation in the app ──────────────────
        activity.sidebar = SidebarView(activity)
        activity.sidebar.onStateChanged = { open ->
            if (open) activity.refreshSidebar()
            if (activity.menuButtonReady()) {
                // the ☰ rides along with the panel and ends up just clear of it:
                // one control that both opens and closes the menu, never
                // covering the row the user is about to tap
                activity.menuBtn.animate().translationX(
                    if (open) activity.sidebar.panelWidthPx.toFloat() else 0f
                ).setDuration(220).start()
            }
            activity.applyViewportInsets()
        }
        root.addView(activity.sidebar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        activity.refreshSidebar()

        // ── the single persistent control ──────────────────────────────────
        buildMenuButton(activity, root)

        // ── progress card, above everything (cancellable while it is up) ────
        activity.buildProgOverlay(root)

        // ── insets: the canvas is only ever pushed around by an OPEN sheet ──
        activity.bindMinimalChromeInsets()
        activity.bindTransport()
        activity.syncTopStrip()
        activity.applyViewportInsets()

        // ── first run: no tutorial card — the menu simply opens where the
        //    work starts, on a project that has nothing on it yet ───────────
        if (activity.shouldShowCoach() && activity.proj?.layers?.isEmpty() == true) {
            activity.markCoachDone()
            root.post {
                if (activity.proj?.layers?.isEmpty() == true) activity.sidebar.openTo("add")
            }
        }
    }

    // ═══════════════════════════ the pieces ═══════════════════════════

    /**
     * ☰ — the only button that lives on the canvas. It floats *over* the
     * picture (the canvas is never shrunk for it), sits inside the safe area,
     * and dims to half while a take is playing or recording so it stops
     * competing with what you are watching. Tapping it closes the menu again —
     * it rides along with the panel and ends up clear of its right edge, so it
     * never covers the row the user is reaching for.
     */
    private fun buildMenuButton(activity: EditorActivity, root: FrameLayout) {
        val btn = IconBtn(activity)
        btn.tag = "menuButton"
        btn.id = R.id.sidebar_toggle
        btn.setIcon(R.drawable.ic_menu, Color.WHITE, "Menu")
        btn.contentDescription = "Open the studio menu"
        btn.background = Ic.pill(activity, Color.argb(150, 12, 14, 19), 26f,
            Color.argb(90, 255, 255, 255))
        btn.elevation = UI.dpf(activity, 3f)
        btn.setOnClickListener { activity.toggleSidebar() }
        val size = UI.dp(activity, 52)
        root.addView(btn, FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START).apply {
            leftMargin = UI.dp(activity, 8)
            topMargin = UI.dp(activity, 8)
        })
        activity.menuBtn = btn
    }

    /**
     * The empty canvas explains itself once, then gets out of the way: one
     * low-contrast line (no panel, no card) that fades after a few seconds.
     * The whole layer is also tappable while the project is empty, which is
     * the "no sources yet" entry point — the menu opens on Add.
     */
    private fun buildEmptyHint(activity: EditorActivity, root: FrameLayout) {
        val overlay = activity.emptyOverlay
        // A re-inject (rotation) creates a brand new overlay, and the default
        // visibility of a LinearLayout is VISIBLE — for a project that already
        // has sources that would put a full-screen click-eater over the canvas.
        overlay.visibility = if (activity.proj?.layers?.isEmpty() == true) View.VISIBLE
            else View.GONE
        overlay.orientation = LinearLayout.VERTICAL
        overlay.gravity = Gravity.CENTER
        overlay.isClickable = true
        overlay.isFocusable = true
        overlay.contentDescription = "Open the menu and add your first source"
        overlay.setOnClickListener { activity.emptyHintTap() }
        val hint = UI.label(activity,
            "\u2630  Tap anywhere to add your first source", dim = false, size = 13.5f)
        hint.gravity = Gravity.CENTER
        hint.includeFontPadding = false
        hint.setPadding(UI.dp(activity, 14), UI.dp(activity, 9), UI.dp(activity, 14),
            UI.dp(activity, 9))
        hint.background = Ic.pill(activity, Color.argb(120, 12, 14, 19), 14f,
            Color.argb(60, 255, 255, 255))
        overlay.addView(hint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        // self-cleaning: the hint never waits for the user to notice it gone
        root.postDelayed({
            if (overlay.visibility == View.VISIBLE && overlay.alpha > 0.5f)
                overlay.animate().alpha(0f).setDuration(600).start()
        }, 6500L)
    }

    /** ● REC · tap to stop — present ONLY while something is recording. */
    private fun buildRecChip(activity: EditorActivity, root: FrameLayout) {
        activity.recChip.apply {
            tag = "recChip"
            textSize = 12.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
            setPadding(UI.dp(activity, 14), 0, UI.dp(activity, 14), 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            background = Ic.pill(activity, Color.argb(235, 170, 26, 26), 22f,
                Color.argb(200, 255, 130, 130))
            isClickable = true
            isFocusable = true
            elevation = UI.dpf(activity, 3f)
            visibility = View.GONE
            contentDescription = "Recording indicator — tap to stop"
            setOnClickListener { activity.recChipTap() }
        }
        root.addView(activity.recChip, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, UI.dp(activity, 40),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = UI.dp(activity, 12)
        })
    }

    /** Preview health overlay — off unless the user switched it on in Settings. */
    private fun buildStatsHud(activity: EditorActivity, root: FrameLayout) {
        activity.statsHud.apply {
            tag = "statsHud"
            textSize = 9.5f
            setTextColor(Color.argb(215, 200, 210, 226))
            typeface = Typeface.MONOSPACE
            includeFontPadding = false
            setPadding(UI.dp(activity, 9), UI.dp(activity, 5), UI.dp(activity, 9),
                UI.dp(activity, 5))
            background = Ic.pill(activity, Color.argb(120, 8, 9, 12), 8f,
                Color.argb(40, 255, 255, 255))
            visibility = View.GONE
            maxLines = 4
            contentDescription = "Preview health"
        }
        root.addView(activity.statsHud, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END).apply {
            rightMargin = UI.dp(activity, 8)
            topMargin = UI.dp(activity, 8)
        })
    }

    /**
     * The on-demand panel host (audio mixer sliders, advanced source
     * properties, source dock). It is GONE until a menu row opens it, so the
     * canvas is full-bleed by default and only lifts while a sheet is up —
     * the composition is still never covered.
     */
    private fun buildSheetHost(activity: EditorActivity, root: FrameLayout) {
        activity.panelDivider.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = UI.dpf(activity, 2f)
                setColor(Color.argb(120, 255, 255, 255))
            }
        }
        activity.panelContent.orientation = LinearLayout.VERTICAL
        activity.panelContent.setPadding(0, 0, 0, UI.dp(activity, 10))
        activity.panelScroll.removeAllViews()
        activity.panelScroll.addView(activity.panelContent, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        activity.sheet.apply {
            tag = "bottomSheet"
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    UI.dpf(activity, 20f), UI.dpf(activity, 20f),
                    UI.dpf(activity, 20f), UI.dpf(activity, 20f),
                    0f, 0f, 0f, 0f)
                setColor(Color.argb(250, 13, 15, 20))
            }
            elevation = UI.dpf(activity, 8f)
            removeAllViews()
            addView(activity.panelDivider, LinearLayout.LayoutParams(UI.dp(activity, 48),
                UI.dp(activity, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, UI.dp(activity, 10), 0, UI.dp(activity, 2))
            })
            addView(activity.panelScroll, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            visibility = View.GONE
        }
        root.addView(activity.sheet, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM))
    }

    /**
     * Objects the activity still holds references to. The chrome that used to
     * own them is gone from the canvas, but the code that writes their state
     * (transport clocks, save indicators, the dock) did not change — so they
     * exist, unattached, and the writes are free.
     */
    private fun assignRetainedViews(activity: EditorActivity) {
        activity.emptyOverlay = LinearLayout(activity)
        activity.panelDivider = View(activity)
        activity.panelContent = LinearLayout(activity)
        activity.panelScroll = ScrollView(activity)
        activity.sheet = LinearLayout(activity)
        activity.dockContainer = LinearLayout(activity)
        activity.recChip = TextView(activity)
        activity.statsHud = TextView(activity)
        activity.playBtn = IconBtn(activity)
        activity.timeLabel = TextView(activity)
        activity.durationLabel = TextView(activity)
        activity.seek = SeekBar(activity)
        activity.aspectChip = TextView(activity)
        activity.quickBar = LinearLayout(activity)
        activity.hiddenPill = TextView(activity)
        activity.studioBtn = IconBtn(activity)
        activity.tabBar = LinearLayout(activity)
        activity.transportBar = LinearLayout(activity)
        activity.sourceStripWrap = android.widget.HorizontalScrollView(activity)
        activity.sourceStrip = LinearLayout(activity)
        activity.topBar = LinearLayout(activity)
        activity.quickWrap = android.widget.HorizontalScrollView(activity)
        activity.fullExitBtn = TextView(activity)
        activity.launchRow = LinearLayout(activity)
        activity.sideRail = ScrollView(activity)
        activity.railContent = LinearLayout(activity)
        activity.recordBtn = TextView(activity)
        // NOTE: `activity.wheel` is deliberately LEFT UNINITIALISED. Every
        // radial entry point in the activity is guarded by wheelReady(), so
        // the wheel is not merely off-screen — it does not exist, and the
        // canvas cannot be covered by a ring a user did not ask for.
        // The real dock is bound by EditorActivity.rebindDock() right after the
        // first inject; this stand-in only keeps `dock.rebuild()` crash-safe.
        activity.dock = SourceDock(activity, activity.dockContainer,
            { activity.proj ?: Project("", "", Aspect.R169) }, { null }, { }, { _, _ -> },
            { }, { }, { }, { _, _ -> }, { })
    }
}
