package com.rehman.ahmedreactionstudio.core

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Project persistence.
 *
 *  storage layout (app-private, original media is never touched):
 *    filesDir/projects/<id>/
 *        project.json       (authoritative editor state)
 *        snapshot.json      (rotating recovery snapshot)
 *        thumb.png          (home thumbnail)
 *        media/<file>       (imported/captured sources copied into the project)
 */
class ProjectStore(private val ctx: Context) {

    companion object {
        const val SCHEMA = 1
        val DIR_NAME = "projects"
    }

    private val root: File get() = File(ctx.filesDir, DIR_NAME)

    fun ensureRoot() { if (!root.exists()) root.mkdirs() }

    fun projectDir(id: String): File = File(root, id)
    fun mediaDir(id: String): File = File(projectDir(id), "media")
    fun projectFile(id: String): File = File(projectDir(id), "project.json")
    fun snapshotFile(id: String): File = File(projectDir(id), "snapshot.json")
    fun thumbFile(id: String): File = File(projectDir(id), "thumb.png")

    fun create(name: String, aspect: Aspect): Project {
        ensureRoot()
        val p = Project(id = UUID.randomUUID().toString(), name = name.ifBlank { "My Project" }, aspect = aspect)
        projectDir(p.id).mkdirs()
        mediaDir(p.id).mkdirs()
        save(p)
        return p
    }

    /**
     * Persist the project.
     *
     * @return true when the authoritative project.json was actually replaced.
     * BUG-17: this used to swallow every exception and return Unit, so a full
     * disk or a failed rename left a STALE project.json on disk while the
     * editor happily reported "saved". Callers can now surface a real failure.
     */
    fun save(p: Project, alsoSnapshot: Boolean = false): Boolean {
        return try {
            val dir = projectDir(p.id); dir.mkdirs(); mediaDir(p.id).mkdirs()
            val tmp = File(dir, "project.json.tmp")
            tmp.writeText(p.toJson().toString(2))
            if (!tmp.exists()) return false
            val target = projectFile(p.id)
            // renameTo fails on some filesystems when the destination exists
            var ok = tmp.renameTo(target)
            if (!ok) {
                ok = try {
                    tmp.copyTo(target, overwrite = true); tmp.delete(); true
                } catch (_: Exception) { false }
            }
            if (ok && alsoSnapshot) snapshot(p)
            ok
        } catch (_: Exception) { false }
    }

    fun snapshot(p: Project) {
        try {
            val dir = projectDir(p.id); dir.mkdirs()
            val tmp = File(dir, "snapshot.json.tmp")
            tmp.writeText(p.toJson().toString(2))
            if (tmp.exists()) tmp.renameTo(snapshotFile(p.id))
        } catch (_: Exception) { }
    }

    fun load(id: String): Project? = loadFile(projectFile(id))

    fun loadSnapshot(id: String): Project? = loadFile(snapshotFile(id))

    private fun loadFile(f: File): Project? {
        if (!f.exists()) return null
        return try {
            Project.fromJson(JSONObject(f.readText()))
        } catch (_: Exception) { null }
    }

    /** All project ids sorted by most recently updated first. */
    fun listIds(): List<String> {
        ensureRoot()
        return (root.listFiles() ?: emptyArray())
            .filter { it.isDirectory && File(it, "project.json").exists() }
            .mapNotNull { f ->
                try {
                    val j = Project.fromJson(JSONObject(File(f, "project.json").readText()))
                    Triple(f.name, j.updatedAt, j)
                } catch (_: Exception) { null }
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun loadMeta(id: String): Project? = load(id)

    fun delete(id: String) {
        projectDir(id).deleteRecursively()
    }

    fun duplicate(id: String): Project? {
        val src = load(id) ?: return null
        val p = Project(id = UUID.randomUUID().toString(), name = src.name + " copy", aspect = src.aspect, bgColor = src.bgColor,
            createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        projectDir(p.id).mkdirs(); mediaDir(p.id).mkdirs()
        // deep-copy layers
        for (l in src.layers) {
            val nl = l.clone()
            if (l.relPath != null) {
                val from = File(projectDir(src.id), l.relPath)
                if (from.exists()) {
                    val rel = copyIntoMedia(p.id, from)
                    nl.relPath = rel
                }
            }
            p.layers.add(nl)
        }
        save(p)
        return p
    }

    /** Copies a media file into <project>/media and returns its relative path. */
    fun copyIntoMedia(projectId: String, src: File): String {
        val dir = mediaDir(projectId); dir.mkdirs()
        var name = src.name
        if (name.isBlank()) name = "clip_${System.currentTimeMillis()}.mp4"
        var f = File(dir, name)
        // BUG-10: the old collision suffix appended AFTER the extension and
        // produced "clip.mp4_1" — a file no file manager and no MIME sniffer
        // recognises as video. Insert the index before the extension instead.
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (f.exists()) { f = File(dir, stem + "_" + i + ext); i++ }
        src.copyTo(f, overwrite = false)
        return "media/${f.name}"
    }

    /**
     * Delete media files under <project>/media that no reachable state refers
     * to any more.
     *
     * [referenced] must contain the relPaths of the CURRENT project *and* of
     * every undo/redo snapshot still reachable — otherwise undoing a delete
     * would restore a layer whose bytes are gone. Returns the bytes reclaimed.
     */
    fun reclaimOrphanMedia(projectId: String, referenced: Set<String>): Long {
        val dir = mediaDir(projectId)
        if (!dir.isDirectory) return 0L
        var freed = 0L
        val keep = referenced.map { it.substringAfterLast('/') }.toHashSet()
        for (f in dir.listFiles() ?: emptyArray()) {
            if (!f.isFile || keep.contains(f.name)) continue
            val size = f.length()
            if (f.delete()) freed += size
        }
        return freed
    }

    /** Media file for a layer's relPath within a project. */
    fun mediaFile(projectId: String, relPath: String): File = File(projectDir(projectId), relPath)

    /** True if an unfinished session flag exists for this project (crash safety). */
    fun sessionOpenFlag(projectId: String): File = File(projectDir(projectId), "open.flag")

    fun markOpen(id: String) {
        try { sessionOpenFlag(id).writeText(System.currentTimeMillis().toString()) } catch (_: Exception) { }
    }

    fun clearOpen(id: String) {
        try { sessionOpenFlag(id).delete() } catch (_: Exception) { }
    }

    fun isDirty(id: String): Boolean = sessionOpenFlag(id).exists()

    fun saveThumb(id: String, bmp: android.graphics.Bitmap?) {
        if (bmp == null) return
        try {
            val f = thumbFile(id)
            val out = f.outputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
            out.close()
        } catch (_: Exception) { }
    }
}
