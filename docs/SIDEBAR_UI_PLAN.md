# Sidebar UI Redesign Plan

## Goal

Replace the radial-wheel + bottom-sheet + right-rail interface with a **collapsible
sidebar menu** that works in both landscape and portrait. The sidebar uses a
hierarchical tree: **Menu → Sub-Menu → Sub-Sub-Menu**, each level collapsible.
A **hamburger (☰)** button toggles the sidebar. **Floating transport buttons**
(Play/Pause, Stop, Record) live directly on the canvas. Maximum screen real
estate goes to the canvas.

---

## 1. Architecture Overview

```
┌────────────────────────────────────────────────────────────────────┐
│  ☰  Project Name            [Aspect] [Undo] [Redo]    [Save] [⋮] │ ← Top Strip (40dp)
├──────┬─────────────────────────────────────────────────────────────┤
│      │                                                             │
│  S   │           ┌───────────────────────┐                         │
│  I   │           │                       │                         │
│  D   │           │     C A N V A S       │   [▶ Play]             │
│  E   │           │                       │   [⏸ Pause]            │
│  B   │           │   (StageView)         │   [⏹ Stop]             │
│  A   │           │                       │   [● Rec]              │
│  R   │           │                       │                         │
│      │           └───────────────────────┘                         │
│  ▓   │                                                             │
│  c   │  [seek bar ──────────────── 00:00 / 03:24]                  │
│  r   │                                                             │
│  o   │                                                             │
│  l   │                                                             │
│  l   │                                                             │
├──────┴─────────────────────────────────────────────────────────────┤
│  (no bottom sheet needed — everything lives in the sidebar)        │
└────────────────────────────────────────────────────────────────────┘
```

### Sidebar (collapsed vs expanded)

| State       | Width          | Shows                                       |
|-------------|----------------|---------------------------------------------|
| Collapsed   | 0 dp (hidden)  | Nothing — hamburger only in top strip       |
| Expanded    | 240 dp (≈28%)  | Full tree with scrollbar                    |
| Portrait    | 260 dp         | Same tree, slightly wider for touch targets |

Canvas always gets ≥ 70% of screen width in landscape, ≥ 100% when sidebar collapsed.

---

## 2. New Files

| File | Purpose |
|------|---------|
| `editor/SidebarView.kt` | The collapsible sidebar: tree of sections, sub-sections, sub-sub-sections |
| `editor/SidebarTree.kt` | Data model: defines every menu item, its hierarchy, icon, action callback |
| `editor/FloatingControls.kt` | Floating transport buttons on the canvas (Play, Pause, Stop, Record) |
| `res/drawable/ic_menu.xml` | Hamburger (☰) three-line icon |
| `res/drawable/ic_chevron_right.xml` | Expand indicator (▸) |
| `res/drawable/ic_chevron_down.xml` | Collapse indicator (▾) |

---

## 3. Modified Files

| File | Change |
|------|--------|
| `editor/StudioLayoutInjector.kt` | Replace right rail + bottom sheet with sidebar + floating controls |
| `editor/EditorActivity.kt` | Wire sidebar toggle, bind actions, remove old rail/sheet code |
| `editor/RadialMenus.kt` | Keep as data source — SidebarTree reads the same Host interface |

---

## 4. Sidebar Menu Tree (Complete)

Every item currently in the radial menus, panels, and dock is mapped into
this tree. Nothing is lost; everything gets a home.

```
☰ SIDEBAR
│
├─ 📂 SOURCES
│   ├─ (list of all layers — tap to select)
│   │   ├─ 👁 Visibility toggle
│   │   ├─ 🔇 Mute toggle
│   │   ├─ ⬆ Move up
│   │   └─ ⬇ Move down
│   ├─ ➕ Add Source ▸
│   │   ├─ 📷 Camera (live)
│   │   ├─ 🎬 Video file
│   │   ├─ 🖼 Image
│   │   ├─ 🖥 Screen record
│   │   └─ 📝 Text overlay
│   ├─ ➖ Remove selected
│   └─ 📋 Duplicate selected
│
├─ 🎬 SOURCE CONTROLS  (shown when a source is selected)
│   ├─ 👁 Hide / Show
│   ├─ 🔒 Lock / Unlock
│   ├─ ⏯ Pause / Resume layer
│   ├─ 🔄 Fit Mode ▸
│   │   ├─ Fill (cover)
│   │   └─ Fit (letterbox)
│   ├─ 📐 Transform ▸
│   │   ├─ Fit to canvas
│   │   ├─ Reset position
│   │   ├─ Corner: top-left
│   │   ├─ Corner: top-right
│   │   ├─ Corner: bottom-left
│   │   └─ Corner: bottom-right
│   ├─ 🔲 Set as background
│   └─ ⚙ Advanced properties…
│
├─ 🎵 AUDIO
│   ├─ 🎛 Mixer panel
│   ├─ 🎤 Mic gain ▸
│   │   ├─ +10%
│   │   ├─ −10%
│   │   └─ Reset 100%
│   └─ (per-source) ▸
│       ├─ Volume +10%
│       ├─ Volume −10%
│       ├─ Mute / Unmute
│       └─ Solo
│
├─ ⏺ RECORD
│   ├─ ▶ Start recording / ■ Stop & save
│   ├─ ▶ Play / ⏸ Pause
│   ├─ ⏹ Stop
│   ├─ 📷 Camera take
│   ├─ 🖥 Screen record
│   ├─ 📸 Snapshot frame
│   ├─ 🔄 Restart
│   └─ 💡 Light ▸
│       ├─ Torch ▸
│       │   ├─ Front torch
│       │   ├─ Back torch
│       │   └─ Both torches
│       └─ Screen light
│
├─ 🖼 CANVAS
│   ├─ Aspect ratio ▸
│   │   ├─ 16:9 Landscape
│   │   ├─ 9:16 Portrait
│   │   └─ 1:1 Square
│   ├─ Background ▸
│   │   ├─ Dark
│   │   ├─ Black
│   │   ├─ White
│   │   ├─ Orange
│   │   ├─ Navy
│   │   ├─ Green
│   │   └─ Purple
│   ├─ Full canvas mode
│   ├─ Fit all sources
│   └─ Selection → background
│
├─ 📤 EXPORT
│   ├─ ⚡ Quick export (720p30)
│   └─ ⚙ Export settings…
│
├─ 📁 PROJECT
│   ├─ ✏ Rename
│   ├─ 💾 Save now
│   ├─ 📸 Snapshot frame
│   ├─ ℹ Stats overlay toggle
│   ├─ ↩ Undo
│   ├─ ↪ Redo
│   ├─ 🔍 Diagnostics
│   └─ ✖ Close project
│
└─ ⚙ SETTINGS
    ├─ 📤 Export quality…
    ├─ 📂 Save folder: [label]
    ├─ ↩ Use default album
    ├─ 🔄 Orientation ▸
    │   ├─ Follow canvas
    │   ├─ Auto rotate
    │   └─ Lock current
    └─ ℹ About / Diagnostics
```

---

## 5. SidebarView Component Design

### 5.1 Class Structure

```kotlin
class SidebarView(context: Context) : LinearLayout(context) {

    interface Host {
        // reuses the exact same interface as RadialMenus.Host
        // no new methods needed — all actions already exist
    }

    // Sections (top-level collapsible groups)
    private val sections = mutableListOf<SidebarSection>()

    // ScrollView wrapping the tree
    private val scrollView: ScrollView

    fun bind(host: Host)           // populate the tree from live project state
    fun refresh()                  // re-read state, update badges/active states
    fun expandSection(id: String)
    fun collapseSection(id: String)
    fun collapseAll()
}
```

### 5.2 Section Data Model

```kotlin
data class SidebarSection(
    val id: String,           // "sources", "controls", "audio", etc.
    val icon: Int,            // drawable resource
    val label: String,
    val badge: String?,       // e.g. "3" for source count
    val children: List<SidebarItem>,
    var expanded: Boolean = false
)

data class SidebarItem(
    val icon: Int,
    val label: String,
    val badge: String?,
    val active: Boolean,
    val danger: Boolean,
    val enabled: Boolean,
    val children: List<SidebarItem>?,  // null = leaf action, non-null = sub-menu
    val action: (() -> Unit)?,
    var expanded: Boolean = false
)
```

### 5.3 Visual Design

```
┌─────────────────────────┐
│ 📂 Sources          ▾ 3 │ ← Section header (bold, 14sp, icon+label+badge+chevron)
│  📷 Camera 1       👁 🔇│ ← Layer row (with inline toggles)
│  🎬 reaction.mp4   👁 🔇│
│  ───────────────────── │
│  ➕ Add Source       ▸  │ ← Sub-menu (collapsed — tap to expand)
│  ➖ Remove               │ ← Leaf action
│  📋 Duplicate            │
├─────────────────────────┤
│ 🎬 Source Controls    ▸ │ ← Section header (collapsed)
├─────────────────────────┤
│ 🎵 Audio              ▸ │
├─────────────────────────┤
│ ⏺ Record              ▾ │ ← Section header (expanded)
│  ▶ Start recording      │ ← Leaf action
│  ▶ Play                 │
│  💡 Light            ▸  │ ← Sub-menu (collapsed)
├─────────────────────────┤
│ ...                     │
│    ↕ scrollbar          │
└─────────────────────────┘
```

- **Background**: `Color.rgb(14, 16, 22)` — darker than canvas border
- **Section headers**: 44dp height, bold 14sp, `Color.WHITE`, chevron right/down
- **Leaf items**: 44dp height, 13sp, left-indented 16dp under parent
- **Sub-sub-items**: 13sp, left-indented 32dp
- **Active state**: blue tint background `Color.rgb(36, 72, 120)`
- **Danger state**: red text `Color.rgb(255, 90, 90)`
- **Divider**: 1dp `Color.argb(40, 255, 255, 255)` between sections
- **Scrollbar**: thin, semi-transparent, always visible when content overflows
- **Touch targets**: minimum 44dp height (matches existing BUG-11 fix)

### 5.4 Animation

- Sidebar slides in/out with `TranslateXAnimation` (200ms, decelerate)
- Section expand/collapse uses `LayoutTransition` for smooth height changes
- Canvas area reflows via `applyViewportInsets()` when sidebar opens/closes

---

## 6. Floating Transport Controls

### 6.1 Position

Floating buttons are positioned at **bottom-right of the canvas**, stacked
vertically with 8dp gaps, 16dp from the right and bottom edges.

```
Canvas
│
│
│                  [▶] Play     ← 48dp circular FAB
│                  [⏸] Pause    ← 48dp circular FAB
│                  [⏹] Stop     ← 48dp circular FAB
│                  [●] Record   ← 56dp circular FAB (larger, red accent)
```

### 6.2 Behavior

- Semi-transparent dark background (`Color.argb(180, 14, 16, 22)`) with
  rounded corners (24dp)
- White icons from existing `R.drawable.ic_play`, `ic_pause`, `ic_stop`
- Record button pulses red when recording
- Buttons auto-hide in Full Canvas mode
- Tap Play → toggles to Pause icon; same button, state-driven
- Buttons fade in/out with 150ms alpha animation

### 6.3 FloatingControls.kt

```kotlin
class FloatingControls(context: Context) : LinearLayout(context) {

    private val playPauseBtn: FloatingActionButton
    private val stopBtn: FloatingActionButton
    private val recordBtn: FloatingActionButton

    fun bind(onPlayPause: () -> Unit, onStop: () -> Unit,
             onRecord: () -> Unit)

    fun update(playing: Boolean, recording: Boolean, recReady: Boolean)
    fun setVisible(show: Boolean)
}
```

---

## 7. Hamburger Button

- Lives in the **top strip**, leftmost position
- 40dp × 40dp touch target
- Three horizontal lines icon (`ic_menu.xml`)
- Toggles sidebar open/closed
- When sidebar is open, hamburger gets a subtle highlight
- Accessible: `contentDescription = "Toggle menu"`

---

## 8. Top Strip (replaces old topBar)

```
┌──────────────────────────────────────────────────────────────────────┐
│ ☰ │ Ahmed Reaction    16:9 │ ↩ ↪ │ 💾 ● unsaved │ ⋮              │
└──────────────────────────────────────────────────────────────────────┘
  40dp height, semi-transparent dark background
  Left: hamburger
  Center-left: project name
  Center: aspect chip
  Center-right: undo / redo
  Right: save indicator, overflow menu (⋮)
```

---

## 9. Orientation Handling

### Landscape (default)
- Sidebar on the **left** edge, 240dp wide when expanded
- Canvas fills remaining width
- Floating controls bottom-right of canvas
- Top strip full width

### Portrait
- Sidebar on the **left** edge, 260dp wide when expanded (slightly wider for readability)
- Canvas fills remaining width (or full width when sidebar closed)
- Floating controls bottom-center of canvas
- Top strip full width

### Auto-collapse
- Sidebar auto-collapses when recording starts (to maximize canvas visibility)
- Sidebar auto-collapses on Back press if open (before exiting the activity)

---

## 10. Implementation Phases

### Phase 1: Sidebar Shell (files: SidebarView.kt, SidebarTree.kt, ic_menu.xml, ic_chevron_*.xml)
- [x] Create `SidebarView` with ScrollView + LinearLayout tree
- [x] Create `SidebarTree` data model that reads from `RadialMenus.Host`
- [x] Hamburger icon drawable
- [x] Chevron expand/collapse icons
- [x] Section expand/collapse with tap animation
- [x] Sub-menu and sub-sub-menu nesting
- [x] Scrollbar styling

### Phase 2: Floating Controls (file: FloatingControls.kt)
- [x] Circular FAB-style buttons
- [x] Play/Pause state toggle
- [x] Stop button
- [x] Record button with pulse animation
- [x] Position bottom-right on canvas
- [x] Bind to existing EditorActivity actions

### Phase 3: Integration (files: StudioLayoutInjector.kt, EditorActivity.kt)
- [x] Replace right rail with sidebar
- [x] Remove bottom sheet (sidebar replaces it)
- [x] Add hamburger to top strip
- [x] Wire sidebar toggle
- [x] Update `applyViewportInsets()` for sidebar width
- [x] Wire all menu actions to existing Host methods
- [x] Orientation-aware layout

### Phase 4: Polish
- [x] Slide animation for sidebar open/close
- [x] Auto-collapse on recording start
- [x] Badge updates on layer add/remove
- [x] Active states for current aspect, playing state
- [x] Back button closes sidebar before exiting

---

## 11. What Gets Removed

| Old Component | Replaced By |
|---------------|-------------|
| `RadialWheel.kt` (visual overlay) | SidebarView (same Host interface, new UI) |
| `RadialMenus.kt` (tree definition) | SidebarTree.kt (reuses same Host, adapted to tree) |
| `SourceDock.kt` | Sources section in sidebar |
| `SourcesPanel.kt` | Sources section in sidebar |
| `MixerPanel.kt` | Audio section in sidebar |
| `ControlsPanel.kt` | Record section in sidebar + floating controls |
| Right rail (`sideRail`, `railContent`) | SidebarView |
| Bottom sheet (`sheet`, `panelScroll`) | SidebarView |
| Bottom dock transport row | Floating controls on canvas |
| Quick bar / contextual pill | Source Controls section in sidebar |

### What Stays

| Component | Why |
|-----------|-----|
| `StageView.kt` | Canvas — unchanged |
| `PreviewEngine.kt` | Video preview — unchanged |
| `SourceController` | All mutations still go through it |
| `RadialMenus.Host` interface | Reused as-is by SidebarView |
| `LiveCamera.kt` | Camera capture — unchanged |
| Export pipeline | Unchanged |
| Project model / store | Unchanged |

---

## 12. Size Budget

| Element | Size | Notes |
|---------|------|-------|
| Sidebar expanded | 240dp / 260dp | ~28% of a 840dp landscape phone |
| Sidebar collapsed | 0dp | Hidden offscreen |
| Top strip | 40dp height | Compact but readable |
| Floating btns | 48dp each (56dp record) | Circular, 16dp from edges |
| Canvas | Everything else | Always ≥ 70% width in landscape |

---

## 13. Key Design Principles

1. **Canvas first** — the sidebar is hidden by default on new projects; the
   canvas fills the entire screen until the user taps ☰
2. **One hand, one tap** — every action is at most 3 taps deep (section → sub → action)
3. **Nothing lost** — every radial menu item, every panel button, every dock
   action has an equivalent in the sidebar tree
4. **State-aware** — the tree reads live project state on every refresh; badges,
   active highlights and enabled/disabled states update automatically
5. **Same backend** — all actions go through `SourceController` / `Host` exactly
   as the radial menus did; undo/redo and preview==export guarantees hold
