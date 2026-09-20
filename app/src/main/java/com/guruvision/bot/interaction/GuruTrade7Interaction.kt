
package com.guruvision.bot.interaction

import android.content.Context
import android.content.Intent

/**
 * Safe interaction layer: launches/focuses the installed GuruTrade7 app.
 * It intentionally does not place, confirm, or submit financial orders.
 */
class GuruTrade7Interaction(private val context:Context) {
    fun openGuruTrade7(packageName:String):Boolean {
        val intent=context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        context.startActivity(intent)
        return true
    }
}
