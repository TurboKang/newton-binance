package com.turbo.newton

import com.turbo.binance.BinanceClient
import com.turbo.binance.enum.CandleIntervalEnum
import com.turbo.binance.model.Symbol
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.ZonedDateTime

class RsiStrategy(
    private val binanceClient: BinanceClient,
    private val eventManager: EventManager,
    private val symbol: Symbol
) {
  companion object {
    val logger = LoggerFactory.getLogger(RsiStrategy::class.java)
  }

  val shortTerm: Duration = Duration.ofMinutes(1)
  val midTerm: Duration = Duration.ofMinutes(15)
  val longTerm: Duration = Duration.ofMinutes(60)
  var chart: Chart? = null
  var isInitialized = false
  var shortTermTrendValue = 0.0
  var midTermTrendValue = 0.0
  var longTermTrendValue = 0.0

  suspend fun run() {
    val duration = Duration.ofMinutes(1)
    System.out.println("run ${CandleIntervalEnum.fromDuration(duration)}")
    val candles2 = binanceClient.getCandles(
        symbolStr = symbol.symbol,
        firstCandleOpenZonedDateTime = null,
        lastCandleOpenZonedDateTime = null,
        interval = CandleIntervalEnum.fromDuration(duration),
        limit = 1000
    )
    val candles1 = binanceClient.getCandles(
        symbolStr = symbol.symbol,
        firstCandleOpenZonedDateTime = candles2.first().openTime.minusMinutes(1000),
        lastCandleOpenZonedDateTime = candles2.first().openTime.minusMinutes(1),
        interval = CandleIntervalEnum.fromDuration(duration),
        limit = 1000
    )

    chart = Chart((candles1 + candles2).sortedBy { it.openTime }.toMutableList())
    logger.info(candles2.first().toString())
    logger.info(candles2.last().toString())
    logger.info(candles1.first().toString())
    logger.info(candles1.last().toString())

    System.out.println("runDone")
    tick()
  }

  private suspend fun tick() {
    val(prevShortTermTrend, prevMidTermTrend, prevLongTermTrend) = Triple(shortTermTrend, midTermTrend, longTermTrend)

    val realtimeCandles = binanceClient.getCandles(
        symbolStr = symbol.symbol,
        firstCandleOpenZonedDateTime = null,
        lastCandleOpenZonedDateTime = null,
        interval = CandleIntervalEnum.MINUTE_1,
        limit = 2
    )

    chart!!.addCandle(realtimeCandles.first())
    chart!!.addCandle(realtimeCandles.last())

    shortTermTrendValue = evalShortTermTrend()
    midTermTrendValue = evalMidTermTrend()
    longTermTrendValue = evalLongTermTrend()

    logger.info("STOCH: $shortTermTrendValue")

    val(shortTermTrend, midTermTrend, longTermTrend) = Triple(shortTermTrend, midTermTrend, longTermTrend)
    if(prevShortTermTrend * shortTermTrend == -1 || prevMidTermTrend * midTermTrend == -1 || prevLongTermTrend * longTermTrend == -1) {
      logger.info("${ZonedDateTime.now()} : <$prevShortTermTrend,$prevMidTermTrend,$prevLongTermTrend> -> <$shortTermTrend, $midTermTrend, $longTermTrend> : ${realtimeCandles.last().closePrice}")
    }
    eventManager.bookFuture(1000, suspend { tick() })
  }

  private fun evalShortTermTrend(): Double {
    val mcc = chart!!.getMergedCandleChart(shortTerm)
    return mcc.slowStochsticValue(5,3).toDouble()
  }

  private fun evalMidTermTrend(): Double {
    return chart!!.getMergedCandleChart(midTerm).slowStochsticValue(5,3).toDouble()
  }

  private fun evalLongTermTrend(): Double {
    return chart!!.getMergedCandleChart(longTerm).slowStochsticValue(5,3).toDouble()
  }

  private val shortTermTrend: Int get() {
    return when {
        shortTermTrendValue > 0.70 -> 1
      shortTermTrendValue < 0.30 -> -1
        else -> 0
    }
  }
  private val midTermTrend: Int get() {
    return when {
      midTermTrendValue > 0.70 -> 1
      midTermTrendValue < 0.30 -> -1
      else -> 0
    }
  }
  private val longTermTrend: Int get() {
    return when {
      longTermTrendValue > 0.70 -> 1
      longTermTrendValue < 0.30 -> -1
      else -> 0
    }
  }
}