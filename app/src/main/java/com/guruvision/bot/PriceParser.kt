package com.guruvision.bot

object PriceParser {
    private val number = Regex("(?<!\\d)(\\d{1,3}[.,]\\d{4,8})(?!\\d)")
    private val eurUsd = Regex("(?i)EUR\\s*[/\\-]?\\s*USD|EURUSD")

    data class ParseResult(
        val assetDetected: Boolean,
        val price: Double?,
        val matchedText: String?
    )

    fun parse(text: String): ParseResult {
        val normalized = text.replace(',', '.')
        val asset = eurUsd.containsMatchIn(normalized)
        val candidates = number.findAll(normalized)
            .mapNotNull { m -> m.groupValues[1].toDoubleOrNull()?.let { it to m.groupValues[1] } }
            .filter { (v, _) -> v in 0.5..2.0 }
            .toList()

        val last = candidates.lastOrNull()
        return ParseResult(asset, last?.first, last?.second)
    }

    fun findEurUsdPrice(text: String): Double? = parse(text).price
}
