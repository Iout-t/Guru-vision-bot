
package com.guruvision.bot.calibration

data class Region(val left:Int, val top:Int, val right:Int, val bottom:Int)

data class Calibration(
    val screenWidth:Int,
    val screenHeight:Int,
    val symbol:Region?,
    val price:Region?,
    val chart:Region?
)

class CalibrationStore {
    @Volatile var current: Calibration? = null
}
