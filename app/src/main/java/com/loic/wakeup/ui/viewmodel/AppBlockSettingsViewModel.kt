package com.loic.wakeup.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loic.wakeup.data.AppBlockStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One installed, launchable app shown in the allow-list picker. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

class AppBlockSettingsViewModel(app: Application) : AndroidViewModel(app) {

    val enabled: StateFlow<Boolean> = AppBlockStore.enabled
    val powerMenuGuard: StateFlow<Boolean> = AppBlockStore.powerMenuGuardEnabled
    val allowedPackages: StateFlow<Set<String>> = AppBlockStore.allowedPackages

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        seedDefaults()
        loadApps()
    }

    fun setEnabled(value: Boolean) = AppBlockStore.setEnabled(value)

    fun setPowerMenuGuard(value: Boolean) = AppBlockStore.setPowerMenuGuardEnabled(value)

    fun setAllowed(packageName: String, allowed: Boolean) =
        AppBlockStore.setAllowed(packageName, allowed)

    /** Seed the allow-list with the phone's default dialer, SMS app and Settings (first run only). */
    private fun seedDefaults() {
        val ctx = getApplication<Application>()
        val defaults = buildSet {
            (ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage)?.let(::add)
            Telephony.Sms.getDefaultSmsPackage(ctx)?.let(::add)
            Intent(Settings.ACTION_SETTINGS)
                .resolveActivity(ctx.packageManager)?.packageName?.let(::add)
        }
        if (defaults.isNotEmpty()) AppBlockStore.seedDefaultsIfNeeded(defaults)
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val pm = ctx.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(launcher, 0)
                .asSequence()
                .map { it.activityInfo.packageName }
                .filter { it != ctx.packageName }   // WakeUp is always implicitly allowed
                .distinct()
                .map { pkg ->
                    val info = pm.getApplicationInfo(pkg, 0)
                    InstalledApp(
                        packageName = pkg,
                        label = pm.getApplicationLabel(info).toString(),
                        icon = runCatching { pm.getApplicationIcon(info).toBitmap().asImageBitmap() }
                            .getOrNull(),
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()
            _apps.value = list
            _loading.value = false
        }
    }
}
