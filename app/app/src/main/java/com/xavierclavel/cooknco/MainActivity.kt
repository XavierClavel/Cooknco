package com.xavierclavel.cooknco

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.xavierclavel.cooknco.navigation.AppNavigation
import com.xavierclavel.cooknco.ui.auth.AuthViewModel
import com.xavierclavel.cooknco.ui.theme.CookncoTheme

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.factory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthIntent(intent)
        setContent {
            CookncoTheme {
                AppNavigation(viewModel = authViewModel)
            }
        }
    }

    // Called when app is already running and receives a deep link (singleTop mode)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme == "cooknco" && data.host == "login") {
            val token = data.getQueryParameter("token") ?: return
            authViewModel.handleOAuthToken(token)
        }
    }
}
