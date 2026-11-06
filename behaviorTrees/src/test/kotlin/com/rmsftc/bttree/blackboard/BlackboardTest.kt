package com.rmsftc.bttree.blackboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlackboardTest {
    private enum class Keys { SCORE, TARGET }

    @Test
    fun `stores and retrieves string and enum keys`() {
        val blackboard = Blackboard()

        blackboard.set("name", "robot")
        blackboard.set(Keys.SCORE, 42)

        assertEquals("robot", blackboard.get("name", String::class.java))
        assertEquals(42, blackboard.get(Keys.SCORE, Int::class.javaObjectType))
        assertTrue(blackboard.has("name"))
        assertTrue(blackboard.has(Keys.SCORE))
        assertNull(blackboard.get("missing", String::class.java))
        assertThrows(ClassCastException::class.java) {
            blackboard.get("name", Int::class.javaObjectType)
        }
    }

    @Test
    fun `default is persisted only when non-null and type matches`() {
        val blackboard = Blackboard()
        var callbackValue: Any? = null
        blackboard.registerCallback("count") { _, value -> callbackValue = value }

        assertEquals(3, blackboard.getOrDefault("count", Int::class.javaObjectType, 3))
        assertEquals(3, callbackValue)
        blackboard.set("count", "wrong type")
        assertEquals(4, blackboard.getOrDefault("count", Int::class.javaObjectType, 4))
        assertEquals(4, blackboard.get("count", Int::class.javaObjectType))
        assertNull(blackboard.getOrDefault("absent", String::class.java, null))
        assertFalse(blackboard.has("absent"))
    }

    @Test
    fun `callbacks use a snapshot and continue after failures`() {
        val blackboard = Blackboard()
        val calls = mutableListOf<String>()
        lateinit var removed: BlackboardCallback<Any>
        removed = BlackboardCallback { _, _ -> calls += "removed" }
        val failing = BlackboardCallback<Any> { _, _ ->
            calls += "failing"
            blackboard.deregisterCallback("key", removed)
            throw IllegalStateException("callback failure")
        }
        val succeeding = BlackboardCallback<Any> { _, _ -> calls += "succeeding" }
        blackboard.registerCallback("key", failing)
        blackboard.registerCallback("key", removed)
        blackboard.registerCallback("key", succeeding)

        assertThrows(IllegalStateException::class.java) { blackboard.set("key", 1) }
        assertEquals(listOf("failing", "removed", "succeeding"), calls)
        assertEquals(2, blackboard.getCallbacks("key").size)

        calls.clear()
        assertThrows(IllegalStateException::class.java) { blackboard.set("key", 2) }
        assertEquals(listOf("failing", "succeeding"), calls)
    }

    @Test
    fun `callback collections are defensive and removal clears callbacks`() {
        val blackboard = Blackboard()
        val callback = BlackboardCallback<Any> { _, _ -> }
        blackboard.registerCallback(Keys.TARGET, callback)
        val callbacks = blackboard.getCallbacks(Keys.TARGET)

        callbacks.clear()
        assertEquals(1, blackboard.getCallbacks(Keys.TARGET).size)
        blackboard.remove(Keys.TARGET)
        assertTrue(blackboard.getCallbacks(Keys.TARGET).isEmpty())
        blackboard.set(Keys.TARGET, "value")
        blackboard.clearAll()
        assertFalse(blackboard.has(Keys.TARGET))
    }

    @Test
    fun `rejects null required arguments`() {
        val blackboard = Blackboard()

        assertThrows(NullPointerException::class.java) { blackboard.set(null as String?, "value") }
        assertThrows(NullPointerException::class.java) { blackboard.set("key", null) }
        assertThrows(NullPointerException::class.java) {
            blackboard.registerCallback(null as String?, BlackboardCallback<Any> { _, _ -> })
        }
    }

    @Test
    fun `getIfPresent reads without writing and without throwing on wrong types`() {
        val blackboard = Blackboard()
        blackboard.set("name", "robot")

        assertEquals("robot", blackboard.getIfPresent("name", String::class.java))
        assertNull(blackboard.getIfPresent("missing", String::class.java))
        assertNull(blackboard.getIfPresent("name", Int::class.javaObjectType))
        // A read must not store anything.
        assertFalse(blackboard.has("missing"))
    }

    @Test
    fun `removeValue keeps callbacks and removeCallbacks keeps the value`() {
        val blackboard = Blackboard()
        var calls = 0
        blackboard.set("key", 1)
        blackboard.registerCallback("key") { _, _ -> calls++ }

        blackboard.removeValue("key")
        assertFalse(blackboard.has("key"))
        assertEquals(1, blackboard.getCallbacks("key").size)
        blackboard.set("key", 2)
        assertEquals(1, calls)

        blackboard.removeCallbacks("key")
        assertTrue(blackboard.getCallbacks("key").isEmpty())
        assertEquals(2, blackboard.get("key", Int::class.javaObjectType))
    }

    @Test
    fun `putIfAbsent stores only once and returns the stored value`() {
        val blackboard = Blackboard()
        var calls = 0
        blackboard.registerCallback("count") { _, _ -> calls++ }

        assertEquals(3, blackboard.putIfAbsent("count", Int::class.javaObjectType, 3))
        assertEquals(1, calls)
        // Second call leaves the existing value (and its callbacks) alone.
        assertEquals(3, blackboard.putIfAbsent("count", Int::class.javaObjectType, 9))
        assertEquals(1, calls)
        assertEquals(3, blackboard.get("count", Int::class.javaObjectType))
        assertThrows(ClassCastException::class.java) {
            blackboard.putIfAbsent("count", String::class.java, "wrong type")
        }
    }
}
