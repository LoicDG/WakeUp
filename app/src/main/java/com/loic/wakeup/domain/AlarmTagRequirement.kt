package com.loic.wakeup.domain

import com.loic.wakeup.data.AlarmEntity

fun AlarmEntity.requiresGlobalTag(): Boolean = nfcTagUid == null && !dismissWithoutTag

fun AlarmEntity.canActivateWithGlobalTag(globalTagUid: String?): Boolean =
    !requiresGlobalTag() || globalTagUid != null
