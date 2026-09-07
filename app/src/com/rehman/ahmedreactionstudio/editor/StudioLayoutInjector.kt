package com.rehman.ahmedreactionstudio.editor

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Button
import com.rehman.ahmedreactionstudio.R
import com.rehman.ahmedreactionstudio.core.Aspect
import com.rehman.ahmedreactionstudio.core.Project
import com.rehman.ahmedreactionstudio.util.UI

object StudioLayoutInjector {

    /**
     * A compact, theme-matched pill control (replaces the raw android [Button]
     * widgets that used to sit in the thin top / transport bars). Raw Buttons
     * carry Android's ~48dp default min-height, all-caps and large internal
     * padding, so inside a short weighted bar they overflowed vertically (text
     * clipped) and sat at different heights/baselines than the neighbouring
     * 44dp icon buttons and labels — the "cropped / misaligned" buttons. This
     * pill is a [TextView] with zero minimum height and an explicit height, so
     * it centres cleanly against the row's other controls.
     */
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
        // ============================================================
        // MINIMAL CHROME: fullscreen canvas + rail + REC pill + record dock +
        // snackbar/progress overlays. (P0-3/P0-4/P0-7/P0-8 grew the original
        // "canvas + 5 triggers" — the rail is still the only *navigation*.)
        // Every lateinit var EditorActivity declares still gets a real
        // (but often invisible/unattached) object assigned here, so any
        // existing call site elsewhere in the file — dock.rebuild(),
        // timeLabel.text = …, seek.progress = … — stays crash-safe even
        // though none of those legacy panels are shown anymore. Only the
        // pieces actually wired into rootFrame below are visible.
        // ============================================================
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

        // ---- the only thing actually on screen: canvas, edge-to-edge ----
        val canvasContainer = FrameLayout(activity)
        canvasContainer.setBackgroundColor(Color.rgb(4, 5, 7))
        root.addView(canvasContainer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        activity.stage = StageView(activity)
        activity.stage.host = activity
        canvasContainer.addView(activity.stage, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))

        // gentle empty-state hint — points at the Sources wheel instead of a
        // panel that no longer exists
        activity.emptyOverlay.orientation = LinearLayout.VERTICAL
        activity.emptyOverlay.gravity = Gravity.CENTER
        val emptyHint = UI.label(activity, "Tap ⊕ Sources to add your first camera or video",
            dim = true, size = 13f)
        emptyHint.gravity = Gravity.CENTER
        activity.emptyOverlay.addView(emptyHint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(activity.emptyOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        // ---- P0-3: persistent REC pill (top-center, tap = stop) ----
        // One pill for composite takes, screen recording and camera takes;
        // text/visibility/contentDescription are owned by updateRecChip().
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
            background = Ic.pill(activity, Color.argb(235, 170, 26, 26), 24f,
                Color.argb(200, 255, 130, 130))
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            contentDescription = "Recording indicator"
            setOnClickListener { activity.recChipTap() }
        }
        root.addView(activity.recChip, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, UI.dp(activity, 48),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = UI.dp(activity, 10)
        })

        // ---- P0-8: bottom dock — record-readiness pill now, transport row later (P0-2) ----
        val bottomDock = LinearLayout(activity).apply {
            tag = "bottomDock"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(UI.dp(activity, 12), UI.dp(activity, 8),
                UI.dp(activity, 12), UI.dp(activity, 12))
        }
        activity.recordBtn.apply {
            tag = "recordBtn"
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
            setPadding(UI.dp(activity, 18), 0, UI.dp(activity, 18), 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            isClickable = true
            isFocusable = true
            // text/background/alpha/visibility are owned by updateRecordButton()
            setOnClickListener { activity.recordButtonTap() }
        }
        bottomDock.addView(activity.recordBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, UI.dp(activity, 48)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        root.addView(bottomDock, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM))

        // ---- 5 radial-wheel triggers, floating column on the right edge ----
        activity.rootFrame = root
        val wheelRail = LinearLayout(activity)
        wheelRail.tag = "wheelRail"
        wheelRail.orientation = LinearLayout.VERTICAL
        wheelRail.gravity = Gravity.CENTER_HORIZONTAL
        val railBg = GradientDrawable()
        railBg.cornerRadius = UI.dpf(activity, 24f)
        railBg.setColor(Color.argb(140, 10, 11, 15))
        wheelRail.background = railBg
        wheelRail.setPadding(UI.dp(activity, 4), UI.dp(activity, 8), UI.dp(activity, 4), UI.dp(activity, 8))

        fun wheelTrigger(icon: Int, desc: String, level: () -> RadialMenuView.Level): IconBtn {
            val btn = IconBtn(activity)
            btn.layoutParams = IconBtn.sized(activity, 46)
            btn.setIcon(icon, Color.WHITE, desc)
            btn.setOnClickListener {
                val loc = IntArray(2); val rootLoc = IntArray(2)
                btn.getLocationOnScreen(loc)
                root.getLocationOnScreen(rootLoc)
                val ax = (loc[0] + btn.width / 2f) - rootLoc[0]
                val ay = (loc[1] + btn.height / 2f) - rootLoc[1]
                activity.openWheelLevel(level(), ax, ay)
            }
            val lp = LinearLayout.LayoutParams(UI.dp(activity, 46), UI.dp(activity, 46))
            lp.setMargins(0, UI.dp(activity, 4), 0, UI.dp(activity, 4))
            wheelRail.addView(btn, lp)
            return btn
        }

        // 1. Sources — every current source, show/hide, delete, add new
        wheelTrigger(R.drawable.ic_layers, "Sources") { RadialMenus.sources(activity) }
        // 2. Audio mixer — mute/volume per source + mic gain
        wheelTrigger(R.drawable.ic_volume, "Audio") { RadialMenus.audioWheel(activity) }
        // 3. Play/Stop — master playback + composite recording (auto-export on stop)
        wheelTrigger(R.drawable.ic_play, "Play / Record") { RadialMenus.playStop(activity) }
        // 4. Flashlight — front/back/both LED + screen light
        wheelTrigger(R.drawable.ic_flash, "Flash") { RadialMenus.lightRoot(activity) }
        // 5. Studio — the root ring: Canvas · Export · Project (aspect ratio,
        //    background, quick export + settings, rename, save, undo/redo,
        //    snapshot, diagnostics). P0-1: root() was previously reachable
        //    ONLY via a hidden long-press on empty canvas, so Canvas/Export/
        //    Project were effectively undiscoverable. This trigger replaces
        //    the dead "Test" placeholder (P0-6) — no dead buttons on the rail.
        activity.studioBtn = wheelTrigger(R.drawable.ic_wheel, "Studio") { RadialMenus.root(activity) }

        root.addView(wheelRail, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL or Gravity.END).apply {
            rightMargin = UI.dp(activity, 10)
        })

        // small unobtrusive back/close affordance — the one piece of "chrome"
        // kept outside the wheels, since there is otherwise no way to leave
        // the canvas at all
        val closeBtn = IconBtn(activity)
        closeBtn.tag = "closeBtn"
        closeBtn.layoutParams = IconBtn.sized(activity, 40)
        closeBtn.setIcon(R.drawable.ic_back, Color.WHITE, "Back")
        closeBtn.setOnClickListener { activity.onBackPressed() }
        root.addView(closeBtn, FrameLayout.LayoutParams(
            UI.dp(activity, 40), UI.dp(activity, 40), Gravity.TOP or Gravity.START).apply {
            topMargin = UI.dp(activity, 10); leftMargin = UI.dp(activity, 10)
        })

        // ---- P0-7: snackbar under the wheel (visible once the wheel dismisses) ----
        activity.buildSnackBar(root)

        // ---- the radial-menu overlay itself, on top of everything ----
        activity.wheel = RadialMenuView(activity)
        activity.wheel.onDismiss = { }
        root.addView(activity.wheel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // ---- P0-7: export/record progress sits above all (cancellable) ----
        activity.buildProgOverlay(root)

        // ---- P0-4: fit the canvas clear of rail + pills + dock ----
        activity.bindMinimalChromeInsets()
    }

    private fun bindPanels(activity: EditorActivity, sourcesPanel: SourcesPanel, mixerPanel: MixerPanel, propertiesPanel: View) {
        sourcesPanel.listener = object : SourcesPanel.Listener {
            override fun onSelect(id: String) { activity.select(id) }
            override fun onToggleVisible(id: String) { activity.ctrl.toggleVisible(id) }
            override fun onAdd() { activity.pickMedia(true) }
            override fun onAddVideo() { activity.pickMedia(true) }
            override fun onAddImage() { activity.pickMedia(false) }
            override fun onRemove() { activity.removeSelectedSource() }
            override fun onHide() { activity.selectedId?.let { activity.ctrl.toggleVisible(it) } }
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
                activity.pushUndoLight()
                if (activity.engineReady()) activity.engine.setVolume(l, v) else l.volume = v
                activity.markDirty()
            }
        }
    }

    private fun buildTransport(activity: EditorActivity, bar: LinearLayout) {

        val playBtn = IconBtn(activity).apply {
            setIcon(R.drawable.ic_play, Color.WHITE, "Play")
            setOnClickListener { activity.togglePlay() }
        }
        activity.playBtn = playBtn
        bar.addView(playBtn, LinearLayout.LayoutParams(UI.dp(activity, 44), UI.dp(activity, 44)))

        // Same visual grammar and height as the play icon so the whole row is
        // one aligned baseline. recordBtn is a TextView (per its declared type)
        // — updateRecordButton() restyles it into a full pill + label anyway.
        val recBtn = TextView(activity).apply {
            activity.recordBtn = this
            text = "● Record"
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.argb(255, 255, 90, 90))
            setPadding(UI.dp(activity, 12), 0, UI.dp(activity, 12), 0)
            setOnClickListener { activity.recordButtonTap() }
        }
        val recG = GradientDrawable()
        recG.cornerRadius = UI.dpf(activity, 17f)
        recG.setColor(Color.argb(200, 200, 34, 34))
        recBtn.background = recG
        val recLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, UI.dp(activity, 34))
        recLp.setMargins(UI.dp(activity, 2), 0, UI.dp(activity, 2), 0)
        recBtn.layoutParams = recLp
        bar.addView(recBtn)

        val stopBtn = pillBtn(activity, "⏹ Stop", UI.FG,
            Color.argb(150, 38, 42, 52), heightDp = 34, bold = false) { activity.controlsStopTap() }
        bar.addView(stopBtn)
        
        val timeLabel = TextView(activity).apply {
            text = "00:00:00"
            setTextColor(Color.WHITE)
            setPadding(UI.dp(activity, 10), 0, 0, 0)
        }
        activity.timeLabel = timeLabel
        bar.addView(timeLabel)

        val durationLabel = TextView(activity).apply {
            text = "/ 00:00:00"
            setTextColor(Color.GRAY)
            setPadding(UI.dp(activity, 4), 0, 0, 0)
        }
        activity.durationLabel = durationLabel
        bar.addView(durationLabel)

    }
}
