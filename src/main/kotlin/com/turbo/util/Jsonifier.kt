package com.turbo.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

object Jsonifier {
    private val objectMapper = ObjectMapper().registerModule(KotlinModule())

    fun readTree(str: String): JsonNode {
        return objectMapper.readTree(str)
    }

    fun <T> readValue(str: String, classType: Class<T>): T {
        return objectMapper.readValue(str, classType)
    }
}