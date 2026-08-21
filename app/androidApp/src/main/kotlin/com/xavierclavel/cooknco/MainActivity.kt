package com.xavierclavel.cooknco

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.di.initFor

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.initFor(applicationContext)
        enableEdgeToEdge()
        handleOAuthIntent(intent)
        setContent {
            App()
        }
    }

    // Called when app is already running and receives a deep link (singleTop mode)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent) {
        val data = intent.data ?: return
        DeepLinks.onCallbackUrl(data.toString())
    }
}
