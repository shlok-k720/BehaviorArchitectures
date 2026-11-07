package com.rmsftc.fsm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class StateTest {

    @Test
    fun `string and enum states with the same name are the same state`() {
        assertEquals(State.of("IDLE"), State.of(TestStates.valueOf("IDLE")))
        assertEquals(State.of("IDLE"), State.of(TestStates.IDLE))
        assertNotEquals(State.of("IDLE"), State.of(TestStates.WORKING))
    }

    @Test
    fun `name returns the string form`() {
        assertEquals("WORKING", State.of(TestStates.WORKING).name())
        assertEquals("custom", State.of("custom").name())
        assertEquals("WORKING", State.of(TestStates.WORKING).toString())
    }

    @Test
    fun `state rejects null and empty`() {
        assertThrows(IllegalArgumentException::class.java) { State.of("") }
        assertThrows(IllegalArgumentException::class.java) { State.of(null as String?) }
        assertThrows(IllegalArgumentException::class.java) { State.of(null as TestStates?) }
    }
}
