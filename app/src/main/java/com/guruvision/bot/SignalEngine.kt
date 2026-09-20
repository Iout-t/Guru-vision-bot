package com.guruvision.bot

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PricePoint(
    val timestamp: Long,
    val price: Double
)

enum class Signal {
    CALL,
    PUT,
    RANGE,
    WAIT
}

data class SignalResult(
    val signal: Signal,
    val confidence: Double,
    val price: Double,
    val reason: String,
    val horizonSeconds: Int = 5,
    val stability: Double = 0.0,
    val noise: Double = 0.0
)

class SignalEngine {

    companion object {

        // Prediction horizon is ALWAYS 5 seconds.
        const val HORIZON_MS = 5_000L

        // Warm-up requirement.
        const val MIN_HISTORY_MS = 5_000L

        // High-confidence model threshold.
        // This is a model score, NOT a 86% probability of winning.
        const val BASE_THRESHOLD = 0.86

        // Required directional agreement through the window.
        const val MIN_STABILITY = 0.80

        // Reject extremely noisy windows.
        const val MAX_NOISE = 0.70

        // Avoid treating microscopic price changes as signals.
        const val MIN_NORMALIZED_MOVE = 0.30
    }

    private val prices = ArrayDeque<PricePoint>()

    private var lastResult: SignalResult =
        SignalResult(
            signal = Signal.WAIT,
            confidence = 0.0,
            price = 0.0,
            reason = "Waiting for 5-second history"
        )

    fun addPrice(
        price: Double,
        timestamp: Long = System.currentTimeMillis(),
        candleBias: Double = 0.0
    ): SignalResult {

        if (!price.isFinite() || price <= 0.0) {
            return lastResult.copy(
                signal = Signal.WAIT,
                reason = "Invalid price"
            )
        }

        /*
         * Reject duplicate OCR values arriving multiple times
         * without enough elapsed time.
         */
        val previous = prices.lastOrNull()

        if (previous != null &&
            abs(price - previous.price) < 1e-12 &&
            timestamp - previous.timestamp < 150L
        ) {
            return lastResult
        }

        prices.addLast(
            PricePoint(timestamp, price)
        )

        /*
         * Keep a little more than the prediction horizon so
         * the engine has room to construct rolling features.
         */
        val cutoff =
            timestamp - 7_000L

        while (
            prices.isNotEmpty() &&
            prices.first().timestamp < cutoff
        ) {
            prices.removeFirst()
        }

        if (prices.size < 8) {
            return SignalResult(
                Signal.WAIT,
                0.0,
                price,
                "Collecting 5-second observations"
            )
        }

        if (
            timestamp - prices.first().timestamp
            < MIN_HISTORY_MS
        ) {
            return SignalResult(
                Signal.WAIT,
                0.0,
                price,
                "5-second window not complete"
            )
        }

        val result =
            calculate(
                pricePoints = prices.toList(),
                candleBias = candleBias
            )

        lastResult = result

        return result
    }

    private fun calculate(
        pricePoints: List<PricePoint>,
        candleBias: Double
    ): SignalResult {

        val now = pricePoints.last()

        val windowStart =
            now.timestamp - HORIZON_MS

        val window =
            pricePoints.filter {
                it.timestamp >= windowStart
            }

        if (window.size < 6) {
            return SignalResult(
                Signal.WAIT,
                0.0,
                now.price,
                "Insufficient 5-second observations"
            )
        }

        val prices =
            window.map { it.price }

        /*
         * ----------------------------------------------------
         * 1. LOG RETURNS
         * ----------------------------------------------------
         */

        val returns =
            mutableListOf<Double>()

        for (i in 1 until prices.size) {

            val a = prices[i - 1]
            val b = prices[i]

            if (a > 0.0 && b > 0.0) {
                returns.add(
                    ln(b / a)
                )
            }
        }

        if (returns.size < 4) {
            return SignalResult(
                Signal.WAIT,
                0.0,
                now.price,
                "Insufficient return observations"
            )
        }

        /*
         * ----------------------------------------------------
         * 2. MULTI-SCALE MOMENTUM
         * ----------------------------------------------------
         */

        val firstPrice =
            prices.first()

        val currentPrice =
            prices.last()

        val totalReturn =
            ln(currentPrice / firstPrice)

        val halfIndex =
            max(1, prices.size / 2)

        val halfReturn =
            ln(
                prices.last() /
                    prices[halfIndex]
            )

        /*
         * Recent movement gets more weight.
         */
        val recentReturn =
            returns
                .takeLast(
                    min(returns.size, 4)
                )
                .sum()

        /*
         * ----------------------------------------------------
         * 3. PRICE-CHANGE DIRECTIONAL IMBALANCE
         *
         * This is only a PRICE-TICK proxy.
         * It is NOT real order-book imbalance.
         * ----------------------------------------------------
         */

        var upWeight = 0.0
        var downWeight = 0.0

        for (i in 1 until window.size) {

            val move =
                window[i].price -
                    window[i - 1].price

            /*
             * Recent observations receive more weight.
             */
            val age =
                (window.size - i)
                    .toDouble()

            val weight =
                exp(-0.20 * age)

            if (move > 0.0) {
                upWeight += weight
            } else if (move < 0.0) {
                downWeight += weight
            }
        }

        val imbalanceDenominator =
            max(
                upWeight + downWeight,
                1e-9
            )

        val tickImbalance =
            (upWeight - downWeight) /
                imbalanceDenominator

        /*
         * ----------------------------------------------------
         * 4. DIRECTIONAL PERSISTENCE
         * ----------------------------------------------------
         */

        var directionalSteps = 0

        for (i in 1 until returns.size) {

            val a = returns[i - 1]
            val b = returns[i]

            if (
                (a >= 0.0 && b >= 0.0) ||
                (a <= 0.0 && b <= 0.0)
            ) {
                directionalSteps++
            }
        }

        val persistence =
            directionalSteps.toDouble() /
                max(1, returns.size - 1)

        /*
         * ----------------------------------------------------
         * 5. REVERSAL PENALTY
         * ----------------------------------------------------
         */

        var reversals = 0

        for (i in 1 until returns.size) {

            if (
                returns[i] *
                returns[i - 1] < 0.0
            ) {
                reversals++
            }
        }

        val reversalRatio =
            reversals.toDouble() /
                max(1, returns.size - 1)

        /*
         * ----------------------------------------------------
         * 6. VOLATILITY / NOISE
         * ----------------------------------------------------
         */

        val mean =
            returns.average()

        var variance = 0.0

        for (r in returns) {
            val d = r - mean
            variance += d * d
        }

        variance /=
            max(1, returns.size - 1)

        val volatility =
            sqrt(variance)

        val absoluteMovement =
            returns.sumOf {
                abs(it)
            }

        val netMovement =
            abs(totalReturn)

        /*
         * If absolute movement is much larger than net
         * movement, the path is noisy and reverses heavily.
         */
        val pathNoise =
            (
                1.0 -
                    min(
                        1.0,
                        netMovement /
                            max(
                                absoluteMovement,
                                1e-12
                            )
                    )
            ).coerceIn(0.0, 1.0)

        val noise =
            (
                pathNoise * 0.70 +
                    reversalRatio * 0.30
            ).coerceIn(0.0, 1.0)

        /*
         * ----------------------------------------------------
         * 7. ACCELERATION
         * ----------------------------------------------------
         */

        val acceleration =
            recentReturn -
                (totalReturn / 2.0)

        val accelerationDirection =
            when {
                acceleration > 0.0 -> 1.0
                acceleration < 0.0 -> -1.0
                else -> 0.0
            }

        /*
         * ----------------------------------------------------
         * 8. NORMALIZED MOVEMENT
         * ----------------------------------------------------
         *
         * Movement is normalized by recent volatility so
         * different assets can use the same engine.
         */

        val normalizedMove =
            abs(totalReturn) /
                max(
                    volatility *
                        sqrt(
                            returns.size.toDouble()
                        ),
                    1e-10
                )

        /*
         * ----------------------------------------------------
         * 9. FEATURE SCORES
         * ----------------------------------------------------
         */

        val momentumDirection =
            sign(totalReturn)

        val recentDirection =
            sign(recentReturn)

        val imbalanceDirection =
            sign(tickImbalance)

        val accelerationAgreement =
            when {
                accelerationDirection == 0.0 ->
                    0.5

                accelerationDirection ==
                    momentumDirection ->
                    1.0

                else ->
                    0.0
            }

        val directionAgreement =
            listOf(
                momentumDirection,
                recentDirection,
                imbalanceDirection
            )
                .count {
                    it == momentumDirection &&
                        it != 0.0
                }
                .toDouble() / 3.0

        /*
         * Candle bias is supplied by the CV subsystem:
         *
         * +1 = bullish
         *  0 = neutral
         * -1 = bearish
         */
        val candleAgreement =
            if (candleBias == 0.0) {
                0.5
            } else if (
                sign(candleBias) ==
                momentumDirection
            ) {
                1.0
            } else {
                0.0
            }

        /*
         * ----------------------------------------------------
         * 10. MODEL ENSEMBLE
         * ----------------------------------------------------
         */

        val rawDirection =
            (
                tickImbalance * 0.30 +
                    normalizedSignedMove(
                        totalReturn,
                        volatility,
                        returns.size
                    ) * 0.25 +
                    recentDirection * 0.15 +
                    accelerationDirection * 0.10 +
                    candleBias * 0.10 +
                    momentumDirection * 0.10
            )
                .coerceIn(-1.0, 1.0)

        val direction =
            sign(rawDirection)

        /*
         * Stability measures whether all components agree.
         */
        val stability =
            (
                directionAgreement * 0.55 +
                    persistence * 0.25 +
                    candleAgreement * 0.10 +
                    accelerationAgreement * 0.10
            )
                .coerceIn(0.0, 1.0)

        /*
         * Strong movement without excessive noise.
         */
        val movementStrength =
            min(
                1.0,
                normalizedMove / 2.5
            )

        val lowNoiseScore =
            1.0 - noise

        /*
         * ----------------------------------------------------
         * 11. CONFIDENCE SCORE
         * ----------------------------------------------------
         *
         * This is deliberately conservative.
         */
        var confidence =
            (
                abs(rawDirection) * 0.35 +
                    stability * 0.30 +
                    movementStrength * 0.20 +
                    lowNoiseScore * 0.15
            )
                .coerceIn(0.0, 1.0)

        /*
         * High noise directly reduces confidence.
         */
        confidence *=
            (1.0 - noise * 0.35)

        /*
         * Dynamic threshold:
         * noisy windows must reach an even higher score.
         */
        val threshold =
            when {
                noise >= 0.55 ->
                    0.94

                noise >= 0.40 ->
                    0.90

                else ->
                    BASE_THRESHOLD
            }

        /*
         * ----------------------------------------------------
         * 12. FINAL DECISION
         * ----------------------------------------------------
         */

        if (noise > MAX_NOISE) {

            return SignalResult(
                Signal.RANGE,
                confidence,
                currentPrice,
                "5-second path is too noisy",
                stability = stability,
                noise = noise
            )
        }

        if (
            abs(rawDirection) < 0.25 ||
            normalizedMove < MIN_NORMALIZED_MOVE
        ) {

            return SignalResult(
                Signal.WAIT,
                confidence,
                currentPrice,
                "5-second directional movement is weak",
                stability = stability,
                noise = noise
            )
        }

        if (stability < MIN_STABILITY) {

            return SignalResult(
                Signal.WAIT,
                confidence,
                currentPrice,
                "5-second direction is not stable enough",
                stability = stability,
                noise = noise
            )
        }

        if (confidence < threshold) {

            return SignalResult(
                Signal.WAIT,
                confidence,
                currentPrice,
                "5-second confidence threshold not reached",
                stability = stability,
                noise = noise
            )
        }

        return if (direction > 0.0) {

            SignalResult(
                signal = Signal.CALL,
                confidence = confidence,
                price = currentPrice,
                reason =
                    "5-second ensemble agrees upward",
                stability = stability,
                noise = noise
            )

        } else {

            SignalResult(
                signal = Signal.PUT,
                confidence = confidence,
                price = currentPrice,
                reason =
                    "5-second ensemble agrees downward",
                stability = stability,
                noise = noise
            )
        }
    }

    /*
     * Normalize total movement using realized volatility.
     */
    private fun normalizedSignedMove(
        totalReturn: Double,
        volatility: Double,
        sampleCount: Int
    ): Double {

        val normalized =
            totalReturn /
                max(
                    volatility *
                        sqrt(sampleCount.toDouble()),
                    1e-10
                )

        return normalized
            .coerceIn(-1.0, 1.0)
    }

    private fun sign(value: Double): Double {
        return when {
            value > 0.0 -> 1.0
            value < 0.0 -> -1.0
            else -> 0.0
        }
    }

    fun reset() {
        prices.clear()

        lastResult =
            SignalResult(
                Signal.WAIT,
                0.0,
                0.0,
                "Engine reset"
            )
    }

    fun latest(): SignalResult {
        return lastResult
    }
}
