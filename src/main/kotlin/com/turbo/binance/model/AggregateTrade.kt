package com.turbo.binance.model

import java.math.BigDecimal
import java.time.ZonedDateTime

data class AggregateTrade(
        val aggregateTradeId: Long,
        val price: BigDecimal,
        val aggregateQuantity: BigDecimal,
        val firstTradeId: Long,
        val lastTradeId: Long,
        val time: ZonedDateTime,
        val isBuyerMaker: Boolean,
        val isBestMatch: Boolean
)