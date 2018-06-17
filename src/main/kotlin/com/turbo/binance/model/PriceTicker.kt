package com.turbo.binance.model

import java.math.BigDecimal

data class PriceTicker(
        val symbol: String,
        val price: BigDecimal
)