package com.turbo.newton

import com.turbo.binance.BinanceMock
import com.turbo.binance.model.Candle
import com.turbo.newton.db.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import java.lang.Math.abs
import java.math.BigDecimal
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.math.abs

class BackTest {

  @Before
  fun setup() {
    val prop = Properties()
    prop.load(ClassLoader.getSystemResourceAsStream("application.properties"))
    DatabaseManager.connect(
        url = prop.getProperty("database.url"),
        user = prop.getProperty("database.user"),
        password = prop.getProperty("database.password"),
        driver = prop.getProperty("database.driver"),
        withClean = false
    )
  }

  @Test
  fun backTest() {
    runBlocking {
      var historyGroups = emptyList<HistoryGroup>()
      transaction {
        historyGroups = DatabaseManager.selectHistoryGroup()
      }
      val binanceMock = BinanceMock(
          historyGroupMap = mapOf("BTCUSDT" to historyGroups.single { it.description == "BTCUSDT" })
      )

      fun eventManager(): EventManager {
        return EventManager(
            stepMillis = 1,
            eventQueue = mutableListOf(mutableListOf(suspend { delay(1000) }))
        )
      }

      fun BTS(symbolStr: String, start: String, end: String, eventManager: EventManager): BackTestStrategy {
        return BackTestStrategy(
            binanceMock = binanceMock,
            backTestStartDateTime = ZonedDateTime.parse(start),
            backTestEndDateTime = ZonedDateTime.parse(end),
            symbolStr = symbolStr,
            shortTerm = Duration.ofMinutes(1),
            midTerm = Duration.ofMinutes(15),
            longTerm = Duration.ofMinutes(60),
            saveEvaluation = true,
            eventManager = eventManager
        )
      }

      val testCases = listOf(
          Pair(eventManager(), Triple("BTCUSDT", "2018-12-08T03:32+09:00[Asia/Seoul]", "2018-12-08T04:31:59.999+09:00[Asia/Seoul]")),
          Pair(eventManager(), Triple("BTCUSDT", "2019-02-24T22:32+09:00[Asia/Seoul]", "2019-02-24T23:31:59.999+09:00[Asia/Seoul]"))
      )

      testCases.forEach {
        it.first.bookFuture(1000, suspend { BTS(it.second.first, it.second.second, it.second.third, it.first).run() })
      }
      testCases.forEach {
        try {
          it.first.start()
        } catch (e: CancellationException) {
          System.out.println("Done")
        }
      }
    }
  }

  @Test
  fun searchTarget() {
    transaction {
      val candles = CandleHistory.find { CandleHistories.historyGroup eq 1 }.asSequence().toList()
      val mainChart = Chart(candles = candles.map { Candle.buildFromCandleHistory(it) }.toMutableList())
      val hourChart = mainChart.getMergedCandleChart(Duration.ofHours(1))
      val dayChart = mainChart.getMergedCandleChart(Duration.ofDays(1))

      fun getDiff(it: Candle): BigDecimal {
        return (it.highPrice - it.lowPrice) / it.lowPrice
      }
      hourChart.candles.filter { getDiff(it).abs() > BigDecimal(0.05) }
          .forEach { System.out.println("${it.openTime} | ${it.closeTime} | ${getDiff(it) * BigDecimal(100).setScale(2, BigDecimal.ROUND_DOWN)} | ${it.openPrice} | ${it.closePrice} | ${it.highPrice} | ${it.lowPrice}") }
      System.out.println("___________________________--------")
      dayChart.candles.filter { getDiff(it).abs() > BigDecimal(0.05) }
          .forEach { System.out.println("${it.openTime} | ${it.closeTime} | ${getDiff(it) * BigDecimal(100).setScale(2, BigDecimal.ROUND_DOWN)} | ${it.openPrice} | ${it.closePrice} | ${it.highPrice} | ${it.lowPrice}") }
    }
  }
}
