package com.rehman.ahmedreactionstudio.editor

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.rehman.ahmedreactionstudio.R
import com.rehman.ahmedreactionstudio.util.UI

/**
 * THE STUDIO MENU — the only navigation surface in the canvas-first editor.
 *
 * Why it looks like this:
 *  - The canvas owns 100 % of the interface, so the menu is an **overlay**:
 *    it slides over the picture instead of stealing width from it. Nothing
 *    about opening the menu re-lays-out or rescales the composition, so what
 *    you framed stays framed (and what gets exported stays identical).
 *  - The whole view is `GONE` while closed. An always-present transparent
 *    overlay above the stage is exactly how the previous revision lost its
 *    taps to the old rails; `GONE` means the canvas gets every gesture.
 *  - Hierarchy is Section → Sub-menu → Sub-sub-menu (and deeper when a source
 *    genuinely needs it). Rows are recursive, so a new verb is one line in
 *    [SidebarTree], not a new toolbar.
 *  - A search box, because "everything lives here" also means ~150 rows.
 *
 * Width is 300 dp in landscape (never more than 45 % of the screen) and 82 %
 * in portrait; the tree slides in/out over 220 ms.
 */
class SidebarView(context: Context) : FrameLayout(context) {

    // ── callbacks ──
    var onStateChanged: ((open: Boolean) -> Unit)? = null

    // ── state ──
    private var _open = false
    val isOpen: Boolean get() = _open
    private var dirty = true
    private var query = ""
    private var currentSections: List<SidebarTree.Section> = emptyList()

    /** section id → expanded, and full item path → expanded (persist across rebuilds) */
    private val sectionState = HashMap<String, Boolean>()
    private val itemState = HashMap<String, Boolean>()
    /** key → row view, so a deep link can scroll its row into sight */
    private val rowViews = HashMap<String, View>()

    // ── views ──
    private val scrim = View(context).apply {
        setBackgroundColor(Color.argb(120, 0, 0, 0))
        isClickable = true
        isFocusable = true
        alpha = 0f
    }
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.rgb(13, 15, 20))
    }
    private val titleView = TextView(context).apply {
        text = "Studio"
        setTextColor(Color.WHITE)
        textSize = 16f
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val metaView = TextView(context).apply {
        setTextColor(UI.FG2)
        textSize = 11f
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    /** "Expand all" ⇄ "Collapse all" — the one-tap way to see the whole feature set */
    private val expandAllBtn = TextView(context).apply {
        text = "Expand all"
        setTextColor(UI.FG)
        textSize = 11.5f
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = "Expand or collapse every section"
        setPadding(UI.dp(context, 10), UI.dp(context, 8), UI.dp(context, 10),
            UI.dp(context, 8))
        background = Ic.pill(context, Color.argb(160, 38, 42, 52), 12f,
            Color.argb(60, 255, 255, 255))
    }
    private val searchField = android.widget.EditText(context).apply {
        hint = "Search commands"
        textSize = 13f
        setTextColor(Color.WHITE)
        setHintTextColor(Color.rgb(110, 118, 134))
        maxLines = 1
        isSingleLine = true
        includeFontPadding = false
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH or
            android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        background = Ic.pill(context, Color.argb(235, 24, 27, 35), 12f,
            Color.argb(60, 255, 255, 255))
        setPadding(UI.dp(context, 12), UI.dp(context, 9), UI.dp(context, 10),
            UI.dp(context, 9))
        isFocusable = true
        isFocusableInTouchMode = true
    }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, UI.dp(context, 4), 0, UI.dp(context, 18))
    }
    private val scrollView = ScrollView(context).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = true
        scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
        addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /**
     * Current panel width in px. The activity uses it to slide the ☰ to the
     * panel's right edge while the menu is open, so the button can toggle the
     * menu closed without sitting on top of its header.
     */
    val panelWidthPx: Int get() = panelWidth

    /** panel width for the current orientation + screen size, in px */
    private val panelWidth: Int
        get() {
            val dm = context.resources.displayMetrics
            val land = context.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val cap = (dm.widthPixels * (if (land) 0.45f else 0.86f)).toInt()
            val want = UI.dp(context, 300)
            return minOf(want, cap).coerceAtMost(dm.widthPixels - UI.dp(context, 48))
        }

    init {
        // closed = invisible = cannot intercept a single pixel of the canvas
        visibility = View.GONE

        addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        scrim.setOnClickListener { close() }

        // ---- header ----
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UI.dp(context, 14), UI.dp(context, 12), UI.dp(context, 6),
                UI.dp(context, 10))
        }
        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleCol.addView(titleView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        titleCol.addView(metaView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        header.addView(titleCol, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        expandAllBtn.setOnClickListener { setAllExpanded(expandAllBtn.text == "Expand all") }
        header.addView(expandAllBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, UI.dp(context, 34)).apply {
            marginEnd = UI.dp(context, 6)
        })
        // NO ✕ button: the ☰ rides to this panel's edge and stays the one
        // toggle, the scrim closes, and Back closes. A second exit in the
        // header is exactly the kind of redundancy that made the old
        // workspace unreadable — one control, three ways to reach it.
        panel.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- search ----
        val searchWrap = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UI.dp(context, 14), 0, UI.dp(context, 14), UI.dp(context, 8))
        }
        searchWrap.addView(searchField, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(context, 40)))
        panel.addView(searchWrap, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val hair = View(context).apply { setBackgroundColor(Color.argb(45, 255, 255, 255)) }
        panel.addView(hair, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            UI.dp(context, 1)))

        panel.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))

        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                if (_open) rebuildViews()
            }
        })
    }

    // ── public API ──

    fun toggle() { if (_open) close() else open() }

    fun open() {
        if (_open) return
        _open = true
        if (dirty) rebuildViews()
        val w = panelWidth
        panel.layoutParams = (panel.layoutParams as FrameLayout.LayoutParams).also {
            it.width = w
        }
        panel.translationX = -w.toFloat()
        visibility = View.VISIBLE
        // the search box must never steal focus (and pop the keyboard) just
        // because the menu appeared — the canvas is what the user is looking at
        searchField.clearFocus()
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(180).start()
        panel.animate()
            .translationX(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    panel.translationX = 0f
                    onStateChanged?.invoke(true)
                }
            }).start()
    }

    fun close() {
        if (!_open) return
        _open = false
        hideKeyboard()
        val w = panelWidth
        scrim.animate().alpha(0f).setDuration(160).start()
        panel.animate()
            .translationX(-w.toFloat())
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (!_open) visibility = View.GONE
                    onStateChanged?.invoke(false)
                }
            }).start()
    }

    /** re-injection after a rotation: restore the open state without animating */
    fun restoreOpen(open: Boolean) {
        if (!open) return
        _open = true
        dirty = true
        rebuildViews()
        visibility = View.VISIBLE
        val w = panelWidth
        panel.layoutParams = (panel.layoutParams as FrameLayout.LayoutParams).also {
            it.width = w
        }
        panel.translationX = 0f
        scrim.alpha = 1f
        onStateChanged?.invoke(true)
    }

    /** Project title + state line, so the menu answers "which project, saved?". */
    fun setHeader(title: String, meta: String) {
        titleView.text = title
        metaView.text = meta
    }

    /**
     * Store the freshly built tree. The view tree is only rebuilt when the menu
     * is actually on screen — a rebuild used to run on every project mutation,
     * including ones fired per video frame while recording.
     */
    fun refresh(sections: List<SidebarTree.Section>) {
        restoreStateInto(sections)
        currentSections = sections
        dirty = true
        if (_open) rebuildViews()
    }

    /**
     * Open the menu and walk down to a specific branch:
     * `openTo("sources", "layer.<uuid>", "arrange")`.
     *
     * The walk resolves each id against the REAL tree (so the key it records is
     * the same key a row click records) and scrolls the deepest hit into view.
     */
    fun openTo(sectionId: String, vararg itemIds: String) {
        var deepest = ""
        for (s in currentSections) {
            if (s.id != sectionId) continue
            s.expanded = true
            sectionState[s.id] = true
            var kids: List<SidebarTree.Item>? = s.children
            var prefix = s.id
            for (id in itemIds) {
                val list = kids ?: break
                val idx = list.indexOfFirst { it.key == id }
                if (idx < 0) break
                val key = itemKey(prefix, list, idx)
                val hit = list[idx]
                hit.expanded = true
                itemState[key] = true
                deepest = key
                kids = hit.children
                prefix = key
            }
            break
        }
        if (query.isNotEmpty()) {
            query = ""
            searchField.setText("")
        }
        open()
        rebuildViews()
        if (deepest.isNotEmpty()) reveal(deepest)
    }

    // ── expansion state ──

    private fun setAllExpanded(on: Boolean) {
        fun walk(items: List<SidebarTree.Item>, prefix: String) {
            for ((i, item) in items.withIndex()) {
                if (!item.isFolder) continue
                val key = itemKey(prefix, items, i)
                item.expanded = on
                itemState[key] = on
                item.children?.let { walk(it, key) }
            }
        }
        for (s in currentSections) {
            s.expanded = on
            sectionState[s.id] = on
            walk(s.children, s.id)
        }
        expandAllBtn.text = if (on) "Collapse all" else "Expand all"
        rebuildViews()
    }

    /**
     * Stable key for a row: parent path + the row's id (or label), with an
     * occurrence suffix so two siblings that read the same can still be told
     * apart. Index-based keys drift the moment a source is added on top, which
     * is what used to make expansion state look random.
     */
    private fun itemKey(prefix: String, items: List<SidebarTree.Item>, idx: Int): String {
        val it = items[idx]
        val base = it.key
        var dup = 0
        for (j in 0 until idx) if (items[j].key == base) dup++
        return if (dup > 0) "$prefix/$base#$dup" else "$prefix/$base"
    }

    /** Re-apply saved open/closed state onto a freshly built tree. */
    private fun restoreStateInto(sections: List<SidebarTree.Section>) {
        fun walk(items: List<SidebarTree.Item>, prefix: String) {
            for ((i, item) in items.withIndex()) {
                if (!item.isFolder) continue
                val key = itemKey(prefix, items, i)
                itemState[key]?.let { item.expanded = it }
                item.children?.let { walk(it, key) }
            }
        }
        for (s in sections) {
            sectionState[s.id]?.let { s.expanded = it }
            walk(s.children, s.id)
        }
    }

    // ── search ──

    private fun matches(label: String, q: String) = label.contains(q, ignoreCase = true)

    /** true when this row, or any row nested under it, matches */
    private fun containsMatch(item: SidebarTree.Item, q: String): Boolean {
        if (matches(item.label, q)) return true
        val kids = item.children ?: return false
        for (k in kids) if (containsMatch(k, q)) return true
        return false
    }

    private fun sectionMatches(s: SidebarTree.Section, q: String): Boolean {
        if (matches(s.label, q)) return true
        for (c in s.children) if (containsMatch(c, q)) return true
        return false
    }

    // ── view building ──

    /**
     * Rows are rendered from the REAL tree (never filtered copies), so a tap
     * mutates the same object the state maps key on: expand/collapse keeps
     * working while a search query is active.
     */
    private fun rebuildViews() {
        dirty = false
        content.removeAllViews()
        rowViews.clear()
        val q = query
        val searching = q.isNotBlank()
        var shown = 0
        for (s in currentSections) {
            if (searching && !sectionMatches(s, q)) continue
            if (shown > 0) addDivider(6)
            shown++
            addSectionRow(s, searching || s.expanded, searching && matches(s.label, q))
        }
        if (shown == 0) {
            content.addView(TextView(context).apply {
                text = if (searching) "No command matches \"$q\""
                    else "Nothing in the menu yet"
                setTextColor(UI.FG2)
                textSize = 12.5f
                setPadding(UI.dp(context, 16), UI.dp(context, 14), UI.dp(context, 16),
                    UI.dp(context, 14))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun addDivider(sidePadDp: Int) {
        val div = View(context).apply { setBackgroundColor(Color.argb(32, 255, 255, 255)) }
        content.addView(div, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            UI.dp(context, 1)).apply {
            topMargin = UI.dp(context, 3)
            bottomMargin = UI.dp(context, 3)
            marginStart = UI.dp(context, sidePadDp)
            marginEnd = UI.dp(context, sidePadDp)
        })
    }

    private fun addSectionRow(section: SidebarTree.Section, expanded: Boolean,
                              forceShowKids: Boolean) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = UI.dp(context, 48)
            isClickable = true
            isFocusable = true
            contentDescription = "${section.label} section"
            setPadding(UI.dp(context, 14), UI.dp(context, 6), UI.dp(context, 8),
                UI.dp(context, 6))
            background = GradientDrawable().apply {
                cornerRadius = UI.dpf(context, 10f)
                setColor(if (expanded) Color.argb(26, 255, 255, 255)
                else Color.TRANSPARENT)
            }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                section.expanded = !section.expanded
                sectionState[section.id] = section.expanded
                rebuildViews()
            }
        }
        row.addView(ImageView(context).apply {
            setImageDrawable(Ic.get(context, section.icon, Color.rgb(150, 160, 180)))
        }, LinearLayout.LayoutParams(UI.dp(context, 20), UI.dp(context, 20)))

        row.addView(TextView(context).apply {
            text = section.label
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(UI.dp(context, 11), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (section.badge != null) row.addView(badgeView(section.badge, Color.rgb(180, 190, 210)),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = UI.dp(context, 6)
            })

        row.addView(ImageView(context).apply {
            setImageDrawable(Ic.get(context,
                if (expanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right,
                Color.rgb(120, 130, 150)))
        }, LinearLayout.LayoutParams(UI.dp(context, 18), UI.dp(context, 18)))

        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        rowViews[section.id] = row

        if (expanded) {
            val kids = section.children
            for ((i, item) in kids.withIndex()) {
                addItemRow(item, depth = 1, path = section.id, siblings = kids, index = i,
                    ancestorMatch = forceShowKids)
            }
        }
    }

    private fun badgeView(text: String, color: Int): TextView = TextView(context).apply {
        this.text = text
        setTextColor(color)
        textSize = 10f
        maxLines = 1
        setPadding(UI.dp(context, 6), UI.dp(context, 2), UI.dp(context, 6), UI.dp(context, 2))
        background = GradientDrawable().apply {
            cornerRadius = UI.dpf(context, 7f)
            setColor(Color.argb(48, 255, 255, 255))
        }
    }

    private fun addItemRow(item: SidebarTree.Item, depth: Int, path: String,
                           siblings: List<SidebarTree.Item>, index: Int,
                           ancestorMatch: Boolean) {
        val q = query
        val searching = q.isNotBlank()
        val selfLiteral = searching && matches(item.label, q)
        val selfMatch = !searching || ancestorMatch || selfLiteral
        val belowMatch = searching && !ancestorMatch && containsMatch(item, q)
        if (searching && !selfMatch && !belowMatch) return

        val key = itemKey(path, siblings, index)
        val indent = UI.dp(context, 16 + depth * 15)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = UI.dp(context, 46)
            isClickable = true
            isFocusable = true
            alpha = if (item.enabled) 1f else 0.42f
            contentDescription = item.label
            setPadding(indent, UI.dp(context, 5), UI.dp(context, 8), UI.dp(context, 5))
            background = GradientDrawable().apply {
                cornerRadius = UI.dpf(context, 9f)
                setColor(when {
                    item.active -> Color.argb(70, 46, 92, 158)
                    item.isFolder && item.expanded -> Color.argb(22, 255, 255, 255)
                    else -> Color.TRANSPARENT
                })
            }
            setOnClickListener {
                if (!item.enabled) {
                    if (item.badge != null) UI.toast(context, item.badge)
                    return@setOnClickListener
                }
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (item.isFolder) {
                    item.expanded = !item.expanded
                    itemState[key] = item.expanded
                    rebuildViews()
                    return@setOnClickListener
                }
                item.action?.invoke()
                // toggles stay put and re-render; one-shot verbs close the menu
                if (item.keepOpen) rebuildViews() else if (_open) close()
            }
        }

        // a thin guide rail at depth ≥ 2 so a sub-sub-menu reads as a child
        if (depth >= 2) {
            row.addView(View(context).apply {
                setBackgroundColor(Color.argb(60, 255, 90, 44))
            }, LinearLayout.LayoutParams(UI.dp(context, 2), UI.dp(context, 18)).apply {
                marginEnd = UI.dp(context, 9)
            })
        }

        row.addView(ImageView(context).apply {
            val tint = when {
                !item.enabled -> Color.rgb(120, 126, 140)
                item.danger -> Color.rgb(255, 105, 105)
                item.active -> UI.ACCENT2
                else -> Color.rgb(178, 188, 205)
            }
            setImageDrawable(Ic.get(context, item.icon, tint))
        }, LinearLayout.LayoutParams(UI.dp(context, 18), UI.dp(context, 18)))

        row.addView(TextView(context).apply {
            text = item.label
            setTextColor(when {
                item.danger -> Color.rgb(255, 120, 120)
                item.active -> Color.WHITE
                else -> Color.rgb(224, 229, 238)
            })
            textSize = if (depth == 1) 13f else 12.5f
            maxLines = 2
            setPadding(UI.dp(context, 9), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (item.badge != null) row.addView(badgeView(item.badge,
            if (item.danger) Color.rgb(255, 140, 140) else Color.rgb(158, 168, 188)),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = UI.dp(context, 5)
            })

        if (item.isFolder) {
            row.addView(ImageView(context).apply {
                setImageDrawable(Ic.get(context,
                    if (item.expanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right,
                    Color.rgb(110, 120, 140)))
            }, LinearLayout.LayoutParams(UI.dp(context, 16), UI.dp(context, 16)))
        }

        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = UI.dp(context, 1)
        lp.marginEnd = UI.dp(context, 4)
        content.addView(row, lp)
        rowViews[key] = row

        val kids = item.children ?: return
        // while searching, a branch that contains a hit is open; otherwise the
        // user's own expand/collapse state decides
        val expanded = if (searching) (belowMatch || ancestorMatch || selfLiteral)
            else item.expanded
        if (!expanded) return
        for ((i, child) in kids.withIndex()) {
            addItemRow(child, depth + 1, key, kids, i,
                ancestorMatch = searching && (ancestorMatch || selfLiteral))
        }
    }

    private fun reveal(key: String) {
        val v = rowViews[key] ?: return
        scrollView.post {
            val target = (v.top - UI.dp(context, 56)).coerceAtLeast(0)
            scrollView.smoothScrollTo(0, target)
        }
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        try {
            imm?.hideSoftInputFromWindow(searchField.windowToken, 0)
        } catch (_: Exception) { }
        searchField.clearFocus()
    }
}
