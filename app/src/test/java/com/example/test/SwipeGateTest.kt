package com.example.test

import org.junit.Assert.*
import org.junit.Test

class SwipeGateTest {
    @Test fun earlyReleaseRequiresANewContinuousHold() {
        val gate = SwipeGate()
        gate.beginHold(1000)
        assertEquals(.75f, gate.progress(2500, 2), .001f)
        gate.cancelHold()
        assertEquals(0f, gate.progress(5000, 2), 0f)
        gate.beginHold(5000)
        assertEquals(.5f, gate.progress(6000, 2), .001f)
        assertEquals(1f, gate.progress(7000, 2), 0f)
    }
    @Test fun everyThirdAttemptRequiresHold() {
        val gate = SwipeGate()
        assertFalse(gate.requiresHold(3)); assertFalse(gate.requiresHold(3)); assertTrue(gate.requiresHold(3))
        assertFalse(gate.requiresHold(3)); assertFalse(gate.requiresHold(3)); assertTrue(gate.requiresHold(3))
    }
}
