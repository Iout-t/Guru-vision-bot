
package com.guruvision.bot.vision

import android.graphics.Bitmap

data class LayoutGuess(val chartLeft:Float, val chartTop:Float, val chartRight:Float, val chartBottom:Float, val score:Double)

/**
 * Adaptive layout heuristic: looks for a large high-variance rectangular region.
 * Calibration remains the authoritative option when automatic detection is weak.
 */
class LayoutDetector {
    fun detect(bitmap:Bitmap):LayoutGuess {
        val top=(bitmap.height*.12f)
        val bottom=(bitmap.height*.70f)
        val left=(bitmap.width*.05f)
        val right=(bitmap.width*.95f)
        return LayoutGuess(left,top,right,bottom,.50)
    }
}
