package com.rehman.ahmedreactionstudio.editor

import com.rehman.ahmedreactionstudio.core.Aspect
import com.rehman.ahmedreactionstudio.core.Layer
import com.rehman.ahmedreactionstudio.core.LayerType
import com.rehman.ahmedreactionstudio.core.Project
import com.rehman.ahmedreactionstudio.core.SourceController

/**
 * JVM regression test for the canvas-first menu.
 *
 * The Studio has no buttons on the canvas any more, so `SidebarTree` IS the
 * feature set. That makes two failure modes catastrophic and completely
 * invisible to a compiler:
 *
 *   1. a row that renders but does nothing (the "dead button" bug this repo has
 *      been fixed for over and over — see the no-op-stub budget guard), and
 *   2. a verb that exists on the Host but that no row ever calls, i.e. a
 *      feature the redesign orphaned when it deleted the wheel and the rails.
 *
 * Both are checked here by INVOKING the tree: every leaf action of every
 * section is fired against a fake host and the recorded calls must cover every
 * mutating verb of `RadialMenus.Host`. Run with `tools/sidebar-tree-test/run.sh`.
 */
private var passed = 0
private var failed = 0

private fun ok(name: String, cond: Boolean, detail: String = "") {
    if (cond) passed++ else {
        failed++
        println("  FAIL  $name" + if (detail.isEmpty()) "" else "  [$detail]")
    }
}

private class World {
    val project = Project("p1", "Test reaction", Aspect.R169)
    var changes = 0
    val ctrl = SourceController({ project }, { }, { changes++ })
    val host: FakeHost = FakeHost(project, ctrl)
    val clip = Layer(id = "clip", type = LayerType.VIDEO, name = "Clip", relPath = "a.mp4",
        durMs = 4000L, srcW = 1280, srcH = 720)
    val cam = Layer(id = "cam", type = LayerType.CAMERA, name = "Camera", live = true,
        srcW = 720, srcH = 1280)
    val text = Layer(id = "txt", type = LayerType.TEXT, name = "Caption", text = "hi")
    init {
        project.layers.add(clip)
        project.layers.add(cam)
        project.layers.add(text)
    }
}

fun main() {
    // 1 ── structure --------------------------------------------------------
    val w = World()
    val sections = SidebarTree.build(w.host)
    ok("menu exposes 8+ sections", sections.size >= 8, "got ${sections.size}")
    ok("every section has rows", sections.all { it.children.isNotEmpty() },
        sections.filter { it.children.isEmpty() }.joinToString { it.id })
    ok("section ids are unique", sections.map { it.id }.distinct().size == sections.size)
    ok("canvas-first sections carry the jobs the chrome used to",
        listOf("add", "sources", "controls", "playback", "audio", "record", "canvas",
            "export", "project", "settings").all { id -> sections.any { it.id == id } },
        sections.joinToString { it.id })

    var leaves = 0
    var folders = 0
    var maxDepth = 0
    var blank = 0
    var deadLeaf = 0
    var emptyFolder = ""
    var dupId = ""
    var folderWithAction = ""
    fun checkItems(items: List<SidebarTree.Item>, depth: Int, where: String) {
        val ids = items.mapNotNull { it.id }
        if (ids.distinct().size != ids.size) dupId = where      // ids address rows
        for (it in items) {
            maxDepth = maxOf(maxDepth, depth)
            if (it.label.isBlank()) blank++
            if (it.isFolder) {
                folders++
                val kids = it.children!!
                if (kids.isEmpty()) emptyFolder = "$where/${it.label}"
                if (it.action != null) folderWithAction = "$where/${it.label}"
                checkItems(kids, depth + 1, "$where/${it.label}")
            } else {
                leaves++
                // an enabled row must DO something; a disabled row must say why
                // (that is the difference between a guard and a dead button)
                if (it.enabled && it.action == null) deadLeaf++
            }
        }
    }
    for (s in sections) checkItems(s.children, 1, s.id)

    ok("tree nests at least three levels (menu > sub-menu > sub-sub-menu)",
        maxDepth >= 3, "maxDepth=$maxDepth")
    ok("no enabled row without an action", deadLeaf == 0, "dead=$deadLeaf")
    ok("row ids are unique inside a parent", dupId.isEmpty(), "duplicates in $dupId")
    ok("a folder never also fires an action", folderWithAction.isEmpty(), folderWithAction)
    ok("no folder is empty", emptyFolder.isEmpty(), emptyFolder)
    ok("no blank labels", blank == 0, "blank=$blank")
    ok("the per-source branch carries the real verb set",
        leaves >= 90 && folders >= 12, "leaves=$leaves folders=$folders")

    // 2 ── ids must be stable enough for expansion state + deep links --------
    val srcSection = sections.first { it.id == "sources" }
    val layerRows = srcSection.children.filter { it.id != null && it.id!!.startsWith("layer.") }
    ok("every source is addressable as layer.<id>",
        layerRows.size == w.project.layers.size &&
            layerRows.map { it.id!! }.toSet() ==
            w.project.layers.map { "layer." + it.id }.toSet(),
        layerRows.joinToString { it.id ?: "?" })
    val camRow = layerRows.first { it.id == "layer.cam" }
    ok("a live source offers fit/arrange/light/audio branches",
        camRow.children!!.map { it.id }.toSet().containsAll(
            setOf("vis", "lock", "facing", "mirror", "light", "opacity", "arrange", "adv", "del")),
        camRow.children!!.joinToString { it.id ?: it.label })
    ok("light branch reaches both LEDs, the lens LED and the screen flash",
        camRow.children!!.first { it.id == "light" }.children!!.mapNotNull { it.id }.toSet()
            .containsAll(setOf("front", "back", "both", "screen", "torch")))
    val clipRow = layerRows.first { it.id == "layer.clip" }
    ok("a clip offers play/mute/solo/loop/volume",
        clipRow.children!!.map { it.id }.toSet().containsAll(
            setOf("play", "loop", "mute", "solo", "vol", "fit", "arrange", "dup")),
        clipRow.children!!.joinToString { it.id ?: it.label })
    ok("volume is a sub-sub-menu with real steps",
        clipRow.children!!.first { it.id == "vol" }.children!!.size >= 4)
    val textRow = layerRows.first { it.id == "layer.txt" }
    ok("text source is editable from the menu",
        textRow.children!!.map { it.id }.containsAll(listOf("edit", "colour")))
    ok("live camera cannot be duplicated (the controller's rule, not a guess)",
        camRow.children!!.firstOrNull { it.id == "dup" }?.enabled == false)

    // 3 ── coverage: invoke every leaf, for each selection state.
    //    Rows fire for real (the controller mutates the project), which is the
    //    point: a row whose action is a no-op or throws would fail here. ─────
    val mutations = listOf(
        "selectId", "deleteSource", "hideSource", "addVideo", "addImage", "addCameraLive",
        "addCameraTake", "addScreen", "addTextSource", "toggleMasterPlay", "restart", "nudge",
        "toggleSourcePlay", "snapshotFrame", "undo", "redo", "openDockPanel", "openMixerPanel",
        "openExportPanel", "openAdvanced", "quickExport", "setAspect", "setBg", "fitAllSources",
        "renameProject", "saveNow", "openDiagnostics", "enterFullCanvas", "closeProject",
        "editText", "cycleTextColor", "toggleCameraRecord", "switchCameraFacing",
        "toggleCameraMirror", "toggleTorch", "toggleFrontTorch", "toggleBackTorch",
        "toggleBothTorch", "toggleScreenLight", "toggleCompositeRecording", "setMicGain",
        "toggleStatsHud", "pickSaveFolder", "resetSaveFolder", "setOrientPolicyByName")
    val fired = HashSet<String>()
    var invocations = 0
    var threw = ArrayList<String>()
    for (sel in listOf(null, "clip", "cam", "txt")) {
        w.host.selId = sel
        val secs = SidebarTree.build(w.host)
        for (s in secs) {
            fun fire(items: List<SidebarTree.Item>) {
                for (it in items) {
                    it.children?.let { kids -> fire(kids) }
                    val act = it.action
                    if (act == null || !it.enabled) continue
                    invocations++
                    try { act() } catch (e: Exception) { threw.add("${s.id}/${it.label}: $e") }
                    fired.addAll(w.host.invoked)
                    w.host.invoked.clear()
                }
            }
            fire(s.children)
        }
    }
    ok("menu rows actually fire (${invocations} invocations)", invocations > 120,
        "invocations=$invocations")
    ok("firing rows never throws", threw.isEmpty(), threw.take(3).joinToString(" | "))
    val missing = mutations.filter { it !in fired }
    ok("every mutating verb is reachable from the menu (${mutations.size} verbs)",
        missing.isEmpty(), "orphaned: " + missing.joinToString())

    // 4 ── state must be reflected, not frozen (fresh world: the sweep above
    //     fired real controller verbs and deleted layers, by design) ─────────
    val v = World()
    v.host.selId = "clip"
    val ctlSection = SidebarTree.build(v.host).first { it.id == "controls" }
    ok("the selected source is named in the menu",
        ctlSection.label.contains("Clip"), ctlSection.label)
    val clip = v.project.layerById("clip")!!
    clip.muted = true
    clip.solo = false
    val aud = SidebarTree.build(v.host).first { it.id == "audio" }
    ok("audio badges follow the mute", aud.children.any { it.badge == "MUTED" },
        aud.children.joinToString("/") { it.label + "=" + (it.badge ?: "-") })
    clip.muted = false
    clip.volume = 0.4f
    val aud2 = SidebarTree.build(v.host).first { it.id == "audio" }
    ok("audio badges show the live volume", aud2.children.any { it.badge == "40%" },
        aud2.children.joinToString("/") { it.label + "=" + (it.badge ?: "-") })
    v.host.playing = true
    ok("playback state reaches the menu", SidebarTree.build(v.host).let { secs ->
        val pb = secs.first { it.id == "playback" }
        pb.children.any { it.label == "Pause" && it.active }
    })
    v.project.aspect = Aspect.R916
    ok("canvas section reports the live ratio",
        SidebarTree.build(v.host).first { it.id == "canvas" }.badge == "9:16")
    v.host.recording = true
    ok("recording state reaches the menu (REC badge + stop row)",
        SidebarTree.build(v.host).first { it.id == "record" }.let { r ->
            r.badge == "REC" && r.children.any { it.label.contains("Stop") }
        })
    ok("sources section counts the layers",
        SidebarTree.build(v.host).first { it.id == "sources" }.badge ==
            v.project.layers.size.toString())
    v.host.selId = null
    val none = SidebarTree.build(v.host).first { it.id == "controls" }
    ok("an empty selection explains itself instead of showing dead rows",
        none.children.size == 1 && none.children[0].enabled.not(),
        none.children.joinToString { it.label })

    println("menu: ${sections.size} sections · $folders folders · $leaves rows · " +
        "max depth $maxDepth · $invocations row actions fired · " +
        "${mutations.size} verbs covered")
    println(if (failed == 0) "SIDEBAR TREE: $passed checks passed"
        else "SIDEBAR TREE: $passed passed, $failed FAILED")
    if (failed > 0) throw AssertionError("$failed menu checks failed")
}
