package com.trikicontrol.scroller

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.trikicontrol.scroller.accessibility.ScrollAccessibilityService
import com.trikicontrol.scroller.ble.MotionState
import com.trikicontrol.scroller.ble.TrikiBleService
import com.trikicontrol.scroller.databinding.ActivityMainBinding
import com.trikicontrol.scroller.databinding.DialogSettingsBinding
import com.trikicontrol.scroller.gestures.GestureAction

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result observed indirectly */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)

        val langCode = prefs.appLanguage.code
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langCode))

        val selectedTheme = AppTheme.fromIndex(prefs.appThemeIndex)
        setTheme(selectedTheme.styleResId)

        if (prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.btnConnect.setOnClickListener { TrikiBleService.connect(this) }
        binding.btnDisconnect.setOnClickListener { TrikiBleService.disconnect(this) }
        binding.btnHomeGrantPermissions.setOnClickListener { requestBluetoothPermissions() }
        binding.btnHomeAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        TrikiBleService.StatusLiveData.observe(this) { binding.textStatus.text = it }
        TrikiBleService.BatteryLiveData.observe(this) { battery ->
            binding.textBattery.text = if (battery != null) getString(R.string.label_battery) + ": $battery%" else ""
        }

        TrikiBleService.MotionStateLiveData.observe(this) { info ->
            val (state, gz, shakeMag) = info
            val rotTh = prefs.rotationThreshold
            val shakeTh = prefs.shakeThreshold

            binding.textImuValues.text = getString(
                R.string.label_imu_format,
                gz, rotTh, shakeMag, shakeTh
            )

            when (state) {
                MotionState.ROTATE_CW -> {
                    binding.textMotionState.setText(R.string.motion_rotate_cw)
                    binding.textMotionState.setBackgroundColor(ContextCompat.getColor(this, R.color.motion_cw_bg))
                    binding.textMotionState.setTextColor(ContextCompat.getColor(this, R.color.motion_cw_text))
                }
                MotionState.ROTATE_CCW -> {
                    binding.textMotionState.setText(R.string.motion_rotate_ccw)
                    binding.textMotionState.setBackgroundColor(ContextCompat.getColor(this, R.color.motion_ccw_bg))
                    binding.textMotionState.setTextColor(ContextCompat.getColor(this, R.color.motion_ccw_text))
                }
                MotionState.SHAKE -> {
                    binding.textMotionState.setText(R.string.motion_shake)
                    binding.textMotionState.setBackgroundColor(ContextCompat.getColor(this, R.color.motion_shake_bg))
                    binding.textMotionState.setTextColor(ContextCompat.getColor(this, R.color.motion_shake_text))
                }
                MotionState.IDLE -> {
                    binding.textMotionState.setText(R.string.motion_idle)
                    binding.textMotionState.setBackgroundColor(ContextCompat.getColor(this, R.color.motion_idle_bg))
                    binding.textMotionState.setTextColor(ContextCompat.getColor(this, R.color.motion_idle_text))
                }
            }
        }

        TrikiBleService.LastGestureLiveData.observe(this) { lastGesture ->
            if (lastGesture.isNotEmpty()) {
                binding.textLastGesture.text = lastGesture
            } else {
                binding.textLastGesture.setText(R.string.label_last_gesture)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.textAccessibilityWarning.visibility =
            if (ScrollAccessibilityService.isRunning) View.GONE else View.VISIBLE
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            showSettingsDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val dialogBinding = DialogSettingsBinding.bind(dialogView)

        dialogBinding.editRotationThreshold.setText(prefs.rotationThreshold.toString())
        dialogBinding.editShakeThreshold.setText(prefs.shakeThreshold.toString())
        dialogBinding.editDeviceName.setText(prefs.deviceName)
        dialogBinding.switchInvert.isChecked = prefs.invertDirection
        dialogBinding.switchShakeTap.isChecked = prefs.shakeTapEnabled
        dialogBinding.switchKeepScreenOn.isChecked = prefs.keepScreenOn
        dialogBinding.switchHaptic.isChecked = prefs.hapticFeedbackEnabled

        var tempCw = prefs.actionRotateCw
        var tempCcw = prefs.actionRotateCcw
        var tempShake = prefs.actionShake

        dialogBinding.btnActionCw.text = getString(tempCw.titleResId)
        dialogBinding.btnActionCcw.text = getString(tempCcw.titleResId)
        dialogBinding.btnActionShake.text = getString(tempShake.titleResId)

        dialogBinding.btnActionCw.setOnClickListener {
            showActionDialog(getString(R.string.label_action_cw), tempCw) { action ->
                tempCw = action
                dialogBinding.btnActionCw.text = getString(action.titleResId)
            }
        }
        dialogBinding.btnActionCcw.setOnClickListener {
            showActionDialog(getString(R.string.label_action_ccw), tempCcw) { action ->
                tempCcw = action
                dialogBinding.btnActionCcw.text = getString(action.titleResId)
            }
        }
        dialogBinding.btnActionShake.setOnClickListener {
            showActionDialog(getString(R.string.label_action_shake), tempShake) { action ->
                tempShake = action
                dialogBinding.btnActionShake.text = getString(action.titleResId)
            }
        }

        dialogBinding.btnChangeLanguage.setOnClickListener { showLanguageDialog() }
        dialogBinding.btnChangeTheme.setOnClickListener { showThemeDialog() }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogBinding.btnSave.setOnClickListener {
            dialogBinding.editRotationThreshold.text.toString().toFloatOrNull()?.let { prefs.rotationThreshold = it }
            dialogBinding.editShakeThreshold.text.toString().toFloatOrNull()?.let { prefs.shakeThreshold = it }
            prefs.deviceName = dialogBinding.editDeviceName.text.toString().ifBlank { "Triki" }
            prefs.invertDirection = dialogBinding.switchInvert.isChecked
            prefs.shakeTapEnabled = dialogBinding.switchShakeTap.isChecked
            prefs.keepScreenOn = dialogBinding.switchKeepScreenOn.isChecked
            prefs.hapticFeedbackEnabled = dialogBinding.switchHaptic.isChecked

            prefs.actionRotateCw = tempCw
            prefs.actionRotateCcw = tempCcw
            prefs.actionShake = tempShake

            if (prefs.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            TrikiBleService.instance?.updateThresholds(
                prefs.rotationThreshold.toDouble(),
                prefs.shakeThreshold.toDouble()
            )

            Toast.makeText(this, R.string.msg_settings_saved, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showActionDialog(title: String, current: GestureAction, onSelected: (GestureAction) -> Unit) {
        val actions = GestureAction.values()
        val names = actions.map { getString(it.titleResId) }.toTypedArray()
        val currentIndex = actions.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                onSelected(actions[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showLanguageDialog() {
        val languages = AppLanguage.values()
        val names = languages.map { it.displayName }.toTypedArray()
        val currentIndex = languages.indexOf(prefs.appLanguage).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_language_title)
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                val selectedLang = languages[which]
                prefs.appLanguage = selectedLang
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selectedLang.code))
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showThemeDialog() {
        val themes = AppTheme.values()
        val names = themes.map { getString(it.titleResId) }.toTypedArray()
        val currentIndex = prefs.appThemeIndex.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_theme_title)
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                prefs.appThemeIndex = which
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun requestBluetoothPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        requestPermissions.launch(perms.toTypedArray())
    }
}
