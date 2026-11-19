# Phase 4 — Real-time RTMP pipeline

This phase replaces the Phase 3 CameraX-only recording path with RootEncoder's real-time encoder pipeline.

## Architecture

Camera source → RootEncoder GL pipeline → ViewFilterRender watermark → H.264/AAC → RTMP/RTMPS
                                            ↘ MP4 recording

The watermark is therefore part of the encoded output rather than an Android UI element merely sitting above CameraX PreviewView.

## Security

The stream key is not stored in SharedPreferences. Only the non-secret server URL is remembered. The key is held in memory for the current session. A production release should move destination profiles to Android Keystore-backed encrypted storage.
