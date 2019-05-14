package com.turbo.newton

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class EventManager(
        val stepMillis: Long,
        val eventQueue: MutableList<MutableList<Event>>
) {
    private var currentStep = 0

    companion object {
        val logger = LoggerFactory.getLogger(EventManager::class.java)
    }
    suspend fun start() {
        while (true) {
            logger.debug("Step $currentStep Start : ${LocalDateTime.now()}")
            coroutineScope {
                val events = eventQueue[0]
                val minimalWait = launch {
                    DelayEvent(stepMillis).run()
                }
                val jobs = events.forEach {
                    launch {
                        val futures = it.run()
                        futures.forEach {
                            future -> bookFuture(future)
                        }
                    }
                }
            }
            logger.debug("Step $currentStep End : ${LocalDateTime.now()}")
            currentStep++
            eventQueue.removeAt(0)
            allocateMinimumStep(1)
        }
    }

    private fun allocateMinimumStep(minimumSteps: Int) {
        if(eventQueue.size <= minimumSteps) {
            logger.debug("AllocateMinimumStep : ${minimumSteps - eventQueue.size + 1}")
            (0..(minimumSteps - eventQueue.size)).forEach { _ ->
                eventQueue.add(mutableListOf())
            }
        }
    }

    fun bookFuture(future: Future) {
        val delayStep = if(future.delayMillis < stepMillis) {
            1
        } else {
            future.delayMillis / stepMillis
        }.toInt()
        allocateMinimumStep(delayStep)
        eventQueue[delayStep].add(future.event)
    }
}