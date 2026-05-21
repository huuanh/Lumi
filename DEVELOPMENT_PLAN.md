# Lumi Retouch Development Plan

## Progress

- Phase 1: Completed and smoke-tested on LDPlayer.
- Phase 2: Completed and smoke-tested on LDPlayer.
- Phase 3: Completed and smoke-tested on LDPlayer.
- Phase 4: Completed for review and smoke-tested on LDPlayer. Studio segmentation, local heal, pose/body tune, commercial templates, and export flow are built.
- Phase 5: Completed for review and smoke-tested on LDPlayer. Engine module, preset packs, feature flags, benchmarks, and regression tests are built.
- Phase 6: Completed for review and smoke-tested on LDPlayer. Commercial retouch quality upgrade adds matte refinement, transparent cutout, relight, catchlight, under-eye lift, adjustable heal brush, and preset pack v2.
- Phase 7: Completed for review and smoke-tested on LDPlayer. Pro workflow adds local project autosave/restore, custom saved looks, batch preset export, brush controls, export quality, transparent PNG, and watermark toggle.
- Phase 8: Completed for review and smoke-tested on LDPlayer. Performance/device coverage adds device capability profile reporting, local benchmark status, queue status, and memory-aware release notes.
- Phase 9: Completed for review and smoke-tested on LDPlayer. QA/release hardening adds CI workflow, expanded contract tests, Android golden regression pass, privacy notes, and manual beta checklist.
- Phase 10: Completed for review and smoke-tested on LDPlayer. Productization adds local analytics hooks, preset catalog architecture notes, monetization gates behind flags, and store/beta readiness docs.

## Current Completion Snapshot

Done and verified:

- Native Android editor shell with import/demo, preview, before/after compare, split compare, zoom/pan, crop/rotate/flip, undo/redo, reset, export, share, and debug overlays.
- `:retouch-engine` module with public `RetouchEngine` contract, CPU pipeline, GPU preview path, LUT support, feature flags, preset packs, benchmark helper, and regression tests.
- ML Kit integration for face detection, face mesh, selfie segmentation, and pose-based body tune.
- Commercial prototype tools: skin smoothing, blemish soften, eye/teeth brightening, face/eye/nose/lip/body shape, makeup masks, studio backgrounds, local heal points, relight, matte refinement, transparent cutout, and preset pack v2.
- Pro workflow: project autosave/restore with URI/checksum/export history metadata, local custom presets, recipe sharing, copy-export-look, current-image preset batch, multi-image batch queue, cancel support, JPEG quality, watermark toggle, and local-only analytics counters.
- Brush workflow: adjustable size/strength, Heal/Clone/Restore/Erase modes, visible brush cursor/point overlay, undo-last-point, clear, and full-resolution replay.
- Performance and device coverage: low/mid/high device profile, GPU fallback gating, benchmark matrix fields, queue status, memory status, and documented device matrix.
- QA/release/productization: debug/release CI workflow, R8/resource shrink release build, ProGuard keep rules, privacy/release notes, asset/license audit, store materials, productization notes, LDPlayer smoke script, unit tests, and Android golden instrumentation tests.

## Phase 1: Image Quality

- Standardize `portrait-grade-v3` as explicit stages: base color, skin, warp, makeup, final tone.
- Add real LUT asset support with versioned preset packs: Clean, Film, Cream, Korean, Cool white, Warm portrait, Neo.
- Improve makeup masks: inner/outer lip, cheek contour, eyelid shadow, eyeliner, and adaptive strength.
- Improve skin retouch: local acne/blemish detection, edge-preserving smoothing, and texture recovery.
- Improve Auto Enhance rules for dark images, clipped skin, color cast, pale lips, small eyes, and turned faces.

## Phase 2: Realtime GPU

- Move preview LUT, tone, vignette, sharpen, and warp to GPU.
- Add GPU makeup compositing from face-mask uniforms or mask textures.
- Cache face-region layers so beauty/makeup slider changes avoid full-image CPU work.
- Keep CPU fallback for weak or incompatible devices.
- Add internal render-time/FPS diagnostics for preview tuning.

Completed scope:

- GPU preview now handles tone/filter, first-face warp, approximate skin retouch, makeup compositing, sharpen, and vignette.
- Preview skips CPU skin/makeup/vignette when the GPU path is available for the detected mesh.
- Preview render status shows the GPU stages and elapsed milliseconds for internal tuning.

## Phase 3: Commercial Editing UX

- Add preset carousel with thumbnail previews.
- Polish before/after: hold-to-compare, split handle, zoom, pan, and fit modes.
- Add named edit history: Auto, Skin, Lip, Filter, Warp.
- Improve export flow: PNG/JPEG, quality, share, metadata, and optional watermark.
- Add crop/rotate interaction with grid, pinch zoom, and drag crop.

Completed scope:

- Presets now use a horizontal carousel with tone/makeup thumbnail previews.
- Preview supports pinch zoom and pan while preserving hold-to-compare and split compare.
- Transform view shows a rule-of-thirds crop grid fitted to the current bitmap.
- Export flow supports PNG/JPEG selection, correct share MIME type, recipe metadata, and latest export display.
- Edit changes are stored as a compact recent-history list for inspection in the Export panel.

## Phase 4: Premium Retouch Features

- Add background segmentation and studio background replacement.
- Improve skin/lip/teeth/hair segmentation with stronger ML models where available.
- Add local heal/inpaint brush for acne and small objects.
- Improve body reshape with pose landmarks.
- Add social/profile/product portrait templates.

Completed scope:

- Added `StudioBackdrop` presets and `studioStrength` to recipes/export metadata.
- Replaced the old demo oval cutout with ML Kit Selfie Segmentation, face-aware heuristic fallback, and generated studio gradients.
- Added studio background controls under Templates: On/Off, Soft gray, Warm cream, Cool white, Peach, and Slate.
- Upgraded ML Kit Face Mesh to `16.0.0-beta3` so it can run beside `segmentation-selfie:16.0.0-beta6`.
- Smoke-tested preview and PNG export on LDPlayer with studio background enabled.
- Added bilinear mask sampling plus one-pass matte refinement to reduce jagged subject edges and hard halos.
- Added local heal brush points that work in preview, undo/redo, metadata, and full-resolution export.
- Added commercial profile/product/studio templates for headshot, commerce, and editorial portrait workflows.
- Added ML Kit Pose Detection and pose-aware body tune warp for torso/waist edits when body landmarks are available.
- Added preview overlays for heal brush targets and pose/body landmarks to make retouch controls inspectable during editing.
- Added Beauty quick chips for face/body shaping so pose-based body tune can be reached without digging through the full slider list.
- Added a Shape studio template that combines profile tone, studio background replacement, face shaping, contour, and subtle body tune for commercial portrait testing.
- Final integrated LDPlayer pass: sample image, Beauty quick body shaping, Shape studio template, ML segmentation preview, PNG export, and share enablement all completed without fatal runtime errors.

## Phase 5: Product Architecture

- Split processing into a `:retouch-engine` module.
- Add golden image tests for color and retouch regressions.
- Add CPU/GPU benchmark suite.
- Store presets as versioned JSON with import/export.
- Prepare feature flags, preset packs, and remote configuration.

Completed scope:

- Added `:retouch-engine` Android library module and moved engine-owned models, LUT, CPU processor, GPU preview renderer, pipeline stages, and mask utilities into it.
- Added `RetouchEngine` as the public engine contract used by the app preview/export path, with GPU fallback reporting preserved.
- Added debug unit tests for preset id uniqueness, share metadata contract, and transform-free prepared recipes.
- Updated pipeline metadata to export readable stage labels instead of raw enum names.
- Built `:retouch-engine:testDebugUnitTest assembleDebug`, installed the new APK on LDPlayer, and smoke-tested sample image loading after the module split.
- Added `portrait-pack-v1` as a versioned JSON preset pack under engine assets, plus `PresetPackLoader` with validation and builtin fallback.
- Moved the Templates UI to consume presets from `EditorUiState`, so future local/remote preset packs can replace the builtin pack without changing Compose UI code.
- Added parser tests to verify premium fields such as studio cutout, body tune, filter look, backdrop, and lip color survive JSON loading.
- Installed the preset-pack build on LDPlayer and smoke-tested the Templates carousel after loading a sample image.
- Added `local-config-v1` feature config under engine assets for GPU preview, studio segmentation, pose body tune, local heal, benchmark panel, and future remote preset config.
- Wired feature flags into ViewModel behavior so GPU preview, segmentation, pose detection, heal brush, and benchmark controls are controlled from config.
- Added `RetouchBenchmark` with CPU/GPU preview timing and deterministic bitmap checksums, exposed through a Benchmark action in the Export panel.
- Added Android instrumentation golden tests for LUT output colors, bitmap checksum stability, and nonblank CPU preview pipeline output.
- Verified `:retouch-engine:testDebugUnitTest assembleDebug`, direct LDPlayer instrumentation `OK (3 tests)`, and LDPlayer UI benchmark smoke test (`Bench CPU 868ms / GPU 143ms`) without fatal runtime errors.

## Phase 6: Commercial Retouch Quality

Goal: make outputs look closer to commercial portrait apps instead of a strong prototype.

- Replace simple skin heuristics with richer region masks: face skin, neck skin, hair boundary, sclera, iris, lips, teeth, eyebrows, eyelids, jawline, cheeks, T-zone, and background edge bands.
- Upgrade background cutout matting: hair-aware edge refinement, color decontamination near subject edges, transparent/background export option, and manual edge restore/erase brush.
- Improve makeup realism: lip tint following natural highlights, eyelid shadow constrained by eye geometry, eyeliner thickness control, blush under skin luminance, and contour that respects face lighting.
- Add portrait relight: catchlight, under-eye lift, cheek highlight, nose bridge, jaw shadow, and background/subject light matching.
- Add local retouch tools beyond current heal points: adjustable brush radius/strength, tap-to-remove blemish preview, clone/heal modes, undoable strokes, and optional before/after loupe.
- Add face/body guardrails: strength ceilings per face size, asymmetry detection, pose confidence thresholds, and artifact avoidance near hands/hair/accessories.
- Build curated preset pack v2: Korean clean, ID headshot, ecommerce white, night selfie rescue, creamy indoor, film portrait, livestream face, and natural no-makeup.

Acceptance checklist:

- Makeup no longer looks like opaque layers on normal selfie lighting.
- Studio background has fewer hard halos around hair/shoulders.
- Auto preset produces a usable result on at least dark, warm indoor, cool outdoor, and low-contrast portraits.
- Full-res export matches preview closely for tone, makeup, background, and retouch.

Completed scope:

- Added recipe controls for matte refinement, transparent background export, portrait relight, catchlight, under-eye lift, brush radius, and brush strength.
- Upgraded studio cutout compositing with stronger matte contrast, edge color decontamination, and transparent PNG alpha output.
- Added CPU portrait relight pass for cheek/nose lift, jaw shadow, under-eye lift, and catchlight rendering on face mesh or landmark fallback.
- Added `portrait-pack-v2` presets including ID headshot and night selfie rescue looks.
- Added tests for new premium fields and recipe JSON round-trip.

Closure scope:

- Added heuristic semantic region coverage for skin, lips, eye area, eye line, cheeks, contour, nose, T-zone, subject, and studio edge bands.
- Added transparent/background export, matte refinement, color decontamination, relight, catchlight, under-eye lift, and manual Restore/Erase brush modes for retouch/cutout cleanup workflows.
- Added preset pack v2 coverage for clean, headshot, commerce, studio, shape, and night-rescue cases.

## Phase 7: Pro Editing Workflow

Goal: turn the editor from a single-session prototype into a usable editing product.

- Add project/session model: keep original URI, recipe version, preview cache, export history, timestamp, and thumbnail.
- Add recipe import/export: JSON share, copy preset from an edited image, and save custom user presets.
- Add batch apply: choose multiple images, apply one preset, export queue with progress and retry.
- Add brush workflow: brush size/strength controls, visible cursor, eraser mode, stroke list, and per-tool undo.
- Add advanced crop/export: free crop, straightening, social ratios, watermark toggle, quality slider, export destination, and transparent PNG for cutouts.
- Add share templates: profile avatar crop, product card, before/after collage, and story format.
- Add better empty/error states for permissions, no face found, low memory, unsupported image, and ML model failure.

Acceptance checklist:

- User can save a custom look and reuse it after app restart.
- User can edit one image, close app, reopen, and continue the project.
- Export queue handles at least 5 images without blocking the UI.

Completed scope:

- Added local project autosave/restore through SharedPreferences and `RecipeCodec`.
- Added custom saved look storage and Template carousel injection.
- Added batch export action for the first five active presets with queue progress.
- Added adjustable JPEG quality, transparent PNG toggle, watermark toggle, and brush size/strength controls.

Closure scope:

- Project session now records original URI string, image name, recipe JSON, preview checksum, export history, and timestamp.
- Export panel exposes save look, restore project, share recipe JSON, copy last exported look, pick multi-image batch, run queue, and cancel queue.
- Batch export supports selected multiple images with saved/failed counts plus current-image preset batch when no batch selection is active.
- Brush controls include modes, size, strength, visible cursor, point list, clear, and undo-last-point.

## Phase 8: Performance And Device Coverage

Goal: make preview and export reliable across real Android devices, not only LDPlayer.

- Add device capability profiles: GPU enabled/disabled, max preview side, export side, ML feature availability, and fallback mode.
- Add benchmark matrix: CPU preview, GPU preview, full-res export, segmentation, face mesh, pose, memory peak, and app startup time.
- Add cache strategy: transformed bitmap cache, mask cache, GPU texture reuse, face-region invalidation, and preset thumbnail cache.
- Move more preview work to GPU: segmentation composite, selected makeup masks, final tone, and multi-face path where practical.
- Add cancellation and backpressure rules for rapid slider dragging and export while preview is rendering.
- Add memory safety: bitmap pool, max megapixel guard, OOM recovery, downsample strategy, and explicit recycle boundaries where useful.
- Smoke test device tiers: emulator, OPPO/CPH, ZTE/Nubia Z2461, one low-memory device, one Android 15 device.

Acceptance checklist:

- Common slider changes feel responsive on physical devices.
- App survives large imported photos without OOM.
- Benchmark panel shows stable values and no fatal crash across target devices.

Completed scope:

- Added low/mid/high `DeviceCapabilityProfile` reporting based on memory class and CPU count.
- Preserved preview debounce/cancellation and benchmark panel while surfacing profile/status in Export.
- Documented target device QA and memory/performance release checks.

Closure scope:

- Device profile gates GPU preview on low-tier devices and reports max preview/export targets.
- Benchmark reports CPU preview, GPU preview, transform, full export, mesh count, pose availability, segmentation availability, checksum, and memory usage.
- Device test targets and pass criteria are documented in `DEVICE_TEST_MATRIX.md`.
- Existing preview transform cache, debounce, cancellation, and queue cancellation remain wired for responsive interaction.

## Phase 9: QA, Privacy, And Release Hardening

Goal: make the app safe enough for closed beta distribution.

- Add automated test layers: engine unit tests, Android golden tests, ViewModel tests, export metadata tests, and basic Compose UI smoke tests.
- Add CI workflow for build, unit tests, lint, and Android instrumentation on available runner/emulator.
- Add crash/report hooks behind feature flags with privacy-safe events only.
- Add privacy and permission review: gallery access, media save, share intent, no hidden upload, and clear local-only processing statement.
- Add release build config: signing placeholders, ProGuard/R8 rules, versioning, changelog, and split APK/AAB options.
- Add asset/license audit: sample images, LUT assets, generated presets, model dependencies, and attribution.
- Add manual QA checklist for every release: import, auto, beauty, makeup, template, segmentation, export, share, rotate/crop, background app resume.

Acceptance checklist:

- Release APK/AAB builds successfully.
- Manual QA checklist passes on at least two physical devices.
- Privacy notes and dependency/license notes are documented.

Completed scope:

- Added GitHub Actions debug build/unit test workflow.
- Added `PRIVACY_AND_RELEASE.md` with local-only processing notes and release checklist.
- Expanded engine contract tests for Phase 6-10 fields.
- Verified engine unit tests, debug build, LDPlayer smoke, and Android golden instrumentation tests.

Closure scope:

- Added debug and release CI workflow entries.
- Enabled R8/resource shrinking for release and verified `assembleRelease`.
- Added ProGuard/R8 keep rules for ML Kit, Play Services tasks, and coroutine metadata.
- Added `PRIVACY_AND_RELEASE.md`, `ASSET_LICENSE_AUDIT.md`, and `DEVICE_TEST_MATRIX.md`.
- Added local privacy-safe analytics counters and release QA checklist for closed beta validation.

## Phase 10: Productization And Growth

Goal: prepare the app as a real product, not only an editor implementation.

- Add onboarding focused on immediate editing: import/demo first, one-tap Auto, preset recommendations, and permission explanation.
- Add preset catalog architecture: local packs, downloadable packs, A/B config hooks, and featured categories.
- Add monetization gates only after core UX is stable: pro presets, batch export, transparent cutout, high-res export, and watermark removal.
- Add account-free local mode first, then optional sync only if needed.
- Add analytics events for product decisions: import success, preset apply, export success, tool usage, benchmark profile, crash-free sessions.
- Add app store preparation: screenshots, short description, privacy policy, changelog, beta track notes, and support channel.
- Add growth loops: share card, before/after export, preset share/import, and creator-style packs.

Acceptance checklist:

- New user can import or use demo and export an improved photo in under one minute.
- Feature flags can enable/disable pro experiments without code changes.
- App has a clear beta release checklist and store-facing materials.

Completed scope:

- Added feature flags for project sessions, batch export, transparent cutout, local analytics events, and monetization gates.
- Added local-only analytics counters for key product actions.
- Added `PRODUCTIZATION.md` with preset catalog, monetization gate, event, and store readiness notes.
- Kept onboarding immediate by preserving first-screen import/demo actions.

Closure scope:

- First-run path remains immediate: Import, Sample, Auto, Templates, and Export are visible without a landing page.
- Preset catalog uses local versioned packs with config-selected active pack and custom user looks.
- Monetization gates remain controlled by feature flags for future pro presets, batch export, transparent cutout, high-res export, and watermark removal.
- Added `STORE_MATERIALS.md` for screenshots, short description, beta notes, support channel, and privacy copy.
- Growth hooks include share image, share recipe JSON, copy exported look, custom saved looks, and batch exports.

## Working Rule

Each phase must be implemented, built, smoke-tested on LDPlayer when available, and then handed to the user for review before moving to the next phase.
