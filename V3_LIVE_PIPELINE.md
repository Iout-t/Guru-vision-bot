# GuruVision V3 — Live pipeline patch

This patch fixes the V2 behavior where the service could remain at `starting…` without showing useful state.

## What changed
- Robust `ImageReader` RGBA conversion that handles row padding.
- Live status notification: capture state, OCR state, detected price, candle count and signal.
- EUR/USD asset verification from OCR instead of assuming the selected asset.
- Floating overlay is actually connected to the service.
- Main screen now requests overlay permission before screen capture.
- 5-second engine now receives candle direction as an additional non-dominant input.
- Candle detector uses red/green screen colors instead of the previous alternating-index placeholder.
- No automatic CALL/PUT clicking or trade submission is added.

## Test
1. Install the new APK.
2. Open GuruVision and enable `ENABLE FLOATING OVERLAY`.
3. In GuruTrade7 select `EUR/USD (OTC)`.
4. Keep expiry at 5 seconds and keep one-click trading OFF.
5. Tap `START SCREEN VISION` and grant capture permission.
6. Return to GuruTrade7.
7. Watch the persistent notification and floating overlay.

Expected diagnostic states:
- `CAPTURE: OK • waiting for EUR/USD OCR`
- `CAPTURE: OK • EUR/USD found • price not found`
- `WAIT • reading price`
- `WAIT` with a warming-up count
- eventually CALL/PUT/RANGE only when the internal threshold is reached.

The confidence value is an internal signal score, not a guaranteed probability of a winning trade.
