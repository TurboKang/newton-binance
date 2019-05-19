package com.turbo.newton.db

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.CurrentDateTime
import org.joda.time.DateTime
import java.math.BigDecimal

object HistoryGroups : IntIdTable() {
  val quoteAsset: Column<String> = varchar(name = "quoteAsset", collate = "utf8_general_ci", length = 5)
  val baseAsset: Column<String> = varchar(name = "baseAsset", collate = "utf8_general_ci", length = 5)
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