package com.turbo.binance.model

import com.turbo.binance.enum.OrderSideEnum
import com.turbo.binance.enum.OrderStatusEnum
import com.turbo.binance.enum.OrderTypeEnum
import java.math.BigDecimal
import java.time.ZonedDateTime

data class Order(
        val symbol: String,
        val orderId: Long,
        val clientOrderId: String,
        val price: BigDecimal,
        val originQuantity: BigDecimal,
        val executedQuantity: BigDecimal,
        val status: OrderStatusEnum,
        val type: OrderTypeEnum,
        val side: OrderSideEnum,
        val stopPrice: BigDecimal,
        val icebergQuantity: BigDecimal,
        val time: ZonedDateTime,
        val isWorking: Boolean
)