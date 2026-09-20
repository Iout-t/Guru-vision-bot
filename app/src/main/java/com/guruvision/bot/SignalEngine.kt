package com.guruvision.bot

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    val horizonSeconds: Int = 5
)

class SignalEngine {

    companion object {
        const val HORIZON_SECONDS = 5

        // Signal is produced only when the score reaches this level.
        // This is a model score, NOT a win probability.
        const val CONFIDENCE_THRESHOLD = 0.82

        // Minimum movement required to avoid calling tiny noise a trend.
        const val MIN_MOVE_RATIO = 0.000015

        // Require the recent observations to agree.
        const val MIN_DIRECTION_AGREEMENT = 0.75
    }

    private val prices = ArrayDeque<PricePoint>()

    fun addPrice(price: Double, timestamp: Long = System.currentTimeMillis()): SignalResult {
        if (!price.isFinite() || price <= 0.0) {
            return SignalResult(
                Signal.WAIT,
                0.0,
                price,
                "Invalid price"
            )
        }

        prices.addLast(PricePoint(timestamp, price))

        // Keep approximately the latest 5 seconds + small buffer.
        val cutoff = timestamp - 5500L

        while (prices.isNotEmpty() && prices.first().timestamp < cutoff) {
            prices.removeFirst()
        }

        if (prices.size < 5) {
            return SignalResult(
                Signal.WAIT,
                0.0,
                price,
                "Collecting 5-second price window"
            )
        }

        return calculate5SecondSignal()
    }

    private fun calculate5SecondSignal(): SignalResult {

        val current = prices.last()
        val first = prices.first()

        val totalMove = current.price - first.price

        val moveRatio =
            abs(totalMove) / first.price

        // Too little movement = noise/range.
        if (moveRatio < MIN_MOVE_RATIO) {
            return SignalResult(
                Signal.RANGE,
                0.82,
                current.price,
                "5-second movement is too small; range condition"
            )
        }

        var upward = 0
        var downward = 0

        val list = prices.toList()

        for (i in 1 until list.size) {

            val previous = list[i - 1].price
            val now = list[i].price

            if (now > previous) {
                upward++
            } else if (now < previous) {
                downward++
            }
        }

        val totalChanges = max(1, upward + downward)

        val upAgreement =
            upward.toDouble() / totalChanges

        val downAgreement =
            downward.toDouble() / totalChanges

        val directionAgreement =
            max(upAgreement, downAgreement)

        /*
         * Acceleration:
         * Compare movement in the first half and second half
         * of the 5-second observation window.
         */

        val midpoint = list.size / 2

        val firstHalfMove =
            list[midpoint].price - list.first().price

        val secondHalfMove =
            list.last().price - list[midpoint].price

        val acceleration =
            abs(secondHalfMove) - abs(firstHalfMove)

        val accelerationScore =
            min(
                1.0,
                abs(acceleration) /
                    max(abs(totalMove), 1e-10)
            )

        /*
         * Direction score.
         */
        val directionScore =
            min(
                1.0,
                directionAgreement
            )

        /*
         * Movement score.
         */
        val movementScore =
            min(
                1.0,
                moveRatio / 0.00015
            )

        /*
         * Final model score.
         *
         * Direction is weighted most heavily because this is
         * a 5-second CALL/PUT direction engine.
         */
        val confidence =
            (
                directionScore * 0.55 +
                movementScore * 0.30 +
                accelerationScore * 0.15
            ).coerceIn(0.0, 1.0)

        /*
         * Do not produce CALL/PUT unless the direction remained
         * sufficiently consistent throughout the observation window.
         */
        if (directionAgreement < MIN_DIRECTION_AGREEMENT) {

            return SignalResult(
                Signal.WAIT,
                confidence,
                current.price,
                "5-second direction is unstable"
            )
        }

        if (confidence < CONFIDENCE_THRESHOLD) {

            return SignalResult(
                Signal.WAIT,
                confidence,
                current.price,
                "5-second confidence threshold not reached"
            )
        }

        return if (totalMove > 0) {

            SignalResult(
                signal = Signal.CALL,
                confidence = confidence,
                price = current.price,
                reason =
                    "5-second upward movement remained consistent"
            )

        } else {

            SignalResult(
                signal = Signal.PUT,
                confidence = confidence,
                price = current.price,
                reason =
                    "5-second downward movement remained consistent"
            )
        }
    }

    fun reset() {
        prices.clear()
    }
}
