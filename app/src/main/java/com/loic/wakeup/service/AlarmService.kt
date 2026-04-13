package com.loic.wakeup.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.loic.wakeup.R
import com.loic.wakeup.WakeUpApp
import com.loic.wakeup.data.AlarmDatabase
import com.loic.wakeup.data.AlarmRepository
import com.loic.wakeup.ui.screens.AlarmRingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** State published to the ringing screen so it can adapt its UI. */
sealed interface RingState {
    data class Ringing(val snoozeCount: Int, val maxSnoozes: Int) : RingState
    data class Snoozed(val untilMs: Long, val snoozeCount: Int, val maxSnoozes: Int) : RingState
}

class AlarmService : Service() {

    companion object {
        const val ACTION_DISMISS = "com.loic.wakeup.ACTION_DISMISS"
        const val ACTION_SNOOZE  = "com.loic.wakeup.ACTION_SNOOZE"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_ALARM_ID  = "alarmId"

        @Volatile var isRunning = false
        @Volatile var runningAlarmId = -1

        private val _ringState = MutableStateFlow<RingState>(RingState.Ringing(0, 3))
        val ringState: StateFlow<RingState> = _ringState
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var currentAlarmId: Int = -1
    private var currentRingtoneUri: String? = null

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_DISMISS -> stopSelf()
                ACTION_SNOOZE  -> handleSnooze()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(ACTION_DISMISS)
            addAction(ACTION_SNOOZE)
        }
        ContextCompat.registerReceiver(this, dismissReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentAlarmId  = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        isRunning       = true
        runningAlarmId  = currentAlarmId

        val ringingIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, currentAlarmId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, 0, ringingIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val snoozePi = PendingIntent.getBroadcast(
            this, 1, Intent(ACTION_SNOOZE).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, WakeUpApp.ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.alarm_notification_title))
            .setContentText(getString(R.string.alarm_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.snooze), snoozePi)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        // AlarmReceiver already launched the activity in the broadcast window.
        // fullScreenIntent handles the locked-screen case via the notification above.

        scope.launch(Dispatchers.IO) {
            val repo  = AlarmRepository(AlarmDatabase.getInstance(this@AlarmService).alarmDao())
            val alarm = repo.getById(currentAlarmId)
            currentRingtoneUri = alarm?.ringtoneUri
            val count = alarm?.snoozeCount ?: 0
            val max   = alarm?.maxSnoozes  ?: 3
            _ringState.value = RingState.Ringing(count, max)
            startRingtone(currentRingtoneUri)
        }
        startVibration()

        return START_NOT_STICKY
    }

    private fun handleSnooze() {
        scope.launch(Dispatchers.IO) {
            val repo  = AlarmRepository(AlarmDatabase.getInstance(this@AlarmService).alarmDao())
            val alarm = repo.getById(currentAlarmId) ?: return@launch
            if (alarm.snoozeCount >= alarm.maxSnoozes) return@launch

            val newCount = alarm.snoozeCount + 1
            repo.setSnoozeCount(currentAlarmId, newCount)

            val untilMs = System.currentTimeMillis() + alarm.snoozeDurationSeconds * 1000L
            stopAudioAndVibration()
            _ringState.value = RingState.Snoozed(untilMs, newCount, alarm.maxSnoozes)

            val remaining = untilMs - System.currentTimeMillis()
            if (remaining > 0) delay(remaining)

            if (isRunning) {
                _ringState.value = RingState.Ringing(newCount, alarm.maxSnoozes)
                startRingtone(currentRingtoneUri)
                startVibration()
            }
        }
        // Do NOT call stopSelf() — service stays alive during snooze
    }

    private fun startRingtone(ringtoneUriString: String?) {
        val uri = if (!ringtoneUriString.isNullOrEmpty()) {
            Uri.parse(ringtoneUriString)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@AlarmService, uri)
            isLooping = true
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
            prepare()
            start()
        }
    }

    private fun stopAudioAndVibration() {
        try { mediaPlayer?.stop() } catch (_: IllegalStateException) {}
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 500, 500)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    override fun onDestroy() {
        isRunning      = false
        runningAlarmId = -1
        _ringState.value = RingState.Ringing(0, 3)
        stopAudioAndVibration()
        unregisterReceiver(dismissReceiver)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
