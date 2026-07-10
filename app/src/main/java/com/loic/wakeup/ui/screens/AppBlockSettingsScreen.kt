package com.loic.wakeup.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loic.wakeup.R
import com.loic.wakeup.service.AppBlockAccessibilityService
import com.loic.wakeup.ui.theme.auroraSky
import com.loic.wakeup.ui.theme.frostedPanel
import com.loic.wakeup.ui.theme.liquidGlass
import com.loic.wakeup.ui.viewmodel.AppBlockSettingsViewModel
import com.loic.wakeup.ui.viewmodel.InstalledApp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBlockSettingsScreen(
    onBack: () -> Unit,
    vm: AppBlockSettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val enabled by vm.enabled.collectAsState()
    val allowed by vm.allowedPackages.collectAsState()
    val apps by vm.apps.collectAsState()
    val loading by vm.loading.collectAsState()
    val hazeState = remember { HazeState() }

    // The accessibility service is toggled in system settings, so re-check whenever we resume.
    var serviceOn by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceOn = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
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

                    item {
                        BlockingControlPanel(
                            enabled = enabled,
                            serviceOn = serviceOn,
                            onToggle = vm::setEnabled,
                            onOpenAccessibility = {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                )
                            },
                        )
                    }

                    item {
                        Text(
                            stringResource(R.string.app_blocking_allowed_apps),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.app_blocking_allowed_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (loading) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        items(apps, key = { it.packageName }) { app ->
                            AppRow(
                                app = app,
                                checked = app.packageName in allowed,
                                onCheckedChange = { vm.setAllowed(app.packageName, it) },
                            )
                        }
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
                    stringResource(R.string.app_blocking),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun BlockingControlPanel(
    enabled: Boolean,
    serviceOn: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
) {
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
                stringResource(R.string.app_blocking_title),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.app_blocking_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.app_blocking_enable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            // Blocking is inert without the accessibility service — surface its state.
            if (enabled && !serviceOn) {
                Text(
                    stringResource(R.string.app_blocking_service_needed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (serviceOn) {
                Text(
                    stringResource(R.string.app_blocking_service_on),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedButton(
                onClick = onOpenAccessibility,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) { Text(stringResource(R.string.app_blocking_open_accessibility)) }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .frostedPanel(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp),
                    ),
            )
        }
        Text(
            app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

private fun isAccessibilityServiceEnabled(
    context: android.content.Context,
): Boolean {
    val expected = ComponentName(context, AppBlockAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}
