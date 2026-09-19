package com.loic.wakeup.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loic.wakeup.R
import com.loic.wakeup.data.SettingsStore
import com.loic.wakeup.domain.GeoPoint
import com.loic.wakeup.domain.LocationAccess
import com.loic.wakeup.domain.spot
import com.loic.wakeup.ui.components.TimeText
import com.loic.wakeup.ui.components.formatClock
import com.loic.wakeup.ui.theme.auroraSky
import com.loic.wakeup.ui.theme.frostedPanel
import com.loic.wakeup.ui.theme.liquidGlass
import com.loic.wakeup.ui.viewmodel.FailsafeSettingsViewModel
import com.loic.wakeup.ui.viewmodel.TagFailsafeItem
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FailsafeSettingsScreen(
    onBack: () -> Unit,
    vm: FailsafeSettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val tags by vm.tags.collectAsState()
    val locatingUid by vm.locatingUid.collectAsState()
    val hazeState = remember { HazeState() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(vm) {
        vm.errorEvent.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    // Location access is also changed from system settings, so re-check whenever we resume.
    var hasPrecise by remember { mutableStateOf(LocationAccess.hasPrecise(context)) }
    var hasBackground by remember { mutableStateOf(LocationAccess.hasBackground(context)) }
    val refreshAccess = {
        hasPrecise = LocationAccess.hasPrecise(context)
        hasBackground = LocationAccess.hasBackground(context)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Tag whose spot to fill in from the current location once precise access is granted.
    var pendingLocateUid by remember { mutableStateOf<String?>(null) }
    val preciseLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshAccess()
        val uid = pendingLocateUid
        pendingLocateUid = null
        if (uid != null && hasPrecise) vm.useCurrentLocation(uid)
    }
    // On Android 11+ this opens the app's location page, where "Allow all the time" lives.
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshAccess() }

    val requestPrecise = {
        preciseLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
    val requestBackground = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
    val openAppSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        )
    }
    val noMapApp = stringResource(R.string.failsafe_no_map_app)
    val openMap: (GeoPoint) -> Unit = { point ->
        val coords = "${point.latitude},${point.longitude}"
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coords?q=$coords")))
        } catch (_: ActivityNotFoundException) {
            coroutineScope.launch { snackbarHostState.showSnackbar(noMapApp) }
        }
    }

    var timeDialogFor by remember { mutableStateOf<TagFailsafeItem?>(null) }
    var coordinatesDialogFor by remember { mutableStateOf<TagFailsafeItem?>(null) }

    timeDialogFor?.let { item ->
        CheckTimeDialog(
            initialHour = item.failsafe.hour,
            initialMinute = item.failsafe.minute,
            onConfirm = { hour, minute ->
                vm.setCheckTime(item.tagUid, hour, minute)
                timeDialogFor = null
            },
            onDismiss = { timeDialogFor = null },
        )
    }
    coordinatesDialogFor?.let { item ->
        CoordinatesDialog(
            initial = item.failsafe.spot,
            onConfirm = { point ->
                vm.setSpot(item.tagUid, point)
                coordinatesDialogFor = null
            },
            onDismiss = { coordinatesDialogFor = null },
        )
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // Clears the pinned glass bar; content scrolls up under it.
                    item { Spacer(Modifier.height(64.dp)) }

                    item { FailsafeIntroPanel() }

                    item {
                        LocationAccessPanel(
                            hasPrecise = hasPrecise,
                            hasBackground = hasBackground,
                            onRequestPrecise = requestPrecise,
                            onRequestBackground = requestBackground,
                            onOpenAppSettings = openAppSettings,
                        )
                    }

                    if (tags.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.failsafe_no_tags),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    items(tags, key = { it.tagUid }) { item ->
                        TagFailsafeCard(
                            item = item,
                            locating = locatingUid == item.tagUid,
                            hasBackground = hasBackground,
                            onToggle = { enabled ->
                                vm.setEnabled(item.tagUid, enabled)
                                // Walk the user through the access the check needs, one step at a time.
                                if (enabled && item.failsafe.spot != null) {
                                    if (!hasPrecise) requestPrecise()
                                    else if (!hasBackground) requestBackground()
                                }
                            },
                            onPickTime = { timeDialogFor = item },
                            onUseCurrentLocation = {
                                if (hasPrecise) {
                                    vm.useCurrentLocation(item.tagUid)
                                } else {
                                    pendingLocateUid = item.tagUid
                                    requestPrecise()
                                }
                            },
                            onEnterCoordinates = { coordinatesDialogFor = item },
                            onViewOnMap = openMap,
                        )
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            }

            // Pinned liquid-glass top bar (sibling overlay) the list scrolls up under.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .liquidGlass(hazeState, RectangleShape)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    stringResource(R.string.failsafe),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun FailsafeIntroPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frostedPanel(RoundedCornerShape(24.dp)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.failsafe_title),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.failsafe_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.failsafe_how_it_works),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Precise + "all the time" location status, with the next step to grant what's missing. */
@Composable
private fun LocationAccessPanel(
    hasPrecise: Boolean,
    hasBackground: Boolean,
    onRequestPrecise: () -> Unit,
    onRequestBackground: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frostedPanel(RoundedCornerShape(24.dp)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.failsafe_access_title),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                !hasPrecise -> {
                    Text(
                        stringResource(R.string.failsafe_access_precise_needed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    PrimaryButton(stringResource(R.string.failsafe_grant_precise), onRequestPrecise)
                }
                !hasBackground -> {
                    Text(
                        stringResource(R.string.failsafe_access_background_needed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    PrimaryButton(stringResource(R.string.failsafe_grant_background), onRequestBackground)
                }
                else -> Text(
                    stringResource(R.string.failsafe_access_ok),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // Fallback once the system stops showing the permission prompt (denied twice).
            if (!hasBackground) {
                OutlinedButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Text(stringResource(R.string.failsafe_open_app_settings)) }
            }
        }
    }
}

@Composable
private fun TagFailsafeCard(
    item: TagFailsafeItem,
    locating: Boolean,
    hasBackground: Boolean,
    onToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onEnterCoordinates: () -> Unit,
    onViewOnMap: (GeoPoint) -> Unit,
) {
    val failsafe = item.failsafe
    val spot = failsafe.spot
    val use24Hour by SettingsStore.use24Hour.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frostedPanel(RoundedCornerShape(24.dp)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    if (item.isGlobal) R.string.failsafe_tag_global else R.string.failsafe_tag_custom,
                    item.tagUid.take(4),
                    item.tagUid.takeLast(4),
                ),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (item.alarms.isEmpty()) {
                    stringResource(R.string.failsafe_no_linked_alarms)
                } else {
                    stringResource(
                        R.string.failsafe_linked_alarms,
                        item.alarms.joinToString(", ") { alarm ->
                            val time = formatClock(alarm.hour, alarm.minute, use24Hour)
                            if (alarm.label.isNotBlank()) "$time (${alarm.label})" else time
                        },
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.failsafe_enable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = failsafe.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            // The check can't run without background location — surface it on the tag itself.
            if (failsafe.enabled && !hasBackground) {
                Text(
                    stringResource(R.string.failsafe_needs_access),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.failsafe_check_time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onPickTime) {
                    TimeText(
                        hour = failsafe.hour,
                        minute = failsafe.minute,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.failsafe_spot),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        spot?.format() ?: stringResource(R.string.failsafe_spot_not_set),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (spot != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (spot != null) {
                    TextButton(
                        onClick = { onViewOnMap(spot) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) { Text(stringResource(R.string.failsafe_view_on_map)) }
                }
            }

            if (locating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.failsafe_locating),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(
                onClick = onUseCurrentLocation,
                enabled = !locating,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) { Text(stringResource(R.string.failsafe_use_current)) }
            OutlinedButton(
                onClick = onEnterCoordinates,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) { Text(stringResource(R.string.failsafe_enter_coordinates)) }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) { Text(text) }
}

/** Material clock-dial picker in a dialog, following the app's 12/24-hour setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val use24Hour by SettingsStore.use24Hour.collectAsState()
    val state = rememberTimePickerState(initialHour, initialMinute, is24Hour = use24Hour)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            // No fillMaxWidth children: the column wraps to the picker's own width.
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.failsafe_set_time_title),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 20.dp),
                )
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectorColor = MaterialTheme.colorScheme.primary,
                        periodSelectorBorderColor = MaterialTheme.colorScheme.outline,
                        periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

/** Free-text "lat, lng" entry, so any spot can be chosen (e.g. pasted from a map app). */
@Composable
private fun CoordinatesDialog(
    initial: GeoPoint?,
    onConfirm: (GeoPoint) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.format() ?: "") }
    val parsed = GeoPoint.parse(text)
    val showError = text.isNotBlank() && parsed == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.failsafe_coordinates_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.failsafe_coordinates_hint)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (showError) R.string.failsafe_coordinates_invalid
                            else R.string.failsafe_coordinates_help
                        )
                    )
                },
                isError = showError,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
