package com.turbo.newton

import com.turbo.binance.BinanceMock
import com.turbo.newton.db.DatabaseManager
import com.turbo.newton.db.HistoryGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

class BackTest {

  @Test
  fun backTest() {
    runBlocking {
      val prop = Properties()
      prop.load(ClassLoader.getSystemResourceAsStream("application.properties"))
      DatabaseManager.connect(
          url = prop.getProperty("database.url"),
          user = prop.getProperty("database.user"),
          password = prop.getProperty("database.password"),
          driver = prop.getProperty("database.driver"),
          withClean = false
      )

      val eventQueue = mutableListOf(
          mutableListOf(suspend { delay(1000) })
      )
      val eventManager = EventManager(
          stepMillis = 1,
          eventQueue = eventQueue
      )
      var historyGroups = emptyList<HistoryGroup>()
      transaction {
        historyGroups = DatabaseManager.selectHistoryGroup()
      }
      val binanceMock = BinanceMock(
          historyGroupMap = mapOf("BTCUSDT" to historyGroups.single { it.description == "BTCUSDT" })
      )
      val backTestStrategy = BackTestStrategy(
          binanceMock = binanceMock,
          backTestStartDateTime = ZonedDateTime.of(2019, 2, 1, 0, 0, 0, 0, ZoneId.systemDefault()),
          symbolStr = "BTCUSDT",
          shortTerm = Duration.ofMinutes(1),
          midTerm = Duration.ofMinutes(15),
          longTerm = Duration.ofMinutes(60),
          eventManager = eventManager
      )

      eventManager.bookFuture(1000, suspend { backTestStrategy.run() })
      eventManager.start()
    }
  }
}
