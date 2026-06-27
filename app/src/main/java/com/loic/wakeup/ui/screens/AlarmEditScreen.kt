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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loic.wakeup.R
import com.loic.wakeup.data.NfcTagStore
import com.loic.wakeup.data.SettingsStore
import com.loic.wakeup.ui.nfc.NfcScanningEffect
import com.loic.wakeup.ui.theme.Midnight
import com.loic.wakeup.ui.theme.auroraSky
import com.loic.wakeup.ui.theme.frostedPanel
import com.loic.wakeup.ui.theme.liquidGlass
import com.loic.wakeup.ui.viewmodel.AlarmEditViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
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
    val use24Hour by SettingsStore.use24Hour.collectAsState()
    val context = LocalContext.current
    val globalUid = remember { NfcTagStore(context).getUid() }
    val snackbarHostState = remember { SnackbarHostState() }

    val hazeState = remember { HazeState() }
    var showNfcSheet by remember { mutableStateOf(false) }
    var nfcScanning by remember { mutableStateOf(false) }

    if (showNfcSheet) {
        ModalBottomSheet(
            onDismissRequest = { nfcScanning = false; showNfcSheet = false },
        ) {
            NfcScanningEffect(nfcScanning) { hex ->
                vm.update(alarm.copy(nfcTagUid = hex, dismissWithoutTag = false))
                nfcScanning = false
                showNfcSheet = false
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "NFC TAG",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.scan_tag_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { nfcScanning = false; showNfcSheet = false },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

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
        containerColor = Color.Transparent,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .auroraSky()
                .hazeSource(hazeState),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Clears the pinned glass bar; the form scrolls up under it.
            Spacer(Modifier.height(64.dp))

            // Wheel time picker on a glass slab. Each wheel keeps its own amber
            // selection band; a single fade spans the whole slab — dark at the top
            // and bottom edges, clear across the selection — so the wheels dissolve
            // into the panel with no hard edge.
            // Swallow any scroll the wheels don't use (e.g. dragging the short
            // AM/PM wheel past its ends) so the form behind doesn't scroll while
            // the user is setting the time.
            val pickerNestedScroll = remember {
                object : NestedScrollConnection {
                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset = available

                    override suspend fun onPostFling(
                        consumed: Velocity,
                        available: Velocity,
                    ): Velocity = available
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(pickerNestedScroll)
                    .frostedPanel(RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (use24Hour) {
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
                    } else {
                        // Storage stays 24-hour; these wheels show a 12-hour clock
                        // plus a separate AM/PM wheel.
                        val isPm = alarm.hour >= 12
                        val hour12 = ((alarm.hour + 11) % 12) + 1   // 1..12
                        WheelPicker(
                            count = 12,
                            value = hour12 - 1,
                            onValueChange = { vm.update(alarm.copy(hour = to24Hour(it + 1, isPm))) },
                            label = { (it + 1).toString() },
                            modifier = Modifier.width(76.dp),
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
                            modifier = Modifier.width(76.dp),
                        )
                        WheelPicker(
                            count = 2,
                            value = if (isPm) 1 else 0,
                            onValueChange = { vm.update(alarm.copy(hour = to24Hour(hour12, it == 1))) },
                            label = { if (it == 0) "AM" else "PM" },
                            loop = false,
                            textStyle = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.sp),
                            modifier = Modifier.width(64.dp).padding(start = 8.dp),
                        )
                    }
                }
                // Full-slab depth fade, dissolving the edge numbers into the panel.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Midnight.copy(alpha = 0.6f),
                                0.35f to Color.Transparent,
                                0.65f to Color.Transparent,
                                1f to Midnight.copy(alpha = 0.6f),
                            )
                        )
                )
            }

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedPanel(RoundedCornerShape(24.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedPanel(RoundedCornerShape(24.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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

            // NFC tag override
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedPanel(RoundedCornerShape(24.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.nfc_section_title),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    alarm.dismissWithoutTag -> {
                        Text(
                            stringResource(R.string.nfc_no_tag_required),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        OutlinedButton(
                            onClick = { },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) { Text(stringResource(R.string.nfc_pair_custom)) }
                    }
                    alarm.nfcTagUid != null -> {
                        Text(
                            stringResource(
                                R.string.nfc_using_custom_tag,
                                alarm.nfcTagUid!!.take(4),
                                alarm.nfcTagUid!!.takeLast(4),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { showNfcSheet = true; nfcScanning = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) { Text(stringResource(R.string.nfc_pair_new)) }
                            OutlinedButton(
                                onClick = { vm.update(alarm.copy(nfcTagUid = null, dismissWithoutTag = false)) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) { Text(stringResource(R.string.nfc_use_global)) }
                        }
                    }
                    globalUid != null -> {
                        Text(
                            stringResource(R.string.nfc_using_global_tag),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { showNfcSheet = true; nfcScanning = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) { Text(stringResource(R.string.nfc_pair_custom)) }
                    }
                    else -> {
                        Text(
                            stringResource(R.string.nfc_no_tag_anywhere),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { showNfcSheet = true; nfcScanning = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) { Text(stringResource(R.string.nfc_pair_custom)) }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.nfc_use_no_tag),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = alarm.dismissWithoutTag,
                        onCheckedChange = { enabled ->
                            vm.update(
                                alarm.copy(
                                    dismissWithoutTag = enabled,
                                    nfcTagUid = if (enabled) null else alarm.nfcTagUid,
                                )
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            // Snooze
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedPanel(RoundedCornerShape(24.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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

        // Pinned liquid-glass top bar (sibling overlay) the form scrolls up under.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .liquidGlass(hazeState, RectangleShape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                if (alarmId == null) "New Alarm" else "Edit Alarm",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { vm.save(onDone) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    }
}

/** Convert a 12-hour clock reading (1..12) plus AM/PM into a 24-hour hour (0..23). */
private fun to24Hour(hour12: Int, isPm: Boolean): Int = when {
    isPm  -> if (hour12 == 12) 12 else hour12 + 12
    else  -> if (hour12 == 12) 0 else hour12
}

@Composable
private fun WheelPicker(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: (Int) -> String = { "%02d".format(it) },
    loop: Boolean = true,
    textStyle: TextStyle? = null,
    itemHeight: Dp = 56.dp,
    visibleCount: Int = 5,
) {
    val itemStyle = textStyle ?: MaterialTheme.typography.headlineLarge
    val scope = rememberCoroutineScope()
    val halfVisible = visibleCount / 2
    // Looping wheels use a huge virtual list and start in the middle so we can
    // scroll both ways; non-looping wheels render exactly `count` real items
    // plus `halfVisible` blank slots on each end so the first and last value can
    // still reach the centered selection band.
    // With SnapPosition.Center the snapped item is centered, meaning
    // firstVisibleItemIndex points to the TOP item, so the centered (selected)
    // index = firstVisible + halfVisible.
    val itemCount = if (loop) count * 10_000 else count + halfVisible * 2
    val initialIndex = if (loop)
        (count * 5_000 + value - halfVisible).coerceAtLeast(0)
    else
        value
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState, snapPosition = SnapPosition.Center)

    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    fun settledValue(): Int =
        if (loop) (listState.firstVisibleItemIndex + halfVisible) % count
        else listState.firstVisibleItemIndex.coerceIn(0, count - 1)

    // Sync list to value when changed externally (e.g. loading an existing alarm)
    LaunchedEffect(value) {
        if (!listState.isScrollInProgress && settledValue() != value) {
            if (loop) {
                val centerVirtual = listState.firstVisibleItemIndex + halfVisible
                val cur = centerVirtual % count
                val base = centerVirtual - cur
                listState.scrollToItem((base + value - halfVisible).coerceAtLeast(0))
            } else {
                listState.scrollToItem(value)
            }
        }
    }

    // Report selection once scroll settles on a new item
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .drop(1) // skip the initial false at composition time
            .collect {
                val settled = settledValue()
                if (settled != currentValue) currentOnValueChange(settled)
            }
    }

    // List index of the centered (selected) item
    val centerVirtualIndex by remember { derivedStateOf { listState.firstVisibleItemIndex + halfVisible } }

    Box(
        modifier = modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center,
    ) {
        // Amber-tinted band behind the selected item (one per wheel)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .padding(horizontal = 10.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    RoundedCornerShape(16.dp),
                )
        )

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(itemCount) { listIndex ->
                // Looping: every slot maps to a value. Non-looping: the leading
                // and trailing `halfVisible` slots are blank padding (null).
                val index: Int? = if (loop) listIndex % count
                    else (listIndex - halfVisible).takeIf { it in 0 until count }
                val distance = abs(listIndex - centerVirtualIndex)
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
                        .height(itemHeight)
                        // Tap any item to bring it to the centered selection band;
                        // the settle listener then reports the new value. Blank
                        // padding slots stay inert.
                        .then(
                            if (index != null) Modifier.clickable {
                                scope.launch {
                                    listState.animateScrollToItem(
                                        (listIndex - halfVisible).coerceAtLeast(0)
                                    )
                                }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (index != null) {
                        Text(
                            text = label(index),
                            style = itemStyle,
                            maxLines = 1,
                            softWrap = false,
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
        }
    }
}

@Composable
private fun DaySelector(mask: Int, onMaskChange: (Int) -> Unit) {
    val letters = listOf("S", "M", "T", "W", "T", "F", "S")
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
