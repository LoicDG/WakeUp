package com.loic.wakeup.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loic.wakeup.data.AlarmEntity
import com.loic.wakeup.domain.NextTriggerCalculator
import com.loic.wakeup.ui.components.TimeText
import com.loic.wakeup.ui.theme.auroraSky
import com.loic.wakeup.ui.theme.frostedPanel
import com.loic.wakeup.ui.viewmodel.AlarmListViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlarmListScreen(
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onSettings: () -> Unit,
    vm: AlarmListViewModel = viewModel()
) {
    val alarms by vm.alarms.collectAsState()
    // Ids of alarms the user just toggled off in this composition. Plain `remember`
    // (not rememberSaveable) so it clears when the list leaves composition — i.e. the
    // "Turn back on" button only shows right after toggling off, gone after navigating away.
    val justDisabledIds = remember { mutableStateListOf<Int>() }
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            currentTime = System.currentTimeMillis()
        }
    }

    LaunchedEffect(vm) {
        vm.errorEvent.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    // The soonest enabled alarm drives the hero. Temporarily-disabled alarms
    // (enabled == false, re-enable scheduled) won't ring at their next occurrence,
    // so they're correctly excluded here.
    val nextAlarm = remember(alarms, currentTime) {
        alarms.filter { it.enabled }
            .minByOrNull { NextTriggerCalculator.next(it.hour, it.minute, it.daysMask, currentTime) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(4.dp, 8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add alarm", modifier = Modifier.size(28.dp))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .auroraSky()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Title scrolls with the list — no background card, it just sits on the aurora.
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                "WAKE UP",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 4.sp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Alarms",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .frostedPanel(CircleShape)
                                .clickable(onClick = onSettings),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                item {
                    NextAlarmHero(
                        nextAlarm = nextAlarm,
                        hasAlarms = alarms.isNotEmpty(),
                        currentTime = currentTime,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        currentTime = currentTime,
                        justTurnedOff = alarm.id in justDisabledIds,
                        onToggle = { enabled ->
                            vm.setEnabled(alarm, enabled)
                            if (!enabled) justDisabledIds.add(alarm.id)
                            else justDisabledIds.remove(alarm.id)
                        },
                        onTurnBackOnAfterNextRing = { vm.turnBackOnAfterNextRing(alarm) },
                        onEdit = { onEdit(alarm.id) },
                        onDelete = { vm.delete(alarm) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NextAlarmHero(
    nextAlarm: AlarmEntity?,
    hasAlarms: Boolean,
    currentTime: Long,
    modifier: Modifier = Modifier,
) {
    // A quiet, single-line information strip — deliberately smaller than an alarm card.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .frostedPanel(RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (nextAlarm == null) {
            // Truthful for both states: no alarms yet, or all toggled off.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "ALL QUIET",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (hasAlarms) "No alarm scheduled — toggle one on" else "No alarms yet — tap + to start",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "NEXT ALARM",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${formatNextRingDay(currentTime, nextAlarm)} · ${formatCountdown(currentTime, nextAlarm)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TimeText(
                hour = nextAlarm.hour,
                minute = nextAlarm.minute,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: AlarmEntity,
    currentTime: Long,
    justTurnedOff: Boolean,
    onToggle: (Boolean) -> Unit,
    onTurnBackOnAfterNextRing: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frostedPanel(shape)
            .clickable(onClick = onEdit),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    TimeText(
                        hour = alarm.hour,
                        minute = alarm.minute,
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (alarm.enabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(4.dp))
                    if (alarm.label.isNotEmpty()) {
                        Text(
                            text = alarm.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    DayDots(mask = alarm.daysMask, enabled = alarm.enabled)
                    if (alarm.enabled) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = formatCountdown(currentTime, alarm),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Switch(
                        checked = alarm.enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // The "Turn back on" button is transient — only right after the user toggles
            // this alarm off (justTurnedOff). The "Turns back on for X" text is persistent
            // info shown whenever a re-enable is scheduled (temporaryDisabledUntilMillis set).
            val showTurnBackOnButton =
                alarm.daysMask != 0 && !alarm.enabled &&
                    alarm.temporaryDisabledUntilMillis == null && justTurnedOff
            val showTurnsBackOnText =
                alarm.daysMask != 0 && !alarm.enabled &&
                    alarm.temporaryDisabledUntilMillis != null
            if (showTurnBackOnButton || showTurnsBackOnText) {
                val reenableDay = alarm.temporaryDisabledUntilMillis?.let { reenableAt ->
                    formatNextRingAfter(alarm, reenableAt)
                } ?: formatTurnBackOnDay(alarm, currentTime)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                if (showTurnBackOnButton) {
                    TextButton(
                        onClick = onTurnBackOnAfterNextRing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Turn back on for $reenableDay")
                    }
                } else {
                    Text(
                        text = "Turns back on for $reenableDay",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayDots(mask: Int, enabled: Boolean) {
    if (mask == 0) {
        Text(
            "ONE TIME",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = if (enabled)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        return
    }
    val letters = listOf("S", "M", "T", "W", "T", "F", "S")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        letters.forEachIndexed { bit, letter ->
            val active = mask and (1 shl bit) != 0
            Text(
                text = letter,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    active && enabled -> MaterialTheme.colorScheme.primary
                    active -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                },
            )
        }
    }
}

internal fun formatDuration(diffMillis: Long): String {
    val diffMin = (diffMillis / 60_000).coerceAtLeast(0)
    val hours = diffMin / 60
    val minutes = diffMin % 60
    return if (hours > 0) "Rings in ${hours}h ${minutes}min"
    else "Rings in ${minutes}min"
}

internal fun formatCountdown(now: Long, alarm: AlarmEntity): String {
    val triggerMs = NextTriggerCalculator.next(alarm.hour, alarm.minute, alarm.daysMask, now)
    return formatDuration(triggerMs - now)
}

internal fun formatNextRingDay(now: Long, alarm: AlarmEntity): String {
    val triggerMs = NextTriggerCalculator.next(alarm.hour, alarm.minute, alarm.daysMask, now)
    return formatDayName(triggerMs)
}

internal fun formatTurnBackOnDay(alarm: AlarmEntity, fromMillis: Long): String {
    val triggerMs = nextRingAfterSkippedOccurrence(alarm, fromMillis)
    return formatDayName(triggerMs)
}

internal fun formatNextRingAfter(alarm: AlarmEntity, fromMillis: Long): String {
    val triggerMs = nextRingAfter(alarm, fromMillis)
    return formatDayName(triggerMs)
}

internal fun nextRingAfterSkippedOccurrence(alarm: AlarmEntity, fromMillis: Long): Long {
    val skippedTriggerMs = NextTriggerCalculator.next(alarm.hour, alarm.minute, alarm.daysMask, fromMillis)
    return nextRingAfter(alarm, skippedTriggerMs)
}

internal fun nextRingAfter(alarm: AlarmEntity, fromMillis: Long): Long =
    NextTriggerCalculator.next(alarm.hour, alarm.minute, alarm.daysMask, fromMillis + 1)

private fun formatDayName(millis: Long): String =
    SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(millis))
