#!/usr/bin/env bash
# Compiles the canvas-first menu tree against a headless Host and runs the JVM
# checks that prove the menu is complete: three levels deep, every enabled row
# wired, and every mutating verb of RadialMenus.Host reachable from a row.
#
# Why this exists: the Studio removed every persistent control, so SidebarTree
# IS the feature set. A row that renders but does nothing — or a verb no row
# calls — is a silently missing feature, which is this repo's oldest failure.
#
# No Android device, no aapt2: resource ids are only ever passed around as
# ints here, so a generated stub R object stands in for the real one.
set -euo pipefail
cd "$(dirname "$0")/../.."
TC_ROOT="${TC_ROOT:-/tmp/ahmed-tc}"
export JAVA_HOME="${JAVA_HOME:-$TC_ROOT/java-runtime}"
export PATH="$JAVA_HOME/bin:$PATH"
OUT=build_out/sidebar-tree-test
rm -rf "$OUT"; mkdir -p "$OUT"
CORE=app/src/com/rehman/ahmedreactionstudio/core
ED=app/src/com/rehman/ahmedreactionstudio/editor

python3 - "$OUT/R.kt" <<'PY'
import re, sys
from pathlib import Path
out = Path(sys.argv[1])
lines = ['@file:Suppress("unused", "ConstPropertyName")',
         'package com.rehman.ahmedreactionstudio', '', 'object R {']
groups = {'drawable': sorted(p.stem for p in Path('res/drawable').glob('*.xml'))}
ids = re.findall(r'<item name="(\w+)" type="(\w+)"',
                 Path('res/values/ids.xml').read_text())
for name, kind in ids:
    groups.setdefault(kind, []).append(name)
for kind, names in groups.items():
    lines.append(f'    object {kind} {{')
    for i, n in enumerate(sorted(set(names)), start=1):
        lines.append(f'        const val {n}: Int = {i}')
    lines.append('    }')
lines.append('}')
out.write_text('\n'.join(lines) + '\n')
print(f'stub R.kt: ' + ', '.join(f'{k}={len(set(v))}' for k, v in groups.items()))
PY

"$TC_ROOT/kotlinc/bin/kotlinc" -nowarn -jvm-target 1.8 \
  -classpath "$TC_ROOT/android.jar" \
  app/src/com/rehman/ahmedreactionstudio/util/Util.kt \
  "$CORE/Model.kt" "$CORE/Sources.kt" "$CORE/Undo.kt" \
  "$ED/Icons.kt" "$ED/RadialMenus.kt" "$ED/RadialWheel.kt" "$ED/SidebarTree.kt" \
  "$OUT/R.kt" tools/sidebar-tree-test/FakeHost.kt tools/sidebar-tree-test/SidebarTreeTest.kt \
  -d "$OUT/classes"
java -cp "$OUT/classes:$TC_ROOT/kotlinc/lib/kotlin-stdlib.jar" \
  com.rehman.ahmedreactionstudio.editor.SidebarTreeTestKt
