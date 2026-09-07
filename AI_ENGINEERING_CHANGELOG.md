# AI Engineering Changelog

## 2026-09-07 — Full audit + Phase 1 (backlog items 1-5)

**Scope:** repository-wide audit (45 Kotlin files, ~17k LOC) followed by the
highest-priority fixes. Audit reports live in `docs/audit/`.

### Verification performed
- Reproduced the CI toolchain locally; **`build-apk.sh` → `BUILD OK`** (1,476,305 B signed APK).
- All existing suites pass: pipeline 81, torch 34, integration 74, audio 32, viewport 420, layers 28, geometry green.
- New `tools/validate-ux-guards.py` (8 guards) passes and is wired into CI.
- All 30 CI dex feature symbols present; 8 new symbols confirmed in the shipped dex.
- **No device was available** — all camera/codec/hardware runtime behaviour remains UNVERIFIED. See `docs/audit/TEST_PLAN.md` Part 2.

### Fixed
- **BUG-01/02/03** Destructive ops (delete/hide) from the radial wheel and Sources panel were completely silent with no undo affordance. All five paths now funnel through `deleteSource()`/`hideSource()`.
- **BUG-04** `ProjectStore.saveThumb()` had zero callers — every Home card was a grey box. Thumbnails now render through the shared `Compositor`.
- **BUG-05** Home `ListView` ignored `convertView` and spawned a thread per bind. Now ViewHolder recycling + single executor + 4 MB LRU cache.
- **BUG-06** Home had no empty state.
- **BUG-07** Aspect change force-rotated the handset mid-edit. Orientation is now the user's choice.
- **BUG-08** Screen recording had no pre-flight; added storage/notification checks + themed dialogs.
- **BUG-09** Deleted layers orphaned their media. Reclamation is **undo-aware** — bytes survive while any undo/redo snapshot references them.
- **BUG-10** Media collision naming produced `clip.mp4_1`; now `clip_1.mp4`.
- **BUG-11** 48dp touch-target pass across rail, transport, dock, mixer, chips.
- **BUG-12** Undo throttle could swallow a distinct edit; coalescing is now kind-aware.
- **BUG-13** CI guard against reintroducing no-op `vararg Any?` stubs.
- **BUG-14** Live camera feed now targets the project aspect.
- **BUG-17** (partial) `ProjectStore.save()` returns success; failures surface to the user.

### Deliberately NOT done
- **BUG-18** `EditorActivity.kt` is 3,290 lines. Splitting it blind, with no UI tests and no device, would produce regressions indistinguishable from these fixes. Sequenced as Phase 2 in `docs/audit/MASTER_IMPROVEMENT_PLAN.md`.
- Global sweep of ~120 silent `catch (_: Exception) {}` — same reasoning.

### Known limitation
Canvas resize handles were raised 24→28dp, **not** 48dp. Nine handles on one box cannot each be 48dp without overlapping on small PiPs. Rationale in `docs/audit/UX_UI_AUDIT.md` §3.
