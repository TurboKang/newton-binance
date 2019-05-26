package com.turbo.newton.db

import com.turbo.binance.model.Candle
import com.turbo.toJodaDateTime
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

object DatabaseManager {

  fun insertHistoryGroup(_quoteAsset: String, _baseAsset: String, _description: String): HistoryGroup {
    val hg = HistoryGroup.new {
      quoteAsset = _quoteAsset
      baseAsset = _baseAsset
      description = _description
      registeredDatetime = DateTime.now()
    }
    return hg
  }

  fun selectHistoryGroup(): List<HistoryGroup> {
    return HistoryGroup.all().asSequence().toList()
  }

  fun insertCandleHistory(hg: HistoryGroup, candle: Candle): CandleHistory {
    val candleHistory = CandleHistory.new {
      historyGroup = hg
      openTime = candle.openTime.toJodaDateTime()
      closeTime = candle.closeTime.toJodaDateTime()
      highPrice = candle.highPrice
      lowPrice = candle.lowPrice
      openPrice = candle.openPrice
      closePrice = candle.closePrice
      volume = candle.volume
      quoteAssetVolume = candle.quoteAssetVolume
      numberOfTrades = candle.numberOfTrades
      takerBuyBaseAssetVolume = candle.takerBuyBaseAssetVolume
      takerBuyQuoteAssetVolume = candle.takerBuyQuoteAssetVolume
      ignore = candle.ignore
    }
    return candleHistory
  }

  fun selectCandleHistoryByHistoryGroup(historyGroupId: Int): List<Candle> {
    val listOfCandleHistory = CandleHistory.find { CandleHistories.historyGroup eq historyGroupId }.sortedBy { it.openTime }.asSequence().toList()
    return listOfCandleHistory.map { Candle.buildFromCandleHistory(it) }
  }

  fun selectCandleHistoryByHistoryGroupAndDuration(historyGroupId: Int, startInclusive: ZonedDateTime, endExclusive: ZonedDateTime): List<Candle> {
    val listOfCandleHistory = CandleHistory.find {
      (CandleHistories.historyGroup eq historyGroupId) and
          (CandleHistories.openTime greaterEq startInclusive.toJodaDateTime()) and
          (CandleHistories.openTime less endExclusive.toJodaDateTime())
    }.sortedBy { it.openTime }.asSequence().toList()
    return listOfCandleHistory.map { Candle.buildFromCandleHistory(it) }
  }

  fun connect(url: String, user: String, password: String, driver: String, withClean: Boolean) {
    Database.connect(
        url = url,
        user = user,
        password = password,
        driver = driver
    )
    transaction {
      addLogger(StdOutSqlLogger)
      if(withClean) {
        clean()
      }
    }
  }
  private fun clean() {
    val conn = TransactionManager.current().connection
    val statement = conn.createStatement()
    statement.execute("SET FOREIGN_KEY_CHECKS = 0")

    val listOfTable = listOf(
        HistoryGroups,
        CandleHistories
    )

    listOfTable.forEach {table ->
      SchemaUtils.drop(table)
      SchemaUtils.create(table)
    }

    statement.execute("SET FOREIGN_KEY_CHECKS = 1")
  }
}

object HistoryGroups : IntIdTable() {
  val quoteAsset: Column<String> = varchar(name = "quoteAsset", collate = "utf8_general_ci", length = 20)
  val baseAsset: Column<String> = varchar(name = "baseAsset", collate = "utf8_general_ci", length = 20)
  val description: Column<String> = text(name = "description", collate = "utf8_general_ci")
  val registeredDatetime = datetime("registeredDatetime")
}

class HistoryGroup(id: EntityID<Int>): IntEntity(id) {
  companion object : IntEntityClass<HistoryGroup>(HistoryGroups)
  var quoteAsset by HistoryGroups.quoteAsset
  var baseAsset by HistoryGroups.baseAsset
  var description by HistoryGroups.description
  var registeredDatetime by HistoryGroups.registeredDatetime
}

object CandleHistories : IntIdTable() {
  val historyGroup = reference(name = "historyGroupId", foreign = HistoryGroups)
  val openTime: Column<DateTime> = datetime("openTime").index()
  val closeTime: Column<DateTime> = datetime("closeTime").index()
  val highPrice: Column<BigDecimal> = decimal(name = "highPrice", precision = 20, scale = 8)
  val lowPrice: Column<BigDecimal> = decimal(name = "lowPrice", precision = 20, scale = 8)
  val openPrice: Column<BigDecimal> = decimal(name = "openPrice", precision = 20, scale = 8)
  val closePrice: Column<BigDecimal> = decimal(name = "closePrice", precision = 20, scale = 8)
  val volume: Column<BigDecimal> = decimal(name = "volume", precision = 20, scale = 8)
  val quoteAssetVolume: Column<BigDecimal> = decimal(name = "quoteAssetVolume", precision = 20, scale = 8)
  val numberOfTrades: Column<Int> = integer(name = "numberOfTrades")
  val takerBuyBaseAssetVolume: Column<BigDecimal> = decimal(name = "takerBuyBaseAssetVolume", precision = 20, scale = 8)
  val takerBuyQuoteAssetVolume: Column<BigDecimal> = decimal(name = "takerBuyQuoteAssetVolume", precision = 20, scale = 8)
  val ignore: Column<BigDecimal> = decimal(name = "ignore", precision = 20, scale = 8)
}

class CandleHistory(id: EntityID<Int>): IntEntity(id) {
  companion object : IntEntityClass<CandleHistory>(CandleHistories)
  var historyGroup by HistoryGroup referencedOn CandleHistories.historyGroup
  var openTime by CandleHistories.openTime
  var closeTime by CandleHistories.closeTime
  var highPrice by CandleHistories.highPrice
  var lowPrice by CandleHistories.lowPrice
  var openPrice by CandleHistories.openPrice
  var closePrice by CandleHistories.closePrice
  var volume by CandleHistories.volume
  var quoteAssetVolume by CandleHistories.quoteAssetVolume
  var numberOfTrades by CandleHistories.numberOfTrades
  var takerBuyBaseAssetVolume by CandleHistories.takerBuyBaseAssetVolume
  var takerBuyQuoteAssetVolume by CandleHistories.takerBuyQuoteAssetVolume
  var ignore by CandleHistories.ignore
}