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
import com.loic.wakeup.domain.canActivateWithGlobalTag
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AlarmEditViewModel(app: Application) : AndroidViewModel(app) {
    private val repo      = AlarmRepository(AlarmDatabase.getInstance(app).alarmDao())
    private val scheduler = AlarmScheduler(app)
    private val nfcStore  = NfcTagStore(app)

    private val _alarm = MutableStateFlow(AlarmEntity(hour = 7, minute = 0))
    val alarm: StateFlow<AlarmEntity> = _alarm

    private val _errorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String> = _errorEvent

    fun load(id: Int) {
        viewModelScope.launch {
            repo.getById(id)?.let { _alarm.value = it }
        }
    }

    fun update(alarm: AlarmEntity) { _alarm.value = alarm }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            if (!_alarm.value.canActivateWithGlobalTag(nfcStore.getUid())) {
                _errorEvent.tryEmit(getApplication<Application>().getString(R.string.nfc_tag_required))
                return@launch
            }
            val toSave = _alarm.value.copy(enabled = true, temporaryDisabledUntilMillis = null)
            val id     = repo.upsert(toSave).toInt()
            val saved  = toSave.copy(id = if (toSave.id == 0) id else toSave.id)
            scheduler.schedule(saved)
            onDone()
        }
    }
}
