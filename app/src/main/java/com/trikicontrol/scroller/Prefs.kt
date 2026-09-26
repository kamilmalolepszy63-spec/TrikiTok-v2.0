package com.trikicontrol.scroller

import android.content.Context
import com.trikicontrol.scroller.gestures.GestureAction

enum class AppTheme(val id: String, val titleResId: Int, val styleResId: Int) {
    PINK("pink", R.string.theme_pink, R.style.Theme_TrikiTok_Pink),
    BLUE("blue", R.string.theme_blue, R.style.Theme_TrikiTok_Blue),
    ORANGE("orange", R.string.theme_orange, R.style.Theme_TrikiTok_Orange),
    GREEN("green", R.string.theme_green, R.style.Theme_TrikiTok_Green),
    PURPLE("purple", R.string.theme_purple, R.style.Theme_TrikiTok_Purple),
    CYAN("cyan", R.string.theme_cyan, R.style.Theme_TrikiTok_Cyan),
    RED("red", R.string.theme_red, R.style.Theme_TrikiTok_Red),
    GOLD("gold", R.string.theme_gold, R.style.Theme_TrikiTok_Gold);

    companion object {
        fun fromIndex(index: Int): AppTheme {
            return values().getOrNull(index) ?: PINK
        }
    }
}

enum class AppLanguage(val code: String, val displayName: String) {
    POLISH("pl", "Polski 🇵🇱"),
    ENGLISH("en", "English 🇬🇧");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return values().firstOrNull { it.code == code } ?: POLISH
        }
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("triki_prefs", Context.MODE_PRIVATE)

    var deviceName: String
        get() = sp.getString(KEY_DEVICE_NAME, "Triki") ?: "Triki"
        set(v) = sp.edit().putString(KEY_DEVICE_NAME, v).apply()

    var rotationThreshold: Float
        get() = sp.getFloat(KEY_ROTATION, 25f)
        set(v) = sp.edit().putFloat(KEY_ROTATION, v).apply()

    var shakeThreshold: Float
        get() = sp.getFloat(KEY_SHAKE, 60f)
        set(v) = sp.edit().putFloat(KEY_SHAKE, v).apply()

    var invertDirection: Boolean
        get() = sp.getBoolean(KEY_INVERT, false)
        set(v) = sp.edit().putBoolean(KEY_INVERT, v).apply()

    var shakeTapEnabled: Boolean
        get() = sp.getBoolean(KEY_SHAKE_TAP, true)
        set(v) = sp.edit().putBoolean(KEY_SHAKE_TAP, v).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(v) = sp.edit().putBoolean(KEY_KEEP_SCREEN_ON, v).apply()

    var hapticFeedbackEnabled: Boolean
        get() = sp.getBoolean(KEY_HAPTIC, true)
        set(v) = sp.edit().putBoolean(KEY_HAPTIC, v).apply()

    var actionRotateCw: GestureAction
        get() = GestureAction.fromId(sp.getString(KEY_ACTION_ROTATE_CW, GestureAction.NEXT_ITEM.id), GestureAction.NEXT_ITEM)
        set(v) = sp.edit().putString(KEY_ACTION_ROTATE_CW, v.id).apply()

    var actionRotateCcw: GestureAction
        get() = GestureAction.fromId(sp.getString(KEY_ACTION_ROTATE_CCW, GestureAction.PREVIOUS_ITEM.id), GestureAction.PREVIOUS_ITEM)
        set(v) = sp.edit().putString(KEY_ACTION_ROTATE_CCW, v.id).apply()

    var actionShake: GestureAction
        get() = GestureAction.fromId(sp.getString(KEY_ACTION_SHAKE, GestureAction.DOUBLE_TAP.id), GestureAction.DOUBLE_TAP)
        set(v) = sp.edit().putString(KEY_ACTION_SHAKE, v.id).apply()

    var appThemeIndex: Int
        get() = sp.getInt(KEY_APP_THEME, 0)
        set(v) = sp.edit().putInt(KEY_APP_THEME, v).apply()

    var appLanguage: AppLanguage
        get() = AppLanguage.fromCode(sp.getString(KEY_APP_LANGUAGE, "pl"))
        set(v) = sp.edit().putString(KEY_APP_LANGUAGE, v.code).apply()

    companion object {
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_ROTATION = "rotation_threshold"
        private const val KEY_SHAKE = "shake_threshold"
        private const val KEY_INVERT = "invert_direction"
        private const val KEY_SHAKE_TAP = "shake_tap_enabled"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_HAPTIC = "haptic_feedback_enabled"

        private const val KEY_ACTION_ROTATE_CW = "action_rotate_cw"
        private const val KEY_ACTION_ROTATE_CCW = "action_rotate_ccw"
        private const val KEY_ACTION_SHAKE = "action_shake"
        private const val KEY_APP_THEME = "app_theme_index"
        private const val KEY_APP_LANGUAGE = "app_language"
    }
}
