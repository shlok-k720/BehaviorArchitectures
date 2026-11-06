package com.rmsftc.bttree.blackboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlackboardCallbackTest {
    @Test
    fun `callback receives supplied key and value`() {
        var received: Pair<String, Int>? = null
        val callback = BlackboardCallback<Int> { key, value -> received = key to value }

        callback.onChange("score", 7)

        assertEquals("score" to 7, received)
    }
}
