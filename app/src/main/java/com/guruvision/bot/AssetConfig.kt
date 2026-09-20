package com.guruvision.bot

data class AssetConfig(
    val symbol: String,
    val displayName: String
)

object AssetConfigList {

    val assets = listOf(
        AssetConfig("EUR/USD (OTC)", "EUR/USD OTC"),
        AssetConfig("GBP/JPY (OTC)", "GBP/JPY OTC"),
        AssetConfig("EUR/AUD (OTC)", "EUR/AUD OTC"),
        AssetConfig("USD/JPY (OTC)", "USD/JPY OTC"),
        AssetConfig("GBP/USD (OTC)", "GBP/USD OTC"),
        AssetConfig("AUD/USD (OTC)", "AUD/USD OTC"),

        AssetConfig("BTC/USD", "Bitcoin / USD"),
        AssetConfig("ETH/USD", "Ethereum / USD"),
        AssetConfig("SOL/USD", "Solana / USD"),

        AssetConfig("GOOGL", "Alphabet"),
        AssetConfig("MSFT", "Microsoft"),
        AssetConfig("META", "Meta")
    )
}
