package com.turbo.newton

import com.turbo.binance.BinanceClient
import com.turbo.binance.enum.CandleIntervalEnum
import com.turbo.newton.db.DatabaseManager
import com.turbo.newton.db.HistoryGroup
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class Migration {
  private lateinit var binanceClient : BinanceClient

  companion object {
    private val logger = LoggerFactory.getLogger(Migration::class.java)
  }

  @Before
  fun setupClient() {
    val prop = Properties()
    prop.load(ClassLoader.getSystemResourceAsStream("application.properties"))
    binanceClient =  BinanceClient(
        domain = prop.getProperty("binance.domain"),
        apiKey = prop.getProperty("binance.key"),
        apiSecret = prop.getProperty("binance.secret")
    )
    DatabaseManager.connect(
        url = prop.getProperty("database.url"),
        user = prop.getProperty("database.user"),
        password = prop.getProperty("database.password"),
        driver = prop.getProperty("database.driver"),
        withClean = true
    )
  }

  @Test
  fun fetchDataFromBinance() {
    val fetchDataFrom = ZonedDateTime.of(2019, 1, 1, 0, 0 ,0, 0, ZoneId.systemDefault())
    val fetchDataTo = ZonedDateTime.now()
    val minutes = ChronoUnit.MINUTES.between(fetchDataFrom, fetchDataTo)
    val fetchCount = minutes / 1000 + 1
    val markets = listOf(Pair("BTC","USDT"),
        Pair("BNB","USDT"),
        Pair("ETH","USDT"),
        Pair("LTC","USDT"),
        Pair("MATIC","USDT"),
        Pair("BCHABC","USDT"),
        Pair("EOS","USDT"),
        Pair("XRP","USDT"),
        Pair("TFUEL","USDT"),
        Pair("BTT","USDT")
    )
    val historyGroups = mutableMapOf<String, HistoryGroup>()
    transaction {
      markets.forEach { market ->
        val symbol = "${market.first}${market.second}"
        historyGroups[symbol] = DatabaseManager.insertHistoryGroup(_baseAsset = market.first, _quoteAsset = market.second, _description = symbol)
      }
    }
    val indexChunk = (0..fetchCount).chunked(10)
    indexChunk.forEach { indexes ->
      transaction {
        runBlocking {
          coroutineScope {
            indexes.forEach { index ->
              val firstCandleOpenZonedDateTime = fetchDataTo.minusMinutes(1000 * (fetchCount - index))
              val lastCandleOpenZonedDateTime = fetchDataTo.minusMinutes(1000 * (fetchCount - index - 1))
              historyGroups.forEach { symbol, historyGroup ->
                launch {
                  val candles = binanceClient.getCandles(
                      symbolStr = symbol,
                      firstCandleOpenZonedDateTime =  firstCandleOpenZonedDateTime,
                      lastCandleOpenZonedDateTime = lastCandleOpenZonedDateTime,
                      interval = CandleIntervalEnum.MINUTE_1,
                      limit = 1000
                  )
                  candles.forEach { candle -> DatabaseManager.insertCandleHistory(historyGroup, candle) }
                }
              }
              logger.info("migrate : $firstCandleOpenZonedDateTime")
              delay(500)
            }
          }
        }
      }
    }
  }
}