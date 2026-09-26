package com.trikicontrol.scroller.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/**
 * Turns Triki gestures into on-screen swipes/taps/volume commands via the Accessibility gesture-dispatch API.
 * This is the only way a non-rooted app can inject actions into another app (TikTok, Spotify) on Android;
 * it never reads window content (see accessibility_service_config.xml).
 */
class ScrollAccessibilityService : AccessibilityService() {

    private var gestureInFlight = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        Log.i(TAG, "Accessibility service connected successfully")
    }

    override fun onDestroy() {
        instanceRef = null
        super.onDestroy()
        Log.i(TAG, "Accessibility service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't act on events; we only need the service bound to dispatch gestures.
    }

    override fun onInterrupt() {}

    private fun swipe(startYFrac: Float, endYFrac: Float, durationMs: Long = 220): Boolean {
        if (gestureInFlight) return false
        return try {
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(720)
            val height = metrics.heightPixels.coerceAtLeast(1280)

            val cx = width * 0.5f
            val startY = height * startYFrac
            val endY = height * endYFrac

            val path = Path().apply {
                moveTo(cx, startY)
                lineTo(cx, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            gestureInFlight = true
            val resetRunnable = Runnable { gestureInFlight = false }
            handler.postDelayed(resetRunnable, durationMs + 100L)

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    handler.removeCallbacks(resetRunnable)
                    gestureInFlight = false
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    handler.removeCallbacks(resetRunnable)
                    gestureInFlight = false
                }
            }, null)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "swipe exception: ${e.message}", e)
            gestureInFlight = false
            false
        }
    }

    private fun swipeHorizontal(startXFrac: Float, endXFrac: Float, durationMs: Long = 200): Boolean {
        if (gestureInFlight) return false
        return try {
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(720)
            val height = metrics.heightPixels.coerceAtLeast(1280)

            val cy = height * 0.5f
            val startX = width * startXFrac
            val endX = width * endXFrac

            val path = Path().apply {
                moveTo(startX, cy)
                lineTo(endX, cy)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            gestureInFlight = true
            val resetRunnable = Runnable { gestureInFlight = false }
            handler.postDelayed(resetRunnable, durationMs + 100L)

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    handler.removeCallbacks(resetRunnable)
                    gestureInFlight = false
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    handler.removeCallbacks(resetRunnable)
                    gestureInFlight = false
                }
            }, null)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "swipeHorizontal exception: ${e.message}", e)
            gestureInFlight = false
            false
        }
    }

    private fun tapCenter(durationMs: Long = 50): Boolean {
        if (gestureInFlight) return false
        return try {
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(720)
            val height = metrics.heightPixels.coerceAtLeast(1280)

            val cx = width * 0.5f
            val cy = height * 0.5f

            val path = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + 2f, cy + 2f)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            gestureInFlight = true
            val resetRunnable = Runnable { gestureInFlight = false }
            handler.postDelayed(resetRunnable, durationMs + 100L)

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    handler.removeCallbacks(resetRunnable)
                    gestureInFlight = false
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    handler.removeCallbacks(resetRunnable)
                    gestureInFlight = false
                }
            }, null)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "tapCenter exception: ${e.message}", e)
            gestureInFlight = false
            false
        }
    }

    private fun doubleTapCenter(durationMs: Long = 50, delayMs: Long = 100): Boolean {
        if (gestureInFlight) return false
        return try {
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(720)
            val height = metrics.heightPixels.coerceAtLeast(1280)

            val cx = width * 0.5f
            val cy = height * 0.5f

            val path1 = Path().apply { moveTo(cx, cy); lineTo(cx + 2f, cy + 2f) }
            val stroke1 = GestureDescription.StrokeDescription(path1, 0, durationMs)

            val path2 = Path().apply { moveTo(cx, cy); lineTo(cx + 2f, cy + 2f) }
            val stroke2 = GestureDescription.StrokeDescription(path2, durationMs + delayMs, durationMs)

            val gesture = GestureDescription.Builder()
                .addStroke(stroke1)
                .addStroke(stroke2)
                .build()

            gestureInFlight = true
            val totalMs = durationMs + delayMs + durationMs

            val resetRunnable = Runnable { gestureInFlight = false }
            handler.postDelayed(resetRunnable, totalMs + 100L)

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    handler.removeCallbacks(resetRunnable)
                    gestureInFlight = false
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    handler.removeCallbacks(resetRunnable)
                    gestureInFlight = false
                }
            }, null)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "doubleTapCenter exception: ${e.message}", e)
            gestureInFlight = false
            false
        }
    }

    companion object {
        private const val TAG = "TrikiAccessibility"
        private var instanceRef: WeakReference<ScrollAccessibilityService>? = null

        val isRunning: Boolean get() = instanceRef?.get() != null

        /** Swipe from low on screen (82%) to high (18%): advances to the next item in a vertical feed. */
        fun nextItem(): Boolean {
            val service = instanceRef?.get() ?: return false
            return try {
                service.swipe(startYFrac = 0.82f, endYFrac = 0.18f)
            } catch (e: Throwable) {
                false
            }
        }

        /** Swipe from high on screen (18%) to low (82%): goes back to the previous item. */
        fun previousItem(): Boolean {
            val service = instanceRef?.get() ?: return false
            return try {
                service.swipe(startYFrac = 0.18f, endYFrac = 0.82f)
            } catch (e: Throwable) {
                false
            }
        }

        fun tap(): Boolean {
            val service = instanceRef?.get() ?: return false
            return try {
                service.tapCenter()
            } catch (e: Throwable) {
                false
            }
        }

        fun doubleTap(): Boolean {
            val service = instanceRef?.get() ?: return false
            return try {
                service.doubleTapCenter()
            } catch (e: Throwable) {
                false
            }
        }

        fun swipeLeft(): Boolean {
            val service = instanceRef?.get() ?: return false
            return try {
                service.swipeHorizontal(startXFrac = 0.85f, endXFrac = 0.15f)
            } catch (e: Throwable) {
                false
            }
        }

        fun swipeRight(): Boolean {
            val service = instanceRef?.get() ?: return false
            return try {
                service.swipeHorizontal(startXFrac = 0.15f, endXFrac = 0.85f)
            } catch (e: Throwable) {
                false
            }
        }

        fun volumeUp(): Boolean {
            val service = instanceRef?.get() ?: return false
            return try {
                val am = service.getSystemService(AUDIO_SERVICE) as? AudioManager
                am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                true
            } catch (e: Throwable) {
                false
            }
        }

        fun volumeDown(): Boolean {
            val service = instanceRef?.get() ?: return false
            return try {
                val am = service.getSystemService(AUDIO_SERVICE) as? AudioManager
                am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                true
            } catch (e: Throwable) {
                false
            }
        }
    }
}
