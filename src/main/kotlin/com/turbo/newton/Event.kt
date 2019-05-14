package com.turbo.newton

import kotlinx.coroutines.delay

interface Event {
    suspend fun run(): List<Future>
}

class Future(
        val delayMillis: Long,
        val event: Event
)

class DelayEvent(
        private val delayMillis: Long
): Event {
    override suspend fun run(): List<Future> {
        delay(delayMillis)
        return emptyList()
    }
}