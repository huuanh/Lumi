# Lumi Retouch Device Test Matrix

## Required tiers

| Tier | Device target | Status to record |
| --- | --- | --- |
| Emulator | LDPlayer / Android emulator | Install, demo, face scan, export, benchmark |
| OPPO/CPH | Mid-tier physical Android | Import, Auto, Beauty, Templates, Export |
| ZTE/Nubia Z2461 | Target physical Android | GPU preview, pose/body tune, batch export |
| Low memory | <= 4 CPU cores or low memory class | CPU fallback, large import guard, export survival |
| Android 15 | Recent OS physical or emulator | permissions, MediaStore export, share sheet |

## Benchmark fields

Record CPU preview, GPU preview, transform, full export, mesh count, segmentation availability, pose availability, and memory usage from the Export panel benchmark line.

## Pass criteria

- App stays foreground and responsive after demo load.
- Face scan finishes or displays a nonfatal no-face state.
- Export writes PNG and JPEG to MediaStore.
- Batch queue finishes or can be cancelled.
- No fatal crash during background/resume.
