package com.loic.wakeup.ui.screens

import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loic.wakeup.R
import com.loic.wakeup.data.SettingsStore
import com.loic.wakeup.ui.nfc.NfcScanningEffect
import com.loic.wakeup.ui.theme.auroraSky
import com.loic.wakeup.ui.theme.frostedPanel
import com.loic.wakeup.ui.theme.liquidGlass
import com.loic.wakeup.ui.viewmodel.NfcSettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcSettingsScreen(
    onBack: () -> Unit,
    onAppBlocking: () -> Unit = {},
    vm: NfcSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uid by vm.uid.collectAsState()
    val scanning by vm.scanning.collectAsState()

    NfcScanningEffect(scanning) { vm.onTagScanned(it) }

    val coroutineScope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }
    var clearCount by remember { mutableIntStateOf(0) }
    val hazeState = remember { HazeState() }

    Scaffold(
        containerColor = Color.Transparent,
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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Clears the pinned glass bar; content scrolls up under it.
            Spacer(Modifier.height(64.dp))
            // NFC tag section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedPanel(RoundedCornerShape(24.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "NFC TAG",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (showClearConfirm) {
                        AlertDialog(
                            onDismissRequest = { showClearConfirm = false },
                            title = { Text(stringResource(R.string.clear_global_tag_confirm_title, clearCount)) },
                            text = { Text(stringResource(R.string.clear_global_tag_confirm_body)) },
                            confirmButton = {
                                TextButton(onClick = { vm.clearTag(); showClearConfirm = false }) {
                                    Text(stringResource(R.string.clear_global_tag_confirm_ok))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearConfirm = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            },
                        )
                    }

                    if (uid != null) {
                        Text(
                            "Registered: ${uid!!.take(4)}…${uid!!.takeLast(4)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        clearCount = vm.previewClearCount()
                                        if (clearCount > 0) showClearConfirm = true else vm.clearTag()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) { Text("Remove") }
                            Button(
                                onClick = { vm.startScan() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) { Text(stringResource(R.string.replace_tag)) }
                        }
                    } else {
                        Text(
                            stringResource(R.string.no_tag_registered),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { vm.startScan() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) { Text(stringResource(R.string.register_tag)) }
                    }

                    if (scanning) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.scan_tag_prompt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = { vm.cancelScan() },
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                }
            }

            // Time format section
            val use24Hour by SettingsStore.use24Hour.collectAsState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedPanel(RoundedCornerShape(24.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "TIME FORMAT",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val segmentColors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                        inactiveBorderColor = MaterialTheme.colorScheme.outline,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !use24Hour,
                            onClick = { SettingsStore.setUse24Hour(false) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            colors = segmentColors,
                        ) { Text(stringResource(R.string.time_format_12h)) }
                        SegmentedButton(
                            selected = use24Hour,
                            onClick = { SettingsStore.setUse24Hour(true) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            colors = segmentColors,
                        ) { Text(stringResource(R.string.time_format_24h)) }
                    }
                }
            }

            // App blocking section
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
                        stringResource(R.string.app_blocking_title),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.app_blocking_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Button(
                        onClick = onAppBlocking,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text(stringResource(R.string.app_blocking)) }
                }
            }

            // Permissions section
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
                        "PERMISSIONS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text(stringResource(R.string.exact_alarm_permission)) }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text(stringResource(R.string.full_screen_intent_permission)) }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
        }

        // Pinned liquid-glass top bar (sibling overlay) the settings scroll up under.
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
                stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
    }
}

