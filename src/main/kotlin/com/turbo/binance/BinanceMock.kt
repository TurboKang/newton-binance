package com.turbo.binance

import com.turbo.binance.enum.CandleIntervalEnum
import com.turbo.binance.model.Candle
import com.turbo.newton.db.DatabaseManager
import com.turbo.newton.db.HistoryGroup
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

class BinanceMock(
    private val historyGroupMap: Map<String, HistoryGroup>
):BinanceInterface {
  companion object {
    private val logger = LoggerFactory.getLogger(BinanceMock::class.java)
  }

  override suspend fun getCandles(symbolStr: String, interval: CandleIntervalEnum, limit: Int, firstCandleOpenZonedDateTime: ZonedDateTime?, lastCandleOpenZonedDateTime: ZonedDateTime?): List<Candle> {
    val a = System.currentTimeMillis()
    val historyGroup = historyGroupMap[symbolStr]!!
    var candles: List<Candle> = emptyList()
    transaction {
      val (confirmedStart, confirmedEnd) = when {
        firstCandleOpenZonedDateTime != null && lastCandleOpenZonedDateTime != null -> {
          Pair(firstCandleOpenZonedDateTime, lastCandleOpenZonedDateTime)
        }
        firstCandleOpenZonedDateTime == null && lastCandleOpenZonedDateTime != null ->
          Pair(lastCandleOpenZonedDateTime.minus(interval.toDuration().multipliedBy(limit.toLong() + 1)), lastCandleOpenZonedDateTime)
        firstCandleOpenZonedDateTime != null && lastCandleOpenZonedDateTime == null ->
          Pair(firstCandleOpenZonedDateTime, firstCandleOpenZonedDateTime.plus(interval.toDuration().multipliedBy(limit.toLong() + 1)))
        else ->
          Pair(ZonedDateTime.now().minusMinutes(limit.toLong() + 1), ZonedDateTime.now()!!)
      }
      candles = DatabaseManager.selectCandleHistoryByHistoryGroupAndDuration(historyGroup.id.value, confirmedStart, confirmedEnd)
    }
//    System.out.println("Query: " + (System.currentTimeMillis() - a))
    return candles
  }
}