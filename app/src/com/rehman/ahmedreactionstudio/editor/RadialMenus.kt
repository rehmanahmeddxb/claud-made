package com.rehman.ahmedreactionstudio.editor

import com.rehman.ahmedreactionstudio.R
import com.rehman.ahmedreactionstudio.core.Aspect
import com.rehman.ahmedreactionstudio.core.Layer
import com.rehman.ahmedreactionstudio.core.LayerType
import com.rehman.ahmedreactionstudio.core.Project
import com.rehman.ahmedreactionstudio.core.SourceController

/**
 * THE INTERFACE, AS RINGS.
 *
 * Everything the old tab bar and its button panels did is expressed here as a
 * tree of radial levels, so the user's model holds:
 *
 *      tap ◉  →  petals (Sources · Add · Controls · Dock · Mixing · Canvas ·
 *                        Export · Project)
 *      tap a petal  →  its sub-petals
 *      tap a sub-petal  →  the action happens
 *
 * Rings never own state: each `Level.items` lambda reads the live [Project]
 * every time it is drawn, and every verb goes through [SourceController] (or a
 * host callback) so undo/redo and preview==export still hold by construction.
 */
object RadialMenus {

    /** Everything the rings need from the editor. */
    interface Host {
        val project: Project
        val ctrl: SourceController
        fun selected(): Layer?
        fun selectId(id: String?)

        /**
         * UNDOABLE DESTRUCTIVE VERBS.
         *
         * Rings must NOT call `ctrl.delete` / `ctrl.toggleVisible` directly.
         * Going straight to the controller mutates state correctly but skips
         * the snackbar, so the user gets no "UNDO" affordance — deleting a
         * source from the wheel used to be completely silent. Routing through
         * the host keeps one implementation of "destroy + explain + offer undo"
         * for every surface (wheel, panels, dock, canvas).
         */
        fun deleteSource(l: Layer)
        fun hideSource(l: Layer)

        // add
        fun addVideo()
        fun addImage()
        fun addCameraLive()
        fun addCameraTake()
        fun addScreen()
        fun addTextSource()

        // transport / controls
        fun anyPlaying(): Boolean
        fun toggleMasterPlay()
        fun restart()
        fun nudge(ms: Long)
        fun toggleSourcePlay(l: Layer)
        fun snapshotFrame()
        fun undo()
        fun redo()

        // panels that genuinely need a sheet (sliders / pickers)
        fun openDockPanel()
        fun openMixerPanel()
        fun openExportPanel()
        fun openAdvanced(l: Layer)
        fun quickExport()

        // canvas / project
        fun setAspect(a: Aspect)
        fun setBg(color: Int)
        fun fitAllSources()
        fun renameProject()
        fun saveNow()
        fun openDiagnostics()
        /** Full Canvas / immersive mode: every overlay hidden, canvas is 100 % */
        fun enterFullCanvas()
        /** true while immersive mode is on (the hamburger itself is hidden too) */
        fun isImmersive(): Boolean
        fun closeProject()
        fun editText(l: Layer)
        fun cycleTextColor(l: Layer)

        // live camera
        fun isCameraRecording(l: Layer): Boolean
        fun toggleCameraRecord(l: Layer)
        fun switchCameraFacing(l: Layer)
        fun toggleCameraMirror(l: Layer)
        /** hardware LED torch state of the camera that is open right now */
        fun isTorchOn(l: Layer): Boolean
        /** true when the CURRENTLY selected camera actually has an LED */
        fun hasTorch(l: Layer): Boolean
        fun toggleTorch(l: Layer)
        // per-facing torch (front / back / both) — remember user wants both flashes option
        fun hasFrontTorch(): Boolean
        fun hasBackTorch(): Boolean
        fun isFrontTorchOn(): Boolean
        fun isBackTorchOn(): Boolean
        fun isBothTorchOn(): Boolean
        fun toggleFrontTorch()
        fun toggleBackTorch()
        fun toggleBothTorch()
        /** screen flash: the canvas glows white to light a front-camera face */
        fun isScreenLightOn(): Boolean
        fun toggleScreenLight()
        fun openFlashRing(l: Layer)

        // master play + composite recording (Play/Stop radial wheel)
        fun isRecordingComposite(): Boolean
        fun toggleCompositeRecording()
        fun canRecordComposite(): Boolean

        // mic input gain, adjustable live from the Audio wheel
        fun micGain(): Float
        fun setMicGain(g: Float)

        // preview health overlay (also toggled from the editor overflow)
        fun isStatsHudOn(): Boolean
        fun toggleStatsHud()

        // settings: where finished videos land + how the studio rotates
        fun saveFolderLabel(): String
        fun pickSaveFolder()
        fun resetSaveFolder()
        fun orientPolicyName(): String
        fun setOrientPolicyByName(p: String)

        fun toast(msg: String)
    }

    private fun item(
        icon: Int, label: String, active: Boolean = false, danger: Boolean = false,
        badge: String? = null, keepOpen: Boolean = false, enabled: Boolean = true,
        action: () -> Unit
    ) = RadialMenuView.Item(icon, label, active, danger, badge, null, keepOpen, enabled, action)

    private fun folder(
        icon: Int, label: String, badge: String? = null, sub: () -> RadialMenuView.Level
    ) = RadialMenuView.Item(icon, label, false, false, badge, sub, false, true, null)

    // ================= ROOT =================

    /**
     * Root ring, kept to 7 petals so it NEVER pages (8/page). One-tap jobs
     * live on the persistent bottom bar; the ring is the power-user shortcut.
     * Deleted rings and where their verbs went:
     *  - Controls → transport row (±10 s buttons flank it) + Snapshot in Project
     *  - Dock     → Layers bottom-bar sheet (with ‹ › selection steppers)
     *  - Mixing   → Audio sheet (single mixer: mute/solo/loop/level per channel)
     */
    fun root(h: Host): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_wheel, "Studio", "tap a petal · tap the hub to close"
    ) {
        val n = h.project.layers.size
        val live = h.project.layers.firstOrNull { it.isLive() }
        val lightBadge = if (h.isScreenLightOn() || (live != null && (h.isTorchOn(live) || h.isFrontTorchOn() || h.isBackTorchOn())) || h.isBothTorchOn()) "ON" else null
        val audioN = h.project.layers.count { it.isClip() }
        listOf(
            folder(R.drawable.ic_layers, "Sources", badge = if (n > 0) "$n" else null) { sources(h) },
            folder(R.drawable.ic_add, "Add") { add(h) },
            // P0-1: the mixer SHEET isn't attached in minimal chrome, so
            // openMixerPanel() would be a silent no-op. The audio WHEEL is the
            // working equivalent (mute/volume/solo/mic gain) — open that.
            // (P1-3 may re-point this at the restored Audio sheet.)
            folder(R.drawable.ic_volume, "Audio", badge = if (audioN > 0) "$audioN" else null) { audioWheel(h) },
            // Recording is a top-level job, not something buried under Play.
            // Light lives inside it (lighting is a *recording* decision) and
            // still keeps its ON badge visible from the root ring.
            folder(R.drawable.ic_stop, "Record",
                badge = if (h.isRecordingComposite()) "REC" else lightBadge) { record(h) },
            folder(R.drawable.ic_aspect, "Canvas") { canvas(h) },
            folder(R.drawable.ic_export, "Export") { export(h) },
            folder(R.drawable.ic_settings, "Settings") { settings(h) }
        )
    }

    // ================= RECORD =================

    /** Everything that starts or stops a capture, in one ring. */
    fun record(h: Host): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_stop, "Record", "start · stop · take · screen"
    ) {
        val rec = h.isRecordingComposite()
        val live = h.project.layers.firstOrNull { it.isLive() }
        val out = ArrayList<RadialMenuView.Item>()
        out.add(item(if (rec) R.drawable.ic_stop else R.drawable.ic_camera,
            if (rec) "Stop && save" else "Start recording",
            active = rec, danger = rec,
            enabled = rec || h.canRecordComposite()) { h.toggleCompositeRecording() })
        if (!rec && !h.canRecordComposite())
            out.add(item(R.drawable.ic_info,
                "Needs a live camera + a video to record", enabled = false) { })
        out.add(item(if (h.anyPlaying()) R.drawable.ic_pause else R.drawable.ic_play,
            if (h.anyPlaying()) "Pause playback" else "Play", active = h.anyPlaying(),
            keepOpen = true) { h.toggleMasterPlay() })
        if (live != null) {
            val camRec = h.isCameraRecording(live)
            out.add(item(if (camRec) R.drawable.ic_stop else R.drawable.ic_camera,
                if (camRec) "Stop camera take" else "Camera take",
                active = camRec, danger = camRec) { h.toggleCameraRecord(live) })
        } else {
            out.add(item(R.drawable.ic_camera, "Add live camera") { h.addCameraLive() })
        }
        out.add(item(R.drawable.ic_screen, "Screen record") { h.addScreen() })
        out.add(item(R.drawable.ic_image, "Snapshot frame") { h.snapshotFrame() })
        out.add(folder(R.drawable.ic_flash, "Light") { lightRoot(h) })
        out.add(item(R.drawable.ic_reset, "Restart", keepOpen = true) { h.restart() })
        out
    }

    // ================= SETTINGS =================

    /**
     * One home for the things that are neither a source nor a canvas: output
     * quality, where finished videos land, how the studio rotates, plus the
     * project verbs that used to sit in their own ring.
     */
    fun settings(h: Host): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_settings, "Settings", "quality · folder · rotation"
    ) {
        val hudOn = h.isStatsHudOn()
        val pol = h.orientPolicyName()
        listOf(
            item(R.drawable.ic_export, "Export quality…") { h.openExportPanel() },
            item(R.drawable.ic_layers, "Save folder: " + h.saveFolderLabel()) { h.pickSaveFolder() },
            item(R.drawable.ic_reset, "Use default album", keepOpen = true) { h.resetSaveFolder() },
            folder(R.drawable.ic_aspect, "Rotation: " + when (pol) {
                "auto" -> "free"; "lock" -> "locked"; else -> "follow canvas"
            }) { rotation(h) },
            item(R.drawable.ic_info, if (hudOn) "Stats overlay: on" else "Stats overlay: off",
                active = hudOn, keepOpen = true) { h.toggleStatsHud() },
            folder(R.drawable.ic_edit, "Project") { project(h) },
            item(R.drawable.ic_info, "Diagnostics") { h.openDiagnostics() }
        )
    }

    fun rotation(h: Host): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_aspect, "Rotation", "how the studio follows your phone"
    ) {
        val pol = h.orientPolicyName()
        listOf(
            item(R.drawable.ic_aspect, "Follow canvas (16:9 = landscape)",
                active = pol == "canvas", keepOpen = true) { h.setOrientPolicyByName("canvas") },
            item(R.drawable.ic_switch, "Free rotation",
                active = pol == "auto", keepOpen = true) { h.setOrientPolicyByName("auto") },
            item(R.drawable.ic_lock, "Lock current orientation",
                active = pol == "lock", keepOpen = true) { h.setOrientPolicyByName("lock") }
        )
    }

    /** Top-level Light menu so flashlight is discoverable without selecting a source */
    fun lightRoot(h: Host): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_flash, "Light", "front · back · both · screen flash"
    ) {
        val live = h.project.layers.firstOrNull { it.isLive() }
        val out = ArrayList<RadialMenuView.Item>()
        if (live != null) {
            out.addAll(flashItems(h, live))
        } else {
            out.add(item(R.drawable.ic_camera, "Add live camera first") { h.addCameraLive() })
            val screenOn = h.isScreenLightOn()
            out.add(item(R.drawable.ic_eye, if (screenOn) "Screen light: on" else "Screen light: off", active = screenOn, keepOpen = true) { h.toggleScreenLight() })
        }
        out
    }

    private fun flashItems(h: Host, l: Layer): List<RadialMenuView.Item> {
        val out = ArrayList<RadialMenuView.Item>()
        val frontHas = h.hasFrontTorch()
        val backHas = h.hasBackTorch()
        val frontOn = h.isFrontTorchOn()
        val backOn = h.isBackTorchOn()
        val bothOn = h.isBothTorchOn()
        val screenOn = h.isScreenLightOn()
        if (frontHas) out.add(item(R.drawable.ic_flash, if (frontOn) "Front flash: on" else "Front flash: off", active = frontOn, badge = if (frontOn) "LED" else null, keepOpen = true) { h.toggleFrontTorch() })
        else out.add(item(R.drawable.ic_flash, "Front: no LED — use screen light", enabled = false) { })
        if (backHas) out.add(item(R.drawable.ic_flash, if (backOn) "Back flash: on" else "Back flash: off", active = backOn, badge = if (backOn) "LED" else null, keepOpen = true) { h.toggleBackTorch() })
        else out.add(item(R.drawable.ic_flash, "Back: no LED", enabled = false) { })
        if (frontHas && backHas) out.add(item(R.drawable.ic_flash, if (bothOn) "Both flashes: on" else "Both flashes: off", active = bothOn, keepOpen = true) { h.toggleBothTorch() })
        out.add(item(R.drawable.ic_eye, if (screenOn) "Screen light: on" else "Screen light: off", active = screenOn, badge = if (screenOn) "BRIGHT" else null, keepOpen = true) { h.toggleScreenLight() })
        out.add(item(R.drawable.ic_switch, if (l.camFacing == 0) "Switch to back cam" else "Switch to front cam", keepOpen = true) { h.switchCameraFacing(l) })
        return out
    }

    // ================= SOURCES =================

    /** One petal per source, top-most first; each opens that source's ring. */
    fun sources(h: Host): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_layers, "Sources", "every source · tap for its controls"
    ) {
        val p = h.project
        val out = ArrayList<RadialMenuView.Item>()
        // "Add" is always available here, not just when the canvas is empty —
        // the dedicated Sources wheel is now the one place to both manage
        // existing sources and add new ones.
        out.add(folder(R.drawable.ic_add,
            if (p.layers.isEmpty()) "Add the first source" else "Add source") { add(h) })
        p.layers.indices.reversed().forEach { i ->
            val l = p.layers[i]
            out.add(folder(Ic.typeIcon(l.type), l.name.ifBlank { l.type.label }, badge = badgeOf(h, l)) {
                source(h, l.id)
            })
        }
        out
    }

    private fun badgeOf(h: Host, l: Layer): String? = when {
        l.isLive() && h.isCameraRecording(l) -> "REC"
        l.isLive() -> "LIVE"
        !l.visible -> "HIDDEN"
        l.locked -> "LOCK"
        l.solo -> "SOLO"
        l.isClip() && h.ctrl.effectiveMuted(l) -> "MUTED"
        l.isClip() && !l.playing -> "PAUSED"
        else -> null
    }

    /** The per-source ring — every verb the old quick bar + sheet exposed. */
    fun source(h: Host, id: String): RadialMenuView.Level = RadialMenuView.Level(
        Ic.typeIcon(h.project.layerById(id)?.type ?: LayerType.VIDEO),
        h.project.layerById(id)?.name?.ifBlank { "Source" } ?: "Source",
        "controls for this source"
    ) {
        val l = h.project.layerById(id)
        if (l == null) emptyList() else {
            val out = ArrayList<RadialMenuView.Item>()
            out.add(item(R.drawable.ic_check, "Select on canvas",
                active = h.selected()?.id == l.id) { h.selectId(l.id) })
            out.add(item(if (l.visible) R.drawable.ic_eye_off else R.drawable.ic_eye,
                if (l.visible) "Hide" else "Show", active = !l.visible, keepOpen = true) {
                h.hideSource(l)
            })

            if (l.isLive()) {
                val rec = h.isCameraRecording(l)
                out.add(item(if (rec) R.drawable.ic_stop else R.drawable.ic_camera,
                    if (rec) "Stop take" else "Record take",
                    active = rec, danger = rec) { h.toggleCameraRecord(l) })
                out.add(item(R.drawable.ic_switch, "Switch cam", keepOpen = true) {
                    h.switchCameraFacing(l)
                })
                out.add(item(R.drawable.ic_loop, if (l.mirror) "Mirror: on" else "Mirror: off",
                    active = l.mirror, keepOpen = true) { h.toggleCameraMirror(l) })
                out.add(folder(R.drawable.ic_flash, "Light",
                    badge = if (h.isTorchOn(l) || h.isScreenLightOn()) "ON" else null) {
                    flash(h, l.id)
                })
            } else if (l.isClip()) {
                out.add(item(if (l.playing) R.drawable.ic_pause else R.drawable.ic_play,
                    if (l.playing) "Pause" else "Play", active = !l.playing, keepOpen = true) {
                    h.toggleSourcePlay(l)
                })
                val m = h.ctrl.effectiveMuted(l)
                out.add(item(if (m) R.drawable.ic_volume_off else R.drawable.ic_volume,
                    if (m) "Unmute" else "Mute", active = m, keepOpen = true) {
                    h.ctrl.toggleMuted(l.id)
                })
                out.add(item(R.drawable.ic_loop, if (l.loop) "Loop: on" else "Loop: off",
                    active = l.loop, keepOpen = true) { h.ctrl.toggleLoop(l.id) })
                out.add(item(R.drawable.ic_star, if (l.solo) "Solo: on" else "Solo",
                    active = l.solo, keepOpen = true) { h.ctrl.toggleSolo(l.id) })
            }

            if (l.isText()) {
                out.add(item(R.drawable.ic_edit, "Edit text") { h.editText(l) })
                out.add(item(R.drawable.ic_palette, "Colour", keepOpen = true) { h.cycleTextColor(l) })
            } else {
                // Naming standard (used in every surface): Fit = whole frame,
                // Fill = crop to box. Never "Fill" for background promotion.
                out.add(item(if (l.fit == Layer.FIT_FIT) R.drawable.ic_fit else R.drawable.ic_fill,
                    if (l.fit == Layer.FIT_FIT) "Fit: whole frame" else "Fill: crop to box",
                    active = l.fit == Layer.FIT_FIT, keepOpen = true) { h.ctrl.toggleFit(l.id) })
            }

            out.add(item(if (l.locked) R.drawable.ic_lock else R.drawable.ic_lock_open,
                if (l.locked) "Unlock" else "Lock", active = l.locked, keepOpen = true) {
                h.ctrl.toggleLocked(l.id)
            })
            out.add(folder(R.drawable.ic_drag, "Arrange") { arrange(h, l.id) })
            out.add(item(R.drawable.ic_settings, "Advanced…") { h.openAdvanced(l) })
            if (!l.isLive()) out.add(item(R.drawable.ic_copy, "Duplicate") {
                h.ctrl.duplicate(l.id)
            })
            out.add(item(R.drawable.ic_delete, "Delete", danger = true) { h.deleteSource(l) })
            out
        }
    }

    /**
     * LIGHT RING — flashlight for both cameras + both-on + screen flash.
     * Shows Front LED, Back LED, Both, and Screen Light as independent toggles
     * so a device with LEDs on both sides can run front, back or both while recording.
     */
    fun flash(h: Host, id: String): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_flash, "Light", "front · back · both · screen flash"
    ) {
        val l = h.project.layerById(id) ?: return@Level emptyList()
        flashItems(h, l)
    }

    fun arrange(h: Host, id: String): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_drag, "Arrange", "position · size · z-order"
    ) {
        val l = h.project.layerById(id)
        if (l == null) emptyList() else listOf(
            item(R.drawable.ic_up, "Bring forward", keepOpen = true) { h.ctrl.moveZ(l.id, "up") },
            item(R.drawable.ic_down, "Send backward", keepOpen = true) { h.ctrl.moveZ(l.id, "down") },
            item(R.drawable.ic_up, "To front", keepOpen = true) { h.ctrl.moveZ(l.id, "front") },
            item(R.drawable.ic_down, "To back", keepOpen = true) { h.ctrl.moveZ(l.id, "back") },
            item(R.drawable.ic_reset, "Centre + unrotate", keepOpen = true) { h.ctrl.resetGeometry(l.id) },
            item(R.drawable.ic_corner_tl, "Corner: top-left", keepOpen = true) { h.ctrl.anchor(l.id, "tl") },
            item(R.drawable.ic_corner_tr, "Corner: top-right", keepOpen = true) { h.ctrl.anchor(l.id, "tr") },
            item(R.drawable.ic_corner_bl, "Corner: bottom-left", keepOpen = true) { h.ctrl.anchor(l.id, "bl") },
            item(R.drawable.ic_corner_br, "Corner: bottom-right", keepOpen = true) { h.ctrl.anchor(l.id, "br") },
            item(R.drawable.ic_fill, "Set as background") {
                h.ctrl.setAsCanvasBackground(l.id)
            }
        )
    }

    // ================= AUDIO (radial mixer) =================

    /**
     * Audio wheel — one folder-petal per audio-capable source, each opening a
     * stepped mixer sub-ring (mute / vol- / vol+ / solo), plus a Mic folder
     * for the live camera's input gain. No sliders here on purpose — every
     * other ring in this app is a petal tree, so volume is stepped 10% at a
     * time via repeatable taps rather than introducing a one-off widget type.
     */
    fun audioWheel(h: Host): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_volume, "Audio", "mute · volume · mic gain"
    ) {
        val p = h.project
        val out = ArrayList<RadialMenuView.Item>()
        // P1-2: sliders live in the mixer sheet (the wheel keeps stepped control).
        out.add(item(R.drawable.ic_volume, "Mixer panel") { h.openMixerPanel() })
        val live = p.layers.firstOrNull { it.isLive() }
        if (live != null) {
            out.add(folder(R.drawable.ic_camera, "Mic",
                badge = "${(h.micGain() * 100).toInt()}%") { micLevel(h) })
        }
        val clips = p.layers.filter { it.isClip() }
        if (clips.isEmpty() && live == null) {
            out.add(item(R.drawable.ic_info, "No audio sources yet", enabled = false) { })
        }
        clips.forEach { l ->
            val muted = h.ctrl.effectiveMuted(l)
            out.add(folder(Ic.typeIcon(l.type), l.name.ifBlank { l.type.label },
                badge = if (muted) "MUTED" else "${(l.volume * 100).toInt()}%") { audioLevel(h, l.id) })
        }
        out
    }

    fun audioLevel(h: Host, id: String): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_volume, h.project.layerById(id)?.name?.ifBlank { "Source" } ?: "Source",
        "volume · mute · solo"
    ) {
        val l = h.project.layerById(id)
        if (l == null) emptyList() else {
            val muted = h.ctrl.effectiveMuted(l)
            listOf(
                item(R.drawable.ic_up, "Volume +10%", keepOpen = true) {
                    h.ctrl.setVolume(l.id, l.volume + 0.1f)
                },
                item(R.drawable.ic_down, "Volume −10%", keepOpen = true) {
                    h.ctrl.setVolume(l.id, l.volume - 0.1f)
                },
                item(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume,
                    if (muted) "Unmute" else "Mute", active = muted, keepOpen = true) {
                    h.ctrl.toggleMuted(l.id)
                },
                item(R.drawable.ic_star, if (l.solo) "Solo: on" else "Solo",
                    active = l.solo, keepOpen = true) { h.ctrl.toggleSolo(l.id) }
            )
        }
    }

    fun micLevel(h: Host): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_camera, "Mic gain", "${(h.micGain() * 100).toInt()}% of raw input"
    ) {
        listOf(
            item(R.drawable.ic_up, "Gain +10%", keepOpen = true) { h.setMicGain(h.micGain() + 0.1f) },
            item(R.drawable.ic_down, "Gain −10%", keepOpen = true) { h.setMicGain(h.micGain() - 0.1f) },
            item(R.drawable.ic_reset, "Reset to 100%", keepOpen = true) { h.setMicGain(1f) }
        )
    }

    // ================= PLAY / STOP =================

    /**
     * Play/Stop wheel — master playback plus composite recording in one
     * place. Recording auto-exports on stop (see EditorActivity.stopComposite
     * Recording -> publishAndReport); the "View" action in that dialog then
     * opens the saved file's location, not just a video player.
     */
    fun playStop(h: Host): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_play, "Play / Record", "playback and composite recording"
    ) {
        val recording = h.isRecordingComposite()
        val out = ArrayList<RadialMenuView.Item>()
        out.add(item(if (h.anyPlaying()) R.drawable.ic_pause else R.drawable.ic_play,
            if (h.anyPlaying()) "Pause" else "Play", active = h.anyPlaying(), keepOpen = true) {
            h.toggleMasterPlay()
        })
        out.add(item(if (recording) R.drawable.ic_stop else R.drawable.ic_camera,
            if (recording) "Stop && save" else "Start recording",
            active = recording, danger = recording,
            enabled = recording || h.canRecordComposite()) {
            h.toggleCompositeRecording()
        })
        if (!recording && !h.canRecordComposite())
            out.add(item(R.drawable.ic_info,
                "Needs a live camera + a video to record", enabled = false) { })
        out.add(item(R.drawable.ic_reset, "Restart", keepOpen = true) { h.restart() })
        out
    }

    // ================= TEST (placeholder — filled in later) =================

    /** Stub wheel: reserved slot, not wired to anything yet. */
    fun testWheel(h: Host): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_star, "Test", "placeholder — not wired up yet"
    ) {
        listOf(
            item(R.drawable.ic_info, "Nothing here yet", enabled = false) { }
        )
    }

    // ================= ADD =================

    fun add(h: Host): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_add, "Add source",
        if (h.project.layers.isEmpty()) "the first one fills the canvas" else "added as a PiP overlay"
    ) {
        listOf(
            item(R.drawable.ic_camera, "Camera (live on canvas)") { h.addCameraLive() },
            item(R.drawable.ic_video, "Video file") { h.addVideo() },
            item(R.drawable.ic_image, "Image") { h.addImage() },
            item(R.drawable.ic_screen, "Screen record") { h.addScreen() },
            item(R.drawable.ic_text, "Text overlay") { h.addTextSource() }
            // NOTE: the old 6th item "Fullscreen take (fallback)" is gone on
            // purpose — the fullscreen recorder now only appears automatically
            // when the live camera fails on a device. One camera path to learn.
        )
    }

    // ================= CANVAS =================

    fun canvas(h: Host): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_aspect, "Canvas", "ratio · background · layout"
    ) {
        val p = h.project
        listOf(
            item(R.drawable.ic_aspect, "16:9", active = p.aspect == Aspect.R169, keepOpen = true) {
                h.setAspect(Aspect.R169)
            },
            item(R.drawable.ic_aspect, "9:16", active = p.aspect == Aspect.R916, keepOpen = true) {
                h.setAspect(Aspect.R916)
            },
            item(R.drawable.ic_aspect, "1:1", active = p.aspect == Aspect.R11, keepOpen = true) {
                h.setAspect(Aspect.R11)
            },
            folder(R.drawable.ic_palette, "Background") { background(h) },
            item(R.drawable.ic_fullscreen, "Full canvas") { h.enterFullCanvas() },
            item(R.drawable.ic_fit, "Fit all sources", keepOpen = true) { h.fitAllSources() },
            item(R.drawable.ic_fill, "Selection as background", keepOpen = true) {
                val s = h.selected()
                if (s == null) h.toast("Select a source first") else h.ctrl.setAsCanvasBackground(s.id)
            }
        )
    }

    fun background(h: Host): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_palette, "Background", "canvas colour"
    ) {
        listOf(
            item(R.drawable.ic_palette, "Dark", keepOpen = true) { h.setBg(0xFF101418.toInt()) },
            item(R.drawable.ic_palette, "Black", keepOpen = true) { h.setBg(0xFF000000.toInt()) },
            item(R.drawable.ic_palette, "White", keepOpen = true) { h.setBg(0xFFFFFFFF.toInt()) },
            item(R.drawable.ic_palette, "Orange", keepOpen = true) { h.setBg(0xFFFF5A2C.toInt()) },
            item(R.drawable.ic_palette, "Navy", keepOpen = true) { h.setBg(0xFF1E3C78.toInt()) },
            item(R.drawable.ic_palette, "Green", keepOpen = true) { h.setBg(0xFF14785A.toInt()) },
            item(R.drawable.ic_palette, "Purple", keepOpen = true) { h.setBg(0xFF781E5A.toInt()) }
        )
    }

    // ================= EXPORT =================

    fun export(h: Host): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_export, "Export", "render the composition"
    ) {
        listOf(
            item(R.drawable.ic_export, "Quick export 720p30") { h.quickExport() },
            item(R.drawable.ic_settings, "Export settings…") { h.openExportPanel() }
        )
    }

    // ================= PROJECT =================

    fun project(h: Host): RadialMenuView.Level = RadialMenuView.Level(
        R.drawable.ic_settings, "Project", h.project.name
    ) {
        val hudOn = h.isStatsHudOn()
        listOf(
            item(R.drawable.ic_edit, "Rename project") { h.renameProject() },
            item(R.drawable.ic_check, "Save now", keepOpen = true) { h.saveNow() },
            item(R.drawable.ic_image, "Snapshot frame") { h.snapshotFrame() },
            item(R.drawable.ic_info, if (hudOn) "Stats overlay: on" else "Stats overlay: off",
                active = hudOn, keepOpen = true) { h.toggleStatsHud() },
            item(R.drawable.ic_undo, "Undo", keepOpen = true) { h.undo() },
            item(R.drawable.ic_redo, "Redo", keepOpen = true) { h.redo() },
            item(R.drawable.ic_info, "Diagnostics") { h.openDiagnostics() },
            item(R.drawable.ic_back, "Close project", danger = true) { h.closeProject() }
        )
    }
}
