# GuruVision Bot V2 Architecture

## Runtime

GuruTrade7 screen
 -> MediaProjection
 -> ScreenCaptureService
 -> FrameProcessor
 -> Calibration / LayoutDetector
 -> Price OCR + Chart CV
 -> CandleDetector
 -> PatternClassifier
 -> SignalEngine
 -> Confidence/WAIT filter
 -> Room SignalDatabase
 -> Floating SignalOverlay + notification

## Modules

- capture: MediaProjection and ImageReader
- calibration: user-selected regions for symbol, price and chart
- vision: chart-region CV, candle extraction, pattern classification, adaptive layout heuristic
- signal: existing 5-second direction/movement engine
- db: Room persistence of signal history
- overlay: TYPE_APPLICATION_OVERLAY floating signal
- interaction: safe launcher/focus helper for GuruTrade7

## Automatic interaction boundary

The interaction module only opens/focuses GuruTrade7. It does not click
CALL/PUT, set investment, confirm orders, or submit trades. The signal engine
is analysis-only.

## Candle CV

The included CandleDetector is a lightweight prototype intended for screen
images. For reliable deployment, calibrate the chart region and tune the
pixel/color segmentation against the exact GuruTrade7 theme/device screenshot.
