package com.loic.wakeup.domain

import com.loic.wakeup.data.AlarmEntity

fun AlarmEntity.requiresGlobalTag(): Boolean = nfcTagUid == null && !dismissWithoutTag

fun AlarmEntity.canActivateWithGlobalTag(globalTagUid: String?): Boolean =
    !requiresGlobalTag() || globalTagUid != null

/**
 * UID of the tag that dismisses this alarm — its custom tag, else the global one — or null when
 * it needs no tag. Mirrors the lookup in [com.loic.wakeup.ui.screens.AlarmRingingActivity].
 */
fun AlarmEntity.effectiveTagUid(globalTagUid: String?): String? =
    if (dismissWithoutTag) null else nfcTagUid ?: globalTagUid

/** Every registered tag, global first, then each alarm's custom tag (deduplicated, in list order). */
fun registeredTagUids(globalTagUid: String?, alarms: List<AlarmEntity>): List<String> =
    (listOfNotNull(globalTagUid) + alarms.mapNotNull { it.nfcTagUid }).distinct()
