package com.loic.wakeup.ui.screens

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loic.wakeup.R
import com.loic.wakeup.data.AlarmDatabase
import com.loic.wakeup.data.AlarmRepository
import com.loic.wakeup.data.NfcTagStore
import com.loic.wakeup.service.AlarmService
import com.loic.wakeup.service.RingState
import androidx.compose.ui.semantics.Role
import com.loic.wakeup.ui.components.TimeText
import com.loic.wakeup.ui.theme.Midnight
import com.loic.wakeup.ui.theme.StarWhite
import com.loic.wakeup.ui.theme.WakeUpTheme
import com.loic.wakeup.ui.theme.auroraSky
import com.loic.wakeup.ui.theme.liquidGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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

    // Set true immediately before requesting a keyguard dismiss so the onStop() relaunch loop
    // stands down: a deliberate unlock must NOT be fought with a re-launch the way HOME-away is.
    // Cleared in onResume(), which fires on every return path (unlock success, cancel, or error).
    @Volatile private var dismissingKeyguard = false

    // Finish only when the alarm is dismissed — from anywhere (in-app NFC scan or notification).
    // Snooze no longer finishes the activity: the service stays alive in a quiet Snoozed state and
    // the screen stays up so the NFC tag can dismiss the alarm before it re-rings. The UI reacts to
    // that transition via AlarmService.ringState.
    private val teardownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        alarmId = intent.getIntExtra(AlarmService.EXTRA_ALARM_ID, -1)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        nfcTagStore = NfcTagStore(this)

        ContextCompat.registerReceiver(
            this,
            teardownReceiver,
            IntentFilter(AlarmService.ACTION_DISMISS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

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
                    onUnlockToScan = ::unlockToScan,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Focus is back (unlock succeeded, was cancelled, or errored) — HOME-away protection
        // resumes, and on a successful unlock the device is now in the state where Samsung
        // permits NFC, so re-arming reader mode here is what makes the post-unlock scan work.
        dismissingKeyguard = false
        enableNfcReaderIfNeeded()
    }

    // Samsung One UI gates the NFC radio while the keyguard is active, so a tag can't be scanned
    // over the lock screen no matter what the app does. Rather than send the user to NFC settings
    // (which only toggles the radio, not the lock), prompt the keyguard dismiss directly: it
    // overlays the bouncer without backgrounding us, and onResume re-arms the reader on success.
    private fun unlockToScan() {
        val nfc = nfcAdapter
        if (nfc == null || !nfc.isEnabled) {
            // Radio genuinely off — unlocking won't help; the only fix is the NFC settings toggle.
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            return
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        dismissingKeyguard = true
        keyguard?.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissError() {
                snackbarMessage = getString(R.string.unlock_failed)
            }
        })
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
        // A deliberate keyguard dismiss must not be fought with a relaunch — that re-creates the
        // very race we're fixing (lockscreen flashes, then the ringing UI yanks itself back).
        // Reader mode is torn down above regardless; onResume re-arms it once focus returns.
        if (dismissingKeyguard) return
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
        // onStop() already disabled reader mode and dropped this activity's NFC state. Calling
        // disableReaderMode() again here finds no state, so NfcActivityManager tries to lazily
        // create one — and its constructor throws IllegalStateException because the activity is
        // already destroyed by the time onDestroy() runs. This call is best-effort duplicate
        // cleanup, so swallow that framework throw rather than crash on teardown (e.g. snooze).
        try {
            nfcAdapter?.disableReaderMode(this)
        } catch (_: IllegalStateException) {
        }
        unregisterReceiver(teardownReceiver)
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

    // dismiss/snooze only broadcast — teardownReceiver finishes the activity uniformly, so the
    // notification's snooze action gets the same treatment as the on-screen button. On snooze the
    // re-ring is owned by AlarmManager (AlarmReceiver relaunches the UI when the window elapses),
    // so the device is free to sleep meanwhile.
    private fun dismiss() {
        sendBroadcast(Intent(AlarmService.ACTION_DISMISS).setPackage(packageName))
    }

    private fun snooze() {
        sendBroadcast(Intent(AlarmService.ACTION_SNOOZE).setPackage(packageName))
    }

}

@Composable
private fun RingingScreen(
    snackbarMessage: String?,
    dismissWithoutTag: Boolean,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    onUnlockToScan: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val state by AlarmService.ringState.collectAsState()

    var currentTime by remember { mutableStateOf(nowHourMinute()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = nowHourMinute()
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
    val snoozeCount = state.snoozeCount
    val maxSnoozes  = state.maxSnoozes
    val snoozedState = state as? RingState.Snoozed
    val isSnoozed = snoozedState != null
    val hazeState = remember { HazeState() }

    // While snoozed, count down to the re-ring so the user knows how long they have to scan.
    var snoozeRemaining by remember { mutableStateOf("") }
    LaunchedEffect(snoozedState?.reRingAtMillis) {
        val reRingAt = snoozedState?.reRingAtMillis
        if (reRingAt == null) {
            snoozeRemaining = ""
        } else {
            while (true) {
                val secs = ((reRingAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
                snoozeRemaining = "%d:%02d".format(secs / 60, secs % 60)
                if (secs <= 0) break
                delay(1000)
            }
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
            // Aurora + pulsing rings + clock — the live scene the glass buttons refract.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .auroraSky()
                    .hazeSource(hazeState),
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
                    if (isSnoozed) stringResource(R.string.snoozed_label) else "WAKE UP",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 6.sp),
                    color = amber.copy(alpha = 0.75f),
                )
                TimeText(
                    hour = currentTime.first,
                    minute = currentTime.second,
                    style = MaterialTheme.typography.displayLarge,
                    color = StarWhite,
                )
            }
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
                        GlassButton(
                            text = stringResource(R.string.dismiss),
                            onClick = onDismiss,
                            hazeState = hazeState,
                            contentColor = StarWhite,
                        )
                    }
                    isSnoozed -> {
                        // Quietly waiting to re-ring — no snooze button, just the countdown.
                        Text(
                            stringResource(R.string.snoozed_rerings_in, snoozeRemaining),
                            style = MaterialTheme.typography.bodyMedium,
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
                        GlassButton(
                            text = stringResource(R.string.snooze),
                            onClick = onSnooze,
                            hazeState = hazeState,
                            contentColor = StarWhite,
                        )
                    }
                }

                if (!dismissWithoutTag) {
                    GlassButton(
                        text = stringResource(R.string.unlock_to_scan),
                        onClick = onUnlockToScan,
                        hazeState = hazeState,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        height = 48.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassButton(
    text: String,
    onClick: () -> Unit,
    hazeState: HazeState,
    contentColor: Color,
    height: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .liquidGlass(hazeState, shape, blurRadius = 16.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = contentColor)
    }
}

private val RingState.snoozeCount: Int get() = when (this) {
    is RingState.Ringing -> snoozeCount
    is RingState.Snoozed -> snoozeCount
}
private val RingState.maxSnoozes: Int get() = when (this) {
    is RingState.Ringing -> maxSnoozes
    is RingState.Snoozed -> maxSnoozes
}

private fun nowHourMinute(): Pair<Int, Int> {
    val cal = java.util.Calendar.getInstance()
    return cal.get(java.util.Calendar.HOUR_OF_DAY) to cal.get(java.util.Calendar.MINUTE)
}
