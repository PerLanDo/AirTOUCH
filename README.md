# Spatial Motion

A simple Android native app for hands-free short video playback. Use hand gestures in front of the front camera to scroll between reels and play or pause.

## Features

- Full-screen vertical video reels with looping playback
- Front-camera hand gesture control (swipe up/down, palm to pause)
- Floating settings bubble for camera preview and guide
- Tap video to play or pause manually

## Requirements

- Android 7.0+ (API 24)
- Front camera
- Internet connection (videos stream from sample URLs)

## Build

1. Open the project in [Android Studio](https://developer.android.com/studio)
2. Sync Gradle and run on a physical device (camera gestures need a real front camera)

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Gesture Controls

| Gesture | Action |
|---------|--------|
| Swipe hand top → bottom | Next reel |
| Swipe hand bottom → top | Previous reel |
| Hold open palm in center | Play / Pause |

Open the floating settings bubble (bottom-right) to toggle the camera preview or reopen the gesture guide.
