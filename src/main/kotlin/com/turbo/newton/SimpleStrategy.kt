package com.turbo.newton

import com.turbo.binance.BinanceClient
import com.turbo.binance.BinanceMock
import com.turbo.binance.enum.CandleIntervalEnum
import com.turbo.binance.enum.OrderSideEnum
import com.turbo.binance.model.Candle
import com.turbo.binance.model.Symbol
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

class SimpleStrategy(
    private val binanceClient: BinanceClient,
    private val symbolStr: String,
    private val backTestStartDateTime: ZonedDateTime,
    private val backTestEndDateTime: ZonedDateTime,
    private val tradingTerm: Duration = Duration.ofMinutes(1),
    private val evaluationTerm: Duration = Duration.ofDays(1),
    private val shortTerm: Duration = Duration.ofMinutes(1),
    private val midTerm: Duration = Duration.ofMinutes(15),
    private val longTerm: Duration = Duration.ofMinutes(60),
    private val shortTermTrendThreshold: Pair<Double, Double> = Pair(20.0, 80.0),
    private val midTermTrendThreshold: Pair<Double, Double> = Pair(20.0, 80.0),
    private val longTermTrendThreshold: Pair<Double, Double> = Pair(20.0, 80.0)
) {
  companion object {
    val logger = LoggerFactory.getLogger(SimpleStrategy::class.java)
  }

  val testId = UUID.randomUUID().toString()
  val evaluationPerTrading: Int
  val trader: Trader
  val startPrice: BigDecimal

  init {
    val minimumPast = Duration.ofDays(15)
    evaluationPerTrading = 100

    val (price, bidAskPair, balancePair) = runBlocking {
      val price = binanceClient.getPriceTickerOfSymbol(symbolStr).price
      val bidAskPair = binanceClient.getBookTickerOfSymbol(symbolStr).let { it.bidPrice to it.askPrice }
      val balancePair = binanceClient.getAccountInfo().let {
        val base = it.balances.single { it.asset == "BTC" }.free
        val quote = it.balances.single { it.asset == "USDT" }.free
        base to quote
      }

      Triple(price, bidAskPair, balancePair)
    }

    startPrice = price
    val seedQuote = balancePair.first * price + balancePair.second
    val quotePosition = (BigDecimal.TEN * balancePair.second.divide(seedQuote, 8, BigDecimal.ROUND_HALF_UP)).toBigIntegerExact().toInt()
    val basePosition = 10 - quotePosition
    trader = Trader(
        seedQuote = seedQuote,
        currentPrice = price,
        basePosition = basePosition,
        quotePosition = quotePosition,
        baseBalance = balancePair.first,
        quoteBalance = balancePair.second,
        bidPrice = bidAskPair.first,
        askPrice = bidAskPair.second
    )
  }

  private fun getStep(duration: Duration): Int {
    return (duration.toNanos() / CandleIntervalEnum.MINUTE_1.toDuration().toNanos()).toInt()
  }

  fun run() {
    (1..Int.MAX_VALUE).fold(trader) { trader, index ->
      val (candles, bookTicker) = runBlocking {
        val candles = binanceClient.getCandles(
            symbolStr = symbolStr,
            interval = CandleIntervalEnum.MINUTE_1,
            limit = 10000,
            firstCandleOpenZonedDateTime = null,
            lastCandleOpenZonedDateTime = null
        )
        val bookTicker = binanceClient.getBookTickerOfSymbol(symbolStr)

        candles to bookTicker
      }
      val (previousBasePosition, previousQuotePosition) = trader.basePosition to trader.quotePosition
      val (basePosition, quotePosition) = getPosition(candles)
      val currentCandle = candles.last()
      val newTrader = if(Fraction(previousBasePosition, previousBasePosition + previousQuotePosition) == Fraction(basePosition, basePosition + quotePosition)) {
        Trader(
            basePosition = basePosition,
            quotePosition = quotePosition,
            baseBalance = trader.baseBalance,
            quoteBalance = trader.quoteBalance,
            currentPrice = currentCandle.closePrice,
            seedQuote = trader.seedQuote,
            askPrice = bookTicker.askPrice,
            bidPrice = bookTicker.bidPrice
        )
      } else {
        val tempTrader = Trader(
            basePosition = basePosition,
            quotePosition = quotePosition,
            baseBalance = trader.baseBalance,
            quoteBalance = trader.quoteBalance,
            currentPrice = currentCandle.closePrice,
            seedQuote = trader.seedQuote,
            askPrice = bookTicker.askPrice,
            bidPrice = bookTicker.bidPrice
        )
        val baseBalanceToBE = BigFraction(basePosition, (basePosition + quotePosition))
            .multiply(tempTrader.totalByQuote).divide(tempTrader.currentPrice, 8, BigDecimal.ROUND_DOWN)

        val baseToBuy = baseBalanceToBE - tempTrader.baseBalance

        val (baseBalanceAfterTrade, quoteBalanceAfterTrade) = runBlocking {
          if(baseToBuy > BigDecimal.ZERO) {
            val (price, quantity) = binanceClient.sendSimpleOrder(
                symbolStr = symbolStr,
                side = OrderSideEnum.BUY,
                quantity = baseToBuy
            )
            trader.baseBalance + quantity to trader.quoteBalance - price*quantity
          } else {
            val (price, quantity) = binanceClient.sendSimpleOrder(
                symbolStr = symbolStr,
                side = OrderSideEnum.SELL,
                quantity = baseToBuy.negate()
            )
            trader.baseBalance - quantity to trader.quoteBalance + price*quantity
          }
        }

        Trader(
            basePosition = basePosition,
            quotePosition = quotePosition,
            baseBalance = baseBalanceAfterTrade,
            quoteBalance = quoteBalanceAfterTrade,
            currentPrice = currentCandle.closePrice,
            seedQuote = trader.seedQuote,
            askPrice = bookTicker.askPrice,
            bidPrice = bookTicker.bidPrice
        )
      }

      if (index % evaluationPerTrading == 0) {
        evaluate(currentCandle.openTime, currentCandle.closeTime, newTrader)
      }

      newTrader
    }
  }

  private fun getPosition(candles: List<Candle>): Pair<Int, Int> {
    val candleIndex = candles.size - 1
    val shortTermTrendValue = evalShortTermTrendValue(Chart(candles.toMutableList()), candleIndex)
    val midTermTrendValue = evalMidTermTrendValue(Chart(candles.toMutableList()), candleIndex)
    val longTermTrendValue = evalLongTermTrendValue(Chart(candles.toMutableList()), candleIndex)

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

  private fun getTrendSignal(trendValue: Double, threshold: Pair<Double, Double>): Int {
    return when {
      threshold.first > trendValue && threshold.second > trendValue -> -1
      threshold.first < trendValue && threshold.second < trendValue -> +1
      else -> 0
    }
  }

  data class Trader(
      val seedQuote: BigDecimal,
      val basePosition: Int,
      val quotePosition: Int,
      val baseBalance: BigDecimal,
      val quoteBalance: BigDecimal,
      val currentPrice: BigDecimal,
      val bidPrice: BigDecimal,
      val askPrice: BigDecimal
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
  }

}