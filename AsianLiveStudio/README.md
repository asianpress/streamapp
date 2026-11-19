# Asian Live Studio — Final 1.0.0

Android live camera, recording and RTMP/RTMPS studio app for custom business/studio branding.

## Included

- Rear/front camera preview and camera flip
- Real-time watermark composition into the RootEncoder GL pipeline
- Local MP4 recording
- RTMP / RTMPS live streaming
- 480p / 720p / 1080p quality profiles
- 30 FPS capture profile
- Automatic reconnect settings supplied by RootEncoder
- Upload bitrate display
- Studio profiles (business name, phone, ticker)
- Persistent logo per studio profile
- Watermark editor with drag positioning
- Custom text layers
- PNG/JPG/WebP image overlay layers
- LIVE badge layers
- Live date/time layer
- Resize/scale, duplicate, delete and layer ordering
- Text size, font, text color and background controls
- Layer opacity and exact percentage positioning
- Save/load/delete templates
- JSON template export/import
- Offline local template/profile storage
- Asian Press defaults prefilled for the first profile

## Important implementation note

This package is a source project for Android Studio. It has not been claimed as a tested APK because the build environment used to prepare this package does not contain an Android SDK/Gradle installation or a physical Android test device.

The streaming layer is based on RootEncoder 2.8.1. RootEncoder's 2.8.1 release renamed `AndroidViewFilterRender` to `ViewFilterRender`; this final source uses the renamed API and the current 2.8.1 dependency. The official RootEncoder documentation also documents RTMP/RTMPS, MP4 recording, camera switching and real-time filters.

## Build

1. Open this `AsianLiveStudio` folder in Android Studio.
2. Let Gradle sync and download dependencies.
3. Connect an Android 8.0+ device or start an emulator with a camera/microphone.
4. Run the `app` configuration.
5. Grant Camera and Microphone permissions.
6. Test local recording first.
7. For live streaming, enter the RTMP/RTMPS ingest URL and stream key supplied by your platform.

## Release configuration

- applicationId: `np.asianpress.asianlivestudio`
- versionName: `1.0.0`
- versionCode: `10`
- minSdk: 26
- targetSdk: 35
- compileSdk: 35

## Security

Stream keys are never written to the template JSON files. Treat RTMP stream keys as passwords. For a public production release, add Android Keystore-backed encrypted destination storage before distributing the APK widely.

## Final testing checklist

- Test front/rear camera switching on the actual target phones.
- Test 480p, 720p and 1080p encoder support; some devices may reject 1080p.
- Test the watermark on the exact social platform/server being used.
- Test RTMPS certificates and server URLs.
- Test long recordings and available device storage.
- Test imported image URI permissions after device restart.
- Test thermal behavior during long live sessions.
