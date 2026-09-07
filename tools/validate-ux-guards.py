#!/usr/bin/env python3
"""
UX / correctness guards that a compiler cannot enforce.

This repository's regression history is specific and repeatable: UI surfaces
get stubbed out to `private fun x(vararg args: Any?) { }`, which makes EVERY
call site compile while doing nothing, so a feature silently disappears from
the APK while CI stays green. These checks encode the invariants that broke.

Each guard states WHY it exists. Run from the repo root.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app" / "src" / "com" / "rehman" / "ahmedreactionstudio"

failures = []
notes = []


def read(rel: str) -> str:
    return (SRC / rel).read_text(encoding="utf-8")


# --- Guard 1 -----------------------------------------------------------------
# BUG-01/02/03: destructive verbs must route through the host so the undo
# snackbar is always offered. A ring calling ctrl.delete() directly is exactly
# how "delete from the wheel is silent" shipped.
radial = read("editor/RadialMenus.kt")
for bad, why in [
    ("h.ctrl.delete(", "delete must go through Host.deleteSource (undo snack)"),
    ("h.ctrl.toggleVisible(", "hide must go through Host.hideSource (undo snack)"),
]:
    if bad in radial:
        failures.append(f"RadialMenus.kt calls {bad} directly — {why}")

injector = read("editor/StudioLayoutInjector.kt")
for bad, why in [
    ("activity.ctrl.delete(", "panel delete must use activity.deleteSourceById"),
]:
    if bad in injector:
        failures.append(f"StudioLayoutInjector.kt calls {bad} directly — {why}")

# --- Guard 2 -----------------------------------------------------------------
# BUG-13: no new `vararg args: Any?` stubs. They swallow real calls silently.
# The existing ones are grandfathered with an explicit budget that must only
# ever shrink; adding one fails the build.
STUB_BUDGET = 11
editor = read("editor/EditorActivity.kt")
stubs = re.findall(r"private fun (\w+)\(vararg args: Any\?\)\s*\{\s*\}", editor)
if len(stubs) > STUB_BUDGET:
    failures.append(
        f"{len(stubs)} `vararg Any?` no-op stubs in EditorActivity.kt "
        f"(budget {STUB_BUDGET}). These compile away real call sites. "
        f"New: {stubs[STUB_BUDGET:]}"
    )
else:
    notes.append(f"vararg-stub budget: {len(stubs)}/{STUB_BUDGET}")

# --- Guard 3 -----------------------------------------------------------------
# BUG-04: the thumbnail writer must have a caller. It shipped with zero callers
# and every Home card was a grey box.
if "saveThumb" not in editor:
    failures.append(
        "Nothing calls ProjectStore.saveThumb() — Home thumbnails will be blank"
    )

# --- Guard 4 -----------------------------------------------------------------
# 2026-09-07: a 16:9 project IS a landscape project, so the studio may follow
# the canvas ratio (that is the requested behaviour). What is NOT allowed is
# taking that choice away from the user: any forced orientation must be behind
# the PREF_ORIENT policy, which has to offer a free and a locked mode too.
if re.search(r"SCREEN_ORIENTATION_SENSOR_(LANDSCAPE|PORTRAIT)", editor):
    for token in ("PREF_ORIENT", "ORIENT_AUTO", "ORIENT_LOCK",
                  "SCREEN_ORIENTATION_UNSPECIFIED"):
        if token not in editor:
            failures.append(
                "EditorActivity forces device rotation without the "
                f"user-overridable orientation policy (missing {token})."
            )

# --- Guard 5 -----------------------------------------------------------------
# BUG-11: touch targets. These specific constants regressed before.
checks = [
    ("editor/SourceDock.kt", r"ROW_DP = (\d+)", 56, "source dock row"),
    ("editor/MixerPanel.kt", r"LinearLayout\.LayoutParams\(UI\.dp\(context, (\d+)\), UI\.dp\(context, \d+\)\)\s*\n\s*lp\.marginStart", 48, "mixer mute/solo"),
]
for rel, pattern, minimum, what in checks:
    text = read(rel)
    m = re.search(pattern, text)
    if not m:
        notes.append(f"[skip] could not locate {what} in {rel}")
        continue
    val = int(m.group(1))
    if val < minimum:
        failures.append(f"{what} is {val}dp, below the {minimum}dp minimum ({rel})")

util = read("util/Util.kt")
m = re.search(r"LinearLayout\.LayoutParams\(ViewGroup\.LayoutParams\.WRAP_CONTENT, dp\(ctx, (\d+)\)\)", util)
if m and int(m.group(1)) < 48:
    failures.append(f"UI.chip height is {m.group(1)}dp, below the 48dp minimum")

# --- Guard 6 -----------------------------------------------------------------
# BUG-09: media reclamation must be undo-aware. Deleting bytes at delete-time
# makes UNDO restore a layer whose file is gone.
store = (SRC / "core" / "ProjectStore.kt").read_text(encoding="utf-8")
if "reclaimOrphanMedia" not in store:
    failures.append("ProjectStore.reclaimOrphanMedia missing — deleted media leaks")
if "reachableSnapshots" not in (SRC / "core" / "Undo.kt").read_text(encoding="utf-8"):
    failures.append("UndoStack.reachableSnapshots missing — media GC cannot be undo-safe")

# --- Guard 7 -----------------------------------------------------------------
# BUG-10: media collision naming must preserve the file extension.
if re.search(r'File\(dir,\s*"\$\{name\}_\$i"\)', store):
    failures.append(
        'copyIntoMedia appends the index after the extension ("clip.mp4_1"); '
        "insert it before the extension instead"
    )

# --- Guard 8 -----------------------------------------------------------------
# BUG-08: screen recording must pre-flight before burning a consent dialog.
if "screenRecordPreflight" not in editor:
    failures.append("startScreenCapture has no pre-flight (storage/notification checks)")

for n in notes:
    print(f"note: {n}")

if failures:
    print("\nUX GUARD FAILURES:\n")
    for f in failures:
        print(f"  ✗ {f}")
    sys.exit(1)

print("\nAll UX guards passed.")
