package com.turbo.binance.model

import java.math.BigDecimal

data class BookTicker(
        val symbol: String,
        val bidPrice: BigDecimal,
        val bidQuantity: BigDecimal,
        val askPrice: BigDecimal,
        val askQuantity: BigDecimal
)