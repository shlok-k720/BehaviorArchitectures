package com.rmsftc.bttree.logger

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoggerTest {
    @AfterEach
    fun cleanUp() = Logger.reset()

    @Test
    fun `attached loggers receive one timestamped event and can be removed`() {
        val received = mutableListOf<Triple<String, String, Double>>()
        val logger = LogSystem { caption, data, timestamp -> received += Triple(caption, data, timestamp) }
        Logger.attachLogger(logger, logger)

        val before = System.currentTimeMillis().toDouble()
        Logger.log("started", "name=node")
        Logger.removeLogger(logger)
        Logger.log("ignored", "ignored")

        assertEquals(1, received.size)
        assertEquals("started", received.single().first)
        assertEquals("name=node", received.single().second)
        assertTrue(received.single().third >= before)
    }

    @Test
    fun `reset detaches every logger`() {
        var calls = 0
        Logger.attachLogger { _, _, _ -> calls++ }

        Logger.reset()
        Logger.log("ignored", "ignored")

        assertEquals(0, calls)
    }
}
