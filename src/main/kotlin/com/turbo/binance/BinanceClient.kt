package com.turbo.binance

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelManager
import com.turbo.binance.model.ExchangeInfo
import com.turbo.util.Jsonifier
import org.apache.commons.codec.binary.Hex
import java.nio.charset.Charset
import java.time.Instant
import java.time.ZoneId
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class BinanceClient(
        private val domain: String,
        private val apiKey: String,
        private val apiSecret: String
) {
    init {
        FuelManager.instance.baseHeaders = mapOf(Pair("X-MBX-APIKEY", apiKey))
    }

    fun ping() {
        val path = "/api/v1/ping"
        val (_,_,result) = Fuel.get(domain + path).responseString()
        System.out.println(result)
    }

    fun getServerTime(): Long {
        val path = "/api/v1/time"
        val (_,_,result) = Fuel.get(domain + path).responseString()
        val timestamp = Jsonifier.readTree(result.get())["serverTime"].longValue()
        val zonedDateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        return timestamp
    }

    fun getExchangeInfo(): ExchangeInfo {
        val path = "/api/v1/exchangeInfo"
        val (_,_,result) = Fuel.get(domain + path).responseString()
        return Jsonifier.readValue(result.get(), ExchangeInfo::class.java)
    }

    private fun createSignature(data: Map<String, String>): String {
        val queryString = data.toList().joinToString("&") { "${it.first}=${it.second}" }
        System.out.println(queryString)
        val sha256_HMAC = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(apiSecret.toByteArray(Charset.forName("UTF-8")), "HmacSHA256")
        sha256_HMAC.init(secretKeySpec)
        return Hex.encodeHexString(sha256_HMAC.doFinal(queryString.toByteArray(charset("UTF-8"))))
    }
}