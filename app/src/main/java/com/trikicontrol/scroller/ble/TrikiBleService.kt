package com.trikicontrol.scroller.ble

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.trikicontrol.scroller.MainActivity
import com.trikicontrol.scroller.Prefs
import com.trikicontrol.scroller.R
import com.trikicontrol.scroller.accessibility.ScrollAccessibilityService
import com.trikicontrol.scroller.gestures.GestureAction
import com.trikicontrol.scroller.gestures.GestureType
import com.trikicontrol.scroller.gestures.TrikiGestureDetector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class MotionState {
    IDLE,
    ROTATE_CW,
    ROTATE_CCW,
    SHAKE
}

data class MotionInfo(
    val state: MotionState,
    val gz: Double,
    val shakeMag: Double,
    val tMs: Long
)

class TrikiBleService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var adapter: BluetoothAdapter

    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var wanted = false // true while the user wants a connection (drives auto-retry)
    private var scanning = false

    private var decoder = SampleDecoder()
    private lateinit var detector: TrikiGestureDetector

    private val idleResetRunnable = Runnable {
        val current = MotionStateLiveData.value ?: return@Runnable
        if (current.state != MotionState.IDLE) {
            MotionStateLiveData.postValue(current.copy(state = MotionState.IDLE))
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        detector = TrikiGestureDetector(prefs.rotationThreshold.toDouble(), prefs.shakeThreshold.toDouble())
        val manager = getSystemService(BluetoothManager::class.java)
        adapter = manager.adapter
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification(getString(R.string.status_disconnected)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                wanted = true
                detector.rotationThreshold = prefs.rotationThreshold.toDouble()
                detector.shakeThreshold = prefs.shakeThreshold.toDouble()
                beginScan()
            }
            ACTION_DISCONNECT -> {
                wanted = false
                teardown()
                setStatus(getString(R.string.status_disconnected), connected = false)
            }
            ACTION_STOP -> {
                wanted = false
                teardown()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        teardown()
        super.onDestroy()
    }

    fun updateThresholds(rotation: Double, shake: Double) {
        detector.rotationThreshold = rotation
        detector.shakeThreshold = shake
    }

    // ---- permissions ------------------------------------------------------
    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasScanPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED

    // ---- scanning -----------------------------------------------------------
    private fun beginScan() {
        if (!hasScanPermission()) {
            setStatus("Missing Bluetooth permission", connected = false)
            return
        }
        if (scanning) return
        val scanner = adapter.bluetoothLeScanner ?: run {
            setStatus("Bluetooth is off", connected = false)
            handler.postDelayed({ if (wanted) beginScan() }, RETRY_DELAY_MS)
            return
        }
        setStatus("Scanning for ${prefs.deviceName}...", connected = false)
        scanning = true
        try {
            scanner.startScan(null, ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(), scanCallback)
        } catch (e: SecurityException) {
            Log.w(TAG, "scan failed: ${e.message}")
            scanning = false
        }
        handler.postDelayed({
            if (scanning) {
                stopScan()
                if (wanted) {
                    setStatus("No device found - retrying...", connected = false)
                    handler.postDelayed({ if (wanted) beginScan() }, RETRY_DELAY_MS)
                }
            }
        }, SCAN_TIMEOUT_MS)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            if (hasScanPermission()) adapter.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.w(TAG, "stopScan failed: ${e.message}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            if (!name.contains(prefs.deviceName, ignoreCase = true)) return
            stopScan()
            connectTo(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed: $errorCode")
            scanning = false
        }
    }

    // ---- connection -----------------------------------------------------
    private fun connectTo(device: BluetoothDevice) {
        if (!hasConnectPermission()) {
            setStatus("Missing Bluetooth permission", connected = false)
            return
        }
        setStatus("Connecting...", connected = false)
        try {
            gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.w(TAG, "connectGatt failed: ${e.message}")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                decoder = SampleDecoder() // fresh startup-discard count for this session
                try {
                    g.discoverServices()
                } catch (e: SecurityException) {
                    Log.w(TAG, "discoverServices failed: ${e.message}")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "GATT disconnected (status=$status)")
                closeGatt()
                BatteryLiveData.postValue(null)
                if (wanted) {
                    setStatus("Disconnected - retrying...", connected = false)
                    handler.postDelayed({ if (wanted) beginScan() }, RETRY_DELAY_MS)
                } else {
                    setStatus(getString(R.string.status_disconnected), connected = false)
                }
            }
        }

        @Suppress("DEPRECATION")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(TrikiProtocol.NUS_SERVICE_UUID)
            val tx = service?.getCharacteristic(TrikiProtocol.NUS_TX_UUID)
            val rx = service?.getCharacteristic(TrikiProtocol.NUS_RX_UUID)
            if (tx == null || rx == null) {
                Log.w(TAG, "Triki UART service/characteristics not found")
                g.disconnect()
                return
            }
            rxChar = rx
            detector.reset()
            try {
                g.setCharacteristicNotification(tx, true)
                val cccd = tx.getDescriptor(TrikiProtocol.CCCD_UUID)
                cccd?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                cccd?.let { g.writeDescriptor(it) }
            } catch (e: SecurityException) {
                Log.w(TAG, "enable notify failed: ${e.message}")
            }
            readBattery(g)
        }

        @Suppress("DEPRECATION")
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == TrikiProtocol.CCCD_UUID) {
                // Keep Triki still while the IMU settles, then start the stream.
                handler.postDelayed({
                    val rx = rxChar ?: return@postDelayed
                    rx.value = TrikiProtocol.START_COMMAND
                    rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    try {
                        g.writeCharacteristic(rx)
                        setStatus("Connected: ${prefs.deviceName}", connected = true)
                    } catch (e: SecurityException) {
                        Log.w(TAG, "start command failed: ${e.message}")
                    }
                }, SETTLE_DELAY_MS)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid != TrikiProtocol.NUS_TX_UUID) return
            val data = characteristic.value ?: return
            val tMs = SystemClock.elapsedRealtime()

            var currentMotion = MotionState.IDLE
            var lastSample: ImuSample? = null

            for (sample in decoder.push(data, tMs)) {
                lastSample = sample
                val rotTh = detector.rotationThreshold
                val shakeTh = detector.shakeThreshold

                val absGz = abs(sample.gz)
                val absGx = abs(sample.gx)
                val absGy = abs(sample.gy)

                val rotVal = when {
                    absGz >= absGx && absGz >= absGy -> sample.gz
                    absGx >= absGy -> sample.gx
                    else -> sample.gy
                }
                val shakeMag = absGx + absGy + absGz

                val sampleMotion = when {
                    rotVal > rotTh -> MotionState.ROTATE_CW
                    rotVal < -rotTh -> MotionState.ROTATE_CCW
                    shakeMag > shakeTh -> MotionState.SHAKE
                    else -> MotionState.IDLE
                }

                if (sampleMotion != MotionState.IDLE) {
                    currentMotion = sampleMotion
                }

                val event = detector.process(sample) ?: continue
                onGesture(event.type)
            }

            if (lastSample != null) {
                val absGz = abs(lastSample.gz)
                val absGx = abs(lastSample.gx)
                val absGy = abs(lastSample.gy)
                val rotVal = when {
                    absGz >= absGx && absGz >= absGy -> lastSample.gz
                    absGx >= absGy -> lastSample.gx
                    else -> lastSample.gy
                }
                val shakeMag = absGx + absGy + absGz
                val info = MotionInfo(currentMotion, rotVal, shakeMag, tMs)
                MotionStateLiveData.postValue(info)

                scheduleIdleReset()
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid == TrikiProtocol.BATTERY_LEVEL_UUID) {
                val value = characteristic.value
                if (value != null && value.isNotEmpty()) {
                    BatteryLiveData.postValue(value[0].toInt() and 0xFF)
                }
            }
        }
    }

    private fun scheduleIdleReset() {
        handler.removeCallbacks(idleResetRunnable)
        handler.postDelayed(idleResetRunnable, 400L)
    }

    private fun readBattery(g: BluetoothGatt) {
        val char = g.getService(TrikiProtocol.BATTERY_SERVICE_UUID)?.getCharacteristic(TrikiProtocol.BATTERY_LEVEL_UUID)
            ?: return
        try {
            g.readCharacteristic(char)
        } catch (e: SecurityException) {
            Log.w(TAG, "readCharacteristic failed: ${e.message}")
        }
    }

    private fun dispatchAction(action: GestureAction): Boolean {
        return when (action) {
            GestureAction.NEXT_ITEM -> ScrollAccessibilityService.nextItem()
            GestureAction.PREVIOUS_ITEM -> ScrollAccessibilityService.previousItem()
            GestureAction.SINGLE_TAP -> ScrollAccessibilityService.tap()
            GestureAction.DOUBLE_TAP -> ScrollAccessibilityService.doubleTap()
            GestureAction.SWIPE_LEFT -> ScrollAccessibilityService.swipeLeft()
            GestureAction.SWIPE_RIGHT -> ScrollAccessibilityService.swipeRight()
            GestureAction.VOLUME_UP -> ScrollAccessibilityService.volumeUp()
            GestureAction.VOLUME_DOWN -> ScrollAccessibilityService.volumeDown()
            GestureAction.NONE -> false
        }
    }

    private fun onGesture(type: GestureType) {
        val invert = prefs.invertDirection
        val isAccRunning = ScrollAccessibilityService.isRunning
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val rawAction = when (type) {
            GestureType.ROTATE_CW -> if (invert) prefs.actionRotateCcw else prefs.actionRotateCw
            GestureType.ROTATE_CCW -> if (invert) prefs.actionRotateCcw else prefs.actionRotateCcw
            GestureType.SHAKE -> prefs.actionShake
        }

        if (rawAction == GestureAction.NONE) {
            LastGestureLiveData.postValue("Ostatnia akcja: Wyłączone [$timeStr]")
            return
        }

        val dispatched = dispatchAction(rawAction)
        val actionText = getString(rawAction.titleResId)

        if (dispatched && prefs.hapticFeedbackEnabled) {
            val vib = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                vib?.vibrate(VibrationEffect.createOneShot(12, 30))
            }
        }

        val statusMsg = if (isAccRunning) {
            if (dispatched) {
                "Ostatnia akcja: $actionText [$timeStr]"
            } else {
                "Ostatnia akcja: $actionText [$timeStr] ⚠️ Błąd wysłania!"
            }
        } else {
            "Ostatnia akcja: $actionText [$timeStr] ⚠️ Dostępność WYŁĄCZONA!"
        }

        LastGestureLiveData.postValue(statusMsg)
    }

    private fun closeGatt() {
        try {
            if (hasConnectPermission()) {
                gatt?.disconnect()
                gatt?.close()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "close failed: ${e.message}")
        }
        gatt = null
        rxChar = null
    }

    private fun teardown() {
        stopScan()
        closeGatt()
    }

    // ---- status / notification -------------------------------------------
    private fun setStatus(text: String, connected: Boolean) {
        StatusLiveData.postValue(text)
        ConnectedLiveData.postValue(connected)
        val notif = getSystemService(NotificationManager::class.java)
        notif.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "TrikiBleService"
        private const val CHANNEL_ID = "triki_ble"
        private const val NOTIF_ID = 42
        private const val SCAN_TIMEOUT_MS = 30_000L
        private const val SETTLE_DELAY_MS = 3_000L
        private const val RETRY_DELAY_MS = 4_000L

        const val ACTION_CONNECT = "com.trikicontrol.scroller.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.trikicontrol.scroller.ACTION_DISCONNECT"
        const val ACTION_STOP = "com.trikicontrol.scroller.ACTION_STOP"

        val StatusLiveData = MutableLiveData("Disconnected")
        val ConnectedLiveData = MutableLiveData(false)
        val BatteryLiveData = MutableLiveData<Int?>(null)
        val MotionStateLiveData = MutableLiveData<MotionInfo>(MotionInfo(MotionState.IDLE, 0.0, 0.0, 0L))
        val LastGestureLiveData = MutableLiveData<String>("")

        var instance: TrikiBleService? = null
            private set

        fun connect(context: Context) {
            val intent = Intent(context, TrikiBleService::class.java).setAction(ACTION_CONNECT)
            ContextCompat.startForegroundService(context, intent)
        }

        fun disconnect(context: Context) {
            context.startService(Intent(context, TrikiBleService::class.java).setAction(ACTION_DISCONNECT))
        }
    }
}
