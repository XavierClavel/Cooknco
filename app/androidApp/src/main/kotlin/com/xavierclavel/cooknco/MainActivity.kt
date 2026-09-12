package com.xavierclavel.cooknco

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.di.initFor

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super, which is where the theme is read: this swaps Theme.Cooknco.Splash
        // for the postSplashScreenTheme it names. Skipping it would leave the launch
        // window's theme on the activity for its whole life.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AppGraph.initFor(applicationContext)
        // Before any notification can arrive, and idempotent: a channel that does not exist
        // is one Android silently drops every notification naming it
        CookncoMessagingService.ensureChannel(applicationContext)
        enableEdgeToEdge()
        handleOAuthIntent(intent)
        handleNotificationIntent(intent)
        setContent {
            App()
        }
    }

    // Called when app is already running and receives a deep link (singleTop mode)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * Picks up a notification the *system* drew and the user tapped.
     *
     * That is the app-backgrounded case, where Firebase never calls
     * [CookncoMessagingService.onMessageReceived] and the message's data arrives here on the
     * launch intent instead. The extra is read once and removed, so returning to the
     * activity later does not navigate again.
     */
    private fun handleNotificationIntent(intent: Intent) {
        val link = intent.getStringExtra(CookncoMessagingService.EXTRA_LINK)
            ?: intent.extras?.getString("link")
            ?: return
        intent.removeExtra(CookncoMessagingService.EXTRA_LINK)
        intent.removeExtra("link")
        PushNotifications.onNotificationTapped(link)
    }

    private fun handleOAuthIntent(intent: Intent) {
        val data = intent.data ?: return
        DeepLinks.onCallbackUrl(data.toString())
    }
}
