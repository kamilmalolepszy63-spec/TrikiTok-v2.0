package com.trikicontrol.scroller.gestures

import com.trikicontrol.scroller.R
import com.trikicontrol.scroller.ble.ImuSample
import kotlin.math.abs

enum class GestureType { ROTATE_CW, ROTATE_CCW, SHAKE }

enum class GestureAction(val id: String, val titleResId: Int) {
    NEXT_ITEM("NEXT_ITEM", R.string.action_next),
    PREVIOUS_ITEM("PREVIOUS_ITEM", R.string.action_prev),
    SINGLE_TAP("SINGLE_TAP", R.string.action_tap),
    DOUBLE_TAP("DOUBLE_TAP", R.string.action_double),
    SWIPE_LEFT("SWIPE_LEFT", R.string.action_left),
    SWIPE_RIGHT("SWIPE_RIGHT", R.string.action_right),
    VOLUME_UP("VOLUME_UP", R.string.action_volume_up),
    VOLUME_DOWN("VOLUME_DOWN", R.string.action_volume_down),
    NONE("NONE", R.string.action_none);

    companion object {
        fun fromId(id: String?, default: GestureAction): GestureAction {
            return values().firstOrNull { it.id == id } ?: default
        }
    }
}

data class GestureEvent(val type: GestureType, val tMs: Long, val value: Double)

/**
 * Detects rotate-clockwise / rotate-counterclockwise / shake from IMU samples.
 */
class TrikiGestureDetector(
    var rotationThreshold: Double = 25.0,
    var shakeThreshold: Double = 60.0,
    private val holdRequiredMs: Long = 0, // Emit immediately on detected motion
    private val repeatIntervalMs: Long = 350, // Cooldown between consecutive swipes
) {
    private var current: GestureType? = null
    private var startedAt: Long = 0L
    private var lastEmittedAt: Long = 0L

    fun reset() {
        current = null
        startedAt = 0L
        lastEmittedAt = 0L
    }

    private fun detect(s: ImuSample): GestureEvent? {
        val absGz = abs(s.gz)
        val absGx = abs(s.gx)
        val absGy = abs(s.gy)

        // Find dominant gyro axis for rotation so triki orientation on finger doesn't matter
        val rotVal = when {
            absGz >= absGx && absGz >= absGy -> s.gz
            absGx >= absGy -> s.gx
            else -> s.gy
        }

        if (rotVal > rotationThreshold) return GestureEvent(GestureType.ROTATE_CW, s.tMs, rotVal)
        if (rotVal < -rotationThreshold) return GestureEvent(GestureType.ROTATE_CCW, s.tMs, rotVal)

        val shakeMag = absGx + absGy + absGz
        if (shakeMag > shakeThreshold) return GestureEvent(GestureType.SHAKE, s.tMs, shakeMag)

        return null
    }

    fun process(s: ImuSample): GestureEvent? {
        val detected = detect(s) ?: run {
            current = null
            return null
        }

        if (current != detected.type) {
            current = detected.type
            startedAt = s.tMs
        }

        if (s.tMs - startedAt < holdRequiredMs) return null

        // Check repeat interval / cooldown without 64-bit Long overflow
        if (lastEmittedAt > 0L && (s.tMs - lastEmittedAt) < repeatIntervalMs) {
            return null
        }

        lastEmittedAt = s.tMs
        return detected
    }
}
