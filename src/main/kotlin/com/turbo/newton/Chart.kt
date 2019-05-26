package com.turbo.newton

import com.turbo.binance.model.Candle
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Duration

data class Chart(
    private var candles: MutableList<Candle>
) {
  companion object {
      val logger = LoggerFactory.getLogger(Chart::class.java)
  }

  fun addCandle(candle: Candle) {
    when {
      candles.last().openTime == candle.openTime -> candles[candles.size-1] = candle
      candles.last().closeTime.plusNanos(1000000) == candle.openTime -> candles.add(candle)
    }
  }

  fun getLastCandle(): Candle {
    return candles.last()
  }

  fun getMergedCandleChart(duration: Duration): Chart {
    val a = System.currentTimeMillis()
    val chunkSize = duration.toNanos() / Duration.ofMinutes(1).toNanos()
    val ret = if(chunkSize != 1L) {
      Chart(
          candles
              .chunked(chunkSize.toInt())
              .map { Candle.merge(it) }
              .toMutableList()
      )
    } else {
      this
    }
    System.out.println("MergeCandle: " + (System.currentTimeMillis() - a))
    return ret
  }

  fun fastStochasticValue(barCount: Int): BigDecimal? {
    return stochastic(candles.size - barCount, barCount)
  }

  fun fastStochasticList(barCount: Int): List<BigDecimal> {
    return (0 .. (candles.size - barCount)).map {
      stochastic(it, barCount)!!
    }
  }

  fun slowStochsticValue(barCount: Int, movingAverageSize: Int): BigDecimal {
    val fastStochasticList = fastStochasticList(barCount)
    return fastStochasticList.subList(fastStochasticList.size - movingAverageSize, fastStochasticList.size).fold(BigDecimal.ZERO, BigDecimal::add) / movingAverageSize.toBigDecimal()
  }

  fun slowStochsticList(barCount: Int, movingAverageSize: Int): List<BigDecimal> {
    val fastStochasticList = fastStochasticList(barCount)
    return (0 .. fastStochasticList.size - movingAverageSize).map {
      fastStochasticList.subList(it, it+movingAverageSize).fold(BigDecimal.ZERO, BigDecimal::add) / movingAverageSize.toBigDecimal()
    }
  }

  private fun stochastic(startIndex: Int, barCount: Int): BigDecimal? {
    if(startIndex + barCount > candles.size) {
      return null
    } else {
      val scopedCandles = candles.subList(startIndex, startIndex+barCount)
      val minClosePrice = scopedCandles.map { it.closePrice }.min()!!
      val maxClosePrice = scopedCandles.map { it.closePrice }.max()!!
      val numerator = scopedCandles.last().closePrice - minClosePrice
      val denominator = maxClosePrice - minClosePrice
      return numerator / denominator * BigDecimal(100)
    }
  }
}