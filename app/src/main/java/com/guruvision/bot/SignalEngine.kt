package com.guruvision.bot

import kotlin.math.abs
import kotlin.math.max

data class SignalResult(
    val signal: String,
    val confidence: Double,
    val reason: String,
    val price: Double?
)

class SignalEngine(
    private val windowMs: Long = 5_000L,
    private val threshold: Double = 0.82
) {
    private val samples = ArrayDeque<Pair<Long, Double>>()

    fun add(price: Double, now: Long = System.currentTimeMillis()): SignalResult {
        samples.addLast(now to price)
        while (samples.isNotEmpty() && now - samples.first().first > windowMs) samples.removeFirst()

        if (samples.size < 8) return SignalResult("WAIT", 0.0, "warming_up", price)

        val values = samples.map { it.second }
        val first = values.first()
        val last = values.last()
        val move = last - first
        val span = (values.maxOrNull() ?: last) - (values.minOrNull() ?: first)

        if (span <= 0.0) return SignalResult("RANGE", 1.0, "flat", last)

        var up = 0
        var down = 0
        for (i in 1 until values.size) {
            if (values[i] > values[i - 1]) up++
            if (values[i] < values[i - 1]) down++
        }
        val total = max(1, up + down)
        val persistence = max(up, down).toDouble() / total
        val movement = minOf(1.0, abs(move) / (span * 0.55))
        val confidence = 0.50 * persistence + 0.50 * movement

        if (abs(move) <= span * 0.25 && span / last < 0.00008)
            return SignalResult("RANGE", 0.80, "compressed", last)

        if (confidence >= threshold && persistence >= 0.70)
            return SignalResult(if (move > 0) "CALL" else "PUT", confidence, "persistent_5s", last)

        return SignalResult("WAIT", confidence, "below_threshold", last)
    }
}
