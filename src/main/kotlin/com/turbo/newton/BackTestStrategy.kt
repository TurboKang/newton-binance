package com.turbo.newton

import com.turbo.binance.BinanceClient
import com.turbo.binance.BinanceMock
import com.turbo.binance.enum.CandleIntervalEnum
import com.turbo.binance.model.Symbol
import com.turbo.newton.db.DatabaseManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class BackTestStrategy(
    private val binanceMock: BinanceMock,
    private val symbolStr: String,
    private val backTestStartDateTime: ZonedDateTime,
    private val backTestEndDateTime: ZonedDateTime,
    private val shortTerm: Duration = Duration.ofMinutes(1),
    private val midTerm: Duration = Duration.ofMinutes(15),
    private val longTerm: Duration = Duration.ofMinutes(60),
    private val saveEvaluation:Boolean,
    private val eventManager: EventManager
) {
  companion object {
    val logger = LoggerFactory.getLogger(BackTestStrategy::class.java)
  }

  val testId: String = UUID.randomUUID().toString()
  var index = 0
  var indexEnd = 0
  var chart: Chart? = null
  var smlTrendValue = Triple(0.0, 0.0, 0.0)

  var quoteAssetBalance = BigDecimal(1000).setScale(8, BigDecimal.ROUND_DOWN)
  var baseAssetBalance = BigDecimal.ZERO.setScale(8, BigDecimal.ROUND_DOWN)

  var positionPair = Pair(0, 10)

  var startTime = System.currentTimeMillis() / 1000
  var startAsset = BigDecimal.ZERO
  var startPrice = BigDecimal.ZERO

  var evaluations = mutableListOf<EvaluationDataClass>()

  suspend fun run() {
    val candles = binanceMock.getCandles(
        symbolStr = symbolStr,
        firstCandleOpenZonedDateTime = backTestStartDateTime.minusDays(15),
        lastCandleOpenZonedDateTime = backTestEndDateTime,
        interval = CandleIntervalEnum.MINUTE_1,
        limit = 10000
    ).filter { it.closeTime < backTestEndDateTime }

    chart = Chart(candles.sortedBy { it.openTime }.toMutableList())
    index = chart!!.candles.filter { it.openTime < backTestStartDateTime }.size
    indexEnd = chart!!.candles.filter { it.openTime < backTestEndDateTime }.size

    startPrice = chart!!.candles[index].closePrice
    startAsset = baseAssetBalance * startPrice + quoteAssetBalance
    checkTrendAndSetPosition()
  }

  suspend fun checkTrendAndSetPosition() {
    val previousTrend = getTrendFromValueVector(smlTrendValue)
    smlTrendValue = Triple(evalShortTermTrendValue(chart!!, index), evalMidTermTrendValue(chart!!, index), evalLongTermTrendValue(chart!!, index))
    val currentTrend = getTrendFromValueVector(smlTrendValue)
    if(currentTrend != previousTrend) {
//      logger.info("${candles.last().closeTime} - $currentTrend")
      setPosition(currentTrend)
    }

    if(true || chart!!.candles[index].openTime.minute == 0) {
        evaluate()
    }

    val currentTime = chart!!.candles[index].openTime
    if(currentTime.monthValue == 2 && currentTime.dayOfMonth == 9 && currentTime.hour == 1 && currentTime.minute == 0) {
      System.out.println("Here")
    }
    if(currentTime.minute == 0 && currentTime.hour == 0 && currentTime.dayOfWeek.value == 1) {
      if(saveEvaluation) {
        transaction {
          evaluations.forEach {
            DatabaseManager.insertEvaluation(
                _testId = testId,
                _openTime = it.openTime,
                _closeTime = it.closeTime,
                _myReturn = it.myReturn,
                _marketReturn = it.marketReturn,
                _price = it.price,
                _totalBalance = it.totalBalance,
                _basePosition = it.basePosition,
                _quotePosition = it.quotePosition,
                _baseBalance = it.baseBalance,
                _quoteBalance = it.quoteBalance
            )
          }
        }
        evaluations = mutableListOf()
      }
    }
    index++
    if(index < indexEnd) {
      eventManager.bookFuture(0, suspend { checkTrendAndSetPosition() })
    } else {
      done()
    }
  }

  private fun done() {
    if(evaluations.isNotEmpty()) {
      if(saveEvaluation) {
        transaction {
          evaluations.forEach {
            DatabaseManager.insertEvaluation(
                _testId = testId,
                _openTime = it.openTime,
                _closeTime = it.closeTime,
                _myReturn = it.myReturn,
                _marketReturn = it.marketReturn,
                _price = it.price,
                _totalBalance = it.totalBalance,
                _basePosition = it.basePosition,
                _quotePosition = it.quotePosition,
                _baseBalance = it.baseBalance,
                _quoteBalance = it.quoteBalance
            )
          }
        }
      }
    }
    logger.info("$symbolStr | $backTestStartDateTime | $backTestEndDateTime | ${evaluations.last().myReturn} | ${evaluations.last().marketReturn}")
    eventManager.poison()
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
      val currentCandle = chart!!.candles[index]
      val currentPrice = currentCandle.closePrice

      baseAssetBalance = (baseAssetBalance + baseAssetDifference).setScale(8, BigDecimal.ROUND_DOWN)
      quoteAssetBalance = (quoteAssetBalance - baseAssetDifference * currentPrice).setScale(8, BigDecimal.ROUND_DOWN)
    }
  }

  private fun evaluate() {
    val currentCandle = chart!!.candles[index]
    val currentPrice = currentCandle.closePrice
    val balanceByQuoteAsset = getBalanceByQuoteAsset(currentPrice)
    val backTestROI = ((balanceByQuoteAsset-startAsset) / startAsset * BigDecimal(100)).setScale(2, BigDecimal.ROUND_HALF_UP)
    val marketROI = ((currentPrice - startPrice) / startPrice * BigDecimal(100)).setScale(2, BigDecimal.ROUND_HALF_UP)

    if(saveEvaluation) {
      evaluations.add(EvaluationDataClass(
          openTime = currentCandle.openTime,
          closeTime = currentCandle.closeTime,
          myReturn = backTestROI,
          marketReturn = marketROI,
          price = currentPrice,
          totalBalance = balanceByQuoteAsset,
          basePosition = positionPair.first,
          quotePosition = positionPair.second,
          baseBalance = baseAssetBalance,
          quoteBalance = quoteAssetBalance
      ))
    }
  }

  private fun getPositionChangeResult(previousPosition: Pair<Int, Int>, positionPair: Pair<Int, Int>): BigDecimal {
    val currentCandle = chart!!.candles[index]
    val currentBalanceTotal = getBalanceByQuoteAsset(currentCandle.closePrice)
    val ret = currentBalanceTotal * positionPair.first.toBigDecimal() / (positionPair.first + positionPair.second).toBigDecimal() / currentCandle.closePrice - baseAssetBalance
    return ret.setScale(8, BigDecimal.ROUND_DOWN)
  }

  private fun getBalanceByQuoteAsset(currentPrice: BigDecimal): BigDecimal {
    return baseAssetBalance * currentPrice + quoteAssetBalance
  }

  private fun evalShortTermTrendValue(chart: Chart, endIndexExclusive: Int): Double {
    val barCount = 5
    val movingAverageSize = 3
    return chart.getMergedCandleChart(shortTerm, endIndexExclusive, barCount+movingAverageSize+2).slowStochsticValue(5,3).toDouble()
  }

  private fun evalMidTermTrendValue(chart: Chart, endIndexExclusive: Int): Double {
    val barCount = 5
    val movingAverageSize = 3
    return chart.getMergedCandleChart(midTerm, endIndexExclusive, barCount+movingAverageSize+2).slowStochsticValue(5,3).toDouble()
  }

  private fun evalLongTermTrendValue(chart: Chart, endIndexExclusive: Int): Double {
    val barCount = 5
    val movingAverageSize = 3
    return chart.getMergedCandleChart(longTerm, endIndexExclusive, barCount+movingAverageSize+2).slowStochsticValue(5,3).toDouble()
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
      threshold.first < trendValue && threshold.second < trendValue -> +1
      else -> 0
    }
  }

  data class EvaluationDataClass(
      val openTime: ZonedDateTime,
      val closeTime: ZonedDateTime,
      val myReturn: BigDecimal,
      val marketReturn: BigDecimal,
      val price: BigDecimal,
      val totalBalance: BigDecimal,
      val baseBalance: BigDecimal,
      val quoteBalance: BigDecimal,
      val basePosition: Int,
      val quotePosition: Int
  )

}