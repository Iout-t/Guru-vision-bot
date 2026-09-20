# GuruVision Bot V2

Android screen-vision prototype for GuruTrade7 EUR/USD (OTC).

## Included

- MediaProjection screen capture
- ML Kit price OCR
- Chart-region computer vision
- Candle-like structure detection
- Candle pattern classifier
- User calibration model for symbol/price/chart regions
- Adaptive layout heuristic
- 5-second signal engine
- CALL / PUT / RANGE / WAIT
- Room signal-history database
- Floating signal overlay
- GuruTrade7 launch/focus helper
- GitHub Actions APK build

## Build on GitHub

Push the repository to GitHub, then:
Actions -> Build GuruVision APK -> Run workflow.

## Android permissions

The app requires screen-capture approval. The overlay feature requires the
Android "draw over other apps" permission. Notification permission is needed
on Android 13+ for notifications.

## Important

The current candle detector is a lightweight screen-CV prototype and should
be calibrated/tuned to the exact GuruTrade7 chart theme and device resolution.

The interaction helper can open/focus GuruTrade7, but this project does not
automatically place or submit financial trades.

## Signal interpretation

Confidence is an internal analysis score, not a guaranteed probability of
the next 5-second price movement. Screen capture/OCR latency is explicitly
part of the system's uncertainty.
