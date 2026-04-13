package com.loic.wakeup.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loic.wakeup.R
import com.loic.wakeup.ui.viewmodel.AlarmEditViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarmId: Int?,
    onDone: () -> Unit,
    vm: AlarmEditViewModel = viewModel()
) {
    LaunchedEffect(alarmId) { alarmId?.let { vm.load(it) } }

    val alarm by vm.alarm.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.errorEvent.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data
                ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                ?.toString() ?: ""
            vm.update(alarm.copy(ringtoneUri = uri))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (alarmId == null) "New Alarm" else "Edit Alarm",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { vm.save(onDone) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(
                            stringResource(R.string.save),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // Wheel time picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WheelPicker(
                    count = 24,
                    value = alarm.hour,
                    onValueChange = { vm.update(alarm.copy(hour = it)) },
                    modifier = Modifier.width(100.dp),
                )
                Text(
                    ":",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                WheelPicker(
                    count = 60,
                    value = alarm.minute,
                    onValueChange = { vm.update(alarm.copy(minute = it)) },
                    modifier = Modifier.width(100.dp),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Label
            OutlinedTextField(
                value = alarm.label,
                onValueChange = { vm.update(alarm.copy(label = it)) },
                label = { Text(stringResource(R.string.label_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )

            // Repeat days
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "REPEAT",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DaySelector(
                    mask = alarm.daysMask,
                    onMaskChange = { vm.update(alarm.copy(daysMask = it)) },
                )
            }

            // Ringtone
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "RINGTONE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            if (alarm.ringtoneUri.isNotEmpty()) {
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    Uri.parse(alarm.ringtoneUri)
                                )
                            }
                        }
                        ringtoneLauncher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(stringResource(R.string.ringtone))
                }
            }

            // Snooze
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "SNOOZE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    NumberField(
                        label = stringResource(R.string.snooze_duration),
                        value = alarm.snoozeDurationSeconds,
                        range = 5..300,
                        modifier = Modifier.weight(1f),
                        onValue = { vm.update(alarm.copy(snoozeDurationSeconds = it)) },
                    )
                    NumberField(
                        label = stringResource(R.string.max_snoozes),
                        value = alarm.maxSnoozes,
                        range = 0..10,
                        modifier = Modifier.weight(1f),
                        onValue = { vm.update(alarm.copy(maxSnoozes = it)) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WheelPicker(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 56.dp,
    visibleCount: Int = 5,
) {
    val halfVisible = visibleCount / 2
    // Huge virtual list for infinite wrapping; start in the middle so we can scroll both ways.
    // With SnapPosition.Center the snapped item is centered, meaning firstVisibleItemIndex
    // points to the TOP item, so the centered (selected) virtual index = firstVisible + halfVisible.
    val virtualCount = count * 10_000
    // Place `value` at the center slot on first composition
    val initialIndex = (count * 5_000 + value - halfVisible).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState, snapPosition = SnapPosition.Center)
    val background = MaterialTheme.colorScheme.background

    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    // Sync list to value when changed externally (e.g. loading an existing alarm)
    LaunchedEffect(value) {
        if (!listState.isScrollInProgress) {
            val centerVirtual = listState.firstVisibleItemIndex + halfVisible
            val currentValue = centerVirtual % count
            if (currentValue != value) {
                val base = centerVirtual - currentValue
                listState.scrollToItem((base + value - halfVisible).coerceAtLeast(0))
            }
        }
    }

    // Report selection once scroll settles on a new item
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .drop(1) // skip the initial false at composition time
            .collect {
                val settled = (listState.firstVisibleItemIndex + halfVisible) % count
                if (settled != currentValue) currentOnValueChange(settled)
            }
    }

    // Virtual index of the centered (selected) item
    val centerVirtualIndex by remember { derivedStateOf { listState.firstVisibleItemIndex + halfVisible } }

    Box(
        modifier = modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center,
    ) {
        // Highlight band behind the selected item
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .padding(horizontal = 2.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(10.dp),
                )
        )

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(virtualCount) { virtualIndex ->
                val index = virtualIndex % count
                val distance = abs(virtualIndex - centerVirtualIndex)
                val alpha = when (distance) {
                    0 -> 1f
                    1 -> 0.50f
                    2 -> 0.20f
                    else -> 0f
                }
                val scale = 1f - distance.coerceAtMost(2) * 0.12f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "%02d".format(index),
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (distance == 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    )
                }
            }
        }

        // Top fade overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(itemHeight * halfVisible)
                .background(
                    Brush.verticalGradient(listOf(background, Color.Transparent))
                )
        )
        // Bottom fade overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(itemHeight * halfVisible)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, background))
                )
        )
    }
}

@Composable
private fun DaySelector(mask: Int, onMaskChange: (Int) -> Unit) {
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        letters.forEachIndexed { bit, letter ->
            val selected = mask and (1 shl bit) != 0
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable {
                        val newMask = if (selected) mask and (1 shl bit).inv() else mask or (1 shl bit)
                        onMaskChange(newMask)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    onValue: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            text = s
            s.toIntOrNull()?.coerceIn(range)?.let(onValue)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
