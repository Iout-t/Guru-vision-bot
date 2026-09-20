package com.guruvision.bot.vision

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/** Screen-CV candle detector. It uses the green/red candle colors to estimate body/wick geometry. */
class CandleDetector {
    fun detect(bitmap: Bitmap): List<Candle> {
        if (bitmap.width < 80 || bitmap.height < 80) return emptyList()

        val step = max(2, bitmap.width / 220)
        data class Col(val x: Int, val colored: Int, val green: Int, val red: Int)
        val cols = mutableListOf<Col>()

        for (x in 0 until bitmap.width step step) {
            var colored = 0
            var green = 0
            var red = 0
            for (y in 0 until bitmap.height step max(1, bitmap.height / 140)) {
                val c = bitmap.getPixel(x, y)
                val r = (c shr 16) and 255
                val g = (c shr 8) and 255
                val b = c and 255
                if (g > 135 && g > r * 1.18 && g > b * 1.05) { green++; colored++ }
                else if (r > 150 && r > g * 1.18 && r > b * 1.10) { red++; colored++ }
            }
            if (colored >= 2) cols.add(Col(x, colored, green, red))
        }

        if (cols.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<Col>>()
        for (col in cols) {
            if (groups.isEmpty() || col.x - groups.last().last().x > step * 3) groups.add(mutableListOf(col))
            else groups.last().add(col)
        }

        return groups.takeLast(40).mapIndexedNotNull { idx, group ->
            val left = group.first().x
            val right = min(bitmap.width - 1, group.last().x + step)
            var top = bitmap.height
            var bottom = 0
            var greenPixels = 0
            var redPixels = 0

            for (x in left..right) {
                for (y in 0 until bitmap.height step max(1, bitmap.height / 180)) {
                    val c = bitmap.getPixel(x, y)
                    val r = (c shr 16) and 255
                    val g = (c shr 8) and 255
                    val b = c and 255
                    if (g > 135 && g > r * 1.18 && g > b * 1.05) {
                        greenPixels++; top = min(top, y); bottom = max(bottom, y)
                    } else if (r > 150 && r > g * 1.18 && r > b * 1.10) {
                        redPixels++; top = min(top, y); bottom = max(bottom, y)
                    }
                }
            }

            if (bottom <= top) return@mapIndexedNotNull null
            val range = (bottom - top).coerceAtLeast(1)
            val bullish = greenPixels >= redPixels
            // The screen-CV stage knows direction/color but not true OHLC yet; keep wick/body estimates conservative.
            val bodyRatio = 0.45f
            Candle(
                idx,
                top + range * 0.275f,
                top.toFloat(),
                bottom.toFloat(),
                top + range * 0.725f,
                bullish,
                bodyRatio,
                0.5f,
                0.5f
            )
        }
    }
}
