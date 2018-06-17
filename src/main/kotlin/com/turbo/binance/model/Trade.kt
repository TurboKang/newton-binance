package com.turbo.binance.model

import java.math.BigDecimal
import java.time.ZonedDateTime

data class Trade (
        val id: Long,
        val price: BigDecimal,
        val quantity: BigDecimal,
        val time: ZonedDateTime,
        val isBuyerMaker: Boolean,
        val isBestMatch: Boolean
)