# Spatial Motion

A simple Android overlay app with a **floating bubble** that lets you control TikTok, Instagram, Facebook, and YouTube using hand gestures — no built-in video player.

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
| Closed hand swipe top → bottom | Next video |
| Closed hand swipe bottom → top | Previous video |
| Hold open palm in center | Play / Pause (center tap) |

## Bubble controls

- **Drag** the bubble to move it
- **Tap** the bubble to open settings (camera preview, stop)
- Green border = hand detected

## Build

```bash
./gradlew assembleDebug
```

Install on a physical device with a front camera. The bubble runs as a foreground service while active.

## Permissions explained

- **Overlay** — required for the floating bubble over other apps
- **Accessibility** — required to inject scroll and tap gestures into TikTok, IG, FB, YT
- **Camera** — required for hand tracking only; no video is recorded or uploaded
