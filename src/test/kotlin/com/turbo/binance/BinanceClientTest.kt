package com.turbo.binance

import org.junit.Assert
import org.junit.Before
import org.junit.Test
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
        binanceClient.ping()
    }

    @Test
    fun getServerTime() {
        binanceClient.getServerTime()
    }
    @Test
    fun getExchangeInfo() {
        Assert.assertNotNull(binanceClient.getExchangeInfo())
    }
}