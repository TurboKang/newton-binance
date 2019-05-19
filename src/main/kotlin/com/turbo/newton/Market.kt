package com.turbo.newton

import com.turbo.binance.enum.OrderSideEnum
import com.turbo.binance.model.Candle
import com.turbo.binance.model.Order
import com.turbo.binance.model.Symbol
import org.slf4j.LoggerFactory
import org.ta4j.core.BaseTimeSeries
import java.math.BigDecimal
import java.time.Duration

data class Market(
    val symbol: Symbol,
    val baseAssetBalance: BigDecimal,
    val quoteAssetBalance: BigDecimal,
    val allocatedBaseAsset: BigDecimal,
    var minuteCandles: MutableList<Candle>,
    val orders: List<Order>
) {

  companion object {
    val logger = LoggerFactory.getLogger(Market::class.java)
  }


  val averagePrice: BigDecimal get() {
    return orders.fold(Pair(BigDecimal.ZERO, BigDecimal.ZERO)
    ) {
      quantityAndPrice, order ->
      val newQuantity = when(order.side) {
        OrderSideEnum.BUY -> quantityAndPrice.first + order.executedQuantity
        OrderSideEnum.SELL -> quantityAndPrice.first - order.executedQuantity
      }
      val newAverage = when(order.side) {
        OrderSideEnum.BUY -> quantityAndPrice.first * quantityAndPrice.second + order.executedQuantity*order.price
        OrderSideEnum.SELL -> quantityAndPrice.second
      }
      Pair(newQuantity, newAverage)
    }.second
  }
}