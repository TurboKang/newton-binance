package com.turbo.binance.model

import java.math.BigDecimal
import java.time.ZonedDateTime

data class Candle(
        val openTime: ZonedDateTime,
        val closeTime: ZonedDateTime,
        val highPrice: BigDecimal,
        val lowPrice: BigDecimal,
        val openPrice: BigDecimal,
        val closePrice: BigDecimal,
        val volume: BigDecimal,
        val quoteAssetVolume: BigDecimal,
        val numberOfTrades: Int,
        val takerBuyBaseAssetVolume: BigDecimal,
        val takerBuyQuoteAssetVolume: BigDecimal,
        val ignore: BigDecimal
)