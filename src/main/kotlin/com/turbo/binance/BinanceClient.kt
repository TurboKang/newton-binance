package com.turbo.binance

import com.fasterxml.jackson.databind.node.ArrayNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelManager
import com.turbo.binance.enum.CandleIntervalEnum
import com.turbo.binance.model.*
import com.turbo.util.Jsonifier
import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.nio.charset.Charset
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
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

    companion object {
        private val logger = LoggerFactory.getLogger(BinanceClient::class.java)
    }

    fun ping() {
        val path = "/api/v1/ping"
        val (_,_,result) = Fuel.get(domain + path).responseString()
        System.out.println(result)
    }

    fun getServerTime(): Long {
        val path = "/api/v1/time"
        val (_,_,result) = Fuel.get(domain + path).responseString()
        return Jsonifier.readTree(result.get())["serverTime"].longValue()
    }

    fun getExchangeInfo(): ExchangeInfo {
        val path = "/api/v1/exchangeInfo"
        val (_,_,result) = Fuel.get(domain + path).responseString()
        return Jsonifier.readValue(result.get(), ExchangeInfo::class.java)
    }

    fun getDepth(symbolStr: String, limit: Int): Depth {

        val confirmedLimit = if(!listOf(5, 10, 20, 50, 100, 500, 1000).contains(limit)) {
            logger.error("Unavailable limit")
            100
        } else {
            limit
        }

        val path = "/api/v1/depth"
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = listOf(
                        Pair("symbol", symbolStr),
                        Pair("limit", confirmedLimit.toString())
                )
        ).responseString()
        val resultNode = Jsonifier.readTree(result.get())
        val listOfBidArrayNode = (resultNode["bids"] as ArrayNode).toList() as List<ArrayNode>
        val listOfAskArrayNode = (resultNode["asks"] as ArrayNode).toList() as List<ArrayNode>

        val bids = listOfBidArrayNode.map { Depth.DepthPriceQuantity(price = BigDecimal(it[0].textValue()), quantity = BigDecimal(it[1].textValue())) }
        val asks = listOfAskArrayNode.map { Depth.DepthPriceQuantity(price = BigDecimal(it[0].textValue()), quantity = BigDecimal(it[1].textValue())) }
        return Depth(
                bids = bids,
                asks = asks
        )
    }

    fun getTrades(symbolStr: String, limit: Int): List<Trade> {
        val confirmedLimit = if(limit > 500) {
            500
        } else {
            limit
        }
        val path = "/api/v1/trades"
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = listOf(
                        Pair("symbol", symbolStr),
                        Pair("limit", confirmedLimit.toString())
                )
        ).responseString()
        val tradeArrayNode = Jsonifier.readTree(result.get())
        return tradeArrayNode.map { Trade(
                id = it["id"].longValue(),
                price = BigDecimal(it["price"].textValue()),
                quantity = BigDecimal(it["qty"].textValue()),
                time = Instant.ofEpochMilli(it["time"].longValue()).atZone(ZoneId.systemDefault()),
                isBuyerMaker = it["isBuyerMaker"].booleanValue(),
                isBestMatch = it["isBestMatch"].booleanValue()
        ) }
    }

    fun getHistoricalTradesFromToRecent(symbolStr: String, limit: Int, fromIdInclusive: Long): List<Trade> {
        val confirmedLimit = if(limit > 500) {
            500
        } else {
            limit
        }
        val path = "/api/v1/historicalTrades"
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = listOf(
                        Pair("symbol", symbolStr),
                        Pair("limit", confirmedLimit.toString()),
                        Pair("fromId", fromIdInclusive.toString())
                )
        ).responseString()
        val tradeArrayNode = Jsonifier.readTree(result.get())
        return tradeArrayNode.map { Trade(
                id = it["id"].longValue(),
                price = BigDecimal(it["price"].textValue()),
                quantity = BigDecimal(it["qty"].textValue()),
                time = Instant.ofEpochMilli(it["time"].longValue()).atZone(ZoneId.systemDefault()),
                isBuyerMaker = it["isBuyerMaker"].booleanValue(),
                isBestMatch = it["isBestMatch"].booleanValue()
        ) }
    }

    fun getAggregateTrade(symbolStr: String, /*fromIdInclusive: Long,*/ startZonedDateTime: ZonedDateTime, duration: Duration, limit: Int): List<AggregateTrade> {
        val confirmedLimit = if(limit > 500) {
            500
        } else {
            limit
        }
        val endZonedDateTime = startZonedDateTime.plus(if(duration > Duration.ofDays(1)) {
            Duration.ofDays(1)
        } else {
            duration
        })
        val path = "/api/v1/aggTrades"
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = listOf(
                        Pair("symbol", symbolStr),
                        Pair("limit", confirmedLimit.toString()),
//                        Pair("fromId", fromIdInclusive.toString()),
                        Pair("startTime", (startZonedDateTime.toEpochSecond()*1000L).toString()),
                        Pair("endTime", (endZonedDateTime.toEpochSecond()*1000L).toString())
                )
        ).responseString()
        val aggregateTradeArrayNode = Jsonifier.readTree(result.get())
        return aggregateTradeArrayNode.map { AggregateTrade(
                aggregateTradeId = it["a"].longValue(),
                price = BigDecimal(it["p"].textValue()),
                aggregateQuantity = BigDecimal(it["q"].textValue()),
                firstTradeId = it["f"].longValue(),
                lastTradeId = it["l"].longValue(),
                time = Instant.ofEpochMilli(it["T"].longValue()).atZone(ZoneId.systemDefault()),
                isBuyerMaker = it["m"].booleanValue(),
                isBestMatch = it["M"].booleanValue()
        ) }
    }

    fun getCandles(symbolStr: String, interval: CandleIntervalEnum, limit: Int, firstCandleOpenZonedDateTime: ZonedDateTime, lastCandleOpenZonedDateTime: ZonedDateTime): List<Candle> {
        val confirmedLimit = if(limit > 500) {
            500
        } else {
            limit
        }
        val path = "/api/v1/klines"
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = listOf(
                        Pair("symbol", symbolStr),
                        Pair("interval", interval.toString()),
                        Pair("limit", confirmedLimit.toString()),
                        Pair("startTime", (firstCandleOpenZonedDateTime.toEpochSecond()*1000L).toString()),
                        Pair("endTime", (lastCandleOpenZonedDateTime.toEpochSecond()*1000L).toString())
                )
        ).responseString()
        val candleArrayNode = Jsonifier.readTree(result.get())
        return candleArrayNode.map { Candle(
                openTime = Instant.ofEpochMilli(it[0].longValue()).atZone(ZoneId.systemDefault()),
                closeTime = Instant.ofEpochMilli(it[6].longValue()).atZone(ZoneId.systemDefault()),
                highPrice = BigDecimal(it[1].textValue()),
                lowPrice = BigDecimal(it[2].textValue()),
                openPrice = BigDecimal(it[3].textValue()),
                closePrice = BigDecimal(it[4].textValue()),
                volume = BigDecimal(it[5].textValue()),
                quoteAssetVolume = BigDecimal(it[7].textValue()),
                numberOfTrades = it[8].intValue(),
                takerBuyBaseAssetVolume = BigDecimal(it[9].textValue()),
                takerBuyQuoteAssetVolume = BigDecimal(it[10].textValue()),
                ignore = BigDecimal(it[11].textValue())
        ) }
    }

    fun get24HourStatOfSymbol(symbolStr: String): Stat24Hour {
        val path = "/api/v1/ticker/24hr"
        val (_,_,result) = Fuel.get(domain + path, listOf(Pair("symbol", symbolStr))).responseString()
        val jsonNode = Jsonifier.readTree(result.get())
        return Stat24Hour(
                symbol = jsonNode["symbol"].textValue(),
                priceChange = BigDecimal(jsonNode["priceChange"].textValue()),
                priceChangePercent = BigDecimal(jsonNode["priceChangePercent"].textValue()),
                weightedAvgPrice = BigDecimal(jsonNode["weightedAvgPrice"].textValue()),
                prevClosePrice = BigDecimal(jsonNode["prevClosePrice"].textValue()),
                lastPrice = BigDecimal(jsonNode["lastPrice"].textValue()),
                lastQty = BigDecimal(jsonNode["lastQty"].textValue()),
                bidPrice = BigDecimal(jsonNode["bidPrice"].textValue()),
                askPrice = BigDecimal(jsonNode["askPrice"].textValue()),
                openPrice = BigDecimal(jsonNode["openPrice"].textValue()),
                highPrice = BigDecimal(jsonNode["highPrice"].textValue()),
                lowPrice = BigDecimal(jsonNode["lowPrice"].textValue()),
                volume = BigDecimal(jsonNode["volume"].textValue()),
                quoteVolume = BigDecimal(jsonNode["quoteVolume"].textValue()),
                openTime = Instant.ofEpochMilli(jsonNode["openTime"].longValue()).atZone(ZoneId.systemDefault()),
                closeTime = Instant.ofEpochMilli(jsonNode["closeTime"].longValue()).atZone(ZoneId.systemDefault()),
                firstTradeId = jsonNode["firstId"].longValue(),
                lastTradeId = jsonNode["lastId"].longValue(),
                tradeCount = jsonNode["count"].intValue()
        )
    }
    fun get24HourStatOfEverySymbols(): List<Stat24Hour> {
        val path = "/api/v1/ticker/24hr"
        val (_,_,result) = Fuel.get(domain + path).responseString()
        val statArrayNode = Jsonifier.readTree(result.get())
        return statArrayNode.toList().map{jsonNode ->
            Stat24Hour(
                    symbol = jsonNode["symbol"].textValue(),
                    priceChange = BigDecimal(jsonNode["priceChange"].textValue()),
                    priceChangePercent = BigDecimal(jsonNode["priceChangePercent"].textValue()),
                    weightedAvgPrice = BigDecimal(jsonNode["weightedAvgPrice"].textValue()),
                    prevClosePrice = BigDecimal(jsonNode["prevClosePrice"].textValue()),
                    lastPrice = BigDecimal(jsonNode["lastPrice"].textValue()),
                    lastQty = BigDecimal(jsonNode["lastQty"].textValue()),
                    bidPrice = BigDecimal(jsonNode["bidPrice"].textValue()),
                    askPrice = BigDecimal(jsonNode["askPrice"].textValue()),
                    openPrice = BigDecimal(jsonNode["openPrice"].textValue()),
                    highPrice = BigDecimal(jsonNode["highPrice"].textValue()),
                    lowPrice = BigDecimal(jsonNode["lowPrice"].textValue()),
                    volume = BigDecimal(jsonNode["volume"].textValue()),
                    quoteVolume = BigDecimal(jsonNode["quoteVolume"].textValue()),
                    openTime = Instant.ofEpochMilli(jsonNode["openTime"].longValue()).atZone(ZoneId.systemDefault()),
                    closeTime = Instant.ofEpochMilli(jsonNode["closeTime"].longValue()).atZone(ZoneId.systemDefault()),
                    firstTradeId = jsonNode["firstId"].longValue(),
                    lastTradeId = jsonNode["lastId"].longValue(),
                    tradeCount = jsonNode["count"].intValue()
            )
        }
    }

    fun getPriceTickerOfSymbol(symbolStr: String): PriceTicker {
        val path = "/api/v3/ticker/price"
        val (_,_,result) = Fuel.get(path = domain + path, parameters = listOf(Pair("symbol", symbolStr))).responseString()
        val jsonNode = Jsonifier.readTree(result.get())
        return PriceTicker(
                symbol = jsonNode["symbol"].textValue(),
                price = BigDecimal(jsonNode["price"].textValue())
        )
    }

    fun getEveryPriceTickerOfSymbols(): List<PriceTicker> {
        val path = "/api/v3/ticker/price"
        val (_,_,result) = Fuel.get(path = domain + path).responseString()
        val priceTickerArrayNode = Jsonifier.readTree(result.get())
        return priceTickerArrayNode.toList().map {
            PriceTicker(
                    symbol = it["symbol"].textValue(),
                    price = BigDecimal(it["price"].textValue())
            )
        }
    }

    fun getBookTickerOfSymbol(symbolStr: String): BookTicker {
        val path = "/api/v3/ticker/bookTicker"
        val (_,_,result) = Fuel.get(path = domain + path, parameters = listOf(Pair("symbol", symbolStr))).responseString()
        val jsonNode = Jsonifier.readTree(result.get())
        return BookTicker(
                symbol = jsonNode["symbol"].textValue(),
                bidPrice = BigDecimal(jsonNode["bidPrice"].textValue()),
                bidQuantity = BigDecimal(jsonNode["bidQty"].textValue()),
                askPrice = BigDecimal(jsonNode["askPrice"].textValue()),
                askQuantity = BigDecimal(jsonNode["askQty"].textValue())
        )
    }

    fun getEveryBookTickerOfSymbols(): List<BookTicker> {
        val path = "/api/v3/ticker/price"
        val (_,_,result) = Fuel.get(path = domain + path).responseString()
        val bookTickerArrayNode = Jsonifier.readTree(result.get())
        return bookTickerArrayNode.toList().map {jsonNode ->
            BookTicker(
                    symbol = jsonNode["symbol"].textValue(),
                    bidPrice = BigDecimal(jsonNode["bidPrice"].textValue()),
                    bidQuantity = BigDecimal(jsonNode["bidQty"].textValue()),
                    askPrice = BigDecimal(jsonNode["askPrice"].textValue()),
                    askQuantity = BigDecimal(jsonNode["askQty"].textValue())
            )
        }
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