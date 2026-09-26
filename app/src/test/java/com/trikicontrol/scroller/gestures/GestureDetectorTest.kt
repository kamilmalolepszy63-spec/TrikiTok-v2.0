package com.trikicontrol.scroller.gestures

import com.trikicontrol.scroller.ble.ImuSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun sample(tMs: Long, gx: Double = 0.0, gy: Double = 0.0, gz: Double = 0.0) =
    ImuSample(0, tMs, gx, gy, gz, 0.0, 0.0, 0.0)

class GestureDetectorTest {

    private fun runStream(gz: Double = 0.0, gx: Double = 0.0, gy: Double = 0.0, durationMs: Long = 1000, stepMs: Long = 10): List<GestureEvent> {
        val d = TrikiGestureDetector()
        val events = mutableListOf<GestureEvent>()
        var t = 0L
        while (t <= durationMs) {
            d.process(sample(t, gx, gy, gz))?.let { events.add(it) }
            t += stepMs
        }
        return events
    }

    @Test
    fun rotateClockwiseRepeatsWhileHeld() {
        val events = runStream(gz = 100.0)
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.type == GestureType.ROTATE_CW })
    }

    @Test
    fun rotateCounterclockwise() {
        val events = runStream(gz = -100.0)
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.type == GestureType.ROTATE_CCW })
    }

    @Test
    fun shake() {
        val events = runStream(gx = 50.0, gy = 50.0)
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.type == GestureType.SHAKE })
    }

    @Test
    fun belowThresholdIsSilent() {
        assertEquals(0, runStream(gz = 20.0, gx = 10.0, gy = 10.0).size)
    }

    @Test
    fun rotationBeatsShakeWhenBothExceedThreshold() {
        val events = runStream(gz = 100.0, gx = 80.0, gy = 80.0)
        assertTrue(events.all { it.type == GestureType.ROTATE_CW })
    }

    @Test
    fun sameTimestampFramesDoNotDoubleFire() {
        // Frames batched in one BLE notification share a timestamp, so hold time never elapses
        // for the second one - this stops one held gesture from firing on every frame in a batch.
        val d = TrikiGestureDetector()
        assertEquals(null, d.process(sample(1000, gz = 100.0)))
        assertEquals(null, d.process(sample(1000, gz = 100.0)))
    }
}
