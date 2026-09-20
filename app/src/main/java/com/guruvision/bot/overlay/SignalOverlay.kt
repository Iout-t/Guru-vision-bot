
package com.guruvision.bot.overlay

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class SignalOverlay(private val context:Context) {
    private var view:TextView?=null
    fun show(signal:String, confidence:Double) {
        val wm=context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if(view==null) {
            view=TextView(context).apply {
                setTextColor(Color.WHITE); setBackgroundColor(0xCC111111.toInt())
                setPadding(24,16,24,16); textSize=16f
            }
            val type=WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            val lp=WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                -3
            )
            lp.gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL
            wm.addView(view,lp)
        }
        view?.text="$signal  ${(confidence*100).toInt()}%"
    }
    fun hide() {
        val v=view ?: return
        val wm=context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.removeView(v); view=null
    }
}
