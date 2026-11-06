package com.rmsftc.bttree.logger

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LogSystemTest {
    @Test
    fun `log system receives every event argument`() {
        var event: Triple<String, String, Double>? = null
        val logSystem = LogSystem { caption, data, timestamp -> event = Triple(caption, data, timestamp) }

        logSystem.log("caption", "data", 12.5)

        assertEquals(Triple("caption", "data", 12.5), event)
    }
}
