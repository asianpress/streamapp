# Asian Live Studio — Phase 6

Phase 6 upgrades the Phase 5 watermark editor into a reusable template system.

## Added
- Save complete watermark canvas state as JSON in local app storage.
- Load saved watermark templates.
- Delete saved templates.
- Add custom text layers.
- Duplicate selected text layers.
- Bring selected layer forward / send backward.
- Resize selected layer.
- Basic text size and text color controls.
- Per-layer opacity control.
- Preserve position, scale, opacity, visibility, text, text size and text color in templates.
- Version bumped to `0.6.0` / versionCode `6`.

## Notes
- Templates are stored locally in SharedPreferences; no internet is required.
- The editor currently persists the built-in profile watermark geometry plus custom text/badge layers.
- Full image-layer serialization and import/export files are planned for a later phase.
- The source package is intended for Android Studio. An APK is not claimed unless it has been compiled and tested on a real Android device.
