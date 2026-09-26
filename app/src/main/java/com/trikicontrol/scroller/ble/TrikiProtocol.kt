package com.trikicontrol.scroller.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Triki wire protocol: 14-byte IMU frames streamed over the Nordic UART Service. */
object TrikiProtocol {
    val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val NUS_RX_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // write here
    val NUS_TX_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // notifies here
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val DEVICE_NAME_UUID: UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
    val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    val START_COMMAND: ByteArray = byteArrayOf(
        0x20, 0x10, 0x00.toByte(), 0xD0.toByte(), 0x07, 0x68, 0x00, 0x03
    )

    const val FRAME_LENGTH = 14
    val FRAME_HEADER = byteArrayOf(0x22, 0x00)
    const val GYRO_SCALE = 131.0
    const val ACCEL_SCALE = 2048.0
    const val STARTUP_DISCARD_SAMPLES = 20
}

data class ImuSample(
    val index: Int,
    val tMs: Long,
    val gx: Double,
    val gy: Double,
    val gz: Double,
    val ax: Double,
    val ay: Double,
    val az: Double,
)

/** Re-assembles 14-byte frames (header 0x22 0x00) from arbitrary BLE notification chunks. */
class FrameParser {
    private val buf = ArrayDeque<Byte>()

    fun push(data: ByteArray): List<ByteArray> {
        buf.addAll(data.toList())
        val frames = mutableListOf<ByteArray>()
        while (true) {
            val idx = indexOfHeader()
            if (idx < 0) {
                // Keep a lone trailing 0x22 in case the header is split across notifications.
                val keepTrailing = buf.isNotEmpty() && buf.last() == TrikiProtocol.FRAME_HEADER[0]
                val drop = buf.size - (if (keepTrailing) 1 else 0)
                repeat(drop) { buf.removeFirst() }
                return frames
            }
            repeat(idx) { buf.removeFirst() }
            if (buf.size < TrikiProtocol.FRAME_LENGTH) return frames
            val frame = ByteArray(TrikiProtocol.FRAME_LENGTH) { buf.removeFirst() }
            frames.add(frame)
        }
    }

    private fun indexOfHeader(): Int {
        if (buf.size < 2) return if (buf.isNotEmpty() && buf[0] == TrikiProtocol.FRAME_HEADER[0]) -1 else -1
        for (i in 0..buf.size - 2) {
            if (buf[i] == TrikiProtocol.FRAME_HEADER[0] && buf[i + 1] == TrikiProtocol.FRAME_HEADER[1]) return i
        }
        return -1
    }
}

fun decodeFrame(frame: ByteArray, index: Int, tMs: Long): ImuSample {
    val bb = ByteBuffer.wrap(frame, 2, 12).order(ByteOrder.LITTLE_ENDIAN)
    val gx = bb.short.toDouble()
    val gy = bb.short.toDouble()
    val gz = bb.short.toDouble()
    val ax = bb.short.toDouble()
    val ay = bb.short.toDouble()
    val az = bb.short.toDouble()
    return ImuSample(
        index, tMs,
        gx / TrikiProtocol.GYRO_SCALE, gy / TrikiProtocol.GYRO_SCALE, gz / TrikiProtocol.GYRO_SCALE,
        ax / TrikiProtocol.ACCEL_SCALE, ay / TrikiProtocol.ACCEL_SCALE, az / TrikiProtocol.ACCEL_SCALE,
    )
}

/** Bytes in, ImuSamples out. Drops the first N samples while the sensor settles. */
class SampleDecoder(private var discardLeft: Int = TrikiProtocol.STARTUP_DISCARD_SAMPLES) {
    private val parser = FrameParser()
    private var nextIndex = 0

    fun push(data: ByteArray, tMs: Long): List<ImuSample> {
        val frames = parser.push(data)
        val out = mutableListOf<ImuSample>()
        val count = frames.size
        for ((i, frame) in frames.withIndex()) {
            if (discardLeft > 0) {
                discardLeft--
                continue
            }
            // Interpolate sample timestamp assuming ~10ms per IMU sample (100 Hz)
            val sampleTimeMs = tMs - (count - 1 - i) * 10L
            out.add(decodeFrame(frame, nextIndex, sampleTimeMs))
            nextIndex++
        }
        return out
    }
}
