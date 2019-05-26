package com.turbo.newton

import com.turbo.binance.BinanceClient
import com.turbo.binance.BinanceMock
import com.turbo.binance.enum.CandleIntervalEnum
import com.turbo.binance.model.Symbol
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Duration
import java.time.ZonedDateTime

class BackTestStrategy(
    private val binanceMock: BinanceMock,
    private val symbolStr: String,
    private val backTestStartDateTime: ZonedDateTime,
    private val shortTerm: Duration = Duration.ofMinutes(1),
    private val midTerm: Duration = Duration.ofMinutes(15),
    private val longTerm: Duration = Duration.ofMinutes(60),
    private val eventManager: EventManager
) {
  companion object {
    val logger = LoggerFactory.getLogger(BackTestStrategy::class.java)
  }

  var index = 1
  var chart: Chart? = null
  var smlTrendValue = Triple(0.0, 0.0, 0.0)

  var quoteAssetBalance = BigDecimal(1000).setScale(8, BigDecimal.ROUND_DOWN)
  var baseAssetBalance = BigDecimal.ZERO.setScale(8, BigDecimal.ROUND_DOWN)

  var positionPair = Pair(0, 10)

  var startTime = System.currentTimeMillis() / 1000
  var startAsset = BigDecimal.ZERO
  var startPrice = BigDecimal.ZERO

  suspend fun run() {
    val candles = binanceMock.getCandles(
        symbolStr = symbolStr,
        firstCandleOpenZonedDateTime = null,
        lastCandleOpenZonedDateTime = backTestStartDateTime,
        interval = CandleIntervalEnum.MINUTE_1,
        limit = 10000
    )

    chart = Chart(candles.sortedBy { it.openTime }.toMutableList())
    startPrice = chart!!.getLastCandle().closePrice
    startAsset = baseAssetBalance * startPrice + quoteAssetBalance
    checkTrendAndSetPosition()
  }

  suspend fun checkTrendAndSetPosition() {
    val previousTrend = getTrendFromValueVector(smlTrendValue)
    val candles = binanceMock.getCandles(
        symbolStr = symbolStr,
        firstCandleOpenZonedDateTime = null,
        lastCandleOpenZonedDateTime = backTestStartDateTime.plusMinutes(index.toLong()),
        interval = CandleIntervalEnum.MINUTE_1,
        limit = 2
    )

    chart!!.addCandle(candles.first())
    chart!!.addCandle(candles.last())

    smlTrendValue = Triple(evalShortTermTrendValue(chart!!), evalMidTermTrendValue(chart!!), evalLongTermTrendValue(chart!!))
    val currentTrend = getTrendFromValueVector(smlTrendValue)
    if(currentTrend != previousTrend) {
//      logger.info("${candles.last().closeTime} - $currentTrend")
      setPosition(currentTrend)
    }

    if(index % 60 == 0) {
      evaluate()
    }
    index++
    eventManager.bookFuture(0, suspend { checkTrendAndSetPosition() })
  }

  suspend fun setPosition(trend: Triple<Int, Int, Int>) {
    val previousPosition = positionPair
    positionPair = when(trend) {
      Triple(1,1,1) -> Pair(10, 0)
      Triple(1,1,0) -> Pair(10, 0)
      Triple(1,1,-1) -> Pair(10, 0)
      Triple(1,0,1) -> Pair(8, 2)
      Triple(1,0,0) -> Pair(8, 2)
      Triple(1,0,-1) -> Pair(8, 2)
      Triple(1,-1,1) -> Pair(6, 4)
      Triple(1,-1,0) -> Pair(6, 4)
      Triple(1,-1,-1) -> Pair(6, 4)
      Triple(0,1,1) -> Pair(5, 5)
      Triple(0,1,0) -> Pair(5, 5)
      Triple(0,1,-1) -> Pair(5, 5)
      Triple(0,0,1) -> Pair(5, 5)
      Triple(0,0,0) -> Pair(5, 5)
      Triple(0,0,-1) -> Pair(5, 5)
      Triple(0,-1,1) -> Pair(5, 5)
      Triple(0,-1,0) -> Pair(5, 5)
      Triple(0,-1,-1) -> Pair(5, 5)
      Triple(-1,1,1) -> Pair(4, 6)
      Triple(-1,1,0) -> Pair(4, 6)
      Triple(-1,1,-1) -> Pair(4, 6)
      Triple(-1,0,1) -> Pair(2, 8)
      Triple(-1,0,0) -> Pair(2, 8)
      Triple(-1,0,-1) -> Pair(2, 8)
      Triple(-1,-1,1) -> Pair(0, 10)
      Triple(-1,-1,0) -> Pair(0, 10)
      Triple(-1,-1,-1) -> Pair(0, 10)
      else -> previousPosition
    }
    if(previousPosition != positionPair) {
      val baseAssetDifference = getPositionChangeResult(previousPosition, positionPair)
      val currentCandle = chart!!.getLastCandle()
      val currentPrice = currentCandle.closePrice

      baseAssetBalance = (baseAssetBalance + baseAssetDifference).setScale(8, BigDecimal.ROUND_DOWN)
      quoteAssetBalance = (quoteAssetBalance - baseAssetDifference * currentPrice).setScale(8, BigDecimal.ROUND_DOWN)
    }
  }

  private fun evaluate() {
    val currentCandle = chart!!.getLastCandle()
    val currentPrice = currentCandle.closePrice
    val balanceByQuoteAsset = getBalanceByQuoteAsset(currentPrice)
    val backTestROI = ((balanceByQuoteAsset-startAsset) / startAsset * BigDecimal(100)).setScale(2, BigDecimal.ROUND_HALF_UP)
    val marketROI = ((currentPrice - startPrice) / startPrice * BigDecimal(100)).setScale(2, BigDecimal.ROUND_HALF_UP)
    logger.info("${System.currentTimeMillis() / 1000 - startTime} ${currentCandle.closeTime} : $backTestROI : $marketROI : $balanceByQuoteAsset : ${positionPair.first} : ${positionPair.second} : $baseAssetBalance : $quoteAssetBalance")
  }

  private fun getPositionChangeResult(previousPosition: Pair<Int, Int>, positionPair: Pair<Int, Int>): BigDecimal {
    val currentCandle = chart!!.getLastCandle()
    val currentBalanceTotal = getBalanceByQuoteAsset(currentCandle.closePrice)
    val ret = currentBalanceTotal * positionPair.first.toBigDecimal() / (positionPair.first + positionPair.second).toBigDecimal() / currentCandle.closePrice - baseAssetBalance
    return ret.setScale(8, BigDecimal.ROUND_DOWN)
  }

  private fun getBalanceByQuoteAsset(currentPrice: BigDecimal): BigDecimal {
    return baseAssetBalance * currentPrice + quoteAssetBalance
  }

  private fun evalShortTermTrendValue(chart: Chart): Double {
    return chart.getMergedCandleChart(shortTerm).slowStochsticValue(5,3).toDouble()
  }

  private fun evalMidTermTrendValue(chart: Chart): Double {
    return chart.getMergedCandleChart(midTerm).slowStochsticValue(5,3).toDouble()
  }

  private fun evalLongTermTrendValue(chart: Chart): Double {
    return chart.getMergedCandleChart(longTerm).slowStochsticValue(5,3).toDouble()
  }

  private fun getTrendFromValueVector(valueTriple: Triple<Double, Double, Double>): Triple<Int, Int, Int> {
    val shortThreshold = Pair(30.0, 70.0)
    val midThreshold = Pair(30.0, 70.0)
    val longThreshold = Pair(30.0, 70.0)

    return Triple(
        getTrendFromValueScalar(valueTriple.first, shortThreshold),
        getTrendFromValueScalar(valueTriple.second, midThreshold),
        getTrendFromValueScalar(valueTriple.third, longThreshold)
    )
  }

  private fun getTrendFromValueScalar(trendValue: Double, threshold: Pair<Double, Double>): Int {
    return when {
      threshold.first > trendValue && threshold.second > trendValue -> -1
      threshold.first < trendValue && threshold.second < trendValue -> -1
      else -> 0
    }
  }

}