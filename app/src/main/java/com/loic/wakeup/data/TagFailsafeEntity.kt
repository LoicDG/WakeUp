package com.loic.wakeup.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-NFC-tag location failsafe. Every day at [hour]:[minute] the app checks where the device is;
 * if it's farther than [com.loic.wakeup.domain.FailsafePolicy.RADIUS_METERS] from the tag's spot
 * ([latitude], [longitude]), the alarms this tag dismisses are deactivated until the next check —
 * so an alarm can't ring while its tag is out of reach.
 *
 * Keyed by the tag's lowercase hex UID, the same form as [NfcTagStore] and [AlarmEntity.nfcTagUid].
 * The spot is null until the user picks one; a failsafe without a spot is never scheduled.
 */
@Entity(tableName = "tag_failsafes")
data class TagFailsafeEntity(
    @PrimaryKey val tagUid: String,
    val enabled: Boolean = false,
    val hour: Int = 22,
    val minute: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
