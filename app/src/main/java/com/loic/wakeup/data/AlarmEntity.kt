package com.loic.wakeup.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    /** Bitmask: bit 0 = Sunday … bit 6 = Saturday. 0 = one-shot. */
    val daysMask: Int = 0,
    val label: String = "",
    /** URI string of the chosen ringtone, or empty string for default. */
    val ringtoneUri: String = "",
    val enabled: Boolean = true,
    val snoozeCount: Int = 0,
    val maxSnoozes: Int = 3,
    val snoozeDurationSeconds: Int = 20,
    val nfcTagUid: String? = null,
    val temporaryDisabledUntilMillis: Long? = null,
    val dismissWithoutTag: Boolean = false,
)
