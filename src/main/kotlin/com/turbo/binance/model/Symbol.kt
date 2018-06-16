package com.turbo.binance.model

import com.turbo.binance.enum.OrderTypeEnum
import com.turbo.binance.enum.StatusEnum
import java.math.BigDecimal

data class Symbol(
        val symbol: String,
        val status: StatusEnum,
        val baseAsset: String,
        val baseAssetPrecision: Int,
        val quoteAsset: String,
        val quotePrecision: Int,
        val orderTypes: List<OrderTypeEnum>,
        val icebergAllowed: Boolean,
        val filters: List<Map<String, String>>
) {
    val minPrice: BigDecimal get() {
        return BigDecimal(filters[0]["minPrice"] as String).setScale(quotePrecision)
    }
    val maxPrice: BigDecimal get() {
        return BigDecimal(filters[0]["maxPrice"] as String).setScale(quotePrecision)
    }
    val tickSize: BigDecimal get() {
        return BigDecimal(filters[0]["tickSize"] as String).setScale(quotePrecision)
    }


    val minQuantity: BigDecimal get() {
        return BigDecimal(filters[1]["minQty"] as String).setScale(quotePrecision)
    }
    val maxQuantity: BigDecimal get() {
        return BigDecimal(filters[1]["maxQuantity"] as String).setScale(quotePrecision)
    }
    val stepSize: BigDecimal get() {
        return BigDecimal(filters[1]["stepSize"] as String).setScale(quotePrecision)
    }

    val minNotional: BigDecimal get() {
        return BigDecimal(filters[2]["minNotional"] as String).setScale(quotePrecision)
    }

    fun price2tick(price: BigDecimal): Int {
        return ((price - minPrice) / tickSize).intValueExact()
    }
    fun tick2price(tick: Int): BigDecimal {
        return minPrice + tick.toBigDecimal() * tickSize
    }
}