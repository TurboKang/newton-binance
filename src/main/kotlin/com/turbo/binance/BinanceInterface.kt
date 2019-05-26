package com.turbo.binance

import com.turbo.binance.enum.CandleIntervalEnum
import com.turbo.binance.model.Candle
import java.time.ZonedDateTime

interface BinanceInterface {

  suspend fun getCandles(symbolStr: String, interval: CandleIntervalEnum, limit: Int, firstCandleOpenZonedDateTime: ZonedDateTime?, lastCandleOpenZonedDateTime: ZonedDateTime?): List<Candle>
}