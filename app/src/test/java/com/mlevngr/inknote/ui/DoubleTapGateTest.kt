package com.mlevngr.inknote.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleTapGateTest {
    @Test
    fun `second tap within timeout activates`() {
        val gate = DoubleTapGate(300)

        assertFalse(gate.registerTap(1_000))
        assertTrue(gate.registerTap(1_250))
    }

    @Test
    fun `slow second tap starts a new pair`() {
        val gate = DoubleTapGate(300)

        assertFalse(gate.registerTap(1_000))
        assertFalse(gate.registerTap(1_301))
        assertTrue(gate.registerTap(1_500))
    }

    @Test
    fun `successful pair does not leak into the next tap`() {
        val gate = DoubleTapGate(300)

        assertFalse(gate.registerTap(1_000))
        assertTrue(gate.registerTap(1_100))
        assertFalse(gate.registerTap(1_200))
    }
}
