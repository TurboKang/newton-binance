package com.turbo.binance.model

import java.math.BigDecimal
import java.time.ZonedDateTime

data class Stat24Hour(
        val symbol: String,
        val priceChange: BigDecimal,
        val priceChangePercent: BigDecimal,
        val weightedAvgPrice: BigDecimal,
        val prevClosePrice: BigDecimal,
        val lastPrice: BigDecimal,
        val lastQty: BigDecimal,
        val bidPrice: BigDecimal,
        val askPrice: BigDecimal,
        val openPrice: BigDecimal,
        val highPrice: BigDecimal,
        val lowPrice: BigDecimal,
        val volume: BigDecimal,
        val quoteVolume: BigDecimal,
        val openTime: ZonedDateTime,
        val closeTime: ZonedDateTime,
        val firstTradeId: Long,
        val lastTradeId: Long,
        val tradeCount: Int
)