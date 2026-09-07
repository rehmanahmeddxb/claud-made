package com.rehman.ahmedreactionstudio.editor

import com.rehman.ahmedreactionstudio.R
import com.rehman.ahmedreactionstudio.core.Aspect
import com.rehman.ahmedreactionstudio.core.Layer

/**
 * Complete sidebar menu tree. Every item the radial wheels, panels and dock
 * exposed is mapped here as a hierarchy: Section → Sub-menu → Sub-sub-menu.
 *
 * The tree reads live [RadialMenus.Host] state on every [build] call, so
 * badges, active flags and enabled flags stay in sync with the project.
 */
object SidebarTree {

    // ────────────────────── data model ──────────────────────

    data class Section(
        val id: String,
        val icon: Int,
        val label: String,
        val badge: String?,
        val children: List<Item>,
        var expanded: Boolean = false
    )

    data class Item(
        val icon: Int,
        val label: String,
        val badge: String? = null,
        val active: Boolean = false,
        val danger: Boolean = false,
        val enabled: Boolean = true,
        val children: List<Item>? = null,
        val action: (() -> Unit)? = null,
        var expanded: Boolean = false
    ) {
        val isFolder get() = children != null
    }

    // ────────────────────── tree builder ──────────────────────

    fun build(h: RadialMenus.Host): List<Section> {
        val p = h.project
        val n = p.layers.size
        val live = p.layers.firstOrNull { it.isLive() }
        val audioN = p.layers.count { it.isClip() }
        val rec = h.isRecordingComposite()
        val lightOn = h.isScreenLightOn() ||
                (live != null && (h.isTorchOn(live) || h.isFrontTorchOn() || h.isBackTorchOn())) ||
                h.isBothTorchOn()

        return listOf(
            sourcesSection(h, n),
            controlsSection(h),
            audioSection(h, audioN, live),
            recordSection(h, rec, live, lightOn),
            canvasSection(h),
            exportSection(h),
            projectSection(h),
            settingsSection(h)
        )
    }

    // ────────────────────── SOURCES ──────────────────────

    private fun sourcesSection(h: RadialMenus.Host, n: Int): Section {
        val p = h.project
        val layerItems = p.layers.asReversed().map { l ->
            Item(
                icon = Ic.typeIcon(l.type),
                label = l.name.ifBlank { l.type.label },
                badge = if (!l.visible) "hidden" else null,
                active = l.id == (h.selected()?.id),
                children = listOf(
                    Item(if (l.visible) R.drawable.ic_eye else R.drawable.ic_eye_off,
                        if (l.visible) "Hide" else "Show",
                        action = { h.hideSource(l) }),
                    Item(R.drawable.ic_up, "Move up",
                        action = { h.ctrl.moveZ(l.id, "up") }),
                    Item(R.drawable.ic_down, "Move down",
                        action = { h.ctrl.moveZ(l.id, "down") }),
                    Item(R.drawable.ic_copy, "Duplicate",
                        action = { /* dup handled by host */ }),
                    Item(R.drawable.ic_delete, "Remove", danger = true,
                        action = { h.deleteSource(l) })
                ),
                action = { h.selectId(l.id) }
            )
        }

        val addSub = Item(
            icon = R.drawable.ic_add, label = "Add Source",
            children = listOf(
                Item(R.drawable.ic_camera, "Camera (live)",
                    action = { h.addCameraLive() }),
                Item(R.drawable.ic_video, "Video file",
                    action = { h.addVideo() }),
                Item(R.drawable.ic_image, "Image",
                    action = { h.addImage() }),
                Item(R.drawable.ic_screen, "Screen record",
                    action = { h.addScreen() }),
                Item(R.drawable.ic_text, "Text overlay",
                    action = { h.addTextSource() })
            )
        )

        val allChildren = mutableListOf<Item>()
        allChildren.addAll(layerItems)
        allChildren.add(addSub)
        if (h.selected() != null) {
            allChildren.add(Item(R.drawable.ic_delete, "Remove selected", danger = true,
                action = { h.selected()?.let { h.deleteSource(it) } }))
        }

        return Section("sources", R.drawable.ic_layers, "Sources",
            if (n > 0) "$n" else null, allChildren, expanded = n > 0)
    }

    // ────────────────────── SOURCE CONTROLS ──────────────────────

    private fun controlsSection(h: RadialMenus.Host): Section {
        val s = h.selected()
        if (s == null) {
            return Section("controls", R.drawable.ic_settings, "Source Controls", null,
                listOf(Item(R.drawable.ic_info, "Select a source first", enabled = false) {}))
        }
        val items = mutableListOf<Item>()

        items.add(Item(if (s.visible) R.drawable.ic_eye else R.drawable.ic_eye_off,
            if (s.visible) "Hide" else "Show",
            action = { h.hideSource(s) }))

        items.add(Item(if (s.locked) R.drawable.ic_lock else R.drawable.ic_lock_open,
            if (s.locked) "Unlock" else "Lock",
            active = s.locked,
            action = { h.ctrl.toggleLocked(s.id) }))

        items.add(Item(if (s.playing) R.drawable.ic_pause else R.drawable.ic_play,
            if (s.playing) "Pause layer" else "Resume layer",
            action = { h.toggleSourcePlay(s) }))

        items.add(Item(R.drawable.ic_fit, "Fit Mode",
            badge = s.fit,
            children = listOf(
                Item(R.drawable.ic_fill, "Fill (cover)", active = s.fit == "fill",
                    action = { h.ctrl.setFit(s.id, "fill") }),
                Item(R.drawable.ic_fit, "Fit (letterbox)", active = s.fit == "fit",
                    action = { h.ctrl.setFit(s.id, "fit") })
            )))

        items.add(Item(R.drawable.ic_center, "Transform",
            children = listOf(
                Item(R.drawable.ic_center, "Center on canvas",
                    action = { h.ctrl.center(s.id) }),
                Item(R.drawable.ic_reset, "Reset geometry",
                    action = { h.ctrl.resetGeometry(s.id) }),
                Item(R.drawable.ic_corner_tl, "Corner: top-left",
                    action = { h.ctrl.anchor(s.id, "tl") }),
                Item(R.drawable.ic_corner_tr, "Corner: top-right",
                    action = { h.ctrl.anchor(s.id, "tr") }),
                Item(R.drawable.ic_corner_bl, "Corner: bottom-left",
                    action = { h.ctrl.anchor(s.id, "bl") }),
                Item(R.drawable.ic_corner_br, "Corner: bottom-right",
                    action = { h.ctrl.anchor(s.id, "br") })
            )))

        items.add(Item(R.drawable.ic_fill, "Set as background",
            action = { h.ctrl.setAsCanvasBackground(s.id) }))

        if (s.type == com.rehman.ahmedreactionstudio.core.LayerType.TEXT) {
            items.add(Item(R.drawable.ic_edit, "Edit text",
                action = { h.editText(s) }))
            items.add(Item(R.drawable.ic_palette, "Cycle text colour",
                action = { h.cycleTextColor(s) }))
        }

        items.add(Item(R.drawable.ic_settings, "Advanced properties…",
            action = { h.openAdvanced(s) }))

        return Section("controls", R.drawable.ic_settings, "Source Controls",
            s.name.ifBlank { s.type.label }.take(12), items, expanded = true)
    }

    // ────────────────────── AUDIO ──────────────────────

    private fun audioSection(h: RadialMenus.Host, audioN: Int,
                             live: Layer?): Section {
        val p = h.project
        val items = mutableListOf<Item>()

        items.add(Item(R.drawable.ic_volume, "Mixer panel",
            action = { h.openMixerPanel() }))

        if (live != null) {
            items.add(Item(R.drawable.ic_camera, "Mic gain",
                badge = "${(h.micGain() * 100).toInt()}%",
                children = listOf(
                    Item(R.drawable.ic_up, "Gain +10%",
                        action = { h.setMicGain(h.micGain() + 0.1f) }),
                    Item(R.drawable.ic_down, "Gain −10%",
                        action = { h.setMicGain(h.micGain() - 0.1f) }),
                    Item(R.drawable.ic_reset, "Reset to 100%",
                        action = { h.setMicGain(1f) })
                )))
        }

        val clips = p.layers.filter { it.isClip() }
        clips.forEach { l ->
            val muted = h.ctrl.effectiveMuted(l)
            items.add(Item(Ic.typeIcon(l.type),
                l.name.ifBlank { l.type.label },
                badge = if (muted) "MUTED" else "${(l.volume * 100).toInt()}%",
                children = listOf(
                    Item(R.drawable.ic_up, "Volume +10%",
                        action = { h.ctrl.setVolume(l.id, l.volume + 0.1f) }),
                    Item(R.drawable.ic_down, "Volume −10%",
                        action = { h.ctrl.setVolume(l.id, l.volume - 0.1f) }),
                    Item(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume,
                        if (muted) "Unmute" else "Mute", active = muted,
                        action = { h.ctrl.toggleMuted(l.id) }),
                    Item(R.drawable.ic_star,
                        if (l.solo) "Solo: on" else "Solo", active = l.solo,
                        action = { h.ctrl.toggleSolo(l.id) })
                )))
        }

        if (clips.isEmpty() && live == null) {
            items.add(Item(R.drawable.ic_info, "No audio sources yet",
                enabled = false) {})
        }

        return Section("audio", R.drawable.ic_volume, "Audio",
            if (audioN > 0) "$audioN" else null, items)
    }

    // ────────────────────── RECORD ──────────────────────

    private fun recordSection(h: RadialMenus.Host, rec: Boolean,
                              live: Layer?, lightOn: Boolean): Section {
        val items = mutableListOf<Item>()

        items.add(Item(
            if (rec) R.drawable.ic_stop else R.drawable.ic_camera,
            if (rec) "Stop && save" else "Start recording",
            active = rec, danger = rec,
            enabled = rec || h.canRecordComposite(),
            action = { h.toggleCompositeRecording() }))

        items.add(Item(
            if (h.anyPlaying()) R.drawable.ic_pause else R.drawable.ic_play,
            if (h.anyPlaying()) "Pause" else "Play",
            active = h.anyPlaying(),
            action = { h.toggleMasterPlay() }))

        items.add(Item(R.drawable.ic_stop, "Stop",
            action = { h.restart() }))

        if (live != null) {
            val camRec = h.isCameraRecording(live)
            items.add(Item(
                if (camRec) R.drawable.ic_stop else R.drawable.ic_camera,
                if (camRec) "Stop camera take" else "Camera take",
                active = camRec, danger = camRec,
                action = { h.toggleCameraRecord(live) }))
        } else {
            items.add(Item(R.drawable.ic_camera, "Add live camera",
                action = { h.addCameraLive() }))
        }

        items.add(Item(R.drawable.ic_screen, "Screen record",
            action = { h.addScreen() }))
        items.add(Item(R.drawable.ic_image, "Snapshot frame",
            action = { h.snapshotFrame() }))
        items.add(Item(R.drawable.ic_reset, "Restart",
            action = { h.restart() }))

        // Light sub-menu
        val lightChildren = mutableListOf<Item>()
        if (h.hasFrontTorch() || h.hasBackTorch()) {
            lightChildren.add(Item(R.drawable.ic_flash, "Torch",
                children = buildList {
                    if (h.hasFrontTorch()) add(Item(R.drawable.ic_flash,
                        if (h.isFrontTorchOn()) "Front: ON" else "Front torch",
                        active = h.isFrontTorchOn(),
                        action = { h.toggleFrontTorch() }))
                    if (h.hasBackTorch()) add(Item(R.drawable.ic_flash,
                        if (h.isBackTorchOn()) "Back: ON" else "Back torch",
                        active = h.isBackTorchOn(),
                        action = { h.toggleBackTorch() }))
                    if (h.hasFrontTorch() && h.hasBackTorch()) add(Item(R.drawable.ic_flash,
                        if (h.isBothTorchOn()) "Both: ON" else "Both torches",
                        active = h.isBothTorchOn(),
                        action = { h.toggleBothTorch() }))
                }))
        }
        lightChildren.add(Item(R.drawable.ic_flash,
            if (h.isScreenLightOn()) "Screen light: ON" else "Screen light",
            active = h.isScreenLightOn(),
            action = { h.toggleScreenLight() }))
        items.add(Item(R.drawable.ic_flash, "Light",
            badge = if (lightOn) "ON" else null,
            children = lightChildren))

        return Section("record", R.drawable.ic_stop, "Record",
            if (rec) "REC" else if (lightOn) "ON" else null, items, expanded = rec)
    }

    // ────────────────────── CANVAS ──────────────────────

    private fun canvasSection(h: RadialMenus.Host): Section {
        val p = h.project
        val items = listOf(
            Item(R.drawable.ic_aspect, "Aspect ratio",
                badge = p.aspect.code,
                children = listOf(
                    Item(R.drawable.ic_aspect, "16:9 Landscape",
                        active = p.aspect == Aspect.R169,
                        action = { h.setAspect(Aspect.R169) }),
                    Item(R.drawable.ic_aspect, "9:16 Portrait",
                        active = p.aspect == Aspect.R916,
                        action = { h.setAspect(Aspect.R916) }),
                    Item(R.drawable.ic_aspect, "1:1 Square",
                        active = p.aspect == Aspect.R11,
                        action = { h.setAspect(Aspect.R11) })
                )),
            Item(R.drawable.ic_palette, "Background",
                children = listOf(
                    Item(R.drawable.ic_palette, "Dark",
                        action = { h.setBg(0xFF101418.toInt()) }),
                    Item(R.drawable.ic_palette, "Black",
                        action = { h.setBg(0xFF000000.toInt()) }),
                    Item(R.drawable.ic_palette, "White",
                        action = { h.setBg(0xFFFFFFFF.toInt()) }),
                    Item(R.drawable.ic_palette, "Orange",
                        action = { h.setBg(0xFFFF5A2C.toInt()) }),
                    Item(R.drawable.ic_palette, "Navy",
                        action = { h.setBg(0xFF1E3C78.toInt()) }),
                    Item(R.drawable.ic_palette, "Green",
                        action = { h.setBg(0xFF14785A.toInt()) }),
                    Item(R.drawable.ic_palette, "Purple",
                        action = { h.setBg(0xFF781E5A.toInt()) })
                )),
            Item(R.drawable.ic_fullscreen, "Full canvas mode",
                action = { h.enterFullCanvas() }),
            Item(R.drawable.ic_fit, "Fit all sources",
                action = { h.fitAllSources() }),
            Item(R.drawable.ic_fill, "Selection → background",
                enabled = h.selected() != null,
                action = {
                    val s = h.selected()
                    if (s != null) h.ctrl.setAsCanvasBackground(s.id)
                    else h.toast("Select a source first")
                })
        )
        return Section("canvas", R.drawable.ic_aspect, "Canvas",
            p.aspect.code, items)
    }

    // ────────────────────── EXPORT ──────────────────────

    private fun exportSection(h: RadialMenus.Host): Section {
        return Section("export", R.drawable.ic_export, "Export", null,
            listOf(
                Item(R.drawable.ic_export, "Quick export (720p30)",
                    action = { h.quickExport() }),
                Item(R.drawable.ic_settings, "Export settings…",
                    action = { h.openExportPanel() })
            ))
    }

    // ────────────────────── PROJECT ──────────────────────

    private fun projectSection(h: RadialMenus.Host): Section {
        val hudOn = h.isStatsHudOn()
        return Section("project", R.drawable.ic_edit, "Project",
            h.project.name.take(10),
            listOf(
                Item(R.drawable.ic_edit, "Rename",
                    action = { h.renameProject() }),
                Item(R.drawable.ic_check, "Save now",
                    action = { h.saveNow() }),
                Item(R.drawable.ic_image, "Snapshot frame",
                    action = { h.snapshotFrame() }),
                Item(R.drawable.ic_info,
                    if (hudOn) "Stats overlay: on" else "Stats overlay: off",
                    active = hudOn,
                    action = { h.toggleStatsHud() }),
                Item(R.drawable.ic_undo, "Undo",
                    action = { h.undo() }),
                Item(R.drawable.ic_redo, "Redo",
                    action = { h.redo() }),
                Item(R.drawable.ic_info, "Diagnostics",
                    action = { h.openDiagnostics() }),
                Item(R.drawable.ic_back, "Close project", danger = true,
                    action = { h.closeProject() })
            ))
    }

    // ────────────────────── SETTINGS ──────────────────────

    private fun settingsSection(h: RadialMenus.Host): Section {
        val pol = h.orientPolicyName()
        return Section("settings", R.drawable.ic_settings, "Settings", null,
            listOf(
                Item(R.drawable.ic_export, "Export quality…",
                    action = { h.openExportPanel() }),
                Item(R.drawable.ic_layers, "Save folder: ${h.saveFolderLabel()}",
                    action = { h.pickSaveFolder() }),
                Item(R.drawable.ic_reset, "Use default album",
                    action = { h.resetSaveFolder() }),
                Item(R.drawable.ic_reset, "Orientation",
                    badge = pol,
                    children = listOf(
                        Item(R.drawable.ic_aspect, "Follow canvas",
                            active = pol == "canvas",
                            action = { h.setOrientPolicyByName("canvas") }),
                        Item(R.drawable.ic_aspect, "Auto rotate",
                            active = pol == "auto",
                            action = { h.setOrientPolicyByName("auto") }),
                        Item(R.drawable.ic_lock, "Lock current",
                            active = pol == "lock",
                            action = { h.setOrientPolicyByName("lock") })
                    ))
            ))
    }
}
