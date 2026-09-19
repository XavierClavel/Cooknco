package com.xavierclavel.cooknco

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.di.initFor
import com.xavierclavel.cooknco.platform.ACTION_COOK_TIMER_STOP
import com.xavierclavel.cooknco.platform.postCookTimerAlert
import com.xavierclavel.cooknco.ui.i18n.stringsFor

/**
 * Rings, and keeps ringing, until somebody stops it.
 *
 * This exists for one reason: a notification's sound plays **once**. There is no looping
 * notification on Android, so a timer that goes on sounding — which is what a timer is, and
 * what Android's own does — has to be an app playing audio, and an app playing audio with
 * its screen off has to be a foreground service or it is killed mid-beep.
 *
 * It is a `shortService`, which is the honest description of it: the type is capped at a few
 * minutes by the platform and this stops itself after [RING_MILLIS] anyway. That cap is why
 * the *countdown* is not a service — half an hour of held-open process to decrement a number
 * the clock already knows — and why only the ring is one. It also means no `specialUse`
 * declaration: this asks the platform for exactly what it is doing, briefly.
 *
 * Stopping is not this service's decision. Every way out — the shade's Stop button, the card's
 * — goes through `CookTimer.stop()`, and the platform layer stops this in response. The one
 * exception is running out of [RING_MILLIS], after which it detaches its notification and
 * leaves it behind: a cook who was out of the room still comes back to "time's up" rather
 * than to nothing.
 */
class CookTimerRingService : Service() {

    private companion object {
        /**
         * Shared with the one-shot alert on purpose: whichever of the two a device manages,
         * it is the same event, and the two must never be able to stack.
         */
        const val NOTIFICATION_ID = 8_102

        /**
         * Silent, because this service is the one making the sound. A channel that had one
         * would fire a second, non-looping copy of it the moment the notification was posted.
         */
        const val CHANNEL_ID = "cooknco_cook_timer_ring"

        /** Long enough to be heard from another room, short enough not to be a nuisance. */
        const val RING_MILLIS = 60_000L

        /** A bar of vibration per beep group, repeated with the sound. */
        val VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400, 200, 400, 900)
    }

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val giveUp = Runnable { silence(keepNotification = true) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Startable with nothing else of this app running — the alarm woke us for this.
        AppGraph.initFor(applicationContext)

        val foreground = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
        }.isSuccess

        if (!foreground) {
            // The window to call this is five seconds and the right to at all is the
            // platform's to withhold. Losing it costs the ring, not the timer: the cook still
            // gets told, once, the way they would have without this service at all.
            AppGraph.cookTimer.state.value?.let { postCookTimerAlert(applicationContext, it) }
            stopSelf()
            return START_NOT_STICKY
        }

        startRinging()
        handler.removeCallbacks(giveUp)
        handler.postDelayed(giveUp, RING_MILLIS)
        return START_NOT_STICKY
    }

    /**
     * The platform's own cap on a `shortService`, which lands earlier than [RING_MILLIS] only
     * if something has gone unusually slowly. Not stopping here is a crash, so it stops.
     */
    override fun onTimeout(startId: Int) = silence(keepNotification = true)

    override fun onDestroy() {
        handler.removeCallbacks(giveUp)
        releasePlayer()
        stopVibrating()
        super.onDestroy()
    }

    private fun silence(keepNotification: Boolean) {
        releasePlayer()
        stopVibrating()
        // DETACH leaves the notification behind as an ordinary, dismissible one. The cook who
        // missed the sound should still find out that the timer went.
        stopForeground(if (keepNotification) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startRinging() {
        requestAudioFocus()
        val attributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            // The alarm stream: heard over what is playing, at the volume people leave up,
            // and not silenced by a phone turned face-down on a worktop.
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()

        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(applicationContext, Uri.parse("android.resource://$packageName/${R.raw.cook_timer}"))
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()

        vibrate()
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(AudioManager::class.java) ?: return
        // TRANSIENT rather than ducking: a timer that has to be heard over the podcast the
        // cook is listening to should stop it, not compete with it turned down.
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            )
            .build()
        focusRequest = request
        runCatching { manager.requestAudioFocus(request) }
    }

    private fun releasePlayer() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { request ->
                getSystemService(AudioManager::class.java)?.runCatching { abandonAudioFocusRequest(request) }
            }
        }
        focusRequest = null
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    private fun vibrate() {
        val vibrator = vibrator() ?: return
        // Index 0 — repeat the whole pattern, in step with the sound, until it is cancelled.
        // Declared as an alarm either way, which is what keeps it going under the Do Not
        // Disturb the phone may well be on while somebody is cooking.
        val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build(),
                )
            }
        }
    }

    private fun stopVibrating() {
        runCatching { vibrator()?.cancel() }
    }

    private fun notification(): android.app.Notification {
        ensureChannel()
        val s = stringsFor(AppLanguage.current.value)
        val timer = AppGraph.cookTimer.state.value
        val body = timer?.stepText?.takeIf { it.isNotBlank() } ?: timer?.recipeTitle.orEmpty()

        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            timer?.let { putExtra(CookncoMessagingService.EXTRA_LINK, "/recipe/cook?id=${it.recipeId}") }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            // The same hourglass the countdown was posted under — this replaces it, and a
            // timer that changed mark on going off would read as something else entirely.
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(s.timerTimeIsUp)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText(timer?.recipeTitle)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    8_120,
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            // The way out. It stops the *timer*, and the platform layer stops this in
            // response — so there is one way to end a timer rather than two that can disagree.
            .addAction(
                0,
                s.stopTimer,
                PendingIntent.getBroadcast(
                    this,
                    8_121,
                    Intent(ACTION_COOK_TIMER_STOP).setPackage(packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val s = stringsFor(AppLanguage.current.value)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, s.timerDoneChannelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = s.timerDoneChannelDescription
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }
}
