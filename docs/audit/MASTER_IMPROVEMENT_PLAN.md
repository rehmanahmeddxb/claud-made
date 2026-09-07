# Master Improvement Plan — Ahmed Reaction Studio

Phases are ordered so that **nothing user-visible regresses**. Each phase states its exit criterion.

---

## PHASE 0 — Discovery ✅ DONE

Full static read, CI toolchain reproduced locally, baseline APK built (`BUILD OK`).
Artifacts: `PROJECT_AUDIT_REPORT.md`, `BUG_REGISTER.md`, `FEATURE_MATRIX.md`, `UX_UI_AUDIT.md`, `TEST_PLAN.md`.

---

## PHASE 1 — Critical UX + data integrity ✅ DONE (this change)

The user's own backlog items 1–5, plus the bugs the audit found underneath them.

| Item | Bugs closed |
|---|---|
| Undo snacks on every destructive op | BUG-01, BUG-02, BUG-03, BUG-12 |
| 48dp touch-target pass | BUG-11 |
| Home hygiene (thumbs, recycling, empty state, per-card ✕) | BUG-04, BUG-05, BUG-06 |
| No forced rotation + camera follows project aspect | BUG-07, BUG-14 |
| Screen-record pre-flight + themed dialogs | BUG-08 |
| Storage integrity | BUG-09, BUG-10, BUG-17 (save path) |
| Anti-regression guard | BUG-13 |

**Exit criterion:** APK builds, all JVM suites pass, CI green. ✅

---

## PHASE 2 — Architecture (NOT started — needs its own review cycle)

**Why not now:** `EditorActivity` is 3,290 lines with no UI tests guarding it. A blind split would produce regressions indistinguishable from the Phase-1 fixes.

Proposed order, one PR each, CI green between:

1. Extract **export/recording orchestration** → `editor/ExportCoordinator.kt` (~500 LOC). Lowest coupling to views.
2. Extract **camera ownership** (`liveCam`, torch, fallback, reconcile) → `editor/CameraCoordinator.kt` (~400 LOC).
3. Extract **dialogs** (aspect picker, text edit, export sheet, confirmations) → `editor/EditorDialogs.kt` (~350 LOC).
4. Extract **permissions + activity results** → `editor/EditorIntents.kt` (~200 LOC).
5. Delete the 19 `vararg Any?` stubs once each has a real owner; replace with explicit `error("…")` or removal.

**Exit criterion:** `EditorActivity` under 1,200 LOC; no `vararg Any?` stubs; CI green.

---

## PHASE 3 — Core functionality gaps

- Timeline/trim: the model has `durMs`/`pausedMediaMs` but no trim in/out. Add `trimInMs`/`trimOutMs` to `Layer` with JSON migration.
- Media GC pass on project open (not just on delete).
- Schema migration: `ProjectStore.SCHEMA` exists but is never read or written. Wire it before the format changes again.
- Crop as a first-class per-source property (currently only fit/fill).

---

## PHASE 4 — UX/UI

- Replace remaining ~70 toasts with the themed snackbar (a toast cannot be actioned or undone).
- TalkBack pass: the canvas and its handles are invisible to accessibility services today.
- Tablet/landscape layout intent (currently landscape is a rail bolt-on).
- Consistent naming: "Sources" vs "Layers" vs "Dock" still drift across surfaces.

---

## PHASE 5 — Data / calculation validation

`AudioMath` is already JVM-tested. Extend with property-based cases:
zero-length clip, 1-sample clip, speed 0.05/20×, 8 kHz and 48 kHz mics, loop wrap at exact boundary, limiter with a full-scale square wave.

---

## PHASE 6 — Performance

- Profile Home scroll after the thumbnail cache lands (Phase 1 fixed the algorithmic cause; the measurement still needs a device).
- `PreviewEngine` decoder pooling under 5+ video sources.
- Bitmap memory ceiling for `recordImageCache`.

---

## PHASE 7 — Security

Current posture is genuinely good (local-only, no network, no exported components, no secrets). Remaining:
- Validate imported file paths from `content://` against traversal when copying into `media/`.
- Consider `android:allowBackup="false"` — project media may be personal video.

---

## PHASE 8 — Testing

- Introduce a real JVM test framework for `core/` and `export/` (no Android types there — it is already possible today).
- Robolectric or instrumentation for `ProjectStore` and `HomeActivity`.
- CI: add `--warning-mode` gating and a lint pass.

---

## PHASE 9 — Production hardening

- Crash reporter (local file, no cloud, matching the offline promise).
- Storage quota UI: show project size on the Home card.
- ProGuard/R8 is currently unused; the app ships unminified.

---

## PHASE 10 — Final regression

Re-run the whole audit against the new baseline, on a physical device, using `TEST_PLAN.md`.
