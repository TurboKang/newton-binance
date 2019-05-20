package com.turbo
import com.turbo.binance.BinanceClient
import com.turbo.binance.model.Symbol
import com.turbo.newton.EventManager
import com.turbo.newton.RsiStrategy
import com.turbo.newton.db.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.LocalDateTime
import java.math.BigDecimal
import java.util.*

fun main() = runBlocking {
  val prop = Properties()
  prop.load(ClassLoader.getSystemResourceAsStream("application.properties"))
  val binanceClient = BinanceClient(
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

  transaction {
    val hg = HistoryGroup.new {
      quoteAsset = "BTC"
      baseAsset = "USDT"
      description = "testtest"
      registeredDatetime = DateTime.now()
    }

    CandleHistory.new {
      historyGroup = hg
      openTime = DateTime.now().minusMinutes(1)
      closeTime = DateTime.now()
      highPrice = BigDecimal(1000.12345678)
      lowPrice = BigDecimal(400.12345678)
      openPrice = BigDecimal(500)
      closePrice = BigDecimal(700)
      volume = BigDecimal(100000)
      quoteAssetVolume = BigDecimal(200000)
      numberOfTrades = 30512
      takerBuyBaseAssetVolume = BigDecimal(0.12345678)
      takerBuyQuoteAssetVolume = BigDecimal(0.12345678)
      ignore = BigDecimal.ZERO
    }
    CandleHistory.new {
      historyGroup = hg
      openTime = DateTime.now().minusMinutes(1)
      closeTime = DateTime.now()
      highPrice = BigDecimal(1000.12345678)
      lowPrice = BigDecimal(400.12345678)
      openPrice = BigDecimal(500)
      closePrice = BigDecimal(700)
      volume = BigDecimal(100000)
      quoteAssetVolume = BigDecimal(200000)
      numberOfTrades = 30512
      takerBuyBaseAssetVolume = BigDecimal(0.12345678)
      takerBuyQuoteAssetVolume = BigDecimal(0.12345678)
      ignore = BigDecimal.ZERO
    }

    val candles = CandleHistory.find { CandleHistories.historyGroup eq hg.id }
    candles.iterator().forEach {
      System.out.println(it)
    }
  }

  System.out.println(binanceClient.getServerTime() - System.currentTimeMillis())
  val exchangeInt = binanceClient.getExchangeInfo()
  val accountInfo = binanceClient.getAccountInfo()

  val balance_BCC = accountInfo.balances.find { it.asset == "BCC" }
  System.out.println(balance_BCC)
  val symbol_BCCBTC = exchangeInt.symbols.find { it.symbol == "ETHBTC" }!!
  System.out.println(symbol_BCCBTC)

  val eventQueue = mutableListOf(
      mutableListOf( suspend { delay(1000) })
  )
  val eventManager = EventManager(
      stepMillis = 1000,
      eventQueue = eventQueue
  )

  val rsiStrategy1 = RsiStrategy(
      binanceClient = binanceClient,
      eventManager = eventManager,
      symbol = symbol_BCCBTC
  )

  rsiStrategy1.run()

  eventManager.start()
}

