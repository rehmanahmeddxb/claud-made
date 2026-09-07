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
import android.widget.PopupMenu
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
        // P1-6: tappable + honest (names the real entry point; no \u2295 exists anywhere).
        val emptyHint = UI.label(activity,
            "No sources yet — tap here or Layers \u2192\nto add your first camera or video",
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
            // P1-1: sits below the top strip (strip ~57dp tall)
            topMargin = UI.dp(activity, 66)
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

        // ---- P0-2: transport row (play + time + seek + duration) ----
        // Lives in the bottom dock under the record pill; the canvas is fitted
        // above the whole dock by refreshViewportInsets(). State is owned by
        // PreviewEngine + onTick(); the seek listener is bound here with the chrome
        // (the activity only refreshes engine frames via transportScrubEnded()).
        val transport = LinearLayout(activity).apply {
            tag = "transportRow"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ic.pill(activity, Color.argb(150, 12, 14, 19), 16f,
                Color.argb(60, 255, 255, 255))
            setPadding(UI.dp(activity, 6), UI.dp(activity, 4),
                UI.dp(activity, 12), UI.dp(activity, 4))
        }
        activity.playBtn.apply {
            setIcon(R.drawable.ic_play, Color.WHITE, "Play")
            setOnClickListener { activity.transportPlayTap() }
        }
        val listBtn = IconBtn(activity).apply {
            setIcon(R.drawable.ic_drag, Color.WHITE, "Source list")
            setOnClickListener { activity.toggleSourcesSheet() }
        }
        transport.addView(listBtn,
            LinearLayout.LayoutParams(UI.dp(activity, 48), UI.dp(activity, 48)))
        transport.addView(activity.playBtn,
            LinearLayout.LayoutParams(UI.dp(activity, 48), UI.dp(activity, 48)))
        activity.timeLabel.apply {
            text = "0:00"
            setTextColor(UI.FG)
            textSize = 12f
            setPadding(UI.dp(activity, 4), 0, 0, 0)
        }
        transport.addView(activity.timeLabel,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        activity.seek.apply {
            max = 1
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(UI.ACCENT)
            thumbTintList = android.content.res.ColorStateList.valueOf(UI.ACCENT2)
            contentDescription = "Seek through the composition"
            minimumHeight = UI.dp(activity, 48)
        }
        // CI-green: gestures owned by the chrome, engine refresh by the activity.
        activity.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                if (!fromUser || activity.isRecording()) return
                activity.timeLabel.text = UI.fmtTime(v.toLong())
                if (activity.engineReady()) activity.engine.seekTo(v.toLong())
            }
            override fun onStartTrackingTouch(s: SeekBar?) { activity.scrubbing = true }
            override fun onStopTrackingTouch(s: SeekBar?) {
                activity.scrubbing = false
                activity.transportScrubEnded()
            }
        })
        transport.addView(activity.seek,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(UI.dp(activity, 8), 0, UI.dp(activity, 8), 0)
            })
        activity.durationLabel.apply {
            text = "/ 0:00"
            setTextColor(UI.FG2)
            textSize = 12f
        }
        transport.addView(activity.durationLabel,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        bottomDock.addView(transport,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = UI.dp(activity, 8)
            })
        // LANDSCAPE STUDIO: the transport must not run under the left source
        // rail or the wheel hub — that was the "overlapping / cropped buttons"
        // report. Reserve their exact widths as margins instead of stacking
        // floating bars on top of each other.
        val landscape = activity.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val railW = UI.dp(activity, 196)
        val hubW = UI.dp(activity, 92)
        root.addView(bottomDock, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM).apply {
            if (landscape) { leftMargin = railW + UI.dp(activity, 8); rightMargin = hubW }
        })

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

        // P1-6: icon + short text label (the icon-only rail was undiscoverable).
        fun wheelTrigger(icon: Int, desc: String, label: String,
                         level: () -> RadialMenuView.Level): IconBtn {
            val cell = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                minimumWidth = UI.dp(activity, 56)
            }
            val btn = IconBtn(activity)
            btn.setIcon(icon, Color.WHITE, desc)
            btn.setOnClickListener {
                val loc = IntArray(2); val rootLoc = IntArray(2)
                btn.getLocationOnScreen(loc)
                root.getLocationOnScreen(rootLoc)
                val ax = (loc[0] + btn.width / 2f) - rootLoc[0]
                val ay = (loc[1] + btn.height / 2f) - rootLoc[1]
                activity.openWheelLevel(level(), ax, ay)
            }
            // BUG-11: 48dp is the Material / WCAG 2.5.5 minimum. 46dp was a
            // near-miss that still fails the guideline and mis-taps on the
            // rail edge, which is the most-used navigation surface in the app.
            cell.addView(btn,
                LinearLayout.LayoutParams(UI.dp(activity, 48), UI.dp(activity, 48)))
            val lb = TextView(activity).apply {
                text = label
                textSize = 9.5f
                maxLines = 1
                gravity = Gravity.CENTER
                setTextColor(UI.FG2)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            cell.addView(lb, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, UI.dp(activity, 4), 0, UI.dp(activity, 4))
            wheelRail.addView(cell, lp)
            return btn
        }

        // FOUR triggers, not five. "Play" and "Flash" used to duplicate verbs
        // that now live inside Record, and a five-cell column was tall enough
        // to collide with the top strip in landscape (clipped labels).
        // 1. Sources — every current source, show/hide, delete, add new
        wheelTrigger(R.drawable.ic_layers, "Sources", "Sources") { RadialMenus.sources(activity) }
        // 2. Audio mixer — mute/volume per source + mic gain
        wheelTrigger(R.drawable.ic_volume, "Audio", "Audio") { RadialMenus.audioWheel(activity) }
        // 3. Record — start/stop, camera take, screen record, light, snapshot
        wheelTrigger(R.drawable.ic_stop, "Record", "Record") { RadialMenus.record(activity) }
        // 4. Studio — the root ring: Sources · Add · Audio · Record · Canvas ·
        //    Export · Settings (quality, save folder, rotation, diagnostics).
        activity.studioBtn = wheelTrigger(R.drawable.ic_wheel, "Studio", "Studio") { RadialMenus.root(activity) }

        root.addView(wheelRail, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL or Gravity.END).apply {
            rightMargin = UI.dp(activity, 10)
        })

        // ---- LANDSCAPE: the source rail (OBS dock) down the left edge ----
        // Portrait keeps the sources sheet; in landscape there is width to
        // spare on the left and none at the bottom, so the mixer rows live
        // there permanently: tap = select, 👁 = hide, 🔇 = mute, drag = Z.
        if (landscape) {
            val railWrap = LinearLayout(activity).apply {
                tag = "sourceRail"
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = UI.dpf(activity, 16f)
                    setColor(Color.argb(205, 12, 14, 19))
                    setStroke(UI.dp(activity, 1), Color.argb(55, 255, 255, 255))
                }
                setPadding(UI.dp(activity, 6), UI.dp(activity, 6),
                    UI.dp(activity, 6), UI.dp(activity, 6))
            }
            val head = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(UI.dp(activity, 8), 0, 0, 0)
            }
            head.addView(TextView(activity).apply {
                text = "Sources"
                setTextColor(UI.FG)
                textSize = 12f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(IconBtn(activity).apply {
                setIcon(R.drawable.ic_add, Color.WHITE, "Add source")
                setOnClickListener { activity.openWheelLevel(RadialMenus.add(activity), -1f, -1f) }
            }, LinearLayout.LayoutParams(UI.dp(activity, 40), UI.dp(activity, 40)))
            railWrap.addView(head, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            activity.railContent.orientation = LinearLayout.VERTICAL
            activity.sideRail.apply {
                isFillViewport = true
                removeAllViews()
                addView(activity.railContent, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            // the dock rows render into dockContainer; host it inside the rail
            (activity.dockContainer.parent as? ViewGroup)?.removeView(activity.dockContainer)
            activity.railContent.addView(activity.dockContainer,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
            railWrap.addView(activity.sideRail, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

            root.addView(railWrap, FrameLayout.LayoutParams(
                railW, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START).apply {
                leftMargin = UI.dp(activity, 6)
                topMargin = UI.dp(activity, 62)     // clear of the top strip
                bottomMargin = UI.dp(activity, 8)
            })
        }

        // ---- P1-1: top strip (back + project + aspect + undo/redo) ----
        // Answers "which project am I in, what ratio, is it saved" at a glance.
        // Name/meta text is owned by updateName() (tags "name"/"meta"); the aspect
        // chip by updateAspectChip(); undo/redo dim by refreshUndoRedo().
        val topStripWrap = LinearLayout(activity).apply {
            tag = "topStrip"
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.argb(190, 10, 11, 15)) }
        }
        val topStrip = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UI.dp(activity, 4), UI.dp(activity, 4),
                UI.dp(activity, 8), UI.dp(activity, 4))
        }
        val backBtn = IconBtn(activity).apply {
            setIcon(R.drawable.ic_back, Color.WHITE, "Back")
            setOnClickListener { activity.onBackPressed() }
        }
        topStrip.addView(backBtn,
            LinearLayout.LayoutParams(UI.dp(activity, 48), UI.dp(activity, 48)))
        val titleCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UI.dp(activity, 4), 0, UI.dp(activity, 4), 0)
        }
        val nameView = TextView(activity).apply {
            tag = "name"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val metaView = TextView(activity).apply {
            tag = "meta"
            setTextColor(UI.FG2)
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        titleCol.addView(nameView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        titleCol.addView(metaView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        topStrip.addView(titleCol,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        activity.aspectChip.apply {
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
            setPadding(UI.dp(activity, 14), 0, UI.dp(activity, 14), 0)
            background = Ic.pill(activity, Color.argb(170, 38, 42, 52), 20f,
                Color.argb(120, 255, 255, 255))
            isClickable = true
            isFocusable = true
            setOnClickListener { activity.showAspectPicker() }
        }
        topStrip.addView(activity.aspectChip,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                UI.dp(activity, 48)).apply {
                setMargins(UI.dp(activity, 4), 0, UI.dp(activity, 4), 0)
            })
        val undoBtn = IconBtn(activity).apply {
            tag = "undoBtn"
            setIcon(R.drawable.ic_undo, Color.WHITE, "Undo")
            setOnClickListener { activity.doUndo() }
        }
        topStrip.addView(undoBtn,
            LinearLayout.LayoutParams(UI.dp(activity, 48), UI.dp(activity, 48)))
        val redoBtn = IconBtn(activity).apply {
            tag = "redoBtn"
            setIcon(R.drawable.ic_redo, Color.WHITE, "Redo")
            setOnClickListener { activity.doRedo() }
        }
        topStrip.addView(redoBtn,
            LinearLayout.LayoutParams(UI.dp(activity, 48), UI.dp(activity, 48)))
        // CI-green: Save / Export / Diagnostics live in a ⋮ overflow — the
        // strip has room for exactly one more 48dp target next to the title.
        val moreBtn = IconBtn(activity).apply {
            tag = "moreBtn"
            setIcon(R.drawable.ic_more, Color.WHITE, "More actions")
            setOnClickListener { v ->
                val pop = PopupMenu(activity, v)
                pop.menu.add("Export video").setOnMenuItemClickListener {
                    activity.quickExport(); true
                }
                pop.menu.add("Save project").setOnMenuItemClickListener {
                    activity.saveNow(); true
                }
                pop.menu.add("Diagnostics").setOnMenuItemClickListener {
                    activity.openDiagnostics(); true
                }
                pop.show()
            }
        }
        topStrip.addView(moreBtn,
            LinearLayout.LayoutParams(UI.dp(activity, 48), UI.dp(activity, 48)))
        topStripWrap.addView(topStrip,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        val hairline = View(activity).apply {
            setBackgroundColor(Color.argb(50, 255, 255, 255))
        }
        topStripWrap.addView(hairline,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                UI.dp(activity, 1)))
        root.addView(topStripWrap, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP))

        // ---- P1-2/P1-3: bottom sheet host (sources · mixer · properties) ----
        // Modal over the bottom dock; the canvas fits above it via insets and it
        // caps at 45% of the screen (capPanelHeight). Content builders live in
        // EditorActivity; setSheet() drives visibility + animations; Back closes.
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

        // ---- P0-2: seek listener + initial transport state ----
        activity.bindTransport()

        // ---- P1-1: project name/meta + aspect + undo/redo state ----
        activity.syncTopStrip()

        // ---- P1-6: first-run coach (once ever, dismisses to the canvas) ----
        if (activity.shouldShowCoach()) {
            val scrim = View(activity).apply {
                setBackgroundColor(Color.argb(170, 4, 5, 8))
                isClickable = true
                isFocusable = true
            }
            val coach = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = Ic.pill(activity, Color.argb(250, 20, 23, 31), 16f,
                    Color.argb(100, 255, 255, 255))
                setPadding(UI.dp(activity, 20), UI.dp(activity, 18),
                    UI.dp(activity, 20), UI.dp(activity, 16))
            }
            val ct = TextView(activity).apply {
                text = "Make your first reaction"
                setTextColor(Color.WHITE)
                textSize = 17f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }
            coach.addView(ct)
            val steps = TextView(activity).apply {
                text = "1 \u00b7 Add camera + video from Layers (right rail)\n" +
                    "2 \u00b7 Drag, pinch and resize right on the canvas\n" +
                    "3 \u00b7 Record from Play, export from Studio"
                setTextColor(UI.FG)
                textSize = 13f
                setLineSpacing(UI.dpf(activity, 4f), 1f)
                setPadding(0, UI.dp(activity, 10), 0, UI.dp(activity, 14))
            }
            coach.addView(steps)
            fun dismissCoach() {
                activity.markCoachDone()
                try { root.removeView(coach) } catch (_: Exception) { }
                try { root.removeView(scrim) } catch (_: Exception) { }
            }
            val gotIt = UI.btn(activity, "Got it", accent = true).apply {
                setOnClickListener { dismissCoach() }
            }
            coach.addView(gotIt, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(activity, 50)))
            scrim.setOnClickListener { dismissCoach() }
            root.addView(scrim, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            root.addView(coach, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER).apply {
                setMargins(UI.dp(activity, 28), 0, UI.dp(activity, 28), 0)
            })
        }
    }

    private fun bindPanels(activity: EditorActivity, sourcesPanel: SourcesPanel, mixerPanel: MixerPanel, propertiesPanel: View) {
        sourcesPanel.listener = object : SourcesPanel.Listener {
            override fun onSelect(id: String) { activity.select(id) }
            // BUG-02: route through the host so hiding always explains itself
            // and offers UNDO, exactly like every other surface.
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

    private fun buildTransport(activity: EditorActivity, bar: LinearLayout) {

        val playBtn = IconBtn(activity).apply {
            setIcon(R.drawable.ic_play, Color.WHITE, "Play")
            setOnClickListener { activity.togglePlay() }
        }
        activity.playBtn = playBtn
        // BUG-11: transport play/pause raised 44 -> 48dp
        bar.addView(playBtn, LinearLayout.LayoutParams(UI.dp(activity, 48), UI.dp(activity, 48)))

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
        // BUG-11: record chip raised 34 -> 48dp (it is a primary action)
        val recLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, UI.dp(activity, 48))
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
