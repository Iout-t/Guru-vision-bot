
package com.guruvision.bot.vision

data class PatternResult(val name:String, val bias:Int, val confidence:Double)

class PatternClassifier {
    fun classify(candles:List<Candle>): PatternResult {
        if (candles.isEmpty()) return PatternResult("unknown",0,0.0)
        val recent=candles.takeLast(minOf(5,candles.size))
        val bull=recent.count{it.bullish}
        val bear=recent.size-bull
        if (recent.size>=3 && bull==recent.size)
            return PatternResult("bullish_sequence",1,.75)
        if (recent.size>=3 && bear==recent.size)
            return PatternResult("bearish_sequence",-1,.75)
        val last=recent.last()
        if (last.lowerWickRatio>.65 && last.bodyRatio<.35)
            return PatternResult("hammer_like",1,.62)
        if (last.upperWickRatio>.65 && last.bodyRatio<.35)
            return PatternResult("shooting_star_like",-1,.62)
        return PatternResult("mixed", if(bull>bear) 1 else if(bear>bull) -1 else 0,.45)
    }
}
