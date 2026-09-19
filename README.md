# GuruVision Bot — Android screen-vision prototype

This project captures the Android screen with MediaProjection, runs local
OCR with ML Kit, extracts a EUR/USD-like displayed price, and feeds the
price into a rolling 5-second CALL/PUT/RANGE/WAIT engine.

## Build

Open this folder in Android Studio and let Gradle sync. Then run the app on
the Android phone.

The app targets Android 35 and declares the mediaProjection foreground
service type. Android requires the user to approve screen capture before
the capture service can start.

## Run

1. Launch GuruVision Bot.
2. Tap START SCREEN VISION.
3. Accept Android's screen-capture permission.
4. Switch to GuruTrade7.
5. Keep EUR/USD (OTC) visible.
6. The persistent notification shows the latest detected signal.

## Current prototype limitation

The first prototype OCRs the full screen. This is intentionally simple for
the first calibration pass. For reliable GuruTrade7 use, the next pass should
crop the exact price region and chart region from the user's device screenshot,
then add a calibration UI so the rectangles can be adjusted for different
screen resolutions.

This app does not place trades.

## Dependencies

ML Kit bundled Latin text recognition is used so the OCR model is packaged
with the app rather than requiring a first-run model download.
