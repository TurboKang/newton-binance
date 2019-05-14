package com.turbo

import com.turbo.newton.DelayEvent
import com.turbo.newton.Event
import com.turbo.newton.EventManager
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    /*
    val prop = Properties()
    prop.load(ClassLoader.getSystemResourceAsStream("application.properties"))
    val binanceClient = BinanceClient(
            domain = prop.getProperty("binance.domain"),
            apiKey = prop.getProperty("binance.key"),
            apiSecret = prop.getProperty("binance.secret")
    )
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

    val eventQueue = mutableListOf<MutableList<Event>>(
            mutableListOf(DelayEvent(400), DelayEvent(200)),
            mutableListOf(DelayEvent(4000), DelayEvent(2000))
    )
    EventManager(
            stepMillis = 1000,
            eventQueue = eventQueue
    ).start()
}

