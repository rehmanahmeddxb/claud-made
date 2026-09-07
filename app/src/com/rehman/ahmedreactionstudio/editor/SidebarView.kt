package com.rehman.ahmedreactionstudio.editor

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
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
 * Collapsible sidebar with a hierarchical tree: Section → Sub-menu → Sub-sub-menu.
 *
 * Width is ~240dp in landscape, ~260dp in portrait. Slides in/out from the left
 * edge with a 220ms animation. A [ScrollView] wraps the entire tree so long
 * source lists scroll independently of the canvas.
 *
 * The tree is rebuilt from [SidebarTree.build] every time [refresh] is called,
 * preserving expand/collapse state by section/item id.
 */
class SidebarView(context: Context) : FrameLayout(context) {

    // ── callbacks ──
    var onStateChanged: ((open: Boolean) -> Unit)? = null

    // ── state ──
    private var _open = false
    val isOpen get() = _open

    // ── dimensions ──
    private val sidebarWidth: Int
        get() = if (isLandscape()) UI.dp(context, 240) else UI.dp(context, 260)

    // ── views ──
    private val scrim = View(context).apply {
        setBackgroundColor(Color.argb(100, 0, 0, 0))
        isClickable = true
        isFocusable = true
        visibility = View.GONE
        alpha = 0f
    }
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.rgb(14, 16, 22))
    }
    private val scrollContent = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, UI.dp(context, 4), 0, UI.dp(context, 16))
    }
    private val scrollView = ScrollView(context).apply {
        isFillViewport = false
        isVerticalScrollBarEnabled = true
        scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
        addView(scrollContent, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    // expansion state persistence: section id → expanded
    private val sectionState = HashMap<String, Boolean>()
    // item path → expanded (for sub-menus)
    private val itemState = HashMap<String, Boolean>()

    private var currentSections: List<SidebarTree.Section> = emptyList()

    init {
        // scrim covers the whole area, tap to close
        addView(scrim, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        scrim.setOnClickListener { toggle() }

        // header row inside panel
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UI.dp(context, 14), UI.dp(context, 10),
                UI.dp(context, 10), UI.dp(context, 10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.rgb(18, 20, 28))
            }
        }
        val title = TextView(context).apply {
            text = "Menu"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        header.addView(title, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val closeBtn = ImageView(context).apply {
            setImageDrawable(Ic.get(context, R.drawable.ic_close, Color.WHITE))
            setPadding(UI.dp(context, 8), UI.dp(context, 8),
                UI.dp(context, 8), UI.dp(context, 8))
            isClickable = true
            isFocusable = true
            contentDescription = "Close menu"
            setOnClickListener { toggle() }
        }
        header.addView(closeBtn, LinearLayout.LayoutParams(
            UI.dp(context, 36), UI.dp(context, 36)))
        panel.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // divider
        val div = View(context).apply {
            setBackgroundColor(Color.argb(50, 255, 255, 255))
        }
        panel.addView(div, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(context, 1)))

        panel.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // panel starts offscreen to the left
        panel.translationX = -sidebarWidth.toFloat()
        addView(panel, LayoutParams(sidebarWidth,
            ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
    }

    // ── public API ──

    fun toggle() {
        if (_open) close() else open()
    }

    fun open() {
        if (_open) return
        _open = true
        scrim.visibility = View.VISIBLE
        scrim.animate().alpha(1f).setDuration(200).start()
        panel.animate()
            .translationX(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    onStateChanged?.invoke(true)
                }
            }).start()
    }

    fun close() {
        if (!_open) return
        _open = false
        scrim.animate().alpha(0f).setDuration(180)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    scrim.visibility = View.GONE
                }
            }).start()
        panel.animate()
            .translationX(-sidebarWidth.toFloat())
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    onStateChanged?.invoke(false)
                }
            }).start()
    }

    /**
     * Rebuild the tree from a fresh [SidebarTree.build] call, preserving
     * expansion state by section/item id.
     */
    fun refresh(sections: List<SidebarTree.Section>) {
        // persist current expansion before rebuild
        for (s in currentSections) {
            sectionState[s.id] = s.expanded
            persistItemState(s.id, s.children)
        }
        // apply persisted state to new sections
        for (s in sections) {
            s.expanded = sectionState[s.id] ?: s.expanded
            restoreItemState(s.id, s.children)
        }
        currentSections = sections
        rebuildViews()
    }

    // ── internal: view building ──

    private fun rebuildViews() {
        scrollContent.removeAllViews()
        for ((idx, section) in currentSections.withIndex()) {
            addSectionView(section, idx)
        }
    }

    private fun addSectionView(section: SidebarTree.Section, index: Int) {
        if (index > 0) {
            // divider between sections
            val div = View(context).apply {
                setBackgroundColor(Color.argb(35, 255, 255, 255))
            }
            scrollContent.addView(div, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(context, 1)).apply {
                topMargin = UI.dp(context, 4)
                bottomMargin = UI.dp(context, 4)
            })
        }

        // section header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UI.dp(context, 14), 0, UI.dp(context, 8), 0)
            minimumHeight = UI.dp(context, 44)
            isClickable = true
            isFocusable = true
            contentDescription = "${section.label} section"
            background = GradientDrawable().apply {
                cornerRadius = UI.dpf(context, 6f)
                setColor(if (section.expanded) Color.argb(30, 255, 255, 255)
                         else Color.TRANSPARENT)
            }
            setOnClickListener {
                section.expanded = !section.expanded
                sectionState[section.id] = section.expanded
                rebuildViews()
            }
        }
        val icon = ImageView(context).apply {
            setImageDrawable(Ic.get(context, section.icon,
                Color.rgb(160, 170, 190)))
        }
        header.addView(icon, LinearLayout.LayoutParams(
            UI.dp(context, 20), UI.dp(context, 20)))

        val label = TextView(context).apply {
            text = section.label
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(UI.dp(context, 10), 0, 0, 0)
        }
        header.addView(label, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (section.badge != null) {
            val badge = TextView(context).apply {
                text = section.badge
                setTextColor(Color.rgb(180, 190, 210))
                textSize = 11f
                setPadding(UI.dp(context, 6), UI.dp(context, 2),
                    UI.dp(context, 6), UI.dp(context, 2))
                background = GradientDrawable().apply {
                    cornerRadius = UI.dpf(context, 8f)
                    setColor(Color.argb(60, 255, 255, 255))
                }
            }
            header.addView(badge, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = UI.dp(context, 4)
            })
        }

        val chevron = ImageView(context).apply {
            setImageDrawable(Ic.get(context,
                if (section.expanded) R.drawable.ic_chevron_down
                else R.drawable.ic_chevron_right,
                Color.rgb(120, 130, 150)))
        }
        header.addView(chevron, LinearLayout.LayoutParams(
            UI.dp(context, 20), UI.dp(context, 20)))

        scrollContent.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(context, 44)))

        // children (only when expanded)
        if (section.expanded) {
            for ((i, item) in section.children.withIndex()) {
                addItemView(item, depth = 1, path = "${section.id}.$i")
            }
        }
    }

    private fun addItemView(item: SidebarTree.Item, depth: Int, path: String) {
        val indent = UI.dp(context, 14 + depth * 16)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(indent, 0, UI.dp(context, 8), 0)
            minimumHeight = UI.dp(context, 44)
            isClickable = true
            isFocusable = true
            alpha = if (item.enabled) 1f else 0.45f
            contentDescription = item.label
            background = GradientDrawable().apply {
                cornerRadius = UI.dpf(context, 6f)
                setColor(when {
                    item.active -> Color.rgb(36, 72, 120)
                    else -> Color.TRANSPARENT
                })
            }
            setOnClickListener {
                if (!item.enabled) return@setOnClickListener
                if (item.isFolder) {
                    item.expanded = !item.expanded
                    itemState[path] = item.expanded
                    rebuildViews()
                } else {
                    item.action?.invoke()
                    // auto-close sidebar on leaf action
                    close()
                }
            }
        }

        val icon = ImageView(context).apply {
            val tintColor = when {
                item.danger -> Color.rgb(255, 90, 90)
                item.active -> Color.WHITE
                else -> Color.rgb(180, 190, 210)
            }
            setImageDrawable(Ic.get(context, item.icon, tintColor))
        }
        row.addView(icon, LinearLayout.LayoutParams(
            UI.dp(context, 18), UI.dp(context, 18)))

        val label = TextView(context).apply {
            text = item.label
            setTextColor(when {
                item.danger -> Color.rgb(255, 90, 90)
                item.active -> Color.WHITE
                else -> Color.rgb(220, 225, 235)
            })
            textSize = 12.5f
            maxLines = 1
            setPadding(UI.dp(context, 8), 0, 0, 0)
        }
        row.addView(label, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (item.badge != null) {
            val badge = TextView(context).apply {
                text = item.badge
                setTextColor(Color.rgb(150, 160, 180))
                textSize = 10f
                setPadding(UI.dp(context, 5), UI.dp(context, 1),
                    UI.dp(context, 5), UI.dp(context, 1))
                background = GradientDrawable().apply {
                    cornerRadius = UI.dpf(context, 6f)
                    setColor(Color.argb(50, 255, 255, 255))
                }
            }
            row.addView(badge, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = UI.dp(context, 4)
            })
        }

        if (item.isFolder) {
            val chevron = ImageView(context).apply {
                setImageDrawable(Ic.get(context,
                    if (item.expanded) R.drawable.ic_chevron_down
                    else R.drawable.ic_chevron_right,
                    Color.rgb(100, 110, 130)))
            }
            row.addView(chevron, LinearLayout.LayoutParams(
                UI.dp(context, 18), UI.dp(context, 18)))
        }

        scrollContent.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(context, 44)).apply {
            topMargin = UI.dp(context, 1)
        })

        // render children if folder is expanded
        if (item.isFolder && item.expanded && item.children != null) {
            for ((i, child) in item.children.withIndex()) {
                addItemView(child, depth = depth + 1, path = "$path.$i")
            }
        }
    }

    // ── expansion state persistence ──

    private fun persistItemState(prefix: String, items: List<SidebarTree.Item>?) {
        items ?: return
        for ((i, item) in items.withIndex()) {
            val key = "$prefix.$i"
            if (item.isFolder) {
                itemState[key] = item.expanded
                persistItemState(key, item.children)
            }
        }
    }

    private fun restoreItemState(prefix: String, items: List<SidebarTree.Item>?) {
        items ?: return
        for ((i, item) in items.withIndex()) {
            val key = "$prefix.$i"
            itemState[key]?.let { item.expanded = it }
            if (item.isFolder) restoreItemState(key, item.children)
        }
    }

    private fun isLandscape(): Boolean {
        val conf = context.resources.configuration
        return conf.screenWidthDp > conf.screenHeightDp
    }
}
