package com.rehman.ahmedreactionstudio.editor

import com.rehman.ahmedreactionstudio.core.Aspect
import com.rehman.ahmedreactionstudio.core.Layer
import com.rehman.ahmedreactionstudio.core.Project
import com.rehman.ahmedreactionstudio.core.SourceController

/**
 * A headless [RadialMenus.Host] for the JVM menu test: it records every verb
 * the menu calls and mirrors just enough state for the tree to be built.
 *
 * GENERATED SHAPE, HAND-CHECKED SEMANTICS: the override list is derived from
 * `RadialMenus.Host`, so adding a verb to the interface without wiring it into
 * `SidebarTree` fails `SidebarTreeTest` (coverage) rather than shipping a
 * feature no surface can reach.
 */
class FakeHost(
    override val project: Project,
    override val ctrl: SourceController
) : RadialMenus.Host {
    val invoked = ArrayList<String>()
    val toasts = ArrayList<String>()
    var selId: String? = null
    var playing = false
    var recording = false
    var camTake = false
    var torch = false
    var frontTorch = false
    var backTorch = false
    var bothTorch = false
    var screenLight = false
    var hud = true
    var mic = 1f
    var orient = "canvas"
    var immersive = false

    override fun selected(): Layer? { invoked += "selected"; return selId?.let { project.layerById(it) } }
    override fun selectId(id: String?) { invoked += "selectId"; selId = id }
    override fun deleteSource(l: Layer) { invoked += "deleteSource"; ctrl.delete(l.id) }
    override fun hideSource(l: Layer) { invoked += "hideSource"; ctrl.toggleVisible(l.id) }
    override fun addVideo() { invoked += "addVideo" }
    override fun addImage() { invoked += "addImage" }
    override fun addCameraLive() { invoked += "addCameraLive" }
    override fun addCameraTake() { invoked += "addCameraTake" }
    override fun addScreen() { invoked += "addScreen" }
    override fun addTextSource() { invoked += "addTextSource" }
    override fun anyPlaying(): Boolean { invoked += "anyPlaying"; return playing }
    override fun toggleMasterPlay() { invoked += "toggleMasterPlay"; playing = !playing }
    override fun restart() { invoked += "restart" }
    override fun nudge(ms: Long) { invoked += "nudge" }
    override fun toggleSourcePlay(l: Layer) { invoked += "toggleSourcePlay" }
    override fun snapshotFrame() { invoked += "snapshotFrame" }
    override fun undo() { invoked += "undo" }
    override fun redo() { invoked += "redo" }
    override fun openDockPanel() { invoked += "openDockPanel" }
    override fun openMixerPanel() { invoked += "openMixerPanel" }
    override fun openExportPanel() { invoked += "openExportPanel" }
    override fun openAdvanced(l: Layer) { invoked += "openAdvanced" }
    override fun quickExport() { invoked += "quickExport" }
    override fun setAspect(a: Aspect) { invoked += "setAspect"; project.aspect = a }
    override fun setBg(color: Int) { invoked += "setBg"; project.bgColor = color }
    override fun fitAllSources() { invoked += "fitAllSources" }
    override fun renameProject() { invoked += "renameProject" }
    override fun saveNow() { invoked += "saveNow" }
    override fun openDiagnostics() { invoked += "openDiagnostics" }
    override fun enterFullCanvas() { invoked += "enterFullCanvas" }
    override fun isImmersive(): Boolean { invoked += "isImmersive"; return immersive }
    override fun closeProject() { invoked += "closeProject" }
    override fun editText(l: Layer) { invoked += "editText" }
    override fun cycleTextColor(l: Layer) { invoked += "cycleTextColor" }
    override fun isCameraRecording(l: Layer): Boolean { invoked += "isCameraRecording"; return camTake }
    override fun toggleCameraRecord(l: Layer) { invoked += "toggleCameraRecord"; camTake = !camTake }
    override fun switchCameraFacing(l: Layer) { invoked += "switchCameraFacing" }
    override fun toggleCameraMirror(l: Layer) { invoked += "toggleCameraMirror" }
    override fun isTorchOn(l: Layer): Boolean { invoked += "isTorchOn"; return torch }
    override fun hasTorch(l: Layer): Boolean { invoked += "hasTorch"; return true }
    override fun toggleTorch(l: Layer) { invoked += "toggleTorch"; torch = !torch }
    override fun hasFrontTorch(): Boolean { invoked += "hasFrontTorch"; return true }
    override fun hasBackTorch(): Boolean { invoked += "hasBackTorch"; return true }
    override fun isFrontTorchOn(): Boolean { invoked += "isFrontTorchOn"; return frontTorch }
    override fun isBackTorchOn(): Boolean { invoked += "isBackTorchOn"; return backTorch }
    override fun isBothTorchOn(): Boolean { invoked += "isBothTorchOn"; return bothTorch }
    override fun toggleFrontTorch() { invoked += "toggleFrontTorch"; frontTorch = !frontTorch }
    override fun toggleBackTorch() { invoked += "toggleBackTorch"; backTorch = !backTorch }
    override fun toggleBothTorch() { invoked += "toggleBothTorch"; bothTorch = !bothTorch }
    override fun isScreenLightOn(): Boolean { invoked += "isScreenLightOn"; return screenLight }
    override fun toggleScreenLight() { invoked += "toggleScreenLight"; screenLight = !screenLight }
    override fun openFlashRing(l: Layer) { invoked += "openFlashRing" }
    override fun isRecordingComposite(): Boolean { invoked += "isRecordingComposite"; return recording }
    override fun toggleCompositeRecording() { invoked += "toggleCompositeRecording"; recording = !recording }
    override fun canRecordComposite(): Boolean { invoked += "canRecordComposite"; return true }
    override fun micGain(): Float { invoked += "micGain"; return mic }
    override fun setMicGain(g: Float) { invoked += "setMicGain"; mic = g.coerceIn(0f, 3f) }
    override fun isStatsHudOn(): Boolean { invoked += "isStatsHudOn"; return hud }
    override fun toggleStatsHud() { invoked += "toggleStatsHud"; hud = !hud }
    override fun saveFolderLabel(): String { invoked += "saveFolderLabel"; return "Movies" }
    override fun pickSaveFolder() { invoked += "pickSaveFolder" }
    override fun resetSaveFolder() { invoked += "resetSaveFolder" }
    override fun orientPolicyName(): String { invoked += "orientPolicyName"; return orient }
    override fun setOrientPolicyByName(p: String) { invoked += "setOrientPolicyByName"; orient = p }
    override fun toast(msg: String) { toasts += msg }
}
