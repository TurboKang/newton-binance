package com.turbo.binance

import com.fasterxml.jackson.databind.node.ArrayNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.turbo.binance.enum.CandleIntervalEnum
import com.turbo.binance.enum.OrderSideEnum
import com.turbo.binance.enum.OrderStatusEnum
import com.turbo.binance.enum.OrderTypeEnum
import com.turbo.binance.model.*
import com.turbo.util.Jsonifier
import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.nio.charset.Charset
import java.time.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class BinanceClient(
        private val domain: String,
        private val apiKey: String,
        private val apiSecret: String
): BinanceInterface {
    init {
        FuelManager.instance.baseHeaders = mapOf(
                Pair("X-MBX-APIKEY", apiKey)
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BinanceClient::class.java)
    }

    suspend fun ping() {
        val path = "/api/v3/ping"
        val (_,_,result) = Fuel.get(domain + path).awaitStringResponseResult()
        System.out.println(result)
    }

    suspend fun getServerTime(): Long {
        val path = "/api/v3/time"
        val (_,_,result) = Fuel.get(domain + path).awaitStringResponseResult()
        return Jsonifier.readTree(result.get())["serverTime"].longValue()
    }

    suspend fun getExchangeInfo(): ExchangeInfo {
        val path = "/api/v3/exchangeInfo"
        val (_,_,result) = Fuel.get(domain + path).awaitStringResponseResult()
        return Jsonifier.readValue(result.get(), ExchangeInfo::class.java)
    }

    suspend fun getDepth(symbolStr: String, limit: Int): Depth {

        val confirmedLimit = if(!listOf(5, 10, 20, 50, 100, 500, 1000).contains(limit)) {
            logger.error("Unavailable limit")
            100
        } else {
            limit
        }

        val path = "/api/v3/depth"
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = listOf(
                        Pair("symbol", symbolStr),
                        Pair("limit", confirmedLimit.toString())
                )
        ).awaitStringResponseResult()
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

    suspend fun getTrades(symbolStr: String, limit: Int): List<Trade> {
      val confirmedLimit = minOf(limit, 1000)
        val path = "/api/v3/trades"
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = listOf(
                        Pair("symbol", symbolStr),
                        Pair("limit", confirmedLimit.toString())
                )
        ).awaitStringResponseResult()
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

    suspend fun getHistoricalTradesFromToRecent(symbolStr: String, limit: Int, fromIdInclusive: Long): List<Trade> {
      val confirmedLimit = minOf(limit, 1000)
        val path = "/api/v3/historicalTrades"
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = listOf(
                        Pair("symbol", symbolStr),
                        Pair("limit", confirmedLimit.toString()),
                        Pair("fromId", fromIdInclusive.toString())
                )
        ).awaitStringResponseResult()
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

    suspend fun getAggregateTrade(symbolStr: String, /*fromIdInclusive: Long,*/ startZonedDateTime: ZonedDateTime, duration: Duration, limit: Int): List<AggregateTrade> {
      val confirmedLimit = minOf(limit, 1000)
        val endZonedDateTime = startZonedDateTime.plus(if(duration > Duration.ofDays(1)) {
            Duration.ofDays(1)
        } else {
            duration
        })
        val path = "/api/v3/aggTrades"
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = listOf(
                        Pair("symbol", symbolStr),
                        Pair("limit", confirmedLimit.toString()),
//                        Pair("fromId", fromIdInclusive.toString()),
                        Pair("startTime", (startZonedDateTime.toEpochSecond()*1000L).toString()),
                        Pair("endTime", (endZonedDateTime.toEpochSecond()*1000L).toString())
                )
        ).awaitStringResponseResult()
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

    override suspend fun getCandles(symbolStr: String, interval: CandleIntervalEnum, limit: Int, firstCandleOpenZonedDateTime: ZonedDateTime?, lastCandleOpenZonedDateTime: ZonedDateTime?): List<Candle> {
      val confirmedLimit = minOf(limit, 1000)
      val path = "/api/v3/klines"
      val parameters = mutableListOf(
          Pair("symbol", symbolStr),
          Pair("interval", interval.toString()),
          Pair("limit", confirmedLimit.toString())
      )
      when {
        firstCandleOpenZonedDateTime != null && lastCandleOpenZonedDateTime != null -> {
          parameters.add(Pair("startTime", (firstCandleOpenZonedDateTime.toEpochSecond()*1000L).toString()))
          parameters.add(Pair("endTime", (lastCandleOpenZonedDateTime.toEpochSecond()*1000L).toString()))
        }
        firstCandleOpenZonedDateTime == null && lastCandleOpenZonedDateTime != null ->
          parameters.add(Pair("endTime", (lastCandleOpenZonedDateTime.toEpochSecond()*1000L).toString()))
        firstCandleOpenZonedDateTime != null && lastCandleOpenZonedDateTime == null ->
          parameters.add(Pair("startTime", (firstCandleOpenZonedDateTime.toEpochSecond()*1000L).toString()))
        else -> { }
      }

      val (_,_,result) = Fuel.get(
          path = domain + path,
          parameters = parameters.toList()
      ).awaitStringResponseResult()
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

    suspend fun get24HourStatOfSymbol(symbolStr: String): Stat24Hour {
        val path = "/api/v3/ticker/24hr"
        val (_,_,result) = Fuel.get(domain + path, listOf(Pair("symbol", symbolStr))).awaitStringResponseResult()
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
    suspend fun get24HourStatOfEverySymbols(): List<Stat24Hour> {
        val path = "/api/v3/ticker/24hr"
        val (_,_,result) = Fuel.get(domain + path).awaitStringResponseResult()
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

    suspend fun getPriceTickerOfSymbol(symbolStr: String): PriceTicker {
        val path = "/api/v3/ticker/price"
        val (_,_,result) = Fuel.get(path = domain + path, parameters = listOf(Pair("symbol", symbolStr))).awaitStringResponseResult()
        val jsonNode = Jsonifier.readTree(result.get())
        return PriceTicker(
                symbol = jsonNode["symbol"].textValue(),
                price = BigDecimal(jsonNode["price"].textValue())
        )
    }

    suspend fun getEveryPriceTickerOfSymbols(): List<PriceTicker> {
        val path = "/api/v3/ticker/price"
        val (_,_,result) = Fuel.get(path = domain + path).awaitStringResponseResult()
        val priceTickerArrayNode = Jsonifier.readTree(result.get())
        return priceTickerArrayNode.toList().map {
            PriceTicker(
                    symbol = it["symbol"].textValue(),
                    price = BigDecimal(it["price"].textValue())
            )
        }
    }

    suspend fun getBookTickerOfSymbol(symbolStr: String): BookTicker {
        val path = "/api/v3/ticker/bookTicker"
        val (_,_,result) = Fuel.get(path = domain + path, parameters = listOf(Pair("symbol", symbolStr))).awaitStringResponseResult()
        val jsonNode = Jsonifier.readTree(result.get())
        return BookTicker(
                symbol = jsonNode["symbol"].textValue(),
                bidPrice = BigDecimal(jsonNode["bidPrice"].textValue()),
                bidQuantity = BigDecimal(jsonNode["bidQty"].textValue()),
                askPrice = BigDecimal(jsonNode["askPrice"].textValue()),
                askQuantity = BigDecimal(jsonNode["askQty"].textValue())
        )
    }

    suspend fun getEveryBookTickerOfSymbols(): List<BookTicker> {
        val path = "/api/v3/ticker/bookTicker"
        val (_,_,result) = Fuel.get(path = domain + path).awaitStringResponseResult()
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

    suspend fun sendSimpleOrder(symbolStr: String, side: OrderSideEnum, quantity: BigDecimal): Pair<BigDecimal, BigDecimal> {
      val params = mapOf(
          Pair("symbol", symbolStr),
          Pair("type", OrderTypeEnum.MARKET.toString()),
          Pair("side", side.toString()),
          Pair("quantity", quantity.toString()),
          Pair("newOrderRespType", "ACK"),
          Pair("timestamp", System.currentTimeMillis().toString())
      )
      val path = "/api/v3/order"
      val (_,_,result) = Fuel.post(
          path = domain + path,
          parameters = params.toList().plus(Pair("signature", createSignature(params)))
      ).awaitStringResponseResult()
      val map = Jsonifier.readTree(result.get())
      return BigDecimal(map["price"].asText()) to BigDecimal(map["executedQty"].asText())
    }

    suspend fun sendMarketOrderACK(symbolStr: String, clientOrderId: String, side: OrderSideEnum, quantity: BigDecimal): Long {
        val params = mapOf(
                Pair("symbol", symbolStr),
                Pair("newClientOrderId", clientOrderId),
                Pair("type", OrderTypeEnum.MARKET.toString()),
                Pair("side", side.toString()),
                Pair("quantity", quantity.toString()),
                Pair("newOrderRespType", "ACK"),
                Pair("timestamp", System.currentTimeMillis().toString())
        )
        return order(params)
    }
    suspend fun sendLimitOrderACK(symbolStr: String, clientOrderId: String, side: OrderSideEnum, price: BigDecimal, quantity: BigDecimal): Long {
        val params = mapOf(
                Pair("symbol", symbolStr),
                Pair("newClientOrderId", clientOrderId),
                Pair("type", OrderTypeEnum.LIMIT.toString()),
                Pair("side", side.toString()),
                Pair("price", price.toString()),
                Pair("quantity", quantity.toString()),
                Pair("newOrderRespType", "ACK"),
                Pair("timeInForce", "GTC"),
                Pair("timestamp", System.currentTimeMillis().toString())
        )
        return order(params)
    }
    suspend fun sendLimitMakerOrderACK(symbolStr: String, clientOrderId: String, side: OrderSideEnum, price: BigDecimal, quantity: BigDecimal): Long {
        val params = mapOf(
                Pair("symbol", symbolStr),
                Pair("newClientOrderId", clientOrderId),
                Pair("type", OrderTypeEnum.LIMIT_MAKER.toString()),
                Pair("side", side.toString()),
                Pair("price", price.toString()),
                Pair("quantity", quantity.toString()),
                Pair("recvWindow", "5000"),
                Pair("newOrderRespType", "ACK"),
                Pair("timestamp", System.currentTimeMillis().toString())
        )
        return order(params)
    }
    suspend fun sendStopLossOrderACK(symbolStr: String, clientOrderId: String, side: OrderSideEnum, stopPrice: BigDecimal, quantity: BigDecimal): Long {
        val params = mapOf(
                Pair("symbol", symbolStr),
                Pair("newClientOrderId", clientOrderId),
                Pair("type", OrderTypeEnum.STOP_LOSS.toString()),
                Pair("side", side.toString()),
                Pair("stopPrice", stopPrice.toString()),
                Pair("quantity", quantity.toString()),
                Pair("newOrderRespType", "ACK"),
                Pair("timestamp", System.currentTimeMillis().toString())
        )
        return order(params)
    }
    suspend fun sendStopLossLimitOrderACK(symbolStr: String, clientOrderId: String, side: OrderSideEnum, stopPrice: BigDecimal, limitPrice: BigDecimal, quantity: BigDecimal): Long {
        val params = mapOf(
                Pair("symbol", symbolStr),
                Pair("newClientOrderId", clientOrderId),
                Pair("type", OrderTypeEnum.STOP_LOSS_LIMIT.toString()),
                Pair("side", side.toString()),
                Pair("price", limitPrice.toString()),
                Pair("stopPrice", stopPrice.toString()),
                Pair("quantity", quantity.toString()),
                Pair("newOrderRespType", "ACK"),
                Pair("timeInForce", "GTC"),
                Pair("timestamp", System.currentTimeMillis().toString())
        )
        return order(params)
    }
    suspend fun sendTakeProfitOrderACK(symbolStr: String, clientOrderId: String, side: OrderSideEnum, stopPrice: BigDecimal, quantity: BigDecimal): Long {
        val params = mapOf(
                Pair("symbol", symbolStr),
                Pair("newClientOrderId", clientOrderId),
                Pair("type", OrderTypeEnum.TAKE_PROFIT.toString()),
                Pair("side", side.toString()),
                Pair("stopPrice", stopPrice.toString()),
                Pair("quantity", quantity.toString()),
                Pair("newOrderRespType", "ACK"),
                Pair("timestamp", System.currentTimeMillis().toString())
        )
        return order(params)
    }
    suspend fun sendTakeProfitLimitOrderACK(symbolStr: String, clientOrderId: String, side: OrderSideEnum, stopPrice: BigDecimal, limitPrice: BigDecimal, quantity: BigDecimal): Long {
        val params = mapOf(
                Pair("symbol", symbolStr),
                Pair("newClientOrderId", clientOrderId),
                Pair("type", OrderTypeEnum.TAKE_PROFIT_LIMIT.toString()),
                Pair("side", side.toString()),
                Pair("price", limitPrice.toString()),
                Pair("stopPrice", stopPrice.toString()),
                Pair("quantity", quantity.toString()),
                Pair("newOrderRespType", "ACK"),
                Pair("timeInForce", "GTC"),
                Pair("timestamp", System.currentTimeMillis().toString())
        )
        return order(params)
    }

    private suspend fun order(params: Map<String, String>): Long {
        val path = "/api/v3/order"
        val (_,_,result) = Fuel.post(
                path = domain + path,
                parameters = params.toList().plus(Pair("signature", createSignature(params)))
        ).awaitStringResponseResult()
        System.out.println(result.get())
        return Jsonifier.readTree(result.get())["orderId"].longValue()
    }

    suspend fun cancelOrderByOrderId(symbolStr: String, orderId: Long, recvWindow: Int = 5000) {
        val path = "/api/v3/order"
        val params = mapOf(
                Pair("symbol", symbolStr),
                Pair("orderId", orderId.toString()),
                Pair("recvWindow", recvWindow.toString()),
                Pair("timestamp", System.currentTimeMillis().toString())
        )
        val (_,_,result) = Fuel.delete(
                path = domain + path,
                parameters = params.toList().plus(Pair("signature", createSignature(params)))
        ).awaitStringResponseResult()
        System.out.println(result.get())
    }
    suspend fun cancelOrderByClientOrderId(symbolStr: String, clientOrderIdOfCancelTarget: String, recvWindow: Int = 5000) {
        val path = "/api/v3/order"
        val params = mapOf(
                Pair("symbol", symbolStr),
                Pair("orgClientOrderId", clientOrderIdOfCancelTarget),
                Pair("recvWindow", recvWindow.toString()),
                Pair("timestamp", System.currentTimeMillis().toString())
        )
        val (_,_,result) = Fuel.delete(
                path = domain + path,
                parameters = params.toList().plus(Pair("signature", createSignature(params)))
        ).awaitStringResponseResult()
    }

    suspend fun queryOrder(symbolStr: String, clientOrderId: String, recvWindow: Int = 5000): Order {
        val path = "/api/v3/order"
        val params = mapOf(
                Pair("symbol", symbolStr),
                Pair("clientOrderId", clientOrderId),
                Pair("recvWindow", recvWindow.toString()),
                Pair("timestamp", System.currentTimeMillis().toString())
        )
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = params.toList().plus(Pair("signature", createSignature(params)))
        ).awaitStringResponseResult()
        val jsonNode = Jsonifier.readTree(result.get())
        return Order(
                symbol = symbolStr,
                orderId = jsonNode["orderId"].longValue(),
                clientOrderId = jsonNode["clientOrderId"].textValue(),
                price = BigDecimal(jsonNode["price"].textValue()),
                originQuantity = BigDecimal(jsonNode["origQty"].textValue()),
                executedQuantity = BigDecimal(jsonNode["executedQty"].textValue()),
                status = OrderStatusEnum.valueOf(jsonNode["status"].textValue()),
                type = OrderTypeEnum.valueOf(jsonNode["type"].textValue()),
                side = OrderSideEnum.valueOf(jsonNode["side"].textValue()),
                stopPrice = BigDecimal(jsonNode["stopPrice"].textValue()),
                icebergQuantity = BigDecimal(jsonNode["icebergQty"].textValue()),
                time = Instant.ofEpochMilli(jsonNode["time"].longValue()).atZone(ZoneId.systemDefault()),
                isWorking = jsonNode["isWorking"].booleanValue()
        )
    }
    suspend fun queryOpenOrdersOfSymbol(symbolStr: String, recvWindow: Int = 5000): List<Order> {
        val path = "/api/v3/openOrders"
        val params = mapOf(
                Pair("symbol", symbolStr),
                Pair("timestamp", System.currentTimeMillis().toString()),
                Pair("recvWindow", recvWindow.toString())
        )
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = params.toList().plus(Pair("signature", createSignature(params)))
        ).awaitStringResponseResult()

        val ordersArrayNode = Jsonifier.readTree(result.get()) as ArrayNode
        return ordersArrayNode.toList().map {jsonNode ->
            Order(
                    symbol = symbolStr,
                    orderId = jsonNode["orderId"].longValue(),
                    clientOrderId = jsonNode["clientOrderId"].textValue(),
                    price = BigDecimal(jsonNode["price"].textValue()),
                    originQuantity = BigDecimal(jsonNode["origQty"].textValue()),
                    executedQuantity = BigDecimal(jsonNode["executedQty"].textValue()),
                    status = OrderStatusEnum.valueOf(jsonNode["status"].textValue()),
                    type = OrderTypeEnum.valueOf(jsonNode["type"].textValue()),
                    side = OrderSideEnum.valueOf(jsonNode["side"].textValue()),
                    stopPrice = BigDecimal(jsonNode["stopPrice"].textValue()),
                    icebergQuantity = BigDecimal(jsonNode["icebergQty"].textValue()),
                    time = Instant.ofEpochMilli(jsonNode["time"].longValue()).atZone(ZoneId.systemDefault()),
                    isWorking = jsonNode["isWorking"].booleanValue()
            )
        }
    }
    suspend fun queryOpenOrdersOfEverySymbols(recvWindow: Int = 5000): List<Order> {
        val path = "/api/v3/openOrders"
        val params = mapOf(
                Pair("timestamp", System.currentTimeMillis().toString()),
                Pair("recvWindow", recvWindow.toString())
        )
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = params.toList().plus(Pair("signature", createSignature(params)))
        ).awaitStringResponseResult()

        val ordersArrayNode = Jsonifier.readTree(result.get()) as ArrayNode
        return ordersArrayNode.toList().map {jsonNode ->
            Order(
                    symbol = jsonNode["symbol"].textValue(),
                    orderId = jsonNode["orderId"].longValue(),
                    clientOrderId = jsonNode["clientOrderId"].textValue(),
                    price = BigDecimal(jsonNode["price"].textValue()),
                    originQuantity = BigDecimal(jsonNode["origQty"].textValue()),
                    executedQuantity = BigDecimal(jsonNode["executedQty"].textValue()),
                    status = OrderStatusEnum.valueOf(jsonNode["status"].textValue()),
                    type = OrderTypeEnum.valueOf(jsonNode["type"].textValue()),
                    side = OrderSideEnum.valueOf(jsonNode["side"].textValue()),
                    stopPrice = BigDecimal(jsonNode["stopPrice"].textValue()),
                    icebergQuantity = BigDecimal(jsonNode["icebergQty"].textValue()),
                    time = Instant.ofEpochMilli(jsonNode["time"].longValue()).atZone(ZoneId.systemDefault()),
                    isWorking = jsonNode["isWorking"].booleanValue()
            )
        }
    }
    suspend fun queryAllOrders(symbolStr: String, limit: Int, recvWindow: Int = 5000): List<Order> {
        val path = "/api/v3/allOrders"
        val confirmedLimit = minOf(limit, 1000)
        val params = mapOf(
                Pair("symbol", symbolStr),
                Pair("limit", confirmedLimit.toString()),
                Pair("timestamp", System.currentTimeMillis().toString()),
                Pair("recvWindow", recvWindow.toString())
        )
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = params.toList().plus(Pair("signature", createSignature(params)))
        ).awaitStringResponseResult()

        val ordersArrayNode = Jsonifier.readTree(result.get()) as ArrayNode
        return ordersArrayNode.toList().map {jsonNode ->
            Order(
                    symbol = symbolStr,
                    orderId = jsonNode["orderId"].longValue(),
                    clientOrderId = jsonNode["clientOrderId"].textValue(),
                    price = BigDecimal(jsonNode["price"].textValue()),
                    originQuantity = BigDecimal(jsonNode["origQty"].textValue()),
                    executedQuantity = BigDecimal(jsonNode["executedQty"].textValue()),
                    status = OrderStatusEnum.valueOf(jsonNode["status"].textValue()),
                    type = OrderTypeEnum.valueOf(jsonNode["type"].textValue()),
                    side = OrderSideEnum.valueOf(jsonNode["side"].textValue()),
                    stopPrice = BigDecimal(jsonNode["stopPrice"].textValue()),
                    icebergQuantity = BigDecimal(jsonNode["icebergQty"].textValue()),
                    time = Instant.ofEpochMilli(jsonNode["time"].longValue()).atZone(ZoneId.systemDefault()),
                    isWorking = jsonNode["isWorking"].booleanValue()
            )
        }
    }
    suspend fun queryAllOrdersLowerBoundedOrderId(symbolStr: String, orderIdLowerBound: Long, limit: Int, recvWindow: Int = 5000): List<Order> {
        val path = "/api/v3/allOrders"
        val confirmedLimit = minOf(limit, 1000)
        val params = mapOf(
                Pair("symbol", symbolStr),
                Pair("limit", confirmedLimit.toString()),
                Pair("orderId", orderIdLowerBound.toString()),
                Pair("timestamp", System.currentTimeMillis().toString()),
                Pair("recvWindow", recvWindow.toString())
        )
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = params.toList().plus(Pair("signature", createSignature(params)))
        ).awaitStringResponseResult()

        val ordersArrayNode = Jsonifier.readTree(result.get()) as ArrayNode
        return ordersArrayNode.toList().map {jsonNode ->
            Order(
                    symbol = symbolStr,
                    orderId = jsonNode["orderId"].longValue(),
                    clientOrderId = jsonNode["clientOrderId"].textValue(),
                    price = BigDecimal(jsonNode["price"].textValue()),
                    originQuantity = BigDecimal(jsonNode["origQty"].textValue()),
                    executedQuantity = BigDecimal(jsonNode["executedQty"].textValue()),
                    status = OrderStatusEnum.valueOf(jsonNode["status"].textValue()),
                    type = OrderTypeEnum.valueOf(jsonNode["type"].textValue()),
                    side = OrderSideEnum.valueOf(jsonNode["side"].textValue()),
                    stopPrice = BigDecimal(jsonNode["stopPrice"].textValue()),
                    icebergQuantity = BigDecimal(jsonNode["icebergQty"].textValue()),
                    time = Instant.ofEpochMilli(jsonNode["time"].longValue()).atZone(ZoneId.systemDefault()),
                    isWorking = jsonNode["isWorking"].booleanValue()
            )
        }
    }

    suspend fun getAccountInfo(recvWindow: Int = 5000): AccountInfo {
        val path = "/api/v3/account"
        val params = mapOf(
                Pair("timestamp", System.currentTimeMillis().toString()),
                Pair("recvWindow", recvWindow.toString())
        )
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = params.toList().plus(Pair("signature", createSignature(params)))
        ).awaitStringResponseResult()
        val jsonNode = Jsonifier.readTree(result.get())
        return AccountInfo(
                makerCommission = jsonNode["makerCommission"].intValue(),
                takerCommission = jsonNode["makerCommission"].intValue(),
                buyerCommission = jsonNode["makerCommission"].intValue(),
                sellerCommission = jsonNode["makerCommission"].intValue(),
                canTrade = jsonNode["canTrade"].booleanValue(),
                canWithdraw = jsonNode["canTrade"].booleanValue(),
                canDeposit = jsonNode["canTrade"].booleanValue(),
                balances = (jsonNode["balances"] as ArrayNode).map {
                    AccountInfo.AssetBalance(
                            asset = it["asset"].textValue(),
                            free = BigDecimal(it["free"].textValue()),
                            locked = BigDecimal(it["locked"].textValue())
                    )
                },
                updateTime = Instant.ofEpochMilli(jsonNode["updateTime"].longValue()).atZone(ZoneId.systemDefault())
        )
    }

    suspend fun getMyRecentTrades(symbolStr: String, limit: Int, recvWindow: Int = 5000): List<MyTrade> {
        val path = "/api/v3/myTrades"
        val confirmedLimit = minOf(limit, 1000)
        val params = mapOf(
                Pair("symbol", symbolStr),
                Pair("limit", confirmedLimit.toString()),
                Pair("timestamp", System.currentTimeMillis().toString()),
                Pair("recvWindow", recvWindow.toString())
        )
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = params.toList().plus(Pair("signature", createSignature(params)))
        ).awaitStringResponseResult()

        val myTradeArrayNode = Jsonifier.readTree(result.get()) as ArrayNode
        return myTradeArrayNode.toList().map {
            MyTrade(
                    id = it["id"].longValue(),
                    orderId = it["orderId"].longValue(),
                    price = BigDecimal(it["price"].textValue()),
                    quantity = BigDecimal(it["qty"].textValue()),
                    commission = BigDecimal(it["commission"].textValue()),
                    commissionAsset = it["commissionAsset"].textValue(),
                    time = Instant.ofEpochMilli(it["time"].longValue()).atZone(ZoneId.systemDefault()),
                    isBuyer = it["isBuyer"].booleanValue(),
                    isMaker = it["isMaker"].booleanValue(),
                    isBestMatch = it["isBestMatch"].booleanValue()
            )
        }
    }

    suspend fun getMyTradesWithLowerBoundTradeId(symbolStr: String, tradeIdLowerBound: Long, limit: Int, recvWindow: Int = 5000): List<MyTrade> {
        val path = "/api/v3/myTrades"
      val confirmedLimit = minOf(limit, 1000)
        val params = mapOf(
                Pair("symbol", symbolStr),
                Pair("fromId", tradeIdLowerBound.toString()),
                Pair("limit", confirmedLimit.toString()),
                Pair("timestamp", System.currentTimeMillis().toString()),
                Pair("recvWindow", recvWindow.toString())
        )
        val (_,_,result) = Fuel.get(
                path = domain + path,
                parameters = params.toList().plus(Pair("signature", createSignature(params)))
        ).awaitStringResponseResult()

        val myTradeArrayNode = Jsonifier.readTree(result.get()) as ArrayNode
        return myTradeArrayNode.toList().map {
            MyTrade(
                    id = it["id"].longValue(),
                    orderId = it["orderId"].longValue(),
                    price = BigDecimal(it["price"].textValue()),
                    quantity = BigDecimal(it["qty"].textValue()),
                    commission = BigDecimal(it["commission"].textValue()),
                    commissionAsset = it["commissionAsset"].textValue(),
                    time = Instant.ofEpochMilli(it["time"].longValue()).atZone(ZoneId.systemDefault()),
                    isBuyer = it["isBuyer"].booleanValue(),
                    isMaker = it["isMaker"].booleanValue(),
                    isBestMatch = it["isBestMatch"].booleanValue()
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