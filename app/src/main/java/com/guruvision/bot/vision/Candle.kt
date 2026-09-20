
package com.guruvision.bot.vision

data class Candle(
    val index:Int,
    val openY:Float,
    val highY:Float,
    val lowY:Float,
    val closeY:Float,
    val bullish:Boolean,
    val bodyRatio:Float,
    val upperWickRatio:Float,
    val lowerWickRatio:Float
)
