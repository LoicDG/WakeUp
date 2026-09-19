package com.loic.wakeup.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loic.wakeup.R
import com.loic.wakeup.data.AlarmDatabase
import com.loic.wakeup.data.AlarmEntity
import com.loic.wakeup.data.AlarmRepository
import com.loic.wakeup.data.NfcTagStore
import com.loic.wakeup.data.TagFailsafeEntity
import com.loic.wakeup.data.TagFailsafeRepository
import com.loic.wakeup.domain.AlarmScheduler
import com.loic.wakeup.domain.CurrentLocation
import com.loic.wakeup.domain.GeoPoint
import com.loic.wakeup.domain.effectiveTagUid
import com.loic.wakeup.domain.registeredTagUids
import com.loic.wakeup.domain.spot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One registered NFC tag and its failsafe, as the failsafe screen shows it. */
data class TagFailsafeItem(
    val tagUid: String,
    val isGlobal: Boolean,
    /** Alarms this tag dismisses. */
    val alarms: List<AlarmEntity>,
    /** The stored failsafe, or defaults (off, no spot) when none exists yet. */
    val failsafe: TagFailsafeEntity,
)

class FailsafeSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val failsafeRepo = TagFailsafeRepository(AlarmDatabase.getInstance(app).tagFailsafeDao())
    private val scheduler    = AlarmScheduler(app)
    private val globalUid    = NfcTagStore(app).getUid()

    val tags: StateFlow<List<TagFailsafeItem>> = combine(
        AlarmRepository(AlarmDatabase.getInstance(app).alarmDao()).observeAll(),
        failsafeRepo.observeAll(),
    ) { alarms, failsafes ->
        val byUid = failsafes.associateBy { it.tagUid }
        registeredTagUids(globalUid, alarms).map { uid ->
            TagFailsafeItem(
                tagUid = uid,
                isGlobal = uid == globalUid,
                alarms = alarms.filter { it.effectiveTagUid(globalUid) == uid },
                failsafe = byUid[uid] ?: TagFailsafeEntity(tagUid = uid),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** UID of the tag whose spot is being set from the current location, if any. */
    private val _locatingUid = MutableStateFlow<String?>(null)
    val locatingUid: StateFlow<String?> = _locatingUid

    private val _errorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String> = _errorEvent

    // Serialises read-modify-write edits so quick successive changes never overwrite each other.
    private val editLock = Mutex()

    fun setEnabled(tagUid: String, enabled: Boolean) = edit(tagUid) { current ->
        if (enabled && current.spot == null) {
            _errorEvent.tryEmit(getApplication<Application>().getString(R.string.failsafe_spot_required))
            null
        } else {
            current.copy(enabled = enabled)
        }
    }

    fun setCheckTime(tagUid: String, hour: Int, minute: Int) =
        edit(tagUid) { it.copy(hour = hour, minute = minute) }

    fun setSpot(tagUid: String, point: GeoPoint) =
        edit(tagUid) { it.copy(latitude = point.latitude, longitude = point.longitude) }

    /** Sets the tag's spot to where the device is now. Caller ensures precise location access. */
    fun useCurrentLocation(tagUid: String) {
        if (_locatingUid.value != null) return
        _locatingUid.value = tagUid
        viewModelScope.launch {
            val fix = try {
                CurrentLocation.get(getApplication())
            } finally {
                _locatingUid.value = null
            }
            if (fix == null) {
                _errorEvent.tryEmit(getApplication<Application>().getString(R.string.failsafe_locate_failed))
            } else {
                setSpot(tagUid, GeoPoint(fix.latitude, fix.longitude))
            }
        }
    }

    /** Applies [change] to the stored failsafe (or defaults) and reschedules; null aborts. */
    private fun edit(tagUid: String, change: (TagFailsafeEntity) -> TagFailsafeEntity?) {
        viewModelScope.launch {
            editLock.withLock {
                val current = failsafeRepo.getByUid(tagUid) ?: TagFailsafeEntity(tagUid = tagUid)
                val updated = change(current) ?: return@withLock
                failsafeRepo.upsert(updated)
                scheduler.scheduleFailsafe(updated) // cancels instead when off or spot-less
            }
        }
    }
}
