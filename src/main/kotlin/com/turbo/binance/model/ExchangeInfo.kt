package com.turbo.binance.model

import com.fasterxml.jackson.databind.node.ArrayNode

data class ExchangeInfo(
        val timezone: String,
        val serverTime: Long,
        val rateLimits: ArrayNode,
        val symbols: List<Symbol>
)