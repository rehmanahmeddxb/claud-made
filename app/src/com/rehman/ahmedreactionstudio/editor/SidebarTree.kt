package com.rehman.ahmedreactionstudio.editor

import com.rehman.ahmedreactionstudio.R
import com.rehman.ahmedreactionstudio.core.Aspect
import com.rehman.ahmedreactionstudio.core.Layer

/**
 * THE CANVAS-FIRST MENU TREE.
 *
 * The Studio canvas owns 100 % of the interface, so this tree is the *only*
 * place a control can live. Everything the removed chrome used to offer — the
 * right-edge radial triggers, the per-source ◉ wheel, the landscape source
 * rail, the floating Quick Control Bar, the bottom transport row and the ⋮
 * overflow — is mapped here as a hierarchy:
 *
 *     Section  →  Sub-menu  →  Sub-sub-menu  →  (deeper if it needs to be)
 *
 * Levels never own state. Every row reads live [RadialMenus.Host] state on
 * each [build] and every verb goes through [com.rehman.ahmedreactionstudio.core.SourceController]
 * or a host callback, so undo/redo + "preview == export" still hold by
 * construction and no action is a toast pretending to be a feature.
 *
 * Item ids are stable (`layer.<uuid>`, `aspect`, `bg`, …) so the sidebar can
 * restore expand/collapse state across rebuilds and deep-link straight at a
 * source when the canvas is long-pressed.
 */
object SidebarTree {

    // ────────────────────────── data model ──────────────────────────

    /** One top-level branch of the menu. */
    data class Section(
        val id: String,
        val icon: Int,
        val label: String,
        val badge: String? = null,
        val children: List<Item>,
        var expanded: Boolean = false
    )

    /**
     * One row. A row is either a FOLDER ([children] != null → it opens the
     * next level) or a LEAF ([action] != null → it fires).
     *
     * [keepOpen] is the "toggle" contract: the action fires, the row stays in
     * the menu and the tree re-renders with the new state, so hiding/muting/
     * locking a source never dumps the user back onto the canvas. A plain leaf
     * closes the menu after firing — the menu is a means, not a place to live.
     *
     * NOTE: [action] is deliberately the LAST parameter so the classic
     * `Item(…) { … }` trailing-lambda call site still binds to the action and
     * not to [expanded] (that exact ordering bug is what stopped the previous
     * revision from compiling).
     */
    class Item(
        val icon: Int,
        val label: String,
        val id: String? = null,
        val badge: String? = null,
        val active: Boolean = false,
        val danger: Boolean = false,
        val enabled: Boolean = true,
        val keepOpen: Boolean = false,
        val children: List<Item>? = null,
        var expanded: Boolean = false,
        val action: (() -> Unit)? = null
    ) {
        val isFolder: Boolean get() = children != null
        /** identity used for expansion state + deep links */
        val key: String get() = id ?: label
    }

    /** leaf that fires and closes the menu */
    private fun L(icon: Int, label: String, id: String? = null, badge: String? = null,
                  active: Boolean = false, danger: Boolean = false, enabled: Boolean = true,
                  action: () -> Unit): Item =
        Item(icon = icon, label = label, id = id, badge = badge, active = active,
            danger = danger, enabled = enabled, action = action)

    /** leaf that fires and keeps the menu open (a toggle) */
    private fun T(icon: Int, label: String, id: String? = null, badge: String? = null,
                  active: Boolean = false, danger: Boolean = false, enabled: Boolean = true,
                  action: () -> Unit): Item =
        Item(icon = icon, label = label, id = id, badge = badge, active = active,
            danger = danger, enabled = enabled, keepOpen = true, action = action)

    /** folder — opens the next level */
    private fun F(icon: Int, label: String, id: String? = null, badge: String? = null,
                  children: List<Item>): Item =
        Item(icon = icon, label = label, id = id, badge = badge, children = children)

    /** a row that explains why something is not available (never tappable) */
    private fun info(label: String): Item =
        Item(icon = R.drawable.ic_info, label = label, enabled = false)

    // ────────────────────────── tree builder ──────────────────────────

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
            addSection(h),
            sourcesSection(h, n),
            selectedSection(h),
            playbackSection(h),
            audioSection(h, audioN, live),
            recordSection(h, rec, live, lightOn),
            canvasSection(h),
            exportSection(h),
            projectSection(h),
            settingsSection(h)
        )
    }

    /** Row-level state for the section badges (single source of truth). */
    private fun badgeOf(h: RadialMenus.Host, l: Layer): String? = when {
        l.isLive() && h.isCameraRecording(l) -> "REC"
        l.isLive() -> "LIVE"
        !l.visible -> "HIDDEN"
        l.locked -> "LOCK"
        l.solo -> "SOLO"
        l.isClip() && h.ctrl.effectiveMuted(l) -> "MUTED"
        l.isClip() && !l.playing -> "PAUSED"
        else -> null
    }

    // ────────────────────── 1. ADD ──────────────────────

    private fun addItems(h: RadialMenus.Host): List<Item> = listOf(
        L(R.drawable.ic_camera, "Camera (live on canvas)", id = "cam-live") {
            h.addCameraLive()
        },
        L(R.drawable.ic_video, "Video file", id = "video") { h.addVideo() },
        L(R.drawable.ic_image, "Image", id = "image") { h.addImage() },
        L(R.drawable.ic_text, "Text overlay", id = "text") { h.addTextSource() },
        L(R.drawable.ic_screen, "Screen record", id = "screen") { h.addScreen() },
        L(R.drawable.ic_switch, "Fullscreen camera take", id = "take") {
            h.addCameraTake()
        }
    )

    private fun addSection(h: RadialMenus.Host): Section {
        val empty = h.project.layers.isEmpty()
        val kids = addItems(h)
        return Section("add", R.drawable.ic_add,
            if (empty) "Add the first source" else "Add source",
            if (empty) "start here" else null, kids, expanded = empty)
    }

    // ────────────────────── 2. SOURCES ──────────────────────

    private fun sourcesSection(h: RadialMenus.Host, n: Int): Section {
        val p = h.project
        val kids = ArrayList<Item>()
        // top-most source first, exactly like the OBS dock it replaces
        for (l in p.layers.asReversed()) kids.add(sourceFolder(h, l))
        kids.add(F(R.drawable.ic_add, if (p.layers.isEmpty()) "Add the first source"
            else "Add another source", id = "add-here", children = addItems(h)))
        kids.add(L(R.drawable.ic_layers, "Source dock / mini mixer…", id = "dock") {
            h.openDockPanel()
        })
        kids.add(if (h.selected() == null) info("Nothing selected to remove")
        else L(R.drawable.ic_delete, "Remove selected source", id = "remove-sel",
            danger = true) { h.selected()?.let { h.deleteSource(it) } })

        return Section("sources", R.drawable.ic_layers, "Sources",
            if (n > 0) "$n" else "empty", kids, expanded = n > 0)
    }

    /**
     * One source → every verb that source supports. This is the level the old
     * per-source radial wheel and the floating Quick Control Bar occupied, and
     * it is where the sub-sub-menus (Fit, Arrange, Audio, Opacity, Light) live.
     */
    private fun sourceFolder(h: RadialMenus.Host, l: Layer): Item {
        val out = ArrayList<Item>()
        out.add(T(R.drawable.ic_check, "Select on canvas", id = "select",
            active = h.selected()?.id == l.id) { h.selectId(l.id) })
        out.add(T(if (l.visible) R.drawable.ic_eye else R.drawable.ic_eye_off,
            if (l.visible) "Hide" else "Show", id = "vis", active = !l.visible) {
            h.hideSource(l)
        })
        out.add(T(if (l.locked) R.drawable.ic_lock else R.drawable.ic_lock_open,
            if (l.locked) "Unlock" else "Lock", id = "lock", active = l.locked) {
            h.ctrl.toggleLocked(l.id)
        })

        if (l.isLive()) {
            val rec = h.isCameraRecording(l)
            out.add(L(if (rec) R.drawable.ic_stop else R.drawable.ic_camera,
                if (rec) "Stop camera take" else "Record camera take",
                id = "take", active = rec, danger = rec) { h.toggleCameraRecord(l) })
            out.add(T(R.drawable.ic_switch,
                if (l.camFacing == Layer.FACING_FRONT) "Switch to back camera" else "Switch to front camera",
                id = "facing") { h.switchCameraFacing(l) })
            out.add(T(R.drawable.ic_loop, if (l.mirror) "Mirror: on" else "Mirror: off",
                id = "mirror", active = l.mirror) { h.toggleCameraMirror(l) })
            out.add(F(R.drawable.ic_flash, "Light", id = "light",
                badge = if (h.isTorchOn(l) || h.isScreenLightOn()) "ON" else null,
                children = lightItems(h, l)))
        } else if (l.isClip()) {
            out.add(T(if (l.playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (l.playing) "Pause source" else "Play source",
                id = "play", active = !l.playing) { h.toggleSourcePlay(l) })
            out.add(T(R.drawable.ic_loop, if (l.loop) "Loop: on" else "Loop: off",
                id = "loop", active = l.loop) { h.ctrl.toggleLoop(l.id) })
            val m = h.ctrl.effectiveMuted(l)
            out.add(T(if (m) R.drawable.ic_volume_off else R.drawable.ic_volume,
                if (m) "Unmute" else "Mute", id = "mute", active = m) {
                h.ctrl.toggleMuted(l.id)
            })
            out.add(T(R.drawable.ic_star, if (l.solo) "Solo: on" else "Solo",
                id = "solo", active = l.solo) { h.ctrl.toggleSolo(l.id) })
            out.add(F(R.drawable.ic_volume, "Volume", id = "vol",
                badge = "${(l.volume * 100).toInt()}%", children = volumeItems(h, l)))
        }

        if (l.isText()) {
            out.add(L(R.drawable.ic_edit, "Edit text", id = "edit") { h.editText(l) })
            out.add(T(R.drawable.ic_palette, "Cycle text colour", id = "colour") {
                h.cycleTextColor(l)
            })
        } else {
            out.add(F(R.drawable.ic_fit, "Frame fit", id = "fit", badge = l.fit,
                children = listOf(
                    // Naming standard (used in every surface): Fit = whole frame,
                    // Fill = crop to box. Never "Fill" for background promotion.
                    T(R.drawable.ic_fill, "Fill: crop to box", id = "fill",
                        active = l.fit == Layer.FIT_FILL) { h.ctrl.setFit(l.id, Layer.FIT_FILL) },
                    T(R.drawable.ic_fit, "Fit: whole frame", id = "fitbox",
                        active = l.fit == Layer.FIT_FIT) { h.ctrl.setFit(l.id, Layer.FIT_FIT) }
                )))
        }

        out.add(F(R.drawable.ic_reset, "Opacity", id = "opacity",
            badge = "${(l.opacity * 100).toInt()}%",
            children = listOf(
                T(R.drawable.ic_up, "Opaque +10%", id = "op+", enabled = l.opacity < 1f) {
                    h.ctrl.setOpacity(l.id, l.opacity + 0.1f)
                },
                T(R.drawable.ic_down, "Transparent −10%", id = "op-") {
                    h.ctrl.setOpacity(l.id, l.opacity - 0.1f)
                },
                T(R.drawable.ic_check, "Fully opaque", id = "op100",
                    active = l.opacity >= 0.999f) { h.ctrl.setOpacity(l.id, 1f) }
            )))

        out.add(F(R.drawable.ic_drag, "Arrange", id = "arrange", children = arrangeItems(h, l)))
        out.add(L(R.drawable.ic_settings, "Advanced properties…", id = "adv") {
            h.openAdvanced(l)
        })
        // "can the live camera be cloned" is a controller rule, not a UI guess
        val canDup = h.ctrl.canDuplicate(l.id)
        out.add(L(R.drawable.ic_copy, "Duplicate", id = "dup", enabled = canDup,
            badge = if (canDup) null else "live cam") {
            val nid = h.ctrl.duplicate(l.id)
            if (nid == null) h.toast("The live camera cannot be duplicated — record a take instead")
            else { h.selectId(nid); h.toast("Duplicated ${l.name.ifBlank { l.type.label }}") }
        })
        out.add(L(R.drawable.ic_delete, "Delete source", id = "del", danger = true) {
            h.deleteSource(l)
        })

        return F(Ic.typeIcon(l.type), l.name.ifBlank { l.type.label },
            id = "layer." + l.id, badge = badgeOf(h, l), children = out)
    }

    private fun arrangeItems(h: RadialMenus.Host, l: Layer): List<Item> = listOf(
        T(R.drawable.ic_up, "Bring forward", id = "up") { h.ctrl.moveZ(l.id, "up") },
        T(R.drawable.ic_down, "Send backward", id = "down") { h.ctrl.moveZ(l.id, "down") },
        T(R.drawable.ic_up, "To front", id = "front") { h.ctrl.moveZ(l.id, "front") },
        T(R.drawable.ic_down, "To back (canvas background)", id = "back") {
            h.ctrl.moveZ(l.id, "back")
        },
        T(R.drawable.ic_center, "Centre on canvas", id = "centre") { h.ctrl.center(l.id) },
        T(R.drawable.ic_reset, "Centre + unrotate", id = "reset") {
            h.ctrl.resetGeometry(l.id)
        },
        T(R.drawable.ic_corner_tl, "Corner: top-left", id = "tl") { h.ctrl.anchor(l.id, "tl") },
        T(R.drawable.ic_corner_tr, "Corner: top-right", id = "tr") { h.ctrl.anchor(l.id, "tr") },
        T(R.drawable.ic_corner_bl, "Corner: bottom-left", id = "bl") { h.ctrl.anchor(l.id, "bl") },
        T(R.drawable.ic_corner_br, "Corner: bottom-right", id = "br") { h.ctrl.anchor(l.id, "br") },
        L(R.drawable.ic_fill, "Set as canvas background", id = "asbg",
            enabled = !l.isText()) { h.ctrl.setAsCanvasBackground(l.id) },
        T(R.drawable.ic_fit, "Fit all sources to canvas", id = "fitall") { h.fitAllSources() }
    )

    private fun volumeItems(h: RadialMenus.Host, l: Layer): List<Item> = listOf(
        T(R.drawable.ic_up, "Volume +10%", id = "v+", enabled = l.volume < 1f) {
            h.ctrl.setVolume(l.id, l.volume + 0.1f)
        },
        T(R.drawable.ic_down, "Volume −10%", id = "v-") {
            h.ctrl.setVolume(l.id, l.volume - 0.1f)
        },
        T(R.drawable.ic_reset, "Reset to 100%", id = "v100", active = l.volume >= 0.999f) {
            h.ctrl.setVolume(l.id, 1f)
        },
        T(if (h.ctrl.effectiveMuted(l)) R.drawable.ic_volume_off else R.drawable.ic_volume,
            if (h.ctrl.effectiveMuted(l)) "Unmute" else "Mute", id = "vmute") {
            h.ctrl.toggleMuted(l.id)
        },
        T(R.drawable.ic_star, if (l.solo) "Solo: on" else "Solo", id = "vsolo",
            active = l.solo) { h.ctrl.toggleSolo(l.id) }
    )

    /** Front LED · rear LED · both · screen light · only what the device has. */
    private fun lightItems(h: RadialMenus.Host, l: Layer): List<Item> {
        val out = ArrayList<Item>()
        val frontHas = h.hasFrontTorch()
        val backHas = h.hasBackTorch()
        if (h.hasTorch(l)) out.add(T(R.drawable.ic_flash,
            if (h.isTorchOn(l)) "Torch (open lens): on" else "Torch (open lens): off",
            id = "torch", active = h.isTorchOn(l)) { h.toggleTorch(l) })
        if (frontHas) out.add(T(R.drawable.ic_flash,
            if (h.isFrontTorchOn()) "Front flash: on" else "Front flash: off",
            id = "front", active = h.isFrontTorchOn(), badge = "LED") { h.toggleFrontTorch() })
        else out.add(info("Front: no LED — use screen light"))
        if (backHas) out.add(T(R.drawable.ic_flash,
            if (h.isBackTorchOn()) "Back flash: on" else "Back flash: off",
            id = "back", active = h.isBackTorchOn(), badge = "LED") { h.toggleBackTorch() })
        else out.add(info("Back: no LED"))
        if (frontHas && backHas) out.add(T(R.drawable.ic_flash,
            if (h.isBothTorchOn()) "Both flashes: on" else "Both flashes: off",
            id = "both", active = h.isBothTorchOn()) { h.toggleBothTorch() })
        val screenOn = h.isScreenLightOn()
        out.add(T(R.drawable.ic_eye, if (screenOn) "Screen light: on" else "Screen light: off",
            id = "screen", active = screenOn, badge = if (screenOn) "BRIGHT" else null) {
            h.toggleScreenLight()
        })
        return out
    }

    // ────────────────────── 3. SELECTED SOURCE ──────────────────────

    private fun selectedSection(h: RadialMenus.Host): Section {
        val s = h.selected()
        if (s == null) {
            return Section("controls", R.drawable.ic_settings, "Selected source", null,
                listOf(info("Tap a source on the canvas, or long-press it to jump here")))
        }
        return Section("controls", R.drawable.ic_settings, "Selected: " +
            s.name.ifBlank { s.type.label }, s.type.label,
            sourceFolder(h, s).children ?: emptyList(), expanded = false)
    }

    // ────────────────────── 4. PLAYBACK ──────────────────────

    private fun playbackSection(h: RadialMenus.Host): Section {
        val playing = h.anyPlaying()
        val kids = listOf(
            T(if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (playing) "Pause" else "Play", id = "play", active = playing,
                badge = if (playing) "▶" else null) { h.toggleMasterPlay() },
            L(R.drawable.ic_stop, "Stop (pause + rewind)", id = "stop") { h.restart() },
            T(R.drawable.ic_reset, "Restart from 0:00", id = "restart") { h.restart() },
            F(R.drawable.ic_drag, "Step", id = "step", children = listOf(
                L(R.drawable.ic_back, "Back 1 s", id = "b1") { h.nudge(-1000L) },
                L(R.drawable.ic_back, "Back 5 s", id = "b5") { h.nudge(-5000L) },
                L(R.drawable.ic_up, "Forward 1 s", id = "f1") { h.nudge(1000L) },
                L(R.drawable.ic_up, "Forward 5 s", id = "f5") { h.nudge(5000L) }
            )),
            L(R.drawable.ic_image, "Snapshot this frame", id = "snap") { h.snapshotFrame() }
        )
        return Section("playback", R.drawable.ic_play, "Playback",
            if (playing) "▶" else null, kids)
    }

    // ────────────────────── 5. AUDIO ──────────────────────

    private fun audioSection(h: RadialMenus.Host, audioN: Int, live: Layer?): Section {
        val p = h.project
        val out = ArrayList<Item>()
        out.add(L(R.drawable.ic_volume, "Mixer panel (sliders)…", id = "mixer") {
            h.openMixerPanel()
        })
        if (live != null) {
            out.add(F(R.drawable.ic_camera, "Mic gain", id = "mic",
                badge = "${(h.micGain() * 100).toInt()}%",
                children = listOf(
                    T(R.drawable.ic_up, "Gain +10%", id = "m+") {
                        h.setMicGain(h.micGain() + 0.1f)
                    },
                    T(R.drawable.ic_down, "Gain −10%", id = "m-") {
                        h.setMicGain(h.micGain() - 0.1f)
                    },
                    T(R.drawable.ic_reset, "Reset to 100%", id = "m100",
                        active = Math.abs(h.micGain() - 1f) < 0.001f) { h.setMicGain(1f) }
                )))
        }
        val clips = p.layers.filter { it.isClip() }
        for (l in clips) {
            val muted = h.ctrl.effectiveMuted(l)
            out.add(F(Ic.typeIcon(l.type), l.name.ifBlank { l.type.label },
                id = "aud." + l.id,
                badge = if (muted) "MUTED" else "${(l.volume * 100).toInt()}%",
                children = volumeItems(h, l)))
        }
        if (clips.isEmpty() && live == null) out.add(info("No audio sources yet"))
        return Section("audio", R.drawable.ic_volume, "Audio",
            if (audioN > 0) "$audioN" else null, out)
    }

    // ────────────────────── 6. RECORD ──────────────────────

    private fun recordSection(h: RadialMenus.Host, rec: Boolean, live: Layer?,
                              lightOn: Boolean): Section {
        val p = h.project
        val out = ArrayList<Item>()
        out.add(if (rec) L(R.drawable.ic_stop, "Stop && save the take", id = "stoprec",
            active = true, danger = true) { h.toggleCompositeRecording() }
        else L(R.drawable.ic_camera, "Start recording", id = "startrec",
            enabled = h.canRecordComposite(),
            badge = if (h.canRecordComposite()) null else "need cam + video") {
            h.toggleCompositeRecording()
        })
        if (live != null) {
            val camRec = h.isCameraRecording(live)
            out.add(L(if (camRec) R.drawable.ic_stop else R.drawable.ic_camera,
                if (camRec) "Stop camera take" else "Camera take only",
                id = "camtake", active = camRec, danger = camRec) {
                h.toggleCameraRecord(live)
            })
        } else {
            out.add(L(R.drawable.ic_camera, "Add a live camera first", id = "addcam") {
                h.addCameraLive()
            })
        }
        out.add(L(R.drawable.ic_screen, "Screen record", id = "screen") { h.addScreen() })
        out.add(if (p.layers.isEmpty()) info("Snapshot needs something on the canvas")
        else L(R.drawable.ic_image, "Snapshot frame", id = "snap") { h.snapshotFrame() })
        out.add(F(R.drawable.ic_flash, "Light", id = "light",
            badge = if (lightOn) "ON" else null,
            children = if (live != null) lightItems(h, live)
            else listOf(L(R.drawable.ic_eye,
                if (h.isScreenLightOn()) "Screen light: on" else "Screen light: off",
                id = "screenlight", active = h.isScreenLightOn(),
                badge = if (h.isScreenLightOn()) "BRIGHT" else null) {
                h.toggleScreenLight()
            }, info("A live camera unlocks the device flashes"))))
        out.add(L(R.drawable.ic_export, "Quick export after recording", id = "qexp") {
            h.quickExport()
        })
        return Section("record", R.drawable.ic_stop, "Record",
            if (rec) "REC" else null, out, expanded = rec)
    }

    // ────────────────────── 7. CANVAS ──────────────────────

    private fun canvasSection(h: RadialMenus.Host): Section {
        val p = h.project
        val kids = listOf(
            F(R.drawable.ic_aspect, "Aspect ratio", id = "aspect", badge = p.aspect.code,
                children = listOf(
                    T(R.drawable.ic_aspect, "16:9 — landscape (YouTube)", id = "169",
                        active = p.aspect == Aspect.R169) { h.setAspect(Aspect.R169) },
                    T(R.drawable.ic_aspect, "9:16 — portrait (Reels/Shorts)", id = "916",
                        active = p.aspect == Aspect.R916) { h.setAspect(Aspect.R916) },
                    T(R.drawable.ic_aspect, "1:1 — square posts", id = "11",
                        active = p.aspect == Aspect.R11) { h.setAspect(Aspect.R11) }
                )),
            F(R.drawable.ic_palette, "Background colour", id = "bg", children = listOf(
                T(R.drawable.ic_palette, "Dark", id = "bg-dark") { h.setBg(0xFF101418.toInt()) },
                T(R.drawable.ic_palette, "Black", id = "bg-black") { h.setBg(0xFF000000.toInt()) },
                T(R.drawable.ic_palette, "White", id = "bg-white") { h.setBg(0xFFFFFFFF.toInt()) },
                T(R.drawable.ic_palette, "Orange", id = "bg-orange") { h.setBg(0xFFFF5A2C.toInt()) },
                T(R.drawable.ic_palette, "Navy", id = "bg-navy") { h.setBg(0xFF1E3C78.toInt()) },
                T(R.drawable.ic_palette, "Green", id = "bg-green") { h.setBg(0xFF14785A.toInt()) },
                T(R.drawable.ic_palette, "Purple", id = "bg-purple") { h.setBg(0xFF781E5A.toInt()) }
            )),
            T(R.drawable.ic_fullscreen, "Immersive canvas (hide the menu button)",
                id = "immersive", active = h.isImmersive(),
                badge = if (h.isImmersive()) "ON" else null) { h.enterFullCanvas() },
            T(R.drawable.ic_fit, "Fit all sources into the canvas", id = "fitall") {
                h.fitAllSources()
            },
            if (h.selected() == null) info("Select a source to promote it to background")
            else L(R.drawable.ic_fill, "Selection → canvas background", id = "selbg") {
                h.selected()?.let { h.ctrl.setAsCanvasBackground(it.id) }
            }
        )
        return Section("canvas", R.drawable.ic_aspect, "Canvas", p.aspect.code, kids)
    }

    // ────────────────────── 8. EXPORT ──────────────────────

    private fun exportSection(h: RadialMenus.Host): Section {
        return Section("export", R.drawable.ic_export, "Export", null,
            listOf(
                L(R.drawable.ic_export, "Quick export (last settings)", id = "quick") {
                    h.quickExport()
                },
                L(R.drawable.ic_settings, "Export settings (codec · size · fps)…",
                    id = "settings") { h.openExportPanel() }
            ))
    }

    // ────────────────────── 9. PROJECT ──────────────────────

    private fun projectSection(h: RadialMenus.Host): Section {
        val hudOn = h.isStatsHudOn()
        return Section("project", R.drawable.ic_edit, "Project",
            h.project.name.take(12),
            listOf(
                L(R.drawable.ic_edit, "Rename project", id = "rename") { h.renameProject() },
                T(R.drawable.ic_check, "Save now", id = "save") { h.saveNow() },
                T(R.drawable.ic_undo, "Undo", id = "undo") { h.undo() },
                T(R.drawable.ic_redo, "Redo", id = "redo") { h.redo() },
                T(R.drawable.ic_info, if (hudOn) "Stats overlay: on" else "Stats overlay: off",
                    id = "hud", active = hudOn) { h.toggleStatsHud() },
                L(R.drawable.ic_info, "Diagnostics", id = "diag") { h.openDiagnostics() },
                L(R.drawable.ic_back, "Close project", id = "close", danger = true) {
                    h.closeProject()
                }
            ))
    }

    // ────────────────────── 10. SETTINGS ──────────────────────

    private fun settingsSection(h: RadialMenus.Host): Section {
        val pol = h.orientPolicyName()
        return Section("settings", R.drawable.ic_settings, "Settings", null,
            listOf(
                F(R.drawable.ic_reset, "Rotation", id = "rotation",
                    badge = when (pol) {
                        "auto" -> "free"; "lock" -> "locked"; else -> "follow canvas"
                    },
                    children = listOf(
                        T(R.drawable.ic_aspect, "Follow canvas (16:9 = landscape)",
                            id = "rot-canvas", active = pol == "canvas") {
                            h.setOrientPolicyByName("canvas")
                        },
                        T(R.drawable.ic_switch, "Free rotation", id = "rot-auto",
                            active = pol == "auto") { h.setOrientPolicyByName("auto") },
                        T(R.drawable.ic_lock, "Lock current orientation", id = "rot-lock",
                            active = pol == "lock") { h.setOrientPolicyByName("lock") }
                    )),
                L(R.drawable.ic_layers, "Save folder: ${h.saveFolderLabel()}", id = "folder") {
                    h.pickSaveFolder()
                },
                T(R.drawable.ic_reset, "Use default album", id = "default-album") {
                    h.resetSaveFolder()
                },
                L(R.drawable.ic_export, "Export quality…", id = "quality") {
                    h.openExportPanel()
                },
                info("Canvas-first: the canvas owns the screen, every control lives here"),
                info("Long-press the canvas to jump to a source · swipe the menu closed")
            ))
    }
}
