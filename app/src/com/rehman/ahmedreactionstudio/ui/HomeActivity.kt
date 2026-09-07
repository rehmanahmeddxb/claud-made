package com.rehman.ahmedreactionstudio.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.rehman.ahmedreactionstudio.core.Aspect
import com.rehman.ahmedreactionstudio.core.Project
import com.rehman.ahmedreactionstudio.core.ProjectStore
import com.rehman.ahmedreactionstudio.editor.EditorActivity
import com.rehman.ahmedreactionstudio.util.UI
import java.io.File

class HomeActivity : Activity() {

    private lateinit var store: ProjectStore
    private val projects = ArrayList<Project>()
    private lateinit var adapter: ProjectsAdapter
    private lateinit var emptyState: LinearLayout
    private lateinit var listView: ListView

    /**
     * BUG-05: one shared decoder thread and a bounded bitmap cache.
     *
     * The previous code started `Thread {}` inside getView — an unbounded
     * number of threads racing recycled rows, with no cache, so scrolling back
     * up decoded everything again.
     */
    private val thumbExec: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "home-thumbs").apply { isDaemon = true }
        }

    /** ~4 MB of decoded thumbnails is plenty for a project list. */
    private val thumbCache = object :
        android.util.LruCache<String, android.graphics.Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int =
            value.byteCount
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        UI.styleWindow(this)
        store = ProjectStore(this)
        store.ensureRoot()
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        projects.clear()
        for (id in store.listIds()) {
            store.load(id)?.let { projects.add(it) }
        }
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    /** BUG-06: a first-run user used to get a blank screen under the header. */
    private fun updateEmptyState() {
        if (!this::emptyState.isInitialized) return
        val empty = projects.isEmpty()
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        listView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        thumbExec.shutdownNow()
        thumbCache.evictAll()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = UI.col(this, true)
        root.setPadding(UI.dp(this, 16), UI.dp(this, 22), UI.dp(this, 16), 0)

        // ---- header
        val header = UI.col(this, false)
        val logo = TextView(this)
        logo.text = "\u25B6"
        logo.setTextColor(UI.ACCENT)
        logo.textSize = 26f
        logo.gravity = Gravity.CENTER
        val lg = GradientDrawable()
        lg.cornerRadius = UI.dpf(this, 10f)
        lg.setColor(Color.argb(40, 255, 90, 44))
        logo.background = lg
        val lp = LinearLayout.LayoutParams(UI.dp(this, 46), UI.dp(this, 46))
        logo.layoutParams = lp
        header.addView(logo)

        val tt = UI.col(this, true)
        tt.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val name = UI.title(this, "Ahmed Reaction Studio")
        tt.addView(name)
        val sub = UI.label(this, "Local-first Kotlin reaction & PiP editor", dim = true, size = 12f)
        tt.addView(sub)
        header.addView(tt)

        val diagBtn = UI.chip(this, "Diagnostics")
        diagBtn.contentDescription = "Open diagnostics"
        diagBtn.setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        header.addView(diagBtn)
        root.addView(header)

        val hint = UI.label(this,
            "Projects are stored on this device only. No accounts, no cloud.",
            dim = true, size = 11f)
        UI.margin(hint, 0, 14, 0, 0, this)
        root.addView(hint)

        // ---- list
        val list = ListView(this)
        list.divider = null
        list.setPadding(0, UI.dp(this, 6), 0, 0)
        list.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        adapter = ProjectsAdapter()
        list.adapter = adapter
        listView = list
        list.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            openProject(projects[pos].id)
        }
        // T-30 — long-press menu. Open / Rename / Duplicate / Delete without
        // hunting for the small chips on the card.
        list.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            projects.getOrNull(pos)?.let { showProjectMenu(it) }
            true
        }
        root.addView(list)

        // ---- BUG-06: empty state (occupies the same slot as the list)
        emptyState = LinearLayout(this)
        emptyState.orientation = LinearLayout.VERTICAL
        emptyState.gravity = Gravity.CENTER
        emptyState.visibility = View.GONE
        emptyState.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        val emptyIcon = TextView(this)
        emptyIcon.text = "\u25B6"
        emptyIcon.textSize = 44f
        emptyIcon.gravity = Gravity.CENTER
        emptyIcon.setTextColor(Color.argb(70, 255, 255, 255))
        emptyState.addView(emptyIcon)
        val emptyTitle = TextView(this)
        emptyTitle.text = "No projects yet"
        emptyTitle.textSize = 17f
        emptyTitle.gravity = Gravity.CENTER
        emptyTitle.setTextColor(UI.FG)
        emptyTitle.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        UI.margin(emptyTitle, 0, 12, 0, 0, this)
        emptyState.addView(emptyTitle)
        val emptyBody = TextView(this)
        emptyBody.text =
            "Create a project, then add your reaction camera,\na video to react to, images or a screen recording."
        emptyBody.textSize = 12.5f
        emptyBody.gravity = Gravity.CENTER
        emptyBody.setTextColor(UI.FG2)
        emptyBody.setLineSpacing(UI.dpf(this, 4f), 1f)
        UI.margin(emptyBody, 0, 6, 0, 0, this)
        emptyState.addView(emptyBody)
        val emptyCta = UI.btn(this, "Create your first project", accent = true)
        emptyCta.contentDescription = "Create your first project"
        val ectaLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, UI.dp(this, 48))
        ectaLp.topMargin = UI.dp(this, 18)
        emptyCta.layoutParams = ectaLp
        emptyCta.setOnClickListener { showNewDialog() }
        emptyState.addView(emptyCta)
        root.addView(emptyState)

        // ---- new project button
        val newBtn = UI.btn(this, "+  New project", accent = true)
        newBtn.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(this, 50))
        newBtn.contentDescription = "Create a new project"
        newBtn.setOnClickListener { showNewDialog() }
        root.addView(newBtn)

        setContentView(root)
    }

    /** T-30 — the long-press action sheet for a project card. */
    private fun showProjectMenu(p: Project) {
        val labels = arrayOf("Open", "Rename project", "Duplicate", "Delete")
        AlertDialog.Builder(this)
            .setTitle(p.name)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> openProject(p.id)
                    1 -> renameProject(p)
                    2 -> { store.duplicate(p.id); refresh() }
                    3 -> confirmDelete(p)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameProject(p: Project) {
        val input = EditText(this)
        input.setText(p.name)
        input.setSelection(input.text.length)
        input.setTextColor(UI.FG)
        AlertDialog.Builder(this)
            .setTitle("Rename project")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val nm = input.text.toString().trim()
                if (nm.isEmpty()) return@setPositiveButton
                // load the FULL project before saving: saving a meta-only copy
                // would drop every layer on disk.
                val full = store.load(p.id) ?: return@setPositiveButton
                full.name = nm
                store.save(full)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(p: Project) {
        AlertDialog.Builder(this)
            .setTitle("Delete project")
            .setMessage("\"${p.name}\" and its project media will be deleted from this device.")
            .setPositiveButton("Delete") { _, _ -> store.delete(p.id); refresh() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openProject(id: String) {
        val p = store.load(id) ?: return
        val i = Intent(this, EditorActivity::class.java)
        i.putExtra(EditorActivity.EXTRA_PROJECT_ID, id)
        i.putExtra(EditorActivity.EXTRA_PROJECT_NAME, p.name)
        i.putExtra(EditorActivity.EXTRA_PROJECT_ASPECT, p.aspect.code)
        store.markOpen(id)
        startActivity(i)
    }

    private fun showNewDialog() {
        val holder = LinearLayout(this)
        holder.orientation = LinearLayout.VERTICAL
        holder.setPadding(UI.dp(this, 22), UI.dp(this, 8), UI.dp(this, 22), 0)

        val nameInput = EditText(this)
        nameInput.hint = "Project name"
        nameInput.setText("My Reaction")
        nameInput.selectAll()
        nameInput.setTextColor(UI.FG)
        nameInput.setHintTextColor(Color.argb(150, 255, 255, 255))
        holder.addView(nameInput)

        val aspectRow = UI.col(this, false)
        aspectRow.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        UI.margin(aspectRow, 0, 14, 0, 6, this)
        val chips = HashMap<Aspect, TextView>()
        val rowLp = LinearLayout.LayoutParams(0, UI.dp(this, 48), 1f)   // BUG-11
        for (a in Aspect.entries) {
            val c = UI.chip(this, a.code)
            c.contentDescription = "Canvas aspect ratio ${a.code}"
            c.layoutParams = rowLp
            c.setOnClickListener {
                for ((k, v) in chips) v.isSelected = (k == a)
                refreshChips(chips)
            }
            chips[a] = c
            aspectRow.addView(c)
        }
        holder.addView(aspectRow)
        // default 16:9, selected BEFORE show (no chip flicker)
        chips[Aspect.R169]?.isSelected = true
        refreshChips(chips)

        // Positive button wired AFTER show so a blank name keeps the dialog
        // open with an inline error instead of auto-dismissing (P1-10).
        val dlg = AlertDialog.Builder(this)
            .setTitle("New project")
            .setView(holder)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                nameInput.error = "Give your project a name"
                return@setOnClickListener
            }
            val aspect = chips.entries.firstOrNull { it.value.isSelected }?.key ?: Aspect.R169
            val p = store.create(name, aspect)
            dlg.dismiss()
            openProject(p.id)
        }
    }

    private fun refreshChips(chips: HashMap<Aspect, TextView>) {
        for ((a, v) in chips) {
            val g = v.background as GradientDrawable
            if (v.isSelected) {
                g.setColor(UI.ACCENT)
                g.setStroke(UI.dp(this, 1), Color.argb(140, 255, 220, 180))
                v.setTextColor(Color.WHITE)
            } else {
                g.setColor(UI.BG3)
                g.setStroke(UI.dp(this, 1), Color.argb(60, 255, 255, 255))
                v.setTextColor(UI.FG)
            }
            v.background = g
        }
    }

    /**
     * BUG-05: a recycling adapter.
     *
     * The old getView() ignored `convert` entirely and rebuilt ~12 views for
     * every card on every bind, then started a RAW THREAD per bind to decode
     * the thumbnail. With thumbnails actually being written now (BUG-04) that
     * combination janks the list and churns threads. This version:
     *   - builds the hierarchy once and reuses it through a ViewHolder,
     *   - decodes on ONE shared background executor, not a thread per row,
     *   - caches decoded bitmaps in a bounded LRU so scrolling back is free,
     *   - guards recycled rows with a tag so a late decode cannot paint the
     *     wrong card.
     */
    private class Holder(
        val card: LinearLayout,
        val thumb: ImageView,
        val placeholder: TextView,
        val name: TextView,
        val meta: TextView,
        val menuBtn: TextView
    )

    private inner class ProjectsAdapter : BaseAdapter() {
        override fun getCount(): Int = projects.size
        override fun getItem(pos: Int): Any = projects[pos]
        override fun getItemId(pos: Int): Long = pos.toLong()

        override fun getView(pos: Int, convert: View?, parent: ViewGroup?): View {
            val ctx = this@HomeActivity
            val p = projects[pos]
            val holder: Holder
            val card: LinearLayout

            if (convert != null && convert.tag is Holder) {
                holder = convert.tag as Holder
                card = holder.card
            } else {
                card = LinearLayout(ctx)
                card.orientation = LinearLayout.HORIZONTAL
                card.gravity = Gravity.CENTER_VERTICAL
                card.setPadding(UI.dp(ctx, 12), UI.dp(ctx, 10), UI.dp(ctx, 12), UI.dp(ctx, 10))
                val g = GradientDrawable()
                g.cornerRadius = UI.dpf(ctx, 14f)
                g.setColor(UI.BG2)
                g.setStroke(UI.dp(ctx, 1), Color.argb(50, 255, 255, 255))
                card.background = g
                card.layoutParams = android.widget.AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(ctx, 96))

                val frame = FrameLayout(ctx)
                frame.layoutParams = LinearLayout.LayoutParams(UI.dp(ctx, 72), UI.dp(ctx, 72))
                val fg = GradientDrawable()
                fg.cornerRadius = UI.dpf(ctx, 8f)
                fg.setColor(UI.BG3)
                frame.background = fg
                frame.clipToOutline = true

                // shown until (or unless) a real thumbnail exists
                val placeholder = TextView(ctx)
                placeholder.text = "\u25B6"
                placeholder.gravity = Gravity.CENTER
                placeholder.setTextColor(Color.argb(90, 255, 255, 255))
                placeholder.textSize = 20f
                frame.addView(placeholder, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

                val thumb = ImageView(ctx)
                thumb.scaleType = ImageView.ScaleType.CENTER_CROP
                frame.addView(thumb, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                card.addView(frame)

                val col = UI.col(ctx, true)
                col.layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                UI.margin(col, 12, 0, 8, 0, ctx)
                val nm = TextView(ctx)
                nm.setTextColor(UI.FG)
                nm.textSize = 15f
                nm.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                nm.maxLines = 1
                col.addView(nm)
                val meta = TextView(ctx)
                meta.setTextColor(UI.FG2)
                meta.textSize = 11.5f
                meta.maxLines = 1
                UI.margin(meta, 0, 2, 0, 0, ctx)
                col.addView(meta)
                card.addView(col)

                // BUG-06 / hygiene: the per-card ✕ is GONE.
                //
                // A 34dp delete chip sitting next to a same-looking "Copy"
                // chip, inside a row whose whole job is "tap to open", is a
                // mis-tap waiting to happen for an irreversible action. One
                // ⋮ opens the same Open/Rename/Duplicate/Delete sheet that
                // long-press already showed, so nothing became less reachable.
                val menuBtn = TextView(ctx)
                menuBtn.text = "\u22EE"
                menuBtn.gravity = Gravity.CENTER
                menuBtn.setTextColor(UI.FG)
                menuBtn.textSize = 18f
                menuBtn.layoutParams = LinearLayout.LayoutParams(
                    UI.dp(ctx, 48), UI.dp(ctx, 48))
                val mg = GradientDrawable()
                mg.cornerRadius = UI.dpf(ctx, 24f)
                mg.setColor(Color.argb(30, 255, 255, 255))
                menuBtn.background = mg
                card.addView(menuBtn)

                holder = Holder(card, thumb, placeholder, nm, meta, menuBtn)
                card.tag = holder
            }

            holder.name.text = p.name
            holder.meta.text = "${p.aspect.code}  \u00b7  ${p.layers.size} layer" +
                (if (p.layers.size == 1) "" else "s") + "  \u00b7  " +
                UI.fmtTime(p.durationMs()) + "  \u00b7  " + UI.relTime(p.updatedAt)
            holder.card.contentDescription =
                "Project ${p.name}, ${p.aspect.code}, ${p.layers.size} layers"
            holder.menuBtn.contentDescription = "More actions for ${p.name}"
            holder.menuBtn.setOnClickListener { showProjectMenu(p) }

            bindThumb(holder, p)
            return card
        }

        private fun bindThumb(holder: Holder, p: Project) {
            val f = store.thumbFile(p.id)
            val key = p.id + ":" + (if (f.exists()) f.lastModified() else 0L)
            holder.thumb.tag = key
            val cached = thumbCache[key]
            if (cached != null) {
                holder.thumb.setImageBitmap(cached)
                holder.placeholder.visibility = View.GONE
                return
            }
            holder.thumb.setImageDrawable(null)
            holder.placeholder.visibility = View.VISIBLE
            if (!f.exists()) return
            val path = f.absolutePath
            thumbExec.execute {
                val bmp = try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    // downsample to roughly the 72dp the card actually shows
                    val target = UI.dp(this@HomeActivity, 72).coerceAtLeast(1)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= target) sample *= 2
                    BitmapFactory.decodeFile(path,
                        BitmapFactory.Options().apply { inSampleSize = sample })
                } catch (_: Throwable) { null }
                if (bmp == null) return@execute
                holder.thumb.post {
                    thumbCache.put(key, bmp)
                    if (holder.thumb.tag == key) {
                        holder.thumb.setImageBitmap(bmp)
                        holder.placeholder.visibility = View.GONE
                    }
                }
            }
        }
    }
}
