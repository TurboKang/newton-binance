package com.turbo

import com.turbo.binance.BinanceClient
import com.turbo.binance.enum.OrderSideEnum
import java.math.BigDecimal
import java.util.*

fun main(args : Array<String>) {
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
    val priceTicker_BCCBTC = binanceClient.getPriceTickerOfSymbol("BCCBTC")

    val orderResult = binanceClient.sendTakeProfitLimitOrderACK(
            symbolStr = "BCCBTC",
            clientOrderId = "Test" + System.currentTimeMillis().toString(),
            side = OrderSideEnum.BUY,
            stopPrice = priceTicker_BCCBTC.price - symbol_BCCBTC.tickSize - symbol_BCCBTC.tickSize - symbol_BCCBTC.tickSize,
            limitPrice = priceTicker_BCCBTC.price - symbol_BCCBTC.tickSize - symbol_BCCBTC.tickSize - symbol_BCCBTC.tickSize,
            quantity = BigDecimal("0.008")
    )
    System.out.println(orderResult)
    binanceClient.cancelOrderByOrderId("BCCBTC", orderResult)
}