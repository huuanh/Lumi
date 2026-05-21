# Lumi Retouch Productization

## Beta offer

Lumi Retouch opens directly into editing: import, demo, one-tap Auto, quick beauty, templates, and export. The first useful action is always available from the first screen.

## Preset catalog

- Built-in packs live under `retouch-engine/src/main/assets/presets`.
- `FeatureConfig.presetPackPath` selects the active local pack.
- `remotePresetConfig` is reserved for a future signed config or CMS fetch.
- Custom user looks are saved locally through the project session store.

## Monetization gates

`monetizationGates` is false for beta. Future gates should only wrap pro presets, high-res batch export, transparent cutout, watermark removal, or marketplace packs after core editing quality is stable.

## Local analytics events

Current events are local-only session counters for:

- app start and device tier
- demo/import
- recipe changes
- heal points
- custom preset save
- project restore
- export and batch export

No network analytics SDK is included.

## Store readiness

Before public beta, prepare screenshots for import, auto, beauty, studio cutout, transparent PNG, batch export, and share. Keep privacy messaging aligned with `PRIVACY_AND_RELEASE.md`.
