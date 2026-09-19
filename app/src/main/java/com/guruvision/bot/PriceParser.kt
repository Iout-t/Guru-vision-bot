package com.guruvision.bot

object PriceParser {
    private val price = Regex("""(?<!\d)(\d{1,2}[.,]\d{4,8})(?!\d)""")

    fun findEurUsdPrice(text: String): Double? {
        // Look for EUR/USD context first, then accept a plausible FX quote.
        val normalized = text.replace(',', '.')
        val candidates = price.findAll(normalized)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it in 0.5..2.0 }
            .toList()
        return candidates.lastOrNull()
    }
}
