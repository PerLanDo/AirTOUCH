# Spatial Motion

A simple Android overlay app with a **floating bubble** that lets you control TikTok, Instagram, Facebook, and YouTube using **ML hand tracking** — no built-in video player.

Hand detection uses Google's **MediaPipe Hand Landmarker** (21 skeletal landmarks per hand) to recognize palm shape, finger extension, and motion — not color-based skin detection.

## How it works

1. Open Spatial Motion and grant three permissions:
   - **Camera** — tracks your hand
   - **Display over other apps** — shows the floating bubble
   - **Accessibility** — sends scroll/tap to the app you are using
2. Tap **Start floating bubble**
3. Open TikTok, Instagram, Facebook, or YouTube
4. Use hand gestures in front of the front camera

## Gestures

| Hand gesture | Action in other apps |
|--------------|----------------------|
| Closed fist swipe top → bottom | Next video |
| Closed fist swipe bottom → top | Previous video |
| Hold open palm (fingers extended) in center | Play / Pause (center tap) |

## Bubble controls

- **Drag** the bubble or portrait camera preview to move them
- **Tap** the bubble to open settings (stop, gesture help)
- **Tap − / +** on the camera preview to minimize or maximize it
- Green border on bubble = hand detected
- **Skeleton overlay** on the camera preview shows live hand bones (green) and joints (blue), mirrored to match the front camera

## Build

### Download (no local build)

A debug APK is built on GitHub Actions when `main` changes and committed to the repo:

**[`.build-outputs/app-debug.apk`](.build-outputs/app-debug.apk)** — download from GitHub (open the file on `main` → **Download**).

### Build locally (optional)

```bash
test -f debug.keystore || base64 -d debug.keystore.base64 > debug.keystore
./gradlew assembleDebug
```

The installable APK is copied to **`.build-outputs/app-debug.apk`**.

Install on a physical device with a front camera. The bubble runs as a foreground service while active.

## Permissions explained

- **Overlay** — required for the floating bubble over other apps
- **Accessibility** — required to inject scroll and tap gestures into TikTok, IG, FB, YT
- **Camera** — required for hand tracking only; no video is recorded or uploaded
