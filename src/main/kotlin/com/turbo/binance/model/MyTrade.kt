package com.turbo.binance.model

import java.math.BigDecimal
import java.time.ZonedDateTime

data class MyTrade (
        val id: Long,
        val orderId: Long,
        val price: BigDecimal,
        val quantity: BigDecimal,
        val commission: BigDecimal,
        val commissionAsset: String,
        val time: ZonedDateTime,
        val isBuyer: Boolean,
        val isMaker: Boolean,
        val isBestMatch: Boolean
)
