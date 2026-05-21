# Lumi Retouch Asset And License Audit

## Sample images

- `sample_portrait.jpg`: public domain Wikimedia Commons source listed in `README.md`.
- `sample_color_portrait.jpg`: Library of Congress source listed in `README.md`.
- `sample_color_face.jpg`: local crop derived from the Library of Congress source.

## Generated/local assets

- Launcher icon is project-local vector art.
- Studio backgrounds are generated procedurally in code.
- Template thumbnails are generated procedurally in Compose.
- Preset JSON values are project-authored.

## LUT assets

The bundled `.cube` LUT files are project-local generated look tables. Before public release, keep their provenance attached to the release notes if they are replaced by third-party LUTs.

## ML dependencies

- Google ML Kit Face Detection
- Google ML Kit Face Mesh beta
- Google ML Kit Selfie Segmentation beta
- Google ML Kit Pose Detection beta

Review Google ML Kit terms and Android dependency notices before open beta or production release.
