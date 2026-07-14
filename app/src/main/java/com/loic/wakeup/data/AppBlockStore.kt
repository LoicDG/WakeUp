package com.loic.wakeup.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide preferences for the app-blocking feature, backed by plain
 * [SharedPreferences] and exposed as [StateFlow]s so the settings UI and the
 * accessibility service both see changes immediately. Call [init] once from
 * [com.loic.wakeup.WakeUpApp.onCreate] before any reader runs.
 *
 * Holds two things:
 *  - [enabled]: master on/off for the blocker.
 *  - [allowedPackages]: package names that stay usable while an alarm is ringing.
 */
object AppBlockStore {
    private const val PREFS = "wakeup_app_block"
    private const val KEY_ENABLED = "blocking_enabled"
    private const val KEY_ALLOWED = "allowed_packages"
    private const val KEY_SEEDED = "allowed_seeded"
    private const val KEY_POWER_MENU_GUARD = "power_menu_guard_enabled"

    private lateinit var prefs: SharedPreferences

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _allowedPackages = MutableStateFlow<Set<String>>(emptySet())
    val allowedPackages: StateFlow<Set<String>> = _allowedPackages.asStateFlow()

    // Best-effort power-menu guard — independent of app blocking, but served by the same
    // accessibility service. On by default: it only ever acts while an alarm is ringing, and
    // suppressing the power menu then is the point of enabling the service at all.
    private val _powerMenuGuardEnabled = MutableStateFlow(true)
    val powerMenuGuardEnabled: StateFlow<Boolean> = _powerMenuGuardEnabled.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
        _powerMenuGuardEnabled.value = prefs.getBoolean(KEY_POWER_MENU_GUARD, true)
        // getStringSet may hand back the very instance it stores, so copy defensively.
        _allowedPackages.value = prefs.getStringSet(KEY_ALLOWED, emptySet())!!.toSet()
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun setPowerMenuGuardEnabled(value: Boolean) {
        _powerMenuGuardEnabled.value = value
        prefs.edit().putBoolean(KEY_POWER_MENU_GUARD, value).apply()
    }

    /**
     * On first run only, seed the allow-list with sensible defaults (dialer/SMS/settings,
     * resolved by the caller). Guarded by a flag so that once the user has curated the list —
     * even down to nothing — we never silently re-add apps.
     */
    fun seedDefaultsIfNeeded(defaults: Set<String>) {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        val next = _allowedPackages.value + defaults
        _allowedPackages.value = next
        prefs.edit()
            .putStringSet(KEY_ALLOWED, next)
            .putBoolean(KEY_SEEDED, true)
            .apply()
    }

    fun setAllowed(packageName: String, allowed: Boolean) {
        val next = _allowedPackages.value.toMutableSet()
        if (allowed) next.add(packageName) else next.remove(packageName)
        _allowedPackages.value = next
        prefs.edit().putStringSet(KEY_ALLOWED, next).apply()
    }
}
