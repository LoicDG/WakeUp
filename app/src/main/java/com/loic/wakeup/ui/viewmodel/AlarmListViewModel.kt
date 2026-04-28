package com.loic.wakeup.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loic.wakeup.R
import com.loic.wakeup.data.AlarmDatabase
import com.loic.wakeup.data.AlarmEntity
import com.loic.wakeup.data.AlarmRepository
import com.loic.wakeup.data.NfcTagStore
import com.loic.wakeup.domain.AlarmScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo      = AlarmRepository(AlarmDatabase.getInstance(app).alarmDao())
    private val scheduler = AlarmScheduler(app)
    private val nfcStore  = NfcTagStore(app)

    val alarms: StateFlow<List<AlarmEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _errorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String> = _errorEvent

    fun setEnabled(alarm: AlarmEntity, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && alarm.nfcTagUid == null && nfcStore.getUid() == null) {
                _errorEvent.tryEmit(getApplication<Application>().getString(R.string.nfc_tag_required))
                return@launch
            }
            repo.setEnabled(alarm.id, enabled)
            if (enabled) scheduler.schedule(alarm.copy(enabled = true))
            else scheduler.cancel(alarm.id)
        }
    }

    fun delete(alarm: AlarmEntity) {
        viewModelScope.launch {
            scheduler.cancel(alarm.id)
            repo.delete(alarm)
        }
    }
}
