package com.loic.wakeup.ui.screens

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loic.wakeup.R
import com.loic.wakeup.data.AlarmDatabase
import com.loic.wakeup.data.AlarmRepository
import com.loic.wakeup.data.NfcTagStore
import com.loic.wakeup.service.AlarmService
import com.loic.wakeup.service.RingState
import com.loic.wakeup.ui.theme.Midnight
import com.loic.wakeup.ui.theme.StarWhite
import com.loic.wakeup.ui.theme.WakeUpTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmRingingActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var nfcTagStore: NfcTagStore
    private var snackbarMessage by mutableStateOf<String?>(null)
    private var dismissWithoutTag by mutableStateOf(false)
    private var alarmId = -1

    @Volatile private var expectedUid: String? = null
    @Volatile private var preloadDone: Boolean = false
    @Volatile private var pendingScanHex: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        alarmId = intent.getIntExtra(AlarmService.EXTRA_ALARM_ID, -1)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        nfcTagStore = NfcTagStore(this)

        lifecycleScope.launch {
            val repo = AlarmRepository(AlarmDatabase.getInstance(this@AlarmRingingActivity).alarmDao())
            val alarm = if (alarmId >= 0) repo.getById(alarmId) else null
            dismissWithoutTag = alarm?.dismissWithoutTag == true
            expectedUid = if (dismissWithoutTag) null else alarm?.nfcTagUid ?: nfcTagStore.getUid()
            preloadDone = true
            if (dismissWithoutTag) nfcAdapter?.disableReaderMode(this@AlarmRingingActivity)
            else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) enableNfcReaderIfNeeded()
            val pending = pendingScanHex
            if (pending != null && !dismissWithoutTag) runOnUiThread { handleScan(pending) }
        }

        // Modern lock-screen APIs (API 27+); fall back to legacy flags on API 26
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Block back button
        onBackPressedDispatcher.addCallback(this) { /* no-op */ }

        setContent {
            WakeUpTheme {
                RingingScreen(
                    snackbarMessage = snackbarMessage,
                    dismissWithoutTag = dismissWithoutTag,
                    onSnooze = ::snooze,
                    onDismiss = ::dismiss,
                    onOpenNfcSettings = {
                        startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableNfcReaderIfNeeded()
    }

    private fun enableNfcReaderIfNeeded() {
        if (!preloadDone) return
        if (dismissWithoutTag) return
        val nfc = nfcAdapter
        if (nfc == null || !nfc.isEnabled) {
            snackbarMessage = getString(R.string.nfc_disabled)
            return
        }
        nfc.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    override fun onPause() {
        super.onPause()
        // Do NOT disable NFC here. onPause() fires for transient focus losses (system
        // overlays, lock-screen dimming) that do not actually background the activity.
        // Disabling here kills reader mode prematurely and — if combined with a relaunch —
        // creates a feedback loop of competing instances. Reader mode is torn down in
        // onStop(), which only fires when the activity is truly backgrounded.
    }

    override fun onStop() {
        super.onStop()
        nfcAdapter?.disableReaderMode(this)
        // Bring the activity back if the alarm is still ringing. Using onStop (not onPause)
        // avoids relaunching on transient focus losses, eliminating the race condition.
        if (!isFinishing && AlarmService.isRunning) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && AlarmService.isRunning) {
                    startActivity(
                        Intent(this, AlarmRingingActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            }, 800)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        nfcAdapter?.disableReaderMode(this)
    }

    // Block volume key events so alarm volume can't be muted
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE -> true
            else -> super.dispatchKeyEvent(event)
        }
    }

    // Called on a background thread by the NFC reader-mode stack — works on lock screen
    override fun onTagDiscovered(tag: Tag) {
        if (dismissWithoutTag) return
        val scannedHex = tag.id.joinToString("") { "%02x".format(it) }
        if (!preloadDone) {
            pendingScanHex = scannedHex
            return
        }
        Handler(mainLooper).post { handleScan(scannedHex) }
    }

    private fun handleScan(hex: String) {
        val expected = expectedUid
        if (expected != null && hex == expected) {
            dismiss()
        } else {
            snackbarMessage = getString(R.string.wrong_tag)
        }
    }

    private fun dismiss() {
        sendBroadcast(Intent(AlarmService.ACTION_DISMISS).setPackage(packageName))
        finish()
    }

    private fun snooze() {
        // Ignore taps once snoozes are exhausted — the alarm must only be cleared by a tag.
        val state = AlarmService.ringState.value
        if (state.snoozeCount >= state.maxSnoozes) return
        sendBroadcast(Intent(AlarmService.ACTION_SNOOZE).setPackage(packageName))
        // The service silences and schedules the re-ring through AlarmManager, then stops.
        // Finish so we don't linger over a stopped service; the re-ring relaunches us.
        finish()
    }

}

@Composable
private fun RingingScreen(
    snackbarMessage: String?,
    dismissWithoutTag: Boolean,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    onOpenNfcSettings: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val state by AlarmService.ringState.collectAsState()

    var currentTime by remember { mutableStateOf(formattedNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formattedNow()
            delay(1000)
        }
    }

    // Countdown during snooze
    var remainingSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(state) {
        val snoozed = state as? RingState.Snoozed ?: return@LaunchedEffect
        while (true) {
            val rem = ((snoozed.untilMs - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
            remainingSeconds = rem
            if (rem == 0) break
            delay(1000)
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ringing")
    val pulseOuter by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "outerPulse",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue  = 0.35f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    val amber = MaterialTheme.colorScheme.primary
    val isSnoozed   = state is RingState.Snoozed
    val snoozeCount = state.snoozeCount
    val maxSnoozes  = state.maxSnoozes

    Scaffold(
        containerColor = Midnight,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            // Pulsing rings
            Box(
                modifier = Modifier
                    .size(340.dp)
                    .scale(pulseOuter)
                    .background(amber.copy(alpha = pulseAlpha * 0.4f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .scale(pulseOuter * 0.92f)
                    .background(amber.copy(alpha = pulseAlpha * 0.7f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .background(amber.copy(alpha = 0.07f), CircleShape),
            )

            // Center content
            Column(
                horizontalAlignment  = Alignment.CenterHorizontally,
                verticalArrangement  = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "WAKE UP",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 6.sp),
                    color = amber.copy(alpha = 0.75f),
                )
                Text(
                    currentTime,
                    style = MaterialTheme.typography.displayLarge,
                    color = StarWhite,
                )
            }

            // Bottom actions
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 32.dp, vertical = 40.dp)
                    .fillMaxWidth(),
                verticalArrangement  = Arrangement.spacedBy(12.dp),
                horizontalAlignment  = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(
                        if (dismissWithoutTag) R.string.ringing_no_tag_prompt
                        else R.string.ringing_scan_tag_prompt
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = StarWhite.copy(alpha = 0.4f),
                )

                when {
                    dismissWithoutTag -> {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor   = MaterialTheme.colorScheme.onSurface,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(stringResource(R.string.dismiss), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    isSnoozed -> {
                        // Show countdown during silent snooze window
                        val mm = remainingSeconds / 60
                        val ss = remainingSeconds % 60
                        Text(
                            stringResource(R.string.snoozed_countdown, "%02d:%02d".format(mm, ss)),
                            style = MaterialTheme.typography.titleMedium,
                            color = StarWhite.copy(alpha = 0.6f),
                        )
                    }
                    snoozeCount >= maxSnoozes -> {
                        // Max snoozes reached — no snooze button, informational text only
                        Text(
                            stringResource(R.string.max_snoozes_reached),
                            style = MaterialTheme.typography.bodyMedium,
                            color = StarWhite.copy(alpha = 0.6f),
                        )
                    }
                    else -> {
                        Button(
                            onClick = onSnooze,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor   = MaterialTheme.colorScheme.onSurface,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(stringResource(R.string.snooze), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                if (!dismissWithoutTag) {
                    OutlinedButton(
                        onClick = onOpenNfcSettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.enable_nfc))
                    }
                }
            }
        }
    }
}

// Extension to extract snoozeCount / maxSnoozes from either state variant
private val RingState.snoozeCount: Int get() = when (this) {
    is RingState.Ringing -> snoozeCount
    is RingState.Snoozed -> snoozeCount
}
private val RingState.maxSnoozes: Int get() = when (this) {
    is RingState.Ringing -> maxSnoozes
    is RingState.Snoozed -> maxSnoozes
}

private fun formattedNow(): String {
    val cal = java.util.Calendar.getInstance()
    return "%02d:%02d".format(
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE),
    )
}
