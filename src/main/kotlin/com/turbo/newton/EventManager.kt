package com.turbo.newton

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class EventManager(
        val stepMillis: Long,
        val eventQueue: MutableList<MutableList<suspend () -> Unit>>
) {
    private var currentStep = 0

    companion object {
        val logger = LoggerFactory.getLogger(EventManager::class.java)
    }
    suspend fun start() {
        while (true) {
            logger.info("Step $currentStep Start : ${LocalDateTime.now()}")
            coroutineScope {
                val suspendFunctions = eventQueue[0]
                val minimalWait = launch {
                    delay(stepMillis)
                }
                suspendFunctions.forEach {
                    suspendFunction -> launch {
                        suspendFunction()
                    }
                }
            }
            logger.info("Step $currentStep End : ${LocalDateTime.now()}")
            currentStep++
            eventQueue.removeAt(0)
            allocateMinimumStep(1)
        }
    }

    private fun allocateMinimumStep(minimumSteps: Int) {
        if(eventQueue.size <= minimumSteps) {
            logger.debug("AllocateMinimumStep : ${minimumSteps - eventQueue.size + 1}")
            (0..(minimumSteps - eventQueue.size)).forEach {
                eventQueue.add(mutableListOf())
            }
        }
    }

    fun bookFuture(delayMillis: Int, future: suspend () -> Unit) {
        val delayStep = if(delayMillis < stepMillis) {
            1
        } else {
            delayMillis / stepMillis
        }.toInt()
        allocateMinimumStep(delayStep)
        eventQueue[delayStep].add(future)
    }
}