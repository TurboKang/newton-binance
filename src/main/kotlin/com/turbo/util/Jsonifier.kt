package com.turbo.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

object Jsonifier {
    private val objectMapper = ObjectMapper()

    fun parse(str: String): JsonNode {
        return objectMapper.readTree(str)
    }

    fun map(str: String, type: Class): 
}