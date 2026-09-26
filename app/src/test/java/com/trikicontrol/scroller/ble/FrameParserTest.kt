package com.trikicontrol.scroller.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

private fun frame(gx: Int = 0, gy: Int = 0, gz: Int = 0, ax: Int = 0, ay: Int = 0, az: Int = 0): ByteArray {
    val bb = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
    bb.put(0x22).put(0x00)
    bb.putShort(gx.toShort()).putShort(gy.toShort()).putShort(gz.toShort())
    bb.putShort(ax.toShort()).putShort(ay.toShort()).putShort(az.toShort())
    return bb.array()
}

class FrameParserTest {
    @Test
    fun singleFrame() {
        val f = frame(1, 2, 3)
        assertEquals(listOf(f.toList()), FrameParser().push(f).map { it.toList() })
    }

    @Test
    fun splitAcrossNotifications() {
        val p = FrameParser()
        val f = frame(5, 6, 7)
        assertTrue(p.push(f.copyOfRange(0, 5)).isEmpty())
        val out = p.push(f.copyOfRange(5, f.size))
        assertEquals(1, out.size)
        assertEquals(f.toList(), out[0].toList())
    }

    @Test
    fun headerSplitBetweenChunks() {
        val p = FrameParser()
        val f = frame(9)
        assertTrue(p.push(f.copyOfRange(0, 1)).isEmpty()) // lone 0x22 must be retained
        val out = p.push(f.copyOfRange(1, f.size))
        assertEquals(1, out.size)
        assertEquals(f.toList(), out[0].toList())
    }

    @Test
    fun garbageBeforeHeaderIsDropped() {
        val p = FrameParser()
        val garbage = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val out = p.push(garbage + frame(1) + frame(2))
        assertEquals(2, out.size)
        assertEquals(frame(1).toList(), out[0].toList())
        assertEquals(frame(2).toList(), out[1].toList())
    }

    @Test
    fun decodeScalingAndSign() {
        val s = decodeFrame(frame(gx = 131, gy = -262, gz = 655, ax = 2048, ay = -4096, az = 0), 0, 1L)
        assertEquals(1.0, s.gx, 1e-9)
        assertEquals(-2.0, s.gy, 1e-9)
        assertEquals(5.0, s.gz, 1e-9)
        assertEquals(1.0, s.ax, 1e-9)
        assertEquals(-2.0, s.ay, 1e-9)
    }

    @Test
    fun startupSamplesDiscarded() {
        val d = SampleDecoder(discardLeft = 20)
        var bytes = ByteArray(0)
        repeat(25) { bytes += frame() }
        val out = d.push(bytes, 0L)
        assertEquals(5, out.size)
        assertEquals(listOf(0, 1, 2, 3, 4), out.map { it.index })
    }
}
