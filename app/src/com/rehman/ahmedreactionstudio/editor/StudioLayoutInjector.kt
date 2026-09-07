package com.rehman.ahmedreactionstudio.editor

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.rehman.ahmedreactionstudio.R
import com.rehman.ahmedreactionstudio.core.Aspect
import com.rehman.ahmedreactionstudio.core.Project
import com.rehman.ahmedreactionstudio.util.UI

object StudioLayoutInjector {

    private fun pillBtn(activity: EditorActivity, text: String, textColor: Int,
                        fill: Int, heightDp: Int = 32, bold: Boolean = true,
                        onClick: () -> Unit): TextView {
        val t = TextView(activity)
        t.text = text
        t.textSize = 12f
        t.typeface = Typeface.create("sans-serif-medium", if (bold) Typeface.BOLD else Typeface.NORMAL)
        t.isAllCaps = false
        t.gravity = Gravity.CENTER
        t.setTextColor(textColor)
        t.includeFontPadding = false
        t.setPadding(UI.dp(activity, 12), 0, UI.dp(activity, 12), 0)
        val g = GradientDrawable()
        g.cornerRadius = UI.dpf(activity, heightDp / 2f)
        g.setColor(fill)
        g.setStroke(UI.dp(activity, 1), Color.argb(70, 255, 255, 255))
        t.background = g
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, UI.dp(activity, heightDp))
        lp.setMargins(UI.dp(activity, 2), 0, UI.dp(activity, 2), 0)
        t.layoutParams = lp
        t.setOnClickListener { onClick() }
        return t
    }

    fun inject(activity: EditorActivity, root: FrameLayout) {
        // Initialize all lateinit fields declared in EditorActivity so call sites remain crash-safe.
        activity.emptyOverlay = LinearLayout(activity)
        activity.playBtn = IconBtn(activity)
        activity.timeLabel = TextView(activity)
        activity.durationLabel = TextView(activity)
        activity.seek = SeekBar(activity)
        activity.aspectChip = TextView(activity)
        activity.quickBar = LinearLayout(activity)
        activity.panelDivider = View(activity)
        activity.panelContent = LinearLayout(activity)
        activity.sheet = LinearLayout(activity)
        activity.dockContainer = LinearLayout(activity)
        activity.recChip = TextView(activity)
        activity.statsHud = TextView(activity)
        activity.hiddenPill = TextView(activity)
        activity.dock = SourceDock(activity, activity.dockContainer,
            { activity.proj ?: Project("", "", Aspect.R169) }, { null }, { }, { _, _ -> }, { }, { }, { }, { _, _ -> }, { })
        activity.studioBtn = IconBtn(activity)
        activity.tabBar = LinearLayout(activity)
        activity.transportBar = LinearLayout(activity)
        activity.sourceStripWrap = HorizontalScrollView(activity)
        activity.sourceStrip = LinearLayout(activity)
        activity.topBar = LinearLayout(activity)
        activity.quickWrap = HorizontalScrollView(activity)
        activity.fullExitBtn = TextView(activity)
        activity.panelScroll = ScrollView(activity)
        activity.launchRow = LinearLayout(activity)
        activity.sideRail = ScrollView(activity)
        activity.railContent = LinearLayout(activity)
        activity.recordBtn = TextView(activity)
        activity.wheel = RadialMenuView(activity)
        activity.floatingControls = FloatingControls(activity)

        activity.rootFrame = root

        // ---- 1. FULLSCREEN CANVAS (100% of studio interface) ----
        val canvasContainer = FrameLayout(activity)
        canvasContainer.setBackgroundColor(Color.rgb(4, 5, 7))
        root.addView(canvasContainer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        activity.stage = StageView(activity)
        activity.stage.host = activity
        canvasContainer.addView(activity.stage, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))

        // ---- 2. FLOATING HAMBURGER BUTTON (☰) - ONLY CONTROL ON CANVAS ----
        val hamburgerBtn = IconBtn(activity).apply {
            id = R.id.sidebar_toggle
            setIcon(R.drawable.ic_menu, Color.WHITE, "Open menu")
            val bgDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = UI.dpf(activity, 24f)
                setColor(Color.argb(200, 18, 20, 28))
                setStroke(UI.dp(activity, 1), Color.argb(120, 255, 255, 255))
            }
            background = bgDrawable
            setOnClickListener { activity.toggleSidebar() }
        }
        root.addView(hamburgerBtn, FrameLayout.LayoutParams(
            UI.dp(activity, 48), UI.dp(activity, 48),
            Gravity.TOP or Gravity.START
        ).apply {
            leftMargin = UI.dp(activity, 16)
            topMargin = UI.dp(activity, 16)
        })

        // ---- 3. COLLAPSIBLE HIERARCHICAL SIDEBAR MENU ----
        activity.sidebar = SidebarView(activity)
        activity.sidebar.onStateChanged = { open ->
            if (open) activity.refreshSidebar()
        }
        root.addView(activity.sidebar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // ---- 4. GENTLE EMPTY-STATE OVERLAY ----
        activity.emptyOverlay.orientation = LinearLayout.VERTICAL
        activity.emptyOverlay.gravity = Gravity.CENTER
        val emptyHint = UI.label(activity,
            "No sources yet — tap ☰ Menu to add your first camera or video",
            dim = false, size = 14f)
        emptyHint.gravity = Gravity.CENTER
        emptyHint.setPadding(UI.dp(activity, 20), UI.dp(activity, 14),
            UI.dp(activity, 20), UI.dp(activity, 14))
        emptyHint.background = Ic.pill(activity, Color.argb(190, 20, 23, 31), 16f,
            Color.argb(110, 255, 255, 255))
        emptyHint.isClickable = true
        emptyHint.isFocusable = true
        emptyHint.contentDescription = "Add your first source"
        emptyHint.setOnClickListener { activity.emptyHintTap() }
        activity.emptyOverlay.addView(emptyHint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(activity.emptyOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        // ---- 5. PERSISTENT REC PILL (top-center, visible ONLY when actively recording) ----
        activity.recChip.apply {
            tag = "recChip"
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
            setPadding(UI.dp(activity, 16), 0, UI.dp(activity, 16), 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            background = Ic.pill(activity, Color.argb(235, 170, 26, 26), 22f,
                Color.argb(200, 255, 130, 130))
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            contentDescription = "Recording indicator"
            setOnClickListener { activity.recChipTap() }
        }
        root.addView(activity.recChip, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, UI.dp(activity, 44),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = UI.dp(activity, 16)
        })

        // ---- 6. BOTTOM SHEET CONTAINER (for Mixer panel or Advanced properties when opened from sidebar) ----
        activity.panelDivider.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = UI.dpf(activity, 2f)
                setColor(Color.argb(120, 255, 255, 255))
            }
        }
        activity.panelContent.orientation = LinearLayout.VERTICAL
        activity.panelScroll.removeAllViews()
        activity.panelScroll.addView(activity.panelContent,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        activity.sheet.apply {
            tag = "bottomSheet"
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    UI.dpf(activity, 20f), UI.dpf(activity, 20f),
                    UI.dpf(activity, 20f), UI.dpf(activity, 20f),
                    0f, 0f, 0f, 0f)
                setColor(Color.argb(252, 16, 18, 24))
            }
            removeAllViews()
            addView(activity.panelDivider,
                LinearLayout.LayoutParams(UI.dp(activity, 48), UI.dp(activity, 4)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setMargins(0, UI.dp(activity, 10), 0, UI.dp(activity, 2))
                })
            addView(activity.panelScroll,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
            visibility = View.GONE
        }
        root.addView(activity.sheet, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM))

        // ---- 7. OVERLAYS: Snackbar & Progress ----
        activity.buildSnackBar(root)
        activity.buildProgOverlay(root)

        // Initial tree build
        activity.refreshSidebar()
    }

    private fun bindPanels(activity: EditorActivity, sourcesPanel: SourcesPanel, mixerPanel: MixerPanel, propertiesPanel: View) {
        sourcesPanel.listener = object : SourcesPanel.Listener {
            override fun onSelect(id: String) { activity.select(id) }
            override fun onToggleVisible(id: String) {
                activity.proj?.layerById(id)?.let { activity.hideSource(it) }
            }
            override fun onAdd() { activity.pickMedia(true) }
            override fun onAddVideo() { activity.pickMedia(true) }
            override fun onAddImage() { activity.pickMedia(false) }
            override fun onRemove() { activity.removeSelectedSource() }
            override fun onHide() {
                activity.selectedId?.let { id ->
                    activity.proj?.layerById(id)?.let { activity.hideSource(it) }
                }
            }
            override fun onMoveUp(id: String) { activity.ctrl.moveZ(id, "up") }
            override fun onMoveDown(id: String) { activity.ctrl.moveZ(id, "down") }
            override fun onProperties() { 
                propertiesPanel.visibility = View.VISIBLE
                sourcesPanel.visibility = View.GONE
                activity.selectedId?.let { id -> activity.proj?.layerById(id)?.let { layer -> activity.openAdvancedSheet(layer) } }
            }
        }
        mixerPanel.listener = object : MixerPanel.Listener {
            override fun onSelect(id: String) { activity.select(id) }
            override fun onMute(id: String) { activity.ctrl.toggleMuted(id) }
            override fun onSolo(id: String) { activity.ctrl.toggleSolo(id) }
            override fun onVolume(id: String, v: Float) {
                val l = activity.proj?.layerById(id) ?: return
                activity.pushUndoLight("volume:" + id)
                if (activity.engineReady()) activity.engine.setVolume(l, v) else l.volume = v
                activity.markDirty()
            }
        }
    }
}
