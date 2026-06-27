package com.loic.wakeup.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide app preferences backed by plain [SharedPreferences]. Exposed as a
 * [StateFlow] so every screen reflects a change the moment it's made. Call [init]
 * once from [com.loic.wakeup.WakeUpApp.onCreate] before any reader runs.
 *
 * Only holds the clock format for now: `false` = 12-hour with AM/PM (default),
 * `true` = 24-hour.
 */
object SettingsStore {
    private const val PREFS = "wakeup_settings"
    private const val KEY_24H = "use_24_hour"

    private lateinit var prefs: SharedPreferences
    private val _use24Hour = MutableStateFlow(false)
    val use24Hour: StateFlow<Boolean> = _use24Hour.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _use24Hour.value = prefs.getBoolean(KEY_24H, false)
    }

    fun setUse24Hour(value: Boolean) {
        _use24Hour.value = value
        prefs.edit().putBoolean(KEY_24H, value).apply()
    }
}
