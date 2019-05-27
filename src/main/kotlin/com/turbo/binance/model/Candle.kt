package com.turbo.binance.model

import com.turbo.newton.db.CandleHistory
import com.turbo.toJSR310LocalDateTime
import java.math.BigDecimal
import java.time.Duration
import java.time.ZonedDateTime

data class Candle(
        val openTime: ZonedDateTime,
        val closeTime: ZonedDateTime,
        val highPrice: BigDecimal,
        val lowPrice: BigDecimal,
        val openPrice: BigDecimal,
        val closePrice: BigDecimal,
        val volume: BigDecimal,
        val quoteAssetVolume: BigDecimal,
        val numberOfTrades: Int,
        val takerBuyBaseAssetVolume: BigDecimal,
        val takerBuyQuoteAssetVolume: BigDecimal,
        val ignore: BigDecimal
) {
  companion object {
    fun merge(c1: Candle, c2: Candle): Candle {
      return merge(listOf(c1, c2))
    }

    fun merge(listOfCandle: List<Candle>): Candle {
      val sortedListOfCandle = listOfCandle.sortedBy { it.openTime }
      val mergedCandle =  Candle(
          openTime = sortedListOfCandle.first().openTime,
          closeTime = sortedListOfCandle.last().closeTime,
          highPrice = sortedListOfCandle.map { it.highPrice }.max()!!,
          lowPrice = sortedListOfCandle.map { it.lowPrice }.min()!!,
          openPrice = sortedListOfCandle.first().openPrice,
          closePrice = sortedListOfCandle.last().closePrice,
          volume = sortedListOfCandle.map { it.volume }.fold(BigDecimal.ZERO, BigDecimal::add),
          quoteAssetVolume = sortedListOfCandle.map { it.quoteAssetVolume }.fold(BigDecimal.ZERO, BigDecimal::add),
          numberOfTrades = sortedListOfCandle.map { it.numberOfTrades }.fold(0, Int::plus),
          takerBuyBaseAssetVolume = sortedListOfCandle.map { it.takerBuyBaseAssetVolume }.fold(BigDecimal.ZERO, BigDecimal::add),
          takerBuyQuoteAssetVolume = sortedListOfCandle.map { it.takerBuyQuoteAssetVolume }.fold(BigDecimal.ZERO, BigDecimal::add),
          ignore = sortedListOfCandle.map { it.ignore }.fold(BigDecimal.ZERO, BigDecimal::add)
      )
      return mergedCandle
    }

    fun buildFromCandleHistory(candleHistory: CandleHistory): Candle {
      return Candle(
          openTime = candleHistory.openTime.toJSR310LocalDateTime(),
          closeTime = candleHistory.closeTime.toJSR310LocalDateTime(),
          highPrice = candleHistory.highPrice,
          lowPrice = candleHistory.lowPrice,
          openPrice = candleHistory.openPrice,
          closePrice = candleHistory.closePrice,
          volume = candleHistory.volume,
          quoteAssetVolume = candleHistory.quoteAssetVolume,
          numberOfTrades = candleHistory.numberOfTrades,
          takerBuyBaseAssetVolume = candleHistory.takerBuyBaseAssetVolume,
          takerBuyQuoteAssetVolume = candleHistory.takerBuyQuoteAssetVolume,
          ignore = candleHistory.ignore
      )
    }
  }

  fun merge(candle: Candle): Candle {
    return Candle.merge(this, candle)
  }


  val duration:Duration get() {
    return Duration.between(openTime, closeTime)
  }
}