package com.turbo

import com.turbo.binance.BinanceClient
import java.util.*

fun main(args : Array<String>) {
    val prop = Properties()
    prop.load(ClassLoader.getSystemResourceAsStream("application.properties"))
    val binanceClient = BinanceClient(
            domain = prop.getProperty("binance.domain"),
            apiKey = prop.getProperty("binance.key"),
            apiSecret = prop.getProperty("binance.secret")
    )
    System.out.println(binanceClient.getExchangeInfo())
    /*
    FuelManager.instance.basePath = mainUrl
    val fixedRateTimer = fixedRateTimer(name = "hello-timer", period = 1000) {
        "/v1/open/lang-list".httpGet().responseString { request, response, result ->
            //make a GET to http://httpbin.org/get and do something with response
            val (data, error) = result
            if (error == null) {
                System.out.println(data)
            } else {
                System.out.println("error")
            }
        }
    }
    */
}