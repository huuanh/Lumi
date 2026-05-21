# Lumi Retouch Android

Native Android prototype for a Xingtu-like photo editor, built as a clean-room app with Kotlin, Jetpack Compose, MVVM, StateFlow, and a replaceable bitmap processing engine.

## Implemented scope

- Phase 1 editor shell: image import, demo image, preview, before/after, undo/redo, reset, PNG export to gallery.
- Phase 1 transform: center crop ratios, rotate, horizontal flip.
- Phase 1 adjustments: exposure, contrast, saturation, warmth, sharpen, vignette, and filter looks.
- Phase 1 retouch prototype: skin smoothing, blemish softening, eye brightening, teeth whitening.
- Phase 2 panels: makeup controls, lip swatches, blush, contour, face/body reshape controls.
- Phase 2 templates: daily clean, portrait soft, street snap, commerce cutout.
- Processing engine: `Bitmap` + `Canvas` + `ColorMatrix`, split into the `:retouch-engine` Android library module behind the `RetouchEngine` app contract.
- Presets are loaded from a versioned JSON pack at `retouch-engine/src/main/assets/presets/portrait_presets.json`, with builtin fallback if loading fails.
- Feature flags are loaded from `retouch-engine/src/main/assets/config/feature_flags.json` and control GPU preview, studio segmentation, pose body tune, local heal, and benchmark availability.
- Skin retouch now blends a softened bitmap only over skin-tone pixels instead of drawing a fixed demo overlay.
- On-device ML Kit face detection maps eye highlight, teeth whitening, lip color, blush, and jaw guide to detected face landmarks.
- ML Kit Face Mesh beta adds dense 468-point mesh anchors for real local bitmap warping.
- Dedicated `Eye size`, `Nose slim`, and `Lip plump` controls now drive mesh-guided warp separately from color makeup controls.
- Face warp processing is clipped to each detected face region instead of scanning the full image.
- Preview rendering uses a downscaled source capped at 1080px for smoother slider interaction.
- Export rendering replays the same recipe on the high-quality source capped at 4096px, with face anchors scaled from preview space.
- Slider changes cancel stale preview jobs and debounce CPU rendering briefly to reduce churn.
- Mesh-aware makeup now edits pixels with feathered masks and soft-light style blending instead of drawing opaque ovals.
- Lip color preserves texture/luminance, blush is limited by skin-tone weighting, and contour uses subtle shading.
- Lip tint prefers a mesh polygon mask around the real lip contour, with ellipse fallback.
- Makeup strength adapts to luminance/chroma so it is less harsh on very dark, very bright, or already saturated pixels.
- Portrait-grade processing now runs as layered base color, skin retouch, face warp, makeup, and final tone stages.
- Skin retouch uses face/skin/T-zone masks plus local blemish weighting to soften red or dark spots without flattening all skin texture.
- Filter looks now use an internal 3D portrait LUT engine with Clean, Film, Cream, Korean, Cool white, Warm portrait, and Neo looks.
- Preview tone/filter, approximate skin retouch, first-face beauty warp, makeup, sharpen, and vignette are rendered through an OpenGL ES shader when available, with CPU fallback.
- GPU preview reports stage timing such as `GPU tone+skin+makeup+final 152ms` for performance tuning.
- Preview UX includes pinch zoom, pan, hold-to-compare, split compare, and a fitted crop grid in Transform mode.
- Presets are presented as a horizontal carousel with generated tone/makeup thumbnail previews.
- Export supports PNG/JPEG selection, correct Android share MIME type, saved recipe metadata, and a compact recent-edit history.
- Studio background replacement uses ML Kit Selfie Segmentation with a face-aware fallback mask, generated portrait backdrops, and adjustable replacement strength in Templates.
- Segmentation masks are refined with bilinear sampling, light smoothing, and edge feathering before compositing.
- Local heal brush points remove small blemishes/spots and are replayed in full-resolution export.
- Body tune uses ML Kit Pose Detection when shoulder/hip landmarks are available, with face-mesh fallback behavior preserved.
- Beauty preview overlays show heal brush targets and pose/body landmarks while editing.
- Profile headshot, product portrait, studio slate, and shape studio templates cover more commercial portrait workflows.
- Beauty quick chips expose face/body shaping controls without forcing the user to scroll through every advanced slider.
- Auto Enhance uses image statistics plus face brightness, lip saturation, eye ratio, and yaw hints to choose beauty/makeup/filter settings.
- Export stores the recipe metadata in the saved image description and exposes the latest export for Android sharing.
- Export panel includes an engine benchmark action that reports CPU/GPU preview timings and deterministic checksums for performance tuning.
- Engine tests cover preset id uniqueness, readable pipeline metadata, transform-free prepared recipes, JSON config parsing, and Android golden regressions for LUT/checksum/CPU preview behavior.
- Debug overlay toggle shows face bounds, sampled mesh points, lip polygon, cheek regions, and nose region for mask tuning.

## Current limitations

- Face mesh detection is a beta ML Kit API and works best on selfie-like faces. If mesh is unavailable, the app falls back to coarse face landmarks and non-warp overlays.
- GPU preview currently targets the first detected mesh face; full multi-face GPU compositing remains future work.
- Studio background now uses ML Kit Selfie Segmentation with basic matting. Hair and translucent/low-contrast clothing edges still need stronger alpha matting for production-grade results.
- Body tune depends on pose visibility. Tight face crops or seated/occluded bodies may fall back to subtle face/neck shaping only.
- Inpainting and subscription/CMS are not included in this native scaffold.

## Skills used

- `android-jetpack-compose`: Compose UI structure, state hoisting, Material 3 patterns.
- `kotlin-coroutines-flows`: ViewModel `StateFlow`, structured coroutine usage, dispatcher separation, cancellation.

## Build

```powershell
cd C:\dev\Clone\lumi-retouch-android
.\gradlew.bat assembleDebug
```

Engine contract tests:

```powershell
.\gradlew.bat :retouch-engine:testDebugUnitTest
```

Android golden regression tests on a connected emulator/device:

```powershell
.\gradlew.bat :retouch-engine:connectedDebugAndroidTest
```

Debug APK:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Sample Assets

`sample_portrait.jpg` is sourced from Wikimedia Commons:

- File: `Face detail, Acoma woman by Edward S. Curtis, 1905 (cropped).jpg`
- Author: Edward S. Curtis
- License/status: Public domain / Public Domain Mark
- Source: https://commons.wikimedia.org/wiki/File:Face_detail,_Acoma_woman_by_Edward_S._Curtis,_1905_(cropped).jpg

`sample_color_portrait.jpg` and the app-facing crop `sample_color_face.jpg` are sourced from the Library of Congress:

- Item: `[First Lady Betty Ford, half-length portrait, facing front]`
- Date: 1974
- Rights/status: No known restrictions on publication; official White House photograph
- Source: https://www.loc.gov/pictures/item/92510224/
- Download used: https://cdn.loc.gov/service/pnp/cph/3g00000/3g02000/3g02000/3g02019v.jpg
- Crop used for in-app sample: `sample_color_face.jpg`, derived locally from the same source image for makeup and face-mesh testing.
