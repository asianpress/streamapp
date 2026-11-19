# Phase 2 — watermark compositing and live streaming

## Cross-device design
Branding layout uses normalized 0..1 coordinates, never fixed screen pixels.
The same WatermarkSpec is consumed by the preview renderer, recording renderer,
and streaming renderer. Camera discovery and encoder selection are capability-based,
not manufacturer-name-based.

## Rendering rule
A view drawn over PreviewView is only a preview overlay. It does not automatically
become part of a CameraX VideoCapture file. The production implementation must
composite the camera frames and watermark into the encoder input before encoding,
so the exact same pixels go to both the MP4 recorder and RTMP stream.

## Planned pipeline
Camera source (built-in CameraX / supported USB UVC)
  -> frame source adapter
  -> OpenGL overlay compositor (logo + business name + phone + scrolling ticker)
  -> hardware video encoder
  -> fan-out to MP4 muxer and RTMP publisher

Audio:
Microphone -> AAC encoder -> MP4 muxer and RTMP publisher.

RootEncoder is included as the RTMP/encoder foundation. Its optional extra-sources
artifact advertises CameraXSource and CameraUvcSource support. Actual source wiring,
stream lifecycle, reconnect handling, and UI remain to be implemented and tested.

Media3 Transformer is included for a post-recording watermark/export workflow and
media composition. This is a fallback/export path, not a substitute for live
frame compositing.

## Security
- Stream keys are secrets; never print them in logs or show them in plain text.
- Store production credentials encrypted with Android Keystore.
- Validate RTMP/RTMPS destination URLs.
- Do not ship any developer's personal stream key in the APK.

## Device compatibility
- Query camera IDs, lens facing, supported sizes, and encoder capabilities at runtime.
- Prefer H.264/AAC for broad RTMP compatibility; select only profiles supported by
  the current device.
- Start with conservative 720p settings and allow a lower fallback when encoding
  or upload bandwidth is insufficient.
- USB UVC availability depends on Android host support, device drivers, and power.
  Detect it at runtime; do not promise every USB camera works on every phone.

## Current project status
- CameraX preview and local MP4 recording: starter implementation included.
- Multi-studio local profiles and logo selection: starter implementation included.
- Preview-only watermark: included.
- Shared normalized watermark model: added in Phase 2.
- RootEncoder and Media3 dependencies: added in Phase 2.
- Burned-in watermark in saved MP4: not yet wired.
- RTMP streaming UI and publisher: not yet wired.
- External UVC camera switching: not yet wired.

## Verification checklist before calling this a release
1. Build and install on multiple Android API levels and OEMs.
2. Record 30-second portrait and landscape samples.
3. Verify logo/text/ticker are burned into the MP4, not just visible in preview.
4. Verify stream and recording receive the same composited frames.
5. Test network loss, RTMP reconnect, app backgrounding, and audio permission denial.
6. Test UVC camera enumeration, detach, reattach, and power constraints.


## Phase 3 implementation note
The starter's CameraX recording output is now passed through Media3 Transformer after recording completes. A custom CanvasOverlay burns the active studio logo, business name, phone number, scrolling ticker and date/time into every exported video frame. The final file is published to Movies/AsianLiveStudio. RTMP remains a Phase 4 task because live streaming needs the same overlay pipeline wired into the encoder input rather than a post-recording export.
