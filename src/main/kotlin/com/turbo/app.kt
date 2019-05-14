package com.turbo
import com.turbo.binance.BinanceClient
import com.turbo.newton.EventManager
import com.turbo.newton.RsiStrategy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*

fun main() = runBlocking {
    val prop = Properties()
    prop.load(ClassLoader.getSystemResourceAsStream("application.properties"))
    val binanceClient = BinanceClient(
            domain = prop.getProperty("binance.domain"),
            apiKey = prop.getProperty("binance.key"),
            apiSecret = prop.getProperty("binance.secret")
    )
    /*
    System.out.println(binanceClient.getServerTime() - System.currentTimeMillis())
    val exchangeInt = binanceClient.getExchangeInfo()
    val accountInfo = binanceClient.getAccountInfo()

    val balance_BCC = accountInfo.balances.find { it.asset == "BCC" }
    System.out.println(balance_BCC)
    val symbol_BCCBTC = exchangeInt.symbols.find { it.symbol == "BCCBTC" }!!
    System.out.println(symbol_BCCBTC)
    val mkt = Market(
            symbol = symbol_BCCBTC,
            allocatedBaseAsset = BigDecimal.ZERO,
            baseAssetBalance = BigDecimal.ZERO,
            orders = emptyList(),
            quoteAssetBalance = BigDecimal.ZERO
    )
    System.out.println(mkt)
    */

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
            symbol = "LTCBTC"
    )
    val rsiStrategy2 = RsiStrategy(
            binanceClient = binanceClient,
            eventManager = eventManager,
            symbol = "ETHBTC"
    )
    val rsiStrategy3 = RsiStrategy(
            binanceClient = binanceClient,
            eventManager = eventManager,
            symbol = "BTCUSDT"
    )

    rsiStrategy1.run()
    rsiStrategy2.run()
    rsiStrategy3.run()

    eventManager.start()
}

