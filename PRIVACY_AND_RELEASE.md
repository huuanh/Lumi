# Lumi Retouch Privacy And Release Notes

## Privacy posture

- Processing is local-only. Imported photos are decoded on device and are not uploaded by this app.
- ML Kit face, mesh, selfie segmentation, and pose detectors run through on-device APIs used by the Android app.
- Analytics events are local debug counters only. They are kept in memory for product tuning during the current session and are not transmitted.
- Saved exports go to `Pictures/LumiRetouch` through Android MediaStore.
- Sharing uses the Android share sheet and only shares the latest exported image when the user taps Share.

## Manual QA checklist

- Import an image from the picker.
- Load the demo image without permissions.
- Apply Auto, Beauty, Makeup, Templates, and Export controls.
- Verify hold-to-compare, split compare, zoom/pan, crop grid, debug overlay, and heal taps.
- Verify studio background with Soft gray, Warm cream, Cool white, Peach, and Slate.
- Verify transparent PNG with studio cutout enabled.
- Save PNG and JPEG, then share latest export.
- Save a custom look, restart the app, and restore the saved project.
- Run Benchmark and confirm CPU/GPU status appears.
- Run batch export with at least five presets.
- Background and resume the app during preview rendering.
- Repeat on LDPlayer plus at least two physical Android devices before beta.

## Release hardening

- Release signing placeholders are intentionally not checked in.
- R8 is still disabled for the prototype release path; enable and verify ML Kit/GPU behavior before Play beta.
- Keep sample asset attribution in `README.md`.
- Review `DEVELOPMENT_PLAN.md` before each release and only mark a phase complete after build, tests, and LDPlayer smoke pass.
