# Asian Live Studio 1.0.0 — Final Feature Set

This is the final V1 source package rather than another incremental phase.

## Camera and video
- Camera preview
- Front/rear switching
- 480p / 720p / 1080p profiles
- 30 FPS target
- Automatic orientation handling through RootEncoder

## Broadcast
- RTMP and RTMPS destination URL
- Stream-key input
- Connection/auth status
- Upload bitrate display
- Reconnect configuration through RootEncoder

## Recording
- MP4 recording to the app's Movies/AsianLiveStudio directory
- Recording can be started without going live or while live
- Watermark composition uses the same encoded video pipeline

## Branding
- Multiple studio profiles
- Business name
- Phone
- Scrolling ticker
- Per-profile logo
- Custom text
- Image overlays
- LIVE badge
- Date/time layer

## Editor
- Drag layers
- Scale layers
- Duplicate
- Delete
- Bring forward / send backward
- Text size
- Font family choice
- Text color
- Background style
- Opacity
- Percentage X/Y positioning

## Templates
- Save
- Load
- Delete
- JSON export
- JSON import
- Custom image URI persistence

## Known release boundary

Background/foreground-service streaming and USB/UVC camera selection are not included in this final 1.0.0 source package. They require a separate architecture path and device-specific testing rather than being represented as finished features without verification.
