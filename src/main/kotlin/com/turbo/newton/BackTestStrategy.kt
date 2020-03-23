package com.turbo.newton

import com.turbo.binance.BinanceMock
import com.turbo.binance.enum.CandleIntervalEnum
import com.turbo.multiply
import com.turbo.newton.db.DatabaseManager
import kotlinx.coroutines.runBlocking
import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.fraction.Fraction
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

class BackTestStrategy(
    private val binanceMock: BinanceMock,
    private val symbolStr: String,
    private val backTestStartDateTime: ZonedDateTime,
    private val backTestEndDateTime: ZonedDateTime,
    private val tradingTerm: Duration = Duration.ofMinutes(1),
    private val evaluationTerm: Duration = Duration.ofDays(1),
    private val shortTerm: Duration = Duration.ofMinutes(1),
    private val midTerm: Duration = Duration.ofMinutes(15),
    private val longTerm: Duration = Duration.ofMinutes(60),
    private val seedQuote: BigDecimal = BigDecimal(10000),
    private val shortTermTrendThreshold: Pair<Double, Double> = Pair(20.0, 80.0),
    private val midTermTrendThreshold: Pair<Double, Double> = Pair(20.0, 80.0),
    private val longTermTrendThreshold: Pair<Double, Double> = Pair(20.0, 80.0)
) {
  companion object {
    val logger = LoggerFactory.getLogger(BackTestStrategy::class.java)
  }

  val testId = UUID.randomUUID().toString()
  val chart: Chart
  val startIndex: Int
  val endIndex: Int
  val tradingStep: Int
  val evaluationPerTrading: Int
  val trader: Trader
  val startPrice:  BigDecimal

  init {
    val minimumPast = Duration.ofDays(15)
    val candles = runBlocking {
      binanceMock.getCandles(
          symbolStr = symbolStr,
          firstCandleOpenZonedDateTime = backTestStartDateTime.minus(minimumPast),
          lastCandleOpenZonedDateTime = backTestEndDateTime.plus(CandleIntervalEnum.MINUTE_1.toDuration()),
          interval = CandleIntervalEnum.MINUTE_1,
          limit = 10000
      ).filter { it.closeTime < backTestEndDateTime }
    }
    chart = Chart(candles.sortedBy { it.openTime }.toMutableList())
    startIndex = getStep(minimumPast)
    endIndex = startIndex + getStep(Duration.between(backTestStartDateTime, backTestEndDateTime))
    tradingStep = getStep(tradingTerm)
    evaluationPerTrading = getStep(evaluationTerm) / tradingStep
    startPrice = candles.get(startIndex).openPrice

    trader = Trader(
        seedQuote = seedQuote,
        currentPrice = startPrice,
        basePosition = 0,
        quotePosition = 10,
        baseBalance = BigDecimal.ZERO,
        quoteBalance = seedQuote
    )
  }

  private fun getStep(duration: Duration): Int {
    return (duration.toNanos() / CandleIntervalEnum.MINUTE_1.toDuration().toNanos()).toInt()
  }

  fun run() {
    (startIndex until endIndex step tradingStep).foldIndexed(trader) { seq, trader, candleIndex ->
      val candle = chart.candles[candleIndex]
      val (basePosition, quotePosition) = getPosition(candleIndex)
      val newTrader = trader.trade(candle.closePrice, basePosition, quotePosition)

      if(seq % evaluationPerTrading == 0) {
        evaluate(candle.openTime, candle.closeTime, newTrader)
      }
      logger.info(newTrader.toString())
      newTrader
    }
  }

  private fun getPosition(candleIndex: Int): Pair<Int, Int> {
    val shortTermTrendValue = evalShortTermTrendValue(candleIndex)
    val midTermTrendValue = evalMidTermTrendValue(candleIndex)
    val longTermTrendValue = evalLongTermTrendValue(candleIndex)

    val shortTermSignal = getTrendSignal(shortTermTrendValue, shortTermTrendThreshold)
    val midTermSignal = getTrendSignal(midTermTrendValue, midTermTrendThreshold)
    val longTermSignal = getTrendSignal(longTermTrendValue, longTermTrendThreshold)

    return when(Triple(shortTermSignal, midTermSignal, longTermSignal)) {
      Triple(-1, -1, -1) -> 10 to 0
      Triple(-1, -1, 0) -> 10 to 0
      Triple(-1, -1, 1) -> 9 to 1
      Triple(-1, 0, -1) -> 9 to 1
      Triple(-1, 0, 0) -> 9 to 1
      Triple(-1, 0, 1) -> 8 to 2
      Triple(-1, 1, -1) -> 8 to 2
      Triple(-1, 1, 0) -> 7 to 3
      Triple(-1, 1, 1) -> 6 to 4
      Triple(0, -1, -1) -> 5 to 5
      Triple(0, -1, 0) -> 5 to 5
      Triple(0, -1, 1) -> 5 to 5
      Triple(0, 0, -1) -> 5 to 5
      Triple(0, 0, 0) -> 5 to 5
      Triple(0, 0, 1) -> 5 to 5
      Triple(0, 1, -1) -> 5 to 5
      Triple(0, 1, 0) -> 5 to 5
      Triple(0, 1, 1) -> 5 to 5
      Triple(1, -1, -1) -> 4 to 6
      Triple(1, -1, 0) -> 3 to 7
      Triple(1, -1, 1) -> 2 to 8
      Triple(1, 0, -1) -> 2 to 8
      Triple(1, 0, 0) -> 1 to 9
      Triple(1, 0, 1) -> 1 to 9
      Triple(1, 1, -1) -> 1 to 9
      Triple(1, 1, 0) -> 0 to 10
      Triple(1, 1, 1) -> 0 to 10

      else -> throw Exception()
    }
  }

  private fun evaluate(openTime: ZonedDateTime, closeTime: ZonedDateTime, trader: Trader) {
    transaction {
      DatabaseManager.insertEvaluation(
          _testId = testId,
          _openTime = openTime,
          _closeTime = closeTime,
          _price = trader.currentPrice,
          _myReturn = trader.roi,
          _marketReturn = (trader.currentPrice * 100.toBigDecimal() / startPrice).setScale(2, BigDecimal.ROUND_DOWN),
          _totalBalance = trader.totalByQuote,
          _basePosition = trader.basePosition,
          _quotePosition = trader.quotePosition,
          _baseBalance = trader.baseBalance,
          _quoteBalance = trader.quoteBalance
      )
    }
  }

  private fun evalShortTermTrendValue(endIndexExclusive: Int): Double {
    val barCount = 5
    val movingAverageSize = 3
    return chart.getMergedCandleChart(shortTerm, endIndexExclusive, barCount+movingAverageSize+2).slowStochsticValue(5,3).toDouble()
  }

  private fun evalMidTermTrendValue(endIndexExclusive: Int): Double {
    val barCount = 5
    val movingAverageSize = 3
    return chart.getMergedCandleChart(midTerm, endIndexExclusive, barCount+movingAverageSize+2).slowStochsticValue(5,3).toDouble()
  }

  private fun evalLongTermTrendValue(endIndexExclusive: Int): Double {
    val barCount = 5
    val movingAverageSize = 3
    return chart.getMergedCandleChart(longTerm, endIndexExclusive, barCount+movingAverageSize+2).slowStochsticValue(5,3).toDouble()
  }

  private fun getTrendSignal(trendValue: Double, threshold: Pair<Double, Double>): Int {
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

  data class Trader(
      val seedQuote: BigDecimal,
      val basePosition: Int,
      val quotePosition: Int,
      val baseBalance: BigDecimal,
      val quoteBalance: BigDecimal,
      val currentPrice: BigDecimal
  ) {
    val baseByQuote: BigDecimal get() {
      return baseBalance * currentPrice
    }

    val totalByQuote: BigDecimal get() {
      return baseByQuote + quoteBalance
    }

    val roi: BigDecimal get() {
      return (totalByQuote * 100.toBigDecimal() / seedQuote).setScale(2, BigDecimal.ROUND_DOWN)
    }

    fun trade(newPrice: BigDecimal, newBasePosition: Int, newQuotePosition: Int): Trader {
      val traderBeforeTrade = Trader(
          seedQuote = seedQuote,
          basePosition = basePosition,
          quotePosition = quotePosition,
          baseBalance = baseBalance,
          quoteBalance = quoteBalance,
          currentPrice = newPrice
      )

      if(Fraction(basePosition, basePosition + quotePosition) == Fraction(newBasePosition, newBasePosition + newQuotePosition)) {
        return traderBeforeTrade
      } else {
        val baseBalanceToBE = BigFraction(newBasePosition, (newBasePosition + newQuotePosition)).multiply(traderBeforeTrade.totalByQuote).divide(newPrice, 8, BigDecimal.ROUND_DOWN)
        val baseToBuy = baseBalanceToBE - baseBalance
        val quoteToPay = baseToBuy * newPrice

        return Trader(
            seedQuote = seedQuote,
            basePosition = newBasePosition,
            quotePosition = newQuotePosition,
            baseBalance = baseBalanceToBE,
            quoteBalance = quoteBalance - quoteToPay,
            currentPrice = newPrice
        )
      }

    }
  }

}