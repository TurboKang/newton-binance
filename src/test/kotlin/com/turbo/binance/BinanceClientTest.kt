package com.turbo.binance

import com.turbo.binance.enum.CandleIntervalEnum
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

class BinanceClientTest {
    private lateinit var binanceClient : BinanceClient

    @Before
    fun setupClient() {
        val prop = Properties()
        prop.load(ClassLoader.getSystemResourceAsStream("application.properties"))
        binanceClient =  BinanceClient(
                domain = prop.getProperty("binance.domain"),
                apiKey = prop.getProperty("binance.key"),
                apiSecret = prop.getProperty("binance.secret")
        )
    }

    @Test
    fun ping() {
        runBlocking {
            binanceClient.ping()
        }
    }

    @Test
    fun getServerTime() {
        runBlocking {
            binanceClient.getServerTime()
        }
    }
    @Test
    fun getExchangeInfo() {
        runBlocking {
            Assert.assertNotNull(binanceClient.getExchangeInfo())
        }
    }
    @Test
    fun getDepthInfo() {
        runBlocking {
            val exchangeInfo = binanceClient.getExchangeInfo()
            val limit = 5
            val depth = binanceClient.getDepth(exchangeInfo.symbols[0].symbol, limit)
            Assert.assertNotNull(depth)
            Assert.assertEquals(depth.bids.size, limit)
            Assert.assertEquals(depth.asks.size, limit)
        }
    }

    @Test
    fun getTrades() {
        runBlocking {
            val exchangeInfo = binanceClient.getExchangeInfo()
            val limit = 600
            val trades = binanceClient.getTrades(exchangeInfo.symbols[0].symbol, limit)
            Assert.assertNotNull(trades)
            Assert.assertEquals(trades.size, limit)
        }
    }
    @Test
    fun getHistoricalTradesFromToRecent() {
        runBlocking {
            val exchangeInfo = binanceClient.getExchangeInfo()
            val limit = 600
            val trades = binanceClient.getTrades(exchangeInfo.symbols[0].symbol, limit)
            val historicalTrades = binanceClient.getHistoricalTradesFromToRecent(exchangeInfo.symbols[0].symbol, limit - 1, trades.first().id)
            Assert.assertNotNull(historicalTrades)
            Assert.assertEquals(historicalTrades.size, limit - 1)
            for (trade in historicalTrades) {
                Assert.assertTrue(trade.id >= trades.first().id)
            }
        }
    }
    @Test
    fun getAggregateTrades() {
        runBlocking {
            val exchangeInfo = binanceClient.getExchangeInfo()
            val limit = 500
            val aggrTrades = binanceClient.getAggregateTrade(
                    symbolStr = exchangeInfo.symbols[0].symbol,
                    startZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2018, 1, 1, 0, 0, 0), ZoneId.systemDefault()),
                    duration = Duration.ofHours(1L),
                    limit = limit
            )
            Assert.assertNotNull(aggrTrades)
            Assert.assertEquals(aggrTrades.size, limit)
        }
    }
    @Test
    fun getCandles() {
        runBlocking {
            val exchangeInfo = binanceClient.getExchangeInfo()
            val limit = 500
            val candles = binanceClient.getCandles(
                    symbolStr = exchangeInfo.symbols[0].symbol,
                    firstCandleOpenZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2018, 1, 1, 0, 0, 0), ZoneId.systemDefault()),
                    lastCandleOpenZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2018, 1, 1, 1, 0, 0), ZoneId.systemDefault()),
                    interval = CandleIntervalEnum.MINUTE_3,
                    limit = limit
            )
            Assert.assertNotNull(candles)
            Assert.assertTrue(candles.size <= limit)
        }
    }
    @Test
    fun get24HourStatOfOneSymbol() {
        runBlocking {
            val exchangeInfo = binanceClient.getExchangeInfo()
            val stat = binanceClient.get24HourStatOfSymbol(exchangeInfo.symbols[0].symbol)
            Assert.assertNotNull(stat)
        }
    }
    @Test
    fun get24HourStatOfEverySymbols() {
        runBlocking {
            val exchangeInfo = binanceClient.getExchangeInfo()
            val stats = binanceClient.get24HourStatOfEverySymbols()
            Assert.assertTrue(stats.isNotEmpty())
            Assert.assertEquals(exchangeInfo.symbols.size, stats.size)
        }
    }
    @Test
    fun getPriceTickerOfOneSymbol() {
        runBlocking {
            val exchangeInfo = binanceClient.getExchangeInfo()
            val stat = binanceClient.getPriceTickerOfSymbol(exchangeInfo.symbols[0].symbol)
            Assert.assertNotNull(stat)
        }
    }
    @Test
    fun getPriceTickerOfEverySymbols() {
        runBlocking {
            val exchangeInfo = binanceClient.getExchangeInfo()
            val stats = binanceClient.getEveryPriceTickerOfSymbols()
            Assert.assertTrue(stats.isNotEmpty())
            Assert.assertEquals(exchangeInfo.symbols.size, stats.size)
        }
    }
    @Test
    fun getBookTickerOfOneSymbol() {
        runBlocking {
            val exchangeInfo = binanceClient.getExchangeInfo()
            val stat = binanceClient.getBookTickerOfSymbol(exchangeInfo.symbols[0].symbol)
            Assert.assertNotNull(stat)
        }
    }
    @Test
    fun getBookTickerOfEverySymbols() {
        runBlocking {
            val exchangeInfo = binanceClient.getExchangeInfo()
            val stats = binanceClient.getEveryBookTickerOfSymbols()
            Assert.assertTrue(stats.isNotEmpty())
            Assert.assertEquals(exchangeInfo.symbols.size, stats.size)
        }
    }

    @Test
    fun queryOpenOrders() {
        runBlocking {
            val orders = binanceClient.queryOpenOrdersOfEverySymbols()
            System.out.println(orders)
        }
    }
    @Test
    fun queryOpenOrderOfSymbol() {
        runBlocking {
            val orders = binanceClient.queryOpenOrdersOfSymbol("ETHBTC")
            System.out.println(orders)
        }
    }
    @Test
    fun getAccountInfo() {
        runBlocking {
            val accountInfo = binanceClient.getAccountInfo()
            System.out.println(accountInfo)
            Assert.assertNotNull(accountInfo)
        }
    }
    @Test
    fun getMyTrades() {
        runBlocking {
            val exchangeInfo = binanceClient.getExchangeInfo()
            val myTrades = binanceClient.getMyRecentTrades("BCCBTC", 500, 5000)
            System.out.println(myTrades)
            Assert.assertNotNull(myTrades)
        }
    }
}