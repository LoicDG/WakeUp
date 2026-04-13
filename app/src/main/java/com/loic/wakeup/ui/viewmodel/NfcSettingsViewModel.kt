package com.loic.wakeup.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loic.wakeup.data.AlarmDatabase
import com.loic.wakeup.data.AlarmRepository
import com.loic.wakeup.data.NfcTagStore
import com.loic.wakeup.domain.AlarmScheduler
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
            store.setUid(hexUid)
            _uid.value = hexUid
            _scanning.value = false
        }
    }

    fun clearTag() {
        viewModelScope.launch {
            val app  = getApplication<Application>()
            val repo = AlarmRepository(AlarmDatabase.getInstance(app).alarmDao())
            val sched = AlarmScheduler(app)
            // Cancel all scheduled alarms before disabling them in DB
            repo.getAllEnabled().forEach { sched.cancel(it.id) }
            repo.disableAll()
            store.clear()
            _uid.value = null
        }
    }
}
