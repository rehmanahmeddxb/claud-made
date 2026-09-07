# Feature Matrix — Ahmed Reaction Studio

Legend: ✅ works · ⚠️ partial / caveat · ❌ absent · 🔧 fixed in this pass
"Tested" states the honest verification level — **Static** = code inspection, **JVM** = executed test, **Build** = compiles into the APK, **Device** = requires hardware (none available).

## Canvas & composition

| Feature | Exists | Broken | Missing | Priority | Tested |
|---|---|---|---|---|---|
| 16:9 / 9:16 / 1:1 canvas | ✅ | | | P0 | JVM (geom) |
| Custom aspect ratio | | | ❌ | P3 | — |
| Normalized layer geometry | ✅ | | | P0 | JVM |
| Contain-fit viewport | ✅ | | | P0 | JVM |
| Preview == export (one Compositor) | ✅ | | | P0 | Static |
| Aspect change without forced rotation | 🔧 | was ⚠️ | | P2 | Build |
| Safe-area / cutout insets | ✅ | | | P1 | Static |
| Full-canvas mode | ✅ | | | P2 | Static |
| Snap guides / alignment aids | | | ❌ | P3 | — |

## Sources

| Feature | Exists | Broken | Missing | Priority | Tested |
|---|---|---|---|---|---|
| Add video / image / text | ✅ | | | P0 | Static |
| Live camera on canvas | ✅ | | | P0 | Device |
| Camera take (fullscreen recorder) | ✅ | | | P1 | Device |
| Screen recording source | ✅ | | | P1 | Device |
| Show / hide (audio kept) | ✅ | | | P0 | Build |
| Mute / solo / volume | ✅ | | | P0 | JVM (mix) |
| Lock | ✅ | | | P2 | Static |
| Fit / fill per source | ✅ | | | P0 | JVM |
| Move / resize / rotate / pinch | ✅ | | | P0 | Static |
| Crop (true crop, not fit/fill) | | | ❌ | P2 | — |
| Opacity | ✅ | | | P2 | Static |
| Z-order + drag reorder | ✅ | | | P1 | Static |
| Duplicate (live cam refused) | ✅ | | | P2 | Static |
| Delete **with undo snack** | 🔧 | was ❌ | | P1 | Build |
| Trim in/out | | | ❌ | P2 | — |
| Border / corner radius | | | ❌ | P3 | — |

## Camera & hardware

| Feature | Exists | Broken | Missing | Priority | Tested |
|---|---|---|---|---|---|
| Front / rear switch | ✅ | | | P0 | Device |
| Mirror | ✅ | | | P1 | Device |
| Torch front / back / both | ✅ | | | P1 | Device |
| Screen-light fallback | ✅ | | | P2 | Device |
| Capability-checked flash (no fake state) | ✅ | | | P1 | Static |
| Feed follows project aspect | 🔧 | was ⚠️ | | P3 | Build |
| Simultaneous front+back capture | | | ❌ | P3 | Not attempted — needs `CameraCharacteristics` concurrent-camera query; most devices refuse |
| Zoom (in fullscreen recorder) | ✅ | | | P2 | Device |
| Graceful busy/disconnect handling | ✅ | | | P1 | Static |

## Audio

| Feature | Exists | Broken | Missing | Priority | Tested |
|---|---|---|---|---|---|
| Mic capture | ✅ | | | P0 | Device |
| Per-clip cursor on sample clock | ✅ | | | P0 | **JVM (32 tests)** |
| Resampler (48k → 44.1k) | ✅ | | | P0 | JVM |
| Limiter | ✅ | | | P1 | JVM |
| Mixer panel (mute/solo/level) | ✅ | | | P1 | Static |
| Mic gain | ✅ | | | P2 | Static |
| Monitoring / headphone routing | | | ❌ | P3 | — |
| A/V sync in output | ⚠️ | | | P0 | **Device — unverified** |

## Recording & export

| Feature | Exists | Broken | Missing | Priority | Tested |
|---|---|---|---|---|---|
| Composite live recording | ✅ | | | P0 | Device |
| Offline export | ✅ | | | P0 | Device |
| Codec picker (H.264/HEVC/VP8/VP9) | ✅ | | | P1 | Static |
| Quality / resolution / fps | ✅ | | | P1 | Static |
| Bitrate tuning (long GOP, VBR) | ✅ | | | P1 | Static |
| Post-export playability probe | ✅ | | | P1 | Static |
| Progress + cancel | ✅ | | | P1 | Static |
| Save to MediaStore + share | ✅ | | | P1 | Device |
| Screen-record pre-flight | 🔧 | was ❌ | | P2 | Build |
| Recording state machine (explicit) | ⚠️ | | | P2 | Booleans, not a state enum |
| Pause/resume a recording | | | ❌ | P3 | — |

## Projects & storage

| Feature | Exists | Broken | Missing | Priority | Tested |
|---|---|---|---|---|---|
| Create / open / rename / duplicate / delete | ✅ | | | P0 | Static |
| Autosave + snapshot recovery | ✅ | | | P0 | Static |
| Save failure surfaced to user | 🔧 | was ❌ | | P2 | Build |
| Home thumbnails | 🔧 | was ❌ | | P1 | Build |
| Home empty state | 🔧 | was ❌ | | P2 | Build |
| Recycling list + thumb cache | 🔧 | was ❌ | | P1 | Build |
| Orphan media reclamation (undo-safe) | 🔧 | was ❌ | | P2 | Build |
| Media filename collisions keep extension | 🔧 | was ❌ | | P2 | Simulated |
| Schema migration path | | | ❌ | P2 | `SCHEMA` const exists but is never read/written |
| Project storage size display | | | ❌ | P3 | — |

## UX / accessibility

| Feature | Exists | Broken | Missing | Priority | Tested |
|---|---|---|---|---|---|
| Undo / redo (incl. aspect) | ✅ | | | P0 | Build |
| Undo snack on all destructive ops | 🔧 | was ❌ | | P1 | Build |
| Undo coalescing correctness | 🔧 | was ⚠️ | | P2 | Build |
| 48dp touch targets | 🔧 | was ❌ | | P2 | Build (canvas handles: see note) |
| Themed dialogs | 🔧 | was ⚠️ | | P2 | Build |
| Onboarding coach | ✅ | | | P2 | Static |
| Empty / loading / error states (editor) | ⚠️ | | | P2 | Partial |
| TalkBack on canvas | | | ❌ | P2 | Canvas chrome is invisible to a11y services |
| Toast → snackbar migration | ⚠️ | | | P3 | ~70 toasts remain |

**Canvas handle note:** raised 24→28dp, not 48dp. Nine handles on one box cannot each be 48dp without overlapping on any PiP under ~150dp. Documented in `UX_UI_AUDIT.md`.
