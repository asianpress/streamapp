# Asian Live Studio — Phase 5

Phase 5 adds the first interactive watermark editor on top of the Phase 4 real-time RootEncoder pipeline.

## Added
- Drag watermark items directly on the camera preview.
- Add custom text layers.
- Add a LIVE badge.
- Choose a custom PNG/JPG logo from the device.
- Resize the selected layer larger/smaller.
- Delete the selected layer.
- Save a template marker per studio profile.
- Restore the selected studio logo.
- Version 0.5.0 / versionCode 5.

## Important
The editor manipulates the same `watermarkLayer` that Phase 4 sends through `ViewFilterRender`, so the edited layout is intended to appear in the encoded live stream and RootEncoder recordings. Device testing is still required for exact scaling/cropping on different aspect ratios.

## Next
- Persist full layer geometry/style as JSON templates.
- Font, color, opacity and background controls.
- Multi-layer selection/z-order.
- Encrypted stream destination profiles with Android Keystore.
- Foreground service/background streaming.
- Adaptive bitrate and diagnostics.
- YouTube/Facebook destination presets.
- USB/UVC camera support.
