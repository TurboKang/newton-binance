package com.turbo.binance.model

import com.turbo.binance.enum.OrderTypeEnum
import com.turbo.binance.enum.StatusEnum
import java.math.BigDecimal

class Symbol(
        val baseEsset: String,
        val baseEssetPrecision: Int,
        val quoteEsset: String,
        val quoteEssetPrecision: Int,
        val status: StatusEnum,
        val orderTypes: List<OrderTypeEnum>,
        val icebergAllowed: Boolean,
        val filters: List<Map<String, String>>
) {
    val minPrice: BigDecimal get() {
        return BigDecimal(filters[0]["minPrice"] as String).setScale(quoteEssetPrecision)
    }
    val maxPrice: BigDecimal get() {
        return BigDecimal(filters[0]["maxPrice"] as String).setScale(quoteEssetPrecision)
    }
    val tickSize: BigDecimal get() {
        return BigDecimal(filters[0]["tickSize"] as String).setScale(quoteEssetPrecision)
    }


    val minQuantity: BigDecimal get() {
        return BigDecimal(filters[1]["minQty"] as String).setScale(quoteEssetPrecision)
    }
    val maxQuantity: BigDecimal get() {
        return BigDecimal(filters[1]["maxQuantity"] as String).setScale(quoteEssetPrecision)
    }
    val stepSize: BigDecimal get() {
        return BigDecimal(filters[1]["stepSize"] as String).setScale(quoteEssetPrecision)
    }

    val minNotional: BigDecimal get() {
        return BigDecimal(filters[2]["minNotional"] as String).setScale(quoteEssetPrecision)
    }

    fun price2tick(price: BigDecimal): Int {
        return ((price - minPrice) / tickSize).intValueExact()
    }
    fun tick2price(tick: Int): BigDecimal {
        return minPrice + tick.toBigDecimal() * tickSize
    }
}