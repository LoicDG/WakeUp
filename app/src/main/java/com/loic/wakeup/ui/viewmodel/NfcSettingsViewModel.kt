package com.loic.wakeup.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loic.wakeup.data.AlarmDatabase
import com.loic.wakeup.data.AlarmRepository
import com.loic.wakeup.data.NfcTagStore
import com.loic.wakeup.data.TagFailsafeRepository
import com.loic.wakeup.domain.AlarmScheduler
import com.loic.wakeup.domain.requiresGlobalTag
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NfcSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = NfcTagStore(app)

    private val _uid = MutableStateFlow(store.getUid())
    val uid: StateFlow<String?> = _uid

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    fun startScan() { _scanning.value = true }
    fun cancelScan() { _scanning.value = false }

    fun onTagScanned(hexUid: String) {
        viewModelScope.launch {
            val previousUid = store.getUid()
            store.setUid(hexUid)
            _uid.value = hexUid
            _scanning.value = false
            if (previousUid != null && previousUid != hexUid) carryOverFailsafe(previousUid, hexUid)
        }
    }

    /**
     * Replacing the global tag keeps its failsafe — the new tag most likely lives in the same spot.
     * The old UID's failsafe is left alone (a custom-tag alarm may still use it); if nothing does,
     * [com.loic.wakeup.receiver.FailsafeReceiver] drops it at its next check.
     */
    private suspend fun carryOverFailsafe(fromUid: String, toUid: String) {
        val app = getApplication<Application>()
        val failsafes = TagFailsafeRepository(AlarmDatabase.getInstance(app).tagFailsafeDao())
        val previous = failsafes.getByUid(fromUid) ?: return
        if (failsafes.getByUid(toUid) != null) return
        val carried = previous.copy(tagUid = toUid)
        failsafes.upsert(carried)
        AlarmScheduler(app).scheduleFailsafe(carried)
    }

    suspend fun previewClearCount(): Int {
        val app = getApplication<Application>()
        val repo = AlarmRepository(AlarmDatabase.getInstance(app).alarmDao())
        return repo.getAllEnabled().count { it.requiresGlobalTag() }
    }

    fun clearTag() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val repo = AlarmRepository(AlarmDatabase.getInstance(app).alarmDao())
            val sched = AlarmScheduler(app)
            repo.getAllEnabled()
                .filter { it.requiresGlobalTag() }
                .forEach { alarm ->
                    sched.cancel(alarm.id)
                    repo.setEnabled(alarm.id, false)
                }
            repo.getAllTemporarilyDisabled()
                .filter { it.requiresGlobalTag() }
                .forEach { alarm ->
                    sched.cancel(alarm.id)
                    repo.setEnabled(alarm.id, false)
                }
            store.clear()
            _uid.value = null
        }
    }
}
