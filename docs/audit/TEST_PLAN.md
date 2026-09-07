# Test Plan — Ahmed Reaction Studio

Two sections: what **can** be verified without hardware (and now is), and what **must** be verified on a device before this app is called production-ready.

---

## Part 1 — Automated, runs in CI today

| Suite | What it proves | Count | Status |
|---|---|---:|---|
| `tools/validate-pipeline.py` | Export/record pipeline invariants: strided YUV, monotonic PTS, sample-count audio clock, EOS handling, probe-after-finalize | 81 | ✅ pass |
| `tools/validate-torch.py` | Torch is capability-checked, never faked, always released | 34 | ✅ pass |
| `tools/validate-integration.py` | Chrome wiring survives (panels bound, transport wired, rotation doesn't restart the engine) | 74 | ✅ pass |
| `tools/validate-ux-guards.py` **(new)** | Destructive ops offer undo; ≥48dp targets; no new no-op stubs; thumbnails have a caller; no forced rotation; undo-safe media GC | 8 guards | ✅ pass |
| `tools/audio-math-test` | Resampler, ClipCursor, Limiter, PTS math | 32 | ✅ pass |
| `tools/viewport-fit-test` | Contain-fit + panel budget across viewports | 420 | ✅ pass |
| `tools/layer-model-test` | Layer geometry, hit-test z-order, clamping | 28 | ✅ pass |
| `tools/step2-geom-check` | Selection chrome concentric with the drawn frame | — | ✅ pass |
| `build-apk.sh` | Full compile → dex → sign → verify | — | ✅ `BUILD OK` |
| Dex feature asserts | 30 named symbols present in the shipped APK | 30 | ✅ pass |

**Total: ~700 automated assertions, all green locally.**

### Gap: no real test framework
These are hand-rolled `main()` harnesses and grep-based guards. `core/` and `export/` are deliberately free of Android types and could run under JUnit **today** with no refactor. Recommended for Phase 8.

---

## Part 2 — Requires a physical device (NOT DONE — no hardware available)

Nothing below has been executed. Each item states what would falsify the implementation.

### 2.1 Camera
| # | Test | Pass criterion |
|---|---|---|
| C1 | Front camera live on canvas | Feed appears, correct orientation, mirrored |
| C2 | Rear camera switch | Switches without dropping the session |
| C3 | **9:16 project + live camera** | Feed is captured portrait, not letterboxed 16:9 — *validates BUG-14 fix* |
| C4 | Camera permission denied | Clear message, no crash, layer stays for undo |
| C5 | Permission revoked while running | Graceful stop, no crash |
| C6 | Another app steals the camera | "disconnected" path, revives on resume |
| C7 | Background → foreground | Camera released then restored |
| C8 | Torch on a lens with no LED | Button disabled, no fake "on" state |
| C9 | Torch front + back simultaneously | Both LEDs, or honest refusal |
| C10 | Screen-light fallback | Panel glows, brightness restored on exit |

### 2.2 Recording & export
| # | Test | Pass criterion |
|---|---|---|
| R1 | Composite record 60 s, 3 sources | Output plays; **A/V stays in sync at the end** |
| R2 | Export same project offline | Byte-comparable framing to preview; live camera shows frozen frame |
| R3 | Export each codec (H.264/HEVC/VP8/VP9) | Only offered codecs appear; each output plays |
| R4 | Cancel mid-export | No partial file left behind |
| R5 | Record until storage fills | Graceful stop, file finalized |
| R6 | **Screen record with <200 MB free** | Pre-flight dialog blocks it — *validates BUG-08 fix* |
| R7 | Screen record with notifications denied | Service survives or fails loudly |
| R8 | Leave app mid-recording | `onStop` finalizes the take |

### 2.3 Undo & destructive ops — *validates BUG-01/02/03*
| # | Test | Pass criterion |
|---|---|---|
| U1 | Delete source **from the radial wheel** | Snackbar appears with UNDO; UNDO restores it |
| U2 | Hide from wheel | Snack says audio still plays; UNDO restores |
| U3 | Hide from Sources panel eye | Same snack |
| U4 | Remove from Sources panel | Snack + UNDO |
| U5 | **Delete → UNDO → verify media still plays** | File was NOT reclaimed prematurely — *validates BUG-09 undo-safety* |
| U6 | Delete → leave editor → reopen | Orphan bytes reclaimed, project intact |
| U7 | Drag a volume slider then immediately toggle mute | Two separate undo steps — *validates BUG-12* |
| U8 | Aspect change → UNDO | Aspect restored, no rotation |

### 2.4 Home — *validates BUG-04/05/06*
| # | Test | Pass criterion |
|---|---|---|
| H1 | Fresh install | Empty state with working CTA |
| H2 | Create project, add source, exit | Card shows a **real thumbnail** |
| H3 | Scroll 30 projects | No jank, no flicker, no wrong-thumbnail flashes |
| H4 | Rotate on Home | No thread leak, cache survives |
| H5 | ⋮ menu | Open/Rename/Duplicate/Delete all work |

### 2.5 Orientation — *validates BUG-07*
| # | Test | Pass criterion |
|---|---|---|
| O1 | Pick 16:9 while holding portrait | **Phone does NOT rotate**; canvas re-fits |
| O2 | Same with rotation locked | No fight with the system |
| O3 | Rotate manually mid-edit | Chrome re-lays; camera/decoders/clock keep running |
| O4 | Rotate during a recording | Recording continues uninterrupted |

### 2.6 Robustness matrix
Sources: 0 / 1 / 2 / 8. Files: 4K video, 10 s clip, 2 h clip, corrupt MP4, AVI, HEIC, zero-byte.
Conditions: low memory, storage full, airplane mode, permission denied at each of the 4 prompts, app killed mid-edit (snapshot recovery).

### 2.7 Accessibility
TalkBack sweep of every screen. **Known failure:** the canvas exposes no virtual view hierarchy — sources cannot be selected or moved with TalkBack.

---

## Part 3 — Regression baseline

Before merging any future change:
```bash
python3 tools/validate-pipeline.py
python3 tools/validate-torch.py
python3 tools/validate-integration.py
python3 tools/validate-ux-guards.py
TC_ROOT=/tmp/ahmed-tc bash tools/audio-math-test/run.sh
TC_ROOT=/tmp/ahmed-tc bash tools/viewport-fit-test/run.sh
TC_ROOT=/tmp/ahmed-tc bash tools/layer-model-test/run.sh
TC_ROOT=/tmp/ahmed-tc bash tools/step2-geom-check.sh
TC_ROOT=/tmp/ahmed-tc ./build-apk.sh
```
All are wired into `.github/workflows/android.yml`.
