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
    var historyGroups = emptyList<HistoryGroup>()
    transaction {
      historyGroups = DatabaseManager.selectHistoryGroup()
    }
    val binanceMock = BinanceMock(
        historyGroupMap = mapOf("BTCUSDT" to historyGroups.single { it.description == "BTCUSDT" })
    )
    BackTestStrategy(
        binanceMock = binanceMock,
        symbolStr = "BTCUSDT",
        backTestStartDateTime = ZonedDateTime.of(2020,1,1,0,0,0, 0, ZoneId.systemDefault()),
        backTestEndDateTime = ZonedDateTime.of(2020,3,23,0,0,0, 0, ZoneId.systemDefault())
    ).run()
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
