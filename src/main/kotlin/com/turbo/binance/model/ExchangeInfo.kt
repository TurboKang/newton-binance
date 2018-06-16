package com.turbo.binance.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.node.ArrayNode

data class ExchangeInfo @JsonCreator constructor(
        val timezone: String,
        val serverTime: Long,
        val rateLimits: ArrayNode,
        val exchangeFilters: List<Object>, //TODO
        val symbols: List<Symbol>
)