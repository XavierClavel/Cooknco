package com.xavierclavel.cooknco

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.di.initFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives pushes from Firebase.
 *
 * Android hands a message here in two situations only, which is what shapes everything
 * below. While the app is in the foreground, [onMessageReceived] is called and the system
 * draws nothing — so this has to draw it. While the app is backgrounded or stopped, the
 * system draws the `notification` block itself and this is never called; the tap then opens
 * [MainActivity] with the message's data on the intent, which is where that case is picked
 * up. Between them the two cover every state without ever drawing twice.
 *
 * The service can also be started with the app not running at all, which is why it
 * initialises the object graph itself rather than assuming [MainActivity] has.
 */
class CookncoMessagingService : FirebaseMessagingService() {

    /**
     * Its own scope: a service is not a lifecycle owner, and the work outlives the callback
     * that starts it. A [SupervisorJob] so a failed registration does not kill the next.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /**
         * The channel every notification from the app goes to.
         *
         * Must match what the backend puts in `android.notification.channel_id`
         * (`FcmPushSender.DEFAULT_CHANNEL_ID`): from Android 8 on, a message naming a channel
         * that does not exist is dropped without a trace.
         */
        const val CHANNEL_ID = "cooknco_default"

        /** Where a tapped notification's target is put on [MainActivity]'s intent. */
        const val EXTRA_LINK = "cooknco.notification.link"

        /**
         * Creates the channel.
         *
         * Called from [MainActivity] as well as from here, because whichever of the two runs
         * first has to have created it: a notification arriving before the app was ever
         * opened would otherwise be dropped.
         */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    /**
     * Firebase has issued this install a new token.
     *
     * Fires on first run, after a reinstall, and whenever the SDK rotates one — including
     * with nobody signed in, in which case there is no session to register under and the
     * launch-time registration in `AppNavigation` picks it up instead.
     */
    override fun onNewToken(token: String) {
        AppGraph.initFor(applicationContext)
        scope.launch { AppGraph.pushRepository.register(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        ensureChannel(applicationContext)

        // The `notification` block when the system passed one on, the data as a fallback for
        // a data-only message — which nothing sends today, but which would otherwise vanish
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"] ?: ""
        show(title, body, message.data["link"], message.data["notificationId"])
    }

    private fun show(title: String, body: String, link: String?, notificationId: String?) {
        // Nothing may be drawn without the runtime permission on Android 13+, and a check
        // here rather than a caught exception keeps a refusal from looking like a crash
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            link?.let { putExtra(EXTRA_LINK, it) }
        }

        val pending = PendingIntent.getActivity(
            this,
            // Distinct per notification, so two of them do not share — and overwrite — one
            // another's extras. The FCM id when there is one; the clock otherwise.
            notificationId?.toIntOrNull() ?: System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(this).notify(
            notificationId?.toIntOrNull() ?: System.currentTimeMillis().toInt(),
            notification,
        )
    }
}
