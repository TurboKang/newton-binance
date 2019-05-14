package com.turbo.newton

import com.turbo.binance.BinanceClient
import com.turbo.binance.enum.CandleIntervalEnum
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RsiStrategy(
        private val binanceClient: BinanceClient,
        private val eventManager: EventManager
) {
    companion object {
        val logger = LoggerFactory.getLogger(RsiStrategy::class.java)
    }

    suspend fun run() {
        eventManager.bookFuture(1, suspend { queryCandle() })
    }

    private suspend fun queryCandle() {
        val candles = binanceClient.getCandles(
                symbolStr = "BTCUSDT",
                firstCandleOpenZonedDateTime = ZonedDateTime.of(LocalDateTime.now().minusMinutes(90), ZoneId.systemDefault()),
                lastCandleOpenZonedDateTime = ZonedDateTime.of(LocalDateTime.now(), ZoneId.systemDefault()),
                interval = CandleIntervalEnum.MINUTE_3,
                limit = 30
        )
        System.out.println(candles)
        eventManager.bookFuture(2000, suspend { queryCandle() })
    }
}