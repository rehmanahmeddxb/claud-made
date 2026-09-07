package com.rehman.ahmedreactionstudio.core

import org.json.JSONObject

/**
 * Lightweight command history (spec 34). Commands store full-layer-list JSON
 * deltas (small text, never media bytes).
 */
class UndoStack(private val maxDepth: Int = 50) {
    private val undo: ArrayDeque<String> = ArrayDeque()
    private val redo: ArrayDeque<String> = ArrayDeque()

    fun pushSnapshot(layersJson: JSONObject, limitSize: Boolean = true) {
        if (undo.size >= maxDepth) undo.removeLast()
        undo.addFirst(layersJson.toString())
        redo.clear()
    }

    fun canUndo(): Boolean = undo.isNotEmpty()
    fun canRedo(): Boolean = redo.isNotEmpty()

    fun popUndo(storeCurrent: () -> JSONObject): JSONObject? {
        if (undo.isEmpty()) return null
        val prev = storeCurrent()
        redo.addFirst(prev.toString())
        val snap = undo.removeFirst()
        return try { JSONObject(snap) } catch (_: Exception) { null }
    }

    fun popRedo(storeCurrent: () -> JSONObject): JSONObject? {
        if (redo.isEmpty()) return null
        val prev = storeCurrent()
        undo.addFirst(prev.toString())
        val snap = redo.removeFirst()
        return try { JSONObject(snap) } catch (_: Exception) { null }
    }

    fun clear() { undo.clear(); redo.clear() }

    /**
     * Every layer-list snapshot currently reachable by undo OR redo.
     *
     * Media garbage collection needs this: a file is only genuinely orphaned
     * when NO reachable history state still references it. Deleting a source's
     * bytes at delete-time would make "UNDO" restore a layer whose media file
     * no longer exists — a silent black frame that is far worse than the few
     * kB of storage the eager delete would have saved.
     */
    fun reachableSnapshots(): List<String> = undo.toList() + redo.toList()
}

/** Compose layers list JSON from a project (used as command payloads). */
fun layersJsonOf(p: Project): JSONObject {
    val o = JSONObject()
    o.put("id", p.id)
    o.put("aspect", p.aspect.code)
    val arr = org.json.JSONArray()
    for (l in p.layers) arr.put(l.toJson())
    o.put("layers", arr)
    return o
}

fun applyLayersJson(p: Project, o: JSONObject) {
    // aspect rides along so canvas-ratio changes are undoable too
    if (o.has("aspect")) {
        try { p.aspect = Aspect.from(o.getString("aspect")) } catch (_: Exception) { }
    }
    p.layers.clear()
    val arr = o.optJSONArray("layers") ?: return
    for (i in 0 until arr.length()) {
        try { p.layers.add(Layer.fromJson(arr.getJSONObject(i))) } catch (_: Exception) { }
    }
}
