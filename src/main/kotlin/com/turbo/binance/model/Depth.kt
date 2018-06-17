package com.turbo.binance.model

import java.math.BigDecimal

data class Depth(
        val bids: List<DepthPriceQuantity>,
        val asks: List<DepthPriceQuantity>
) {
    data class DepthPriceQuantity(
            val price: BigDecimal,
            val quantity: BigDecimal
    )
}

